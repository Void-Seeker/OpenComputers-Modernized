package li.cil.oc.core.impl.common.item.data;

import li.cil.oc.api.Items;
import li.cil.oc.api.Persistable;
import li.cil.oc.core.impl.util.SideTracker;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import li.cil.oc.compat.CustomData;
import org.jetbrains.annotations.NotNull;

public abstract class ItemData implements Persistable {
  protected String itemName;

  public ItemData(String itemName) {
    this.itemName = itemName;
  }

  public void load(ItemStack stack, HolderLookup.Provider provider) {
    var tag = CustomData.get(stack);
    if (tag != null && !tag.isEmpty()) {
      load(tag.copyTag(), provider);
    }
  }

  public void load(ItemStack stack) {
    var server = SideTracker.getCurrentServer();
    load(stack, server != null ? server.registryAccess() : li.cil.oc.compat.RegistryLookup.get());
  }

  public void save(ItemStack stack, HolderLookup.Provider provider) {
    var tag = CustomData.get(stack);
    var nbt = tag != null && !tag.isEmpty() ? tag.copyTag() : new CompoundTag();
    save(nbt, provider);
    CustomData.set(stack, CustomData.of(nbt));
  }

  public void save(ItemStack stack) {
    var server = SideTracker.getCurrentServer();
    save(stack, server != null ? server.registryAccess() : li.cil.oc.compat.RegistryLookup.get());
  }

  public ItemStack createItemStack() {
    if (itemName == null) return null;
    ItemStack stack = Items.get(itemName).createItemStack(1);
    save(stack);
    return stack;
  }
}
