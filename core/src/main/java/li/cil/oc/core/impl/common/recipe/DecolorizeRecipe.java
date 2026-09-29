package li.cil.oc.core.impl.common.recipe;

import net.minecraft.resources.ResourceLocation;
import li.cil.oc.core.impl.util.ItemColorizer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

public class DecolorizeRecipe extends CustomRecipe {
  public final Item targetItem;
  private static RecipeSerializer<?> SERIALIZER;

  public static void setSerializer(RecipeSerializer<?> serializer) {
    SERIALIZER = serializer;
  }

  public DecolorizeRecipe(ResourceLocation id, CraftingBookCategory category, Item target) {
    super(id, category);
    this.targetItem = target;
  }

  public DecolorizeRecipe(ResourceLocation id, Item target) {
    this(id, CraftingBookCategory.MISC, target);
  }

  @SuppressWarnings("unused")
  public DecolorizeRecipe(ResourceLocation id, Block target) {
    this(id, target.asItem());
  }

  @Override
  public boolean matches(@NotNull CraftingContainer crafting, @NotNull Level world) {
    ItemStack[] stacks = getItems(crafting);
    java.util.List<ItemStack> targets = new java.util.ArrayList<>();
    java.util.List<ItemStack> other = new java.util.ArrayList<>();
    for (var stack : stacks) {
      if (stack == null) continue;
      if (stack.getItem() == targetItem) {
        targets.add(stack);
      } else {
        other.add(stack);
      }
    }
    return targets.size() == 1 && other.size() == 1 && other.get(0).getItem() == Items.WATER_BUCKET;
  }

  @Override
  public @NotNull ItemStack assemble(@NotNull CraftingContainer crafting, @NotNull net.minecraft.core.RegistryAccess provider) {
    ItemStack targetStack = null;

    ItemStack[] stacks = getItems(crafting);
    for (var stack : stacks) {
      if (stack == null) continue;
      if (stack.getItem() == targetItem) {
        targetStack = stack.copy();
        targetStack.setCount(1);
      } else if (stack.getItem() != Items.WATER_BUCKET) {
        return ItemStack.EMPTY;
      }
    }

    if (targetStack == null) return ItemStack.EMPTY;

    ItemColorizer.removeColor(targetStack);
    return targetStack;
  }

  @Override
  public boolean canCraftInDimensions(int width, int height) {
    return width * height >= 10;
  }

  @Override
  public @NotNull RecipeSerializer<?> getSerializer() {
    return SERIALIZER;
  }

  private ItemStack[] getItems(CraftingContainer crafting) {
    java.util.List<ItemStack> list = new java.util.ArrayList<>();
    for (int i = 0; i < crafting.getContainerSize(); i++) {
      var stack = crafting.getItem(i);
      if (!stack.isEmpty()) list.add(stack);
    }
    return list.toArray(new ItemStack[0]);
  }
}
