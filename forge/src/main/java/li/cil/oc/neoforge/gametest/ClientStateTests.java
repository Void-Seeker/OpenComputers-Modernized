package li.cil.oc.neoforge.gametest;

import static li.cil.oc.neoforge.gametest.TestMachines.when;

import li.cil.oc.core.Constants;
import li.cil.oc.core.impl.client.renderer.ScreenFrameTextures;
import li.cil.oc.core.impl.common.blockentity.Screen;
import li.cil.oc.core.impl.common.component.TextBuffer;
import li.cil.oc.neoforge.OpenComputers;
import li.cil.oc.neoforge.common.init.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * What the client works out from common state, run on the server: a screen block's frame (its model), and the client's
 * copies of robots and screens (a block entity that is not the server's).
 */
@GameTestHolder(OpenComputers.ID)
@PrefixGameTestTemplate(false)
public final class ClientStateTests {
  private static final String EMPTY = "empty";
  private static final BlockPos AT = new BlockPos(2, 2, 2);

  private ClientStateTests() {
  }

  private static ItemStack item(final String name) {
    return li.cil.oc.api.Items.get(name).createItemStack(1);
  }

  private static Screen screen(final GameTestHelper helper, final BlockPos pos, final Direction pitch, final Direction yaw) {
    helper.setBlock(pos, Blocks.SCREEN_TIER_2.get());
    final Screen screen = (Screen) helper.getBlockEntity(pos);
    screen.trySetPitchYaw(pitch, yaw);
    return screen;
  }

  /** The client's copy of a screen, not in the world (in a level, as a client's is). */
  private static Screen clientScreen(final GameTestHelper helper, final BlockPos pos) {
    final Screen screen = new Screen(helper.absolutePos(pos), Blocks.SCREEN_TIER_1.get().defaultBlockState(), 0) {
      @Override
      public boolean isServer() {
        return false;
      }
    };
    screen.setLevel(helper.getLevel());
    return screen;
  }

  private static void expect(final GameTestHelper helper, final Screen screen, final Direction side, final String texture, final int rotation) {
    final String got = ScreenFrameTextures.texture(screen, side);
    final int turns = ScreenFrameTextures.rotation(screen, side);
    helper.assertTrue(texture.equals(got) && rotation == turns, "the " + side + " face of the screen at " + helper.relativePos(screen.getBlockPos())
      + " has " + got + " turned " + turns + ", not " + texture + " turned " + rotation);
  }

  /**
   * Each block of a display has OC 1.12's frame textures for where it sits (the frame was drawn from the display's
   * origin by its block entity renderer, and vanished with it).
   */
  @GameTest(template = EMPTY)
  public static void screenFramesAreChosenByPlaceInTheDisplay(final GameTestHelper helper) {
    // a wall 3 wide and 2 high, facing north: its origin is the east bottom block (the left one, seen from the north)
    final Screen[][] wall = new Screen[3][2];
    for (int x = 0; x < 3; x++) {
      for (int y = 0; y < 2; y++) wall[x][y] = screen(helper, new BlockPos(1 + x, 1 + y, 2), Direction.NORTH, Direction.NORTH);
    }
    // a 2 by 2 ceiling facing down, turned east: its origin is the south-west block
    final Screen[][] ceiling = new Screen[2][2];
    for (int x = 0; x < 2; x++) {
      for (int z = 0; z < 2; z++) ceiling[x][z] = screen(helper, new BlockPos(1 + x, 3, 1 + z), Direction.DOWN, Direction.EAST);
    }
    // and a lone one facing up
    final Screen lone = screen(helper, new BlockPos(4, 1, 0), Direction.UP, Direction.SOUTH);
    when(helper, () -> wall[0][0].width == 3 && wall[0][0].height == 2 && ceiling[0][0].width == 2 && ceiling[0][0].height == 2, 100, () -> {
      helper.assertTrue(wall[2][0].isOrigin() && ceiling[0][1].isOrigin(), "the displays' origins are not the expected blocks");
      expect(helper, wall[2][0], Direction.NORTH, "fbl2", 0);
      expect(helper, wall[1][0], Direction.NORTH, "fbm2", 0);
      expect(helper, wall[0][0], Direction.NORTH, "fbr2", 0);
      expect(helper, wall[2][1], Direction.NORTH, "ftl", 0);
      expect(helper, wall[0][1], Direction.NORTH, "ftr", 0);
      expect(helper, wall[2][1], Direction.UP, "bht", 2);
      expect(helper, wall[2][0], Direction.SOUTH, "bbr2", 0);
      expect(helper, ceiling[0][1], Direction.DOWN, "fbl", 1);
      expect(helper, ceiling[1][0], Direction.DOWN, "ftr", 1);
      expect(helper, ceiling[0][1], Direction.UP, "btl", 3);
      expect(helper, lone, Direction.UP, "f2", 0);
      expect(helper, lone, Direction.NORTH, "b2", 0);
      expect(helper, lone, Direction.DOWN, "b", 0);
      helper.succeed();
    });
  }

  /**
   * The client's robot finds its inventory's size when an inventory upgrade goes into or out of a container slot (the
   * menu's slot update comes to it, and only the server worked the size out: its GUI kept the old slots).
   */
  @GameTest(template = EMPTY)
  public static void aClientRobotFollowsItsInventorySize(final GameTestHelper helper) {
    final var robot = new li.cil.oc.neoforge.common.blockentity.Robot(helper.absolutePos(AT), Blocks.ROBOT.get().defaultBlockState()) {
      @Override
      public boolean isServer() {
        return false;
      }
    };
    robot.info.containers.add(item(Constants.ItemName.UpgradeContainerTier3));
    robot.setLevel(helper.getLevel());
    robot.updateInventorySize();
    helper.assertTrue(robot.mainInventory().getContainerSize() == 0, "the robot starts with " + robot.mainInventory().getContainerSize() + " slots");
    robot.setItem(1, item(Constants.ItemName.InventoryUpgrade));
    helper.assertTrue(robot.mainInventory().getContainerSize() == 16, "with an inventory upgrade the client's robot has " + robot.mainInventory().getContainerSize() + " slots");
    robot.setItem(1, ItemStack.EMPTY);
    helper.assertTrue(robot.mainInventory().getContainerSize() == 0, "without it the client's robot has " + robot.mainInventory().getContainerSize() + " slots");
    helper.succeed();
  }

  /** A screen the client removes (broken, or its chunk unloaded) takes its buffer along (each was kept until the world was left). */
  @GameTest(template = EMPTY)
  public static void aClientScreenTakesItsBufferAlong(final GameTestHelper helper) {
    final Screen screen = clientScreen(helper, AT);
    TextBuffer.clientBuffers.add(screen.buffer);
    try {
      screen.setRemoved();
      helper.assertTrue(!TextBuffer.clientBuffers.contains(screen.buffer), "the removed screen's buffer is still kept");
    } finally {
      TextBuffer.clientBuffers.remove(screen.buffer);
    }
    helper.succeed();
  }

  /** A client buffer replaces the one kept under its address (a tablet made a new one each time it was opened, all kept). */
  @GameTest(template = EMPTY)
  public static void aClientBufferReplacesTheOneForItsAddress(final GameTestHelper helper) {
    final Screen first = clientScreen(helper, AT);
    final Screen second = clientScreen(helper, AT.above());
    final String address = java.util.UUID.randomUUID().toString();
    first.buffer.proxy.nodeAddress = address;
    second.buffer.proxy.nodeAddress = address;
    try {
      TextBuffer.addClientBuffer(first.buffer);
      TextBuffer.addClientBuffer(second.buffer);
      helper.assertTrue(TextBuffer.clientBuffers.contains(second.buffer) && !TextBuffer.clientBuffers.contains(first.buffer),
        "the buffer kept for the address: the new one " + TextBuffer.clientBuffers.contains(second.buffer) + ", the old one " + TextBuffer.clientBuffers.contains(first.buffer));
    } finally {
      TextBuffer.removeClientBuffer(first.buffer);
      TextBuffer.removeClientBuffer(second.buffer);
    }
    helper.succeed();
  }
}
