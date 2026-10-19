package li.cil.oc.neoforge.common.capability;

import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import li.cil.oc.api.Network;
import li.cil.oc.api.capability.SimpleComponentProvider;
import li.cil.oc.api.driver.DeviceInfo;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.SimpleComponent;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.core.Tags;
import li.cil.oc.core.impl.util.LoadedChunks;
import li.cil.oc.core.impl.util.SideTracker;
import li.cil.oc.core.server.machine.CallbackTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * What OC 1.12 injected into block entities implementing {@link SimpleComponent}, as a capability attached to each:
 * their node, hosted here for the block entity (whose callbacks it serves), saved with it, joined when it is placed or
 * its chunk loads, and removed when it is broken or unloaded (Forge invalidates its capabilities then).
 */
public final class SimpleComponentCapability implements SimpleComponentProvider, Environment, CallbackTarget, DeviceInfo, ICapabilitySerializable<CompoundTag> {
  private static final Logger LOGGER = LoggerFactory.getLogger(SimpleComponentCapability.class);
  private static final ResourceLocation ID = new ResourceLocation(Tags.MOD_ID, "simple_component");
  private static final String NODE_TAG = "oc:node";
  // ticks a join waits for its chunk to finish loading
  private static final int JOIN_TRIES = 100;

  // to join at the end of the tick: a placed block entity is in its level by then
  private static final List<SimpleComponentCapability> pending = new ArrayList<>();

  private final BlockEntity blockEntity;
  private final SimpleComponent simpleComponent;
  private final LazyOptional<SimpleComponentProvider> self = LazyOptional.of(() -> this);
  private Node node;
  // the node's data while there is no node (loaded before it was made, or kept while the name was empty)
  private CompoundTag savedNode;
  // the address last saved or marked to be saved
  private String savedAddress;
  private boolean removed;
  private boolean joinScheduled;
  private int joinTries;

  private SimpleComponentCapability(final BlockEntity blockEntity, final SimpleComponent simpleComponent) {
    this.blockEntity = blockEntity;
    this.simpleComponent = simpleComponent;
  }

  /**
   * Attaches the capability to block entities implementing SimpleComponent (in their constructor); not to those that are
   * environments themselves, which OC takes as those (OC 1.12 would not inject into them).
   */
  public static void onAttachBlockEntity(final AttachCapabilitiesEvent<BlockEntity> event) {
    if (event.getObject() instanceof SimpleComponent simpleComponent && !(event.getObject() instanceof Environment)
      && !(event.getObject() instanceof li.cil.oc.api.network.SidedEnvironment)) {
      final var capability = new SimpleComponentCapability(event.getObject(), simpleComponent);
      event.addCapability(ID, capability);
      event.addListener(capability::invalidate);
      // placed (in its level by the end of the tick); those made off the server thread (world generation) join when
      // their chunk loads
      if (SideTracker.isServer()) capability.scheduleJoin();
    }
  }

  /** A loaded chunk's simple components join their networks (the join scheduled when they were made had no level). */
  public static void onChunkLoad(final ChunkEvent.Load event) {
    if (event.getLevel().isClientSide() || !(event.getChunk() instanceof LevelChunk chunk)) return;
    for (final BlockEntity blockEntity : chunk.getBlockEntities().values()) {
      if (blockEntity instanceof SimpleComponent) {
        blockEntity.getCapability(OCBlockCapabilities.SIMPLE_COMPONENT_PROVIDER.capability()).ifPresent(provider -> {
          if (provider instanceof SimpleComponentCapability capability) capability.scheduleJoin();
        });
      }
    }
  }

  public static void onServerTick(final TickEvent.ServerTickEvent event) {
    if (event.phase != TickEvent.Phase.END) return;
    final SimpleComponentCapability[] due;
    synchronized (pending) {
      if (pending.isEmpty()) return;
      due = pending.toArray(new SimpleComponentCapability[0]);
      pending.clear();
    }
    for (final SimpleComponentCapability capability : due) {
      try {
        capability.join();
      } catch (Throwable t) {
        LOGGER.warn("A simple component failed to join its network.", t);
      }
    }
  }

  /** At server stop (they held their block entities, and the levels, into the next world). */
  public static void onServerStopped() {
    synchronized (pending) {
      pending.clear();
    }
  }

  private void scheduleJoin() {
    synchronized (this) {
      if (joinScheduled || removed) return;
      joinScheduled = true;
      joinTries = 0;
    }
    synchronized (pending) {
      pending.add(this);
    }
  }

