package li.cil.oc.neoforge.gametest;

import java.util.List;
import java.util.function.Consumer;
import li.cil.oc.core.Constants;
import li.cil.oc.core.impl.common.blockentity.Microcontroller;
import li.cil.oc.neoforge.OpenComputers;
import li.cil.oc.neoforge.common.init.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Computers come back from a reload as they were, tell the time of day and let go of what they no longer run.
 */
@GameTestHolder(OpenComputers.ID)
@PrefixGameTestTemplate(false)
public final class MachineTests {
  private static final String EMPTY = "empty";
  private static final BlockPos AT = new BlockPos(2, 2, 2);
  private static final List<String> PARTS = List.of(Constants.ItemName.CPUTier1, Constants.ItemName.RAMTier2);

  private MachineTests() {
  }

  /** What ChunkMap.scheduleUnload does to a microcontroller, then the chunk coming back: then gets the new one. */
  private static void reload(final GameTestHelper helper, final Microcontroller mc, final Consumer<Microcontroller> then) {
    final BlockPos abs = mc.getBlockPos();
    mc.markUnloading();
    final var saved = mc.saveWithoutMetadata();
    helper.getLevel().removeBlockEntity(abs);
    helper.runAfterDelay(3, () -> {
      final var reloaded = (Microcontroller) helper.getLevel().getBlockEntity(abs);
      helper.assertTrue(reloaded != null && reloaded != mc, "no fresh block entity after the removal");
      reloaded.load(saved);
      then.accept(reloaded);
    });
  }

  /** A reloaded computer gets no component_added for the components it had (OpenOS mounted every drive again). */
  @GameTest(template = EMPTY, timeoutTicks = 10_000_000)
  public static void reloadedComputerKnowsItsComponents(final GameTestHelper helper) {
    TestMachines.runMicrocontroller(helper, AT, PARTS,
      "while true do local n = computer.pullSignal(1) if n == 'component_added' then error('component_added after a reload') "
        + "elseif n == 'report' then error('done:') end end\n",
      // its network formed again, then asked to report: signals queued since the reload come first (a check after 60
      // ticks alone passed while the program had not got to them)
      mc -> reload(helper, mc, reloaded -> helper.runAfterDelay(60, () -> {
        reloaded.machine().signal("report");
        TestMachines.whenWithin(helper, () -> reloaded.machine().lastError() != null, 60, 10, () -> {
          helper.assertTrue(String.valueOf(reloaded.machine().lastError()).contains("done:"), "the reloaded computer "
            + (reloaded.machine().isRunning() ? "did not report" : "stopped: " + reloaded.machine().lastError()));
          helper.succeed();
        });
      })));
  }

  /** A signal waiting at a save keeps its integer arguments (they came back nil). */
  @GameTest(template = EMPTY, timeoutTicks = 10_000_000)
  public static void pendingSignalsKeepIntegers(final GameTestHelper helper) {
    TestMachines.runMicrocontroller(helper, AT, PARTS,
      "while true do local n, v = computer.pullSignal(1) if n == 't' then error('done:' .. tostring(v)) end end\n",
      mc -> {
        mc.machine().signal("t", 7);
        // (its machine is loaded on its first tick: stopped till then)
        reload(helper, mc, reloaded -> TestMachines.whenWithin(helper, () -> reloaded.machine().lastError() != null, 60, 10, () -> {
          helper.assertTrue(String.valueOf(reloaded.machine().lastError()).contains("done:7"), "the signal's argument came back as " + reloaded.machine().lastError()
            + " (running " + reloaded.machine().isRunning() + ", " + ((li.cil.oc.core.impl.server.machine.MachineBase) reloaded.machine()).state() + ")");
          helper.succeed();
        }));
      });
  }

