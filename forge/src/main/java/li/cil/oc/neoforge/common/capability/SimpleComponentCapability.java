package li.cil.oc.neoforge.common.capability;

import com.google.common.base.Strings;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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
 * their node, hosted here for the block entity (whose callbacks it serves, and whose own onConnect, onDisconnect and
 * onMessage it calls), saved with it, joined when it is placed or its chunk loads, and removed when it is broken or
 * unloaded (Forge invalidates its capabilities then).
 */
public final class SimpleComponentCapability implements SimpleComponentProvider, Environment, CallbackTarget, DeviceInfo, ICapabilitySerializable<CompoundTag> {
  private static final Logger LOGGER = LoggerFactory.getLogger(SimpleComponentCapability.class);
  private static final ResourceLocation ID = new ResourceLocation(Tags.MOD_ID, "simple_component");
  private static final String NODE_TAG = "oc:node";
  // ticks a join waits for the block entity to be in its level and its chunk to finish loading
  private static final int JOIN_TRIES = 100;
  // the block entity's own onConnect, onDisconnect and onMessage, which OC 1.12 kept when it made it an Environment
  private static final ClassValue<Method[]> OWN_METHODS = new ClassValue<>() {
    @Override
    protected Method[] computeValue(final Class<?> type) {
      return new Method[]{ownMethod(type, "onConnect", Node.class), ownMethod(type, "onDisconnect", Node.class), ownMethod(type, "onMessage", Message.class)};
    }
  };

  // to join at the end of the tick: a placed block entity is in its level by then
  private static final List<SimpleComponentCapability> pending = new ArrayList<>();

  private final BlockEntity blockEntity;
  private final SimpleComponent simpleComponent;
  private LazyOptional<SimpleComponentProvider> self = LazyOptional.of(() -> this);
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
      // not in its level yet (made before it was put there, or in a chunk still being generated: its load schedules
      // it again), or its chunk is still loading: tried again
      if (!removed && (level == null || !level.isClientSide && !LoadedChunks.isReady(level, pos))) {
        if (++joinTries < JOIN_TRIES) {
          synchronized (pending) {
            pending.add(this);
          }
        } else {
          joinScheduled = false;
        }
        return;
      }
      if (removed || level.isClientSide) {
        joinScheduled = false;
        return;
      }
    }
    try {
      // (one made for something else, with the level set)
      if (level.getBlockEntity(pos) == blockEntity) Network.joinOrCreateNetwork(blockEntity);
    } finally {
      // (not before: looking the node up during the join would schedule another)
      synchronized (this) {
        joinScheduled = false;
      }
    }
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
    }
    if (old != null) {
      // node() is the node until its removal is through, as in OC 1.12: the block entity, told of it, knows it as its own
      try {
        old.remove();
      } finally {
        synchronized (this) {
          if (node == old) node = null;
        }
      }
    }
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
    final Node current;
    final Node old;
    synchronized (this) {
      // (the node while it is being removed, then none)
      if (removed) return node;
      if (Strings.isNullOrEmpty(name)) {
        // no valid component right now: the node goes (as in OC 1.12), its address kept for when it comes back
        current = null;
        old = node;
        if (old != null) savedNode = save(old);
        node = null;
      } else {
        old = null;
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
        current = node;
      }
    }
    if (old != null) old.remove();
    // a node in no network joins (OC 1.12 joined on every validate: a block entity put in its level later, a name
    // come back); a neighbour joins it only once it is in a network
    if (current != null && current.network() == null && level.getServer() != null && level.getServer().isSameThread()) scheduleJoin();
    return current;
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
    // a new address (a new block, one saved before it had one, or one changed in a merge) is saved: a chunk is saved
    // only when marked
    final Node own;
    final boolean changed;
    synchronized (this) {
      own = this.node;
      final String address = own != null ? own.address() : null;
      changed = address != null && !address.equals(savedAddress);
      if (changed) savedAddress = address;
    }
    final Level level = blockEntity.getLevel();
    if (changed && level != null && level.getServer() != null && level.getServer().isSameThread()) blockEntity.setChanged();
    callOwn(0, node);
  }

  @Override
  public void onDisconnect(final Node node) {
    callOwn(1, node);
  }

  @Override
  public void onMessage(final Message message) {
    callOwn(2, message);
  }

  private static Method ownMethod(final Class<?> type, final String name, final Class<?> parameter) {
    try {
      final Method method = type.getMethod(name, parameter);
      if (Modifier.isStatic(method.getModifiers())) return null;
      method.setAccessible(true); // (public, in a class that need not be)
      return method;
    } catch (NoSuchMethodException | RuntimeException e) {
      return null;
    }
  }

  private void callOwn(final int which, final Object argument) {
    final Method method = OWN_METHODS.get(blockEntity.getClass())[which];
    if (method == null) return;
    try {
      method.invoke(blockEntity, argument);
    } catch (InvocationTargetException e) {
      // (thrown on, as by an Environment's own)
      if (e.getCause() instanceof RuntimeException runtime) throw runtime;
      if (e.getCause() instanceof Error error) throw error;
      throw new RuntimeException(e.getCause());
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Map<String, String> getDeviceInfo() {
    return blockEntity instanceof DeviceInfo deviceInfo ? deviceInfo.getDeviceInfo() : null;
  }

  @Override
  public @NotNull <T> LazyOptional<T> getCapability(final @NotNull Capability<T> capability, final @Nullable Direction side) {
    if (capability != OCBlockCapabilities.SIMPLE_COMPONENT_PROVIDER.capability()) return LazyOptional.empty();
    final LazyOptional<SimpleComponentProvider> current;
    final boolean revived;
    synchronized (this) {
      // asked for after it was removed: its capabilities were revived (CapabilityProvider.reviveCaps) for a block entity
      // put back into its level (Forge asks a removed one's provider for nothing)
      revived = removed && !blockEntity.isRemoved();
      if (revived) {
        removed = false;
        self = LazyOptional.of(() -> this);
      }
      current = self;
    }
    if (revived) scheduleJoin();
    return current.cast();
  }

  private boolean isClient() {
    final Level level = blockEntity.getLevel();
    return level != null && level.isClientSide;
  }

  @Override
  public synchronized CompoundTag serializeNBT() {
    final CompoundTag nbt = new CompoundTag();
    // (a client's copy has none: it gave the server's address to the items picked from its block)
    if (isClient()) return nbt;
    final CompoundTag nodeNbt = node != null ? save(node) : savedNode != null ? savedNode.copy() : null;
    if (nodeNbt != null) {
      nbt.put(NODE_TAG, nodeNbt);
      if (nodeNbt.contains("address")) savedAddress = nodeNbt.getString("address");
    }
    return nbt;
  }

  @Override
  public void deserializeNBT(final CompoundTag nbt) {
    if (isClient() || !nbt.contains(NODE_TAG, Tag.TAG_COMPOUND)) return;
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
