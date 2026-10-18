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
        io.setBundledInput(Direction.EAST, 1, 200);
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
}
