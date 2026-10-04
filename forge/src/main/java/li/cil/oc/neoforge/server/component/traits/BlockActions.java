package li.cil.oc.neoforge.server.component.traits;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.core.impl.server.component.traits.SideRestricted;
import li.cil.oc.core.impl.server.component.traits.WorldAware;
import li.cil.oc.core.impl.util.ExtendedArguments;
import li.cil.oc.core.impl.util.InventoryUtils;
import li.cil.oc.neoforge.util.FakePlayerClick;
import li.cil.oc.neoforge.util.FluidUtilsOriginal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidActionResult;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;

import static li.cil.oc.core.util.ResultWrapper.result;

public interface BlockActions extends WorldAware, SideRestricted {
  String onTransferContents();

  @Callback(doc = "function(side:number[, inventorySide:number[, inventorySlot:number[, tankSide:number]]]):boolean, string -- "
    + "Right-click the block on the given side like a player holding the stack from the inventory slot (or an empty hand). "
    + "Everything the click produces - what is left in the hand, what the block gives the player, items it drops - goes "
    + "into the inventory (the slot first), or is dropped if it does not fit. With a tank, filled fluid containers are "
    + "emptied into it first. Returns whether the click did something, and how it went (success, consume, pass, fail).")
  default Object[] use(Context context, Arguments args) {
    Direction side = checkSideForAction(args, 0);
    Direction inventorySide = !ExtendedArguments.isMissing(args, 1) ? checkSideForAction(args, 1) : null;
    Direction tankSide = !ExtendedArguments.isMissing(args, 3) ? checkSideForAction(args, 3) : null;
    Container inventory = null;
    int slot = -1;
    if (inventorySide != null) {
      inventory = InventoryUtils.inventoryAt(position().offset(inventorySide));
      if (inventory == null) return result(null, "no inventory");
      if (!ExtendedArguments.isMissing(args, 2)) slot = ExtendedArguments.checkSlot(args, inventory, 2);
    } else if (!ExtendedArguments.isMissing(args, 2)) {
      throw new IllegalArgumentException("a slot needs an inventory side");
    }
    IFluidHandler tank = null;
    if (tankSide != null) {
      tank = FluidUtilsOriginal.fluidHandlerAt(position().offset(tankSide));
      if (tank == null || tank.getTanks() == 0) return result(null, "no tank");
    }
    String reason = onTransferContents();
    if (reason != null) return result(null, reason);

    ItemStack held = ItemStack.EMPTY;
    if (slot >= 0) {
      ItemStack[] taken = {ItemStack.EMPTY};
      InventoryUtils.extractFromInventorySlot(stack -> {
        taken[0] = stack.copy();
        stack.setCount(0);
      }, inventory, inventorySide.getOpposite(), slot, 64);
      held = taken[0];
    }

    FakePlayerClick.Outcome outcome = FakePlayerClick.click((ServerLevel) level(), position().toBlockPos(), side, held);
    boolean acted = outcome.result().consumesAction();
    for (ItemStack stack : outcome.items()) {
      for (ItemStack rest : acted && tank != null ? emptyInto(tank, stack) : List.of(stack)) {
        putBack(rest, inventory, inventorySide, slot);
      }
    }
    return result(acted, outcome.result().name().toLowerCase(Locale.ROOT));
  }

  @Callback(doc = "function(sourceSide:number, side:number[, sourceSlot:number]):boolean[, string] -- Place a block from the "
    + "inventory (the slot, or the first block found) into the block space on the given side, which must hold only air "
    + "or fluid.")
  default Object[] placeBlock(Context context, Arguments args) {
    Direction sourceSide = checkSideForAction(args, 0);
    Direction side = checkSideForAction(args, 1);
    Container inventory = InventoryUtils.inventoryAt(position().offset(sourceSide));
    if (inventory == null) return result(null, "no inventory");
    int slot;
    if (!ExtendedArguments.isMissing(args, 2)) {
      slot = ExtendedArguments.checkSlot(args, inventory, 2);
      if (!(inventory.getItem(slot).getItem() instanceof BlockItem)) return result(false, "not a block");
    } else {
      slot = -1;
      for (int i = 0; i < inventory.getContainerSize() && slot < 0; i++) {
        if (inventory.getItem(i).getItem() instanceof BlockItem) slot = i;
      }
      if (slot < 0) return result(false, "no blocks");
    }
    BlockPos target = position().offset(side).toBlockPos();
    BlockState there = level().getBlockState(target);
    if (!there.isAir() && !there.liquid()) return result(false, "blocked");
    String reason = onTransferContents();
    if (reason != null) return result(null, reason);

    ItemStack[] taken = {ItemStack.EMPTY};
    InventoryUtils.extractFromInventorySlot(stack -> {
      taken[0] = stack.copy();
      stack.setCount(0);
    }, inventory, sourceSide.getOpposite(), slot, 64);
    if (taken[0].isEmpty()) return result(false, "cannot take it out");
    FakePlayerClick.Outcome outcome = FakePlayerClick.place((ServerLevel) level(), position().toBlockPos(), side, taken[0]);
    for (ItemStack rest : outcome.items()) putBack(rest, inventory, sourceSide, slot);
    boolean placed = outcome.result().consumesAction();
    return placed ? result(true) : result(false, "cannot place it there");
  }

  // Into the inventory, its slot first; dropped if it does not fit.
  private void putBack(ItemStack stack, Container inventory, Direction inventorySide, int slot) {
    if (inventory != null) {
      if (slot >= 0) InventoryUtils.insertIntoInventorySlot(stack, inventory, inventorySide.getOpposite(), slot, stack.getCount());
      if (!stack.isEmpty()) InventoryUtils.insertIntoInventory(stack, inventory, inventorySide.getOpposite(), stack.getCount());
    }
    if (!stack.isEmpty()) InventoryUtils.spawnStackInWorld(position(), stack, null, null);
  }

  /** Empties filled fluid containers (a stack may hold several) into the tank; returns the containers afterwards. */
  private static List<ItemStack> emptyInto(IFluidHandler tank, ItemStack stack) {
    List<ItemStack> result = new ArrayList<>();
    while (!stack.isEmpty()) {
      FluidActionResult emptied = FluidUtil.tryEmptyContainer(stack, tank, Integer.MAX_VALUE, null, true);
      if (!emptied.isSuccess()) break;
      stack.shrink(1);
      if (!emptied.getResult().isEmpty()) result.add(emptied.getResult());
    }
    if (!stack.isEmpty()) result.add(stack);
    return result;
  }
}
