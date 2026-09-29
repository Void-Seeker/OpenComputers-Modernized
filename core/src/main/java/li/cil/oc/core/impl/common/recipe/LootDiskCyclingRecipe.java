package li.cil.oc.core.impl.common.recipe;

import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.resources.ResourceLocation;
import li.cil.oc.api.Items;
import li.cil.oc.core.Constants;
import li.cil.oc.core.impl.OCSettings;
import li.cil.oc.core.impl.common.LootManager;
import li.cil.oc.core.impl.integration.util.Wrench;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import li.cil.oc.compat.CustomData;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class LootDiskCyclingRecipe extends CustomRecipe {
  @SuppressWarnings("unused")
  public LootDiskCyclingRecipe(ResourceLocation id, CraftingBookCategory category) {
    super(id, category);
  }

  @Override
  public boolean matches(@NotNull CraftingContainer crafting, @NotNull Level world) {
    ItemStack[] stacks = collectStacks(crafting);
    boolean hasLoot = false;
    boolean hasWrench = false;
    for (var stack : stacks) {
      if (LootManager.isLootDisk(stack)) hasLoot = true;
      if (Wrench.isWrench(stack)) hasWrench = true;
    }
    return stacks.length == 2 && hasLoot && hasWrench;
  }

  @Override
  public @NotNull ItemStack assemble(@NotNull CraftingContainer crafting, @NotNull net.minecraft.core.RegistryAccess provider) {
    var lootDiskStacks = LootManager.disksForCycling();
    ItemStack[] stacks = collectStacks(crafting);
    ItemStack lootDisk = null;
    for (var stack : stacks) {
      if (LootManager.isLootDisk(stack)) {
        lootDisk = stack;
        break;
      }
    }
    if (lootDisk != null && !lootDiskStacks.isEmpty()) {
      String lootFactoryName = getLootFactoryName(lootDisk);
      int oldIndex = -1;
      for (int i = 0; i < lootDiskStacks.size(); i++) {
        if (getLootFactoryName(lootDiskStacks.get(i)).equals(lootFactoryName)) {
          oldIndex = i;
          break;
        }
      }
      int newIndex = (oldIndex + 1) % lootDiskStacks.size();
      return lootDiskStacks.get(newIndex).copy();
    }
    return ItemStack.EMPTY;
  }

  public String getLootFactoryName(ItemStack stack) {
    CustomData _ld = CustomData.get(stack);
    return _ld != null ? _ld.copyTag().getString(OCSettings.namespace + "lootFactory") : "";
  }

  public ItemStack[] collectStacks(CraftingContainer crafting) {
    java.util.List<ItemStack> list = new java.util.ArrayList<>();
    for (int i = 0; i < crafting.getContainerSize(); i++) {
      var stack = crafting.getItem(i);
      if (!stack.isEmpty()) list.add(stack);
    }
    return list.toArray(new ItemStack[0]);
  }

  @Override
  public @NotNull NonNullList<Ingredient> getIngredients() {
    var list = NonNullList.<Ingredient>create();
    var disks = LootManager.disksForCycling();
    if (!disks.isEmpty()) {
      list.add(Ingredient.of(disks.toArray(new ItemStack[0])));
    } else {
      var floppy = Items.get(Constants.ItemName.Floppy);
      if (floppy != null) list.add(Ingredient.of(floppy.item()));
    }
    var wrench = Items.get(Constants.ItemName.Wrench);
    if (wrench != null) list.add(Ingredient.of(wrench.item()));
    return list;
  }

  @Override
  public @NotNull ItemStack getResultItem(@NotNull net.minecraft.core.RegistryAccess provider) {
    var disks = LootManager.disksForCycling();
    if (!disks.isEmpty()) return disks.get(0).copy();
    return ItemStack.EMPTY;
  }

  @Override
  public boolean isSpecial() {
    return false;
  }

  @Override
  public boolean canCraftInDimensions(int width, int height) {
    return width * height >= 2;
  }

  @Override
  public @NotNull NonNullList<ItemStack> getRemainingItems(@NotNull CraftingContainer input) {
    var remaining = super.getRemainingItems(input);
    for (int i = 0; i < input.getContainerSize(); i++) {
      var stack = input.getItem(i);
      if (Wrench.isWrench(stack)) {
        remaining.set(i, stack.copy());
      }
    }
    return remaining;
  }

  @Override
  public @NotNull RecipeSerializer<?> getSerializer() {
    return Serializer.INSTANCE;
  }

  public static class Serializer extends SimpleCraftingRecipeSerializer<LootDiskCyclingRecipe> {
    public static final Serializer INSTANCE = new Serializer();

    private Serializer() {
      super(LootDiskCyclingRecipe::new);
    }
  }
}