  /** os.date tells the hour of the day clock (it followed the total game time: wrong after sleeping or /time set). */
  @GameTest(template = EMPTY, timeoutTicks = 10_000_000)
  public static void osDateFollowsTheDayClock(final GameTestHelper helper) {
    TestMachines.runMicrocontroller(helper, AT, PARTS,
      "while true do local n = computer.pullSignal(1) if n == 'report' then error('done:' .. os.date('*t').hour) end end\n",
      mc -> {
        final var level = helper.getLevel();
        final long day = ((level.getGameTime() + 12000) / 1000) * 1000; // an hour away from the game time's, at its start
        level.setDayTime(day);
        final long expected = ((day + 6000) / 1000) % 24;
        helper.runAfterDelay(2, () -> {
          mc.machine().signal("report");
          TestMachines.whenWithin(helper, () -> !mc.machine().isRunning(), 20, 10, () -> {
            helper.assertTrue(String.valueOf(mc.machine().lastError()).contains("done:" + expected), "at day time " + day + " os.date says " + mc.machine().lastError() + ", expected hour " + expected);
            helper.succeed();
          });
        });
      });
  }

  /** Taking the CPU out of a running case closes its Lua state (it was replaced without being closed: a leak). */
  @GameTest(template = EMPTY, timeoutTicks = 300)
  public static void removedCpuClosesItsLuaState(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.CASE_TIER_1.get());
    final var computer = (li.cil.oc.core.impl.common.blockentity.Case) helper.getBlockEntity(AT);
    int cpuSlot = -1;
    for (final ItemStack part : List.of(li.cil.oc.api.Items.get(Constants.ItemName.CPUTier1).createItemStack(1), li.cil.oc.api.Items.get(Constants.ItemName.RAMTier2).createItemStack(1),
      li.cil.oc.api.Items.registerEEPROM("idle", "while true do computer.pullSignal(1) end\n".getBytes(java.nio.charset.StandardCharsets.UTF_8), null, false))) {
      int slot = -1;
      for (int i = 0; i < computer.getContainerSize() && slot < 0; i++) {
        if (computer.getItem(i).isEmpty() && computer.canPlaceItem(i, part)) slot = i;
      }
      helper.assertTrue(slot >= 0, "no case slot takes " + part);
      computer.setItem(slot, part);
      if (cpuSlot < 0) cpuSlot = slot;
    }
    final int cpu = cpuSlot;
    // started once it can be (its node joins the network a little later, later still while all tests start theirs),
    // the CPU taken out once its Lua state is up
    final boolean[] started = {false};
    final li.cil.oc.api.machine.Architecture[] running = {null};
    final int[] ticks = {0};
    helper.onEachTick(() -> {
      if (running[0] != null || ticks[0]++ > 180) return;
      if (!started[0]) {
        ((li.cil.oc.api.network.Connector) computer.machine().node()).changeBuffer(1000);
        started[0] = computer.machine().start();
        return;
      }
      final var architecture = ((li.cil.oc.core.impl.server.machine.MachineBase) computer.machine()).architecture();
      if (architecture != null && architecture.isInitialized()) {
        running[0] = architecture;
        computer.setItem(cpu, ItemStack.EMPTY);
      }
    });
    helper.runAfterDelay(210, () -> {
      helper.assertTrue(started[0], "the case never started: " + computer.machine().lastError());
      helper.assertTrue(running[0] != null, "the case never ran a Lua state: " + computer.machine().lastError());
      helper.assertTrue(!running[0].isInitialized(), "the Lua state of the CPU taken out was never closed");
      helper.succeed();
    });
  }

  /**
   * A machine stopped, closed by its own update and started again in the same tick keeps running (the close still
   * scheduled from the stop ran at the tick's end and ended the new start).
   */
  @GameTest(template = EMPTY, timeoutTicks = 10_000_000)
  public static void machineStartedAgainAfterItsCloseKeepsRunning(final GameTestHelper helper) {
    TestMachines.runMicrocontroller(helper, AT, PARTS, "while true do computer.pullSignal(1) end\n", mc -> {
      final var machine = mc.machine();
      machine.stop(); // stopping, its close scheduled for the tick's end
      machine.update(); // its own update closes it first
      helper.assertTrue(!machine.isRunning(), "the stopped machine runs");
      helper.assertTrue(machine.start(), "the machine did not start again: " + machine.lastError());
      helper.runAfterDelay(3, () -> {
        helper.assertTrue(machine.isRunning(), "the machine started again was closed at the tick's end: " + machine.lastError());
        helper.succeed();
      });
    });
  }
}
