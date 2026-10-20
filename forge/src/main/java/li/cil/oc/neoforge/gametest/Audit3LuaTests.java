package li.cil.oc.neoforge.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import li.cil.oc.core.Constants;
import li.cil.oc.core.impl.server.machine.ArgumentsImpl;
import li.cil.oc.neoforge.OpenComputers;
import li.cil.oc.neoforge.common.init.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.VanillaGameEvent;
import net.minecraftforge.event.level.NoteBlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * What Lua sees from callbacks, as OC 1.12 (third audit): empty slots, numbers, labels, single tables, argument errors,
 * and the vanilla blocks driven through adapters.
 */
@GameTestHolder(OpenComputers.ID)
@PrefixGameTestTemplate(false)
public final class Audit3LuaTests {
  private static final String EMPTY = "empty";
  private static final BlockPos AT = new BlockPos(2, 2, 2);
  private static final ArgumentsImpl NO_ARGS = new ArgumentsImpl(new Object[0]);

  private Audit3LuaTests() {
  }

  private static li.cil.oc.api.machine.Context context() {
    return (li.cil.oc.api.machine.Context) java.lang.reflect.Proxy.newProxyInstance(li.cil.oc.api.machine.Context.class.getClassLoader(),
      new Class<?>[]{li.cil.oc.api.machine.Context.class}, (proxy, method, args) -> method.getReturnType() == boolean.class ? false : null);
  }

  private static li.cil.oc.api.network.EnvironmentHost host(final GameTestHelper helper, final BlockPos pos) {
    final BlockPos abs = helper.absolutePos(pos);
    return new li.cil.oc.api.network.EnvironmentHost() {
      @Override
      public net.minecraft.world.level.Level world() {
        return helper.getLevel();
      }

      @Override
      public double xPosition() {
        return abs.getX() + 0.5;
      }

      @Override
      public double yPosition() {
        return abs.getY() + 0.5;
      }

      @Override
      public double zPosition() {
        return abs.getZ() + 0.5;
      }

      @Override
      public void markChanged() {
      }
    };
  }

  /** Runs the program on a microcontroller with the parts; then checks what it reported (error("done:" ..)). */
  private static void runReporting(final GameTestHelper helper, final List<String> parts, final String program, final Consumer<String> check) {
    TestMachines.runMicrocontroller(helper, AT, parts, program, mc -> TestMachines.when(helper, () -> !mc.machine().isRunning(), 100, () -> {
      final String error = String.valueOf(mc.machine().lastError());
      final int at = error.indexOf("done:");
      helper.assertTrue(at >= 0, "the program did not report: " + error);
      check.accept(error.substring(at + 5));
      helper.succeed();
    }));
  }

  // ----------------------------------------------------------------------- //
  // values as Lua sees them

  /**
   * An empty slot and an empty database entry are nil, as OC 1.12 (they were "air" tables), and a label is the item's
   * name (it was the chat form, "[Diamond]").
   */
  @GameTest(template = EMPTY, timeoutTicks = 300)
  public static void emptySlotsAreNilAndLabelsPlain(final GameTestHelper helper) {
    helper.setBlock(AT.north(), net.minecraft.world.level.block.Blocks.CHEST);
    ((net.minecraft.world.Container) helper.getBlockEntity(AT.north())).setItem(0, new ItemStack(Items.DIAMOND));
    runReporting(helper, List.of(Constants.ItemName.CPUTier1, Constants.ItemName.RAMTier2, Constants.BlockName.Transposer, Constants.ItemName.DatabaseUpgradeTier1),
      "local tp = component.proxy(component.list('transposer')())\n"
        + "local db = component.proxy(component.list('database')())\n"
        + "error('done:' .. type(tp.getStackInSlot(2, 2)) .. ',' .. type(db.get(1)) .. ',' .. tp.getStackInSlot(2, 1).label, 0)\n",
      result -> helper.assertTrue("nil,nil,Diamond".equals(result), "an empty slot, an empty database entry, a diamond's label: " + result));
  }

