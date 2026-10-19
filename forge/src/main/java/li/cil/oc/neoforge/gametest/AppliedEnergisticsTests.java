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

  private static boolean hasImportBus(final li.cil.oc.core.impl.common.blockentity.Adapter adapter) {
    for (final var node : adapter.node().reachableNodes()) {
      if (node.host() instanceof li.cil.oc.core.impl.server.driver.CompoundBlockEnvironment compound) {
        for (final var env : compound.environments()) {
          if (env instanceof li.cil.oc.neoforge.integration.appeng.DriverImportBus.Environment) return true;
        }
      }
    }
    return false;
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
      appeng.api.parts.PartHelper.getPartHost(helper.getLevel(), abs).removePartFromSide(Direction.WEST); // (the bus there now)
      adapter.neighborChanged(Direction.EAST);
      appeng.api.parts.PartHelper.setPart(helper.getLevel(), abs, Direction.WEST, null, iface);
      adapter.neighborChanged(Direction.EAST);
      final String back = compoundAddress(adapter);
      helper.assertTrue(first != null && swapped != null && !swapped.equals(first), "an export bus swapped in for the interface got its address " + swapped);
      helper.assertTrue(back != null && !back.equals(swapped), "an interface put back where the export bus was got the bus's address " + back);
      helper.succeed();
    });
  }

  /**
   * An interface part keeps its address while another part comes and goes elsewhere on the cable bus (the bus's driver
   * set and name changed: it got a new address each time).
   */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void adapterKeepsTheAddressWhenAnotherPartComesAndGoes(final GameTestHelper helper) {
    final BlockPos cable = new BlockPos(2, 2, 2);
    final BlockPos adapterPos = cable.west();
    final BlockPos abs = helper.absolutePos(cable);
    final var iface = (appeng.api.parts.IPartItem<?>) ForgeRegistries.ITEMS.getValue(new ResourceLocation("ae2", "cable_interface"));
    final var importBus = ForgeRegistries.ITEMS.getValue(new ResourceLocation("ae2", "import_bus"));
    helper.assertTrue(importBus instanceof appeng.api.parts.IPartItem<?>, "AE2's import bus is not here: " + importBus);
    helper.assertTrue(appeng.api.parts.PartHelper.setPart(helper.getLevel(), abs, Direction.WEST, null, iface) != null, "the interface part could not be placed");
    helper.setBlock(adapterPos, Blocks.ADAPTER.get());
    helper.runAfterDelay(5, () -> {
      final var adapter = (li.cil.oc.core.impl.common.blockentity.Adapter) helper.getBlockEntity(adapterPos);
      adapter.neighborChanged(Direction.EAST);
      final String first = compoundAddress(adapter);
      helper.assertTrue(appeng.api.parts.PartHelper.setPart(helper.getLevel(), abs, Direction.NORTH, null, (appeng.api.parts.IPartItem<?>) importBus) != null, "the import bus could not be placed");
      adapter.neighborChanged(Direction.EAST);
      final String withBus = compoundAddress(adapter);
      helper.assertTrue(hasImportBus(adapter), "the import bus did not come along: the drivers did not change");
      appeng.api.parts.PartHelper.getPartHost(helper.getLevel(), abs).removePartFromSide(Direction.NORTH);
      adapter.neighborChanged(Direction.EAST);
      final String withoutBus = compoundAddress(adapter);
      helper.assertTrue(!hasImportBus(adapter), "the import bus did not go");
      helper.assertTrue(first != null && first.equals(withBus), "with an import bus added elsewhere the interface got " + withBus + " instead of " + first);
      helper.assertTrue(first.equals(withoutBus), "with the import bus gone again the interface got " + withoutBus + " instead of " + first);
      helper.succeed();
    });
  }

  /**
   * An energy acceptor put where an interface was is a device of its own, and so is an interface put back where it was
   * (energy alone has no name to tell it by: it took over the interface's address, and passed it on).
   */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void adapterTellsAnEnergyAcceptorFromAnInterface(final GameTestHelper helper) {
    final BlockPos cable = new BlockPos(2, 2, 2);
    final BlockPos adapterPos = cable.west();
    final BlockPos abs = helper.absolutePos(cable);
    final var iface = (appeng.api.parts.IPartItem<?>) ForgeRegistries.ITEMS.getValue(new ResourceLocation("ae2", "cable_interface"));
    final var acceptor = ForgeRegistries.ITEMS.getValue(new ResourceLocation("ae2", "cable_energy_acceptor"));
    helper.assertTrue(acceptor instanceof appeng.api.parts.IPartItem<?>, "AE2's energy acceptor part is not here: " + acceptor);
    helper.assertTrue(appeng.api.parts.PartHelper.setPart(helper.getLevel(), abs, Direction.WEST, null, iface) != null, "the interface part could not be placed");
    helper.setBlock(adapterPos, Blocks.ADAPTER.get());
    helper.runAfterDelay(5, () -> {
      final var adapter = (li.cil.oc.core.impl.common.blockentity.Adapter) helper.getBlockEntity(adapterPos);
      adapter.neighborChanged(Direction.EAST);
      final String first = compoundAddress(adapter);
      final var host = appeng.api.parts.PartHelper.getPartHost(helper.getLevel(), abs);
      host.removePartFromSide(Direction.WEST);
      adapter.neighborChanged(Direction.EAST);
      helper.assertTrue(appeng.api.parts.PartHelper.setPart(helper.getLevel(), abs, Direction.WEST, null, (appeng.api.parts.IPartItem<?>) acceptor) != null, "the energy acceptor could not be placed");
      adapter.neighborChanged(Direction.EAST);
      final String energy = compoundAddress(adapter);
      appeng.api.parts.PartHelper.getPartHost(helper.getLevel(), abs).removePartFromSide(Direction.WEST);
      adapter.neighborChanged(Direction.EAST);
      appeng.api.parts.PartHelper.setPart(helper.getLevel(), abs, Direction.WEST, null, iface);
      adapter.neighborChanged(Direction.EAST);
      final String back = compoundAddress(adapter);
      helper.assertTrue(first != null && energy != null && !energy.equals(first), "an energy acceptor put where the interface was got its address " + energy);
      helper.assertTrue(back != null && !back.equals(energy), "an interface put back where the energy acceptor was got its address " + back);
      helper.succeed();
    });
  }

  private static Object identity(final li.cil.oc.core.impl.common.blockentity.Adapter adapter) {
    for (final var node : adapter.node().reachableNodes()) {
      if (node.host() instanceof li.cil.oc.core.impl.server.driver.CompoundBlockEnvironment compound) return compound.identity();
    }
    return null;
  }

  private static boolean hasExportBus(final li.cil.oc.core.impl.common.blockentity.Adapter adapter) {
    for (final var node : adapter.node().reachableNodes()) {
      if (node.host() instanceof li.cil.oc.core.impl.server.driver.CompoundBlockEnvironment compound) {
        for (final var env : compound.environments()) {
          if (env instanceof li.cil.oc.neoforge.integration.appeng.DriverExportBus.Environment) return true;
        }
      }
    }
    return false;
  }

  /**
   * An export bus put where an interface was, with an energy acceptor facing the adapter all along, is a device of its
   * own (energy alone was left between them, and it matched the export bus: it got the interface's address).
   */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void adapterDoesNotPassAnAddressOnThroughEnergy(final GameTestHelper helper) {
    final BlockPos cable = new BlockPos(2, 2, 2);
    final BlockPos adapterPos = cable.west();
    final BlockPos abs = helper.absolutePos(cable);
    final var acceptor = (appeng.api.parts.IPartItem<?>) ForgeRegistries.ITEMS.getValue(new ResourceLocation("ae2", "cable_energy_acceptor"));
    final var iface = (appeng.api.parts.IPartItem<?>) ForgeRegistries.ITEMS.getValue(new ResourceLocation("ae2", "cable_interface"));
    final var export = (appeng.api.parts.IPartItem<?>) ForgeRegistries.ITEMS.getValue(new ResourceLocation("ae2", "export_bus"));
    helper.assertTrue(appeng.api.parts.PartHelper.setPart(helper.getLevel(), abs, Direction.WEST, null, acceptor) != null, "the energy acceptor could not be placed");
    helper.assertTrue(appeng.api.parts.PartHelper.setPart(helper.getLevel(), abs, Direction.NORTH, null, iface) != null, "the interface could not be placed");
    helper.setBlock(adapterPos, Blocks.ADAPTER.get());
    helper.runAfterDelay(5, () -> {
      final var adapter = (li.cil.oc.core.impl.common.blockentity.Adapter) helper.getBlockEntity(adapterPos);
      adapter.neighborChanged(Direction.EAST);
      final String first = compoundAddress(adapter);
      final var firstIdentity = identity(adapter);
      appeng.api.parts.PartHelper.getPartHost(helper.getLevel(), abs).removePartFromSide(Direction.NORTH);
      adapter.neighborChanged(Direction.EAST);
      final var aloneIdentity = identity(adapter);
      helper.assertTrue(appeng.api.parts.PartHelper.setPart(helper.getLevel(), abs, Direction.NORTH, null, export) != null, "the export bus could not be placed");
      adapter.neighborChanged(Direction.EAST);
      final String then = compoundAddress(adapter);
      helper.assertTrue(hasExportBus(adapter), "the export bus did not come along");
      helper.assertTrue(first != null && then != null && !then.equals(first), "an export bus put where the interface was got its address " + then
        + " (" + firstIdentity + " -> " + aloneIdentity + " -> " + identity(adapter) + ")");
      helper.succeed();
    });
  }
}
