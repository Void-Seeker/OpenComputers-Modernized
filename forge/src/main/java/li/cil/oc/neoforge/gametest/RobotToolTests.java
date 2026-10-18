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
    // and the holder carries nothing afterwards: a robot action must not find copies to draw on for free (a mana
    // tool paying its wear from a copied mana tablet)
    helper.assertTrue(robot.player().getInventory().items.stream().allMatch(ItemStack::isEmpty),
      "the robot's fake player still carries copies after the tick: " + robot.player().getInventory().items);

    // OC's own items are left alone (a tablet would boot in the robot's hand)
    robot.equipmentInventory().setItem(0, li.cil.oc.api.Items.get(Constants.BlockName.Cable).createItemStack(1));
    final boolean[] ticked = {false};
    robot.tickTool((tool, holder) -> ticked[0] = true);
    helper.assertTrue(!ticked[0], "an OC item in the robot's hand was ticked");
    helper.succeed();
  }

  /**
   * The carried inventory: robot slots go in after an offset (the tool's place), changes come back, and of the mana a
   * mana item lost only the kept share is spent.
   */
  @GameTest(template = EMPTY)
  public static void carriedInventoryChargesOnlyTheKeptShareOfMana(final GameTestHelper helper) {
    final var robotInv = new net.minecraft.world.SimpleContainer(4);
    final ItemStack tablet = new ItemStack(Items.PAPER);
    tablet.getOrCreateTag().putInt("mana", 1000);
    final ItemStack half = new ItemStack(Items.MAP);
    half.getOrCreateTag().putInt("mana", 1000);
    robotInv.setItem(0, tablet);
    robotInv.setItem(1, half);
    robotInv.setItem(2, new ItemStack(Items.COAL, 5));
    final var carriedList = net.minecraft.core.NonNullList.withSize(36, ItemStack.EMPTY);
    final var carried = li.cil.oc.core.impl.util.CarriedInventory.carry(robotInv, carriedList, 1, -1);
    helper.assertTrue(carriedList.get(1).is(Items.PAPER) && carriedList.get(3).is(Items.COAL) && carriedList.get(0).isEmpty(),
      "robot slot 0 is not carried after the tool's place: " + carriedList.subList(0, 4));
    carriedList.get(1).getTag().putInt("mana", 940);   // a tool paid 60 for its wear
    carriedList.get(2).getTag().putInt("mana", 940);
    carriedList.get(3).shrink(1);
    final java.util.function.Predicate<ItemStack> mana = st -> st.getTag() != null && st.getTag().contains("mana");
    carried.bringBack(0.1, st -> mana.test(st) && st.is(Items.PAPER), net.minecraft.util.RandomSource.create(1));
    carried.restore();
    helper.assertTrue(robotInv.getItem(0) == tablet && tablet.getTag().getInt("mana") == 994,
      "with a 10% wear rate the tablet should have paid 6 of the 60: " + tablet.getTag());
    helper.assertTrue(half.getTag().getInt("mana") == 940, "an item that is no mana item paid in full: " + half.getTag());
    helper.assertTrue(robotInv.getItem(2).getCount() == 4, "the coal count did not come back: " + robotInv.getItem(2));
    helper.assertTrue(carriedList.stream().allMatch(ItemStack::isEmpty), "the holder still carries copies: " + carriedList.subList(0, 4));

    // a slot the robot changed itself meanwhile stays as the robot has it
    final var again = li.cil.oc.core.impl.util.CarriedInventory.carry(robotInv, carriedList, 0, -1);
    carriedList.get(2).shrink(1);
    robotInv.setItem(2, new ItemStack(Items.DIAMOND));
    again.bringBack(1, mana, net.minecraft.util.RandomSource.create(1));
    again.restore();
    helper.assertTrue(robotInv.getItem(2).is(Items.DIAMOND), "the holder's change overwrote the robot's own: " + robotInv.getItem(2));
    helper.succeed();
  }

  /**
   * During a robot action its tool's holder carries the robot's inventory (after the tool): what a mod takes from it
   * there (an arrow, mana) comes off the robot's own items, and afterwards the holder carries nothing.
   */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void robotActionsDrawOnTheRobotsInventory(final GameTestHelper helper) {
    final Robot robot = robot(helper, new BlockPos(2, 2, 2));
    robot.equipmentInventory().setItem(0, new ItemStack(Items.STICK));
    robot.mainInventory().setItem(0, new ItemStack(Items.ARROW, 16));
    final ItemStack jar = new ItemStack(Items.PAPER);
    jar.getOrCreateTag().putInt("charge", 100);
    robot.mainInventory().setItem(1, jar);
    final var agent = (li.cil.oc.neoforge.server.agent.Player) robot.player();
    final String[] seen = {null};
    final java.util.function.Consumer<net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem> mod = event -> {
      if (event.getEntity() != agent) return;
      final var carried = agent.getInventory().items;
      seen[0] = carried.get(0).getItem() + " " + carried.get(1) + " " + carried.get(2);
      carried.get(1).shrink(1);                              // like a bow taking an arrow
      carried.get(2).getOrCreateTag().putInt("charge", 95);  // like a tool drawing on a cell
    };
    net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(mod);
    try {
      agent.useEquippedItem(0);
    } finally {
      net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(mod);
    }
    helper.assertTrue("stick 16 arrow 1 paper".equals(seen[0]), "during the action the holder carried " + seen[0]);
    helper.assertTrue(robot.mainInventory().getItem(0).getCount() == 15, "the arrow did not come off the robot's: " + robot.mainInventory().getItem(0));
    helper.assertTrue(robot.mainInventory().getItem(1) == jar && jar.getTag().getInt("charge") == 95, "the cell was not drawn on in place: " + jar.getTag());
    helper.assertTrue(agent.getInventory().items.stream().allMatch(ItemStack::isEmpty), "the fake player still carries copies after the action");
    helper.succeed();
  }

  /** A robot draws its bow for the time asked, lets go, and the arrow comes from the robot's inventory. */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void robotBowShootsArrowsFromTheRobotsInventory(final GameTestHelper helper) {
    final Robot robot = robot(helper, new BlockPos(2, 2, 2));
    robot.equipmentInventory().setItem(0, new ItemStack(Items.BOW));
    robot.mainInventory().setItem(0, new ItemStack(Items.ARROW, 16));
    final var agent = (li.cil.oc.neoforge.server.agent.Player) robot.player();
    li.cil.oc.neoforge.server.agent.Player.updatePositionAndRotation(agent, net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.SOUTH);
    helper.assertTrue(agent.useEquippedItem(1.5), "drawing the bow did nothing");
    helper.assertTrue(robot.mainInventory().getItem(0).getCount() == 15, "the robot's arrows: " + robot.mainInventory().getItem(0));
    final var arrows = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.projectile.Arrow.class,
      new net.minecraft.world.phys.AABB(helper.absolutePos(BlockPos.ZERO)).inflate(40));
    helper.assertTrue(!arrows.isEmpty(), "no arrow was shot");
    helper.assertTrue(robot.equipmentInventory().getItem(0).is(Items.BOW), "the robot no longer holds its bow: " + robot.equipmentInventory().getItem(0));
    // without arrows it does not shoot
    robot.mainInventory().setItem(0, ItemStack.EMPTY);
    agent.useEquippedItem(1.5);
    helper.assertTrue(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.projectile.Arrow.class,
      new net.minecraft.world.phys.AABB(helper.absolutePos(BlockPos.ZERO)).inflate(40)).size() == arrows.size(), "a bow without arrows shot");
    helper.succeed();
  }

  /** A potion held for its whole use duration is drunk (an empty bottle is left); held briefly, it is not. */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void robotDrinksAPotionHeldLongEnough(final GameTestHelper helper) {
    final Robot robot = robot(helper, new BlockPos(2, 2, 2));
    robot.equipmentInventory().setItem(0, net.minecraft.world.item.alchemy.PotionUtils.setPotion(new ItemStack(Items.POTION), net.minecraft.world.item.alchemy.Potions.WATER));
    final var agent = (li.cil.oc.neoforge.server.agent.Player) robot.player();
    li.cil.oc.neoforge.server.agent.Player.updatePositionAndRotation(agent, net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.SOUTH);
    agent.useEquippedItem(0.3);
    helper.assertTrue(robot.equipmentInventory().getItem(0).is(Items.POTION), "a potion held 0.3 s was drunk: " + robot.equipmentInventory().getItem(0));
    agent.useEquippedItem(2);
    helper.assertTrue(robot.equipmentInventory().getItem(0).is(Items.GLASS_BOTTLE), "a potion held 2 s was not drunk: " + robot.equipmentInventory().getItem(0));
    helper.succeed();
  }

  /**
   * Mana a robot's tool draws during an action (its wear paid from a mana tablet) is charged at the robot's wear
   * rate (itemDamageRate, 0.1 without experience): of 60 drawn, 6 are spent.
   */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void robotActionsSpendManaAtTheRobotsWearRate(final GameTestHelper helper) {
    final Robot robot = robot(helper, new BlockPos(2, 2, 2));
    robot.equipmentInventory().setItem(0, new ItemStack(Items.STICK));
    final ItemStack tablet = new ItemStack(Items.PAPER);
    tablet.getOrCreateTag().putInt("mana", 1000);
    robot.mainInventory().setItem(0, tablet);
    final var agent = (li.cil.oc.neoforge.server.agent.Player) robot.player();
    final java.util.function.Consumer<net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem> tool = event -> {
      if (event.getEntity() == agent) agent.getInventory().items.get(1).getTag().putInt("mana", 940);
    };
    final var manaItems = li.cil.oc.core.impl.util.CarriedInventory.manaItems;
    li.cil.oc.core.impl.util.CarriedInventory.manaItems = st -> st.is(Items.PAPER) && st.getTag() != null && st.getTag().contains("mana");
    net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(tool);
    try {
      agent.useEquippedItem(0);
    } finally {
      net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(tool);
      li.cil.oc.core.impl.util.CarriedInventory.manaItems = manaItems;
    }
    final double rate = li.cil.oc.core.impl.OCSettings.get().itemDamageRate;
    helper.assertTrue(tablet.getTag().getInt("mana") == 1000 - (int) Math.round(60 * rate),
      "60 mana drawn at a wear rate of " + rate + " left " + tablet.getTag().getInt("mana"));
    helper.succeed();
  }
}
