package li.cil.oc.core.impl.common.inventory;

import li.cil.oc.core.impl.OCSettings;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import li.cil.oc.compat.CustomData;


public interface ItemStackInventory extends Inventory {
  ItemStack container();

  @Override
  default ItemStack[] items() {
    return ((ItemStackInventoryAccessor) this).getItemsArray();
  }

  default void reinitialize(HolderLookup.Provider provider) {
    ItemStack[] items = items();
    for (int i = 0; i < items.length; i++) {
      updateItems(i, null);
    }
    ItemStack c = container();
    if (c != null && !c.isEmpty()) {
      load(dataTag(c), provider);
    }
  }

  default void setChanged(HolderLookup.Provider provider) {
    ItemStack c = container();
    if (c != null && !c.isEmpty()) {
      CompoundTag nbt;
      var customData = CustomData.get(c);
      if (customData == null || customData.isEmpty()) {
        nbt = new CompoundTag();
      } else {
        nbt = customData.copyTag();
      }
      CompoundTag data = nbt.contains(OCSettings.namespace + "data") ? nbt.getCompound(OCSettings.namespace + "data") : new CompoundTag();
      save(data, provider);
      nbt.put(OCSettings.namespace + "data", data);
      CustomData.set(c, CustomData.of(nbt));
    }
  }

  private static CompoundTag dataTag(ItemStack stack) {
    CompoundTag nbt;
    var customData = CustomData.get(stack);
    if (customData == null || customData.isEmpty()) {
      nbt = new CompoundTag();
      CustomData.set(stack, CustomData.of(nbt));
    } else {
      nbt = customData.copyTag();
    }
    if (!nbt.contains(OCSettings.namespace + "data")) {
      nbt.put(OCSettings.namespace + "data", new CompoundTag());
    }
    return nbt.getCompound(OCSettings.namespace + "data");
  }

  interface ItemStackInventoryAccessor {
    ItemStack[] getItemsArray();
  }
}