  private void join() {
    final Level level = blockEntity.getLevel();
    final BlockPos pos = blockEntity.getBlockPos();
    synchronized (this) {
      joinScheduled = false;
      // not in a level (made for something else, or in a chunk still being generated: its load schedules it again)
      if (removed || level == null || level.isClientSide) return;
      if (!LoadedChunks.isReady(level, pos)) {
        if (++joinTries < JOIN_TRIES) {
          joinScheduled = true;
          synchronized (pending) {
            pending.add(this);
          }
        }
        return;
      }
    }
    if (level.getBlockEntity(pos) != blockEntity) return;
    Network.joinOrCreateNetwork(blockEntity);
    final Node joined = node();
    // a new address (a new block, or one saved before it had one) is saved: a chunk is saved only when marked
    final String address = joined != null ? joined.address() : null;
    final boolean changed;
    synchronized (this) {
      changed = address != null && !address.equals(savedAddress);
      if (changed) savedAddress = address;
    }
    if (changed) blockEntity.setChanged();
  }

  // network changes are made outside the lock: they call the machines in the network
  private void invalidate() {
    final Node old;
    synchronized (this) {
      if (removed) return;
      removed = true;
      self.invalidate();
      old = node;
      if (old != null) savedNode = save(old);
      node = null;
    }
    if (old != null) old.remove();
  }

  private static CompoundTag save(final Node node) {
    final CompoundTag nbt = new CompoundTag();
    node.save(nbt, li.cil.oc.compat.RegistryLookup.get());
    return nbt;
  }

  @Override
  public Node node() {
    final Level level = blockEntity.getLevel();
    if (level == null || level.isClientSide) return null;
    final String name = simpleComponent.getComponentName();
    final Node old;
    synchronized (this) {
      if (removed) return null;
      if (!Strings.isNullOrEmpty(name)) {
        if (node == null) {
          node = Network.newNode(this, Visibility.Network).withComponent(name).create();
          if (node != null && savedNode != null) {
            try {
              node.load(savedNode, li.cil.oc.compat.RegistryLookup.get());
            } catch (Exception e) {
              LOGGER.warn("Failed to load a simple component's node.", e);
            }
            savedNode = null;
          }
        }
        return node;
      }
      // no valid component right now: the node goes (as in OC 1.12), its address kept for when it comes back
      old = node;
      if (old == null) return null;
      savedNode = save(old);
      node = null;
    }
    old.remove();
    return null;
  }

  @Override
  public String getComponentName() {
    return simpleComponent.getComponentName();
  }

  /** The block entity: its callbacks are the component's, called on it. */
  @Override
  public Object callbackTarget() {
    return blockEntity;
  }

  public BlockEntity blockEntity() {
    return blockEntity;
  }

  @Override
  public void onConnect(final Node node) {
  }

  @Override
  public void onDisconnect(final Node node) {
  }

  @Override
  public void onMessage(final Message message) {
  }

  @Override
  public Map<String, String> getDeviceInfo() {
    return blockEntity instanceof DeviceInfo deviceInfo ? deviceInfo.getDeviceInfo() : null;
  }

  @Override
  public @NotNull <T> LazyOptional<T> getCapability(final @NotNull Capability<T> capability, final @Nullable Direction side) {
    return OCBlockCapabilities.SIMPLE_COMPONENT_PROVIDER.capability().orEmpty(capability, self);
  }

  @Override
  public synchronized CompoundTag serializeNBT() {
    final CompoundTag nbt = new CompoundTag();
    final CompoundTag nodeNbt = node != null ? save(node) : savedNode != null ? savedNode.copy() : null;
    if (nodeNbt != null) {
      nbt.put(NODE_TAG, nodeNbt);
      if (nodeNbt.contains("address")) savedAddress = nodeNbt.getString("address");
    }
    return nbt;
  }

  @Override
  public void deserializeNBT(final CompoundTag nbt) {
    if (!nbt.contains(NODE_TAG, Tag.TAG_COMPOUND)) return;
    final CompoundTag nodeNbt = nbt.getCompound(NODE_TAG);
    final Node current;
    synchronized (this) {
      if (nodeNbt.contains("address")) savedAddress = nodeNbt.getString("address");
      current = node;
      if (current == null) savedNode = nodeNbt.copy();
    }
    // a block entity already set up (/data merge): its node takes the address, in its network too
    if (current != null) current.load(nodeNbt, li.cil.oc.compat.RegistryLookup.get());
  }
}
