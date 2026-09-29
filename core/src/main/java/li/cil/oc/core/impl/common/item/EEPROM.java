package li.cil.oc.core.impl.common.item;

import li.cil.oc.core.impl.OCSettings;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import li.cil.oc.compat.CustomData;
import org.jetbrains.annotations.NotNull;

public class EEPROM extends SimpleItem {
  public EEPROM() {
    super();
  }

  @Override
  public @NotNull String getDescriptionId(ItemStack stack) {
    CustomData cd = CustomData.get(stack);
    if (cd != null && !cd.isEmpty()) {
      CompoundTag tag = cd.copyTag();
      if (tag.contains(OCSettings.namespace + "data")) {
        CompoundTag data = tag.getCompound(OCSettings.namespace + "data");
        if (data.contains(OCSettings.namespace + "label")) {
          return data.getString(OCSettings.namespace + "label");
        }
      }
    }
    return super.getDescriptionId(stack);
  }
}
