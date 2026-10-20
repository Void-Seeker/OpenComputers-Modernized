package li.cil.oc.neoforge.gametest;

import com.mojang.authlib.GameProfile;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import li.cil.oc.core.Constants;
import li.cil.oc.core.impl.OCSettings;
import li.cil.oc.core.impl.common.item.data.TabletData;
import li.cil.oc.core.impl.integration.opencomputers.Item;
import li.cil.oc.core.impl.server.component.TabletHost;
import li.cil.oc.core.impl.server.component.TabletHostBase;
import li.cil.oc.core.impl.server.machine.ArgumentsImpl;
import li.cil.oc.core.impl.server.machine.MachineBase;
import li.cil.oc.core.impl.util.TabletCache;
import li.cil.oc.neoforge.OpenComputers;
import li.cil.oc.neoforge.common.init.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Tablets keep their drive, program and energy through chargers, dimensions, moves and saves; HTTP responses, debug
 * card values, the data card and linked cards (third audit).
 */
@GameTestHolder(OpenComputers.ID)
@PrefixGameTestTemplate(false)
public final class Audit3TabletTests {
  private static final String EMPTY = "empty";
  private static final BlockPos AT = new BlockPos(2, 2, 2);
  private static final String NS = OCSettings.namespace;
  private static final ArgumentsImpl NO_ARGS = new ArgumentsImpl(new Object[0]);

  // counts 'tick' signals, works a second on 'busy' (adding 100), reports the count on 'report'
  private static final String COUNTER = """
    local n = 0
    while true do
      local s = computer.pullSignal()
      if s == "tick" then n = n + 1
      elseif s == "busy" then local t = os.clock() while os.clock() - t < 1 do end n = n + 100
      elseif s == "report" then error("done:" .. n) end
    end""";

  private Audit3TabletTests() {
  }

  private static ItemStack item(final String name) {
    return li.cil.oc.api.Items.get(name).createItemStack(1);
  }

  private static ItemStack eeprom(final String program) {
    return li.cil.oc.api.Items.registerEEPROM("tablet test", program.getBytes(StandardCharsets.UTF_8), null, false);
  }

  private static ItemStack tablet(final ItemStack... parts) {
    final var inventory = new SimpleContainer(22);
    inventory.setItem(0, item(Constants.ItemName.TabletCaseTier1));
    for (int i = 0; i < parts.length; i++) inventory.setItem(13 + i, parts[i]);
    return (ItemStack) li.cil.oc.core.impl.common.template.TabletTemplate.assemble(inventory)[0];
  }

