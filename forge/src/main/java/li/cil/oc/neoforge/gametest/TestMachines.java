package li.cil.oc.neoforge.gametest;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import li.cil.oc.core.impl.common.blockentity.Microcontroller;
import li.cil.oc.core.impl.common.item.data.MicrocontrollerData;
import li.cil.oc.neoforge.common.init.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/**
 * Runs Lua on a microcontroller in a game test.
 */
final class TestMachines {
  private TestMachines() {
  }

  /**
   * Places a microcontroller built from the given parts, boots it with the program as its EEPROM and hands it to the
   * check once the program has reported (error("done:" ..): the machine stops with that error) or waits for a signal
   * with none queued (a program running on: its loop of computer.pullSignal), giving up once 100 ticks and 10 seconds
   * have gone by (tests using it need a timeout past that). A program that waits for a signal before its work is done
   * must report; any other error stops the machine and fails.
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
    later(helper, 5, () -> {
      final var mc = (Microcontroller) helper.getLevel().getBlockEntity(abs);
      mc.changeEEPROM(li.cil.oc.api.Items.registerEEPROM("test", program.apply(mc).getBytes(StandardCharsets.UTF_8), null, false));
      ((li.cil.oc.api.network.Connector) mc.snooperNode).changeBuffer(1000);
      helper.assertTrue(mc.machine().start(), "machine did not start");
      // (checked after 100 ticks, a quarter of a second at times, the boot and a few calls were not always through)
      whenWithin(helper, () -> settled(mc.machine()), 100, 10, () -> {
        final String error = String.valueOf(mc.machine().lastError());
        helper.assertTrue(mc.machine().isRunning() || error.contains("done:"), "program stopped: " + error); // (or reported)
        helper.assertTrue(settled(mc.machine()), "the program neither reported nor waited for a signal within 100 ticks and 10 seconds: "
          + ((li.cil.oc.core.impl.server.machine.MachineBase) mc.machine()).state());
        check.accept(mc);
      });
    });
  }

  // stopped (reported), or waiting for a signal with none queued (Sleeping until its next update with one queued)
  private static boolean settled(final li.cil.oc.api.machine.Machine machine) {
    return !machine.isRunning() || machine instanceof li.cil.oc.core.impl.server.machine.MachineBase m && !m.isExecuting()
      && m.state().peek() == li.cil.oc.core.impl.server.machine.MachineBase.State.Sleeping && noSignals(m);
  }

  private static boolean noSignals(final li.cil.oc.core.impl.server.machine.MachineBase machine) {
    try {
      final var signals = (java.util.Queue<?>) SIGNALS.get(machine);
      synchronized (signals) {
        return signals.isEmpty();
      }
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private static final Field SIGNALS = signalsField();

  private static Field signalsField() {
    try {
      final Field field = li.cil.oc.core.impl.server.machine.MachineBase.class.getDeclaredField("signals");
      field.setAccessible(true);
      return field;
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Places a robot facing south (as placed by a player looking south) with a tier 1 CPU, two tier 1 RAM, the given
   * parts and the program as its EEPROM, then boots it. After delay ticks the check gets the robot and what the program
   * reported: a program reports by ending with error("done:" .. result) (the machine stops with that error); null if it
   * had not. Tests using it (or runDrone) need a timeout past its wait of 10 seconds: a robot reports after half a second
   * to a second and a half, more while many boot, and the test server may tick 400 times a second.
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
    runRobot(helper, pos, List.of(), parts, setup, program, delay, check);
  }

  /** The same, with containers (in slots 1 to 3). */
  static void runRobot(final GameTestHelper helper, final BlockPos pos, final List<String> containers, final List<String> parts,
                       final Consumer<li.cil.oc.core.impl.common.blockentity.Robot> setup, final String program, final int delay,
                       final java.util.function.BiConsumer<li.cil.oc.core.impl.common.blockentity.Robot, String> check) {
    final var data = new li.cil.oc.core.impl.common.item.data.RobotData();
    data.name = "tester";
    for (final String container : containers) {
      data.containers.add(li.cil.oc.api.Items.get(container).createItemStack(1));
    }
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
    later(helper, 5, () -> {
      ((li.cil.oc.api.network.Connector) robot.machine().node()).changeBuffer(20000);
      helper.assertTrue(robot.machine().start(), "the robot did not start: " + robot.machine().lastError());
      later(helper, delay, () -> whenWithin(helper, () -> reported(program, robot.machine()), 100, 10, () -> {
        // (a check of what did not happen passed when the program had not got that far)
        helper.assertTrue(reported(program, robot.machine()), "the program did not report within " + (delay + 100) + " ticks and 10 seconds");
        final String error = robot.machine().lastError();
        final int at = error == null ? -1 : error.indexOf("done:");
        helper.assertTrue(error == null || at >= 0, "the program failed: " + error);
        check.accept(robot, at >= 0 ? error.substring(at + 5) : null);
      }));
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
    later(helper, 5, () -> {
      ((li.cil.oc.api.network.Connector) drone.machine().node()).changeBuffer(20000);
      helper.assertTrue(drone.start(), "the drone did not start: " + drone.machine().lastError());
      later(helper, delay, () -> whenWithin(helper, () -> reported(program, drone.machine()), 100, 10, () -> {
        helper.assertTrue(reported(program, drone.machine()), "the program did not report within " + (delay + 100) + " ticks and 10 seconds");
        final String error = drone.machine().lastError();
        final int at = error == null ? -1 : error.indexOf("done:");
        helper.assertTrue(error == null || at >= 0, "the program failed: " + error);
        check.accept(drone, at >= 0 ? error.substring(at + 5) : null);
      }));
    });
  }

  // whether a program that reports (error("done:" ..)) has, or stopped otherwise; one that runs on, or reports only when
  // asked (on a 'report' signal), is ready at once
  private static boolean reported(final String program, final li.cil.oc.api.machine.Machine machine) {
    return !reportsByItself(program) || machine.lastError() != null || !machine.isRunning();
  }

  private static boolean reportsByItself(final String program) {
    return program.contains("done:") && !program.contains("'report'");
  }

  /**
   * A stone roof over the area (at y 4): gravel of the terrain above the test grid (cleared 20 blocks up) falls in at
   * times, onto what a test placed or into where it drops or places things.
   */
  static void roof(final GameTestHelper helper) {
    for (int x = 0; x < 5; x++) {
      for (int z = 0; z < 5; z++) helper.setBlock(new BlockPos(x, 4, z), net.minecraft.world.level.block.Blocks.STONE);
    }
  }

  /**
   * Runs the cleanup once the test is over, passed or failed (a failed step ends the test without the steps after it,
   * where it let go of tickets, listeners, registrations - and the server runs on with the other tests); returns the
   * cleanup run once only, for the test to run itself.
   */
  static Runnable atEnd(final GameTestHelper helper, final Runnable cleanup) {
    final boolean[] done = {false};
    final Runnable once = () -> {
      if (done[0]) return;
      done[0] = true;
      cleanup.run();
    };
    testInfo(helper).addListener(new GameTestListener() {
      @Override
      public void testStructureLoaded(final GameTestInfo test) {
      }

      @Override
      public void testPassed(final GameTestInfo test) {
        safely(once);
      }

      @Override
      public void testFailed(final GameTestInfo test) {
        safely(once);
      }
    });
    return once;
  }

  // (thrown from a listener, it stopped the server's tick)
  private static void safely(final Runnable cleanup) {
    try {
      cleanup.run();
    } catch (RuntimeException e) {
      LogUtils.getLogger().warn("A test's cleanup failed", e);
    }
  }

  // the helper's test (found by type: the field is named differently outside the development environment)
  private static GameTestInfo testInfo(final GameTestHelper helper) {
    try {
      return (GameTestInfo) TEST_INFO.get(helper);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private static final Field TEST_INFO = testInfoField();

  private static Field testInfoField() {
    for (final Field field : GameTestHelper.class.getDeclaredFields()) {
      if (field.getType() == GameTestInfo.class) {
        field.setAccessible(true);
        return field;
      }
    }
    throw new IllegalStateException("the test helper holds no test");
  }

  /**
   * Runs then once ready holds, checking each tick, or after max ticks: the test server ticks far faster than 20 a
   * second while the machines run in real time (a program had not reported yet within 40 ticks while many booted).
   */
  static void when(final GameTestHelper helper, final java.util.function.BooleanSupplier ready, final int max, final Runnable then) {
    if (max <= 0 || ready.getAsBoolean()) then.run();
    else later(helper, 1, () -> when(helper, ready, max - 1, then));
  }

  /**
   * The same, giving up only once the seconds have gone by too, counted while the server ticks (at most 50 ms a tick):
   * at times the test server ticks hundreds of times a second (a robot had not moved within 160 ticks, a third of a
   * second).
   */
  static void whenWithin(final GameTestHelper helper, final java.util.function.BooleanSupplier ready, final int max, final double seconds, final Runnable then) {
    final long[] clock = {System.nanoTime(), 0}; // last tick, time counted
    final int[] ticks = {0};
    when(helper, () -> {
      final long now = System.nanoTime();
      clock[1] += Math.min(now - clock[0], 50_000_000L);
      clock[0] = now;
      return ready.getAsBoolean() || ++ticks[0] > max && clock[1] > seconds * 1e9;
    }, Integer.MAX_VALUE, then);
  }

  /**
   * Runs the task after the ticks, as helper.runAfterDelay, which a step of a test must not call: GameTestInfo walks a
   * fastutil hash map of the test's steps each tick, and a step put in while it walked - once that grew the map's table
   * (past 24 steps: onEachTick books one a tick) - had the walk skip steps, run them twice or throw. The task goes in at
   * the start of the next server tick, outside the walk, and runs in the test's tick as before (a waiting step checks
   * its condition there: one seen at the start of the tick could change in the level's tick before the step ran).
   */
  static void later(final GameTestHelper helper, final int delay, final Runnable task) {
    final long due = helper.getTick() + delay;
    synchronized (deferred) {
      if (!listening) { // (only once a test runs: the game tests ship in the jar)
        listening = true;
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, TickEvent.ServerTickEvent.class, TestMachines::putDeferred);
      }
      deferred.add(new Deferred(helper, testInfo(helper), due, task));
    }
  }

  private record Deferred(GameTestHelper helper, GameTestInfo test, long due, Runnable task) {
  }

  private static final List<Deferred> deferred = new ArrayList<>();
  private static boolean listening;

  private static void putDeferred(final TickEvent.ServerTickEvent event) {
    if (event.phase != TickEvent.Phase.START) return;
    final List<Deferred> due;
    synchronized (deferred) {
      if (deferred.isEmpty()) return;
      due = new ArrayList<>(deferred);
      deferred.clear();
    }
    for (final Deferred step : due) {
      // (a test over never runs its steps again)
      if (!step.test().isDone()) step.helper().runAtTickTime(Math.max(step.due(), step.helper().getTick() + 1), step.task());
    }
  }
}
