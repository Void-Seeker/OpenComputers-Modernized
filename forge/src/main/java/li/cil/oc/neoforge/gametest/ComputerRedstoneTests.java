package li.cil.oc.neoforge.gametest;

import java.util.List;
import java.util.Map;
import li.cil.oc.core.Constants;
import li.cil.oc.core.impl.common.blockentity.Case;
import li.cil.oc.core.impl.common.item.data.MicrocontrollerData;
import li.cil.oc.core.impl.server.machine.ArgumentsImpl;
import li.cil.oc.neoforge.OpenComputers;
import li.cil.oc.neoforge.common.init.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Computers' redstone: outputs follow the machine, inputs are read from the start and reported by the device's sides,
 * and changes reach what they power.
 */
@GameTestHolder(OpenComputers.ID)
@PrefixGameTestTemplate(false)
public final class ComputerRedstoneTests {
  private static final String EMPTY = "empty";
  private static final BlockPos AT = new BlockPos(2, 2, 2);

  private ComputerRedstoneTests() {
  }

  private static li.cil.oc.api.machine.Context context() {
    return (li.cil.oc.api.machine.Context) java.lang.reflect.Proxy.newProxyInstance(li.cil.oc.api.machine.Context.class.getClassLoader(),
      new Class<?>[]{li.cil.oc.api.machine.Context.class}, (proxy, method, args) -> method.getReturnType() == boolean.class ? false : null);
  }

  private static Case caseFacing(final GameTestHelper helper, final Direction facing) {
    helper.setBlock(AT, Blocks.CASE_TIER_1.get());
    final var computer = (Case) helper.getBlockEntity(AT);
    computer.facing(facing);
    return computer;
  }

  /** A microcontroller's redstone outputs go off when it stops (they stayed on: a door stayed open after a crash). */
  @GameTest(template = EMPTY, timeoutTicks = 300)
  public static void microcontrollerOutputsGoOffWhenItStops(final GameTestHelper helper) {
    TestMachines.runMicrocontroller(helper, AT, List.of(Constants.ItemName.CPUTier1, Constants.ItemName.RAMTier2, Constants.ItemName.RedstoneCardTier1),
      "component.proxy(component.list('redstone')()).setOutput(3, 15)\nwhile true do computer.pullSignal(1) end\n",
      mc -> {
        helper.assertTrue(mc.isOutputEnabled() && mc.output()[3] == 15, "the running microcontroller does not output 15 at its front: " + java.util.Arrays.toString(mc.output()));
        mc.machine().stop();
        helper.runAfterDelay(20, () -> {
          helper.assertTrue(mc.output()[3] == 0, "the stopped microcontroller still outputs " + java.util.Arrays.toString(mc.output()));
          helper.succeed();
        });
      });
  }

  /** A Redstone I/O block placed next to a powered block reads it (inputs were read only after a neighbour changed). */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void redstoneIoReadsWhatIsPoweredWhenPlaced(final GameTestHelper helper) {
    helper.setBlock(AT.east(), net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK);
    helper.setBlock(AT, Blocks.REDSTONE.get());
    final var io = (li.cil.oc.core.impl.common.blockentity.Redstone) helper.getBlockEntity(AT);
    helper.runAfterDelay(5, () -> {
      helper.assertTrue(io.getInput(Direction.EAST) == 15, "a Redstone I/O block next to a redstone block reads " + io.getInput(Direction.EAST));
      helper.succeed();
    });
  }

