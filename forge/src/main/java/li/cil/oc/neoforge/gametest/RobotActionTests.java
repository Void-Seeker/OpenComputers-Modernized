package li.cil.oc.neoforge.gametest;

import java.util.List;
import li.cil.oc.core.Constants;
import li.cil.oc.core.impl.common.blockentity.Robot;
import li.cil.oc.neoforge.OpenComputers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Robots act as a player would, with the robot's inventory as the player's: what they pick up, get back from an
 * action or craft ends up in the robot (or, when it is full, on the ground), and they use, place and hit like a player.
 */
@GameTestHolder(OpenComputers.ID)
@PrefixGameTestTemplate(false)
public final class RobotActionTests {
  private static final String EMPTY = "empty";
  private static final String ROBOT = "local r = component.proxy(component.list('robot')()) ";
  private static final BlockPos AT = new BlockPos(2, 2, 2);

  private RobotActionTests() {
  }

  private static void floor(final GameTestHelper helper) {
    for (int x = 0; x < 5; x++) {
      for (int z = 0; z < 5; z++) helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
    }
  }

  private static int count(final Robot robot, final Item item) {
    int n = 0;
    for (int i = 0; i < robot.mainInventory().getContainerSize(); i++) {
      if (robot.mainInventory().getItem(i).is(item)) n += robot.mainInventory().getItem(i).getCount();
    }
    return n;
  }

