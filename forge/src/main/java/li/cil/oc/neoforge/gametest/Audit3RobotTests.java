package li.cil.oc.neoforge.gametest;

import static li.cil.oc.neoforge.gametest.TestMachines.when;

import com.mojang.authlib.GameProfile;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import li.cil.oc.core.Constants;
import li.cil.oc.core.impl.common.blockentity.Robot;
import li.cil.oc.core.impl.server.component.UpgradeChunkloaderBase;
import li.cil.oc.neoforge.OpenComputers;
import li.cil.oc.neoforge.common.event.ChunkloaderUpgradeHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Robots: their components when the inventory changes size, upgrades swapped in by Lua, their menu, the player they
 * ask permission as, their chunkloader and their selected slot.
 */
@GameTestHolder(OpenComputers.ID)
@PrefixGameTestTemplate(false)
public final class Audit3RobotTests {
  private static final String EMPTY = "empty";
  private static final String ROBOT = "local r = component.proxy(component.list('robot')()) ";
  private static final String CONTROLLER = "local ic = component.proxy(component.list('inventory_controller')()) ";
  private static final String LOADER = "local cl = component.proxy(component.list('chunkloader')()) ";
  private static final String IDLE = "while true do computer.pullSignal(1) end\n";
  private static final BlockPos AT = new BlockPos(2, 2, 2);
  private static final TicketType<ChunkPos> TICKING = TicketType.create("oc_test_robot_loader", Comparator.comparingLong(ChunkPos::toLong));

  private Audit3RobotTests() {
  }

  private static ItemStack item(final String name) {
    return li.cil.oc.api.Items.get(name).createItemStack(1);
  }

  private static boolean isOC(final ItemStack stack, final String name) {
    return !stack.isEmpty() && ItemStack.isSameItem(stack, item(name));
  }

  /** A robot from the containers and parts at AT, facing south, not started. */
  private static Robot placeRobot(final GameTestHelper helper, final List<String> containers, final List<String> parts) {
    final BlockPos abs = helper.absolutePos(AT);
    helper.setBlock(AT, li.cil.oc.neoforge.common.init.Blocks.ROBOT.get());
    final var data = new li.cil.oc.core.impl.common.item.data.RobotData();
    data.name = "audit";
    for (final String container : containers) data.containers.add(item(container));
    for (final String part : parts) data.components.add(item(part));
    final var placer = FakePlayerFactory.getMinecraft(helper.getLevel());
    placer.setYRot(0);
    placer.setXRot(0);
    li.cil.oc.neoforge.common.init.Blocks.ROBOT.get().setPlacedBy(helper.getLevel(), abs, helper.getLevel().getBlockState(abs), placer, data.createItemStack());
    return ((li.cil.oc.core.impl.common.blockentity.RobotProxy) helper.getLevel().getBlockEntity(abs)).robot;
  }

  private static int count(final Iterable<?> things) {
    int n = 0;
    for (final Object ignored : things) n++;
    return n;
  }

  /** How many of the item the robot carries in its inventory, and how many lie in the test's own space. */
  private static int carried(final Robot robot, final java.util.function.Predicate<ItemStack> what) {
    int n = 0;
    for (int i = 0; i < robot.mainInventory().getContainerSize(); i++) {
      final ItemStack stack = robot.mainInventory().getItem(i);
      if (what.test(stack)) n += stack.getCount();
    }
    return n;
  }