  /**
   * A stopped microcontroller placed next to a powered block stays off when another neighbour changes (its first
   * reading went from "unknown" to 15, which woke it).
   */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void placedMicrocontrollerIsNotWokenByWhatWasPowered(final GameTestHelper helper) {
    final var data = new MicrocontrollerData();
    for (final String part : List.of(Constants.ItemName.CPUTier1, Constants.ItemName.RAMTier2, Constants.ItemName.RedstoneCardTier1)) {
      data.components.add(li.cil.oc.api.Items.get(part).createItemStack(1));
    }
    data.components.add(li.cil.oc.api.Items.registerEEPROM("idle", "while true do computer.pullSignal(1) end\n".getBytes(java.nio.charset.StandardCharsets.UTF_8), null, false));
    helper.setBlock(AT.west(), net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK);
    helper.setBlock(AT, Blocks.MICROCONTROLLER.get());
    final BlockPos abs = helper.absolutePos(AT);
    Blocks.MICROCONTROLLER.get().setPlacedBy(helper.getLevel(), abs, helper.getLevel().getBlockState(abs), FakePlayerFactory.getMinecraft(helper.getLevel()), data.createItemStack());
    final var mc = (li.cil.oc.core.impl.common.blockentity.Microcontroller) helper.getBlockEntity(AT);
    ((li.cil.oc.api.network.Connector) mc.snooperNode).changeBuffer(1000);
    helper.runAfterDelay(5, () -> {
      helper.setBlock(AT.above(), net.minecraft.world.level.block.Blocks.STONE);
      helper.runAfterDelay(20, () -> {
        helper.assertTrue(!mc.machine().isRunning(), "placing a block next to a stopped microcontroller by a redstone block started it");
        helper.assertTrue(mc.getInput(Direction.WEST) == 15, "the microcontroller reads " + mc.getInput(Direction.WEST) + " from the redstone block");
        helper.succeed();
      });
    });
  }

  /** A case's output reaches through the block it powers (a lamp behind it did not light). */
  @GameTest(template = EMPTY)
  public static void caseOutputReachesThroughThePoweredBlock(final GameTestHelper helper) {
    final Case computer = caseFacing(helper, Direction.NORTH);
    helper.setBlock(AT.east(), net.minecraft.world.level.block.Blocks.STONE);
    helper.setBlock(AT.east().east(), net.minecraft.world.level.block.Blocks.REDSTONE_LAMP);
    computer.setOutputEnabled(true);
    computer.setOutput(Direction.EAST, 15);
    helper.assertTrue(helper.getBlockState(AT.east().east()).getValue(RedstoneLampBlock.LIT), "a lamp behind the block the case powers stayed off");
    helper.succeed();
  }

  /** Bundled output tables are by the case's sides, and switching outputs off clears them (front/back were swapped; cables kept their colours). */
  @GameTest(template = EMPTY)
  public static void caseBundledOutputsByItsSides(final GameTestHelper helper) {
    final Case computer = caseFacing(helper, Direction.NORTH);
    computer.setOutputEnabled(true);
    computer.setBundledOutput(Map.of(3, Map.of(0, 255)));
    helper.assertTrue(computer.getBundledOutput(Direction.NORTH, 0) == 255, "the case's front (north) carries " + computer.getBundledOutput(Direction.NORTH, 0) + " on white");
    computer.setOutputEnabled(false);
    helper.assertTrue(computer.getBundledOutput(Direction.NORTH, 0) == 0, "with outputs off the case's front still carries " + computer.getBundledOutput(Direction.NORTH, 0));
    helper.succeed();
  }

