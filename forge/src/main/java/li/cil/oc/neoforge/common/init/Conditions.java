package li.cil.oc.neoforge.common.init;

import li.cil.oc.neoforge.common.recipe.LootRecraftingCondition;
import net.minecraftforge.common.crafting.CraftingHelper;

public final class Conditions {
  private Conditions() {
  }

  /**
   * Registers OC's recipe condition serializers (Forge 1.20.1 has no registry for them).
   */
  public static void register() {
    CraftingHelper.register(LootRecraftingCondition.SERIALIZER);
  }
}
