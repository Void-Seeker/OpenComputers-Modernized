package li.cil.oc.neoforge.gametest;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import li.cil.oc.api.network.Node;
import li.cil.oc.core.Constants;
import li.cil.oc.core.impl.common.blockentity.Case;
import li.cil.oc.core.impl.server.machine.MachineBase;
import li.cil.oc.core.impl.server.machine.MachineBase.State;
import li.cil.oc.neoforge.OpenComputers;
import li.cil.oc.neoforge.common.init.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * The machine core: restarts, saves and pauses against a running slice, a program taking out its own CPU, and nested
 * signal arguments (third audit). A slice is stood in for by holding the machine's execution lock on a test thread.
 */
@GameTestHolder(OpenComputers.ID)
@PrefixGameTestTemplate(false)
public final class Audit3MachineTests {
  private static final String EMPTY = "empty";
  private static final BlockPos AT = new BlockPos(2, 2, 2);
  // waits for signals; reports on 'ping'
  private static final String SLEEPER = "while true do if computer.pullSignal() == 'ping' then error('done:pong') end end\n";

  private Audit3MachineTests() {
  }

  private static ItemStack item(final String name) {
    return li.cil.oc.api.Items.get(name).createItemStack(1);
  }

  private static ItemStack eeprom(final String lua) {
    return li.cil.oc.api.Items.registerEEPROM("machine test", lua.getBytes(StandardCharsets.UTF_8), null, false);
  }

  /**
   * Runs then once ready holds or after the seconds, counted while the server ticks (the test server ticks far faster
   * than 20 a second, and machines run in real time; but a stalled server thread updates no machine).
   */
  private static void within(final GameTestHelper helper, final double seconds, final BooleanSupplier ready, final Runnable then) {
    final long[] clock = {System.nanoTime(), 0}; // last tick, time counted
    TestMachines.when(helper, () -> {
      final long now = System.nanoTime();
      clock[1] += Math.min(now - clock[0], 50_000_000L);
      clock[0] = now;
      return ready.getAsBoolean() || clock[1] > seconds * 1e9;
    }, 10_000_000, then);
  }