  /** A change of bundled input on a Redstone I/O block is signalled (only plain inputs were). */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void redstoneIoSignalsBundledInputChanges(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.REDSTONE.get());
    final var io = (li.cil.oc.core.impl.common.blockentity.Redstone) helper.getBlockEntity(AT);
    final java.util.List<Object> signals = new java.util.ArrayList<>();
    final var listener = li.cil.oc.api.Network.newNode(new li.cil.oc.api.network.Environment() {
      private li.cil.oc.api.network.Node node;

      @Override
      public li.cil.oc.api.network.Node node() {
        return node;
      }

      @Override
      public void onConnect(li.cil.oc.api.network.Node n) {
      }

      @Override
      public void onDisconnect(li.cil.oc.api.network.Node n) {
      }

      @Override
      public void onMessage(li.cil.oc.api.network.Message message) {
        if ("computer.signal".equals(message.name()) && message.data().length > 0) signals.add(message.data()[0]);
      }
    }, li.cil.oc.api.network.Visibility.Network).create();
    helper.runAfterDelay(5, () -> {
      io.node.connect(listener);
      try {
        io.setBundledInput(Direction.EAST, 1, 200); // the block read its inputs when placed: this is a change
        helper.assertTrue(signals.contains("redstone_changed"), "a bundled input change made the signals " + signals);
      } finally {
        listener.remove();
      }
      helper.succeed();
    });
  }

  /** A running case reads 15 on a comparator (it read 0). */
  @GameTest(template = EMPTY)
  public static void comparatorReadsARunningCase(final GameTestHelper helper) {
    final Case computer = caseFacing(helper, Direction.NORTH);
    computer.setRunning(true);
    final int signal = helper.getBlockState(AT).getAnalogOutputSignal(helper.getLevel(), helper.absolutePos(AT));
    helper.assertTrue(signal == 15, "a comparator reads " + signal + " from a running case");
    helper.succeed();
  }

  /** getInput() is by the device's sides (it was by world side for a case not facing south). */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void inputTablesAreByTheDevicesSides(final GameTestHelper helper) {
    final Case computer = caseFacing(helper, Direction.NORTH);
    helper.setBlock(AT.north(), net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK);
    helper.runAfterDelay(3, () -> {
      computer.checkRedstoneInputChanged();
      final var card = new li.cil.oc.core.impl.server.component.Redstone.Vanilla(computer);
      final var table = (Map<?, ?>) card.getInput(context(), new ArgumentsImpl(new Object[0]))[0];
      helper.assertTrue(Integer.valueOf(15).equals(table.get(3)), "the redstone block at the case's front shows in the table as " + table);
      helper.succeed();
    });
  }

  /** What ChunkMap.scheduleUnload does to a microcontroller, then the chunk coming back. */
  private static void reload(final GameTestHelper helper, final li.cil.oc.core.impl.common.blockentity.Microcontroller mc,
                             final java.util.function.Consumer<net.minecraft.nbt.CompoundTag> edit,
                             final java.util.function.Consumer<li.cil.oc.core.impl.common.blockentity.Microcontroller> then) {
    final BlockPos abs = mc.getBlockPos();
    mc.markUnloading();
    final var saved = mc.saveWithoutMetadata();
    edit.accept(saved);
    helper.getLevel().removeBlockEntity(abs);
    helper.runAfterDelay(3, () -> {
      final var reloaded = (li.cil.oc.core.impl.common.blockentity.Microcontroller) helper.getLevel().getBlockEntity(abs);
      reloaded.load(saved);
      then.accept(reloaded);
    });
  }

  /** A running microcontroller reloaded with its chunk still drives its outputs (they came back switched off). */
  @GameTest(template = EMPTY, timeoutTicks = 400)
  public static void reloadedMicrocontrollerKeepsItsOutputs(final GameTestHelper helper) {
    TestMachines.runMicrocontroller(helper, AT, List.of(Constants.ItemName.CPUTier1, Constants.ItemName.RAMTier2, Constants.ItemName.RedstoneCardTier1),
      "component.proxy(component.list('redstone')()).setOutput(3, 15)\nwhile true do computer.pullSignal(1) end\n",
      mc -> reload(helper, mc, tag -> {
      }, reloaded -> helper.runAfterDelay(40, () -> {
        helper.assertTrue(reloaded.machine().isRunning(), "the reloaded microcontroller stopped: " + reloaded.machine().lastError());
        helper.assertTrue(reloaded.isOutputEnabled() && reloaded.output()[3] == 15, "the reloaded microcontroller outputs " + java.util.Arrays.toString(reloaded.output()) + ", enabled " + reloaded.isOutputEnabled());
        helper.succeed();
      })));
  }

  /** A stopped microcontroller saved with outputs on (before outputs followed the machine) has them cleared once loaded. */
  @GameTest(template = EMPTY, timeoutTicks = 400)
  public static void stuckMicrocontrollerOutputsAreClearedOnLoad(final GameTestHelper helper) {
    TestMachines.runMicrocontroller(helper, AT, List.of(Constants.ItemName.CPUTier1, Constants.ItemName.RAMTier2, Constants.ItemName.RedstoneCardTier1),
      "while true do computer.pullSignal(1) end\n",
      mc -> {
        mc.machine().stop();
        helper.runAfterDelay(10, () -> {
          mc.output()[3] = 15; // as an older version left it
          reload(helper, mc, tag -> {
          }, reloaded -> helper.runAfterDelay(20, () -> {
            helper.assertTrue(!reloaded.machine().isRunning() && reloaded.output()[3] == 0, "the stopped microcontroller loaded with " + java.util.Arrays.toString(reloaded.output()));
            helper.succeed();
          }));
        });
      });
  }

  /** A robot's outputs go off when it stops, and a placed robot reads what is powered (outputs stayed; inputs read 0). */
  @GameTest(template = EMPTY, timeoutTicks = 300)
  public static void robotRedstoneFollowsItsMachine(final GameTestHelper helper) {
    helper.setBlock(AT.east(), net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK);
    TestMachines.runRobot(helper, AT, List.of(Constants.ItemName.RedstoneCardTier1),
      "local rs = component.proxy(component.list('redstone')())\nrs.setOutput(3, 15)\nlocal seen = rs.getInput(5)\nlocal on = rs.getOutput(3)\nerror('done:' .. seen .. ' ' .. on)\n",
      40, (robot, result) -> {
        // the robot faces south: its left (5) is east
        helper.assertTrue(result != null && result.matches("15(\\.0)? 15(\\.0)?"), "the robot read and set " + result);
        helper.runAfterDelay(5, () -> {
          int total = 0;
          for (final int v : robot.output()) total += v;
          helper.assertTrue(!robot.isOutputEnabled() && total == 0, "the stopped robot outputs " + java.util.Arrays.toString(robot.output()));
          helper.succeed();
        });
      });
  }

  /** A rack's outputs are cleared when they are switched off (they stayed on). */
  @GameTest(template = EMPTY)
  public static void rackOutputsAreClearedWhenSwitchedOff(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.RACK.get());
    final var rack = (li.cil.oc.core.impl.common.blockentity.Rack) helper.getBlockEntity(AT);
    rack.setOutputEnabled(true);
    rack.setOutput(Direction.UP, 15);
    rack.setOutputEnabled(false);
    helper.assertTrue(rack.getOutput(Direction.UP) == 0, "a rack switched off still outputs " + rack.getOutput(Direction.UP));
    helper.succeed();
  }

  private static int total(final int[] values) {
    int n = 0;
    for (final int v : values) n += v;
    return n;
  }

  /**
   * A running robot reloaded with its chunk still drives its outputs (its first tick comes before its machine is loaded,
   * in initialize: it was not running yet, and the outputs were switched off for good).
   */
  @GameTest(template = EMPTY, timeoutTicks = 400)
  public static void reloadedRobotKeepsItsOutputs(final GameTestHelper helper) {
    TestMachines.runRobot(helper, AT, List.of(Constants.ItemName.RedstoneCardTier1),
      "component.proxy(component.list('redstone')()).setOutput(3, 15)\nwhile true do computer.pullSignal(1) end\n",
      40, (robot, result) -> {
        helper.assertTrue(robot.isOutputEnabled() && total(robot.output()) == 15, "the running robot outputs " + java.util.Arrays.toString(robot.output()));
        final BlockPos abs = helper.absolutePos(AT);
        final var proxy = (li.cil.oc.core.impl.common.blockentity.RobotProxy) helper.getLevel().getBlockEntity(abs);
        proxy.markUnloading();
        final var saved = proxy.saveWithFullMetadata();
        helper.getLevel().removeBlockEntity(abs);
        final var loaded = (li.cil.oc.core.impl.common.blockentity.RobotProxy) net.minecraft.world.level.block.entity.BlockEntity.loadStatic(abs, helper.getLevel().getBlockState(abs), saved);
        helper.getLevel().getChunkAt(abs).addAndRegisterBlockEntity(loaded);
        loaded.updateEntity(); // a loaded chunk's block entities tick in the same tick, before what initialize schedules
        helper.runAfterDelay(40, () -> {
          final var again = loaded.robot;
          helper.assertTrue(again.machine().isRunning(), "the reloaded robot stopped: " + again.machine().lastError());
          helper.assertTrue(again.isOutputEnabled() && total(again.output()) == 15, "the reloaded robot outputs " + java.util.Arrays.toString(again.output()) + ", enabled " + again.isOutputEnabled());
          helper.succeed();
        });
      });
  }

  /** A stopped robot lets go of what it powers through a block, and of its bundled outputs (a lamp behind stayed lit; cables kept their colours). */
  @GameTest(template = EMPTY, timeoutTicks = 300)
  public static void stoppedRobotLetsGoOfWhatItPowers(final GameTestHelper helper) {
    // the robot faces south: its front (3) is south
    final BlockPos lamp = AT.south().south();
    helper.setBlock(AT.south(), net.minecraft.world.level.block.Blocks.STONE);
    helper.setBlock(lamp, net.minecraft.world.level.block.Blocks.REDSTONE_LAMP);
    TestMachines.runRobot(helper, AT, List.of(Constants.ItemName.RedstoneCardTier1),
      "component.proxy(component.list('redstone')()).setOutput(3, 15)\nwhile true do computer.pullSignal(1) end\n",
      40, (robot, result) -> {
        helper.assertTrue(helper.getBlockState(lamp).getValue(RedstoneLampBlock.LIT), "the lamp behind the block the robot powers is off: " + java.util.Arrays.toString(robot.output()));
        robot.bundledOutput()[Direction.SOUTH.ordinal()][0] = 15; // as a bundled card sets it
        robot.machine().stop();
        helper.runAfterDelay(20, () -> {
          helper.assertTrue(!robot.isOutputEnabled(), "the stopped robot's outputs are still enabled");
          helper.assertTrue(!helper.getBlockState(lamp).getValue(RedstoneLampBlock.LIT), "the stopped robot's lamp behind the block is still lit");
          helper.assertTrue(robot.bundledOutput()[Direction.SOUTH.ordinal()][0] == 0, "the stopped robot keeps its bundled output");
          helper.succeed();
        });
      });
  }

  /** A rack's first reading of a side is no change: a stopped server with a redstone card stays off (it started by itself). */
  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void rackServerIsNotWokenByTheFirstReading(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.RACK.get());
    helper.runAfterDelay(5, () -> {
      final var rack = (li.cil.oc.core.impl.common.blockentity.Rack) helper.getBlockEntity(AT);
      rack.setItem(0, li.cil.oc.api.Items.get(Constants.ItemName.ServerTier1).createItemStack(1));
      final var server = (li.cil.oc.core.impl.server.component.ServerBase) rack.getMountable(0);
      helper.assertTrue(server != null, "the rack made no server mountable");
      for (final ItemStack part : List.of(li.cil.oc.api.Items.get(Constants.ItemName.CPUTier1).createItemStack(1),
        li.cil.oc.api.Items.get(Constants.ItemName.RAMTier2).createItemStack(1), li.cil.oc.api.Items.get(Constants.ItemName.RedstoneCardTier1).createItemStack(1),
        li.cil.oc.api.Items.registerEEPROM("rack test", "while true do computer.pullSignal(1) end\n".getBytes(java.nio.charset.StandardCharsets.UTF_8), null, false))) {
        int slot = -1;
        for (int i = 0; i < server.getContainerSize() && slot < 0; i++) {
          if (server.getItem(i).isEmpty() && server.canPlaceItem(i, part)) slot = i;
        }
        helper.assertTrue(slot >= 0, "no server slot takes " + part);
        server.setItem(slot, part);
      }
      helper.runAfterDelay(5, () -> {
        ((li.cil.oc.api.network.Connector) server.machine().node()).changeBuffer(1000);
        java.util.Arrays.fill(rack.input(), -1); // as racks were saved
        helper.setBlock(AT.east(), net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK);
        rack.checkRedstoneInputChanged();
        helper.runAfterDelay(20, () -> {
          helper.assertTrue(!server.machine().isRunning(), "the stopped server started by itself");
          helper.succeed();
        });
      });
    });
  }

  /** A placed rack reads what is powered (its inputs read 0 until a neighbour changed). */
  @GameTest(template = EMPTY)
  public static void placedRackReadsWhatIsPowered(final GameTestHelper helper) {
    helper.setBlock(AT.east(), net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK);
    helper.setBlock(AT, Blocks.RACK.get());
    helper.runAfterDelay(3, () -> {
      final var rack = (li.cil.oc.core.impl.common.blockentity.Rack) helper.getBlockEntity(AT);
      helper.assertTrue(rack.getInput(Direction.EAST) == 15, "a rack placed by a redstone block reads " + rack.getInput(Direction.EAST));
      helper.succeed();
    });
  }

  /** A running case reloaded with its chunk still drives its outputs (its machine is loaded in initialize too, as a robot's). */
  @GameTest(template = EMPTY, timeoutTicks = 400)
  public static void reloadedCaseKeepsItsOutputs(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.CASE_CREATIVE.get());
    final var computer = (Case) helper.getBlockEntity(AT);
    for (final ItemStack part : List.of(li.cil.oc.api.Items.get(Constants.ItemName.CPUTier1).createItemStack(1),
      li.cil.oc.api.Items.get(Constants.ItemName.RAMTier2).createItemStack(1), li.cil.oc.api.Items.get(Constants.ItemName.RedstoneCardTier1).createItemStack(1),
      li.cil.oc.api.Items.registerEEPROM("case test", "component.proxy(component.list('redstone')()).setOutput(3, 15)\nwhile true do computer.pullSignal(1) end\n"
        .getBytes(java.nio.charset.StandardCharsets.UTF_8), null, false))) {
      int slot = -1;
      for (int i = 0; i < computer.getContainerSize() && slot < 0; i++) {
        if (computer.getItem(i).isEmpty() && computer.canPlaceItem(i, part)) slot = i;
      }
      helper.assertTrue(slot >= 0, "no case slot takes " + part);
      computer.setItem(slot, part);
    }
    helper.runAfterDelay(5, () -> {
      helper.assertTrue(computer.machine().start(), "the case did not start: " + computer.machine().lastError());
      helper.runAfterDelay(60, () -> {
        helper.assertTrue(computer.isOutputEnabled() && total(computer.output()) == 15, "the running case outputs " + java.util.Arrays.toString(computer.output()));
        final BlockPos abs = helper.absolutePos(AT);
        computer.markUnloading();
        final var saved = computer.saveWithFullMetadata();
        helper.getLevel().removeBlockEntity(abs);
        final var loaded = (Case) net.minecraft.world.level.block.entity.BlockEntity.loadStatic(abs, helper.getLevel().getBlockState(abs), saved);
        helper.getLevel().getChunkAt(abs).addAndRegisterBlockEntity(loaded);
        loaded.updateEntity(); // a loaded chunk's block entities tick in the same tick, before what initialize schedules
        helper.runAfterDelay(40, () -> {
          helper.assertTrue(loaded.machine().isRunning(), "the reloaded case stopped: " + loaded.machine().lastError());
          helper.assertTrue(loaded.isOutputEnabled() && total(loaded.output()) == 15, "the reloaded case outputs " + java.util.Arrays.toString(loaded.output()) + ", enabled " + loaded.isOutputEnabled());
          helper.succeed();
        });
      });
    });
  }
}