  // a real (not fake) player, not in the world, whose packets go nowhere
  private static ServerPlayer player(final GameTestHelper helper) {
    final var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "oc-tablet"));
    new net.minecraft.server.network.ServerGamePacketListenerImpl(helper.getLevel().getServer(),
      new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), player);
    return player;
  }

  private static li.cil.oc.api.machine.Context context() {
    return (li.cil.oc.api.machine.Context) java.lang.reflect.Proxy.newProxyInstance(li.cil.oc.api.machine.Context.class.getClassLoader(),
      new Class<?>[]{li.cil.oc.api.machine.Context.class}, (proxy, method, args) -> method.getReturnType() == boolean.class ? false : null);
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

  /** Runs the step every tick while it says to go on (onEachTick books every tick up to the timeout at once). */
  private static void everyTick(final GameTestHelper helper, final BooleanSupplier step) {
    if (step.getAsBoolean()) helper.runAfterDelay(1, () -> everyTick(helper, step));
  }

  private static double buffer(final TabletHostBase host) {
    return ((li.cil.oc.api.network.Connector) host.serverComponent().node).localBuffer();
  }

  // waiting for its next signal: done booting, between slices
  private static boolean sleeping(final TabletHostBase host) {
    return host.machine() instanceof MachineBase machine && machine.state().peek() == MachineBase.State.Sleeping;
  }

  private static boolean reported(final TabletHostBase host) {
    return String.valueOf(host.machine().lastError()).contains("done:") || !host.machine().isRunning();
  }

  private static <T> T component(final TabletHostBase host, final Class<T> type) {
    for (final var env : host.componentEnvironments()) {
      if (type.isInstance(env)) return type.cast(env);
    }
    throw new IllegalStateException("the tablet has no " + type.getSimpleName());
  }

  private static li.cil.oc.core.impl.util.SafeThreadPool fileSavePool() {
    try { // the pool comes with the first buffered drive
      Class.forName("li.cil.oc.core.impl.server.fs.FileSystem$BufferedFileSystem", true, Audit3TabletTests.class.getClassLoader());
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException(e);
    }
    for (final var pool : li.cil.oc.core.impl.util.ThreadPoolFactory.safePools) {
      if ("FileSystem".equals(pool.name())) return pool;
    }
    throw new IllegalStateException("no file system save pool");
  }

  // ----------------------------------------------------------------------- //
  // tablets

  /**
   * A tablet put in a charger saves its drive first and lets go of it: the charger's file system read the drive from
   * disk without what the tablet had not saved yet, and its save (taken out) put back a folder the tablet had deleted.
   */
  @GameTest(template = EMPTY, timeoutTicks = 400)
  public static void chargersDoNotRollBackATabletsDrive(final GameTestHelper helper) throws Exception {
    helper.setBlock(AT, Blocks.CHARGER.get());
    final var charger = (li.cil.oc.core.impl.common.blockentity.Charger) helper.getBlockEntity(AT);
    final Player player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "oc-tablet"));
    final ItemStack stack = tablet(item(Constants.ItemName.CPUTier1), item(Constants.ItemName.RAMTier1), item(Constants.ItemName.HDDTier1));
    final var host = li.cil.oc.core.impl.common.item.Tablet.get(stack, player);
    host.machine(); // its parts connect
    final var drive = component(host, li.cil.oc.core.impl.server.component.FileSystem.class).fileSystem;
    final File root = ((li.cil.oc.core.server.fs.Buffered) drive).fileRoot();
    try {
      drive.makeDirectory("kept");
      drive.makeDirectory("deleted");
      TabletCache.get().saveAll(helper.getLevel()); // a world save: both on disk
      fileSavePool().withPool(pool -> pool.submit(() -> {
      })).get(10, TimeUnit.SECONDS);
      // a while ago (a deletion removes only files older than itself: not one saved the same millisecond)
      helper.assertTrue(new File(root, "deleted").setLastModified(System.currentTimeMillis() - 10_000), "the folder was not saved");
      host.setChanged(); // (what its next slice does: the drive's address into the tablet)
      drive.delete("deleted"); // not saved yet
      charger.setItem(0, stack);
      TabletCache.get().invalidate(stack); // the tablet's machine dropped, ten seconds later, saves its drive
      charger.setItem(0, ItemStack.EMPTY); // taken out: the charger's file system saves
      fileSavePool().withPool(pool -> pool.submit(() -> {
      })).get(10, TimeUnit.SECONDS);
      helper.assertTrue(new File(root, "kept").isDirectory(), "the tablet's drive lost a folder: " + java.util.Arrays.toString(root.list()));
      helper.assertTrue(!new File(root, "deleted").exists(), "a folder the tablet deleted came back after a charger");
    } finally {
      TabletCache.get().invalidate(stack);
      helper.runAfterDelay(20, () -> org.apache.commons.io.FileUtils.deleteQuietly(root));
    }
    helper.runAfterDelay(25, helper::succeed);
  }

  /** A tablet runs on through a dimension change, as in OC (it was switched off every time). */
  @GameTest(template = EMPTY, timeoutTicks = 10_000_000)
  public static void tabletsRunOnInAnotherDimension(final GameTestHelper helper) {
    dimensionChange(helper, false);
  }

  /** A tablet busy at a dimension change moves once the slice is over (its save was the one before: it came back older). */
  @GameTest(template = EMPTY, timeoutTicks = 10_000_000)
  public static void busyTabletsMoveAfterTheirSlice(final GameTestHelper helper) {
    dimensionChange(helper, true);
  }

  private static void dimensionChange(final GameTestHelper helper, final boolean busy) {
    final ServerPlayer player = player(helper);
    final ItemStack stack = tablet(item(Constants.ItemName.CPUTier1), item(Constants.ItemName.RAMTier1), item(Constants.ItemName.RAMTier1), eeprom(COUNTER));
    final boolean[] carried = {true};
    // as Tablet.inventoryTick: the host for the stack, then its update
    everyTick(helper, () -> {
      if (carried[0]) li.cil.oc.core.impl.common.item.Tablet.get(stack, player).update();
      return carried[0];
    });
    final var before = li.cil.oc.core.impl.common.item.Tablet.get(stack, player);
    before.startMachine();
    within(helper, 30, () -> sleeping(before), () -> {
      helper.assertTrue(sleeping(before), "the tablet's program did not start: " + before.machine().lastError());
      // signals waiting are saved with it; busy, it moves mid-slice
      if (busy) before.machine().signal("busy");
      else for (int i = 0; i < 3; i++) before.machine().signal("tick");
      within(helper, 10, () -> !busy || ((MachineBase) before.machine()).isExecuting(), () -> {
        helper.assertTrue(!busy || ((MachineBase) before.machine()).isExecuting(), "the tablet did not get busy");
        player.setServerLevel(helper.getLevel().getServer().getLevel(Level.NETHER));
        final boolean[] asked = {false};
        within(helper, 20, () -> {
          final var now = li.cil.oc.core.impl.common.item.Tablet.get(stack, player);
          if (now != before && !asked[0]) {
            asked[0] = true;
            if (busy) for (int i = 0; i < 3; i++) now.machine().signal("tick");
            now.machine().signal("report");
          }
          return now != before && reported(now);
        }, () -> {
          carried[0] = false;
          final var after = li.cil.oc.core.impl.common.item.Tablet.get(stack, player);
          try {
            helper.assertTrue(after != before, "the tablet did not move to the other dimension");
            final String expected = "done:" + (busy ? 103 : 3);
            helper.assertTrue(String.valueOf(after.machine().lastError()).contains(expected), "after a dimension change the tablet "
              + (String.valueOf(after.machine().lastError()).contains("done:") ? "reported " : "is off: ") + after.machine().lastError() + " (not " + expected + ")");
          } finally {
            TabletCache.get().invalidate(stack);
          }
          helper.succeed();
        });
      });
    });
  }

  /**
   * A running tablet moved into a chest (a stack its machine does not follow) and picked up again after its machine was
   * dropped comes back off, with the energy it had: it resumed from the last world save's snapshot, its program older
   * than its drive, and with that save's fuller buffer.
   */
  @GameTest(template = EMPTY, timeoutTicks = 10_000_000)
  public static void tabletsBackFromAChestAreOffWithTheirEnergy(final GameTestHelper helper) {
    backFromAChest(helper, 3);
  }

  // A server stall past the tablet cache's ten seconds (other tests load chunks, the GC) drops the cached machine,
  // stopped, and the next get makes a new one: then it runs again with a new tablet (it waited for the dropped one to
  // start, and failed with "did not start: null")
  private static void backFromAChest(final GameTestHelper helper, final int tries) {
    final Player player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "oc-tablet"));
    final ItemStack stack = tablet(item(Constants.ItemName.CPUTier1), item(Constants.ItemName.RAMTier1), item(Constants.ItemName.RAMTier1), eeprom(COUNTER));
    final var host = li.cil.oc.core.impl.common.item.Tablet.get(stack, player);
    final boolean[] carried = {true};
    everyTick(helper, () -> {
      if (carried[0]) li.cil.oc.core.impl.common.item.Tablet.get(stack, player).update(); // (got: kept in the cache)
      return carried[0];
    });
    host.startMachine();
    final java.util.function.BooleanSupplier cached = () -> li.cil.oc.core.impl.common.item.Tablet.get(stack, player) == host;
    within(helper, 30, () -> sleeping(host) || !cached.getAsBoolean(), () -> {
      if (!cached.getAsBoolean() && tries > 1) {
        carried[0] = false;
        TabletCache.get().invalidate(stack);
        backFromAChest(helper, tries - 1);
        return;
      }
      helper.assertTrue(cached.getAsBoolean(), "the tablet's machine was dropped from the cache in every try (server stalls)");
      helper.assertTrue(sleeping(host), "the tablet's program did not start: " + host.machine().lastError());
      TabletCache.get().saveAll(helper.getLevel()); // a world save while it runs
      ((li.cil.oc.api.network.Connector) host.serverComponent().node).changeBuffer(-1000);
      host.update();
      final double left = buffer(host);
      carried[0] = false;
      final ItemStack chest = stack.copy(); // moved: a new stack
      TabletCache.get().invalidate(stack); // dropped ten seconds later: it saves into the stack it had
      final var back = li.cil.oc.core.impl.common.item.Tablet.get(chest, player);
      try {
        back.update();
        helper.assertTrue(back != host, "the tablet's machine was not dropped");
        helper.assertTrue(buffer(back) <= left + 1, "back from a chest the tablet has " + buffer(back) + " energy, it had " + left);
        helper.assertTrue(!back.machine().isRunning(), "back from a chest the tablet resumed the world save's snapshot");
      } finally {
        TabletCache.get().invalidate(chest);
      }
      helper.succeed();
    });
  }

  /** A player's file has their tablets as they were (the tablets were written after it: lost at quitting). */
  @GameTest(template = EMPTY)
  public static void playerSavesHoldTheirTablets(final GameTestHelper helper) {
    final ServerPlayer player = player(helper);
    final ItemStack stack = tablet(item(Constants.ItemName.CPUTier1), item(Constants.ItemName.RAMTier1), item(Constants.ItemName.EEPROM));
    player.getInventory().setItem(0, stack);
    final var host = li.cil.oc.core.impl.common.item.Tablet.get(stack, player);
    try {
      host.machine(); // its parts connect
      component(host, li.cil.oc.core.impl.server.component.EEPROM.class).label = "saved with you";
      final CompoundTag saved = player.saveWithoutId(new CompoundTag());
      final ListTag inventory = saved.getList("Inventory", Tag.TAG_COMPOUND);
      String label = null;
      for (int i = 0; i < inventory.size(); i++) {
        final var data = new TabletData(ItemStack.of(inventory.getCompound(i)));
        for (final ItemStack part : data.items) {
          if (part != null && li.cil.oc.api.Items.get(part) == li.cil.oc.api.Items.get(Constants.ItemName.EEPROM)) {
            label = Item.peekDataTag(part).getString(NS + "label");
          }
        }
      }
      helper.assertTrue("saved with you".equals(label), "the player's file has the tablet's EEPROM labelled " + label);
    } finally {
      TabletCache.get().invalidate(stack);
    }
    helper.succeed();
  }

  /** A host that fails to be got is still cached: one made anew each call made a machine and a Lua state each time. */
  @GameTest(template = EMPTY)
  public static void failingTabletHostsAreNotMadeEachCall(final GameTestHelper helper) {
    final Player player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "oc-tablet"));
    final ItemStack stack = tablet(item(Constants.ItemName.CPUTier1), item(Constants.ItemName.RAMTier1));
    final int[] made = {0};
    final var cache = new TabletCache(10) {
      @Override
      protected TabletHostBase createHost(final ItemStack stack, final Player player) {
        made[0]++;
        return new TabletHost(stack, player) {
          @Override
          public void rebind(final ItemStack current) {
            throw new IllegalStateException("test: the host cannot follow its stack");
          }
        };
      }
    };
    final var first = cache.get(stack, player);
    final var second = cache.get(stack.copy(), player);
    final var third = cache.get(stack.copy(), player);
    helper.assertTrue(made[0] == 1 && second == first && third == first, "three gets made " + made[0] + " tablet hosts");
    helper.succeed();
  }

  // ----------------------------------------------------------------------- //
  // cards

  /** A stand-in HTTP connection (no network): answers when told to, and remembers being closed. */
  private static final class StubConnection extends java.net.HttpURLConnection {
    final java.util.concurrent.CountDownLatch answer = new java.util.concurrent.CountDownLatch(1);
    final AtomicBoolean asked = new AtomicBoolean();
    final AtomicBoolean closed = new AtomicBoolean();
    private final InputStream body = new java.io.ByteArrayInputStream("partial".getBytes(StandardCharsets.US_ASCII)) {
      @Override
      public void close() {
        closed.set(true);
      }
    };

    StubConnection(final URL url) {
      super(url);
    }

    @Override
    public InputStream getInputStream() throws java.io.IOException {
      asked.set(true);
      boolean interrupted = false;
      try {
        while (true) { // (as a socket read: not given up on an interrupt)
          try {
            if (!answer.await(20, TimeUnit.SECONDS)) throw new java.io.IOException("no answer");
            return body;
          } catch (InterruptedException e) {
            interrupted = true;
          }
        }
      } finally {
        if (interrupted) Thread.currentThread().interrupt();
      }
    }

    @Override
    public int getResponseCode() {
      return 200;
    }

    @Override
    public String getResponseMessage() {
      return "OK";
    }

    @Override
    public Map<String, java.util.List<String>> getHeaderFields() {
      return Map.of();
    }

    @Override
    public void connect() {
    }

    @Override
    public boolean usingProxy() {
      return false;
    }

    @Override
    public void disconnect() {
      closed.set(true);
    }
  }

  private static URL stubbed(final StubConnection[] connection) throws java.net.MalformedURLException {
    // (a public address passes the filter; nothing connects to it)
    return new URL(null, "http://8.8.8.8/", new java.net.URLStreamHandler() {
      @Override
      protected java.net.URLConnection openConnection(final URL url) {
        return connection[0] = new StubConnection(url);
      }

      @Override
      protected java.net.URLConnection openConnection(final URL url, final java.net.Proxy proxy) {
        return openConnection(url);
      }
    });
  }

  /**
   * An HTTP response closed before its end, or one that came after its request was closed (its computer stopped), is
   * closed (it was left to the garbage collector, its socket open meanwhile).
   */
  @GameTest(template = EMPTY, timeoutTicks = 10_000_000)
  public static void httpResponsesAreClosedWithTheirRequest(final GameTestHelper helper) throws Exception {
    final var card = new li.cil.oc.core.impl.server.component.InternetCard();
    final StubConnection[] firstConnection = {null};
    final StubConnection[] secondConnection = {null};
    final var first = new li.cil.oc.core.impl.server.component.InternetCard.HTTPRequest(card, stubbed(firstConnection), null, new HashMap<>(), null);
    final var second = new li.cil.oc.core.impl.server.component.InternetCard.HTTPRequest(card, stubbed(secondConnection), null, new HashMap<>(), null);
    final ArgumentsImpl oneByte = new ArgumentsImpl(new Object[]{1});
    final int[] got = {0};
    within(helper, 20, () -> {
      if (firstConnection[0] != null) firstConnection[0].answer.countDown();
      // a byte: the rest stays read ahead, no read waits for more
      if (Boolean.TRUE.equals(first.finishConnect(null, NO_ARGS)[0]) && first.read(null, oneByte)[0] instanceof byte[] data) got[0] += data.length;
      return got[0] > 0 && secondConnection[0] != null && secondConnection[0].asked.get();
    }, () -> {
      helper.assertTrue(got[0] > 0, "no response came");
      helper.assertTrue(secondConnection[0] != null && secondConnection[0].asked.get(), "the second request was not sent");
      first.close(); // before the end
      second.close(); // its computer stopped before the response came
      secondConnection[0].answer.countDown();
      within(helper, 10, () -> firstConnection[0].closed.get() && secondConnection[0].closed.get(), () -> {
        helper.assertTrue(firstConnection[0].closed.get(), "a response closed before its end was not closed");
        helper.assertTrue(secondConnection[0].closed.get(), "a response that came after its request was closed was not closed");
        helper.succeed();
      });
    });
  }

  /**
   * A program holding a debug card's world, player or scoreboard comes back from a save (they could not be made again:
   * "could not restore the computer's state").
   */
  @GameTest(template = EMPTY, timeoutTicks = 10_000_000)
  public static void debugCardValuesComeBackFromASave(final GameTestHelper helper) {
    TestMachines.runMicrocontroller(helper, AT, java.util.List.of(Constants.ItemName.CPUTier1, Constants.ItemName.RAMTier2, Constants.ItemName.DebugCard), """
      local d = component.proxy(component.list("debug")())
      local w, p, s = d.getWorld(), d.getPlayer("nobody"), d.getScoreboard()
      while true do
        if computer.pullSignal() == "report" then error("done:" .. w.getDimensionName() .. "," .. type(p) .. "," .. type(s)) end
      end""", mc -> {
      final var machine = (MachineBase) mc.machine();
      within(helper, 20, () -> machine.state().peek() == MachineBase.State.Sleeping, () -> {
        helper.assertTrue(machine.state().peek() == MachineBase.State.Sleeping, "the program did not get to wait: " + machine.lastError());
        final CompoundTag saved = mc.saveWithoutMetadata().getCompound(NS + "computer");
        machine.load(saved, helper.getLevel().registryAccess()); // as after the chunk is loaded again
        machine.signal("report");
        within(helper, 20, () -> String.valueOf(machine.lastError()).contains("done:") || !machine.isRunning(), () -> {
          final String error = String.valueOf(machine.lastError());
          // (values are tables to Lua)
          helper.assertTrue(error.contains("done:" + helper.getLevel().dimension().location() + ",table,table"), "after a save the program "
            + (error.contains("done:") ? "reported " : "is off: ") + error);
          helper.succeed();
        });
      });
    });
  }

  private static final class Battery extends li.cil.oc.api.prefab.AbstractManagedEnvironment {
    public final li.cil.oc.api.network.Node node = li.cil.oc.api.Network.newNode(this, li.cil.oc.api.network.Visibility.None).withConnector(1e9).create();
  }

  private static byte[] deflate(final byte[] data) {
    final var deflater = new java.util.zip.Deflater();
    deflater.setInput(data);
    deflater.finish();
    final var out = new java.io.ByteArrayOutputStream();
    final byte[] buffer = new byte[8192];
    while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer));
    deflater.end();
    return out.toByteArray();
  }

  /** The data card inflates no more than it takes (a megabyte inflated to a gigabyte on the server's heap). */
  @GameTest(template = EMPTY)
  public static void dataCardInflatesWithinItsLimit(final GameTestHelper helper) {
    final var card = new li.cil.oc.core.impl.server.component.DataCard.Tier1();
    final var battery = new Battery();
    li.cil.oc.api.Network.joinNewNetwork(battery.node);
    try {
      battery.node.connect(card.node);
      ((li.cil.oc.api.network.Connector) battery.node).changeBuffer(1e9);
      final int limit = OCSettings.get().dataCardHardLimit;
      final Object[] small = card.inflate(context(), new ArgumentsImpl(new Object[]{deflate(new byte[1000])}));
      helper.assertTrue(small[0] instanceof byte[] b && b.length == 1000, "1000 bytes inflated to " + (small[0] instanceof byte[] b ? b.length : small[0]));
      Object result;
      try {
        result = card.inflate(context(), new ArgumentsImpl(new Object[]{deflate(new byte[2 * limit + 1])}))[0];
      } catch (IllegalArgumentException e) {
        result = e.getMessage();
      }
      helper.assertTrue("data size limit exceeded".equals(result), "data inflating past the limit gave " + (result instanceof byte[] b ? b.length + " bytes" : result));
    } finally {
      battery.node.remove();
      card.node.remove();
    }
    helper.succeed();
  }

  /** A linked card's channel, as an item (in an inventory read by a transposer), is its own (always "creative"). */
  @GameTest(template = EMPTY)
  public static void linkedCardItemsShowTheirChannel(final GameTestHelper helper) {
    final ItemStack card = item(Constants.ItemName.LinkedCard);
    Item.getDataTag(card).putString(NS + "tunnel", "oc-test-channel");
    final Map<Object, Object> output = new HashMap<>();
    new li.cil.oc.core.impl.integration.opencomputers.ConverterLinkedCard().convert(card, output);
    helper.assertTrue("oc-test-channel".equals(output.get("linkChannel")), "the linked card's channel was given as " + output.get("linkChannel"));
    helper.succeed();
  }
}