  private static int loose(final GameTestHelper helper, final java.util.function.Predicate<ItemStack> what) {
    int n = 0;
    final var box = new AABB(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(5, 5, 5)));
    for (final ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box)) {
      if (what.test(e.getItem())) n += e.getItem().getCount();
    }
    return n;
  }

  private static int inChest(final GameTestHelper helper, final BlockPos pos, final Item item) {
    final var chest = (Container) helper.getBlockEntity(pos);
    int n = 0;
    for (int i = 0; i < chest.getContainerSize(); i++) {
      if (chest.getItem(i).is(item)) n += chest.getItem(i).getCount();
    }
    return n;
  }

  private static void floor(final GameTestHelper helper) {
    for (int x = 0; x < 5; x++) {
      for (int z = 0; z < 5; z++) helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
    }
  }

  // ---- components and the inventory's size ----

  /**
   * An inventory upgrade put into a container while the robot stands moves its components along with their slots:
   * they are saved and left alone by the inventory (saves skipped every component, and items taken out of the first
   * inventory slots removed the components that had been there). Taken out again, they move back.
   */
  @GameTest(template = EMPTY)
  public static void robotComponentsMoveWithTheInventorySize(final GameTestHelper helper) {
    final Robot robot = placeRobot(helper, List.of(Constants.ItemName.UpgradeContainerTier3),
      List.of(Constants.ItemName.CPUTier1, Constants.ItemName.RAMTier1, Constants.ItemName.TankUpgrade));
    final int components = count(robot.installedComponents());
    final ItemStack tankItem = robot.info.components.get(2);
    final var provider = helper.getLevel().registryAccess();
    helper.assertTrue(robot.tankCount() == 1, "placed with " + robot.tankCount() + " tanks");

    robot.setItem(1, item(Constants.ItemName.InventoryUpgrade));
    helper.assertTrue(robot.mainInventory().getContainerSize() == 16, "the inventory upgrade gave " + robot.mainInventory().getContainerSize() + " slots");
    helper.assertTrue(robot.tankCount() == 1, "with 16 slots more the robot has " + robot.tankCount() + " tanks");
    for (int slot = 0; slot < 3; slot++) { // where the components were
      robot.mainInventory().setItem(slot, new ItemStack(Items.DIAMOND));
      robot.mainInventory().setItem(slot, ItemStack.EMPTY);
    }
    helper.assertTrue(count(robot.installedComponents()) == components, "items in and out of the first slots left " + count(robot.installedComponents()) + " of " + components + " components");
    robot.getFluidTank(0).fill(new li.cil.oc.core.util.FluidStack("minecraft:water", 1000), false);
    robot.saveComponents(provider);
    final int saved = robot.dataTag(li.cil.oc.api.API.driver.driverFor(tankItem), tankItem).getCompound("fluid").getInt("Amount");
    helper.assertTrue(saved == 1000, "the tank holding 1000 mB was saved with " + saved);

    robot.setItem(1, ItemStack.EMPTY);
    helper.assertTrue(robot.mainInventory().getContainerSize() == 0 && robot.tankCount() == 1 && count(robot.installedComponents()) == components,
      "taken out: " + robot.mainInventory().getContainerSize() + " slots, " + robot.tankCount() + " tanks, " + count(robot.installedComponents()) + " components");
    robot.getFluidTank(0).fill(new li.cil.oc.core.util.FluidStack("minecraft:water", 500), false);
    robot.saveComponents(provider);
    final int again = robot.dataTag(li.cil.oc.api.API.driver.driverFor(tankItem), tankItem).getCompound("fluid").getInt("Amount");
    helper.assertTrue(again == 1500, "the tank holding 1500 mB was saved with " + again);
    helper.succeed();
  }

  // ---- installUpgrade ----

  /**
   * installUpgrade swapping an inventory upgrade out for the upgrade in a slot that goes with it (slot 20 of 32) loses
   * and copies nothing: the upgrade was installed and dropped with the vanished slots, and the inventory upgrade taken
   * out went over slot 16.
   */
  @GameTest(template = EMPTY, timeoutTicks = 10_000_000)
  public static void robotInstallUpgradeShrinkingTheInventoryKeepsEverything(final GameTestHelper helper) {
    floor(helper);
    TestMachines.runRobot(helper, AT, List.of(Constants.ItemName.UpgradeContainerTier3),
      List.of(Constants.ItemName.InventoryUpgrade, Constants.ItemName.InventoryControllerUpgrade), robot -> {
        robot.setItem(1, item(Constants.ItemName.InventoryUpgrade));
        robot.mainInventory().setItem(15, new ItemStack(Items.COBBLESTONE, 5));
        robot.mainInventory().setItem(19, item(Constants.ItemName.AngelUpgrade));
        robot.mainInventory().setItem(24, new ItemStack(Items.DIAMOND, 3));
      }, ROBOT + CONTROLLER + "r.select(20) local ok, why = ic.installUpgrade(1) "
        + "error('done:' .. tostring(ok) .. ',' .. tostring(why) .. ',' .. r.inventorySize() .. ',' .. r.select())", 60, (robot, result) -> {
        final var angel = (java.util.function.Predicate<ItemStack>) s -> isOC(s, Constants.ItemName.AngelUpgrade);
        final var inventory = (java.util.function.Predicate<ItemStack>) s -> isOC(s, Constants.ItemName.InventoryUpgrade);
        helper.assertTrue("true,nil,16,16".equals(result), "installUpgrade, inventory size, selection: " + result);
        helper.assertTrue(isOC(robot.getItem(1), Constants.ItemName.AngelUpgrade) && carried(robot, angel) + loose(helper, angel) == 0,
          "installed " + robot.getItem(1) + "; " + carried(robot, angel) + " angel upgrades carried, " + loose(helper, angel) + " dropped");
        helper.assertTrue(carried(robot, inventory) + loose(helper, inventory) == 1,
          "the inventory upgrade taken out: " + carried(robot, inventory) + " carried, " + loose(helper, inventory) + " dropped");
        helper.assertTrue(carried(robot, s -> s.is(Items.COBBLESTONE)) == 5, "slot 16 holds " + robot.mainInventory().getItem(15));
        helper.assertTrue(carried(robot, s -> s.is(Items.DIAMOND)) + loose(helper, s -> s.is(Items.DIAMOND)) == 3, "the diamonds from slot 25 are gone");
        helper.succeed();
      });
  }

  /** The same with the last inventory slots going: the inventory upgrade taken out drops (it vanished; the upgrade put in dropped too). */
  @GameTest(template = EMPTY, timeoutTicks = 10_000_000)
  public static void robotInstallUpgradeLeavingNoInventoryDropsTheOldUpgrade(final GameTestHelper helper) {
    floor(helper);
    // this run's upgrade (the test world is kept: one dropped here by an earlier run loaded late, past the clearing, and
    // the drop merged with it)
    final String run = UUID.randomUUID().toString();
    TestMachines.runRobot(helper, AT, List.of(Constants.ItemName.UpgradeContainerTier3), List.of(Constants.ItemName.InventoryControllerUpgrade), robot -> {
        final ItemStack old = item(Constants.ItemName.InventoryUpgrade);
        old.getOrCreateTag().putString("oc-test-run", run);
        robot.setItem(1, old);
        robot.mainInventory().setItem(2, item(Constants.ItemName.AngelUpgrade));
      }, ROBOT + CONTROLLER + "r.select(3) local ok, why = ic.installUpgrade(1) "
        + "error('done:' .. tostring(ok) .. ',' .. tostring(why) .. ',' .. r.inventorySize())", 60, (robot, result) -> {
        final var angel = (java.util.function.Predicate<ItemStack>) s -> isOC(s, Constants.ItemName.AngelUpgrade);
        final var inventory = (java.util.function.Predicate<ItemStack>) s -> isOC(s, Constants.ItemName.InventoryUpgrade) && s.getTag() != null && run.equals(s.getTag().getString("oc-test-run"));
        helper.assertTrue("true,nil,0".equals(result), "installUpgrade, inventory size: " + result);
        helper.assertTrue(isOC(robot.getItem(1), Constants.ItemName.AngelUpgrade) && loose(helper, angel) == 0,
          "installed " + robot.getItem(1) + "; " + loose(helper, angel) + " angel upgrades dropped");
        helper.assertTrue(loose(helper, inventory) == 1, loose(helper, inventory) + " inventory upgrades dropped");
        helper.succeed();
      });
  }

  // ---- the menu ----

  /** A robot's menu closes once the robot is broken (items taken from it were duplicated, items put in lost). */
  @GameTest(template = EMPTY)
  public static void robotMenuClosesWhenTheRobotIsBroken(final GameTestHelper helper) {
    final Robot robot = placeRobot(helper, List.of(), List.of(Constants.ItemName.InventoryUpgrade));
    final BlockPos abs = helper.absolutePos(AT);
    final FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.fromString("0c3a7e52-5d1b-4f0e-9a51-3b2f6c8d9e10"), "oc-robot-menu"));
    player.setPos(abs.getX() + 0.5, abs.getY() + 1, abs.getZ() + 1.5);
    final var menu = new li.cil.oc.neoforge.common.container.Robot(0, player.getInventory(), (li.cil.oc.neoforge.common.blockentity.Robot) robot);
    helper.assertTrue(menu.stillValid(player), "the menu of a robot next to the player is not valid");
    helper.getLevel().destroyBlock(abs, false);
    helper.assertTrue(!menu.stillValid(player), "the menu of a broken robot is still valid");
    helper.succeed();
  }

  // ---- whom robots ask permission as ----

  /** Runs the check, then lets go of the listener the test registered (the game tests ship in the jar). */
  private static void unregisterAfter(final Object listener, final Runnable check) {
    try {
      check.run();
    } finally {
      MinecraftForge.EVENT_BUS.unregister(listener);
    }
  }

  /** A robot drops into a chest that only its owner may open (a claim): it asks as its owner, not as OC's player. */
  @GameTest(template = EMPTY, timeoutTicks = 10_000_000)
  public static void robotUsesAChestAsItsOwner(final GameTestHelper helper) {
    floor(helper);
    helper.setBlock(AT.south(), Blocks.CHEST);
    final BlockPos chest = helper.absolutePos(AT.south());
    final Robot[] holder = new Robot[1];
    final Consumer<PlayerInteractEvent.RightClickBlock> ownerOnly = e -> {
      if (e.getPos().equals(chest) && holder[0] != null && !e.getEntity().getUUID().equals(holder[0].ownerUUID())) e.setCanceled(true);
    };
    MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, PlayerInteractEvent.RightClickBlock.class, ownerOnly);
    TestMachines.runRobot(helper, AT, List.of(Constants.ItemName.InventoryUpgrade), robot -> {
      holder[0] = robot;
      robot.mainInventory().setItem(0, new ItemStack(Items.DIAMOND, 3));
    }, ROBOT + "r.select(1) local ok = r.drop(3) error('done:' .. tostring(ok))", 60, (robot, result) -> unregisterAfter(ownerOnly, () -> {
      helper.assertTrue(inChest(helper, AT.south(), Items.DIAMOND) == 3, "the owner's chest got " + inChest(helper, AT.south(), Items.DIAMOND) + " of 3 diamonds (" + result + ")");
      helper.succeed();
    }));
  }

  /** A chest whose use is denied (not cancelled, as claims may) is not used (it was). */
  @GameTest(template = EMPTY, timeoutTicks = 10_000_000)
  public static void robotRespectsADeniedBlockUse(final GameTestHelper helper) {
    floor(helper);
    helper.setBlock(AT.south(), Blocks.CHEST);
    final BlockPos chest = helper.absolutePos(AT.south());
    final Consumer<PlayerInteractEvent.RightClickBlock> deny = e -> {
      if (e.getPos().equals(chest)) e.setUseBlock(Event.Result.DENY);
    };
    MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, PlayerInteractEvent.RightClickBlock.class, deny);
    TestMachines.runRobot(helper, AT, List.of(Constants.ItemName.InventoryUpgrade), robot -> robot.mainInventory().setItem(0, new ItemStack(Items.DIAMOND, 3)),
      ROBOT + "r.select(1) local ok = r.drop(3) error('done:' .. tostring(ok))", 60, (robot, result) -> unregisterAfter(deny, () -> {
        helper.assertTrue(inChest(helper, AT.south(), Items.DIAMOND) == 0, "the chest denied to robots got " + inChest(helper, AT.south(), Items.DIAMOND) + " diamonds");
        helper.succeed();
      }));
  }

  /** Grass in front that only the robot's owner may break is not in the way (detect and move asked as OC's player). */
  @GameTest(template = EMPTY, timeoutTicks = 10_000_000)
  public static void robotChecksBreakingAsItsOwner(final GameTestHelper helper) {
    floor(helper);
    helper.setBlock(AT.south().below(), Blocks.DIRT);
    helper.setBlock(AT.south(), Blocks.GRASS);
    final BlockPos grass = helper.absolutePos(AT.south());
    final Robot[] holder = new Robot[1];
    final Consumer<BlockEvent.BreakEvent> ownerOnly = e -> {
      if (e.getPos().equals(grass) && holder[0] != null && !e.getPlayer().getUUID().equals(holder[0].ownerUUID())) e.setCanceled(true);
    };
    MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, BlockEvent.BreakEvent.class, ownerOnly);
    TestMachines.runRobot(helper, AT, List.of(), robot -> holder[0] = robot,
      ROBOT + "local blocked, what = r.detect(3) error('done:' .. tostring(blocked) .. ',' .. tostring(what))", 60, (robot, result) -> unregisterAfter(ownerOnly, () -> {
        helper.assertTrue("false,replaceable".equals(result), "detect in front of grass its owner may break: " + result);
        helper.succeed();
      }));
  }

  // ---- the chunkloader ----

  /**
   * A robot in a far chunk row of its own (a loader forces the chunks around it; the test structures' are forced by the
   * framework and must stay so), ticking through a ticket of the test's, on stone. The cleanup lets go of all of it.
   */
  private static void inAFarChunk(final GameTestHelper helper, final int slot, final BiConsumer<BlockPos, Runnable> then) {
    final ServerLevel level = helper.getLevel();
    final BlockPos far = new BlockPos(-40000 - level.random.nextInt(64) * 64 + 8, 100, 40000 + slot * 64 + 8);
    final ChunkPos chunk = new ChunkPos(far);
    level.getChunkSource().addRegionTicket(TICKING, chunk, 2, chunk);
    final Runnable cleanup = () -> {
      for (int dz = -1; dz <= 2; dz++) {
        for (int dy = -1; dy <= 0; dy++) {
          final BlockPos pos = far.offset(0, dy, dz);
          level.removeBlockEntity(pos);
          level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16 | 64);
        }
      }
      level.getEntitiesOfClass(ItemEntity.class, new AABB(far).inflate(8)).forEach(Entity::discard);
      for (int dx = -1; dx <= 1; dx++) {
        for (int dz = -1; dz <= 1; dz++) level.setChunkForced(chunk.x + dx, chunk.z + dz, false);
      }
      level.getChunkSource().removeRegionTicket(TICKING, chunk, 2, chunk);
    };
    when(helper, () -> level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null && level.shouldTickBlocksAt(chunk.toLong()), 200, () -> {
      boolean handedOver = false;
      try {
        helper.assertTrue(level.shouldTickBlocksAt(chunk.toLong()), "the far chunk does not tick");
        level.setBlock(far.below(), Blocks.STONE.defaultBlockState(), 2 | 16);
        level.setBlock(far.south().below(), Blocks.STONE.defaultBlockState(), 2 | 16);
        level.setBlock(far.south(), Blocks.AIR.defaultBlockState(), 2 | 16); // (terrain at y 100 in some worlds: no move)
        then.accept(far, cleanup);
        handedOver = true;
      } finally {
        if (!handedOver) cleanup.run();
      }
    });
  }

  private static BlockPos relative(final GameTestHelper helper, final BlockPos abs) {
    return abs.subtract(helper.absolutePos(BlockPos.ZERO));
  }

  /** How many of the 3x3 chunks around the position are forced. */
  private static int forced(final ServerLevel level, final BlockPos pos) {
    final ChunkPos center = new ChunkPos(pos);
    int n = 0;
    for (int dx = -1; dx <= 1; dx++) {
      for (int dz = -1; dz <= 1; dz++) {
        if (level.getForcedChunks().contains(ChunkPos.asLong(center.x + dx, center.z + dz))) n++;
      }
    }
    return n;
  }

  private static UpgradeChunkloaderBase loader(final Robot robot) {
    for (final var env : robot.installedComponents()) {
      if (env instanceof UpgradeChunkloaderBase loader) return loader;
    }
    return null;
  }

  /** A robot whose chunkloader is off moves without forcing chunks (it forced them on every move, unpaid and for good). */
  @GameTest(template = EMPTY, timeoutTicks = 10_000_000)
  public static void robotMovingWithItsChunkloaderOffForcesNoChunks(final GameTestHelper helper) {
    inAFarChunk(helper, 0, (far, cleanup) -> TestMachines.runRobot(helper, relative(helper, far), List.of(Constants.ItemName.ChunkloaderUpgrade), robot -> {
      }, ROBOT + LOADER + "local was = cl.isActive() cl.setActive(false) local ok = r.move(3) "
        + "error('done:' .. tostring(was) .. ',' .. tostring(ok) .. ',' .. tostring(cl.isActive()))", 60, (robot, result) -> {
        try {
          helper.assertTrue("true,true,false".equals(result), "active while running, moved, active after: " + result);
          helper.assertTrue(forced(helper.getLevel(), far) == 0, forced(helper.getLevel(), far) + " chunks forced after a move with the loader off");
        } finally {
          cleanup.run();
        }
        helper.succeed();
      }));
  }

  /** A broken robot's chunkloader lets go of its chunks (only its machine's node went: they stayed forced). */
  @GameTest(template = EMPTY, timeoutTicks = 10_000_000)
  public static void brokenRobotReleasesItsChunks(final GameTestHelper helper) {
    inAFarChunk(helper, 1, (far, cleanup) -> TestMachines.runRobot(helper, relative(helper, far), List.of(Constants.ItemName.ChunkloaderUpgrade), robot -> {
      }, IDLE, 20, (robot, ignored) -> when(helper, () -> forced(helper.getLevel(), far) == 9, 100, () -> {
        try {
          helper.assertTrue(forced(helper.getLevel(), far) == 9, "the running robot's loader forced " + forced(helper.getLevel(), far) + " chunks");
          helper.getLevel().destroyBlock(far, false);
          helper.assertTrue(forced(helper.getLevel(), far) == 0, "the broken robot left " + forced(helper.getLevel(), far) + " chunks forced");
        } finally {
          cleanup.run();
        }
        helper.succeed();
      })));
  }

  /**
   * A running robot's chunkloader is on again after a reload, and lets go when the robot stops (it came back off, its
   * chunks still forced: a robot's machine resumes without computer.started, and robots are no Context).
   */
  @GameTest(template = EMPTY, timeoutTicks = 10_000_000)
  public static void reloadedRobotKeepsItsChunkloaderOn(final GameTestHelper helper) {
    final ServerLevel level = helper.getLevel();
    inAFarChunk(helper, 2, (far, cleanup) -> TestMachines.runRobot(helper, relative(helper, far), List.of(Constants.ItemName.ChunkloaderUpgrade), robot -> {
      }, IDLE, 20, (robot, ignored) -> when(helper, () -> forced(level, far) == 9, 100, () -> {
        boolean handedOver = false;
        try {
          helper.assertTrue(forced(level, far) == 9, "the running robot's loader forced " + forced(level, far) + " chunks");
          final var proxy = (li.cil.oc.core.impl.common.blockentity.RobotProxy) level.getBlockEntity(far);
          proxy.markUnloading();
          final var saved = proxy.saveWithFullMetadata();
          level.removeBlockEntity(far);
          final var loaded = (li.cil.oc.core.impl.common.blockentity.RobotProxy) net.minecraft.world.level.block.entity.BlockEntity.loadStatic(far, level.getBlockState(far), saved);
          level.getChunkAt(far).addAndRegisterBlockEntity(loaded);
          loaded.updateEntity();
          final Robot again = loaded.robot;
          when(helper, () -> again.machine().isRunning() && forced(level, far) == 9, 100, () -> {
            try {
              final var cl = loader(again);
              helper.assertTrue(again.machine().isRunning() && cl != null && cl.active() && forced(level, far) == 9,
                "reloaded: running " + again.machine().isRunning() + ", loader active " + (cl != null && cl.active()) + ", " + forced(level, far) + " chunks forced");
              again.machine().stop();
              when(helper, () -> forced(level, far) == 0, 100, () -> {
                try {
                  helper.assertTrue(forced(level, far) == 0, "the stopped robot left " + forced(level, far) + " chunks forced");
                } finally {
                  cleanup.run();
                }
                helper.succeed();
              });
            } catch (RuntimeException e) {
              cleanup.run();
              throw e;
            }
          });
          handedOver = true;
        } finally {
          if (!handedOver) cleanup.run();
        }
      })));
  }

  /** The loaders' chunks of a stopped server's levels are let go (they kept those levels, a whole world, for good). */
  @GameTest(template = EMPTY)
  @SuppressWarnings("unchecked")
  public static void chunkloadersForgetAStoppedServer(final GameTestHelper helper) {
    final Map<Object, Object> chunks;
    try {
      final var field = ChunkloaderUpgradeHandler.class.getDeclaredField("worldChunks");
      field.setAccessible(true);
      chunks = (Map<Object, Object>) field.get(null);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
    final var running = new HashMap<>(chunks); // what the other tests' loaders hold, put back (all on the server thread)
    chunks.put(new Object(), new HashMap<>());
    try {
      ChunkloaderUpgradeHandler.onServerStopped(new ServerStoppedEvent(helper.getLevel().getServer()));
      helper.assertTrue(chunks.isEmpty(), "after the server stopped the loaders still hold " + chunks.size() + " levels");
    } finally {
      chunks.clear();
      chunks.putAll(running);
    }
    helper.succeed();
  }

  // ---- the selected slot ----

  /** robot.select goes through the robot's own setter: clamped (and sent to the clients, whose highlight stayed). */
  @GameTest(template = EMPTY)
  public static void robotSelectedSlotIsClamped(final GameTestHelper helper) {
    final Robot robot = placeRobot(helper, List.of(), List.of(Constants.ItemName.InventoryUpgrade));
    robot.setSelectedSlot(99);
    helper.assertTrue(robot.selectedSlot() == 15, "slot 100 of 16 selected as " + (robot.selectedSlot() + 1));
    helper.succeed();
  }
}
