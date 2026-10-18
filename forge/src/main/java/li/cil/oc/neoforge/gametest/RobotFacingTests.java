package li.cil.oc.neoforge.gametest;

import li.cil.oc.core.Constants;
import li.cil.oc.core.impl.common.blockentity.Robot;
import li.cil.oc.core.impl.common.blockentity.RobotProxy;
import li.cil.oc.neoforge.OpenComputers;
import li.cil.oc.neoforge.common.init.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * A robot stands upright: its front is where the player who placed it looked, horizontally, however far up or down
 * the player looked; its top is up.
 */
@GameTestHolder(OpenComputers.ID)
@PrefixGameTestTemplate(false)
public final class RobotFacingTests {
  private static final String EMPTY = "empty";
  private static final float[] PITCHES = {0, 30, 50, 89, -50, -89};

  private RobotFacingTests() {
  }

  private static FakePlayer player(final GameTestHelper helper, final float pitch) {
    final FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new com.mojang.authlib.GameProfile(java.util.UUID.fromString("5d1e2a6e-3f2b-4c39-9a7e-1d2f3b4c5d71"), "oc-test-placer"));
    player.setGameMode(GameType.SURVIVAL);
    player.setYRot(0);      // looking south
    player.setXRot(pitch);  // positive: down
    return player;
  }

  private static ItemStackHolder robotItem() {
    final var data = new li.cil.oc.core.impl.common.item.data.RobotData();
    data.name = "facing";
    data.components.add(li.cil.oc.api.Items.get(Constants.ItemName.InventoryUpgrade).createItemStack(1));
    return new ItemStackHolder(data.createItemStack());
  }

  private record ItemStackHolder(net.minecraft.world.item.ItemStack stack) {
  }

  private static String check(final Robot robot, final float pitch) {
    final Direction front = robot.toGlobal(Direction.SOUTH), top = robot.toGlobal(Direction.UP), bottom = robot.toGlobal(Direction.DOWN);
    return front == Direction.SOUTH && top == Direction.UP && bottom == Direction.DOWN ? null
      : "placed looking south " + pitch + " degrees down: front " + front + ", top " + top + ", bottom " + bottom;
  }

  @GameTest(template = EMPTY)
  public static void robotPlacedLookingUpOrDownStandsUpright(final GameTestHelper helper) {
    final StringBuilder problems = new StringBuilder();
    for (int i = 0; i < PITCHES.length; i++) {
      final BlockPos pos = new BlockPos(1 + i % 3, 2, 1 + i / 3);
      helper.setBlock(pos, Blocks.ROBOT.get());
      final BlockPos abs = helper.absolutePos(pos);
      Blocks.ROBOT.get().setPlacedBy(helper.getLevel(), abs, helper.getLevel().getBlockState(abs), player(helper, PITCHES[i]), robotItem().stack());
      final String problem = check(((RobotProxy) helper.getLevel().getBlockEntity(abs)).robot, PITCHES[i]);
      if (problem != null) problems.append(problem).append("; ");
    }
    helper.assertTrue(problems.isEmpty(), problems.toString());
    helper.succeed();
  }

  /** The same through the robot item, as a player places it (the block state comes from where the player looks). */
  @GameTest(template = EMPTY)
  public static void robotItemPlacedLookingUpOrDownStandsUpright(final GameTestHelper helper) {
    final StringBuilder problems = new StringBuilder();
    for (int i = 0; i < PITCHES.length; i++) {
      final BlockPos floor = helper.absolutePos(new BlockPos(1 + i % 3, 1, 1 + i / 3));
      helper.getLevel().setBlockAndUpdate(floor, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
      final FakePlayer player = player(helper, PITCHES[i]);
      player.setItemInHand(InteractionHand.MAIN_HAND, robotItem().stack());
      final var hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);
      player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
      final var be = helper.getLevel().getBlockEntity(floor.above());
      if (!(be instanceof RobotProxy proxy)) {
        problems.append("no robot placed at pitch ").append(PITCHES[i]).append("; ");
        continue;
      }
      final String problem = check(proxy.robot, PITCHES[i]);
      if (problem != null) problems.append(problem).append("; ");
    }
    helper.assertTrue(problems.isEmpty(), problems.toString());
    helper.succeed();
  }

  private static Robot bootedRobot(final GameTestHelper helper, final BlockPos pos, final String program) {
    final var data = new li.cil.oc.core.impl.common.item.data.RobotData();
    data.name = "turner";
    for (final String part : java.util.List.of(Constants.ItemName.CPUTier1, Constants.ItemName.RAMTier1, Constants.ItemName.RAMTier1)) {
      data.components.add(li.cil.oc.api.Items.get(part).createItemStack(1));
    }
    data.components.add(li.cil.oc.api.Items.registerEEPROM("turn", program.getBytes(java.nio.charset.StandardCharsets.UTF_8), null, false));
    helper.setBlock(pos, Blocks.ROBOT.get());
    final BlockPos abs = helper.absolutePos(pos);
    Blocks.ROBOT.get().setPlacedBy(helper.getLevel(), abs, helper.getLevel().getBlockState(abs), player(helper, 0), data.createItemStack());
    return ((RobotProxy) helper.getLevel().getBlockEntity(abs)).robot;
  }

  /** robot.turnLeft() turns anticlockwise (seen from above), robot.turnRight() clockwise: from south, east and west. */
  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void robotTurnsLeftAndRight(final GameTestHelper helper) {
    final String program = "local r = component.proxy(component.list('robot')()) r.turn(%s) while true do computer.pullSignal(1) end";
    final Robot left = bootedRobot(helper, new BlockPos(1, 2, 1), String.format(program, "false"));
    final Robot right = bootedRobot(helper, new BlockPos(3, 2, 1), String.format(program, "true"));
    helper.runAfterDelay(5, () -> {
      for (final Robot robot : new Robot[]{left, right}) {
        ((li.cil.oc.api.network.Connector) robot.machine().node()).changeBuffer(10000);
        helper.assertTrue(robot.machine().start(), "the robot did not start: " + robot.machine().lastError());
      }
      helper.runAfterDelay(60, () -> {
        helper.assertTrue(left.machine().lastError() == null && right.machine().lastError() == null,
          "program errors: " + left.machine().lastError() + " / " + right.machine().lastError());
        helper.assertTrue(left.yaw() == Direction.EAST && left.toGlobal(Direction.SOUTH) == Direction.EAST,
          "facing south, turnLeft faces " + left.yaw() + " (front " + left.toGlobal(Direction.SOUTH) + ")");
        helper.assertTrue(right.yaw() == Direction.WEST && right.toGlobal(Direction.SOUTH) == Direction.WEST,
          "facing south, turnRight faces " + right.yaw() + " (front " + right.toGlobal(Direction.SOUTH) + ")");
        helper.assertTrue(left.toGlobal(Direction.UP) == Direction.UP && right.toGlobal(Direction.UP) == Direction.UP, "turning tipped a robot over");
        helper.succeed();
      });
    });
  }

  /** A wrench turns a robot about the vertical axis, whichever face it hits. */
  @GameTest(template = EMPTY)
  public static void robotRotatedStaysUpright(final GameTestHelper helper) {
    final BlockPos abs = helper.absolutePos(new BlockPos(2, 2, 2));
    helper.setBlock(new BlockPos(2, 2, 2), Blocks.ROBOT.get());
    Blocks.ROBOT.get().setPlacedBy(helper.getLevel(), abs, helper.getLevel().getBlockState(abs), player(helper, 0), robotItem().stack());
    final Robot robot = ((RobotProxy) helper.getLevel().getBlockEntity(abs)).robot;
    for (final Direction axis : Direction.values()) {
      robot.rotate(axis);
      helper.assertTrue(robot.toGlobal(Direction.UP) == Direction.UP && robot.yaw().getAxis().isHorizontal(),
        "rotated about " + axis + ": top " + robot.toGlobal(Direction.UP) + ", facing " + robot.yaw());
    }
    helper.succeed();
  }
}
