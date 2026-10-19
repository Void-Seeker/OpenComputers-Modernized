package li.cil.oc.neoforge.gametest;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import li.cil.oc.core.Constants;
import li.cil.oc.core.impl.common.blockentity.Screen;
import li.cil.oc.core.impl.server.machine.ArgumentsImpl;
import li.cil.oc.neoforge.OpenComputers;
import li.cil.oc.neoforge.common.init.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Screens and GPUs do what they are told at their edges: walking, precision, palette colours, partly visible text,
 * tall resolutions, and copies that cannot pay for themselves.
 */
@GameTestHolder(OpenComputers.ID)
@PrefixGameTestTemplate(false)
public final class ScreenTests {
  private static final String EMPTY = "empty";
  private static final BlockPos AT = new BlockPos(2, 2, 2);

  private ScreenTests() {
  }

  private static li.cil.oc.api.machine.Context context() {
    return (li.cil.oc.api.machine.Context) java.lang.reflect.Proxy.newProxyInstance(li.cil.oc.api.machine.Context.class.getClassLoader(),
      new Class<?>[]{li.cil.oc.api.machine.Context.class}, (proxy, method, args) -> method.getReturnType() == boolean.class ? false : null);
  }

  private static Screen tier3(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.SCREEN_TIER_3.get());
    return (Screen) helper.getBlockEntity(AT);
  }

  /** A tier 3 screen once it is in a network (its buffer sends to clients under its node's address). */
  private static void withTier3(final GameTestHelper helper, final java.util.function.Consumer<Screen> then) {
    final Screen screen = tier3(helper);
    helper.runAfterDelay(3, () -> {
      screen.buffer.setColorDepth(li.cil.oc.api.internal.TextBuffer.ColorDepth.EightBit);
      then.accept(screen);
    });
  }

  /** A screen walked on again later signals again, with the player's name (once per entity and spot, ever; the name was nil). */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void walkingOnAScreenSignalsEachStep(final GameTestHelper helper) {
    final Screen screen = tier3(helper);
    final List<Object[]> walks = new ArrayList<>();
    final var listener = li.cil.oc.api.Network.newNode(new li.cil.oc.api.network.Environment() {
      @Override
      public li.cil.oc.api.network.Node node() {
        return null;
      }

      @Override
      public void onConnect(li.cil.oc.api.network.Node n) {
      }

      @Override
      public void onDisconnect(li.cil.oc.api.network.Node n) {
      }

      @Override
      public void onMessage(li.cil.oc.api.network.Message message) {
        if ("computer.signal".equals(message.name()) && message.data().length > 0 && "walk".equals(message.data()[0])) walks.add(message.data());
      }
    }, li.cil.oc.api.network.Visibility.Network).create();
    final var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "oc-walker"));
    helper.runAfterDelay(3, () -> {
      screen.origin.node.connect(listener);
      screen.walk(player);
      screen.walk(player); // the next movement tick on the same spot: the same step
      helper.runAfterDelay(20, () -> {
        screen.walk(player);
        try {
          helper.assertTrue(walks.size() == 2, "two steps onto the screen made " + walks.size() + " walk signals");
          helper.assertTrue(walks.get(0).length > 3 && "oc-walker".equals(walks.get(0)[3]), "the walk signal names " + java.util.Arrays.toString(walks.get(0)));
        } finally {
          listener.remove();
        }
        helper.succeed();
      });
    });
  }

  /** setPrecise works on a tier 3 screen (it failed on every screen). */
  @GameTest(template = EMPTY)
  public static void tier3ScreensCanBePrecise(final GameTestHelper helper) {
    withTier3(helper, screen -> {
    try {
      screen.buffer.setPrecise(context(), new ArgumentsImpl(new Object[]{true}));
    } catch (Throwable t) {
      helper.fail("setPrecise on a tier 3 screen failed: " + t);
    }
    helper.assertTrue(screen.buffer.isPrecise(context(), new ArgumentsImpl(new Object[0]))[0] == Boolean.TRUE, "the tier 3 screen is not precise after setPrecise(true)");
    helper.succeed();
    });
  }

  /** A palette colour after the same RGB value is a change (black stayed RGB: palette index 0 was ignored). */
  @GameTest(template = EMPTY)
  public static void paletteColoursAreNotRgbColours(final GameTestHelper helper) {
    withTier3(helper, screen -> {
      screen.buffer.setBackgroundColor(0, false);
      screen.buffer.setBackgroundColor(0, true);
      helper.assertTrue(screen.buffer.isBackgroundFromPalette(), "palette index 0 after RGB 0 left the background RGB");
      helper.succeed();
    });
  }

  /** Text that starts left of the screen shows its visible part (x below 1 drew the whole string from column 1). */
  @GameTest(template = EMPTY)
  public static void textLeftOfTheScreenIsCut(final GameTestHelper helper) {
    withTier3(helper, screen -> {
      screen.buffer.set(-2, 0, "abcdef", false);
      final int first = screen.buffer.getCodePoint(0, 0);
      helper.assertTrue(first == 'c', "\"abcdef\" set at column -1 starts with '" + (char) first + "' on screen");
      helper.succeed();
    });
  }

  /** A tall resolution within the screen's area is taken (only resolutions within width and height were). */
  @GameTest(template = EMPTY)
  public static void tallResolutionsFit(final GameTestHelper helper) {
    withTier3(helper, screen -> {
    final int w = screen.buffer.getMaximumWidth() / 2;
    final int h = screen.buffer.getMaximumHeight() * 2;
    try {
      screen.buffer.setResolution(w, h);
    } catch (Throwable t) {
      helper.fail("a " + w + "x" + h + " resolution on a " + screen.buffer.getMaximumWidth() + "x" + screen.buffer.getMaximumHeight() + " screen: " + t);
    }
    helper.succeed();
    });
  }

  /** A bitblt with a negative size costs nothing and gives nothing (the cost went negative: the GPU made energy). */
  @GameTest(template = EMPTY, timeoutTicks = 400)
  public static void bitbltNeverGivesEnergy(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.CASE_TIER_1.get());
    helper.setBlock(AT.above(), Blocks.SCREEN_TIER_1.get());
    final var computer = (li.cil.oc.core.impl.common.blockentity.Case) helper.getBlockEntity(AT);
    final String lua = "local gpu = component.proxy(component.list('gpu')())\n"
      + "while not component.list('screen')() do computer.pullSignal(0.1) end\n"
      + "gpu.bind(component.list('screen')())\n"
      + "if computer.energy() > computer.maxEnergy() / 2 then error('the buffer is too full to see a gain') end\n"
      + "local before = computer.energy()\n"
      + "for i = 1, 20 do pcall(gpu.bitblt, 0, 1, 1, -1000000, 20) end\n"
      + "error('done:' .. tostring(computer.energy() <= before))\n";
    for (final net.minecraft.world.item.ItemStack part : List.of(li.cil.oc.api.Items.get(Constants.ItemName.CPUTier1).createItemStack(1),
      li.cil.oc.api.Items.get(Constants.ItemName.RAMTier2).createItemStack(1), li.cil.oc.api.Items.get(Constants.ItemName.GraphicsCardTier1).createItemStack(1),
      li.cil.oc.api.Items.registerEEPROM("bitblt", lua.getBytes(java.nio.charset.StandardCharsets.UTF_8), null, false))) {
      int slot = -1;
      for (int i = 0; i < computer.getContainerSize() && slot < 0; i++) {
        if (computer.getItem(i).isEmpty() && computer.canPlaceItem(i, part)) slot = i;
      }
      helper.assertTrue(slot >= 0, "no case slot takes " + part);
      computer.setItem(slot, part);
    }
    final boolean[] started = {false};
    final int[] ticks = {0};
    helper.onEachTick(() -> {
      if (started[0] || ticks[0]++ > 100) return;
      // just enough to start and run: a full buffer would hide a gain
      final var connector = (li.cil.oc.api.network.Connector) computer.machine().node();
      if (connector.globalBuffer() < 40) connector.changeBuffer(40 - connector.globalBuffer());
      started[0] = computer.machine().start();
    });
    helper.runAfterDelay(300, () -> {
      helper.assertTrue(started[0], "the case never started: " + computer.machine().lastError());
      helper.assertTrue(String.valueOf(computer.machine().lastError()).contains("done:true"), "after bitblts of negative size: " + computer.machine().lastError());
      helper.succeed();
    });
  }

  /** A vertical string reaching above the screen is cut there, one entirely above changes nothing (it threw). */
  @GameTest(template = EMPTY)
  public static void verticalTextAboveTheScreenIsCut(final GameTestHelper helper) {
    withTier3(helper, screen -> {
      try {
        screen.buffer.set(0, -5, "ab", true);
      } catch (Throwable t) {
        helper.fail("a vertical string above the screen threw " + t);
      }
      screen.buffer.set(0, -1, "abc", true);
      helper.assertTrue(screen.buffer.getCodePoint(0, 0) == 'b' && screen.buffer.getCodePoint(0, 1) == 'c', "\"abc\" from row 0 starts with '" + (char) screen.buffer.getCodePoint(0, 0) + "'");
      helper.succeed();
    });
  }

  /** A case with a GPU and a tier 1 screen above it runs the Lua; then gets the case once it stopped (or 300 ticks). */
  private static void runCase(final GameTestHelper helper, final String lua, final java.util.function.Consumer<li.cil.oc.core.impl.common.blockentity.Case> then) {
    helper.setBlock(AT, Blocks.CASE_TIER_1.get());
    helper.setBlock(AT.above(), Blocks.SCREEN_TIER_1.get());
    final var computer = (li.cil.oc.core.impl.common.blockentity.Case) helper.getBlockEntity(AT);
    for (final net.minecraft.world.item.ItemStack part : List.of(li.cil.oc.api.Items.get(Constants.ItemName.CPUTier1).createItemStack(1),
      li.cil.oc.api.Items.get(Constants.ItemName.RAMTier2).createItemStack(1), li.cil.oc.api.Items.get(Constants.ItemName.GraphicsCardTier1).createItemStack(1),
      li.cil.oc.api.Items.registerEEPROM("screen test", lua.getBytes(java.nio.charset.StandardCharsets.UTF_8), null, false))) {
      int slot = -1;
      for (int i = 0; i < computer.getContainerSize() && slot < 0; i++) {
        if (computer.getItem(i).isEmpty() && computer.canPlaceItem(i, part)) slot = i;
      }
      helper.assertTrue(slot >= 0, "no case slot takes " + part);
      computer.setItem(slot, part);
    }
    final boolean[] started = {false};
    final int[] ticks = {0};
    helper.onEachTick(() -> {
      if (started[0] || ticks[0]++ > 100) return;
      final var connector = (li.cil.oc.api.network.Connector) computer.machine().node();
      if (connector.globalBuffer() < 200) connector.changeBuffer(200 - connector.globalBuffer());
      started[0] = computer.machine().start();
    });
    helper.runAfterDelay(250, () -> {
      helper.assertTrue(started[0], "the case never started: " + computer.machine().lastError());
      then.accept(computer);
    });
  }

  /** A bitblt starting left of the screen draws up to its right edge (the size was cut to the screen first: the last columns stayed). */
  @GameTest(template = EMPTY, timeoutTicks = 400)
  public static void bitbltFromLeftOfTheScreenReachesItsRightEdge(final GameTestHelper helper) {
    runCase(helper, "local gpu = component.proxy(component.list('gpu')())\n"
      + "while not component.list('screen')() do computer.pullSignal(0.1) end\n"
      + "gpu.bind(component.list('screen')())\n"
      + "local w, h = gpu.getResolution()\n"
      + "local page = gpu.allocateBuffer(w + 10, 2)\n"
      + "gpu.setActiveBuffer(page) gpu.fill(1, 1, w + 10, 2, 'X') gpu.setActiveBuffer(0)\n"
      + "gpu.fill(1, 1, w, h, ' ')\n"
      + "gpu.bitblt(0, -9, 1, w + 10, 2, page, 1, 1)\n"
      + "error('done:' .. gpu.get(w, 1))\n", computer -> {
      helper.assertTrue(String.valueOf(computer.machine().lastError()).contains("done:X"), "the screen's last column after the bitblt: " + computer.machine().lastError());
      helper.succeed();
    });
  }

  /** A bitblt of a huge size draws what fits (the size wrapped around in the bounds and it threw). */
  @GameTest(template = EMPTY, timeoutTicks = 400)
  public static void bitbltOfAHugeSizeDrawsWhatFits(final GameTestHelper helper) {
    runCase(helper, "local gpu = component.proxy(component.list('gpu')())\n"
      + "while not component.list('screen')() do computer.pullSignal(0.1) end\n"
      + "gpu.bind(component.list('screen')())\n"
      + "local w, h = gpu.getResolution()\n"
      + "local page = gpu.allocateBuffer(4, 2)\n"
      + "local ok, r, why = pcall(gpu.bitblt, 0, w + 2, 1, math.huge, 1, page, 1, 1)\n"
      + "error('done:' .. tostring(ok) .. ' ' .. tostring(r) .. ' ' .. tostring(why))\n", computer -> {
      // a Java exception with a message comes back as nil and the message
      helper.assertTrue(String.valueOf(computer.machine().lastError()).contains("done:true true"), "a huge bitblt: " + computer.machine().lastError());
      helper.succeed();
    });
  }
}
