package li.cil.oc.core.impl.server.component.traits;

import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.core.impl.util.BlockPosition;
import li.cil.oc.core.impl.util.ExtendedArguments;
import li.cil.oc.core.impl.util.FluidUtils;
import li.cil.oc.core.impl.util.InventoryAccess;
import li.cil.oc.core.impl.util.InventoryUtils;
import net.minecraft.core.Direction;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;


import static li.cil.oc.core.util.ResultWrapper.result;

public interface InventoryTransfer extends WorldAware, SideRestricted {
  String onTransferContents();

  int fluidTransferRate();

  @Callback(doc = "function(sourceSide:number, sinkSide:number[, count:number[, sourceSlot:number[, sinkSlot:number]]]):boolean -- Transfer some items between two inventories.")
  default Object[] transferItem(Context context, Arguments args) {
    Direction sourceSide = checkSideForAction(args, 0);
    BlockPosition sourcePos = position().offset(sourceSide);
    Direction sinkSide = checkSideForAction(args, 1);
    BlockPosition sinkPos = position().offset(sinkSide);
    int count = ExtendedArguments.optItemCount(args, 2, 64);
    String reason = onTransferContents();
    if (reason != null) return result(null, reason);
    InventoryAccess sourceInv = InventoryUtils.inventoryAt(sourcePos, sourceSide.getOpposite());
    InventoryAccess sinkInv = InventoryUtils.inventoryAt(sinkPos, sinkSide.getOpposite());
    if (args.count() > 3) {
      if (sourceInv == null || sinkInv == null) throw new IllegalArgumentException("no inventory");
      int sourceSlot = ExtendedArguments.checkSlot(args, sourceInv, 3);
      int sinkSlot = ExtendedArguments.optSlot(args, sinkInv, 4, -1);
      return result(InventoryUtils.transferBetweenInventoriesSlots(sourceInv, sourceSlot, sinkInv, sinkSlot < 0 ? null : sinkSlot, count) > 0);
    }
    if (sourceInv == null || sinkInv == null) return result(null, "no inventory");
    return result(InventoryUtils.transferBetweenInventories(sourceInv, sinkInv, count) > 0);
  }

  @Callback(doc = "function(sourceSide:number, side:number[, count:number[, sourceSlot:number]]):number -- Drop items from an inventory into the block space on the given side, where they fall; never into an inventory there (that is transferItem). Returns how many were dropped.")
  default Object[] dropItem(Context context, Arguments args) {
    Direction sourceSide = checkSideForAction(args, 0);
    Direction side = checkSideForAction(args, 1);
    int count = ExtendedArguments.optItemCount(args, 2, 64);
    InventoryAccess source = InventoryUtils.inventoryAt(position().offset(sourceSide), sourceSide.getOpposite());
    if (source == null) return result(null, "no inventory");
    int slot = ExtendedArguments.isMissing(args, 3) ? -1 : ExtendedArguments.checkSlot(args, source, 3);
    Level level = level();
    BlockPos target = position().offset(side).toBlockPos();
    BlockState state = level.getBlockState(target);
    if (state.isCollisionShapeFullBlock(level, target)) return result(0, "blocked");
    String reason = onTransferContents();
    if (reason != null) return result(null, reason);
    // on top of what is there, e.g. on the bottom of a cauldron's cell, so the block can pick the item up
    VoxelShape shape = state.getCollisionShape(level, target);
    double y = target.getY() + (shape.isEmpty() ? 0.375 : shape.max(Direction.Axis.Y) + 0.01);
    int[] dropped = {0};
    Consumer<ItemStack> drop = stack -> {
      ItemEntity entity = new ItemEntity(level, target.getX() + 0.5, y, target.getZ() + 0.5, stack.copy(), 0, 0, 0);
      entity.setDefaultPickUpDelay();
      level.addFreshEntity(entity);
      dropped[0] += stack.getCount();
      stack.setCount(0);
    };
    if (slot >= 0) InventoryUtils.extractFromInventorySlot(drop, source, slot, count);
    else InventoryUtils.extractAnyFromInventory(drop, source, count);
    return result(dropped[0]);
  }

  @Callback(doc = "function(sourceSide:number, sinkSide:number, sourceSlot:number, sinkSlot:number[, safe:boolean]):boolean -- Swap two inventory slots if and only if both directions succeed.")
  default Object[] swap(Context context, Arguments args) {
    Direction sourceSide = checkSideForAction(args, 0);
    BlockPosition sourcePos = position().offset(sourceSide);
    Direction sinkSide = checkSideForAction(args, 1);
    BlockPosition sinkPos = position().offset(sinkSide);
    String reason = onTransferContents();
    if (reason != null) return result(null, reason);
    InventoryAccess source = InventoryUtils.inventoryAt(sourcePos, sourceSide.getOpposite());
    if (source == null) return result(null, "no inventory");
    InventoryAccess sink = InventoryUtils.inventoryAt(sinkPos, sinkSide.getOpposite());
    if (sink == null) return result(null, "no inventory");
    int sourceSlot = ExtendedArguments.checkSlot(args, source, 2);
    int sinkSlot = ExtendedArguments.checkSlot(args, sink, 3);
    boolean safe = args.optBoolean(4, false);
    return result(InventoryUtils.swapBetweenInventoriesSlots(source, sourceSlot, sink, sinkSlot, safe));
  }

  @Callback(doc = "function(sourceSide:number, sinkSide:number[, count:number [, sourceTank:number]]):boolean, number -- Transfer some fluid between two tanks. A source block or vanilla cauldron counts as a 1000 mB tank, and an empty side takes 1000 mB as a source block. Returns operation result and filled amount")
  default Object[] transferFluid(Context context, Arguments args) {
    Direction sourceSide = checkSideForAction(args, 0);
    BlockPosition sourcePos = position().offset(sourceSide);
    Direction sinkSide = checkSideForAction(args, 1);
    BlockPosition sinkPos = position().offset(sinkSide);
    int count = ExtendedArguments.optFluidCount(args, 2, Integer.MAX_VALUE);
    int sourceTank = args.optInteger(3, -1);
    String reason = onTransferContents();
    if (reason != null) return result(null, reason);
    int rate = fluidTransferRate();
    if (rate == 0) return result(null, "device has fluid transfer rate of 0");
    int moved = FluidUtils.transferBetweenFluidHandlersAt(sourcePos, sourceSide.getOpposite(), sinkPos, sinkSide.getOpposite(), count, sourceTank);
    if (moved > 0) {
      context.pause((double) moved / (double) rate);
    }
    return result(moved > 0, moved);
  }

  @Callback(doc = "function():number -- Returns the fluid transfer rate in liters per second.")
  default Object[] getFluidTransferRate(Context context, Arguments args) {
    return result(fluidTransferRate());
  }
}