  private static int loose(final GameTestHelper helper, final Item item) {
    int n = 0;
    for (final ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(8))) {
      if (e.getItem().is(item)) n += e.getItem().getCount();
    }
    return n;
  }

  private static ItemEntity drop(final GameTestHelper helper, final BlockPos pos, final ItemStack stack) {
    final var at = helper.absoluteVec(net.minecraft.world.phys.Vec3.atBottomCenterOf(pos));
    final var entity = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, stack);
    entity.setDeltaMovement(0, 0, 0);
    entity.setNoPickUpDelay();
    helper.getLevel().addFreshEntity(entity);
    return entity;
  }

  private static <T extends Mob> T mob(final GameTestHelper helper, final EntityType<T> type, final BlockPos pos) {
    final T mob = helper.spawnWithNoFreeWill(type, pos);
    mob.setPersistenceRequired();
    return mob;
  }

  /** use() with a tool that does nothing to the block leaves the robot's inventory as it was (it shifted by a slot). */
  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void robotUseLeavesTheInventoryAlone(final GameTestHelper helper) {
    helper.setBlock(AT.south(), Blocks.STONE);
    TestMachines.runRobot(helper, AT, List.of(Constants.ItemName.InventoryUpgrade), robot -> {
      robot.equipmentInventory().setItem(0, new ItemStack(Items.WOODEN_PICKAXE));
      robot.mainInventory().setItem(0, new ItemStack(Items.DIRT, 5));
      robot.mainInventory().setItem(1, new ItemStack(Items.APPLE, 3));
      robot.mainInventory().setItem(2, new ItemStack(Items.STICK, 7));
    }, ROBOT + "local ok, why = r.use(3) error('done:' .. tostring(ok))", 60, (robot, result) -> {
      final var inv = robot.mainInventory();
      helper.assertTrue(inv.getItem(0).is(Items.DIRT) && inv.getItem(0).getCount() == 5 && inv.getItem(1).is(Items.APPLE) && inv.getItem(1).getCount() == 3
          && inv.getItem(2).is(Items.STICK) && inv.getItem(2).getCount() == 7 && inv.getItem(3).isEmpty(),
        "after use(): " + inv.getItem(0) + ", " + inv.getItem(1) + ", " + inv.getItem(2) + ", " + inv.getItem(3));
      helper.succeed();
    });
  }

  /** suck() picks items up from the ground into the robot (they used to vanish). */
  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void robotSucksItemsFromTheGround(final GameTestHelper helper) {
    floor(helper);
    TestMachines.runRobot(helper, AT, List.of(Constants.ItemName.InventoryUpgrade), robot -> drop(helper, AT.south(), new ItemStack(Items.COBBLESTONE, 5)),
      ROBOT + "local n = r.suck(3) error('done:' .. tostring(n))", 60, (robot, result) -> {
        helper.assertTrue(count(robot, Items.COBBLESTONE) == 5 && loose(helper, Items.COBBLESTONE) == 0,
          "suck returned " + result + ": the robot has " + count(robot, Items.COBBLESTONE) + " cobblestone, " + loose(helper, Items.COBBLESTONE) + " left on the ground");
        helper.succeed();
      });
  }

  /** The tractor beam pulls items into the robot. */
  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void robotTractorBeamCollectsItems(final GameTestHelper helper) {
    floor(helper);
    TestMachines.runRobot(helper, AT, List.of(Constants.ItemName.InventoryUpgrade, Constants.ItemName.TractorBeamUpgrade),
      robot -> drop(helper, AT.south().south(), new ItemStack(Items.COBBLESTONE, 5)),
      "local t = component.proxy(component.list('tractor_beam')()) local ok = t.suck() error('done:' .. tostring(ok))", 60, (robot, result) -> {
        helper.assertTrue(count(robot, Items.COBBLESTONE) == 5, "tractor beam returned " + result + ": the robot has " + count(robot, Items.COBBLESTONE) + " cobblestone");
        helper.succeed();
      });
  }

  /** The crafting upgrade crafts from the robot's inventory into it. */
  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void robotCraftsFromItsInventory(final GameTestHelper helper) {
    TestMachines.runRobot(helper, AT, List.of(Constants.ItemName.InventoryUpgrade, Constants.ItemName.CraftingUpgrade),
      robot -> robot.mainInventory().setItem(0, new ItemStack(Items.OAK_LOG, 2)),
      "local c = component.proxy(component.list('crafting')()) local ok, n = c.craft(4) error('done:' .. tostring(ok) .. ',' .. tostring(n))", 60, (robot, result) -> {
        helper.assertTrue("true,4".equals(result) && count(robot, Items.OAK_PLANKS) == 4 && count(robot, Items.OAK_LOG) == 1,
          "craft(4) returned " + result + ": " + count(robot, Items.OAK_PLANKS) + " planks, " + count(robot, Items.OAK_LOG) + " logs");
        helper.succeed();
      });
  }

  /** The generator gives the empty bucket of a lava bucket back. */
  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void robotGeneratorGivesTheBucketBack(final GameTestHelper helper) {
    TestMachines.runRobot(helper, AT, List.of(Constants.ItemName.InventoryUpgrade, Constants.ItemName.GeneratorUpgrade),
      robot -> robot.mainInventory().setItem(0, new ItemStack(Items.LAVA_BUCKET)),
      "local g = component.proxy(component.list('generator')()) local ok, why = g.insert(1) error('done:' .. tostring(ok) .. ',' .. tostring(why))", 60, (robot, result) -> {
        helper.assertTrue(count(robot, Items.BUCKET) == 1 && count(robot, Items.LAVA_BUCKET) == 0,
          "insert returned " + result + ": " + count(robot, Items.BUCKET) + " empty buckets, " + count(robot, Items.LAVA_BUCKET) + " lava buckets");
        helper.succeed();
      });
  }

  /** use() on a cow with buckets: the milk ends up in the robot. */
  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void robotMilksACow(final GameTestHelper helper) {
    floor(helper);
    mob(helper, EntityType.COW, AT.south());
    TestMachines.runRobot(helper, AT, List.of(Constants.ItemName.InventoryUpgrade), robot -> robot.equipmentInventory().setItem(0, new ItemStack(Items.BUCKET, 4)),
      ROBOT + "local ok, why = r.use(3) error('done:' .. tostring(ok) .. ',' .. tostring(why))", 60, (robot, result) -> {
        final var tool = robot.equipmentInventory().getItem(0);
        helper.assertTrue(count(robot, Items.MILK_BUCKET) == 1 && tool.is(Items.BUCKET) && tool.getCount() == 3,
          "use returned " + result + ": " + count(robot, Items.MILK_BUCKET) + " milk buckets in the robot, tool " + tool);
        helper.succeed();
      });
  }

  /** use() with shears on a sheep shears it, and the wool goes into the robot - or stays on the ground when it is full. */
  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void robotShearsASheep(final GameTestHelper helper) {
    floor(helper);
    final var sheep = mob(helper, EntityType.SHEEP, AT.south());
    TestMachines.runRobot(helper, AT, List.of(Constants.ItemName.InventoryUpgrade), robot -> robot.equipmentInventory().setItem(0, new ItemStack(Items.SHEARS)),
      ROBOT + "local ok, why = r.use(3) error('done:' .. tostring(ok) .. ',' .. tostring(why))", 60, (robot, result) -> {
        helper.assertTrue(sheep.isSheared() && count(robot, Items.WHITE_WOOL) > 0 && loose(helper, Items.WHITE_WOOL) == 0,
          "use returned " + result + ": sheared " + sheep.isSheared() + ", " + count(robot, Items.WHITE_WOOL) + " wool in the robot, " + loose(helper, Items.WHITE_WOOL) + " on the ground");
        helper.succeed();
      });
  }

  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void robotFullShearsASheepAndLeavesTheWool(final GameTestHelper helper) {
    floor(helper);
    final var sheep = mob(helper, EntityType.SHEEP, AT.south());
    TestMachines.runRobot(helper, AT, List.of(Constants.ItemName.InventoryUpgrade), robot -> {
      robot.equipmentInventory().setItem(0, new ItemStack(Items.SHEARS));
      for (int i = 0; i < robot.mainInventory().getContainerSize(); i++) robot.mainInventory().setItem(i, new ItemStack(Items.STONE, 64));
    }, ROBOT + "local ok, why = r.use(3) error('done:' .. tostring(ok) .. ',' .. tostring(why))", 60, (robot, result) -> {
      helper.assertTrue(sheep.isSheared() && loose(helper, Items.WHITE_WOOL) > 0,
        "use returned " + result + ": sheared " + sheep.isSheared() + ", " + loose(helper, Items.WHITE_WOOL) + " wool on the ground");
      helper.succeed();
    });
  }

  /** useDown() with an empty bucket over water scoops it (the player's eyes were two blocks up). */
  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void robotScoopsWaterBelow(final GameTestHelper helper) {
    final BlockPos water = new BlockPos(2, 2, 2), at = water.above();
    helper.setBlock(water.below(), Blocks.STONE);
    for (final BlockPos side : List.of(water.north(), water.south(), water.east(), water.west())) helper.setBlock(side, Blocks.STONE);
    helper.setBlock(water, Blocks.WATER);
    TestMachines.runRobot(helper, at, List.of(Constants.ItemName.InventoryUpgrade), robot -> robot.equipmentInventory().setItem(0, new ItemStack(Items.BUCKET)),
      ROBOT + "local ok, why = r.use(0) error('done:' .. tostring(ok) .. ',' .. tostring(why))", 60, (robot, result) -> {
        final var tool = robot.equipmentInventory().getItem(0);
        helper.assertTrue(tool.is(Items.WATER_BUCKET) && !helper.getBlockState(water).is(Blocks.WATER),
          "use(down) returned " + result + ": tool " + tool + ", below " + helper.getBlockState(water));
        helper.succeed();
      });
  }

  /** A robot facing east places a piston facing it, west (the player's head pointed south whatever the robot faced). */
  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void robotPlacesAPistonFacingItself(final GameTestHelper helper) {
    floor(helper);
    TestMachines.runRobot(helper, AT, List.of(Constants.ItemName.InventoryUpgrade), robot -> robot.mainInventory().setItem(0, new ItemStack(Items.PISTON)),
      ROBOT + "r.turn(false) local ok, why = r.place(3) error('done:' .. tostring(ok) .. ',' .. tostring(why))", 60, (robot, result) -> {
        final var state = helper.getBlockState(AT.east());
        helper.assertTrue(state.is(Blocks.PISTON) && state.getValue(PistonBaseBlock.FACING) == net.minecraft.core.Direction.WEST,
          "place returned " + result + ": " + state);
        helper.succeed();
      });
  }

  /** A sword hits with its full damage (the attack was scaled down to a fifth: the fake player never recharged). */
  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void robotHitsWithFullStrength(final GameTestHelper helper) {
    floor(helper);
    final var pig = mob(helper, EntityType.PIG, AT.south());
    TestMachines.runRobot(helper, AT, List.of(Constants.ItemName.InventoryUpgrade), robot -> robot.equipmentInventory().setItem(0, new ItemStack(Items.DIAMOND_SWORD)),
      ROBOT + "local ok, why = r.swing(3) error('done:' .. tostring(ok) .. ',' .. tostring(why))", 60, (robot, result) -> {
        helper.assertTrue(pig.getHealth() <= 4, "swing returned " + result + ": the pig has " + pig.getHealth() + " of 10 health left (a diamond sword does 7)");
        helper.succeed();
      });
  }

  /** use() on a lever flips it while the robot holds a block (the block is used first, then the item, as for a player). */
  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void robotFlipsALeverRatherThanPlacingItsTool(final GameTestHelper helper) {
    floor(helper);
    helper.setBlock(AT.south().south(), Blocks.STONE);
    helper.setBlock(AT.south(), Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.WALL).setValue(LeverBlock.FACING, net.minecraft.core.Direction.NORTH));
    TestMachines.runRobot(helper, AT, List.of(Constants.ItemName.InventoryUpgrade), robot -> robot.equipmentInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 8)),
      ROBOT + "local ok, why = r.use(3) error('done:' .. tostring(ok) .. ',' .. tostring(why))", 60, (robot, result) -> {
        final var lever = helper.getBlockState(AT.south());
        helper.assertTrue(lever.is(Blocks.LEVER) && lever.getValue(BlockStateProperties.POWERED) && robot.equipmentInventory().getItem(0).getCount() == 8
            && helper.getBlockState(AT.south().above()).isAir(),
          "use returned " + result + ": " + lever + ", above it " + helper.getBlockState(AT.south().above()) + ", tool " + robot.equipmentInventory().getItem(0));
        helper.succeed();
      });
  }

  /** The selected slot survives the robot being saved and loaded (it always came back as slot 1). */
  @GameTest(template = EMPTY)
  public static void robotKeepsItsSelectedSlotWhenLoaded(final GameTestHelper helper) {
    final BlockPos abs = helper.absolutePos(AT);
    helper.setBlock(AT, li.cil.oc.neoforge.common.init.Blocks.ROBOT.get());
    final var data = new li.cil.oc.core.impl.common.item.data.RobotData();
    data.name = "keeper";
    data.components.add(li.cil.oc.api.Items.get(Constants.ItemName.InventoryUpgrade).createItemStack(1));
    li.cil.oc.neoforge.common.init.Blocks.ROBOT.get().setPlacedBy(helper.getLevel(), abs, helper.getLevel().getBlockState(abs),
      net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(helper.getLevel()), data.createItemStack());
    final var proxy = (li.cil.oc.core.impl.common.blockentity.RobotProxy) helper.getLevel().getBlockEntity(abs);
    proxy.robot.selectedSlot(5);
    final var tag = proxy.saveWithFullMetadata();
    final var loaded = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(abs, helper.getLevel().getBlockState(abs), tag);
    helper.assertTrue(loaded instanceof li.cil.oc.core.impl.common.blockentity.RobotProxy, "not a robot: " + loaded);
    final int selected = ((li.cil.oc.core.impl.common.blockentity.RobotProxy) loaded).robot.selectedSlot();
    helper.assertTrue(selected == 5, "selected slot 5 came back as " + selected);
    helper.succeed();
  }

  /** An inventory upgrade in an upgrade container adds slots; taken out again, what was in them drops. */
  @GameTest(template = EMPTY)
  public static void robotInventoryUpgradeInAContainerAddsSlots(final GameTestHelper helper) {
    final BlockPos abs = helper.absolutePos(AT);
    helper.setBlock(AT, li.cil.oc.neoforge.common.init.Blocks.ROBOT.get());
    final var data = new li.cil.oc.core.impl.common.item.data.RobotData();
    data.name = "boxer";
    data.containers.add(li.cil.oc.api.Items.get(Constants.ItemName.UpgradeContainerTier1).createItemStack(1));
    li.cil.oc.neoforge.common.init.Blocks.ROBOT.get().setPlacedBy(helper.getLevel(), abs, helper.getLevel().getBlockState(abs),
      net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(helper.getLevel()), data.createItemStack());
    final Robot robot = ((li.cil.oc.core.impl.common.blockentity.RobotProxy) helper.getLevel().getBlockEntity(abs)).robot;
    helper.assertTrue(robot.mainInventory().getContainerSize() == 0, "no inventory yet, has " + robot.mainInventory().getContainerSize());
    robot.setItem(1, li.cil.oc.api.Items.get(Constants.ItemName.InventoryUpgrade).createItemStack(1));
    helper.assertTrue(robot.mainInventory().getContainerSize() == 16, "an inventory upgrade in the container gives " + robot.mainInventory().getContainerSize() + " slots");
    robot.mainInventory().setItem(5, new ItemStack(Items.DIAMOND, 3));
    robot.setItem(1, ItemStack.EMPTY);
    helper.assertTrue(robot.mainInventory().getContainerSize() == 0 && loose(helper, Items.DIAMOND) == 3,
      "taken out: " + robot.mainInventory().getContainerSize() + " slots, " + loose(helper, Items.DIAMOND) + " diamonds dropped");
    helper.succeed();
  }

  /** Waterlogged stairs are what they are: solid. A robot does not move into them (it replaced them with itself). */
  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void robotTreatsWaterloggedBlocksAsSolid(final GameTestHelper helper) {
    floor(helper);
    helper.setBlock(AT.south(), Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true));
    TestMachines.runRobot(helper, AT, List.of(Constants.ItemName.InventoryUpgrade),
      ROBOT + "local blocked, what = r.detect(3) local ok, why = r.move(3) error('done:' .. tostring(blocked) .. ',' .. tostring(what) .. ',' .. tostring(ok))", 60, (robot, result) -> {
        helper.assertTrue(result != null && result.startsWith("true,solid,") && !result.endsWith("true") && helper.getBlockState(AT.south()).is(Blocks.OAK_STAIRS),
          "detect/move returned " + result + ", in front: " + helper.getBlockState(AT.south()));
        helper.succeed();
      });
  }

  /** A robot's geolyzer and its hopper sides go by the robot's sides, not the world's (here it faces east). */
  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void robotGeolyzerAndHoppersUseTheRobotsSides(final GameTestHelper helper) {
    floor(helper);
    helper.setBlock(AT.east(), Blocks.STONE);
    TestMachines.runRobot(helper, AT, List.of(Constants.ItemName.InventoryUpgrade, Constants.BlockName.Geolyzer),
      ROBOT + "r.turn(false) local g = component.proxy(component.list('geolyzer')()) local blocked, what = g.detect(3) error('done:' .. tostring(blocked) .. ',' .. tostring(what))", 60, (robot, result) -> {
        helper.assertTrue("true,solid".equals(result), "facing east, the geolyzer saw " + result + " in front (stone there, air to the south)");
        final int[] fromRight = robot.getSlotsForFace(robot.toGlobal(net.minecraft.core.Direction.WEST));
        helper.assertTrue(fromRight.length == 1 && fromRight[0] == 0, "from the robot's right a hopper reaches " + java.util.Arrays.toString(fromRight) + ", not the tool slot");
        helper.succeed();
      });
  }

  /** The experience upgrade's speed-up keeps fractions of a second (break times were cut to whole seconds). */
  @GameTest(template = EMPTY)
  public static void robotBreakTimeKeepsFractions(final GameTestHelper helper) {
    final BlockPos abs = helper.absolutePos(AT);
    helper.setBlock(AT, li.cil.oc.neoforge.common.init.Blocks.ROBOT.get());
    final var data = new li.cil.oc.core.impl.common.item.data.RobotData();
    data.name = "timer";
    li.cil.oc.neoforge.common.init.Blocks.ROBOT.get().setPlacedBy(helper.getLevel(), abs, helper.getLevel().getBlockState(abs),
      net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(helper.getLevel()), data.createItemStack());
    final Robot robot = ((li.cil.oc.core.impl.common.blockentity.RobotProxy) helper.getLevel().getBlockEntity(abs)).robot;
    final var event = new li.cil.oc.api.event.RobotBreakBlockEvent.Pre(robot, helper.getLevel(), abs.south(), 0.45);
    net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event);
    helper.assertTrue(Math.abs(event.getBreakTime() - 0.45) < 1e-9, "a 0.45 s break became " + event.getBreakTime() + " s");
    helper.succeed();
  }

  /**
   * The tank controller takes from a fluid item only what really comes out: a lava bucket into a tank with room for
   * half a bucket stays a lava bucket (half was lost and the bucket emptied); into an empty tank it gives a bucket back.
   */
  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void robotTankControllerDrainsOnlyWhatFits(final GameTestHelper helper) {
    TestMachines.runRobot(helper, AT, List.of(Constants.ItemName.InventoryUpgrade, Constants.ItemName.TankUpgrade, Constants.ItemName.TankControllerUpgrade), robot -> {
      robot.mainInventory().setItem(0, new ItemStack(Items.LAVA_BUCKET));
      robot.mainInventory().setItem(1, new ItemStack(Items.LAVA_BUCKET));
      final var tank = (li.cil.oc.core.util.FluidTank) robot.getFluidTank(0);
      tank.fill(new li.cil.oc.core.util.FluidStack("minecraft:lava", tank.getCapacity() - 1500), false);   // room for 1500 mB
    }, "local t = component.proxy(component.list('tank_controller')()) local r = component.proxy(component.list('robot')()) "
      + "local ok1 = t.drain() r.select(2) local ok2 = t.drain() error('done:' .. tostring(ok1) .. ',' .. tostring(ok2))", 60, (robot, result) -> {
      final var tank = (li.cil.oc.core.util.FluidTank) robot.getFluidTank(0);
      helper.assertTrue("true,nil".equals(result) && tank.getFluidAmount() == tank.getCapacity() - 500
          && robot.mainInventory().getItem(0).is(Items.BUCKET) && robot.mainInventory().getItem(1).is(Items.LAVA_BUCKET),
        "drains returned " + result + ": tank " + tank.getFluidAmount() + "/" + tank.getCapacity() + " mB, slots " + robot.mainInventory().getItem(0) + ", " + robot.mainInventory().getItem(1));
      helper.succeed();
    });
  }

  /** transferTo from an empty slot moves nothing; compareTo of two empty slots is true; a part into an empty slot moves. */
  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void robotTransferToAndCompareToHandleEmptySlots(final GameTestHelper helper) {
    TestMachines.runRobot(helper, AT, List.of(Constants.ItemName.InventoryUpgrade), robot -> robot.mainInventory().setItem(1, new ItemStack(Items.DIRT, 4)),
      ROBOT + "r.select(1) local a = r.transferTo(2) local b = r.compareTo(3) r.select(2) local c = r.transferTo(5, 3) "
        + "error('done:' .. tostring(a) .. ',' .. tostring(b) .. ',' .. tostring(c))", 60, (robot, result) -> {
        final var inv = robot.mainInventory();
        helper.assertTrue("false,true,true".equals(result) && inv.getItem(0).isEmpty() && inv.getItem(1).getCount() == 1 && inv.getItem(4).is(Items.DIRT) && inv.getItem(4).getCount() == 3,
          "transferTo/compareTo returned " + result + ": slots " + inv.getItem(0) + ", " + inv.getItem(1) + ", " + inv.getItem(4));
        helper.succeed();
      });
  }

  /** A robot facing east: its redstone front output is east, and getOutput() reports it as its front. */
  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void robotRedstoneOutputsByItsOwnSides(final GameTestHelper helper) {
    TestMachines.runRobot(helper, AT, List.of(Constants.ItemName.RedstoneCardTier1),
      ROBOT + "r.turn(false) local rs = component.proxy(component.list('redstone')()) rs.setOutput(3, 15) local o = rs.getOutput() "
        + "error('done:' .. tostring(o[3]) .. ',' .. tostring(o[5]))", 60, (robot, result) -> {
        // what the robot gives its east neighbour (vanilla asks the emitter with the direction towards it)
        final int signal = helper.getLevel().getSignal(helper.absolutePos(AT), net.minecraft.core.Direction.WEST);
        helper.assertTrue("15,0".equals(result) && signal == 15, "getOutput()[front], [world east] = " + result + "; the block to the east gets " + signal);
        helper.succeed();
      });
  }

  /** /setblock and structures clear what a robot carries, not its components: the robot item keeps them. */
  @GameTest(template = EMPTY)
  public static void robotClearedKeepsItsComponents(final GameTestHelper helper) {
    final BlockPos abs = helper.absolutePos(AT);
    helper.setBlock(AT, li.cil.oc.neoforge.common.init.Blocks.ROBOT.get());
    final var data = new li.cil.oc.core.impl.common.item.data.RobotData();
    data.name = "cleared";
    data.components.add(li.cil.oc.api.Items.get(Constants.ItemName.InventoryUpgrade).createItemStack(1));
    li.cil.oc.neoforge.common.init.Blocks.ROBOT.get().setPlacedBy(helper.getLevel(), abs, helper.getLevel().getBlockState(abs),
      net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(helper.getLevel()), data.createItemStack());
    final Robot robot = ((li.cil.oc.core.impl.common.blockentity.RobotProxy) helper.getLevel().getBlockEntity(abs)).robot;
    robot.mainInventory().setItem(0, new ItemStack(Items.DIAMOND, 3));
    net.minecraft.world.Clearable.tryClear(helper.getLevel().getBlockEntity(abs));
    helper.getLevel().destroyBlock(abs, true);
    final var robots = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(3)).stream()
      .map(ItemEntity::getItem).filter(st -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).getPath().equals("robot")).toList();
    helper.assertTrue(robots.size() == 1 && new li.cil.oc.core.impl.common.item.data.RobotData(robots.get(0)).components.size() == 1 && loose(helper, Items.DIAMOND) == 0,
      robots.size() + " robot items, components " + (robots.isEmpty() ? "-" : new li.cil.oc.core.impl.common.item.data.RobotData(robots.get(0)).components.size()) + ", diamonds " + loose(helper, Items.DIAMOND));
    helper.succeed();
  }

  /** The robot player's addItem behaves as vanilla's: the stack keeps what did not fit, true only for all of it. */
  @GameTest(template = EMPTY)
  public static void robotPlayerAddItemKeepsTheRest(final GameTestHelper helper) {
    final BlockPos abs = helper.absolutePos(AT);
    helper.setBlock(AT, li.cil.oc.neoforge.common.init.Blocks.ROBOT.get());
    final var data = new li.cil.oc.core.impl.common.item.data.RobotData();
    data.name = "taker";
    data.components.add(li.cil.oc.api.Items.get(Constants.ItemName.InventoryUpgrade).createItemStack(1));
    li.cil.oc.neoforge.common.init.Blocks.ROBOT.get().setPlacedBy(helper.getLevel(), abs, helper.getLevel().getBlockState(abs),
      net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(helper.getLevel()), data.createItemStack());
    final Robot robot = ((li.cil.oc.core.impl.common.blockentity.RobotProxy) helper.getLevel().getBlockEntity(abs)).robot;
    for (int i = 0; i < robot.mainInventory().getContainerSize() - 1; i++) robot.mainInventory().setItem(i, new ItemStack(Items.STONE, 64));
    final ItemStack offered = new ItemStack(Items.COBBLESTONE, 64);
    offered.setCount(100);
    final boolean all = robot.player().addItem(offered);
    helper.assertTrue(!all && offered.getCount() == 36 && count(robot, Items.COBBLESTONE) == 64,
      "addItem of 100 into one free slot said " + all + ", left " + offered.getCount() + ", the robot has " + count(robot, Items.COBBLESTONE));
    helper.succeed();
  }

  /** The piston upgrade leaves air where it pushed a waterlogged block from (water was duplicated). */
  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void robotPistonLeavesNoWaterBehind(final GameTestHelper helper) {
    floor(helper);
    helper.setBlock(AT.south(), Blocks.STONE_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true));
    TestMachines.runRobot(helper, AT, List.of(Constants.ItemName.PistonUpgrade),
      "local p = component.proxy(component.list('piston')()) local ok, why = p.push() error('done:' .. tostring(ok) .. ',' .. tostring(why))", 60, (robot, result) -> {
        helper.assertTrue(result != null && result.startsWith("true") && helper.getBlockState(AT.south().south()).is(Blocks.STONE_SLAB) && helper.getBlockState(AT.south()).isAir(),
          "push returned " + result + ": " + helper.getBlockState(AT.south()) + " left, " + helper.getBlockState(AT.south().south()) + " pushed");
        helper.succeed();
      });
  }

  /** The sign upgrade reads the side of a sign that faces the robot, and leaves a waxed sign alone. */
  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void robotSignUpgradeReadsTheSideFacingIt(final GameTestHelper helper) {
    // a standing sign south of the robot with its front facing south: the robot sees its back
    helper.setBlock(AT.south(), Blocks.OAK_SIGN.defaultBlockState().setValue(net.minecraft.world.level.block.StandingSignBlock.ROTATION, 0));
    final var sign = (net.minecraft.world.level.block.entity.SignBlockEntity) helper.getBlockEntity(AT.south());
    sign.setText(new net.minecraft.world.level.block.entity.SignText().setMessage(0, net.minecraft.network.chat.Component.literal("front")), true);
    sign.setText(new net.minecraft.world.level.block.entity.SignText().setMessage(0, net.minecraft.network.chat.Component.literal("back")), false);
    TestMachines.runRobot(helper, AT, List.of(Constants.ItemName.SignUpgrade),
      "local s = component.proxy(component.list('sign')()) local v = s.getValue() s.setValue('robot') local w = s.getValue() "
        + "error('done:' .. tostring(v):match('[^\\n]*') .. ',' .. tostring(w):match('[^\\n]*'))", 60, (robot, result) -> {
        helper.assertTrue("back,robot".equals(result) && sign.getFrontText().getMessage(0, false).getString().equals("front"),
          "read/wrote " + result + "; the front now says " + sign.getFrontText().getMessage(0, false).getString());
        helper.succeed();
      });
  }

  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void robotSignUpgradeLeavesAWaxedSignAlone(final GameTestHelper helper) {
    helper.setBlock(AT.south(), Blocks.OAK_SIGN.defaultBlockState().setValue(net.minecraft.world.level.block.StandingSignBlock.ROTATION, 8));
    final var sign = (net.minecraft.world.level.block.entity.SignBlockEntity) helper.getBlockEntity(AT.south());
    sign.setText(new net.minecraft.world.level.block.entity.SignText().setMessage(0, net.minecraft.network.chat.Component.literal("kept")), true);
    sign.setWaxed(true);
    TestMachines.runRobot(helper, AT, List.of(Constants.ItemName.SignUpgrade),
      "local s = component.proxy(component.list('sign')()) local ok, why = s.setValue('robot') error('done:' .. tostring(ok) .. ',' .. tostring(why))", 60, (robot, result) -> {
        helper.assertTrue("nil,the sign is waxed".equals(result) && sign.getFrontText().getMessage(0, false).getString().equals("kept"),
          "setValue on a waxed sign returned " + result + "; it says " + sign.getFrontText().getMessage(0, false).getString());
        helper.succeed();
      });
  }
}
