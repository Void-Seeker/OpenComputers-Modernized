package li.cil.oc.core.impl.common.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

public final class BlockTagIngredient {
  private BlockTagIngredient() {
  }

  public static final ResourceLocation TYPE_ID = new ResourceLocation("opencomputers", "block_tag");

  private static final String MARKER_PREFIX = "opencomputers:block_tag:";

  /**
   * Parses a recipe ingredient, additionally accepting {@code {"type": "opencomputers:block_tag", "tag": ...}}.
   */
  public static Ingredient fromJson(JsonElement json) {
    if (json.isJsonObject()) {
      JsonObject object = json.getAsJsonObject();
      if (TYPE_ID.toString().equals(GsonHelper.getAsString(object, "type", ""))) {
        return marker(TagKey.create(Registries.BLOCK, new ResourceLocation(GsonHelper.getAsString(object, "tag"))));
      }
    }
    return Ingredient.fromJson(json, false);
  }

  private static Ingredient marker(TagKey<Block> tag) {
    ItemStack marker = new ItemStack(Blocks.BARRIER);
    marker.setHoverName(Component.literal(MARKER_PREFIX + tag.location()));
    return Ingredient.of(marker);
  }

  @Nullable
  public static TagKey<Block> markerTag(Ingredient ingredient) {
    ItemStack[] items = ingredient.getItems();
    if (items.length != 1 || !items[0].is(Items.BARRIER)) return null;
    if (!items[0].hasCustomHoverName()) return null;
    Component name = items[0].getHoverName();
    if (!name.getString().startsWith(MARKER_PREFIX)) return null;
    return TagKey.create(Registries.BLOCK, new ResourceLocation(name.getString().substring(MARKER_PREFIX.length())));
  }

  @Nullable
  public static Ingredient expand(TagKey<Block> tag) {
    List<ItemStack> stacks = new ArrayList<>();
    for (Holder<Block> holder : BuiltInRegistries.BLOCK.getTagOrEmpty(tag)) {
      ItemStack stack = new ItemStack(holder.value());
      if (!stack.isEmpty()) {
        stacks.add(stack);
      }
    }
    return stacks.isEmpty() ? null : Ingredient.of(stacks.stream());
  }
}
