package li.cil.oc.neoforge.gametest;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import li.cil.oc.core.impl.common.blockentity.Microcontroller;
import li.cil.oc.core.impl.common.item.data.MicrocontrollerData;
import li.cil.oc.neoforge.common.init.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayerFactory;

/**
 * Runs Lua on a microcontroller in a game test.
 */
final class TestMachines {
  private TestMachines() {
  }

  /**
   * Places a microcontroller built from the given parts, boots it with the program as its EEPROM and hands it to
   * the check five seconds later (tests using it need a longer timeout than the default 100 ticks). The program should end in an endless loop; an error stops the machine.
   */
  static void runMicrocontroller(final GameTestHelper helper, final BlockPos pos, final List<String> parts, final String program, final Consumer<Microcontroller> check) {
    runMicrocontroller(helper, pos, parts, mc -> program, check);
  }

  /** The same, with the program written once the microcontroller stands, e.g. to know which way it faces. */
  static void runMicrocontroller(final GameTestHelper helper, final BlockPos pos, final List<String> parts, final Function<Microcontroller, String> program, final Consumer<Microcontroller> check) {
    final var data = new MicrocontrollerData();
    for (final String part : parts) {
      data.components.add(li.cil.oc.api.Items.get(part).createItemStack(1));
    }
    data.components.add(ItemStack.EMPTY);
    helper.setBlock(pos, Blocks.MICROCONTROLLER.get());
    final BlockPos abs = helper.absolutePos(pos);
    Blocks.MICROCONTROLLER.get().setPlacedBy(helper.getLevel(), abs, helper.getLevel().getBlockState(abs), FakePlayerFactory.getMinecraft(helper.getLevel()), data.createItemStack());
    helper.runAfterDelay(5, () -> {
      final var mc = (Microcontroller) helper.getLevel().getBlockEntity(abs);
      mc.changeEEPROM(li.cil.oc.api.Items.registerEEPROM("test", program.apply(mc).getBytes(StandardCharsets.UTF_8), null, false));
      ((li.cil.oc.api.network.Connector) mc.snooperNode).changeBuffer(1000);
      helper.assertTrue(mc.machine().start(), "machine did not start");
      helper.runAfterDelay(100, () -> {
        helper.assertTrue(mc.machine().isRunning(), "program stopped: " + mc.machine().lastError());
        check.accept(mc);
      });
    });
  }

  /**
   * Places a robot facing south (as placed by a player looking south) with a tier 1 CPU, two tier 1 RAM, the given
   * parts and the program as its EEPROM, then boots it. After delay ticks the check gets the robot and what the program
   * reported: a program reports by ending with error("done:" .. result) (the machine stops with that error); null if it
   * had not.
   */
  static void runRobot(final GameTestHelper helper, final BlockPos pos, final List<String> parts, final String program, final int delay,
                       final java.util.function.BiConsumer<li.cil.oc.core.impl.common.blockentity.Robot, String> check) {
    runRobot(helper, pos, parts, robot -> {
    }, program, delay, check);
  }

  /** The same, with setup given the robot before it boots (its tool, its inventory). */
  static void runRobot(final GameTestHelper helper, final BlockPos pos, final List<String> parts,
                       final Consumer<li.cil.oc.core.impl.common.blockentity.Robot> setup, final String program, final int delay,
                       final java.util.function.BiConsumer<li.cil.oc.core.impl.common.blockentity.Robot, String> check) {
    final var data = new li.cil.oc.core.impl.common.item.data.RobotData();
    data.name = "tester";
    for (final String part : List.of(li.cil.oc.core.Constants.ItemName.CPUTier1, li.cil.oc.core.Constants.ItemName.RAMTier1, li.cil.oc.core.Constants.ItemName.RAMTier1)) {
      data.components.add(li.cil.oc.api.Items.get(part).createItemStack(1));
    }
    for (final String part : parts) {
      data.components.add(li.cil.oc.api.Items.get(part).createItemStack(1));
    }
    data.components.add(li.cil.oc.api.Items.registerEEPROM("robot test", program.getBytes(StandardCharsets.UTF_8), null, false));
    helper.setBlock(pos, Blocks.ROBOT.get());
    final BlockPos abs = helper.absolutePos(pos);
    final var placer = FakePlayerFactory.getMinecraft(helper.getLevel());
    placer.setYRot(0);
    placer.setXRot(0);
    Blocks.ROBOT.get().setPlacedBy(helper.getLevel(), abs, helper.getLevel().getBlockState(abs), placer, data.createItemStack());
    final var robot = ((li.cil.oc.core.impl.common.blockentity.RobotProxy) helper.getLevel().getBlockEntity(abs)).robot;
    setup.accept(robot);
    helper.runAfterDelay(5, () -> {
      ((li.cil.oc.api.network.Connector) robot.machine().node()).changeBuffer(20000);
      helper.assertTrue(robot.machine().start(), "the robot did not start: " + robot.machine().lastError());
      helper.runAfterDelay(delay, () -> {
        final String error = robot.machine().lastError();
        final int at = error == null ? -1 : error.indexOf("done:");
        helper.assertTrue(error == null || at >= 0, "the program failed: " + error);
        check.accept(robot, at >= 0 ? error.substring(at + 5) : null);
      });
    });
  }

  /**
   * Spawns a drone (tier 1 CPU, tier 1 RAM, the given parts, the program as its EEPROM) at the centre of pos, as its item
   * does, and boots it; after delay ticks the check gets the drone and what the program reported (error("done:" ..)).
   */
  static void runDrone(final GameTestHelper helper, final BlockPos pos, final List<String> parts,
                       final Consumer<li.cil.oc.core.impl.common.entity.Drone> setup, final String program, final int delay,
                       final java.util.function.BiConsumer<li.cil.oc.core.impl.common.entity.Drone, String> check) {
    final var data = new li.cil.oc.core.impl.common.item.data.DroneData();
    data.name = "tester";
    for (final String part : List.of(li.cil.oc.core.Constants.ItemName.CPUTier1, li.cil.oc.core.Constants.ItemName.RAMTier1)) {
      data.components.add(li.cil.oc.api.Items.get(part).createItemStack(1));
    }
    for (final String part : parts) {
      data.components.add(li.cil.oc.api.Items.get(part).createItemStack(1));
    }
    data.components.add(li.cil.oc.api.Items.registerEEPROM("drone test", program.getBytes(StandardCharsets.UTF_8), null, false));
    final var level = helper.getLevel();
    final var drone = new li.cil.oc.core.impl.common.entity.Drone(li.cil.oc.core.impl.common.entity.Drone.getEntityType(), level);
    drone.initializeAfterPlacement(data.createItemStack(), null, helper.absoluteVec(net.minecraft.world.phys.Vec3.atCenterOf(pos)));
    level.addFreshEntity(drone);
    setup.accept(drone);
    helper.runAfterDelay(5, () -> {
      ((li.cil.oc.api.network.Connector) drone.machine().node()).changeBuffer(20000);
      helper.assertTrue(drone.start(), "the drone did not start: " + drone.machine().lastError());
      helper.runAfterDelay(delay, () -> {
        final String error = drone.machine().lastError();
        final int at = error == null ? -1 : error.indexOf("done:");
        helper.assertTrue(error == null || at >= 0, "the program failed: " + error);
        check.accept(drone, at >= 0 ? error.substring(at + 5) : null);
      });
    });
  }
}
