package li.cil.oc.core.impl.common.item.data;

import li.cil.oc.core.Constants;
import li.cil.oc.core.impl.OCSettings;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import li.cil.oc.compat.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public class NavigationUpgradeData extends ItemData {
  public ItemStack map = new ItemStack(net.minecraft.world.item.Items.FILLED_MAP);

  public NavigationUpgradeData() {
    super(Constants.ItemName.NavigationUpgrade);
  }

  public NavigationUpgradeData(ItemStack stack) {
    this();
    load(stack);
  }

  public record MapData(int xCenter, int zCenter) {
  }

  public MapData mapData(Level world) {
    try {
      MapItemSavedData savedData = MapItem.getSavedData(map, world);
      if (savedData != null) {
        return new MapData(savedData.centerX, savedData.centerZ);
      }
      return new MapData(0, 0);
    } catch (Throwable t) {
      throw new RuntimeException("invalid map");
    }
  }

  public int getSize(Level world) {
    MapItemSavedData savedData = MapItem.getSavedData(map, world);
    int scale = savedData != null ? savedData.scale : 0;
    return 128 * (1 << scale);
  }

  @Override
  public void load(ItemStack stack, HolderLookup.Provider provider) {
    var tag = CustomData.get(stack);
    if (tag != null && !tag.isEmpty()) {
      load(tag.copyTag().getCompound(OCSettings.namespace + "data"), provider);
    }
  }

  @Override
  public void save(ItemStack stack, HolderLookup.Provider provider) {
    var tag = CustomData.get(stack);
    var nbt = tag != null && !tag.isEmpty() ? tag.copyTag() : new CompoundTag();
    save(nbt.getCompound(OCSettings.namespace + "data"), provider);
    CustomData.set(stack, CustomData.of(nbt));
  }

  @Override
  public void load(CompoundTag nbt, HolderLookup.Provider provider) {
    if (nbt.contains(OCSettings.namespace + "map")) {
      map = ItemStack.of(nbt.getCompound(OCSettings.namespace + "map"));
    }
  }

  @Override
  public void save(CompoundTag nbt, HolderLookup.Provider provider) {
    if (map != null && !map.isEmpty()) {
      nbt.put(OCSettings.namespace + "map", map.save(new CompoundTag()));
    }
  }
}
