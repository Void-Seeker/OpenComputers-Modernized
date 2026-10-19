package li.cil.oc.neoforge.gametest;

import java.util.List;
import li.cil.oc.core.Constants;
import li.cil.oc.core.impl.server.machine.ArgumentsImpl;
import li.cil.oc.neoforge.OpenComputers;
import li.cil.oc.neoforge.common.init.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * State that 1.20.1 saves only when its chunk is marked: OC 1.12 saved every chunk it unloaded, and much of OC's state
 * changes without a mark (what a remote computer set, a stopped robot's tank, energy, progress).
 */
@GameTestHolder(OpenComputers.ID)
@PrefixGameTestTemplate(false)
public final class SaveMarkTests {
  private static final String EMPTY = "empty";
  private static final BlockPos AT = new BlockPos(2, 2, 2);

  private SaveMarkTests() {
  }

  private static li.cil.oc.api.machine.Context context() {
    return (li.cil.oc.api.machine.Context) java.lang.reflect.Proxy.newProxyInstance(li.cil.oc.api.machine.Context.class.getClassLoader(),
      new Class<?>[]{li.cil.oc.api.machine.Context.class}, (proxy, method, args) -> method.getReturnType() == boolean.class ? false : null);
  }

  /** The block entity's own save mark (the chunk's is shared with other tests' blocks), cleared if asked. */
  private static boolean marked(final Object be, final boolean clear) {
    try {
      if (be instanceof li.cil.oc.core.impl.common.blockentity.RobotBase) {
        final var field = li.cil.oc.core.impl.common.blockentity.RobotBase.class.getDeclaredField("saveScheduled");
        field.setAccessible(true);
        final boolean was = field.getBoolean(be);
        if (clear) field.setBoolean(be, false);
        return was;
      }
      final var field = li.cil.oc.core.impl.common.blockentity.traits.BlockEntity.class.getDeclaredField("changeScheduled");
      field.setAccessible(true);
      final var mark = (java.util.concurrent.atomic.AtomicBoolean) field.get(be);
      return clear ? mark.getAndSet(false) : mark.get();
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  /** A chunk holding OC block entities is saved when it unloads, marked or not (as OC 1.12). In a chunk of its own. */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void chunksWithOCBlocksAreSavedOnUnload(final GameTestHelper helper) {
    final ServerLevel level = helper.getLevel();
    final BlockPos far = new BlockPos(-24000 - helper.getLevel().random.nextInt(64) * 16, 100, 24000);
    final ChunkPos chunk = new ChunkPos(far);
    level.setChunkForced(chunk.x, chunk.z, true);
    helper.runAfterDelay(5, () -> {
      try {
        level.setBlock(far, Blocks.CAPACITOR.get().defaultBlockState(), 3);
        final var levelChunk = level.getChunk(chunk.x, chunk.z);
        levelChunk.setUnsaved(false);
        MinecraftForge.EVENT_BUS.post(new ChunkEvent.Unload(levelChunk));
        helper.assertTrue(levelChunk.isUnsaved(), "a chunk with an OC block was not marked to be saved on unload");
      } finally {
        level.removeBlockEntity(far);
        level.setBlock(far, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        level.setChunkForced(chunk.x, chunk.z, false);
      }
      helper.succeed();
    });
  }

  /** A Redstone I/O block's outputs and wake threshold set by a computer are saved (a remote computer marked only itself). */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void redstoneIoSettingsAreSaved(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.REDSTONE.get());
    helper.runAfterDelay(5, () -> {
      final var io = (li.cil.oc.core.impl.common.blockentity.Redstone) helper.getBlockEntity(AT);
      marked(io, true);
      io.setOutput(Direction.EAST, 15);
      final boolean output = marked(io, true);
      io.instance.setWakeThreshold(context(), new ArgumentsImpl(new Object[]{5}));
      final boolean threshold = marked(io, true);
      helper.assertTrue(output, "an output set did not mark the Redstone I/O block to be saved");
      helper.assertTrue(threshold, "a wake threshold set did not mark the Redstone I/O block to be saved");
      helper.succeed();
    });
  }

  /** A screen turned off is saved (it came back on). */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void screenPowerIsSaved(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.SCREEN_TIER_1.get());
    helper.runAfterDelay(5, () -> {
      final var screen = (li.cil.oc.core.impl.common.blockentity.Screen) helper.getBlockEntity(AT);
      marked(screen, true);
      screen.buffer.setPowerState(!screen.buffer.getPowerState());
      helper.assertTrue(marked(screen, false), "turning the screen off did not mark it to be saved");
      helper.succeed();
    });
  }

  /** A printer's job set up by a computer is saved (it was forgotten). */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void printerJobIsSaved(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.PRINTER.get());
    helper.runAfterDelay(5, () -> {
      final var printer = (li.cil.oc.core.impl.common.blockentity.Printer) helper.getBlockEntity(AT);
      marked(printer, true);
      printer.setLabel(context(), new ArgumentsImpl(new Object[]{"a print"}));
      helper.assertTrue(marked(printer, false), "a label set did not mark the printer to be saved");
      helper.succeed();
    });
  }

  /** A stopped robot's tank filled from outside, and its new name, are saved (nothing marked a stopped robot). */
  @GameTest(template = EMPTY, timeoutTicks = 300)
  public static void stoppedRobotChangesAreSaved(final GameTestHelper helper) {
    TestMachines.runRobot(helper, AT, List.of(Constants.ItemName.TankUpgrade), "error('done:stopped')", 20, (robot, result) -> {
      helper.assertTrue(!robot.machine().isRunning(), "the robot still runs");
      marked(robot, true); // (no mark left from before)
      final int filled = ((li.cil.oc.neoforge.common.blockentity.Robot) robot).fill(
        new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000),
        net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
      helper.assertTrue(filled > 0, "the robot's tank took no water");
      final boolean tank = marked(robot, true);
      robot.proxy.setName(context(), new ArgumentsImpl(new Object[]{"renamed"}));
      final boolean name = marked(robot, true);
      helper.assertTrue(tank, "filling a stopped robot's tank did not mark it to be saved");
      helper.assertTrue(name, "renaming a stopped robot did not mark it to be saved");
      helper.succeed();
    });
  }
}
