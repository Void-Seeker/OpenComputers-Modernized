package li.cil.oc.core.impl.server.component.traits;

import java.util.List;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.core.impl.OCSettings;
import li.cil.oc.core.impl.util.BlockPosition;
import li.cil.oc.core.impl.util.ExtendedArguments;
import li.cil.oc.core.impl.util.InventoryAccess;
import li.cil.oc.core.impl.util.InventoryUtils;
import li.cil.oc.core.util.ResultWrapper;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;


public interface InventoryWorldControl extends InventoryAware, WorldAware, SideRestricted {
  @Override
  Player fakePlayer();

  default InventoryAccess inventoryAtSide(BlockPosition pos, Direction side) {
    return InventoryUtils.inventoryAt(pos, side.getOpposite());
  }

  @Callback(doc = "function(side:number[, fuzzy:boolean=false]):boolean -- Compare the block on the specified side with the one in the selected slot. Returns true if equal.")
  default Object[] compare(Context context, Arguments args) {
    Direction side = checkSideForAction(args, 0);
    ItemStack stack = stackInSlot(selectedSlot());
    if (stack != null && stack.getItem() instanceof BlockItem item) {
      BlockPosition blockPos = position().offset(side);
      BlockState state = level().getBlockState(blockPos.toBlockPos());
      args.optBoolean(1, false); // (fuzzy skipped OC 1.12's metadata check: variants are blocks of their own now)
      // the block, not its state (a rotated log or a connected fence never matched); a wall torch is its item's too
      return ResultWrapper.result(item.getBlock() == state.getBlock() || state.getBlock().asItem() == item);
    }
    return ResultWrapper.result(false);
  }

  @Callback(doc = "function(side:number[, count:number=64]):boolean -- Drops items from the selected slot towards the specified side.")
  default Object[] drop(Context context, Arguments args) {
    Direction facing = checkSideForAction(args, 0);
    int count = ExtendedArguments.optItemCount(args, 1, 64);
    ItemStack stack = inventory().getItem(selectedSlot());
    if (!stack.isEmpty()) {
      BlockPosition blockPos = position().offset(facing);
      // not in front of a chunk that is not ready: an inventory there was not seen, and the items fell out in front of it
      if (!li.cil.oc.core.impl.util.LoadedChunks.isReady(blockPos.level(), blockPos.toBlockPos())) {
        return ResultWrapper.result(false, "not loaded");
      }
      InventoryAccess inv = inventoryAtSide(blockPos, facing);
      if (inv != null && inv.stillValid(fakePlayer()) && mayInteract(blockPos, facing.getOpposite())) {
        if (!InventoryUtils.insertIntoInventory(stack, inv, count)) {
          return ResultWrapper.result(false, "inventory full");
        } else if (stack.isEmpty()) {
          inventory().setItem(selectedSlot(), ItemStack.EMPTY);
        } else {
          inventory().setChanged();
        }
      } else {
        ItemStack dropped = inventory().removeItem(selectedSlot(), count);
        if (!dropped.isEmpty()) {
          ItemEntity entity = InventoryUtils.spawnStackInWorld(position(), dropped, facing, null);
          if (entity == null) {   // could not be dropped: back where it came from
            InventoryUtils.insertIntoInventorySlot(dropped, inventory(), null, selectedSlot(), 64);
          }
        }
      }
      context.pause(OCSettings.get().dropDelay);
      return ResultWrapper.result(true);
    }
    return ResultWrapper.result(false);
  }

  default int suckFromItems(Direction facing) {
    return collectFromItems(entitiesOnSide(facing, ItemEntity.class));
  }

  default int collectFromItems(List<ItemEntity> items) {
    for (ItemEntity entity : items) {
      if (!entity.isRemoved() && !entity.hasPickUpDelay()) {
        ItemStack stack = entity.getItem();
        int size = stack.getCount();
        onSuckCollect(entity);
        if (stack.getCount() < size) return size - stack.getCount();
        else if (entity.isRemoved()) return size;
      }
    }
    return 0;
  }

  @Callback(doc = "function(side:number[, count:number=64]):boolean -- Suck up items from the specified side.")
  default Object[] suck(Context context, Arguments args) {
    Direction facing = checkSideForAction(args, 0);
    int count = ExtendedArguments.optItemCount(args, 1, 64);
    BlockPosition blockPos = position().offset(facing);
    int extracted = 0;
    InventoryAccess inv = inventoryAtSide(blockPos, facing);
    if (inv != null && inv.stillValid(fakePlayer()) && mayInteract(blockPos, facing.getOpposite())) {
      extracted = InventoryUtils.extractAnyFromInventory(
        stack -> {
          for (int slot : insertionSlots()) {
            if (InventoryUtils.insertIntoInventorySlot(stack, this.inventory(), null, slot, 64)) break;
          }
        },
        inv, count);
    }
    if (extracted <= 0) {
      extracted = suckFromItems(facing);
    }
    if (extracted <= 0) {
      return ResultWrapper.result(false);
    } else {
      context.pause(OCSettings.get().suckDelay);
      return ResultWrapper.result(extracted);
    }
  }

  /**
   * Picks the item up into the inventory, selected slot first, onto matching stacks first; the entity keeps what does
   * not fit. (It used to be handed to the fake player, whose own inventory is scratch: the items vanished.)
   */
  default void onSuckCollect(ItemEntity entity) {
    ItemStack stack = entity.getItem();
    for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
      for (int slot : insertionSlots()) {
        if (stack.isEmpty()) break;
        ItemStack there = inventory().getItem(slot);
        if ((pass == 0) != there.isEmpty()) InventoryUtils.insertIntoInventorySlot(stack, inventory(), null, slot, 64);
      }
    }
    if (stack.isEmpty()) entity.discard();
    else entity.setItem(stack);
  }
}
