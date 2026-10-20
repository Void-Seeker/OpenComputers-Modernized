package li.cil.oc.neoforge.gametest;

import com.mojang.authlib.GameProfile;
import java.io.File;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import li.cil.oc.core.Constants;
import li.cil.oc.core.impl.common.blockentity.Case;
import li.cil.oc.core.impl.common.blockentity.Rack;
import li.cil.oc.core.impl.server.component.ServerBase;
import li.cil.oc.core.impl.server.machine.ArgumentsImpl;
import li.cil.oc.core.impl.server.machine.MachineBase;
import li.cil.oc.core.impl.server.machine.luac.NativeLuaArchitecture;
import li.cil.oc.core.impl.util.PackedColor;
import li.cil.oc.neoforge.OpenComputers;
import li.cil.oc.neoforge.common.init.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Hosts: what a running case, robot and racked server send and save, the Lua memory limit and factories, what is kept
 * after its world, screens, holograms and GPU buffers that broke on odd values.
 */
@GameTestHolder(OpenComputers.ID)
@PrefixGameTestTemplate(false)
public final class Audit3HostTests {
  private static final String EMPTY = "empty";
  private static final BlockPos AT = new BlockPos(2, 2, 2);
  private static final String BUSY = "while true do computer.pullSignal(0) end\n";

  private Audit3HostTests() {
  }

  private static li.cil.oc.api.machine.Context context() {
    return (li.cil.oc.api.machine.Context) java.lang.reflect.Proxy.newProxyInstance(li.cil.oc.api.machine.Context.class.getClassLoader(),
      new Class<?>[]{li.cil.oc.api.machine.Context.class}, (proxy, method, args) -> method.getReturnType() == boolean.class ? false : null);
  }

  private static ItemStack item(final String name) {
    return li.cil.oc.api.Items.get(name).createItemStack(1);
  }

  private static ItemStack eeprom(final String lua) {
    return li.cil.oc.api.Items.registerEEPROM("host test", lua.getBytes(java.nio.charset.StandardCharsets.UTF_8), null, false);
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

  private static void insert(final GameTestHelper helper, final net.minecraft.world.Container container, final ItemStack part) {
    int slot = -1;
    for (int i = 0; i < container.getContainerSize() && slot < 0; i++) {
      if (container.getItem(i).isEmpty() && container.canPlaceItem(i, part)) slot = i;
    }
    helper.assertTrue(slot >= 0, "no slot takes " + part);
    container.setItem(slot, part);
  }

  /** A creative case counting its own save marks (setChanged). */
  public static final class CountingCase extends li.cil.oc.neoforge.common.blockentity.CaseTile {
    int changes;

    CountingCase(final BlockPos pos, final BlockState state) {
      super(pos, state, 3);
    }

    @Override
    public void setChanged() {
      changes++;
      super.setChanged();
    }
  }

  /** A rack counting its setChanged, which sends its whole inventory to the clients. */
  public static final class CountingRack extends Rack {
    int changes;

    CountingRack(final BlockPos pos, final BlockState state) {
      super(pos, state);
    }

    @Override
    public void setChanged() {
      changes++;
      super.setChanged();
    }
  }

  private static <T> T component(final Iterable<li.cil.oc.api.network.ManagedEnvironment> components, final Class<T> type) {
    for (final var env : components) {
      if (type.isInstance(env)) return type.cast(env);
    }
    throw new IllegalStateException("no " + type.getSimpleName());
  }

  // ---- what running hosts send and save ----

  /**
   * A running case is marked for saving only (as OC): each slice sent the whole case to the clients and recomputed its
   * machine's parts.
   */
  @GameTest(template = EMPTY, timeoutTicks = 10_000_000)
  public static void runningCaseIsOnlyMarkedForSaving(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.CASE_CREATIVE.get());
    final BlockPos abs = helper.absolutePos(AT);
    final var computer = new CountingCase(abs, helper.getLevel().getBlockState(abs));
    helper.getLevel().setBlockEntity(computer);
    for (final ItemStack part : List.of(item(Constants.ItemName.CPUTier1), item(Constants.ItemName.RAMTier2), eeprom(BUSY))) {
      insert(helper, computer, part);
    }
    TestMachines.later(helper, 5, () -> {
      helper.assertTrue(computer.machine().start(), "the case did not start: " + computer.machine().lastError());
      within(helper, 10, () -> computer.machine().architecture() != null && computer.machine().architecture().isInitialized(), () -> {
        helper.assertTrue(computer.machine().isRunning(), "the case stopped: " + computer.machine().lastError());
        computer.changes = 0;
        within(helper, 1, () -> false, () -> {
          helper.assertTrue(computer.machine().isRunning(), "the case stopped: " + computer.machine().lastError());
          helper.assertTrue(computer.changes < 5, "a running case was set changed (and sent to its clients) " + computer.changes + " times in a second");
          helper.succeed();
        });
      });
    });
  }

