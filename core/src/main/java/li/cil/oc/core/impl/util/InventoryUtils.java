package li.cil.oc.core.impl.util;

import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class InventoryUtils {

  public static boolean haveSameItemType(ItemStack stackA, ItemStack stackB, boolean checkNBT) {
    return stackA != null && !stackA.isEmpty() && stackB != null && !stackB.isEmpty() &&
      stackA.getItem() == stackB.getItem() &&
      (!stackA.isDamageableItem() || stackA.getDamageValue() == stackB.getDamageValue()) &&
      (!checkNBT || ItemStack.isSameItemSameTags(stackA, stackB));
  }

  /**
   * The platform's item handler of a block entity, seen from a face (null: from inside), or null if it has none.
   */
  @FunctionalInterface
  public interface ItemHandlerLookup {
    @Nullable InventoryAccess itemHandler(BlockEntity blockEntity, @Nullable Direction face);
  }

  private static @Nullable ItemHandlerLookup itemHandlers;

  public static void setItemHandlerLookup(ItemHandlerLookup lookup) {
    itemHandlers = lookup;
  }

  /** The platform's item handler of an item (a backpack), or null if it has none. */
  @FunctionalInterface
  public interface ItemInventoryLookup {
    @Nullable InventoryAccess itemInventory(ItemStack stack);
  }

  private static @Nullable ItemInventoryLookup itemInventories;

  public static void setItemInventoryLookup(ItemInventoryLookup lookup) {
    itemInventories = lookup;
  }

  /**
   * The inventory in an item: its item handler if it has one (every move through the handler: as a container it
   * dropped what the handler refused, and stacks changed in place were never saved by it), else a driver's container.
   */
  public static @Nullable InventoryAccess itemInventoryFor(ItemStack stack, @Nullable Player player) {
    if (stack.isEmpty()) return null;
    if (itemInventories != null) {
      InventoryAccess handler = itemInventories.itemInventory(stack);
      if (handler != null) return handler;
    }
    Container inv = li.cil.oc.api.Driver.inventoryFor(stack, player);
    return inv != null ? InventoryAccess.of(inv, null) : null;
  }

  /**
   * The inventory at the position, seen from the given face of it: the platform's item handler (Forge's item
   * capability) if the block entity has one there, else the block entity as a vanilla container, else the block's
   * own container (a composter).
   */
  public static @Nullable InventorySource inventorySourceAt(BlockPosition position, @Nullable Direction face) {
    Level world = position.level();
    if (world == null) return null;
    BlockPos pos = position.toBlockPos();
    if (!world.isLoaded(pos)) return null;
    BlockEntity te = world.getBlockEntity(pos);
    if (te != null && itemHandlers != null) {
      InventoryAccess handler = itemHandlers.itemHandler(te, face);
      if (handler != null) return new BlockInventorySource(position, handler);
    }
    if (te instanceof Container inv) return new BlockInventorySource(position, InventoryAccess.of(inv, face));
    BlockState state = world.getBlockState(pos);
    if (state.getBlock() instanceof WorldlyContainerHolder holder) {
      WorldlyContainer inv = holder.getContainer(state, world, pos);
      if (inv != null) return new BlockInventorySource(position, InventoryAccess.of(inv, face));
    }
    return null;
  }

  public static @Nullable InventoryAccess inventoryAt(BlockPosition position, @Nullable Direction face) {
    InventorySource source = inventorySourceAt(position, face);
    return source != null ? source.inventory() : null;
  }

  /**
   * Moves up to limit items of the stack into the slot; the stack shrinks by what went in (also when simulating).
   */
  public static boolean insertIntoInventorySlot(ItemStack stack, InventoryAccess inventory, int slot, int limit, boolean simulate) {
    if (stack == null || stack.isEmpty() || limit <= 0) return false;
    int amount = Math.min(stack.getCount(), limit);
    ItemStack rest = inventory.insertItem(slot, stack.copyWithCount(amount), simulate);
    int inserted = amount - rest.getCount();
    if (inserted <= 0) return false;
    stack.shrink(inserted);
    return true;
  }

  public static boolean insertIntoInventorySlot(ItemStack stack, InventoryAccess inventory, int slot, int limit) {
    return insertIntoInventorySlot(stack, inventory, slot, limit, false);
  }

  public static boolean insertIntoInventorySlot(ItemStack stack, Container inventory, Direction side, int slot, int limit, boolean simulate) {
    return insertIntoInventorySlot(stack, InventoryAccess.of(inventory, side), slot, limit, simulate);
  }

  public static boolean insertIntoInventorySlot(ItemStack stack, Container inventory, Direction side, int slot, int limit) {
    return insertIntoInventorySlot(stack, inventory, side, slot, limit, false);
  }

  /**
   * Offers up to limit items from the slot to the consumer, which takes what it wants by shrinking the stack it is
   * given; only that much leaves the slot. Returns how many did.
   */
  public static int extractFromInventorySlot(Consumer<ItemStack> consumer, InventoryAccess inventory, int slot, int limit) {
    ItemStack stack = inventory.getStackInSlot(slot);
    if (stack.isEmpty() || limit <= 0) return 0;
    int maxStackSize = Math.min(inventory.getSlotLimit(slot), stack.getMaxStackSize());
    int amount = Math.min(stack.getCount(), Math.min(limit, maxStackSize));
    ItemStack available = inventory.extractItem(slot, amount, true);
    if (available.isEmpty()) return 0;
    ItemStack offered = available.copy();
    consumer.accept(offered);
    int taken = available.getCount() - Math.max(offered.getCount(), 0);
    if (taken <= 0) return 0;
    ItemStack extracted = inventory.extractItem(slot, taken, false);
    if (extracted.getCount() != taken) {
      Log.get().warn("Inventory {} gave {} of the {} items it offered from slot {}", inventory, extracted.getCount(), taken, slot);
    }
    return taken;
  }

  public static int extractFromInventorySlot(Consumer<ItemStack> consumer, Container inventory, Direction side, int slot, int limit) {
    return extractFromInventorySlot(consumer, InventoryAccess.of(inventory, side), slot, limit);
  }

  @SuppressWarnings("unused")
  public static void insertIntoInventory(ItemStack stack, Container inventory, Direction side, int limit, boolean simulate, Iterable<Integer> slots) {
    if (stack == null || stack.isEmpty() || limit <= 0) return;
    InventoryAccess access = InventoryAccess.of(inventory, side);
    int remaining = limit;
    for (int slot : slots) {
      int stackSize = stack.getCount();
      if (insertIntoInventorySlot(stack, access, slot, remaining, simulate)) {
        remaining -= stackSize - stack.getCount();
      }
    }
  }

  /**
   * Moves up to limit items of the stack into the inventory, onto matching stacks first, then into empty slots.
   */
  public static boolean insertIntoInventory(ItemStack stack, InventoryAccess inventory, int limit, boolean simulate) {
    if (stack == null || stack.isEmpty() || limit <= 0) return false;
    boolean success = false;
    int remaining = limit;
    for (int pass = 0; pass < 2; pass++) {
      for (int slot = 0; slot < inventory.getSlots(); slot++) {
        if (inventory.getStackInSlot(slot).isEmpty() != (pass == 1)) continue;
        int stackSize = stack.getCount();
        if (insertIntoInventorySlot(stack, inventory, slot, remaining, simulate)) {
          remaining -= stackSize - stack.getCount();
          success = true;
        }
      }
    }
    return success;
  }

  public static boolean insertIntoInventory(ItemStack stack, InventoryAccess inventory, int limit) {
    return insertIntoInventory(stack, inventory, limit, false);
  }

  @SuppressWarnings("unused")
  public static boolean insertIntoInventory(ItemStack stack, Container inventory, Direction side, int limit, boolean simulate) {
    return insertIntoInventory(stack, InventoryAccess.of(inventory, side), limit, simulate);
  }

  public static boolean insertIntoInventory(ItemStack stack, Container inventory, Direction side, int limit) {
    return insertIntoInventory(stack, inventory, side, limit, false);
  }

  public static boolean insertIntoInventory(ItemStack stack, Container inventory, Direction side) {
    return insertIntoInventory(stack, inventory, side, 64, false);
  }

  public static int extractAnyFromInventory(Consumer<ItemStack> consumer, InventoryAccess inventory, int limit) {
    for (int slot = 0; slot < inventory.getSlots(); slot++) {
      int extracted = extractFromInventorySlot(consumer, inventory, slot, limit);
      if (extracted > 0) return extracted;
    }
    return 0;
  }

  public static int extractAnyFromInventory(Consumer<ItemStack> consumer, Container inventory, Direction side, int limit) {
    return extractAnyFromInventory(consumer, InventoryAccess.of(inventory, side), limit);
  }

  public static ItemStack extractFromInventory(ItemStack stack, Container inventory, Direction side, boolean simulate, boolean exact) {
    InventoryAccess access = InventoryAccess.of(inventory, side);
    ItemStack remaining = stack.copy();
    for (int slot = 0; slot < access.getSlots() && remaining.getCount() > 0; slot++) {
      extractFromInventorySlot(stackInInv -> {
        if (stackInInv != null && !stackInInv.isEmpty() && remaining.getItem() == stackInInv.getItem() &&
          (!exact || haveSameItemType(remaining, stackInInv, true))) {
          int transferred = Math.min(stackInInv.getCount(), remaining.getCount());
          remaining.shrink(transferred);
          if (!simulate) {
            stackInInv.shrink(transferred);
          }
        }
      }, access, slot, remaining.getCount());
    }
    return remaining;
  }

  public static void insertIntoInventoryAt(ItemStack stack, BlockPosition position, Direction side, int limit, boolean simulate) {
    InventoryAccess inv = inventoryAt(position, side);
    if (inv != null) {
      insertIntoInventory(stack, inv, limit, simulate);
    }
  }

  public static void insertIntoInventoryAt(ItemStack stack, BlockPosition position, Direction side) {
    insertIntoInventoryAt(stack, position, side, 64, false);
  }

  public static int transferBetweenInventories(InventoryAccess source, InventoryAccess sink, int limit) {
    int[] transferred = {0};
    extractAnyFromInventory(stack -> {
      int before = stack.getCount();
      insertIntoInventory(stack, sink, limit);
      transferred[0] += before - stack.getCount();
    }, source, limit);
    return transferred[0];
  }

  public static int transferBetweenInventoriesSlots(InventoryAccess source, int sourceSlot, InventoryAccess sink, @Nullable Integer sinkSlot, int limit) {
    int[] transferred = {0};
    extractFromInventorySlot(stack -> {
      int before = stack.getCount();
      if (sinkSlot != null) {
        insertIntoInventorySlot(stack, sink, sinkSlot, limit);
      } else {
        insertIntoInventory(stack, sink, limit);
      }
      transferred[0] += before - stack.getCount();
    }, source, sourceSlot, limit);
    return transferred[0];
  }

  /**
   * Swaps two slots, or changes nothing. Two vanilla containers have their slots set directly (safe: only if both
   * may hold the other stack). Otherwise both stacks have to come out whole and go into the other slot whole, by the
   * inventories' own rules; if one does not go in, both go back.
   */
  public static boolean swapBetweenInventoriesSlots(InventoryAccess source, int sourceSlot, InventoryAccess sink, int sinkSlot, boolean safe) {
    if (source instanceof InventoryAccess.ContainerAccess a && sink instanceof InventoryAccess.ContainerAccess b) {
      return swapBetweenContainerSlots(a.container(), a.face(), sourceSlot, b.container(), b.face(), sinkSlot, safe);
    }
    int countA = source.getStackInSlot(sourceSlot).getCount();
    int countB = sink.getStackInSlot(sinkSlot).getCount();
    if (countA == 0 && countB == 0) return false;
    if (source.extractItem(sourceSlot, countA, true).getCount() != countA) return false;
    if (sink.extractItem(sinkSlot, countB, true).getCount() != countB) return false;
    ItemStack stackA = source.extractItem(sourceSlot, countA, false);
    ItemStack stackB = sink.extractItem(sinkSlot, countB, false);
    if (source.insertItem(sourceSlot, stackB, true).isEmpty() && sink.insertItem(sinkSlot, stackA, true).isEmpty()) {
      putBack(stackB, source, sourceSlot, sink);
      putBack(stackA, sink, sinkSlot, source);
      return true;
    }
    putBack(stackA, source, sourceSlot, sink);
    putBack(stackB, sink, sinkSlot, source);
    return false;
  }

  // Into the slot; whatever does not fit there into the rest of that inventory, then the other one, so nothing is
  // lost; anything still left is logged (it cannot be dropped: the inventories have no position here).
  private static void putBack(ItemStack stack, InventoryAccess inventory, int slot, InventoryAccess other) {
    ItemStack rest = inventory.insertItem(slot, stack, false);
    if (!rest.isEmpty()) insertIntoInventory(rest, inventory, rest.getCount());
    if (!rest.isEmpty()) insertIntoInventory(rest, other, rest.getCount());
    if (!rest.isEmpty()) Log.get().warn("Swapping slots left {} with nowhere to go", rest);
  }

  private static boolean swapBetweenContainerSlots(Container source, Direction sourceSide, int sourceSlot, Container sink, Direction sinkSide, int sinkSlot, boolean safe) {
    ItemStack stackA = source.getItem(sourceSlot).copy();
    ItemStack stackB = sink.getItem(sinkSlot).copy();
    if (stackA.isEmpty() && stackB.isEmpty()) return false;
    if (safe) {
      if (!source.canPlaceItem(sourceSlot, stackB) || !sink.canPlaceItem(sinkSlot, stackA)) return false;
      if (source instanceof WorldlyContainer sided && !sided.canPlaceItemThroughFace(sourceSlot, stackB, sourceSide))
        return false;
      if (sink instanceof WorldlyContainer sided && !sided.canPlaceItemThroughFace(sinkSlot, stackA, sinkSide))
        return false;
    }
    source.setItem(sourceSlot, stackB);
    sink.setItem(sinkSlot, stackA);
    source.setChanged();
    sink.setChanged();
    return true;
  }

  public static boolean dropSlot(BlockPosition position, Container inventory, int slot, int count, Direction direction) {
    ItemStack stack = inventory.removeItem(slot, count);
    if (!stack.isEmpty()) {
      spawnStackInWorld(position, stack, direction, null);
      return true;
    }
    return false;
  }

  @SuppressWarnings("UnusedReturnValue")
  public static boolean dropSlot(BlockPosition position, Container inventory, int slot, int count) {
    return dropSlot(position, inventory, slot, count, null);
  }

  public static void dropAllSlots(BlockPosition position, Container inventory) {
    for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
      ItemStack stack = inventory.getItem(slot);
      if (!stack.isEmpty()) {
        inventory.setItem(slot, ItemStack.EMPTY);
        spawnStackInWorld(position, stack, null, null);
      }
    }
  }

  public static void addToPlayerInventory(ItemStack stack, Player player, boolean spawnInWorld) {
    if (stack != null && !stack.isEmpty()) {
      if (player.getInventory().add(stack)) {
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
      }
      if (stack.getCount() > 0 && spawnInWorld) {
        player.drop(stack, false);
      }
    }
  }

  public static void addToPlayerInventory(ItemStack stack, Player player) {
    addToPlayerInventory(stack, player, true);
  }

  public static ItemEntity spawnStackInWorld(BlockPosition position, ItemStack stack, Direction direction, java.util.function.Predicate<ItemEntity> validator) {
    if (position.level() != null && stack != null && !stack.isEmpty()) {
      net.minecraft.world.level.Level world = position.level();
      RandomSource rng = world.random;
      int ox = 0, oy = 0, oz = 0;
      if (direction != null) {
        ox = direction.getStepX();
        oy = direction.getStepY();
        oz = direction.getStepZ();
      }
      double tx = 0.1 * (rng.nextDouble() - 0.5) + ox * 0.65;
      double ty = 0.1 * (rng.nextDouble() - 0.5) + oy * 0.75 + (ox + oz) * 0.25;
      double tz = 0.1 * (rng.nextDouble() - 0.5) + oz * 0.65;
      Vec3 dropPos = position.offset(0.5 + tx, 0.5 + ty, 0.5 + tz);
      ItemEntity entity = new ItemEntity(world, dropPos.x, dropPos.y, dropPos.z, stack.copy());
      entity.setDeltaMovement(
        0.0125 * (rng.nextDouble() - 0.5) + ox * 0.03,
        0.0125 * (rng.nextDouble() - 0.5) + oy * 0.08 + (ox + oz) * 0.03,
        0.0125 * (rng.nextDouble() - 0.5) + oz * 0.03
      );
      if (validator == null || validator.test(entity)) {
        world.addFreshEntity(entity);
        return entity;
      }
    }
    return null;
  }

  public interface InventorySource {
    InventoryAccess inventory();
  }

  public record BlockInventorySource(BlockPosition position, InventoryAccess inventory) implements InventorySource {
  }

  @SuppressWarnings("unused")
  public record EntityInventorySource(Entity entity, InventoryAccess inventory) implements InventorySource {
  }

}
