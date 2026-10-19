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

  private static final String HEAD_LUA = "local gpu = component.proxy(component.list('gpu')())\n"
      + "while not component.list('screen')() do computer.pullSignal(0.1) end\n"
      + "gpu.bind(component.list('screen')())\n"
      + "local w, h = gpu.getResolution()\n";

  /**
   * bitblt clips huge and negative values without wrapping around: a negative size draws nothing (it drew the page, for
   * free), huge positions come back true (they threw), and offsets far left of both buffers still line up (the size
   * cap cut it to nothing).
   */
  @GameTest(template = EMPTY, timeoutTicks = 400)
  public static void bitbltClipsHugeAndNegativeValues(final GameTestHelper helper) {
    runCase(helper, HEAD_LUA
      + "local page = gpu.allocateBuffer(4, 2)\n"
      + "gpu.setActiveBuffer(page) gpu.fill(1, 1, 4, 2, 'X') gpu.setActiveBuffer(0)\n"
      + "gpu.fill(1, 1, w, h, ' ')\n"
      + "local out = {}\n"
      + "gpu.bitblt(0, 1, 1, -math.huge, -math.huge, page, 1, 1)\n"
      + "out[#out + 1] = gpu.get(1, 1)\n"
      + "local ok, r = pcall(gpu.bitblt, 0, math.huge, 1, math.huge, 1, page, 1, 1)\n"
      + "out[#out + 1] = tostring(ok) .. tostring(r)\n"
      + "ok, r = pcall(gpu.bitblt, 0, 1, 1, nil, 1, page, -math.huge, 1)\n"
      + "out[#out + 1] = tostring(ok) .. tostring(r)\n"
      + "ok, r = pcall(gpu.bitblt, 0, math.huge, 1, nil, nil, 0, -math.huge, 1)\n"
      + "out[#out + 1] = tostring(ok) .. tostring(r)\n"
      // the same buffer, an offset beyond it: nothing moves (wrapped around, it cleared the wide character at 4)
      + "gpu.set(4, 1, unicode.char(0x4E2D)) gpu.bitblt(0, 5, 1, 3, 1, 0, -math.huge, 1)\n"
      + "out[#out + 1] = tostring(gpu.get(4, 1) == unicode.char(0x4E2D)) gpu.set(4, 1, '  ')\n"
      + "gpu.bitblt(0, -60, 1, 1000, 1, page, -60, 1)\n"
      + "out[#out + 1] = gpu.get(1, 1)\n"
      + "error('done:' .. table.concat(out, ','))\n", computer -> {
      final String error = String.valueOf(computer.machine().lastError());
      helper.assertTrue(error.endsWith("done: ,truetrue,truetrue,truetrue,true,X"), "negative size, huge positions, far-left offsets: " + error);
      helper.succeed();
    });
  }

  /**
   * copy writes nothing for a target wholly off the screen (its edge column or row got a cell from outside the source),
   * a wide character copied into the last column does not throw, and fill takes a huge width (it filled nothing).
   */
  @GameTest(template = EMPTY, timeoutTicks = 400)
  public static void copyAndFillStayWithinTheirRectangles(final GameTestHelper helper) {
    runCase(helper, HEAD_LUA
      + "gpu.fill(1, 1, w, h, ' ')\n"
      + "local out = {}\n"
      + "gpu.set(11, 1, 'Z') gpu.copy(1, 1, 10, 1, -10, 0)\n"
      + "out[#out + 1] = gpu.get(1, 1)\n"
      + "gpu.set(1, 4, 'Q') gpu.copy(1, 1, 10, 3, 0, -3)\n"
      + "out[#out + 1] = gpu.get(1, 1)\n"
      + "gpu.set(w - 1, 2, unicode.char(0x4E2D))\n"
      + "local ok, r = pcall(gpu.copy, w - 1, 2, 1, 1, 1, 0)\n"
      + "out[#out + 1] = tostring(ok) .. tostring(r)\n"
      // from the smallest column: nothing to copy (it came from the largest, and cleared the wide character at 4)
      + "gpu.set(4, 3, unicode.char(0x4E2D)) gpu.copy(-math.huge, 3, 3, 1, -2147483643, 0)\n"
      + "out[#out + 1] = tostring(gpu.get(4, 3) == unicode.char(0x4E2D))\n"
      + "local page = gpu.allocateBuffer(8, 1)\n"
      + "gpu.setActiveBuffer(page)\n"
      + "gpu.fill(2, 1, math.huge, 1, 'X')\n"
      + "out[#out + 1] = gpu.get(2, 1) .. gpu.get(8, 1)\n"
      + "gpu.setActiveBuffer(0)\n"
      + "error('done:' .. table.concat(out, ','))\n", computer -> {
      final String error = String.valueOf(computer.machine().lastError());
      helper.assertTrue(error.endsWith("done: , ,truetrue,true,XX"), "copy off the screen, a wide character at the edge, from far left, a huge fill: " + error);
      helper.succeed();
    });
  }

  /** Text set at the smallest column or row is off the screen, not an error (the offset overflowed and it threw). */
  @GameTest(template = EMPTY, timeoutTicks = 400)
  public static void textFarOffTheEdgeIsSkipped(final GameTestHelper helper) {
    runCase(helper, HEAD_LUA
      + "local ok1, r1 = pcall(gpu.set, -2147483647, 1, 'abc')\n"
      + "local ok2, r2 = pcall(gpu.set, 1, -2147483647, 'abc', true)\n"
      + "error('done:' .. tostring(ok1) .. tostring(r1) .. ',' .. tostring(ok2) .. tostring(r2))\n", computer -> {
      final String error = String.valueOf(computer.machine().lastError());
      helper.assertTrue(error.endsWith("done:truetrue,truetrue"), "text at the smallest column and row: " + error);
      helper.succeed();
    });
  }

  private static li.cil.oc.core.impl.util.TextBuffer buffer(final int w, final int h) {
    return new li.cil.oc.core.impl.util.TextBuffer(w, h, li.cil.oc.core.impl.util.PackedColor.SingleBitFormat.INSTANCE);
  }

  /**
   * A write that changes only the other half of a wide character counts as a change (it did not: nothing was sent, and
   * the clients kept showing the wide character the server had cleared).
   */
  @GameTest(template = EMPTY)
  public static void writesNextToAWideCharacterAreChanges(final GameTestHelper helper) {
    final String wide = new String(Character.toChars(0x4E2D));
    final var filled = buffer(4, 1);
    filled.set(0, 0, wide, false);
    helper.assertTrue(filled.fill(1, 0, 1, 1, ' '), "filling the other half of a wide character changed nothing");
    helper.assertTrue(filled.get(0, 0) == ' ', "the wide character is still there");
    final var set = buffer(4, 1);
    set.set(0, 0, wide, false);
    helper.assertTrue(set.set(1, 0, " ", false), "writing the other half of a wide character changed nothing");
    final var cut = buffer(4, 1);
    cut.set(0, 0, wide, false);
    cut.buffer[0][1] = 'A'; // as a bitblt cutting a wide character leaves it
    helper.assertTrue(cut.set(0, 0, wide, false), "writing a wide character again over its cut other half changed nothing");
    helper.assertTrue(cut.get(1, 0) == ' ', "the other half of the wide character is " + (char) cut.get(1, 0));
    helper.succeed();
  }

  /**
   * A copy from left of the buffer clears the wide character whose other half it overwrote (it cleared the one before
   * the target's edge, which it never wrote, and left this one without its other half).
   */
  @GameTest(template = EMPTY)
  public static void copyClearsTheWideCharacterItCut(final GameTestHelper helper) {
    final var b = buffer(8, 5);
    b.set(2, 1, new String(Character.toChars(0x4E2D)), false);
    b.copy(-3, -2, 15, 5, 3, 1);
    helper.assertTrue(b.get(2, 1) == ' ', "the wide character at 2 lost its other half and was kept: " + Integer.toHexString(b.get(2, 1)));
    helper.succeed();
  }

  /** A copy that writes nothing in a row clears nothing there (it cleared the wide character before the target). */
  @GameTest(template = EMPTY)
  public static void copyWritingNothingClearsNothing(final GameTestHelper helper) {
    final int wide = 0x4E2D;
    final String w = new String(Character.toChars(wide));
    final var fromRight = buffer(8, 1);
    fromRight.set(3, 0, w, false);
    final boolean rightChanged = fromRight.copy(8, 0, 2, 1, -4, 0); // the source right of the buffer
    final var fromLeft = buffer(8, 1);
    fromLeft.set(0, 0, w, false);
    final boolean leftChanged = fromLeft.copy(-5, 0, 3, 1, 6, 0); // the source left of the buffer
    helper.assertTrue(fromRight.get(3, 0) == wide && !rightChanged, "a copy from right of the buffer cleared the wide character: " + rightChanged);
    helper.assertTrue(fromLeft.get(0, 0) == wide && !leftChanged, "a copy from left of the buffer cleared the wide character: " + leftChanged);
    helper.succeed();
  }

  /** A screen placed against a powered block stays on; power coming again toggles it (the first reading toggled it off). */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void screenPlacedAgainstPowerStaysOn(final GameTestHelper helper) {
    helper.setBlock(AT.east(), net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK);
    helper.setBlock(AT, Blocks.SCREEN_TIER_1.get());
    helper.runAfterDelay(5, () -> {
      final var screen = (li.cil.oc.core.impl.common.blockentity.Screen) helper.getBlockEntity(AT);
      final boolean placed = screen.buffer.getPowerState();
      helper.setBlock(AT.east(), net.minecraft.world.level.block.Blocks.AIR);
      helper.runAfterDelay(2, () -> {
        helper.setBlock(AT.east(), net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(2, () -> {
          helper.assertTrue(placed, "a screen placed against a powered block turned itself off");
          helper.assertTrue(!screen.buffer.getPowerState(), "power coming again did not toggle the screen");
          helper.succeed();
        });
      });
    });
  }

  /** A screen placed against a powered block, then powered on another side too, stays on (it toggled as if it had no input). */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void screenPoweredOnASecondSideStaysOn(final GameTestHelper helper) {
    helper.setBlock(AT.east(), net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK);
    helper.setBlock(AT, Blocks.SCREEN_TIER_1.get());
    helper.runAfterDelay(5, () -> {
      final var screen = (li.cil.oc.core.impl.common.blockentity.Screen) helper.getBlockEntity(AT);
      helper.setBlock(AT.west(), net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK);
      helper.runAfterDelay(2, () -> {
        helper.assertTrue(screen.buffer.getPowerState(), "power on a second side turned the screen off");
        helper.succeed();
      });
    });
  }

  /**
   * A two-screen display powered behind one screen stays toggled when power comes behind the other too (each screen
   * kept its own flag: the second one toggled it back as if the display had no input).
   */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void multiBlockScreenPoweredBehindBothScreensTogglesOnce(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.SCREEN_TIER_1.get());
    helper.setBlock(AT.east(), Blocks.SCREEN_TIER_1.get());
    helper.runAfterDelay(10, () -> {
      final var a = (li.cil.oc.core.impl.common.blockentity.Screen) helper.getBlockEntity(AT);
      helper.assertTrue(a.screens.size() == 2, "the two screens did not merge: " + a.screens.size());
      final boolean before = a.origin.buffer.getPowerState();
      helper.setBlock(AT.below(), net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK);
      helper.runAfterDelay(2, () -> {
        final boolean once = a.origin.buffer.getPowerState();
        helper.setBlock(AT.east().below(), net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(2, () -> {
          helper.assertTrue(once != before, "power behind one screen did not toggle the display");
          helper.assertTrue(a.origin.buffer.getPowerState() == once, "power behind the second screen toggled the display again");
          helper.succeed();
        });
      });
    });
  }

  /** A screen left on its own (its display split) marks its new redstone flag to be saved (it changed only in memory). */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void splitScreenSavesItsRedstoneFlag(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.SCREEN_TIER_1.get());
    helper.setBlock(AT.east(), Blocks.SCREEN_TIER_1.get());
    helper.setBlock(AT.east().below(), net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK);
    helper.runAfterDelay(10, () -> {
      final var a = (li.cil.oc.core.impl.common.blockentity.Screen) helper.getBlockEntity(AT);
      helper.assertTrue(a.screens.size() == 2 && a.origin.hadRedstoneInput, "the powered display did not form");
      final boolean wasOrigin = a.origin == a;
      final java.util.concurrent.atomic.AtomicBoolean mark;
      try {
        final var field = li.cil.oc.core.impl.common.blockentity.traits.BlockEntity.class.getDeclaredField("changeScheduled");
        field.setAccessible(true);
        mark = (java.util.concurrent.atomic.AtomicBoolean) field.get(a);
      } catch (ReflectiveOperationException e) {
        throw new RuntimeException(e);
      }
      a.hadRedstoneInput = true; // as the display's flag (it is a's when a is the origin)
      mark.set(false);
      a.checkMultiBlock(); // what its partner's removal does
      helper.assertTrue(!a.hadRedstoneInput, "a screen without input of its own kept the display's flag");
      helper.assertTrue(mark.get(), "the changed flag was not marked to be saved (origin before: " + wasOrigin + ")");
      helper.succeed();
    });
  }

  /**
   * A display that comes back with power that came while it was away toggles once, as a display (its screens read their
   * inputs before it formed again: the powered screen toggled its own hidden buffer, and the display stayed as it was).
   */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void displayReloadedWithNewPowerToggles(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.SCREEN_TIER_1.get());
    helper.setBlock(AT.east(), Blocks.SCREEN_TIER_1.get());
    helper.runAfterDelay(10, () -> {
      final var a = (li.cil.oc.core.impl.common.blockentity.Screen) helper.getBlockEntity(AT);
      final var b = (li.cil.oc.core.impl.common.blockentity.Screen) helper.getBlockEntity(AT.east());
      helper.assertTrue(a.screens.size() == 2, "the two screens did not merge");
      final boolean before = a.origin.buffer.getPowerState();
      final var level = helper.getLevel();
      final BlockPos absA = helper.absolutePos(AT);
      final BlockPos absB = helper.absolutePos(AT.east());
      a.markUnloading();
      b.markUnloading();
      final var savedA = a.saveWithFullMetadata();
      final var savedB = b.saveWithFullMetadata();
      level.removeBlockEntity(absA);
      level.removeBlockEntity(absB);
      // power behind the second screen while the display is away (no block update reaches it)
      level.setBlock(helper.absolutePos(AT.east().below()), net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState(), 2);
      final var la = (li.cil.oc.core.impl.common.blockentity.Screen) net.minecraft.world.level.block.entity.BlockEntity.loadStatic(absA, level.getBlockState(absA), savedA);
      final var lb = (li.cil.oc.core.impl.common.blockentity.Screen) net.minecraft.world.level.block.entity.BlockEntity.loadStatic(absB, level.getBlockState(absB), savedB);
      level.getChunkAt(absA).addAndRegisterBlockEntity(la);
      level.getChunkAt(absB).addAndRegisterBlockEntity(lb);
      helper.runAfterDelay(10, () -> {
        helper.assertTrue(la.screens.size() == 2, "the display did not form again");
        helper.assertTrue(la.origin.buffer.getPowerState() != before, "power that came while the display was away did not toggle it");
        helper.succeed();
      });
    });
  }
}