  /** A robot's empty slot is nil, and so is an empty fluid (in a bucket, an internal tank); a slot without a container says so. */
  @GameTest(template = EMPTY, timeoutTicks = 400)
  public static void robotSeesEmptySlotsAndFluidsAsNil(final GameTestHelper helper) {
    TestMachines.runRobot(helper, AT, List.of(Constants.ItemName.InventoryUpgrade, Constants.ItemName.InventoryControllerUpgrade,
      Constants.ItemName.TankUpgrade, Constants.ItemName.TankControllerUpgrade), robot -> {
      robot.mainInventory().setItem(0, new ItemStack(Items.BUCKET));
      robot.mainInventory().setItem(2, new ItemStack(Items.WATER_BUCKET));
      robot.mainInventory().setItem(3, new ItemStack(Items.DIAMOND));
    }, "local ic = component.proxy(component.list('inventory_controller')())\n"
      + "local tc = component.proxy(component.list('tank_controller')())\n"
      + "local f, why = tc.getFluidInTankInSlot(2)\n"
      + "error('done:' .. type(ic.getStackInInternalSlot(2)) .. ',' .. ic.getStackInInternalSlot(4).label .. ',' .. type(tc.getFluidInTankInSlot(1))\n"
      + "  .. ',' .. type(f) .. ':' .. tostring(why) .. ',' .. tc.getFluidInTankInSlot(3).name .. ',' .. type(tc.getFluidInInternalTank(1)), 0)\n",
      20, (robot, result) -> {
        helper.assertTrue("nil,Diamond,nil,nil:item is not a fluid container,minecraft:water,nil".equals(result),
          "an empty slot, a diamond, an empty bucket, no container, a water bucket, the empty tank: " + result);
        helper.succeed();
      });
  }

  /** An empty slot of an ME interface's configuration is nil (it was an "air" table). */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void emptyInterfaceConfigurationIsNil(final GameTestHelper helper) {
    helper.setBlock(AT, ForgeRegistries.BLOCKS.getValue(new ResourceLocation("ae2", "interface")));
    final var iface = (appeng.blockentity.misc.InterfaceBlockEntity) helper.getBlockEntity(AT);
    final Object[] config = new li.cil.oc.neoforge.integration.appeng.DriverBlockInterface.Environment(iface).getInterfaceConfiguration(context(), new ArgumentsImpl(new Object[]{1}));
    helper.assertTrue(config.length == 1 && config[0] == null, "an empty configuration slot gave " + java.util.Arrays.toString(config));
    helper.succeed();
  }

