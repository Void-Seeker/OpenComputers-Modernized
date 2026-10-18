package li.cil.oc.neoforge.gametest;

import java.util.List;
import li.cil.oc.core.Constants;
import li.cil.oc.core.impl.common.entity.Drone;
import li.cil.oc.neoforge.OpenComputers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Drones: what they carry is not duplicated or lost by their actions, their GUI or their trips; they survive portals,
 * unloading and water around them, and refuse nonsense.
 */
@GameTestHolder(OpenComputers.ID)
@PrefixGameTestTemplate(false)
public final class DroneTests {
  private static final String EMPTY = "empty";
  private static final String DRONE = "local d = component.proxy(component.list('drone')()) ";
  private static final BlockPos AT = new BlockPos(2, 3, 2);

  private DroneTests() {
  }

  private static void floor(final GameTestHelper helper) {
    for (int x = 0; x < 5; x++) {
      for (int z = 0; z < 5; z++) helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
    }
  }

  private static int count(final Drone drone, final Item item) {
    int n = 0;
    for (int i = 0; i < drone.mainInventory().getContainerSize(); i++) {
      if (drone.mainInventory().getItem(i).is(item)) n += drone.mainInventory().getItem(i).getCount();
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

  /** Placing a block with an experience upgrade aboard places one (it duplicated the rest of the stack). */
  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void dronePlacesWithoutDuplicating(final GameTestHelper helper) {
    floor(helper);
    TestMachines.runDrone(helper, AT, List.of(Constants.ItemName.InventoryUpgrade, Constants.ItemName.ExperienceUpgrade),
      drone -> drone.mainInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 16)),
      DRONE + "local ok, why = d.place(0) error('done:' .. tostring(ok) .. ',' .. tostring(why))", 60, (drone, result) -> {
        final boolean placed = helper.getBlockState(AT.below()).is(Blocks.COBBLESTONE);
        helper.assertTrue(placed && count(drone, Items.COBBLESTONE) == 15,
          "place returned " + result + ": placed " + placed + ", the drone has " + count(drone, Items.COBBLESTONE) + " cobblestone of 16");
        helper.succeed();
      });
  }

