package li.cil.oc.core.impl.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * 1.20.1: item colors live in the stack's {@code display.color} tag, like dyed leather armor.
 */
public final class ItemColorizer {

  public static boolean hasColor(ItemStack stack) {
    CompoundTag displayTag = stack.getTagElement("display");
    return displayTag != null && displayTag.contains("color", Tag.TAG_ANY_NUMERIC);
  }

  public static int getColor(ItemStack stack) {
    return hasColor(stack) ? stack.getTagElement("display").getInt("color") : -1;
  }

  public static void removeColor(ItemStack stack) {
    CompoundTag displayTag = stack.getTagElement("display");
    if (displayTag != null && displayTag.contains("color")) {
      displayTag.remove("color");
      if (displayTag.isEmpty()) stack.removeTagKey("display");
    }
  }

  public static void setColor(ItemStack stack, int color) {
    stack.getOrCreateTagElement("display").putInt("color", color);
  }
}
