package li.cil.oc.core.impl.util;


import li.cil.oc.api.internal.MultiTank;
import li.cil.oc.core.util.FluidHandler;
import li.cil.oc.core.util.FluidStack;
import li.cil.oc.core.util.FluidTank;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

public final class FluidUtils {
  private static FluidTransferHandler handler;

  private FluidUtils() {
  }

  public static void setHandler(FluidTransferHandler h) {
    handler = h;
  }

  public static FluidHandler fluidHandlerAt(BlockPosition position) {
    return handler != null ? handler.fluidHandlerAt(position) : null;
  }

  public static FluidHandler fluidHandlerAt(BlockPosition position, Direction side) {
    return handler != null ? handler.fluidHandlerAt(position, side) : null;
  }

  public static int transferBetweenFluidHandlersAt(BlockPosition sourcePos, Direction sourceSide, BlockPosition sinkPos, Direction sinkSide, int limit, int sourceTank) {
    return handler != null ? handler.transferBetweenFluidHandlersAt(sourcePos, sourceSide, sinkPos, sinkSide, limit, sourceTank) : 0;
  }

  public static FluidTank tankFrom(MultiTank multiTank, int index) {
    return handler != null ? handler.tankFrom(multiTank, index) : null;
  }

  public static FluidHandler fluidHandlerIn(ItemStack stack) {
    return handler != null ? handler.fluidHandlerIn(stack) : null;
  }

  /** What draining a fluid container gives: the fluid that came out and the container afterwards. */
  public record Drained(FluidStack fluid, ItemStack container) {
  }

  /**
   * Drains up to amount from one of the stack's items (a copy): what came out and what the item is now (an empty bucket,
   * a cell with the rest), or null if nothing came out (a bucket gives all or nothing).
   */
  public static Drained drainItem(ItemStack stack, int amount) {
    return handler != null ? handler.drainItem(stack, amount) : null;
  }

  public static ItemStack fillItem(ItemStack stack, FluidStack resource) {
    return handler != null ? handler.fillItem(stack, resource) : null;
  }

  public static boolean isFluidContainer(ItemStack stack) {
    return handler != null && handler.isFluidContainer(stack);
  }

  public interface FluidTransferHandler {
    FluidHandler fluidHandlerAt(BlockPosition position);

    default FluidHandler fluidHandlerAt(BlockPosition position, Direction side) {
      return fluidHandlerAt(position);
    }

    FluidHandler fluidHandlerIn(ItemStack stack);

    ItemStack fillItem(ItemStack stack, FluidStack resource);

    default Drained drainItem(ItemStack stack, int amount) {
      return null;
    }

    boolean isFluidContainer(ItemStack stack);

    @SuppressWarnings("unused")
    int transferBetweenFluidHandlers(FluidHandler source, Direction sourceSide, FluidHandler sink, Direction sinkSide, int limit, int sourceTank);

    int transferBetweenFluidHandlersAt(BlockPosition sourcePos, Direction sourceSide, BlockPosition sinkPos, Direction sinkSide, int limit, int sourceTank);

    FluidTank tankFrom(MultiTank multiTank, int index);
  }
}