  /** Swinging with an experience upgrade aboard leaves the drone's inventory alone (it deleted and shifted it). */
  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void droneSwingsWithoutLosingItems(final GameTestHelper helper) {
    floor(helper);
    helper.setBlock(AT.south(), Blocks.DIRT);
    TestMachines.runDrone(helper, AT, List.of(Constants.ItemName.InventoryUpgrade, Constants.ItemName.ExperienceUpgrade), drone -> {
      drone.mainInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 10));
      drone.mainInventory().setItem(1, new ItemStack(Items.APPLE, 3));
    }, DRONE + "local ok, why = d.swing(3) error('done:' .. tostring(ok) .. ',' .. tostring(why))", 80, (drone, result) -> {
      helper.assertTrue(count(drone, Items.COBBLESTONE) == 10 && count(drone, Items.APPLE) == 3,
        "swing returned " + result + ": the drone has " + count(drone, Items.COBBLESTONE) + " cobblestone, " + count(drone, Items.APPLE) + " apples");
      helper.succeed();
    });
  }

  /** use() on a cow with a lead in the drone: no lead is made (it leashed with a copy and dropped a new lead). */
  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void droneUseOnAnEntityDuplicatesNothing(final GameTestHelper helper) {
    floor(helper);
    helper.setBlock(AT.south().below(), Blocks.STONE);   // the cow stands level with the drone, in its line of sight
    helper.spawnWithNoFreeWill(EntityType.COW, AT.south()).setPersistenceRequired();
    TestMachines.runDrone(helper, AT, List.of(Constants.ItemName.InventoryUpgrade), drone -> drone.mainInventory().setItem(0, new ItemStack(Items.LEAD)),
      DRONE + "d.use(3) d.use(3) error('done:ok')", 80, (drone, result) -> {
        helper.assertTrue(count(drone, Items.LEAD) + loose(helper, Items.LEAD) == 1,
          "leads: " + count(drone, Items.LEAD) + " in the drone, " + loose(helper, Items.LEAD) + " on the ground (1 at the start)");
        helper.succeed();
      });
  }

  /** use() on a potted flower: the flower ends up in the drone (it was put in a hand the drone does not have, and lost). */
  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void droneTakesAFlowerFromAPot(final GameTestHelper helper) {
    helper.setBlock(AT.south().below(), Blocks.STONE);
    helper.setBlock(AT.south(), Blocks.POTTED_DANDELION);
    TestMachines.runDrone(helper, AT, List.of(Constants.ItemName.InventoryUpgrade), drone -> {
    }, DRONE + "local ok, why = d.use(3) error('done:' .. tostring(ok) .. ',' .. tostring(why))", 60, (drone, result) -> {
      helper.assertTrue(count(drone, Items.DANDELION) == 1 && helper.getBlockState(AT.south()).is(Blocks.FLOWER_POT),
        "use returned " + result + ": " + count(drone, Items.DANDELION) + " dandelions in the drone, the pot is " + helper.getBlockState(AT.south()));
      helper.succeed();
    });
  }

  /** A tank upgrade gives a drone a tank (it had none: Forge's IFluidTank was looked for). */
  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void droneHasItsTank(final GameTestHelper helper) {
    TestMachines.runDrone(helper, AT, List.of(Constants.ItemName.TankUpgrade), drone -> {
    }, DRONE + "error('done:' .. tostring(d.tankCount()))", 40, (drone, result) -> {
      helper.assertTrue("1".equals(result), "a drone with a tank upgrade has " + result + " tanks");
      helper.succeed();
    });
  }

  /** move/setAcceleration refuse NaN and infinities (NaN froze the drone for good, inf flew it out of the world). */
  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void droneRefusesNonsenseMoves(final GameTestHelper helper) {
    TestMachines.runDrone(helper, AT, List.of(), drone -> {
    }, DRONE + "local a = pcall(d.setAcceleration, 0/0) local b = pcall(d.move, 0/0, 0, 0) local c = pcall(d.move, math.huge, 0, 0) "
      + "error('done:' .. tostring(a) .. ',' .. tostring(b) .. ',' .. tostring(c))", 40, (drone, result) -> {
      final var v = drone.getDeltaMovement();
      helper.assertTrue("false,false,false".equals(result) && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z)
          && Float.isFinite(drone.targetX()) && Math.abs(drone.targetX() - drone.getX()) < 2,
        "setAcceleration(NaN), move(NaN), move(inf) succeeded: " + result + "; velocity " + v + ", target x " + drone.targetX());
      helper.succeed();
    });
  }

  /** A drone through a portal with no way out (an unlinked nether portal) stays where it was (it was deleted). */
  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void droneSurvivesAPortalWithoutExit(final GameTestHelper helper) {
    TestMachines.runDrone(helper, AT, List.of(Constants.ItemName.InventoryUpgrade), drone -> drone.mainInventory().setItem(0, new ItemStack(Items.DIAMOND, 7)),
      "while true do computer.pullSignal(1) end", 20, (drone, result) -> {
        final var nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        final Entity moved = drone.changeDimension(nether);
        if (moved == null) {
          helper.assertTrue(!drone.isRemoved() && count(drone, Items.DIAMOND) == 7, "a failed trip removed the drone (" + count(drone, Items.DIAMOND) + " diamonds left)");
        } else {
          helper.assertTrue(moved instanceof Drone d && count(d, Items.DIAMOND) == 7, "the drone came through as " + moved);
          moved.discard();
        }
        helper.succeed();
      });
  }

  /** Through a portal, a drone keeps its target where it was relative to it (it flew to near 0, 0 of the new world). */
  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void droneKeepsItsTargetThroughAPortal(final GameTestHelper helper) {
    TestMachines.runDrone(helper, AT, List.of(), drone -> {
    }, "while true do computer.pullSignal(1) end", 20, (drone, result) -> {
      final var end = helper.getLevel().getServer().getLevel(Level.END);
      final Entity moved = drone.changeDimension(end);
      helper.assertTrue(moved instanceof Drone, "the drone did not reach the End: " + moved);
      final Drone there = (Drone) moved;
      helper.runAfterDelay(3, () -> {
        final double off = Math.sqrt(Math.pow(there.targetX() - there.getX(), 2) + Math.pow(there.targetY() - there.getY(), 2) + Math.pow(there.targetZ() - there.getZ(), 2));
        there.discard();
        helper.assertTrue(off < 2, "in the End the drone's target is " + off + " blocks from it (" + there.targetX() + ", " + there.targetY() + ", " + there.targetZ() + ")");
        helper.succeed();
      });
    });
  }

  /** A drone's open GUI is gone with the drone (it went on taking items and giving them to nothing). */
  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void droneGuiClosesWhenTheDroneIsGone(final GameTestHelper helper) {
    TestMachines.runDrone(helper, AT, List.of(Constants.ItemName.InventoryUpgrade), drone -> {
    }, "while true do computer.pullSignal(1) end", 20, (drone, result) -> {
      final var player = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(helper.getLevel());
      player.moveTo(drone.getX() + 1, drone.getY(), drone.getZ());
      helper.assertTrue(drone.mainInventory().stillValid(player), "the drone's inventory is not valid next to it");
      drone.kill();
      helper.assertTrue(!drone.mainInventory().stillValid(player), "the killed drone's inventory is still valid for its open GUI");
      helper.succeed();
    });
  }

  /** A drone unloaded with its chunk lets go of its machine (the Lua state leaked, its components stayed connected). */
  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void droneUnloadedLetsGoOfItsMachine(final GameTestHelper helper) {
    TestMachines.runDrone(helper, AT, List.of(), drone -> {
    }, "while true do computer.pullSignal(1) end", 20, (drone, result) -> {
      helper.assertTrue(drone.machine().isRunning(), "the drone does not run");
      drone.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);
      helper.assertTrue(!drone.machine().isRunning() && drone.machine().node().network() == null,
        "unloaded, the drone's machine runs: " + drone.machine().isRunning() + ", is connected: " + (drone.machine().node().network() != null));
      helper.succeed();
    });
  }

  /** A drone on a waterlogged slab is not "in water": it keeps running (it was switched off and fell). */
  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void droneOnAWaterloggedSlabKeepsRunning(final GameTestHelper helper) {
    helper.setBlock(AT.below(), Blocks.STONE);
    helper.setBlock(AT, Blocks.STONE_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM).setValue(BlockStateProperties.WATERLOGGED, true));
    TestMachines.runDrone(helper, AT, List.of(), drone -> {
    }, "while true do computer.pullSignal(1) end", 40, (drone, result) -> {
      helper.assertTrue(drone.machine().isRunning(), "the drone on a waterlogged slab stopped: " + drone.machine().lastError());
      helper.succeed();
    });
  }
}
