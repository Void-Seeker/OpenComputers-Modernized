package li.cil.oc.neoforge.common.recipe;

import com.google.gson.JsonObject;
import li.cil.oc.core.impl.OCSettings;
import li.cil.oc.neoforge.OpenComputers;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.IConditionSerializer;
import org.jetbrains.annotations.NotNull;

public final class LootRecraftingCondition implements ICondition {
  public static final LootRecraftingCondition INSTANCE = new LootRecraftingCondition();

  public static final ResourceLocation ID = new ResourceLocation(OpenComputers.ID, "loot_recrafting");

  public static final IConditionSerializer<LootRecraftingCondition> SERIALIZER = new IConditionSerializer<>() {
    @Override
    public void write(final JsonObject json, final LootRecraftingCondition value) {
    }

    @Override
    public LootRecraftingCondition read(final JsonObject json) {
      return INSTANCE;
    }

    @Override
    public ResourceLocation getID() {
      return ID;
    }
  };

  private LootRecraftingCondition() {
  }

  @Override
  public ResourceLocation getID() {
    return ID;
  }

  @Override
  public boolean test(@NotNull IContext context) {
    var settings = OCSettings.get();
    return settings == null || settings.lootRecrafting;
  }

  @Override
  public String toString() {
    return ID.toString();
  }
}
