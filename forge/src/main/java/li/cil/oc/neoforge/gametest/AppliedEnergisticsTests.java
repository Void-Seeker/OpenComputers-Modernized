package li.cil.oc.neoforge.gametest;

import li.cil.oc.neoforge.OpenComputers;
import li.cil.oc.neoforge.common.init.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Applied Energistics 2 through an adapter (AE2 is loaded in the test server).
 */
@GameTestHolder(OpenComputers.ID)
@PrefixGameTestTemplate(false)
public final class AppliedEnergisticsTests {
  private static final String EMPTY = "empty";

  private AppliedEnergisticsTests() {
  }

  /**
   * An interface part on a cable, facing an adapter, is the adapter's me_interface (with the adapter asking the cable's
   * own face, the driver looked on the far face: no part there, and every network call failed).
   */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void adapterReachesAnInterfacePart(final GameTestHelper helper) {
    final BlockPos cable = new BlockPos(2, 2, 2);
    final BlockPos adapterPos = cable.west();
    final var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("ae2", "cable_interface"));
    helper.assertTrue(item instanceof appeng.api.parts.IPartItem<?>, "AE2's interface part is not here: " + item);
    final var part = appeng.api.parts.PartHelper.setPart(helper.getLevel(), helper.absolutePos(cable), Direction.WEST, null, (appeng.api.parts.IPartItem<?>) item);
    helper.assertTrue(part != null, "the interface part could not be placed");
    // a second interface on the far face: the one facing the adapter is the one to use
    final var far = appeng.api.parts.PartHelper.setPart(helper.getLevel(), helper.absolutePos(cable), Direction.EAST, null, (appeng.api.parts.IPartItem<?>) item);
    helper.assertTrue(far != null, "the second interface part could not be placed");
    helper.setBlock(adapterPos, Blocks.ADAPTER.get());
    helper.runAfterDelay(5, () -> {
      final var adapter = (li.cil.oc.core.impl.common.blockentity.Adapter) helper.getBlockEntity(adapterPos);
      adapter.neighborChanged(Direction.EAST);
      li.cil.oc.neoforge.integration.appeng.DriverPartInterface.Environment found = null;
      for (final var node : adapter.node().reachableNodes()) {
        if (node.host() instanceof li.cil.oc.core.impl.server.driver.CompoundBlockEnvironment compound) {
          for (final var env : compound.environments()) {
            if (env instanceof li.cil.oc.neoforge.integration.appeng.DriverPartInterface.Environment e) found = e;
          }
        }
        if (node.host() instanceof li.cil.oc.neoforge.integration.appeng.DriverPartInterface.Environment e) found = e;
      }
      helper.assertTrue(found != null, "the adapter made no me_interface for the cable");
      helper.assertTrue(found.tile() == part, "the me_interface uses " + found.tile() + ", not the interface facing the adapter " + part);
      helper.succeed();
    });
  }

  private static String compoundAddress(final li.cil.oc.core.impl.common.blockentity.Adapter adapter) {
    for (final var node : adapter.node().reachableNodes()) {
      if (node.host() instanceof li.cil.oc.core.impl.server.driver.CompoundBlockEnvironment) return node.address();
    }
    return null;
  }

  /**
   * A cable bus part swapped for another is a device of its own, whether swapped at once or through an empty face (the
   * same block id: the export bus took over the interface's address, and programs got "no such method").
   */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void adapterGivesASwappedPartItsOwnAddress(final GameTestHelper helper) {
    final BlockPos cable = new BlockPos(2, 2, 2);
    final BlockPos adapterPos = cable.west();
    final BlockPos abs = helper.absolutePos(cable);
    final var iface = (appeng.api.parts.IPartItem<?>) ForgeRegistries.ITEMS.getValue(new ResourceLocation("ae2", "cable_interface"));
    final var export = ForgeRegistries.ITEMS.getValue(new ResourceLocation("ae2", "export_bus"));
    helper.assertTrue(export instanceof appeng.api.parts.IPartItem<?>, "AE2's export bus is not here: " + export);
    helper.assertTrue(appeng.api.parts.PartHelper.setPart(helper.getLevel(), abs, Direction.WEST, null, iface) != null, "the interface part could not be placed");
    helper.setBlock(adapterPos, Blocks.ADAPTER.get());
    helper.runAfterDelay(5, () -> {
      final var adapter = (li.cil.oc.core.impl.common.blockentity.Adapter) helper.getBlockEntity(adapterPos);
      adapter.neighborChanged(Direction.EAST);
      final String first = compoundAddress(adapter);
      final var host = appeng.api.parts.PartHelper.getPartHost(helper.getLevel(), abs);
      host.removePartFromSide(Direction.WEST);
      appeng.api.parts.PartHelper.setPart(helper.getLevel(), abs, Direction.WEST, null, (appeng.api.parts.IPartItem<?>) export);
      adapter.neighborChanged(Direction.EAST);
      final String swapped = compoundAddress(adapter);
      host.removePartFromSide(Direction.WEST);
      adapter.neighborChanged(Direction.EAST);
      appeng.api.parts.PartHelper.setPart(helper.getLevel(), abs, Direction.WEST, null, iface);
      adapter.neighborChanged(Direction.EAST);
      final String back = compoundAddress(adapter);
      helper.assertTrue(first != null && swapped != null && !swapped.equals(first), "an export bus swapped in for the interface got its address " + swapped);
      helper.assertTrue(back != null && !back.equals(swapped), "an interface put back where the export bus was got the bus's address " + back);
      helper.succeed();
    });
  }
}
