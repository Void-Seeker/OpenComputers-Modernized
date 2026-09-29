package li.cil.oc.neoforge.common.init;

import li.cil.oc.core.impl.common.recipe.ColorizeRecipe;
import li.cil.oc.core.impl.common.recipe.ColorizeRecipeSerializer;
import li.cil.oc.core.impl.common.recipe.DecolorizeRecipe;
import li.cil.oc.core.impl.common.recipe.DecolorizeRecipeSerializer;
import li.cil.oc.core.impl.common.recipe.ExtendedShapedRecipe;
import li.cil.oc.core.impl.common.recipe.ExtendedShapelessOreRecipe;
import li.cil.oc.core.impl.common.recipe.LootDiskCyclingRecipe;
import li.cil.oc.core.impl.common.recipe.LuaBiosRecipe;
import li.cil.oc.core.impl.common.recipe.OPPMRecipe;
import li.cil.oc.core.impl.common.recipe.OpenOSRecipe;
import li.cil.oc.neoforge.OpenComputers;
import li.cil.oc.neoforge.common.recipe.ExtendedRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

public final class Recipes {
  public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
    DeferredRegister.create(Registries.RECIPE_SERIALIZER, OpenComputers.ID);

  @SuppressWarnings("unused")
  public static final RegistryObject<LuaBiosRecipe.LuaBiosRecipeSerializer> LUA_BIOS =
    RECIPE_SERIALIZERS.register("luabios", () -> LuaBiosRecipe.LuaBiosRecipeSerializer.INSTANCE);

  @SuppressWarnings("unused")
  public static final RegistryObject<LootDiskCyclingRecipe.Serializer> LOOT_DISK_CYCLING =
    RECIPE_SERIALIZERS.register("loot_disk_cycling", () -> LootDiskCyclingRecipe.Serializer.INSTANCE);

  @SuppressWarnings("unused")
  public static final RegistryObject<OpenOSRecipe.OpenOSRecipeSerializer> OPEN_OS =
    RECIPE_SERIALIZERS.register("openos", () -> OpenOSRecipe.OpenOSRecipeSerializer.INSTANCE);

  @SuppressWarnings("unused")
  public static final RegistryObject<OPPMRecipe.OPPMRecipeSerializer> OPP =
    RECIPE_SERIALIZERS.register("oppm", () -> OPPMRecipe.OPPMRecipeSerializer.INSTANCE);

  @SuppressWarnings("unused")
  public static final RegistryObject<ColorizeRecipeSerializer> COLORIZE =
    RECIPE_SERIALIZERS.register("colorize", () -> ColorizeRecipeSerializer.INSTANCE);

  @SuppressWarnings("unused")
  public static final RegistryObject<DecolorizeRecipeSerializer> DECOLORIZE =
    RECIPE_SERIALIZERS.register("decolorize", () -> DecolorizeRecipeSerializer.INSTANCE);

  @SuppressWarnings("unused")
  public static final RegistryObject<ExtendedShapelessOreRecipe.Serializer> SHAPELESS =
    RECIPE_SERIALIZERS.register("shapeless", () -> ExtendedShapelessOreRecipe.Serializer.INSTANCE);

  @SuppressWarnings("unused")
  public static final RegistryObject<ExtendedShapedRecipe.Serializer> SHAPED =
    RECIPE_SERIALIZERS.register("shaped", () -> ExtendedShapedRecipe.Serializer.INSTANCE);

  static {
    ColorizeRecipe.setSerializer(ColorizeRecipeSerializer.INSTANCE);
    DecolorizeRecipe.setSerializer(DecolorizeRecipeSerializer.INSTANCE);
    ExtendedRecipe.init();
  }

  private Recipes() {
  }
}
