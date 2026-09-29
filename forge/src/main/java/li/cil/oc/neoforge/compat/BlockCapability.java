package li.cil.oc.neoforge.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import org.jetbrains.annotations.Nullable;

/**
 * Stand-in for NeoForge's {@code BlockCapability}: a block-side view of a Forge {@link Capability}.
 * Lookups go through the block entity at the position, as Forge only has block entity capabilities.
 *
 * @param <T> the capability type.
 * @param <C> the context type, always a nullable {@link Direction} here.
 */
public final class BlockCapability<T, C> {
  private final Capability<T> capability;

  private BlockCapability(final Capability<T> capability) {
    this.capability = capability;
  }

  public static <T> BlockCapability<T, @Nullable Direction> of(final Capability<T> capability) {
    return new BlockCapability<>(capability);
  }

  public Capability<T> capability() {
    return capability;
  }

  /**
   * Equivalent of NeoForge's {@code level.getCapability(capability, pos, side)}.
   */
  public @Nullable T getCapability(final Level level, final BlockPos pos, final @Nullable Direction side) {
    final BlockEntity blockEntity = level.getBlockEntity(pos);
    return blockEntity != null ? getCapability(blockEntity, side) : null;
  }

  public @Nullable T getCapability(final BlockEntity blockEntity, final @Nullable Direction side) {
    return blockEntity.getCapability(capability, side).resolve().orElse(null);
  }
}
