package li.cil.oc.neoforge.gametest;

import li.cil.oc.neoforge.OpenComputers;
import li.cil.oc.neoforge.common.init.Blocks;
import li.cil.oc.neoforge.integration.Mods;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Smoke tests for the mod integrations used by the Reclamation - Hardcore Edition pack.
 * Each test passes trivially when its mod is not installed.
 */
@GameTestHolder(OpenComputers.ID)
@PrefixGameTestTemplate(false)
public final class IntegrationSmokeTests {
  private static final String EMPTY = "empty";

  private IntegrationSmokeTests() {
  }

  @GameTest(template = EMPTY)
  public static void integrationsDetected(final GameTestHelper helper) {
    for (final Mods.ModBase mod : new Mods.ModBase[]{Mods.AppliedEnergistics2, Mods.AppliedMekanistics, Mods.Mekanism, Mods.Create, Mods.Jade}) {
      helper.assertTrue(mod.isModAvailable() == ModList.get().isLoaded(mod.id()),
        "integration availability for " + mod.id() + " does not match the mod list");
    }
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void ae2FindsGridNodeHost(final GameTestHelper helper) {
    if (!ModList.get().isLoaded(Mods.IDs.AppliedEnergistics2)) {
      helper.succeed();
      return;
    }
    final BlockPos pos = new BlockPos(2, 2, 2);
    helper.setBlock(pos, Blocks.POWER_CONVERTER.get());
    helper.assertTrue(appeng.api.networking.GridHelper.getNodeHost(helper.getLevel(), helper.absolutePos(pos)) != null,
      "AE2 does not see the power converter as a grid node host");
    helper.succeed();
  }
}
