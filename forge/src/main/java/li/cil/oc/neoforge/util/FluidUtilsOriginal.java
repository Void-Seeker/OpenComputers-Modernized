package li.cil.oc.neoforge.util;

import li.cil.oc.core.impl.util.BlockPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;

public final class FluidUtilsOriginal {
  private FluidUtilsOriginal() {
  }

  public static int doTransfer(IFluidHandler source, Direction sourceSide, IFluidHandler sink, Direction sinkSide, int limit) {
    return doTransfer(source, sourceSide, sink, sinkSide, limit, -1);
  }

  @SuppressWarnings("unused")
  public static int doTransfer(IFluidHandler source, Direction sourceSide, IFluidHandler sink, Direction sinkSide, int limit, int sourceTank) {
    int tanks = source.getTanks();
    // A copy: getFluidInTank may return the tank's own stack, which must not be modified.
    FluidStack srcFluid = (sourceTank < 0 || tanks <= sourceTank) ? FluidStack.EMPTY : source.getFluidInTank(sourceTank).copy();
    FluidStack drained;
    if (srcFluid.isEmpty()) {
      drained = source.drain(limit, IFluidHandler.FluidAction.SIMULATE);
    } else {
      srcFluid.setAmount(limit);
      drained = source.drain(srcFluid, IFluidHandler.FluidAction.SIMULATE);
    }
    if (drained.isEmpty()) return 0;
    int accepted = sink.fill(drained, IFluidHandler.FluidAction.SIMULATE);
    if (accepted <= 0) return 0;
    drained.setAmount(accepted);
    FluidStack moved = source.drain(drained, IFluidHandler.FluidAction.EXECUTE);
    // Report what really arrived: a source may refuse a partial drain (buckets, cauldrons).
    return moved.isEmpty() ? 0 : sink.fill(moved, IFluidHandler.FluidAction.EXECUTE);
  }

  public static IFluidHandler fluidHandlerAt(BlockPosition position) {
    if (position.level() != null) {
      Level world = position.level();
      if (world.isLoaded(position.toBlockPos())) {
        IFluidHandler capHandler = li.cil.oc.neoforge.compat.Capabilities.FluidHandler.BLOCK.getCapability(world, position.toBlockPos(), null);
        if (capHandler != null) {
          return capHandler;
        }
        BlockEntity te = world.getBlockEntity(position.toBlockPos());
        if (te instanceof IFluidHandler handler) {
          return handler;
        }
        return new GenericBlockWrapper(position);
      }
    }
    return null;
  }

  private record GenericBlockWrapper(BlockPosition position) implements IFluidHandler {

    IFluidHandler currentWrapper() {
      if (position.level() == null) return null;
      Level world = position.level();
      BlockPos pos = position.toBlockPos();
      if (!world.isLoaded(pos)) return null;
      BlockState state = world.getBlockState(pos);
      if (state.is(Blocks.CAULDRON) || state.is(Blocks.WATER_CAULDRON) || state.is(Blocks.LAVA_CAULDRON)) {
        return new CauldronWrapper(world, pos);
      }
      Block block = state.getBlock();
      if (block instanceof LiquidBlock) {
        return new FluidBlockWrapper(world, pos);
      }
      return null;
    }

    @Override
    public int getTanks() {
      IFluidHandler w = currentWrapper();
      return w != null ? w.getTanks() : 0;
    }

    @Override
    public @NotNull FluidStack getFluidInTank(int tank) {
      IFluidHandler w = currentWrapper();
      return w != null ? w.getFluidInTank(tank) : FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) {
      IFluidHandler w = currentWrapper();
      return w != null ? w.getTankCapacity(tank) : 0;
    }

    @Override
    public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
      IFluidHandler w = currentWrapper();
      return w != null && w.isFluidValid(tank, stack);
    }

    @Override
    public int fill(@NotNull FluidStack resource, @NotNull FluidAction action) {
      IFluidHandler w = currentWrapper();
      return w != null ? w.fill(resource, action) : 0;
    }

    @Override
    public @NotNull FluidStack drain(@NotNull FluidStack resource, @NotNull FluidAction action) {
      IFluidHandler w = currentWrapper();
      return w != null ? w.drain(resource, action) : FluidStack.EMPTY;
    }

