package li.cil.oc.neoforge.gametest;

import java.util.ArrayList;
import java.util.List;
import li.cil.oc.neoforge.OpenComputers;
import li.cil.oc.neoforge.common.init.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Mekanism multiblock ports through an adapter: the methods of the multiblock as it is now.
 */
@GameTestHolder(OpenComputers.ID)
@PrefixGameTestTemplate(false)
public final class MekanismTests {
  private static final String EMPTY = "empty";

  private MekanismTests() {
  }

  private static Block mekanism(final String name) {
    return ForgeRegistries.BLOCKS.getValue(new ResourceLocation("mekanism", name));
  }

  private static String[] methodsOf(final li.cil.oc.core.impl.common.blockentity.Adapter adapter) {
    for (final var node : adapter.node().reachableNodes()) {
      if (node.host() instanceof li.cil.oc.neoforge.integration.mekanism.EnvironmentMekanismMachine machine) return machine.methods();
    }
    return null;
  }

  /**
   * An adapter placed at a dynamic tank's valve before the tank is complete offers the tank's methods once it forms
   * (they were bound when the adapter attached: "no such method" until it was broken and placed again).
   */
  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void adapterFollowsAMultiblockThatForms(final GameTestHelper helper) {
    final Block casing = mekanism("dynamic_tank");
    final Block valve = mekanism("dynamic_valve");
    helper.assertTrue(casing != null && valve != null && casing != net.minecraft.world.level.block.Blocks.AIR, "Mekanism's dynamic tank is not here");
    // the tank: x 1..3, y 1..3, z 1..3, its valve on the west face's centre, the adapter west of it
    final BlockPos valvePos = new BlockPos(1, 2, 2);
    final BlockPos adapterPos = valvePos.west();
    helper.setBlock(valvePos, valve);
    helper.setBlock(adapterPos, Blocks.ADAPTER.get());
    TestMachines.later(helper, 5, () -> {
      final var adapter = (li.cil.oc.core.impl.common.blockentity.Adapter) helper.getBlockEntity(adapterPos);
      adapter.neighborChanged(Direction.EAST);
      final List<BlockPos> shell = new ArrayList<>();
      for (int x = 1; x <= 3; x++) {
        for (int y = 1; y <= 3; y++) {
          for (int z = 1; z <= 3; z++) {
            final boolean inside = x == 2 && y == 2 && z == 2;
            final BlockPos pos = new BlockPos(x, y, z);
            if (!inside && !pos.equals(valvePos)) shell.add(pos);
          }
        }
      }
      for (final BlockPos pos : shell) helper.setBlock(pos, casing);
      TestMachines.later(helper, 60, () -> {
        final String[] methods = methodsOf(adapter);
        helper.assertTrue(methods != null, "the adapter has no Mekanism environment for the valve");
        helper.assertTrue(java.util.Arrays.asList(methods).contains("getTankCapacity"), "the formed tank's valve offers " + java.util.Arrays.toString(methods));
        helper.succeed();
      });
    });
  }
}
