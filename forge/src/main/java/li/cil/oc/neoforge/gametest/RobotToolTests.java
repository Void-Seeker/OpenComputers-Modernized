package li.cil.oc.neoforge.gametest;

import li.cil.oc.core.Constants;
import li.cil.oc.core.impl.common.blockentity.Robot;
import li.cil.oc.core.impl.common.blockentity.RobotProxy;
import li.cil.oc.neoforge.OpenComputers;
import li.cil.oc.neoforge.common.init.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * A robot's tool ticks like the item in a player's hand, with the robot's inventory as what its holder carries.
 */
@GameTestHolder(OpenComputers.ID)
@PrefixGameTestTemplate(false)
public final class RobotToolTests {
  private static final String EMPTY = "empty";

  private RobotToolTests() {
  }

  private static Robot robot(final GameTestHelper helper, final BlockPos pos) {
    final var data = new li.cil.oc.core.impl.common.item.data.RobotData();
    data.name = "toolbot";
    data.components.add(li.cil.oc.api.Items.get(Constants.ItemName.InventoryUpgrade).createItemStack(1));
    helper.setBlock(pos, Blocks.ROBOT.get());
    final BlockPos abs = helper.absolutePos(pos);
    final var placer = net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(), new com.mojang.authlib.GameProfile(java.util.UUID.fromString("5d1e2a6e-3f2b-4c39-9a7e-1d2f3b4c5d70"), "oc-test-toolbot"));
    Blocks.ROBOT.get().setPlacedBy(helper.getLevel(), abs, helper.getLevel().getBlockState(abs), placer, data.createItemStack());
    return ((RobotProxy) helper.getLevel().getBlockEntity(abs)).robot;
  }

  /** A lodestone compass forgets a lodestone that is not there when it ticks: the robot's tool ticks by itself. */
  @GameTest(template = EMPTY)
  public static void robotToolTicksLikeAHeldItem(final GameTestHelper helper) {
    final Robot robot = robot(helper, new BlockPos(2, 2, 2));
    final ItemStack compass = new ItemStack(Items.COMPASS);
    final CompoundTag tag = compass.getOrCreateTag();
    tag.putBoolean("LodestoneTracked", true);
    tag.putString("LodestoneDimension", helper.getLevel().dimension().location().toString());
    tag.put("LodestonePos", NbtUtils.writeBlockPos(helper.absolutePos(new BlockPos(0, 1, 0))));
    robot.equipmentInventory().setItem(0, compass);
    helper.runAfterDelay(5, () -> {
      final ItemStack tool = robot.equipmentInventory().getItem(0);
      helper.assertTrue(tool.is(Items.COMPASS) && tool.getTag() != null && !tool.getTag().contains("LodestonePos"),
        "the compass in the robot's hand did not tick: " + tool.getTag());
      helper.succeed();
    });
  }

  /**
   * The tool sees the robot's inventory as its holder's (slot for slot); a carried item whose data the tool changes
   * (an ember cell drawn on) is changed in place, one whose count changes is replaced.
   */
  @GameTest(template = EMPTY)
  public static void robotToolSeesAndChangesTheRobotsInventory(final GameTestHelper helper) {
    final Robot robot = robot(helper, new BlockPos(2, 2, 2));
    robot.equipmentInventory().setItem(0, new ItemStack(Items.STICK));
    final ItemStack cell = new ItemStack(Items.PAPER);
    cell.getOrCreateTag().putInt("ember", 100);
    robot.mainInventory().setItem(1, cell);
    robot.mainInventory().setItem(2, new ItemStack(Items.COAL, 5));
    final boolean[] saw = {false};
    robot.tickTool((tool, holder) -> {
      final var carried = holder.getInventory().items;
      saw[0] = tool.is(Items.STICK) && carried.get(1).is(Items.PAPER) && carried.get(1).getTag().getInt("ember") == 100 && carried.get(2).getCount() == 5;
      carried.get(1).getOrCreateTag().putInt("ember", 95);
      carried.get(2).shrink(1);
    });
    helper.assertTrue(saw[0], "the tool did not see the robot's inventory as its holder's");
    helper.assertTrue(robot.mainInventory().getItem(1) == cell && cell.getTag().getInt("ember") == 95, "the cell was not drawn on in place: " + robot.mainInventory().getItem(1).getTag());
    helper.assertTrue(robot.mainInventory().getItem(2).getCount() == 4, "the coal count did not come back: " + robot.mainInventory().getItem(2));

    // OC's own items are left alone (a tablet would boot in the robot's hand)
    robot.equipmentInventory().setItem(0, li.cil.oc.api.Items.get(Constants.BlockName.Cable).createItemStack(1));
    final boolean[] ticked = {false};
    robot.tickTool((tool, holder) -> ticked[0] = true);
    helper.assertTrue(!ticked[0], "an OC item in the robot's hand was ticked");
    helper.succeed();
  }
}
