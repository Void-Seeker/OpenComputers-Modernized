package li.cil.oc.core.impl.common.inventory;

import java.util.Arrays;
import net.minecraft.world.item.ItemStack;

/**
 * A server item's parts, read from the item (for disassembling it, or an inventory controller looking into it). The
 * anonymous ServerInventory used before could not hold them (no ItemStackInventoryAccessor: a ClassCastException on
 * the first look - disassembling a server destroyed it with all its parts).
 */
public class ItemServerInventory implements ServerInventory, ItemStackInventory.ItemStackInventoryAccessor {
  private final ItemStack container;
  private final ItemStack[] items;

  public ItemServerInventory(ItemStack container) {
    this.container = container;
    this.items = new ItemStack[getContainerSize()];
    Arrays.fill(items, ItemStack.EMPTY);
    if (!container.isEmpty()) reinitialize(null);
  }

  @Override
  public ItemStack container() {
    return container;
  }

  @Override
  public ItemStack[] getItemsArray() {
    return items;
  }

  @Override
  public void updateItems(int slot, ItemStack stack) {
    if (slot >= 0 && slot < items.length) items[slot] = stack == null ? ItemStack.EMPTY : stack;
  }
}