  /**
   * Whole numbers are Lua integers under Lua 5.3, as OC 1.12 (they were floats: OpenOS printed "50.0 16.0" and
   * tostring(rs.getInput(side)) == "15" was false): sizes and colours inside the GPU's screen calls, an old palette
   * index, redstone values, EEPROM sizes, file sizes, positions and times.
   */
  @GameTest(template = EMPTY, timeoutTicks = 400)
  public static void wholeNumbersAreLuaIntegers(final GameTestHelper helper) {
    final String lua = "local gpu = component.proxy(component.list('gpu')())\n"
      + "while not component.list('screen')() do computer.pullSignal(0.1) end\n"
      + "gpu.bind((component.list('screen')()))\n"
      + "local rs = component.proxy(component.list('redstone')())\n"
      + "local eeprom = component.proxy(component.list('eeprom')())\n"
      + "local fs = component.proxy(computer.tmpAddress())\n"
      + "local h = fs.open('/n', 'w') fs.write(h, 'hello') fs.close(h)\n"
      + "h = fs.open('/n', 'r') local at = fs.seek(h, 'set', 2) fs.close(h)\n"
      + "gpu.setForeground(3, true) local _, oldIndex = gpu.setForeground(0xFFFFFF)\n"
      + "local w, hh = gpu.getResolution() local vw, vh = gpu.getViewport() local mw, mh = gpu.maxResolution()\n"
      + "local v = {w, hh, vw, vh, mw, mh, gpu.getDepth(), gpu.maxDepth(), oldIndex, gpu.getActiveBuffer(), rs.getInput(3), rs.getOutput(3),\n"
      + "  rs.setWakeThreshold(5), rs.getWakeThreshold(), eeprom.getSize(), eeprom.getDataSize(), fs.size('/n'), fs.spaceUsed(), fs.lastModified('/n'), at}\n"
      + "local t = {} for i = 1, 20 do t[i] = math.type(v[i]) or type(v[i]) end\n"
      + "error('done:' .. _VERSION .. ' ' .. table.concat(t, ',') .. ' ' .. tostring(w) .. 'x' .. tostring(hh), 0)\n";
    helper.setBlock(AT, Blocks.CASE_TIER_3.get());
    helper.setBlock(AT.above(), Blocks.SCREEN_TIER_2.get());
    final var computer = (li.cil.oc.core.impl.common.blockentity.Case) helper.getBlockEntity(AT);
    for (final ItemStack part : List.of(li.cil.oc.api.Items.get(Constants.ItemName.CPUTier1).createItemStack(1),
      li.cil.oc.api.Items.get(Constants.ItemName.RAMTier2).createItemStack(1), li.cil.oc.api.Items.get(Constants.ItemName.GraphicsCardTier2).createItemStack(1),
      li.cil.oc.api.Items.get(Constants.ItemName.RedstoneCardTier1).createItemStack(1),
      li.cil.oc.api.Items.registerEEPROM("numbers", lua.getBytes(java.nio.charset.StandardCharsets.UTF_8), null, false))) {
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
    helper.runAfterDelay(100, () -> TestMachines.when(helper, () -> started[0] && !computer.machine().isRunning(), 200, () -> {
      final String error = String.valueOf(computer.machine().lastError());
      final String integers = String.join(",", java.util.Collections.nCopies(20, "integer"));
      helper.assertTrue(error.contains("done:Lua 5.3 " + integers + " 80x25"), "the types under Lua 5.3: " + error);
      helper.succeed();
    }));
  }

  /**
   * A whole number prints whole on every architecture: native Lua 5.2, 5.3 and 5.4 and LuaJ (5.3 and 5.4 printed
   * "4096.0" for eeprom.getSize()).
   */
  @GameTest(template = EMPTY, timeoutTicks = 400)
  public static void wholeNumbersPrintWholeOnEveryArchitecture(final GameTestHelper helper) {
    final List<Class<? extends li.cil.oc.api.machine.Architecture>> architectures = List.of(li.cil.oc.core.impl.server.machine.luac.NativeLua52Architecture.class,
      li.cil.oc.core.impl.server.machine.luac.NativeLua53Architecture.class, li.cil.oc.core.impl.server.machine.luac.NativeLua54Architecture.class,
      li.cil.oc.core.impl.server.machine.luaj.LuaJLuaArchitecture.class);
    final List<BlockPos> places = List.of(new BlockPos(0, 1, 0), new BlockPos(4, 1, 0), new BlockPos(0, 1, 4), new BlockPos(4, 1, 4));
    final List<li.cil.oc.core.impl.common.blockentity.Microcontroller> machines = new ArrayList<>();
    final String program = "local e = component.proxy(component.list('eeprom')())\n"
      + "error('done:' .. tostring(e.getSize()) .. ',' .. string.format('%s', e.getDataSize()) .. ';', 0)\n";
    for (int i = 0; i < architectures.size(); i++) {
      final var data = new li.cil.oc.core.impl.common.item.data.MicrocontrollerData();
      final ItemStack cpu = li.cil.oc.api.Items.get(Constants.ItemName.CPUTier1).createItemStack(1);
      ((li.cil.oc.api.driver.item.MutableProcessor) li.cil.oc.api.Driver.driverFor(cpu)).setArchitecture(cpu, architectures.get(i));
      data.components.add(cpu);
      data.components.add(li.cil.oc.api.Items.get(Constants.ItemName.RAMTier2).createItemStack(1));
      data.components.add(ItemStack.EMPTY);
      helper.setBlock(places.get(i), Blocks.MICROCONTROLLER.get());
      final BlockPos abs = helper.absolutePos(places.get(i));
      Blocks.MICROCONTROLLER.get().setPlacedBy(helper.getLevel(), abs, helper.getLevel().getBlockState(abs), net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(helper.getLevel()), data.createItemStack());
      machines.add((li.cil.oc.core.impl.common.blockentity.Microcontroller) helper.getLevel().getBlockEntity(abs));
    }
    helper.runAfterDelay(5, () -> {
      for (final var mc : machines) {
        mc.changeEEPROM(li.cil.oc.api.Items.registerEEPROM("arch", program.getBytes(java.nio.charset.StandardCharsets.UTF_8), null, false));
        ((li.cil.oc.api.network.Connector) mc.snooperNode).changeBuffer(1000);
        helper.assertTrue(mc.machine().start(), "a machine did not start");
      }
      TestMachines.when(helper, () -> machines.stream().noneMatch(mc -> mc.machine().isRunning()), 300, () -> {
        final List<String> reports = new ArrayList<>();
        for (int i = 0; i < machines.size(); i++) {
          final var machine = machines.get(i).machine();
          reports.add(li.cil.oc.api.Machine.getArchitectureName(machine.architecture().getClass()) + " " + machine.lastError());
        }
        helper.assertTrue(reports.stream().allMatch(r -> r.contains("done:4096,256;")), "eeprom.getSize(), getDataSize() printed: " + reports);
        helper.assertTrue(machines.stream().map(mc -> mc.machine().architecture().getClass()).toList().equals(architectures), "the machines ran " + reports);
        helper.succeed();
      });
    });
  }

  /** findWaypoints gives one table of waypoints, as OC 1.12 (each waypoint was a value of its own); none: an empty one. */
  @GameTest(template = EMPTY)
  public static void findWaypointsGivesOneTable(final GameTestHelper helper) {
    final BlockPos abs = helper.absolutePos(AT);
    final var navigation = new li.cil.oc.core.impl.server.component.UpgradeNavigationBase(host(helper, AT)) {
      @Override
      protected boolean consumeEnergy(final double amount) {
        return true;
      }

      @Override
      protected List<li.cil.oc.core.impl.server.component.WaypointInfo> queryWaypoints(final li.cil.oc.core.impl.util.BlockPosition pos, final double range) {
        final List<li.cil.oc.core.impl.server.component.WaypointInfo> found = new ArrayList<>();
        for (final int dx : new int[]{2, -2}) {
          found.add(new li.cil.oc.core.impl.server.component.WaypointInfo(li.cil.oc.core.impl.util.BlockPosition.apply(abs.getX() + dx, abs.getY(), abs.getZ(), helper.getLevel()),
            Direction.UP, 0, "w" + dx, "address" + dx));
        }
        return found;
      }
    };
    final Object[] two = navigation.findWaypoints(context(), new ArgumentsImpl(new Object[]{16.0}));
    helper.assertTrue(two.length == 1 && two[0] instanceof Object[] list && list.length == 2 && list[0] instanceof Map,
      "two waypoints in range gave " + java.util.Arrays.deepToString(two));
    final Object[] none = navigation.findWaypoints(context(), new ArgumentsImpl(new Object[]{0.0}));
    helper.assertTrue(none.length == 1 && none[0] instanceof Object[] list && list.length == 0, "a range of 0 gave " + java.util.Arrays.deepToString(none));
    helper.succeed();
  }

  /** getTrades gives one table of trades, as OC 1.12 (each trade was a value of its own); none in range: an empty one. */
  @GameTest(template = EMPTY)
  public static void getTradesGivesOneTable(final GameTestHelper helper) {
    final var villager = EntityType.VILLAGER.create(helper.getLevel());
    helper.assertTrue(villager != null, "no villager");
    villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
    final BlockPos abs = helper.absolutePos(AT.east());
    villager.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
    helper.getLevel().addFreshEntity(villager);
    try {
      final int offers = villager.getOffers().size();
      helper.assertTrue(offers >= 2, "the farmer has " + offers + " trades");
      final Object[] near = new li.cil.oc.neoforge.server.component.UpgradeTrading(host(helper, AT)).getTrades(context(), NO_ARGS);
      helper.assertTrue(near.length == 1 && near[0] instanceof Object[] list && list.length >= offers, "a farmer's " + offers + " trades came as " + near.length + " values");
      final Object[] far = new li.cil.oc.neoforge.server.component.UpgradeTrading(host(helper, AT.above(60))).getTrades(context(), NO_ARGS);
      helper.assertTrue(far.length == 1 && far[0] instanceof Object[] list && list.length == 0, "no merchant in range gave " + far.length + " values");
    } finally {
      villager.discard();
    }
    helper.succeed();
  }

  // ----------------------------------------------------------------------- //
  // arguments

  /**
   * Argument errors read as OC 1.12's, 1-based with Lua's type names (they were "expected number, got byte[]" and "not
   * enough arguments"), and an optional argument given with the wrong type is an error, not the default.
   */
  @GameTest(template = EMPTY)
  public static void argumentErrorsReadAsOCs(final GameTestHelper helper) {
    final var args = new ArgumentsImpl(new Object[]{"x".getBytes(java.nio.charset.StandardCharsets.UTF_8), 5L, 2.5, true, null, Double.NaN, Map.of()});
    final List<String> problems = new ArrayList<>();
    final java.util.function.BiConsumer<Runnable, String> expect = (call, message) -> {
      try {
        call.run();
        problems.add("no error, expected " + message);
      } catch (IllegalArgumentException e) {
        if (!message.equals(e.getMessage())) problems.add(e.getMessage() + " instead of " + message);
      }
    };
    expect.accept(() -> args.checkDouble(0), "bad argument #1 (number expected, got string)");
    expect.accept(() -> args.checkInteger(0), "bad argument #1 (integer expected, got string)");
    expect.accept(() -> args.checkString(1), "bad argument #2 (string expected, got integer)");
    expect.accept(() -> args.checkBoolean(2), "bad argument #3 (boolean expected, got number)");
    expect.accept(() -> args.checkTable(3), "bad argument #4 (table expected, got boolean)");
    expect.accept(() -> args.checkString(4), "bad argument #5 (string expected, got nil)");
    expect.accept(() -> args.checkInteger(5), "bad argument #6 (number has no integer representation)");
    expect.accept(() -> args.checkByteArray(6), "bad argument #7 (string expected, got table)");
    expect.accept(() -> args.checkAny(9), "bad arguments #10 (value expected, got no value)");
    expect.accept(() -> args.optInteger(0, 7), "bad argument #1 (integer expected, got string)");
    expect.accept(() -> args.optBoolean(1, false), "bad argument #2 (boolean expected, got integer)");
    expect.accept(() -> args.optString(2, "d"), "bad argument #3 (string expected, got number)");
    expect.accept(() -> args.optTable(3, null), "bad argument #4 (table expected, got boolean)");
    expect.accept(() -> args.optItemStack(1, ItemStack.EMPTY), "bad argument #2 (table expected, got integer)");
    if (args.optInteger(4, 7) != 7 || args.optInteger(20, 8) != 8 || !"d".equals(args.optString(4, "d")) || args.optAny(4, "d") != "d") {
      problems.add("nil or no argument did not give the default");
    }
    if (args.checkLong(1) != 5L || args.optDouble(2, 0) != 2.5) problems.add("valid arguments were not taken");
    helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
    helper.succeed();
  }

  /** The errors reach Lua as they read in OC 1.12. */
  @GameTest(template = EMPTY, timeoutTicks = 300)
  public static void argumentErrorsReachLua(final GameTestHelper helper) {
    helper.setBlock(AT.north(), net.minecraft.world.level.block.Blocks.CHEST);
    final var chest = (net.minecraft.world.Container) helper.getBlockEntity(AT.north());
    chest.setItem(0, new ItemStack(Items.DIAMOND));
    chest.setItem(1, new ItemStack(Items.DIRT)); // (two empty slots are the same stack: the flag is never read)
    runReporting(helper, List.of(Constants.ItemName.CPUTier1, Constants.ItemName.RAMTier2, Constants.BlockName.Transposer),
      "local tp = component.proxy(component.list('transposer')())\n"
        + "local _, a = pcall(tp.getStackInSlot, 2, 'x')\n"
        + "local _, b = pcall(tp.compareStacks, 2, 1, 2, 'yes')\n"
        + "error('done:' .. tostring(a) .. '|' .. tostring(b), 0)\n",
      result -> helper.assertTrue(result.contains("bad argument #2 (integer expected, got string)|") && result.contains("bad argument #4 (boolean expected, got string)"),
        "a string for a slot, a string for the optional checkNBT: " + result));
  }

  // ----------------------------------------------------------------------- //
  // blocks

  /**
   * compare matches the block whatever its state, as OC 1.12 matched the item's block and metadata (a rotated log, a
   * connected fence and a wall torch were "different": only the default state matched), and only that block.
   */
  @GameTest(template = EMPTY, timeoutTicks = 400)
  public static void compareMatchesTheBlockInAnyState(final GameTestHelper helper) {
    helper.setBlock(AT.south(), net.minecraft.world.level.block.Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
    helper.setBlock(AT.above().east(), net.minecraft.world.level.block.Blocks.OAK_FENCE);
    helper.setBlock(AT.above(), net.minecraft.world.level.block.Blocks.OAK_FENCE.defaultBlockState().setValue(FenceBlock.EAST, true));
    helper.setBlock(AT.below().south(), net.minecraft.world.level.block.Blocks.STONE);
    helper.setBlock(AT.below(), net.minecraft.world.level.block.Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.NORTH));
    TestMachines.runRobot(helper, AT, List.of(Constants.ItemName.InventoryUpgrade), robot -> {
      robot.mainInventory().setItem(0, new ItemStack(Items.OAK_LOG));
      robot.mainInventory().setItem(1, new ItemStack(Items.OAK_FENCE));
      robot.mainInventory().setItem(2, new ItemStack(Items.TORCH));
    }, "local r = component.proxy(component.list('robot')())\n"
      + "local out = {}\n"
      + "local function c(slot, side, fuzzy) r.select(slot) out[#out + 1] = tostring(r.compare(side, fuzzy)) end\n"
      + "c(1, 3) c(1, 3, true) c(2, 1) c(3, 0) c(3, 1) c(1, 0)\n"
      + "error('done:' .. table.concat(out, ','), 0)\n", 20, (robot, result) -> {
      helper.assertTrue("true,true,true,true,false,false".equals(result),
        "a log on its side (exact, fuzzy), a connected fence, a wall torch; a torch against the fence, a log against the torch: " + result);
      helper.succeed();
    });
  }

  /** jukebox.play() and stop() start and stop the jukebox itself (the record never counted as playing, stop() did nothing). */
  @GameTest(template = EMPTY)
  public static void jukeboxPlaysAndStops(final GameTestHelper helper) {
    helper.setBlock(AT, net.minecraft.world.level.block.Blocks.JUKEBOX);
    final var jukebox = (JukeboxBlockEntity) helper.getBlockEntity(AT);
    jukebox.setRecordWithoutPlaying(new ItemStack(Items.MUSIC_DISC_CAT));
    final var env = new li.cil.oc.neoforge.integration.vanilla.DriverRecordPlayer.Environment(jukebox);
    env.play(context(), NO_ARGS);
    helper.assertTrue(jukebox.isRecordPlaying(), "the record does not play after play()");
    jukebox.startPlaying();
    env.stop(context(), NO_ARGS);
    helper.assertFalse(jukebox.isRecordPlaying(), "the record still plays after stop()");
    helper.succeed();
  }

  /**
   * noteblock.trigger() plays only where vanilla would (it played under a block) and makes the game event sculk sensors
   * hear (it made none); a head above is the instrument, so that one plays.
   */
  @GameTest(template = EMPTY)
  public static void noteBlockPlaysOnlyWhereVanillaWould(final GameTestHelper helper) {
    final BlockPos covered = new BlockPos(0, 1, 0), open = new BlockPos(2, 1, 0), head = new BlockPos(4, 1, 0);
    for (final BlockPos pos : List.of(covered, open, head)) helper.setBlock(pos, net.minecraft.world.level.block.Blocks.NOTE_BLOCK);
    helper.setBlock(covered.above(), net.minecraft.world.level.block.Blocks.STONE);
    helper.setBlock(head.above(), net.minecraft.world.level.block.Blocks.ZOMBIE_HEAD);
    helper.getLevel().setBlock(helper.absolutePos(head), net.minecraft.world.level.block.Blocks.NOTE_BLOCK.defaultBlockState().setValue(NoteBlock.INSTRUMENT, NoteBlockInstrument.ZOMBIE), 2 | 16);
    final List<BlockPos> played = new ArrayList<>(), heard = new ArrayList<>();
    final Consumer<NoteBlockEvent.Play> onPlay = event -> played.add(event.getPos().immutable());
    final Consumer<VanillaGameEvent> onGameEvent = event -> {
      if (event.getVanillaEvent() == GameEvent.NOTE_BLOCK_PLAY) heard.add(BlockPos.containing(event.getEventPosition()));
    };
    MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, NoteBlockEvent.Play.class, onPlay);
    MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, VanillaGameEvent.class, onGameEvent);
    final List<Object> results = new ArrayList<>();
    try {
      for (final BlockPos pos : List.of(covered, open, head)) {
        results.add(new li.cil.oc.core.impl.integration.vanilla.DriverNoteBlock.Environment(helper.getLevel(), helper.absolutePos(pos)).trigger(context(), NO_ARGS)[0]);
      }
    } catch (RuntimeException e) {
      MinecraftForge.EVENT_BUS.unregister(onPlay);
      MinecraftForge.EVENT_BUS.unregister(onGameEvent);
      throw e;
    }
    helper.runAfterDelay(3, () -> { // (block events run at the end of the tick)
      MinecraftForge.EVENT_BUS.unregister(onPlay);
      MinecraftForge.EVENT_BUS.unregister(onGameEvent);
      final List<String> state = new ArrayList<>();
      for (final BlockPos pos : List.of(covered, open, head)) {
        final BlockPos abs = helper.absolutePos(pos);
        state.add(String.format(Locale.ROOT, "%s played=%s heard=%s", results.get(state.size()), played.contains(abs), heard.contains(abs)));
      }
      helper.assertTrue(List.of("false played=false heard=false", "true played=true heard=true", "true played=true heard=true").equals(state),
        "under stone, under air, under a zombie head: " + state);
      helper.succeed();
    });
  }

  // ----------------------------------------------------------------------- //
  // adapters

  /** The adapter's name for a block from the untranslated name of its inventory, as OC 1.12 (it was the translation). */
  @GameTest(template = EMPTY)
  public static void adapterNamesInventoriesByTheirKey(final GameTestHelper helper) {
    final var level = helper.getLevel();
    final li.cil.oc.api.driver.DriverBlock inventoryDriver = new li.cil.oc.api.driver.DriverBlock() {
      @Override
      public boolean worksWith(final net.minecraft.world.level.Level world, final BlockPos pos, final Direction side) {
        return world.getBlockEntity(pos) instanceof net.minecraft.world.Container;
      }

      @Override
      public li.cil.oc.api.network.ManagedEnvironment createEnvironment(final net.minecraft.world.level.Level world, final BlockPos pos, final Direction side) {
        return new li.cil.oc.neoforge.integration.vanilla.DriverInventory.Environment((net.minecraft.world.Container) world.getBlockEntity(pos), world);
      }
    };
    final var compound = new li.cil.oc.core.impl.server.driver.CompoundBlockDriver(new li.cil.oc.api.driver.DriverBlock[]{inventoryDriver});
    final java.util.function.Function<BlockPos, String> name = pos -> {
      final var env = compound.createEnvironment(level, helper.absolutePos(pos), Direction.UP);
      return ((li.cil.oc.api.network.Component) env.node()).name();
    };
    final BlockPos hopper = new BlockPos(0, 1, 0), chest = new BlockPos(2, 1, 0), named = new BlockPos(4, 1, 0);
    helper.setBlock(hopper, net.minecraft.world.level.block.Blocks.HOPPER);
    helper.setBlock(chest, net.minecraft.world.level.block.Blocks.CHEST);
    helper.setBlock(named, net.minecraft.world.level.block.Blocks.BARREL);
    ((net.minecraft.world.level.block.entity.BaseContainerBlockEntity) helper.getBlockEntity(named)).setCustomName(Component.literal("Ore Store"));
    // a game in another language (Cyrillic was "______" in the name)
    final Language english = Language.getInstance();
    final String chestName;
    Language.inject(new Language() {
      @Override
      public String getOrDefault(final String key, final String fallback) {
        return "container.chest".equals(key) ? "Скриня" : english.getOrDefault(key, fallback);
      }

      @Override
      public boolean has(final String key) {
        return english.has(key);
      }

      @Override
      public boolean isDefaultRightToLeft() {
        return english.isDefaultRightToLeft();
      }

      @Override
      public FormattedCharSequence getVisualOrder(final FormattedText text) {
        return english.getVisualOrder(text);
      }
    });
    try {
      chestName = name.apply(chest);
    } finally {
      Language.inject(english);
    }
    final String names = name.apply(hopper) + "," + chestName + "," + name.apply(named);
    helper.assertTrue("hopper,chest,ore_store".equals(names), "a hopper (\"Item Hopper\"), a chest in Ukrainian, a renamed barrel: " + names);
    helper.succeed();
  }
}
