package li.cil.oc.core.impl.common.recipe;

import net.minecraft.util.GsonHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.FriendlyByteBuf;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import li.cil.oc.api.Items;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class OPPMRecipe extends CustomRecipe {
  private final List<Ingredient> ingredients;

  @SuppressWarnings("unused")
  public OPPMRecipe(ResourceLocation id, CraftingBookCategory category, List<Ingredient> ingredients) {
    super(id, category);
    this.ingredients = ingredients;
  }

  @Override
  public boolean matches(@NotNull CraftingContainer input, @NotNull Level level) {
    var remaining = new ArrayList<>(ingredients);
    outer:
    for (int i = 0; i < input.getContainerSize(); i++) {
      var stack = input.getItem(i);
      if (!stack.isEmpty()) {
        for (var it = remaining.iterator(); it.hasNext(); ) {
          if (it.next().test(stack)) {
            it.remove();
            continue outer;
          }
        }
        return false;
      }
    }
    return remaining.isEmpty();
  }

  @Override
  public @NotNull ItemStack assemble(@NotNull CraftingContainer input, @NotNull net.minecraft.core.RegistryAccess provider) {
    var info = Items.get("oppm");
    if (info != null) {
      return info.createItemStack(1);
    }
    return ItemStack.EMPTY;
  }

  @Override
  public @NotNull NonNullList<Ingredient> getIngredients() {
    var list = NonNullList.<Ingredient>createWithCapacity(ingredients.size());
    list.addAll(ingredients);
    return list;
  }

  @Override
  public @NotNull ItemStack getResultItem(@NotNull net.minecraft.core.RegistryAccess provider) {
    var info = Items.get("oppm");
    return info != null ? info.createItemStack(1) : ItemStack.EMPTY;
  }

  @Override
  public boolean isSpecial() {
    return false;
  }

  @Override
  public boolean canCraftInDimensions(int width, int height) {
    return width * height >= ingredients.size();
  }

  @Override
  public @NotNull RecipeSerializer<?> getSerializer() {
    return OPPMRecipeSerializer.INSTANCE;
  }

  @SuppressWarnings("unused")
  public static class OPPMRecipeSerializer implements RecipeSerializer<OPPMRecipe> {
    public static final OPPMRecipeSerializer INSTANCE = new OPPMRecipeSerializer();

    @Override
    public @NotNull OPPMRecipe fromJson(@NotNull ResourceLocation id, @NotNull JsonObject json) {
      var category = CraftingBookCategory.CODEC.byName(GsonHelper.getAsString(json, "category", null), CraftingBookCategory.MISC);
      var items = GsonHelper.getAsJsonArray(json, "ingredients");
      var ingredients = new ArrayList<Ingredient>(items.size());
      for (var item : items) {
        ingredients.add(Ingredient.fromJson(item, false));
      }
      return new OPPMRecipe(id, category, ingredients);
    }

    @Override
    public OPPMRecipe fromNetwork(@NotNull ResourceLocation id, @NotNull FriendlyByteBuf buffer) {
      var category = buffer.readEnum(CraftingBookCategory.class);
      int count = buffer.readVarInt();
      var ingredients = new ArrayList<Ingredient>(count);
      for (int i = 0; i < count; i++) {
        ingredients.add(Ingredient.fromNetwork(buffer));
      }
      return new OPPMRecipe(id, category, ingredients);
    }

    @Override
    public void toNetwork(@NotNull FriendlyByteBuf buffer, @NotNull OPPMRecipe recipe) {
      buffer.writeEnum(recipe.category());
      buffer.writeVarInt(recipe.ingredients.size());
      for (var ingredient : recipe.ingredients) {
        ingredient.toNetwork(buffer);
      }
    }
  }
}