  @SuppressWarnings("unchecked")
  private static <T> T field(final Object owner, final String name) {
    try {
      final var field = MachineBase.class.getDeclaredField(name);
      field.setAccessible(true);
      return (T) field.get(owner);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void setField(final Object owner, final String name, final Object value) {
    try {
      final var field = MachineBase.class.getDeclaredField(name);
      field.setAccessible(true);
      field.set(owner, value);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private static Deque<State> stack(final MachineBase machine) {
    return field(machine, "state");
  }

  // waiting for a signal between slices
  private static boolean asleep(final MachineBase machine) {
    return !machine.isExecuting() && machine.state().peek() == State.Sleeping;
  }

  /** A creative case with the program as its EEPROM, handed over once the program waits for signals. */
  private static Case startCase(final GameTestHelper helper, final BlockPos pos, final String program, final Consumer<MachineBase> then) {
    helper.setBlock(pos, Blocks.CASE_CREATIVE.get());
    final var computer = (Case) helper.getBlockEntity(pos);
    for (final ItemStack part : List.of(item(Constants.ItemName.CPUTier1), item(Constants.ItemName.RAMTier2), eeprom(program))) {
      insert(helper, computer, part);
    }
    helper.runAfterDelay(5, () -> {
      final var machine = (MachineBase) computer.machine();
      helper.assertTrue(machine.start(), "the case did not start: " + machine.lastError());
      within(helper, 30, () -> asleep(machine), () -> {
        helper.assertTrue(asleep(machine), "the case's program did not start: " + machine.state() + " " + machine.lastError());
        then.accept(machine);
      });
    });
    return computer;
  }

  private static int insert(final GameTestHelper helper, final Container container, final ItemStack part) {
    int slot = -1;
    for (int i = 0; i < container.getContainerSize() && slot < 0; i++) {
      if (container.getItem(i).isEmpty() && container.canPlaceItem(i, part)) slot = i;
    }
    helper.assertTrue(slot >= 0, "no slot takes " + part);
    container.setItem(slot, part);
    return slot;
  }

  /** A slice in progress: the machine's execution lock held by another thread until it ends (at most 30 seconds). */
  private static final class Slice {
    private final CountDownLatch over = new CountDownLatch(1);
    private final Thread thread;

    Slice(final MachineBase machine) {
      final ReentrantLock lock = field(machine, "executionLock");
      final CountDownLatch held = new CountDownLatch(1);
      thread = new Thread(() -> {
        lock.lock();
        try {
          held.countDown();
          over.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        } finally {
          lock.unlock();
        }
      }, "test slice");
      thread.start();
      try {
        held.await();
      } catch (InterruptedException e) {
        throw new IllegalStateException(e);
      }
    }

    void end() {
      over.countDown();
      try {
        thread.join();
      } catch (InterruptedException e) {
        throw new IllegalStateException(e);
      }
    }
  }

  /** A node counting the messages of the given name it gets. */
  private static Node probe(final String name, final List<Object> got) {
    return li.cil.oc.api.Network.newNode(new li.cil.oc.api.network.Environment() {
      @Override
      public Node node() {
        return null;
      }

      @Override
      public void onConnect(final Node node) {
      }

      @Override
      public void onDisconnect(final Node node) {
      }

      @Override
      public void onMessage(final li.cil.oc.api.network.Message message) {
        if (name.equals(message.name())) got.add(message);
      }
    }, li.cil.oc.api.network.Visibility.Network).create();
  }

  private static CompoundTag save(final GameTestHelper helper, final MachineBase machine) {
    final var nbt = new CompoundTag();
    machine.save(nbt, helper.getLevel().registryAccess());
    return nbt;
  }

  // ----------------------------------------------------------------------- //

  /**
   * A restart asked for within a slice (a stop, then a start) closes the computer once the slice is over, once: each
   * tick until then sent computer.stopped (closing its files, ports, chunk loader) and wiped /tmp while nothing closed,
   * after waiting 10 ms for the slice.
   */
  @GameTest(template = EMPTY, timeoutTicks = 10_000_000)
  public static void restartWithinASliceHappensOnceAfterIt(final GameTestHelper helper) {
    startCase(helper, AT, SLEEPER, machine -> {
      final List<Object> stopped = new ArrayList<>();
      final Node probe = probe("computer.stopped", stopped);
      machine.node().connect(probe);
      final Slice slice = new Slice(machine);
      try {
        final Deque<State> stack = stack(machine);
        synchronized (stack) { // (as the slice's start)
          stack.clear();
          stack.push(State.Running);
        }
        helper.assertTrue(machine.stop() && machine.start(), "a stop and start within the slice were refused");
        helper.assertTrue(machine.state().peek() == State.Restarting, "a stop and start within the slice left " + machine.state());
        long fastest = Long.MAX_VALUE;
        for (int i = 0; i < 5; i++) {
          final long start = System.nanoTime();
          machine.update();
          fastest = Math.min(fastest, System.nanoTime() - start);
        }
        helper.assertTrue(fastest < 5_000_000, "each update within the slice waited " + fastest / 1_000_000 + " ms or more");
      } catch (RuntimeException | AssertionError e) {
        slice.end();
        probe.remove();
        throw e;
      }
      helper.runAfterDelay(10, () -> { // (the case updates it each tick too)
        try {
          helper.assertTrue(stopped.isEmpty(), "within the slice the computer was stopped " + stopped.size() + " times (computer.stopped)");
          helper.assertTrue(machine.state().peek() == State.Restarting, "within the slice the computer went " + machine.state());
        } catch (RuntimeException | AssertionError e) {
          probe.remove();
          throw e;
        } finally {
          slice.end(); // (its end leaves Restarting as it is)
        }
        within(helper, 30, () -> asleep(machine), () -> {
          probe.remove();
          helper.assertTrue(asleep(machine), "the computer did not restart after the slice: " + machine.state() + " " + machine.lastError());
          helper.assertTrue(stopped.size() == 1, "the restart stopped the computer " + stopped.size() + " times");
          helper.succeed();
        });
      });
    });
  }

  /**
   * A save while a slice runs, of a computer never saved whole, has its node, owners and state: it wrote nothing (back
   * with a new address, switched off, its owners gone).
   */
  @GameTest(template = EMPTY, timeoutTicks = 10_000_000)
  public static void busySavesKeepANewComputer(final GameTestHelper helper) {
    startCase(helper, AT, SLEEPER, machine -> {
      setField(machine, "lastSaved", null); // (as before its first full save)
      final Set<String> users = field(machine, "_users");
      synchronized (users) {
        users.add("oc-owner");
      }
      final CompoundTag saved;
      final Slice slice = new Slice(machine);
      try {
        saved = save(helper, machine);
      } finally {
        slice.end();
      }
      helper.assertTrue(machine.node().address().equals(saved.getCompound("node").getString("address")), "a busy save wrote the node " + saved.getCompound("node") + ", not " + machine.node().address());
      helper.assertTrue("oc-owner".equals(saved.getList("users", Tag.TAG_STRING).getString(0)), "a busy save wrote the owners " + saved.get("users"));
      helper.assertTrue(saved.getIntArray("state").length > 0, "a busy save wrote no state: " + saved);
      helper.succeed();
    });
  }

  /** A computer stopped since its last full save is saved stopped while its slice runs: it came back running. */
  @GameTest(template = EMPTY, timeoutTicks = 10_000_000)
  public static void busySavesOfAStoppedComputerAreStopped(final GameTestHelper helper) {
    startCase(helper, AT, SLEEPER, machine -> {
      save(helper, machine); // a full save while it runs
      final CompoundTag saved;
      final Slice slice = new Slice(machine);
      try {
        helper.assertTrue(machine.stop(), "the computer did not stop");
        saved = save(helper, machine);
      } finally {
        slice.end();
      }
      final int[] state = saved.getIntArray("state");
      helper.assertTrue(state.length > 0 && state[0] == State.Stopped.id, "a stopped computer was saved as " + Arrays.toString(state) + " (running after a reload)");
      helper.succeed();
    });
  }

  /** A computer started again since its last full save is not saved as that earlier run while a slice runs. */
  @GameTest(template = EMPTY, timeoutTicks = 10_000_000)
  public static void busySavesDoNotBringBackAnEarlierRun(final GameTestHelper helper) {
    startCase(helper, AT, SLEEPER, machine -> {
      save(helper, machine); // a full save of the first run
      final Object firstRun = field(machine, "lastSaved");
      final int firstCloses = field(machine, "lastSavedCloses");
      helper.assertTrue(machine.stop(), "the computer did not stop");
      within(helper, 10, () -> machine.state().peek() == State.Stopped, () -> {
        helper.assertTrue(machine.state().peek() == State.Stopped, "the computer did not stop: " + machine.state());
        helper.assertTrue(machine.start(), "the computer did not start again: " + machine.lastError());
        within(helper, 30, () -> asleep(machine), () -> {
          helper.assertTrue(asleep(machine), "the second run did not start: " + machine.state() + " " + machine.lastError());
          // (an autosave of the second run would be what a busy save writes: the first run's is the last one)
          setField(machine, "lastSaved", firstRun);
          setField(machine, "lastSavedCloses", firstCloses);
          final CompoundTag saved;
          final Slice slice = new Slice(machine);
          try {
            saved = save(helper, machine);
          } finally {
            slice.end();
          }
          final int[] state = saved.getIntArray("state");
          helper.assertTrue(state.length > 0 && state[0] == State.Stopped.id, "a busy save wrote the first run's state " + Arrays.toString(state) + " for the second");
          helper.succeed();
        });
      });
    });
  }

  /**
   * A slice that paused itself ends while the server thread resumes it from the pause: resuming is atomic. Between its
   * pop of the pause and its switch, the slice's end was undone - Running, with no slice to end it, for good.
   */
  @GameTest(template = EMPTY, timeoutTicks = 10_000_000)
  public static void resumingFromAPauseKeepsTheSlicesEnd(final GameTestHelper helper) {
    startCase(helper, AT, SLEEPER, machine -> {
      final Thread server = Thread.currentThread();
      final Thread[] sliceEnd = {null};
      // the stack, where the server thread's pop of the pause and look at what is under it lets the slice end
      final Deque<State> stack = new ArrayDeque<>() {
        private boolean armed = true, popped;

        @Override
        public State pop() {
          final State s = super.pop();
          popped = armed && s == State.Paused && Thread.currentThread() == server;
          return s;
        }

        @Override
        public State peek() {
          final State s = super.peek();
          if (popped && Thread.currentThread() == server) {
            armed = popped = false;
            final Deque<State> self = this;
            sliceEnd[0] = new Thread(() -> {
              synchronized (self) { // as run() once the slice returns a sleep
                if (self.peekFirst() == State.Running) {
                  self.removeFirst();
                  self.addFirst(State.Sleeping);
                } else if (self.peekFirst() == State.Paused) {
                  self.removeFirst();
                  self.removeFirst();
                  self.addFirst(State.Sleeping);
                  self.addFirst(State.Paused);
                }
              }
            }, "test slice end");
            sliceEnd[0].start();
            try {
              sliceEnd[0].join(300); // (waits for the state's lock, if the server thread holds it)
            } catch (InterruptedException e) {
              throw new IllegalStateException(e);
            }
          }
          return s;
        }
      };
      final Deque<State> current = stack(machine);
      synchronized (current) {
        // the slice paused itself in a direct call, and the pause is over
        stack.addLast(State.Paused);
        stack.addLast(State.Running);
        setField(machine, "state", stack);
        setField(machine, "remainingPause", 0);
      }
      within(helper, 10, () -> sliceEnd[0] != null && !sliceEnd[0].isAlive(), () -> {
        helper.assertTrue(sliceEnd[0] != null, "the server thread did not resume the computer: " + machine.state());
        machine.signal("ping");
        within(helper, 10, () -> String.valueOf(machine.lastError()).contains("done:"), () -> {
          helper.assertTrue(String.valueOf(machine.lastError()).contains("done:pong"), "the computer did not go on after the pause: "
            + machine.state() + " " + machine.lastError());
          helper.succeed();
        });
      });
    });
  }

  /**
   * A program swapping its own case's CPU into a chest (a transposer, in a synchronized call) gets the CPU there: the
   * close of its architecture, in a call, threw out of the case's inventory with the slot emptied - the CPU was gone.
   * The Lua state is closed once the call is over.
   */
  @GameTest(template = EMPTY, timeoutTicks = 10_000_000)
  public static void programsTakingTheirOwnCpuKeepIt(final GameTestHelper helper) {
    final BlockPos casePos = AT.west(), chestPos = AT.east();
    helper.setBlock(AT, Blocks.TRANSPOSER.get());
    helper.setBlock(chestPos, net.minecraft.world.level.block.Blocks.CHEST);
    helper.setBlock(casePos, Blocks.CASE_CREATIVE.get());
    final var computer = (Case) helper.getBlockEntity(casePos);
    final int cpuSlot = insert(helper, computer, item(Constants.ItemName.CPUTier1));
    insert(helper, computer, item(Constants.ItemName.RAMTier2));
    // the case is west of the transposer (side 4), the chest east (5)
    insert(helper, computer, eeprom("local tp = component.proxy(component.list('transposer')())\n"
      + "computer.pullSignal(0.1)\n"
      + "error('done:' .. tostring(tp.swap(4, 5, " + (cpuSlot + 1) + ", 1)))\n"));
    helper.runAfterDelay(5, () -> {
      final var machine = (MachineBase) computer.machine();
      helper.assertTrue(machine.start(), "the case did not start: " + machine.lastError());
      final var architecture = machine.architecture();
      within(helper, 30, () -> machine.state().peek() == State.Stopped, () -> {
        final Container chest = (Container) helper.getBlockEntity(chestPos);
        helper.assertTrue(machine.state().peek() == State.Stopped, "the program did not take its CPU: " + machine.state() + " " + machine.lastError());
        helper.assertTrue(computer.getItem(cpuSlot).isEmpty(), "the CPU is still in the case: " + machine.lastError());
        helper.assertTrue(li.cil.oc.api.Items.get(chest.getItem(0)) == li.cil.oc.api.Items.get(Constants.ItemName.CPUTier1),
          "the CPU taken from the case is not in the chest but " + chest.getItem(0) + " (" + machine.lastError() + ")");
        final List<?> replaced = field(machine, "replaced");
        helper.assertTrue(replaced.isEmpty(), "the replaced architecture was not closed");
        helper.assertTrue(architecture instanceof li.cil.oc.core.impl.server.machine.luac.NativeLuaArchitecture, "not native Lua: " + architecture);
        try {
          final var lua = li.cil.oc.core.impl.server.machine.luac.NativeLuaArchitecture.class.getDeclaredField("lua");
          lua.setAccessible(true);
          helper.assertTrue(lua.get(architecture) == null, "the Lua state of the CPU taken out was not closed");
        } catch (ReflectiveOperationException e) {
          throw new IllegalStateException(e);
        }
        helper.succeed();
      });
    });
  }

  /**
   * A table within itself pushed as a signal is refused (pushSignal is false): it overflowed the stack. Nested tables
   * still arrive.
   */
  @GameTest(template = EMPTY, timeoutTicks = 10_000_000)
  public static void cyclicSignalTablesAreRefused(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.CASE_CREATIVE.get());
    final var computer = (Case) helper.getBlockEntity(AT);
    for (final ItemStack part : List.of(item(Constants.ItemName.CPUTier1), item(Constants.ItemName.RAMTier2), eeprom("""
      local t = {n = 1}
      t.self = t
      local cyclic = computer.pushSignal("cyclic", t)
      local deep = {}
      for i = 1, 100 do deep = {deep} end
      local tooDeep = computer.pushSignal("deep", deep)
      local nested = computer.pushSignal("nested", {a = {b = 2}})
      local got
      repeat
        local s, v = computer.pullSignal(1)
        if s == "nested" then got = v.a.b end
      until got or computer.uptime() > 30
      error("done:" .. tostring(cyclic) .. "," .. tostring(tooDeep) .. "," .. tostring(nested) .. "," .. tostring(got == 2))
      """))) {
      insert(helper, computer, part);
    }
    helper.runAfterDelay(5, () -> {
      final var machine = computer.machine();
      helper.assertTrue(machine.start(), "the case did not start: " + machine.lastError());
      within(helper, 30, () -> !machine.isRunning(), () -> {
        helper.assertTrue(String.valueOf(machine.lastError()).contains("done:false,false,true,true"), "pushing a table within itself, one 100 deep and a nested one gave "
          + machine.lastError() + " (not done:false,false,true,true)");
        helper.succeed();
      });
    });
  }

  /**
   * A stop asked for within a slice does not hold up the server thread: each tick until the slice was over waited 10 ms
   * for it. The computer closes once the slice is over, once.
   */
  @GameTest(template = EMPTY, timeoutTicks = 10_000_000)
  public static void stopWithinASliceDoesNotWaitForIt(final GameTestHelper helper) {
    startCase(helper, AT, SLEEPER, machine -> {
      final List<Object> stopped = new ArrayList<>();
      final Node probe = probe("computer.stopped", stopped);
      machine.node().connect(probe);
      final Deque<State> stack = stack(machine);
      final Slice slice = new Slice(machine);
      try {
        synchronized (stack) { // (as the slice's start)
          stack.clear();
          stack.push(State.Running);
        }
        helper.assertTrue(machine.stop(), "a stop within the slice was refused");
        long fastest = Long.MAX_VALUE;
        for (int i = 0; i < 5; i++) {
          final long start = System.nanoTime();
          machine.update();
          fastest = Math.min(fastest, System.nanoTime() - start);
        }
        helper.assertTrue(fastest < 5_000_000, "each update within the slice waited " + fastest / 1_000_000 + " ms or more");
        helper.assertTrue(stopped.isEmpty() && machine.isExecuting(), "within the slice the computer went " + machine.state() + ", stopped " + stopped.size() + " times");
        synchronized (stack) { // (as the slice's end, once stopped)
          stack.clear();
          stack.push(State.Stopping);
        }
      } catch (RuntimeException | AssertionError e) {
        probe.remove();
        throw e;
      } finally {
        slice.end();
      }
      within(helper, 30, () -> machine.state().peek() == State.Stopped, () -> {
        probe.remove();
        helper.assertTrue(machine.state().peek() == State.Stopped, "the computer did not stop after the slice: " + machine.state());
        helper.assertTrue(stopped.size() == 1, "the stop closed the computer " + stopped.size() + " times");
        helper.succeed();
      });
    });
  }

  private static Object luaState(final li.cil.oc.api.machine.Architecture architecture) {
    try {
      final var lua = li.cil.oc.core.impl.server.machine.luac.NativeLuaArchitecture.class.getDeclaredField("lua");
      lua.setAccessible(true);
      return lua.get(architecture);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * A program removing its own block (a debug card's setBlock, a synchronized call) is closed once the call is over: the
   * close of its Lua state, in use by the call, threw "cannot close" into the world code removing the block, and the
   * state was never let go.
   */
  @GameTest(template = EMPTY, timeoutTicks = 10_000_000)
  public static void programsRemovingTheirOwnBlockAreClosed(final GameTestHelper helper) {
    TestMachines.runMicrocontroller(helper, AT, List.of(Constants.ItemName.CPUTier1, Constants.ItemName.RAMTier2, Constants.ItemName.DebugCard), """
      local d = component.proxy(component.list("debug")())
      while computer.pullSignal() ~= "go" do end
      d.getWorld().setBlock(math.floor(d.getX()), math.floor(d.getY()), math.floor(d.getZ()), "minecraft:air")
      error("done:still running")
      """, mc -> {
      final var machine = (MachineBase) mc.machine();
      final var architecture = machine.architecture();
      helper.assertTrue(architecture instanceof li.cil.oc.core.impl.server.machine.luac.NativeLuaArchitecture && luaState(architecture) != null,
        "not running native Lua: " + architecture);
      machine.signal("go");
      within(helper, 10, () -> luaState(architecture) == null, () -> {
        helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(AT)).isAir(), "the program did not remove its block: " + machine.lastError());
        helper.assertTrue(machine.state().peek() == State.Stopped, "the program removing its block is " + machine.state());
        helper.assertTrue(luaState(architecture) == null, "the Lua state of the program removing its own block was not closed (" + machine.lastError() + ")");
        helper.succeed();
      });
    });
  }

  /**
   * Under LuaJ a table within itself, as a signal or a call's argument, is an error, not a stack overflow (pushSignal
   * is false, as with native Lua): its conversion went on into the table without end. Nested tables still arrive.
   */
  @GameTest(template = EMPTY, timeoutTicks = 10_000_000)
  public static void luajRefusesCyclicTables(final GameTestHelper helper) {
    final var data = new li.cil.oc.core.impl.common.item.data.MicrocontrollerData();
    final ItemStack cpu = item(Constants.ItemName.CPUTier1);
    ((li.cil.oc.api.driver.item.MutableProcessor) li.cil.oc.api.Driver.driverFor(cpu)).setArchitecture(cpu, li.cil.oc.core.impl.server.machine.luaj.LuaJLuaArchitecture.class);
    data.components.add(cpu);
    data.components.add(item(Constants.ItemName.RAMTier2));
    data.components.add(ItemStack.EMPTY);
    helper.setBlock(AT, Blocks.MICROCONTROLLER.get());
    final var level = helper.getLevel();
    final BlockPos abs = helper.absolutePos(AT);
    Blocks.MICROCONTROLLER.get().setPlacedBy(level, abs, level.getBlockState(abs), net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(level), data.createItemStack());
    helper.runAfterDelay(5, () -> {
      final var mc = (li.cil.oc.core.impl.common.blockentity.Microcontroller) level.getBlockEntity(abs);
      mc.changeEEPROM(eeprom("""
        local t = {n = 1}
        t.self = t
        local cyclic = computer.pushSignal("cyclic", t)
        local deep = {}
        for i = 1, 100 do deep = {deep} end
        local tooDeep = computer.pushSignal("deep", deep)
        local nested = computer.pushSignal("nested", {a = {b = 2}})
        local ok, why = pcall(component.invoke, component.list("eeprom")(), "setLabel", t)
        local got
        repeat
          local s, v = computer.pullSignal(1)
          if s == "nested" then got = v.a.b end
        until got or computer.uptime() > 30
        error("done:" .. tostring(cyclic) .. "," .. tostring(tooDeep) .. "," .. tostring(nested) .. "," .. tostring(got == 2) .. "," .. tostring(ok) .. "," .. tostring(why))
        """));
      ((li.cil.oc.api.network.Connector) mc.snooperNode).changeBuffer(1000);
      final var machine = mc.machine();
      helper.assertTrue(machine.start(), "the microcontroller did not start: " + machine.lastError());
      within(helper, 30, () -> !machine.isRunning(), () -> {
        helper.assertTrue(machine.architecture() instanceof li.cil.oc.core.impl.server.machine.luaj.LuaJLuaArchitecture, "not LuaJ: " + machine.architecture());
        final String error = String.valueOf(machine.lastError());
        helper.assertTrue(error.contains("done:false,false,true,true,false,") && error.contains("table too large or nested too deeply"),
          "under LuaJ pushing a table within itself, one 100 deep, a nested one, and a call with one within itself gave " + error
            + " (not done:false,false,true,true,false,..table too large or nested too deeply)");
        helper.succeed();
      });
    });
  }
}
