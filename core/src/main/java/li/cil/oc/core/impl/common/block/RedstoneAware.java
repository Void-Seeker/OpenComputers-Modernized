package li.cil.oc.core.impl.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public abstract class RedstoneAware extends SimpleBlock {
  public RedstoneAware() {
    super();
  }

  public RedstoneAware(BlockBehaviour.Properties properties) {
    super(properties);
  }

  @Override
  public boolean isSignalSource(@NotNull BlockState state) {
    return true;
  }

  @Override
  public int getSignal(@NotNull BlockState state, BlockGetter world, @NotNull BlockPos pos, @NotNull Direction side) {
    BlockEntity te = world.getBlockEntity(pos);
    if (te instanceof li.cil.oc.core.impl.common.blockentity.traits.RedstoneAware redstone) {
      return Math.max(0, redstone.getOutput(side.getOpposite()));
    }
    return 0;
  }

  @Override
  public int getDirectSignal(@NotNull BlockState state, @NotNull BlockGetter world, @NotNull BlockPos pos, @NotNull Direction side) {
    return getSignal(state, world, pos, side);
  }

  // Taken away, it lets go of what it powered through the blocks next to it (it powers them strongly: a lamp behind
  // one stayed lit, only the blocks next to it were told).
  @Override
  public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean isMoving) {
    boolean powering = false;
    if (!level.isClientSide && !state.is(newState.getBlock())
      && level.getBlockEntity(pos) instanceof li.cil.oc.core.impl.common.blockentity.traits.RedstoneAware redstone) {
      for (Direction side : Direction.values()) powering |= redstone.getOutput(side) > 0;
    }
    super.onRemove(state, level, pos, newState, isMoving);
    if (powering) {
      for (Direction side : Direction.values()) level.updateNeighborsAtExceptFromFacing(pos.relative(side), this, side.getOpposite());
    }
  }

  @Override
  public void neighborChanged(@NotNull BlockState state, Level world, @NotNull BlockPos pos, @NotNull Block block, @NotNull BlockPos fromPos, boolean isMoving) {
    BlockEntity te = world.getBlockEntity(pos);
    if (te instanceof li.cil.oc.core.impl.common.blockentity.traits.RedstoneAware redstone) {
      if (!world.isClientSide) redstone.checkRedstoneInputChanged();
    }
  }
}