  /** A host's update packet leaves its unmanaged drives alone (each one gzipped the disk and wrote it out). */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void updatePacketDoesNotWriteDrives(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.CASE_CREATIVE.get());
    final var computer = (Case) helper.getBlockEntity(AT);
    final ItemStack hdd = item(Constants.ItemName.HDDTier1);
    li.cil.oc.core.impl.common.item.data.DriveData.setUnmanaged(hdd, true);
    insert(helper, computer, hdd);
    TestMachines.later(helper, 5, () -> {
      final var drive = component(computer.installedComponents(), li.cil.oc.core.impl.server.component.Drive.class);
      helper.assertTrue(drive.node().address() != null, "the drive has no address");
      final File file;
      try {
        final var savePath = li.cil.oc.core.impl.server.component.Drive.class.getDeclaredMethod("savePath");
        savePath.setAccessible(true);
        file = (File) savePath.invoke(drive);
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException(e);
      }
      helper.assertTrue(!file.exists() || file.delete(), "could not delete " + file);
      computer.getUpdateTag();
      helper.assertTrue(!file.exists(), "the case's update packet wrote its drive's contents out");
      computer.saveWithoutMetadata();
      helper.assertTrue(file.exists(), "saving the case did not write its drive's contents out");
      helper.succeed();
    });
  }

  // whether the block at the position is to be sent to the clients with the chunk's next changes (sendBlockUpdated)
  private static boolean toBeSent(final net.minecraft.server.level.ServerLevel level, final BlockPos pos) {
    try {
      final var visible = net.minecraft.server.level.ChunkMap.class.getDeclaredMethod("getVisibleChunkIfPresent", long.class);
      visible.setAccessible(true);
      final var holder = visible.invoke(level.getChunkSource().chunkMap, net.minecraft.world.level.ChunkPos.asLong(pos));
      if (holder == null) return false;
      final var changed = net.minecraft.server.level.ChunkHolder.class.getDeclaredField("changedBlocksPerSection");
      changed.setAccessible(true);
      final var sections = (it.unimi.dsi.fastutil.shorts.ShortSet[]) changed.get(holder);
      final var section = sections[level.getSectionIndex(pos.getY())];
      return section != null && section.contains(net.minecraft.core.SectionPos.sectionRelativePos(pos));
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  /** A robot's machine marks it on every slice: saved only (it was sent whole to its clients each time). */
  @GameTest(template = EMPTY, timeoutTicks = 10_000_000)
  public static void runningRobotIsOnlyMarkedForSaving(final GameTestHelper helper) {
    TestMachines.runRobot(helper, AT, List.of(), BUSY, 20, (robot, result) -> {
      helper.assertTrue(robot.machine().isRunning(), "the robot stopped: " + robot.machine().lastError());
      try {
        final var field = li.cil.oc.core.impl.common.blockentity.RobotBase.class.getDeclaredField("saveScheduled");
        field.setAccessible(true);
        field.setBoolean(robot, false);
        robot.markChanged(); // (as its machine does)
        helper.assertTrue(field.getBoolean(robot), "a robot marked changed is not marked to be saved");
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException(e);
      }
      // (looked at after the block entities ticked, before the chunk's changes go out)
      final BlockPos abs = helper.absolutePos(AT);
      final int[] sent = {0};
      within(helper, 1, () -> {
        if (toBeSent(helper.getLevel(), abs)) sent[0]++;
        return false;
      }, () -> {
        helper.assertTrue(robot.machine().isRunning(), "the robot stopped: " + robot.machine().lastError());
        helper.assertTrue(sent[0] < 5, "a running robot was sent to its clients " + sent[0] + " times in a second");
        helper.succeed();
      });
    });
  }

  /**
   * A running racked server marks the rack for saving only: its slot's mark sent the mountable data, the rack's whole
   * inventory (and neighbour updates) every tick.
   */
  @GameTest(template = EMPTY, timeoutTicks = 10_000_000)
  public static void runningRackedServerIsOnlyMarkedForSaving(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.RACK.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    final BlockPos abs = helper.absolutePos(AT);
    final var rack = new CountingRack(abs, helper.getLevel().getBlockState(abs));
    helper.getLevel().setBlockEntity(rack);
    rack.setItem(0, item(Constants.ItemName.ServerTier1));
    final var server = (ServerBase) rack.getMountable(0);
    helper.assertTrue(server != null, "the rack made no server mountable");
    for (final ItemStack part : List.of(item(Constants.ItemName.CPUTier1), item(Constants.ItemName.RAMTier2), eeprom(BUSY))) {
      insert(helper, server, part);
    }
    TestMachines.later(helper, 5, () -> {
      ((li.cil.oc.api.network.Connector) server.machine().node()).changeBuffer(1000);
      helper.assertTrue(server.machine().start(), "the server did not start: " + server.machine().lastError());
      within(helper, 10, () -> server.machine().architecture() != null && server.machine().architecture().isInitialized(), () -> {
        helper.assertTrue(server.machine().isRunning(), "the server stopped: " + server.machine().lastError());
        rack.changes = 0;
        final Object[] last = {rack.lastData[0]};
        final int[] sent = {0};
        within(helper, 1, () -> {
          if (rack.lastData[0] != last[0]) {
            last[0] = rack.lastData[0];
            sent[0]++;
          }
          return false;
        }, () -> {
          helper.assertTrue(server.machine().isRunning(), "the server stopped: " + server.machine().lastError());
          helper.assertTrue(sent[0] < 5, "a running racked server's data was sent " + sent[0] + " times in a second");
          helper.assertTrue(rack.changes < 5, "the rack of a running server was set changed (sending its inventory) " + rack.changes + " times in a second");
          helper.succeed();
        });
      });
    });
  }

  // ---- the Lua state ----

  /**
   * The memory limit is set by whoever has the Lua state: from the server thread (a part changed) it waited for the
   * state's lock, a whole slice. Still, a RAM stick added counts at once.
   */
  @GameTest(template = EMPTY, timeoutTicks = 10_000_000)
  public static void memoryLimitIsNotSetFromTheServerThread(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.CASE_CREATIVE.get());
    final var computer = (Case) helper.getBlockEntity(AT);
    for (final ItemStack part : List.of(item(Constants.ItemName.CPUTier1), item(Constants.ItemName.RAMTier1), eeprom(
      "local t = computer.totalMemory()\nwhile true do computer.pullSignal() if computer.totalMemory() ~= t then error('done:' .. t .. '>' .. computer.totalMemory()) end end\n"))) {
      insert(helper, computer, part);
    }
    TestMachines.later(helper, 5, () -> {
      final var machine = (MachineBase) computer.machine();
      helper.assertTrue(machine.start(), "the case did not start: " + machine.lastError());
      within(helper, 20, () -> machine.architecture() != null && machine.architecture().isInitialized() && !machine.isExecuting()
        && machine.state().peek() == MachineBase.State.Sleeping, () -> {
        helper.assertTrue(machine.architecture() instanceof NativeLuaArchitecture, "not native Lua: " + machine.architecture());
        final Object lua;
        try {
          final var field = NativeLuaArchitecture.class.getDeclaredField("lua");
          field.setAccessible(true);
          lua = field.get(machine.architecture());
        } catch (ReflectiveOperationException e) {
          throw new IllegalStateException(e);
        }
        // the state's lock held as by a computer thread in a slice
        final var held = new java.util.concurrent.CountDownLatch(1);
        final var holder = new Thread(() -> {
          synchronized (lua) {
            held.countDown();
            try {
              Thread.sleep(500);
            } catch (InterruptedException ignored) {
            }
          }
        });
        holder.start();
        final long waited;
        try {
          held.await();
          final long start = System.nanoTime();
          machine.onHostChanged();
          waited = (System.nanoTime() - start) / 1_000_000;
          holder.join();
        } catch (InterruptedException e) {
          throw new IllegalStateException(e);
        }
        helper.assertTrue(waited < 250, "the server thread waited " + waited + " ms for the Lua state to set the memory limit");
        insert(helper, computer, item(Constants.ItemName.RAMTier1));
        machine.signal("wake");
        within(helper, 20, () -> String.valueOf(machine.lastError()).contains("done:") || !machine.isRunning(), () -> {
          final String error = String.valueOf(machine.lastError());
          helper.assertTrue(error.contains("done:"), "the computer did not see more memory: " + error);
          final String[] totals = error.substring(error.indexOf("done:") + 5).trim().split(">");
          helper.assertTrue(Double.parseDouble(totals[1]) > Double.parseDouble(totals[0]), "the memory went from " + totals[0] + " to " + totals[1]);
          helper.succeed();
        });
      });
    });
  }

  /** Each native Lua architecture has its one factory (a new one per start rewrote the library and made a test state). */
  @GameTest(template = EMPTY)
  public static void nativeLuaFactoriesAreMadeOnce(final GameTestHelper helper) {
    final List<java.util.function.Supplier<?>> factories = List.of(new li.cil.oc.core.impl.server.machine.luac.NativeLua52Architecture(null)::factory,
      new li.cil.oc.core.impl.server.machine.luac.NativeLua53Architecture(null)::factory, new li.cil.oc.core.impl.server.machine.luac.NativeLua54Architecture(null)::factory);
    for (final var factory : factories) {
      final Object first = factory.get();
      helper.assertTrue(first == factory.get(), first.getClass().getSimpleName() + " is made again for each start");
    }
    helper.succeed();
  }

  // ---- what is kept after it is gone ----

  private static boolean collected(final List<WeakReference<?>> refs) {
    System.gc();
    for (final var ref : refs) {
      if (ref.get() != null) return false;
    }
    return true;
  }

  /** Keyboards are held weakly, as OC's (every keyboard that ever connected was kept, with its host). */
  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void keyboardsAreNotKept(final GameTestHelper helper) {
    final WeakReference<?> ref = connectedKeyboard(helper);
    TestMachines.when(helper, () -> collected(List.of(ref)), 20, () -> {
      helper.assertTrue(ref.get() == null, "a keyboard that connected once is still held");
      helper.succeed();
    });
  }

  private static WeakReference<?> connectedKeyboard(final GameTestHelper helper) {
    final var level = helper.getLevel();
    final var keyboard = new li.cil.oc.core.impl.server.component.Keyboard(new li.cil.oc.api.network.EnvironmentHost() {
      @Override
      public net.minecraft.world.level.Level world() {
        return level;
      }

      @Override
      public double xPosition() {
        return 0;
      }

      @Override
      public double yPosition() {
        return 0;
      }

      @Override
      public double zPosition() {
        return 0;
      }

      @Override
      public void markChanged() {
      }
    });
    keyboard.onConnect(keyboard.node());
    return new WeakReference<>(keyboard);
  }

  /** A stopped server's MFU listeners go (their removal was queued and dropped: each kept its host and level). */
  @GameTest(template = EMPTY)
  @SuppressWarnings("unchecked")
  public static void blockChangeListenersForgetAStoppedServer(final GameTestHelper helper) {
    final Map<Object, Object> listeners;
    try {
      final var field = li.cil.oc.neoforge.common.event.BlockChangeHandler.class.getDeclaredField("changeListeners");
      field.setAccessible(true);
      listeners = (Map<Object, Object>) field.get(null);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
    final var running = new HashMap<>(listeners); // the other tests' MFUs, put back (all on the server thread)
    final li.cil.oc.neoforge.common.event.BlockChangeHandler.ChangeListener gone = () -> {
    };
    listeners.put(gone, li.cil.oc.core.impl.util.BlockPosition.apply(0, 0, 0, helper.getLevel()));
    try {
      li.cil.oc.neoforge.common.event.BlockChangeHandler.onServerStopped(new ServerStoppedEvent(helper.getLevel().getServer()));
      helper.assertTrue(!listeners.containsKey(gone), "after the server stopped its MFU listeners are still held");
    } finally {
      listeners.clear();
      listeners.putAll(running);
    }
    helper.succeed();
  }

  /** A stopped server's players' nanomachine controllers go (each holds its player, its weak map's key, for good). */
  @GameTest(template = EMPTY)
  public static void nanomachineControllersForgetAStoppedServer(final GameTestHelper helper) {
    final var nanomachines = (li.cil.oc.core.impl.common.nanomachines.Nanomachines) li.cil.oc.api.API.nanomachines;
    final var running = new HashMap<>(nanomachines.serverControllers); // the other tests' players, put back
    final var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "oc-gone"));
    nanomachines.serverControllers.put(player, new li.cil.oc.core.impl.common.nanomachines.ControllerImpl(player));
    try {
      li.cil.oc.neoforge.common.event.NanomachinesHandler.Common.onServerStopped(new ServerStoppedEvent(helper.getLevel().getServer()));
      helper.assertTrue(!nanomachines.serverControllers.containsKey(player), "after the server stopped a player's controller is still held");
    } finally {
      nanomachines.serverControllers.remove(player);
      nanomachines.serverControllers.putAll(running);
    }
    helper.succeed();
  }

  /**
   * AE2 crafting links shared by the craftables of a job are not kept, nor statuses saved while computing once their
   * request came back (each held its link, the old craftable, its host and level for the session); a status loaded
   * again still learns what came of the request.
   */
  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void unmatchedCraftingLinksAreNotKept(final GameTestHelper helper) {
    final CompoundTag saved = new CompoundTag();
    final List<WeakReference<?>> refs = storedUnmatched(helper, saved);
    TestMachines.when(helper, () -> collected(refs), 20, () -> {
      helper.assertTrue(refs.get(0).get() == null, "a crafting status saved while computing is still held after its request came back");
      helper.assertTrue(refs.get(1).get() == null, "a crafting link loaded is still held");
      final var again = new li.cil.oc.neoforge.integration.appeng.NetworkControl.CraftingStatus();
      again.load(saved, helper.getLevel().registryAccess());
      final Object[] result = again.isDone(null, new ArgumentsImpl(new Object[0]));
      helper.assertTrue(result.length == 2 && "request failed (test)".equals(result[1]), "the status loaded again says " + java.util.Arrays.toString(result));
      helper.succeed();
    });
  }

  /**
   * A status restored after a restart whose job no craftable came back with (the craftable was not kept, or the job was
   * the old machine's) says cancelled, as AE2 cancels a job with no requester: it said "no link" for good, and a program
   * waiting for done or cancelled hung. Within a session it still waits, and a craftable back with the job is followed.
   */
  @GameTest(template = EMPTY)
  public static void aStatusWhoseJobDidNotComeBackFromARestartSaysCancelled(final GameTestHelper helper) {
    try {
      final var provider = helper.getLevel().registryAccess();
      final var args = new ArgumentsImpl(new Object[0]);
      final UUID session = (UUID) linkCache("session").invoke(null);
      final java.util.function.BiFunction<UUID, String, li.cil.oc.neoforge.integration.appeng.NetworkControl.CraftingStatus> restored = (id, savedIn) -> {
        final CompoundTag nbt = new CompoundTag();
        nbt.putBoolean("computing", false);
        nbt.putString("link", id.toString());
        nbt.putBoolean("failed", false);
        nbt.putString("reason", "no link");
        if (savedIn != null) nbt.putString("session", savedIn);
        final var status = new li.cil.oc.neoforge.integration.appeng.NetworkControl.CraftingStatus();
        status.load(nbt, provider);
        return status;
      };

      final var lost = restored.apply(UUID.randomUUID(), UUID.randomUUID().toString()); // (saved by the last server)
      helper.assertTrue(java.util.Arrays.equals(lost.isCanceled(null, args), new Object[]{true}), "a job lost in a restart is not cancelled: " + java.util.Arrays.toString(lost.isCanceled(null, args)));
      helper.assertTrue(java.util.Arrays.equals(lost.isDone(null, args), new Object[]{false}), "a job lost in a restart is done: " + java.util.Arrays.toString(lost.isDone(null, args)));
      final CompoundTag again = new CompoundTag();
      lost.save(again, provider);
      final var lostAgain = new li.cil.oc.neoforge.integration.appeng.NetworkControl.CraftingStatus();
      lostAgain.load(again, provider); // (saved under this server: it is not restored)
      helper.assertTrue(java.util.Arrays.equals(lostAgain.isCanceled(null, args), new Object[]{true}), "a job lost in a restart is not cancelled when loaded again: " + java.util.Arrays.toString(lostAgain.isCanceled(null, args)));

      final var older = restored.apply(UUID.randomUUID(), null); // (an older OC saved no session)
      helper.assertTrue(java.util.Arrays.equals(older.isCanceled(null, args), new Object[]{true}), "a job lost in an update is not cancelled: " + java.util.Arrays.toString(older.isCanceled(null, args)));

      final var waiting = restored.apply(UUID.randomUUID(), session.toString()); // (its craftable lives on, not kept)
      helper.assertTrue(java.util.Arrays.equals(waiting.isCanceled(null, args), new Object[]{false, "no link"}), "a job in this session is cancelled: " + java.util.Arrays.toString(waiting.isCanceled(null, args)));
      final CompoundTag waited = new CompoundTag();
      waiting.save(waited, provider);
      final var waitingAgain = new li.cil.oc.neoforge.integration.appeng.NetworkControl.CraftingStatus();
      waitingAgain.load(waited, provider); // (saved under this server)
      helper.assertTrue(java.util.Arrays.equals(waitingAgain.isCanceled(null, args), new Object[]{false, "no link"}), "a job in this session is cancelled when loaded again: " + java.util.Arrays.toString(waitingAgain.isCanceled(null, args)));

      final CompoundTag doneNbt = new CompoundTag(); // (saved done by the last server)
      doneNbt.putBoolean("computing", false);
      doneNbt.putString("link", UUID.randomUUID().toString());
      doneNbt.putBoolean("done", true);
      doneNbt.putBoolean("canceled", false);
      doneNbt.putString("session", UUID.randomUUID().toString());
      final var done = new li.cil.oc.neoforge.integration.appeng.NetworkControl.CraftingStatus();
      done.load(doneNbt, provider);
      helper.assertTrue(java.util.Arrays.equals(done.isDone(null, args), new Object[]{true}), "a job done before a restart is not done: " + java.util.Arrays.toString(done.isDone(null, args)));
      helper.assertTrue(java.util.Arrays.equals(done.isCanceled(null, args), new Object[]{false}), "a job done before a restart is cancelled: " + java.util.Arrays.toString(done.isCanceled(null, args)));

      final UUID kept = UUID.randomUUID();
      final var link = testLink(kept, false);
      linkCache("load", appeng.api.networking.crafting.ICraftingLink.class).invoke(null, link); // (a craftable back with it)
      final var following = restored.apply(kept, UUID.randomUUID().toString());
      helper.assertTrue(java.util.Arrays.equals(following.isCanceled(null, args), new Object[]{false}), "a job back from a restart is not followed: " + java.util.Arrays.toString(following.isCanceled(null, args)));
      helper.assertTrue(linkCache("current", UUID.class).invoke(null, kept) == link, "the link was not shared");
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
    helper.succeed();
  }

  private static List<WeakReference<?>> storedUnmatched(final GameTestHelper helper, final CompoundTag saved) {
    try {
      final var status = new li.cil.oc.neoforge.integration.appeng.NetworkControl.CraftingStatus();
      linkCache("computing", li.cil.oc.neoforge.integration.appeng.NetworkControl.CraftingStatus.class).invoke(null, status);
      status.save(saved, helper.getLevel().registryAccess()); // (computing)
      status.fail("test");
      linkCache("computed", li.cil.oc.neoforge.integration.appeng.NetworkControl.CraftingStatus.class, appeng.api.networking.crafting.ICraftingLink.class)
        .invoke(null, status, null);
      final var link = testLink(UUID.randomUUID(), false);
      linkCache("load", appeng.api.networking.crafting.ICraftingLink.class).invoke(null, link);
      return List.of(new WeakReference<>(status), new WeakReference<>(link));
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private static appeng.api.networking.crafting.ICraftingLink testLink(final UUID id, final boolean done) {
    return (appeng.api.networking.crafting.ICraftingLink) java.lang.reflect.Proxy.newProxyInstance(
      appeng.api.networking.crafting.ICraftingLink.class.getClassLoader(), new Class<?>[]{appeng.api.networking.crafting.ICraftingLink.class},
      (proxy, method, args) -> switch (method.getName()) {
        case "getCraftingID" -> id;
        case "isDone" -> done;
        case "hashCode" -> System.identityHashCode(proxy);
        case "equals" -> proxy == args[0];
        case "toString" -> "test link";
        default -> method.getReturnType() == boolean.class ? false : null;
      });
  }

  private static java.lang.reflect.Method linkCache(final String name, final Class<?>... parameters) throws ReflectiveOperationException {
    final var method = Class.forName("li.cil.oc.neoforge.integration.appeng.NetworkControl$LinkCache").getDeclaredMethod(name, parameters);
    method.setAccessible(true);
    return method;
  }

  /**
   * A stopped server's AE2 bookkeeping goes: links shared by craftables (the next server's ticks count from 0 again, and
   * a craftable loaded at the tick a link of the old one was loaded at took that link: its requester an old craftable,
   * its grid gone), jobs over, requests computing and what came of them.
   */
  @GameTest(template = EMPTY)
  public static void sharedCraftingLinksForgetAStoppedServer(final GameTestHelper helper) {
    try {
      final Class<?> cache = Class.forName("li.cil.oc.neoforge.integration.appeng.NetworkControl$LinkCache");
      final Map<java.lang.reflect.Field, Object> running = new HashMap<>(); // the other tests' entries, put back (all on the server thread)
      for (final var field : cache.getDeclaredFields()) {
        if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
        field.setAccessible(true);
        final Object value = field.get(null);
        if (value instanceof Map<?, ?> map) running.put(field, new HashMap<>(map));
        else if (value instanceof java.util.Collection<?> collection) running.put(field, new java.util.ArrayList<>(collection));
      }
      helper.assertTrue(running.size() >= 5, "LinkCache keeps " + running.size() + " collections");
      final var sessionField = cache.getDeclaredField("session");
      sessionField.setAccessible(true);
      final Object session = sessionField.get(null);
      try {
        final UUID id = UUID.randomUUID();
        final var link = testLink(id, false);
        linkCache("load", appeng.api.networking.crafting.ICraftingLink.class).invoke(null, link); // shared
        linkCache("persisted", UUID.class).invoke(null, id); // persisted, not over
        final var over = testLink(UUID.randomUUID(), true);
        linkCache("persisted", UUID.class).invoke(null, over.getCraftingID());
        linkCache("over", appeng.api.networking.crafting.ICraftingLink.class).invoke(null, over); // a job over
        final var statusClass = li.cil.oc.neoforge.integration.appeng.NetworkControl.CraftingStatus.class;
        linkCache("computing", statusClass).invoke(null, new li.cil.oc.neoforge.integration.appeng.NetworkControl.CraftingStatus()); // computing
        final var saved = new li.cil.oc.neoforge.integration.appeng.NetworkControl.CraftingStatus();
        linkCache("computing", statusClass).invoke(null, saved);
        saved.save(new CompoundTag(), helper.getLevel().registryAccess());
        saved.fail("test");
        linkCache("computed", statusClass, appeng.api.networking.crafting.ICraftingLink.class).invoke(null, saved, null); // what came of one
        helper.assertTrue(linkCache("current", UUID.class).invoke(null, id) == link, "the link was not shared");
        for (final var field : running.keySet()) { // (each holds something of this test's, whatever the others left)
          final Object value = field.get(null);
          final int held = value instanceof Map<?, ?> map ? map.size() : ((java.util.Collection<?>) value).size();
          helper.assertTrue(held > 0, "before the server stopped LinkCache." + field.getName() + " held nothing");
        }
        linkCache("onServerStopped").invoke(null);
        for (final var field : running.keySet()) {
          final Object value = field.get(null);
          final int left = value instanceof Map<?, ?> map ? map.size() : ((java.util.Collection<?>) value).size();
          helper.assertTrue(left == 0, "after the server stopped LinkCache." + field.getName() + " still holds " + left);
        }
        helper.assertTrue(!session.equals(sessionField.get(null)), "the next server's statuses are saved under the stopped one's session");
      } finally {
        sessionField.set(null, session); // (the other tests' statuses are this server's)
        for (final var entry : running.entrySet()) {
          final Object value = entry.getKey().get(null);
          if (value instanceof Map<?, ?> map) {
            map.clear();
            @SuppressWarnings("unchecked") final var back = (Map<Object, Object>) map;
            back.putAll((Map<?, ?>) entry.getValue());
          } else {
            @SuppressWarnings("unchecked") final var back = (java.util.Collection<Object>) value;
            back.clear();
            back.addAll((java.util.Collection<?>) entry.getValue());
          }
        }
      }
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
    helper.succeed();
  }

  // ---- screens, racks, holograms, GPU buffers ----

  /**
   * A palette colour kept through a depth drop (gpu.setForeground(3, true), gpu.setDepth(1)) left the screen unloadable:
   * it was dropped with its address. The colour becomes what it showed, and buffers saved so load.
   */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void paletteColourSurvivesDepthDrop(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.SCREEN_TIER_3.get());
    TestMachines.later(helper, 5, () -> {
      final var screen = (li.cil.oc.core.impl.common.blockentity.Screen) helper.getBlockEntity(AT);
      screen.buffer.setColorDepth(li.cil.oc.api.internal.TextBuffer.ColorDepth.FourBit); // (as a GPU bound to it)
      screen.buffer.setForegroundColor(3, true);
      helper.assertTrue(screen.buffer.isForegroundFromPalette(), "the foreground is not palette colour 3 at depth 4");
      screen.buffer.setColorDepth(li.cil.oc.api.internal.TextBuffer.ColorDepth.OneBit);
      helper.assertTrue(!screen.buffer.isForegroundFromPalette(), "the foreground is still palette colour 3 at depth 1");
      final String address = screen.node().address();
      final BlockPos abs = helper.absolutePos(AT);
      final var level = helper.getLevel();
      screen.markUnloading();
      final var saved = screen.saveWithFullMetadata();
      level.removeBlockEntity(abs);
      final var loaded = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(abs, level.getBlockState(abs), saved);
      helper.assertTrue(loaded instanceof li.cil.oc.core.impl.common.blockentity.Screen, "the screen did not load");
      helper.assertTrue(address.equals(((li.cil.oc.core.impl.common.blockentity.Screen) loaded).node().address()), "the screen came back as another");
      level.getChunkAt(abs).addAndRegisterBlockEntity(loaded);
      // and one saved so before (the palette flag kept at depth 1)
      final var data = new li.cil.oc.core.impl.util.TextBuffer(4, 2, PackedColor.Depth.format(li.cil.oc.api.internal.TextBuffer.ColorDepth.OneBit));
      final var nbt = new CompoundTag();
      data.save(nbt, level.registryAccess());
      nbt.putInt("foreground", 3);
      nbt.putBoolean("foregroundIsPalette", true);
      try {
        new li.cil.oc.core.impl.util.TextBuffer(4, 2, PackedColor.Depth.format(li.cil.oc.api.internal.TextBuffer.ColorDepth.OneBit)).load(nbt, level.registryAccess());
      } catch (IllegalArgumentException e) {
        helper.fail("a buffer saved with a palette colour at depth 1 does not load: " + e.getMessage());
      }
      helper.succeed();
    });
  }

  /** The very top of a rack's front is its top slot (the reversed clamp threw there: use, analyzer, Jade). */
  @GameTest(template = EMPTY)
  public static void rackTopEdgeIsTheTopSlot(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.RACK.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    final var rack = (Rack) helper.getBlockEntity(AT);
    try {
      final var slot = rack.slotAt(rack.facing(), 0.5f, 1.0f, 0.5f);
      helper.assertTrue(slot.isPresent() && slot.get() == 0, "the top edge is slot " + slot);
      final var bottom = rack.slotAt(rack.facing(), 0.5f, 0.0f, 0.5f);
      helper.assertTrue(bottom.isPresent() && bottom.get() == rack.getContainerSize() - 1, "the bottom edge is slot " + bottom);
    } catch (IllegalArgumentException e) {
      helper.fail("the top edge of the rack's front threw: " + e.getMessage());
    }
    helper.succeed();
  }

  /** NaN is refused by the hologram's setters (as the scale it made the power needed NaN: dark for good, and saved). */
  @GameTest(template = EMPTY)
  public static void hologramRefusesNaN(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.HOLOGRAM_TIER_2.get());
    final var hologram = (li.cil.oc.core.impl.common.blockentity.Hologram) helper.getBlockEntity(AT);
    final double nan = Double.NaN;
    final var refused = new java.util.ArrayList<String>();
    final Map<String, Runnable> calls = new java.util.LinkedHashMap<>();
    calls.put("setScale", () -> hologram.setScale(context(), new ArgumentsImpl(new Object[]{nan})));
    calls.put("setTranslation", () -> hologram.setTranslation(context(), new ArgumentsImpl(new Object[]{0.0, nan, 0.0})));
    calls.put("setRotation", () -> hologram.setRotation(context(), new ArgumentsImpl(new Object[]{nan, 0.0, 1.0, 0.0})));
    calls.put("setRotation axis", () -> hologram.setRotation(context(), new ArgumentsImpl(new Object[]{45.0, Double.POSITIVE_INFINITY, 1.0, 0.0})));
    calls.put("setRotationSpeed", () -> hologram.setRotationSpeed(context(), new ArgumentsImpl(new Object[]{nan, 0.0, 1.0, 0.0})));
    calls.put("setRotationSpeed axis", () -> hologram.setRotationSpeed(context(), new ArgumentsImpl(new Object[]{10.0, 0.0, nan, 0.0})));
    for (final var call : calls.entrySet()) {
      try {
        call.getValue().run();
      } catch (IllegalArgumentException e) {
        refused.add(call.getKey());
      }
    }
    helper.assertTrue(refused.size() == calls.size(), "taken: " + calls.keySet().stream().filter(k -> !refused.contains(k)).toList()
      + " (scale " + hologram.scale + ", translation " + hologram.translationY + ")");
    helper.assertTrue(Double.isFinite(hologram.scale) && Double.isFinite(hologram.translationY), "the hologram took NaN");
    hologram.setScale(context(), new ArgumentsImpl(new Object[]{Double.POSITIVE_INFINITY})); // clamped, as OC
    helper.assertTrue(hologram.scale == li.cil.oc.core.impl.OCSettings.get().hologramMaxScaleByTier[hologram.tier], "an infinite scale is " + hologram.scale);
    helper.succeed();
  }

  /**
   * A GPU buffer is no larger than a buffer loads with (160 by 160): a 320 wide one came back cut off, its colours read
   * with the cut width, after a reload and on the clients.
   */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void gpuBuffersFitWhatLoads(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.CASE_CREATIVE.get());
    final var computer = (Case) helper.getBlockEntity(AT);
    insert(helper, computer, item(Constants.ItemName.GraphicsCardTier3));
    TestMachines.later(helper, 5, () -> {
      final var gpu = component(computer.installedComponents(), li.cil.oc.core.impl.server.component.GraphicsCardBase.class);
      final Object[] wide = gpu.allocateBuffer(context(), new ArgumentsImpl(new Object[]{320.0, 50.0}));
      final Object[] fits = gpu.allocateBuffer(context(), new ArgumentsImpl(new Object[]{160.0, 50.0}));
      gpu.freeAllBuffers(context(), new ArgumentsImpl(new Object[0]));
      helper.assertTrue(wide[0] == null, "a 320x50 buffer was allocated as " + wide[0]);
      helper.assertTrue(fits[0] != null, "a 160x50 buffer was refused: " + (fits.length > 1 ? fits[1] : null));
      helper.succeed();
    });
  }
}
