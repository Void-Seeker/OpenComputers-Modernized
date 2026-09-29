package li.cil.oc.core.impl.common.recipe;

import com.google.gson.JsonObject;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.jetbrains.annotations.NotNull;

public class ExtendedShapedRecipe extends ShapedRecipe {
  public ExtendedShapedRecipe(ShapedRecipe recipe) {
    super(recipe.getId(), recipe.getGroup(), recipe.category(), recipe.getWidth(), recipe.getHeight(),
      recipe.getIngredients(), recipe.getResultItem(null), recipe.showNotification());
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

  /**
   * Same format as vanilla shaped recipes, so this delegates to the vanilla serializer.
   */
  public static class Serializer implements RecipeSerializer<ExtendedShapedRecipe> {
    public static final Serializer INSTANCE = new Serializer();

    @Override
    public @NotNull ExtendedShapedRecipe fromJson(@NotNull ResourceLocation id, @NotNull JsonObject json) {
      return new ExtendedShapedRecipe(RecipeSerializer.SHAPED_RECIPE.fromJson(id, json));
    }

    @Override
    public ExtendedShapedRecipe fromNetwork(@NotNull ResourceLocation id, @NotNull FriendlyByteBuf buffer) {
      return new ExtendedShapedRecipe(RecipeSerializer.SHAPED_RECIPE.fromNetwork(id, buffer));
    }

    @Override
    public void toNetwork(@NotNull FriendlyByteBuf buffer, @NotNull ExtendedShapedRecipe recipe) {
      RecipeSerializer.SHAPED_RECIPE.toNetwork(buffer, recipe);
    }
  }
}
