package li.cil.oc.neoforge.util;

import li.cil.oc.api.internal.MultiTank;
import li.cil.oc.core.impl.util.BlockPosition;
import li.cil.oc.core.impl.util.FluidUtils;
import li.cil.oc.core.util.FluidHandler;
import li.cil.oc.core.util.FluidTank;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

public class FluidTransferHandlerImpl implements FluidUtils.FluidTransferHandler {
  @Override
  public FluidHandler fluidHandlerAt(BlockPosition position) {
    IFluidHandler handler = FluidUtilsOriginal.fluidHandlerAt(position);
    return handler != null ? new li.cil.oc.neoforge.util.FluidHandler(handler) : null;
  }

  @Override
  public FluidHandler fluidHandlerAt(BlockPosition position, Direction side) {
    if (position.level() != null) {
      Level world = position.level();
      if (world.isLoaded(position.toBlockPos())) {
        IFluidHandler capHandler = li.cil.oc.neoforge.compat.Capabilities.FluidHandler.BLOCK.getCapability(world, position.toBlockPos(), side);
        if (capHandler != null) {
          return new li.cil.oc.neoforge.util.FluidHandler(capHandler);
        }
        return fluidHandlerAt(position);
      }
    }
    return null;
  }

  @Override
  public FluidHandler fluidHandlerIn(ItemStack stack) {
    if (!stack.isEmpty()) {
      ItemStack oneSized = stack.copy();
      oneSized.setCount(1);
      IFluidHandlerItem handler = li.cil.oc.neoforge.compat.Capabilities.FluidHandler.ITEM.getCapability(oneSized);
      if (handler != null) {
        return new li.cil.oc.neoforge.util.FluidHandler(handler);
      }
    }
    return null;
  }

  @Override
  public ItemStack fillItem(ItemStack stack, li.cil.oc.core.util.FluidStack resource) {
    if (stack.isEmpty()) return null;
    ItemStack oneSized = stack.copy();
    oneSized.setCount(1);
    IFluidHandlerItem handler = li.cil.oc.neoforge.compat.Capabilities.FluidHandler.ITEM.getCapability(oneSized);
    if (handler == null) return null;
    int filled = handler.fill(li.cil.oc.neoforge.util.FluidHandler.toNeo(resource), IFluidHandler.FluidAction.EXECUTE);
    if (filled <= 0) return null;
    return handler.getContainer();
  }

  @Override
  public boolean isFluidContainer(ItemStack stack) {
    if (stack.isEmpty()) return false;
    return li.cil.oc.neoforge.compat.Capabilities.FluidHandler.ITEM.getCapability(stack) != null;
  }

  @Override
  public int transferBetweenFluidHandlers(FluidHandler source, Direction sourceSide, FluidHandler sink, Direction sinkSide, int limit, int sourceTank) {
    IFluidHandler neoSource = unwrap(source);
    IFluidHandler neoSink = unwrap(sink);
    if (neoSource == null || neoSink == null) return 0;
    return FluidUtilsOriginal.doTransfer(neoSource, sourceSide, neoSink, sinkSide, limit, sourceTank);
  }

  @Override
  public int transferBetweenFluidHandlersAt(BlockPosition sourcePos, Direction sourceSide, BlockPosition sinkPos, Direction sinkSide, int limit, int sourceTank) {
    IFluidHandler sourceHandler = internalHandlerAt(sourcePos);
    if (sourceHandler == null) return 0;
    IFluidHandler sinkHandler = internalHandlerAt(sinkPos);
    if (sinkHandler == null) return 0;
    return FluidUtilsOriginal.doTransfer(sourceHandler, sourceSide, sinkHandler, sinkSide, limit, sourceTank);
  }

  @Override
  public FluidTank tankFrom(MultiTank multiTank, int index) {
    Object tank = multiTank.getFluidTank(index);
    if (tank instanceof FluidTank ft) return ft;
    if (tank instanceof IFluidTank ift) return new li.cil.oc.neoforge.util.FluidTank(ift);
    return null;
  }

  private IFluidHandler unwrap(FluidHandler handler) {
    if (handler instanceof li.cil.oc.neoforge.util.FluidHandler wrapper) return wrapper.delegate();
    return null;
  }

  private IFluidHandler internalHandlerAt(BlockPosition position) {
    return FluidUtilsOriginal.fluidHandlerAt(position);
  }
}