    @Override
    public @NotNull FluidStack drain(int maxDrain, @NotNull FluidAction action) {
      IFluidHandler w = currentWrapper();
      return w != null ? w.drain(maxDrain, action) : FluidStack.EMPTY;
    }
  }

  // A fluid source block can be drained like with a bucket, which removes it; flowing fluid holds nothing.
  private record FluidBlockWrapper(Level level, BlockPos pos) implements IFluidHandler {

    private FluidStack contents() {
      FluidState state = level.getFluidState(pos);
      return state.isSource() ? new FluidStack(state.getType(), FluidType.BUCKET_VOLUME) : FluidStack.EMPTY;
    }

    @Override
    public int getTanks() {
      return 1;
    }

    @Override
    public @NotNull FluidStack getFluidInTank(int tank) {
      return contents();
    }

    @Override
    public int getTankCapacity(int tank) {
      return FluidType.BUCKET_VOLUME;
    }

    @Override
    public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
      return false;
    }

    @Override
    public int fill(@NotNull FluidStack resource, @NotNull FluidAction action) {
      return 0;
    }

    @Override
    public @NotNull FluidStack drain(FluidStack resource, @NotNull FluidAction action) {
      return resource.isFluidEqual(contents()) ? drain(resource.getAmount(), action) : FluidStack.EMPTY;
    }

    @Override
    public @NotNull FluidStack drain(int maxDrain, @NotNull FluidAction action) {
      FluidStack contents = contents();
      if (contents.isEmpty() || maxDrain < contents.getAmount()) return FluidStack.EMPTY;
      if (action.execute()) {
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL_IMMEDIATE);
        level.gameEvent(null, GameEvent.FLUID_PICKUP, pos);
      }
      return contents;
    }
  }

  // Vanilla cauldrons work like with a bucket: only a full cauldron (1000 mB) of water or lava goes in or out.
  private record CauldronWrapper(Level level, BlockPos pos) implements IFluidHandler {

    private FluidStack contents() {
      BlockState state = level.getBlockState(pos);
      if (state.is(Blocks.LAVA_CAULDRON)) {
        return new FluidStack(Fluids.LAVA, FluidType.BUCKET_VOLUME);
      }
      if (state.is(Blocks.WATER_CAULDRON)) {
        return new FluidStack(Fluids.WATER, FluidType.BUCKET_VOLUME * state.getValue(LayeredCauldronBlock.LEVEL) / LayeredCauldronBlock.MAX_FILL_LEVEL);
      }
      return FluidStack.EMPTY;
    }

    @Override
    public int getTanks() {
      return 1;
    }

    @Override
    public @NotNull FluidStack getFluidInTank(int tank) {
      return contents();
    }

    @Override
    public int getTankCapacity(int tank) {
      return FluidType.BUCKET_VOLUME;
    }

    @Override
    public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
      return stack.getFluid().isSame(Fluids.WATER) || stack.getFluid().isSame(Fluids.LAVA);
    }

    @Override
    public int fill(@NotNull FluidStack resource, @NotNull FluidAction action) {
      if (resource.getAmount() < FluidType.BUCKET_VOLUME || !level.getBlockState(pos).is(Blocks.CAULDRON)) return 0;
      BlockState filled;
      SoundEvent sound;
      if (resource.getFluid().isSame(Fluids.WATER)) {
        filled = Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL);
        sound = SoundEvents.BUCKET_EMPTY;
      } else if (resource.getFluid().isSame(Fluids.LAVA)) {
        filled = Blocks.LAVA_CAULDRON.defaultBlockState();
        sound = SoundEvents.BUCKET_EMPTY_LAVA;
      } else {
        return 0;
      }
      if (action.execute()) {
        level.setBlockAndUpdate(pos, filled);
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(null, GameEvent.FLUID_PLACE, pos);
      }
      return FluidType.BUCKET_VOLUME;
    }

    @Override
    public @NotNull FluidStack drain(FluidStack resource, @NotNull FluidAction action) {
      return resource.isFluidEqual(contents()) ? drain(resource.getAmount(), action) : FluidStack.EMPTY;
    }

    @Override
    public @NotNull FluidStack drain(int maxDrain, @NotNull FluidAction action) {
      FluidStack contents = contents();
      if (contents.getAmount() < FluidType.BUCKET_VOLUME || maxDrain < FluidType.BUCKET_VOLUME) return FluidStack.EMPTY;
      if (action.execute()) {
        level.setBlockAndUpdate(pos, Blocks.CAULDRON.defaultBlockState());
        level.playSound(null, pos, contents.getFluid().isSame(Fluids.LAVA) ? SoundEvents.BUCKET_FILL_LAVA : SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(null, GameEvent.FLUID_PICKUP, pos);
      }
      return contents;
    }
  }
}
