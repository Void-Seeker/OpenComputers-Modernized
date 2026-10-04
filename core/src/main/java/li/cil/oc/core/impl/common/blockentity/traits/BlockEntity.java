package li.cil.oc.core.impl.common.blockentity.traits;

import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.SidedEnvironment;
import li.cil.oc.core.impl.OCSettings;
import li.cil.oc.core.impl.common.blockentity.traits.power.AppliedEnergistics2;
import li.cil.oc.core.impl.util.BlockPosition;
import li.cil.oc.core.impl.util.EventHandlerDelegate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BlockEntity extends net.minecraft.world.level.block.entity.BlockEntity {
  private static final Logger LOGGER = LoggerFactory.getLogger(BlockEntity.class);
  public static boolean savingForClients = false;
  protected HolderLookup.Provider loadProvider;

  private boolean initialized;
  private boolean unloading;

  public BlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
    super(type, pos, state);
  }

  @Override
  public void setLevel(net.minecraft.world.level.@NotNull Level level) {
    super.setLevel(level);
    if (!initialized && !level.isClientSide) {
      initialized = true;
      EventHandlerDelegate.get().scheduleServer(this::initialize);
    }
  }

  // Workaround for some Intermediary vs SRG mapping runtime weirdness.
  public net.minecraft.world.level.Level world() {
    return getLevel();
  }

  public boolean isClient() {
    return !isServer();
  }

  public boolean isServer() {
    var level = getLevel();
    if (level != null) return !level.isClientSide;
    return true;
  }

  public Block block() {
    return getBlockState().getBlock();
  }

  public BlockPosition position() {
    return BlockPosition.apply(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), getLevel());
  }

  /** Called from the chunk unload event; the chunk is saved after it and removed after that. */
  public void markUnloading() {
    unloading = true;
  }

  public boolean isUnloading() {
    return unloading;
  }

  private final java.util.concurrent.atomic.AtomicBoolean changeScheduled = new java.util.concurrent.atomic.AtomicBoolean();

  /**
   * Asks for this block entity to be saved. Components call this from the computer threads, where vanilla's
   * setChanged() must not run (it touches the chunk), so the save mark is applied on the next server tick.
   */
  public void markChanged() {
    if (!changeScheduled.getAndSet(true)) {
      EventHandlerDelegate.get().scheduleServer(() -> {
        changeScheduled.set(false);
        if (!isRemoved() && getLevel() != null) setChanged();
      });
    }
  }

  // Also on chunk unload. The chunk has been saved by then (ChunkMap.scheduleUnload: event, save, remove), so the
  // nodes can leave their networks and the machines can close. Skipping this, as upstream does, leaves stale nodes
  // in networks that stay loaded: the block gets new addresses when its chunk returns, and Lua states never close.
  @Override
  public void setRemoved() {
    super.setRemoved();
    dispose();
  }

  public void initialize() {
    if (this instanceof AppliedEnergistics2 ae2) {
      ae2.ae2Validate();
    }
  }

  public void dispose() {
    if (this instanceof AppliedEnergistics2 ae2) {
      ae2.ae2Invalidate();
    }
    if (isServer()) {
      if (this instanceof SidedEnvironment sidedEnvironment) {
        for (var side : Direction.values()) {
          var sideNode = sidedEnvironment.sidedNode(side);
          if (sideNode != null) sideNode.remove();
        }
      }
      if (this instanceof Environment environment) {
        var envNode = environment.node();
        if (envNode != null) envNode.remove();
      }
    }
  }

  public void updateEntity() {
    if (this instanceof AppliedEnergistics2 ae2) {
      ae2.ae2UpdateEntity();
    }
    var level = getLevel();
    if (level != null && OCSettings.get().periodicallyForceLightUpdate && level.getGameTime() % 40 == 0 && getBlockState().getLightEmission() > 0) {
      level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
  }

  public void readFromNBTForServer(CompoundTag nbt) {
    if (this instanceof AppliedEnergistics2 ae2) {
      ae2.ae2ReadFromNBT(nbt);
    }
    if (this instanceof RedstoneAware ra) {
      ra.readRedstoneFromNBT(nbt);
    }
    if (this instanceof BundledRedstoneAware bundled) {
      readBundled(nbt, OCSettings.namespace + "rs.bundledInput", bundled.bundledInput());
      readBundled(nbt, OCSettings.namespace + "rs.bundledOutput", bundled.bundledOutput());
    }
  }

  private static void readBundled(CompoundTag nbt, String key, int[][] target) {
    for (int side = 0; side < 6 && side < target.length; side++) {
      if (nbt.contains(key + side)) {
        int[] saved = nbt.getIntArray(key + side);
        System.arraycopy(saved, 0, target[side], 0, Math.min(saved.length, target[side].length));
      }
    }
  }

  public void writeToNBTForServer(CompoundTag nbt) {
    if (this instanceof AppliedEnergistics2 ae2) {
      ae2.ae2WriteToNBT(nbt);
    }
    if (this instanceof RedstoneAware ra) {
      ra.writeRedstoneToNBT(nbt);
    }
    if (this instanceof BundledRedstoneAware bundled) { // bundled outputs went to 0 after a reload
      for (int side = 0; side < 6 && side < bundled.bundledInput().length; side++) {
        nbt.putIntArray(OCSettings.namespace + "rs.bundledInput" + side, bundled.bundledInput()[side].clone());
        nbt.putIntArray(OCSettings.namespace + "rs.bundledOutput" + side, bundled.bundledOutput()[side].clone());
      }
    }
  }

  public void readFromNBTForClient(CompoundTag nbt) {
  }

  public void writeToNBTForClient(CompoundTag nbt) {
  }

  @Override
  public void load(@NotNull CompoundTag nbt) {
    super.load(nbt);
    var level = getLevel();
    loadAdditional(nbt, level != null ? level.registryAccess() : li.cil.oc.compat.RegistryLookup.get());
  }

  @Override
  protected void saveAdditional(@NotNull CompoundTag nbt) {
    super.saveAdditional(nbt);
    saveAdditional(nbt, getEffectiveProviderOrDefault());
  }

  private HolderLookup.Provider getEffectiveProviderOrDefault() {
    var provider = getEffectiveProvider();
    return provider != null ? provider : li.cil.oc.compat.RegistryLookup.get();
  }

  protected void loadAdditional(@NotNull CompoundTag nbt, HolderLookup.@NotNull Provider provider) {
    loadProvider = provider;
    if (isServer()) {
      readFromNBTForServer(nbt);
    } else {
      readFromNBTForClient(nbt);
    }
  }

  public void saveAdditional(@NotNull CompoundTag nbt, HolderLookup.@NotNull Provider provider) {
    loadProvider = provider;
    if (isServer()) {
      writeToNBTForServer(nbt);
    }
  }

  public HolderLookup.Provider getEffectiveProvider() {
    if (loadProvider != null) return loadProvider;
    var level = getLevel();
    if (level != null) return level.registryAccess();
    return null;
  }

  @Override
  public ClientboundBlockEntityDataPacket getUpdatePacket() {
    var nbt = new CompoundTag();
    savingForClients = true;
    try {
      try {
        writeToNBTForClient(nbt);
      } catch (Throwable e) {
        LOGGER.warn("Problem writing BlockEntity description packet", e);
      }
      if (nbt.isEmpty()) return null;
      return ClientboundBlockEntityDataPacket.create(this, be -> nbt);
    } finally {
      savingForClients = false;
    }
  }

  @Override
  public @NotNull CompoundTag getUpdateTag() {
    if (getLevel() == null) return new CompoundTag();
    savingForClients = true;
    var nbt = super.getUpdateTag();
    try {
      writeToNBTForClient(nbt);
    } catch (Throwable ignored) {
    }
    savingForClients = false;
    return nbt;
  }


}
