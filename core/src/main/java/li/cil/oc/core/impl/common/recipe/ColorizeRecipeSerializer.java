package li.cil.oc.core.impl.common.recipe;

import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import org.jetbrains.annotations.NotNull;

public class ColorizeRecipeSerializer implements RecipeSerializer<ColorizeRecipe> {
  public static final ColorizeRecipeSerializer INSTANCE = new ColorizeRecipeSerializer();

  private ColorizeRecipeSerializer() {
  }

  @Override
  public @NotNull ColorizeRecipe fromJson(@NotNull ResourceLocation id, @NotNull JsonObject json) {
    var category = CraftingBookCategory.CODEC.byName(GsonHelper.getAsString(json, "category", null), CraftingBookCategory.MISC);
    var target = GsonHelper.getAsItem(json, "target");
    return new ColorizeRecipe(id, category, target, null);
  }

  @Override
  public ColorizeRecipe fromNetwork(@NotNull ResourceLocation id, @NotNull FriendlyByteBuf buffer) {
    var category = buffer.readEnum(CraftingBookCategory.class);
    var item = BuiltInRegistries.ITEM.byId(buffer.readVarInt());
    return new ColorizeRecipe(id, category, item, null);
  }

  @Override
  public void toNetwork(@NotNull FriendlyByteBuf buffer, @NotNull ColorizeRecipe recipe) {
    buffer.writeEnum(recipe.category());
    buffer.writeVarInt(BuiltInRegistries.ITEM.getId(recipe.targetItem));
  }
}
