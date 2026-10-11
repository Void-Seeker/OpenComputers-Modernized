package li.cil.oc.core.impl.util;

import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * An inventory as OC moves items in and out of it, seen from one face. Shaped like Forge's {@code IItemHandler}, so
 * a platform item handler keeps its own rules (which faces, which slots, which items may come out): stacks from
 * {@link #getStackInSlot} are not to be modified, items move only through {@link #insertItem} and
 * {@link #extractItem}.
 */
public interface InventoryAccess {
  int getSlots();

  ItemStack getStackInSlot(int slot);

  /**
   * Returns what did not (or would not) go in.
   */
  ItemStack insertItem(int slot, ItemStack stack, boolean simulate);

  /**
   * Returns what came (or would come) out.
   */
  ItemStack extractItem(int slot, int amount, boolean simulate);

  int getSlotLimit(int slot);

  /**
   * The vanilla inventory behind this, if that is what it is.
   */
  default @Nullable Container container() {
    return null;
  }

  default boolean stillValid(Player player) {
    return true;
  }

  /**
   * After a stack in it was changed in place (renamed).
   */
  void setChanged();

  static InventoryAccess of(Container container, @Nullable Direction face) {
    return new ContainerAccess(container, face);
  }

  /**
   * A vanilla {@link Container}, with OC's rules for it: every slot can be addressed; a {@link WorldlyContainer}
   * decides per slot what may go in through the face (if there is one) and come out through it.
   */
  record ContainerAccess(Container container, @Nullable Direction face) implements InventoryAccess {
    @Override
    public int getSlots() {
      return container.getContainerSize();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
      return container.getItem(slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
      if (stack.isEmpty() || !container.canPlaceItem(slot, stack)) return stack;
      if (container instanceof WorldlyContainer sided && face != null && !sided.canPlaceItemThroughFace(slot, stack, face)) {
        return stack;
      }
      int maxStackSize = Math.min(container.getMaxStackSize(), stack.getMaxStackSize());
      ItemStack existing = container.getItem(slot);
      int amount;
      if (existing.isEmpty()) {
        amount = Math.min(maxStackSize, stack.getCount());
        if (amount <= 0) return stack;
        if (!simulate) {
          container.setItem(slot, stack.copyWithCount(amount));
          container.setChanged();
        }
      } else {
        if (existing.getCount() >= maxStackSize || !ItemStack.isSameItemSameTags(existing, stack)) return stack;
        amount = Math.min(maxStackSize - existing.getCount(), stack.getCount());
        if (!simulate) {
          existing.grow(amount);
          container.setChanged();
        }
      }
      return amount == stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - amount);
    }

    @Override
    @SuppressWarnings("DataFlowIssue") // canTakeItemThroughFace is asked without a face from inside, as it always was
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
      ItemStack stack = container.getItem(slot);
      if (stack.isEmpty() || amount <= 0) return ItemStack.EMPTY;
      if (container instanceof WorldlyContainer sided && !sided.canTakeItemThroughFace(slot, stack, face)) {
        return ItemStack.EMPTY;
      }
      int count = Math.min(amount, stack.getCount());
      ItemStack extracted = stack.copyWithCount(count);
      if (!simulate) {
        stack.shrink(count);
        if (stack.isEmpty()) container.setItem(slot, ItemStack.EMPTY);
        else container.setChanged();
      }
      return extracted;
    }

    @Override
    public int getSlotLimit(int slot) {
      return container.getMaxStackSize();
    }

    @Override
    public boolean stillValid(Player player) {
      return container.stillValid(player);
    }

    @Override
    public void setChanged() {
      container.setChanged();
    }
  }
}
