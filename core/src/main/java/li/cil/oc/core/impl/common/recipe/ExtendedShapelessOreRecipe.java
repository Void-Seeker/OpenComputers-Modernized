package li.cil.oc.core.impl.common.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

public class ExtendedShapelessOreRecipe extends ShapelessRecipe {
  private final List<BlockTagSlot> blockTagSlots;

  public ExtendedShapelessOreRecipe(ResourceLocation id, String group, CraftingBookCategory category, ItemStack result, NonNullList<Ingredient> ingredients) {
    super(id, group, category, result, ingredients);
    this.blockTagSlots = new ArrayList<>();
    for (int i = 0; i < ingredients.size(); i++) {
      TagKey<Block> tag = BlockTagIngredient.markerTag(ingredients.get(i));
      if (tag != null) {
        ingredients.set(i, Ingredient.EMPTY);
        blockTagSlots.add(new BlockTagSlot(i, tag));
      }
    }
  }

  private record BlockTagSlot(int index, TagKey<Block> tag) {
  }

  @Override
  public @NotNull NonNullList<Ingredient> getIngredients() {
    expandBlockTags();
    return super.getIngredients();
  }

  @Override
  public boolean matches(@NotNull CraftingContainer input, @NotNull Level level) {
    expandBlockTags();
    return super.matches(input, level);
  }

  private void expandBlockTags() {
    if (blockTagSlots.isEmpty()) return;
    for (int i = blockTagSlots.size() - 1; i >= 0; i--) {
      Ingredient expanded = BlockTagIngredient.expand(blockTagSlots.get(i).tag());
      if (expanded != null) {
        super.getIngredients().set(blockTagSlots.get(i).index(), expanded);
        blockTagSlots.remove(i);
      }
    }
  }

  @Override
  public @NotNull ItemStack assemble(@NotNull CraftingContainer inventory, @NotNull RegistryAccess registryAccess) {
    return ExtendedRecipe.addNBTToResult(this, super.assemble(inventory, registryAccess), inventory, registryAccess);
  }

  @Override
  public @NotNull NonNullList<ItemStack> getRemainingItems(@NotNull CraftingContainer inventory) {
    return ExtendedRecipe.getRecraftRemainingItems(inventory, super.getRemainingItems(inventory));
  }

  @Override
  public @NotNull RecipeSerializer<?> getSerializer() {
    return Serializer.INSTANCE;
  }

  public static class Serializer implements RecipeSerializer<ExtendedShapelessOreRecipe> {
    public static final Serializer INSTANCE = new Serializer();

    @Override
    public @NotNull ExtendedShapelessOreRecipe fromJson(@NotNull ResourceLocation id, @NotNull JsonObject json) {
      String group = GsonHelper.getAsString(json, "group", "");
      CraftingBookCategory category = CraftingBookCategory.CODEC.byName(GsonHelper.getAsString(json, "category", null), CraftingBookCategory.MISC);
      JsonArray items = GsonHelper.getAsJsonArray(json, "ingredients");
      NonNullList<Ingredient> ingredients = NonNullList.create();
      for (int i = 0; i < items.size(); i++) {
        Ingredient ingredient = BlockTagIngredient.fromJson(items.get(i));
        if (!ingredient.isEmpty()) ingredients.add(ingredient);
      }
      if (ingredients.isEmpty()) {
        throw new JsonParseException("No ingredients for shapeless recipe");
      } else if (ingredients.size() > 9) {
        throw new JsonParseException("Too many ingredients for shapeless recipe. The maximum is: 9");
      }
      ItemStack result = ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "result"));
      return new ExtendedShapelessOreRecipe(id, group, category, result, ingredients);
    }

    @Override
    public ExtendedShapelessOreRecipe fromNetwork(@NotNull ResourceLocation id, @NotNull FriendlyByteBuf buffer) {
      ShapelessRecipe recipe = RecipeSerializer.SHAPELESS_RECIPE.fromNetwork(id, buffer);
      return new ExtendedShapelessOreRecipe(id, recipe.getGroup(), recipe.category(), recipe.getResultItem(null), recipe.getIngredients());
    }

    @Override
    public void toNetwork(@NotNull FriendlyByteBuf buffer, @NotNull ExtendedShapelessOreRecipe recipe) {
      RecipeSerializer.SHAPELESS_RECIPE.toNetwork(buffer, recipe);
    }
  }
}
