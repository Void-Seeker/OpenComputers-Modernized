package li.cil.oc.neoforge.util;

import li.cil.oc.core.util.FluidStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fluids.capability.IFluidHandler;

public record FluidHandler(IFluidHandler delegate) implements li.cil.oc.core.util.FluidHandler {

  @Override
  public int getTanks() {
    return delegate.getTanks();
  }

  @Override
  public FluidStack getFluidInTank(int tank) {
    net.minecraftforge.fluids.FluidStack neo = delegate.getFluidInTank(tank);
    return neo.isEmpty() ? FluidStack.EMPTY : fromNeo(neo);
  }

  @Override
  public int getTankCapacity(int tank) {
    return delegate.getTankCapacity(tank);
  }

  @Override
  public int fill(FluidStack resource, boolean simulate) {
    net.minecraftforge.fluids.FluidStack neo = resource.isEmpty() ? net.minecraftforge.fluids.FluidStack.EMPTY : toNeo(resource);
    return delegate.fill(neo, simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
  }

  @Override
  public FluidStack drain(FluidStack resource, boolean simulate) {
    net.minecraftforge.fluids.FluidStack neo = resource.isEmpty() ? net.minecraftforge.fluids.FluidStack.EMPTY : toNeo(resource);
    net.minecraftforge.fluids.FluidStack result = delegate.drain(neo, simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
    return result.isEmpty() ? FluidStack.EMPTY : fromNeo(result);
  }

  @Override
  public FluidStack drain(int maxDrain, boolean simulate) {
    net.minecraftforge.fluids.FluidStack result = delegate.drain(maxDrain, simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
    return result.isEmpty() ? FluidStack.EMPTY : fromNeo(result);
  }

  public static net.minecraftforge.fluids.FluidStack toNeo(FluidStack stack) {
    return new net.minecraftforge.fluids.FluidStack(
      BuiltInRegistries.FLUID.get(new ResourceLocation(stack.fluidName())),
      stack.amount()
    );
  }

  public static FluidStack fromNeo(net.minecraftforge.fluids.FluidStack stack) {
    return new FluidStack(BuiltInRegistries.FLUID.getKey(stack.getFluid()).toString(), stack.getAmount(), stack.hasTag());
  }
}
