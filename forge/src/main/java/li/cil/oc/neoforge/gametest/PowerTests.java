package li.cil.oc.neoforge.gametest;

import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import li.cil.oc.core.Constants;
import li.cil.oc.core.impl.OCSettings;
import li.cil.oc.core.impl.server.machine.ArgumentsImpl;
import li.cil.oc.neoforge.OpenComputers;
import li.cil.oc.neoforge.common.init.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Generators keep their fuel exactly once, waypoints are found within the whole range, and sensors and capacitors
 * keep and report what they are.
 */
@GameTestHolder(OpenComputers.ID)
@PrefixGameTestTemplate(false)
public final class PowerTests {
  private static final String EMPTY = "empty";
  private static final BlockPos AT = new BlockPos(2, 2, 2);

  private PowerTests() {
  }

  private static li.cil.oc.api.machine.Context context() {
    return (li.cil.oc.api.machine.Context) java.lang.reflect.Proxy.newProxyInstance(li.cil.oc.api.machine.Context.class.getClassLoader(),
      new Class<?>[]{li.cil.oc.api.machine.Context.class}, (proxy, method, args) -> method.getReturnType() == boolean.class ? false : null);
  }

  private static int coalIn(final ItemStack stack) {
    if (stack.is(Items.COAL)) return stack.getCount();
    int n = 0;
    if (stack.getTag() != null && li.cil.oc.api.Items.get(stack) == li.cil.oc.api.Items.get(Constants.BlockName.Robot)) {
      for (final ItemStack part : new li.cil.oc.core.impl.common.item.data.RobotData(stack).components) {
        final CompoundTag fuel = li.cil.oc.core.impl.integration.opencomputers.Item.peekDataTag(part).getCompound("inventory");
        if (!fuel.isEmpty()) n += ItemStack.of(fuel).is(Items.COAL) ? ItemStack.of(fuel).getCount() : 0;
      }
    }
    return n;
  }

  /** A robot broken with fuel in its generator gives that fuel back once (dropped and kept in the upgrade: twice). */
  @GameTest(template = EMPTY, timeoutTicks = 300)
  public static void generatorFuelComesBackOnce(final GameTestHelper helper) {
    TestMachines.runRobot(helper, AT, List.of(Constants.ItemName.GeneratorUpgrade, Constants.ItemName.InventoryUpgrade),
      robot -> robot.mainInventory().setItem(0, new ItemStack(Items.COAL, 8)),
      "component.proxy(component.list('robot')()).select(1)\nlocal g = component.proxy(component.list('generator')())\nlocal ok, why = g.insert(4)\nerror('done:' .. tostring(ok) .. ' ' .. tostring(g.count()))\n",
      40, (robot, result) -> {
        helper.assertTrue(result != null && result.startsWith("true "), "the generator took no fuel: " + result);
        // what it holds right now (it begins to burn one whenever it likes)
        int held = -1;
        for (final var env : robot.componentEnvironments()) {
          if (env instanceof li.cil.oc.core.impl.server.component.UpgradeGenerator generator) {
            held = ((Number) generator.count(context(), new ArgumentsImpl(new Object[0]))[0]).intValue();
          }
        }
        final int inGenerator = held;
        helper.assertTrue(inGenerator > 0, "the generator holds no fuel: " + result);
        int before = inGenerator;
        for (int i = 0; i < robot.mainInventory().getContainerSize(); i++) before += coalIn(robot.mainInventory().getItem(i));
        final BlockPos abs = helper.absolutePos(AT);
        final AABB box = new AABB(abs).inflate(3);
        helper.getLevel().getEntitiesOfClass(ItemEntity.class, box).forEach(net.minecraft.world.entity.Entity::discard);
        final var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "oc-breaker"));
        player.setGameMode(GameType.SURVIVAL);
        player.gameMode.destroyBlock(abs);
        int after = 0;
        for (final ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box)) after += coalIn(e.getItem());
        helper.assertTrue(after == before, "a robot with " + before + " coal (" + inGenerator + " of them in its generator) gave back " + after);
        helper.succeed();
      });
  }

  private static int generatorFuel(final li.cil.oc.core.impl.common.entity.Drone drone) {
    for (final var env : drone.components.componentEnvironments()) {
      if (env instanceof li.cil.oc.core.impl.server.component.UpgradeGenerator generator) {
        return ((Number) generator.count(context(), new ArgumentsImpl(new Object[0]))[0]).intValue();
      }
    }
    return -1;
  }

  /**
   * A drone through a portal keeps its generator's fuel once (it was dropped where it left and kept by its copy; and
   * dropping it while the drone's chunk unloaded - here, the End where it arrived - crashed the server: an entity was
   * added to the section being unloaded).
   */
  @GameTest(template = EMPTY, timeoutTicks = 300)
  public static void droneGeneratorFuelIsNotDuplicatedByPortals(final GameTestHelper helper) {
    TestMachines.runDrone(helper, AT, List.of(Constants.ItemName.GeneratorUpgrade, Constants.ItemName.InventoryUpgrade),
      drone -> drone.mainInventory().setItem(0, new ItemStack(Items.COAL, 8)),
      "local d = component.proxy(component.list('drone')())\nd.select(1)\nlocal g = component.proxy(component.list('generator')())\ng.insert(4)\nwhile true do computer.pullSignal(1) end\n",
      40, (drone, result) -> {
        final int inGenerator = generatorFuel(drone);
        helper.assertTrue(inGenerator > 0, "the drone's generator holds " + inGenerator + " fuel");
        int before = inGenerator;
        for (int i = 0; i < drone.mainInventory().getContainerSize(); i++) before += coalIn(drone.mainInventory().getItem(i));
        final int expected = before;
        final AABB box = new AABB(drone.blockPosition()).inflate(4);
        helper.getLevel().getEntitiesOfClass(ItemEntity.class, box).forEach(net.minecraft.world.entity.Entity::discard);
        final var end = helper.getLevel().getServer().getLevel(net.minecraft.world.level.Level.END);
        final var moved = drone.changeDimension(end);
        helper.assertTrue(moved instanceof li.cil.oc.core.impl.common.entity.Drone, "the drone did not reach the End: " + moved);
        final var there = (li.cil.oc.core.impl.common.entity.Drone) moved;
        helper.runAfterDelay(5, () -> {
          int after = 0;
          for (final ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box)) after += coalIn(e.getItem());
          for (int i = 0; i < there.mainInventory().getContainerSize(); i++) after += coalIn(there.mainInventory().getItem(i));
          final int fuelThere = generatorFuel(there);
          after += Math.max(fuelThere, 0);
          there.discard();
          helper.assertTrue(after == expected, "a drone with " + expected + " coal (" + inGenerator + " in its generator) has " + after + " after a portal (" + fuelThere + " in the generator there)");
          helper.succeed();
        });
      });
  }

  /** Waypoints are found up to the range asked (the search box reached only half of it). */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void waypointsAreFoundWithinTheWholeRange(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.WAYPOINT.get());
    final BlockPos abs = helper.absolutePos(AT);
    helper.runAfterDelay(5, () -> {
      final var from = li.cil.oc.core.impl.util.BlockPosition.apply(abs.getX() + 12, abs.getY(), abs.getZ(), helper.getLevel());
      int found = 0;
      for (final var ignored : li.cil.oc.core.impl.server.network.WaypointManager.findWaypoints(from, 16)) found++;
      helper.assertTrue(found >= 1, "a waypoint 12 blocks away was not found within 16");
      helper.succeed();
    });
  }

  /**
   * Setting a motion sensor's sensitivity marks the sensor to be saved (it never did: it reverted after a restart).
   * The block entity's own mark is checked: other tests' blocks mark a shared chunk too.
   */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void motionSensorSensitivityIsSaved(final GameTestHelper helper) throws Exception {
    helper.setBlock(AT, Blocks.MOTION_SENSOR.get());
    final var sensor = (li.cil.oc.core.impl.common.blockentity.MotionSensor) helper.getBlockEntity(AT);
    final var mark = li.cil.oc.core.impl.common.blockentity.traits.BlockEntity.class.getDeclaredField("changeScheduled");
    mark.setAccessible(true);
    helper.runAfterDelay(3, () -> {
      try {
        ((java.util.concurrent.atomic.AtomicBoolean) mark.get(sensor)).set(false);
        sensor.motionSensor.setSensitivity(context(), new ArgumentsImpl(new Object[]{2.0}));
        helper.assertTrue(((java.util.concurrent.atomic.AtomicBoolean) mark.get(sensor)).get(), "setting the sensitivity did not mark the sensor to be saved");
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
      helper.succeed();
    });
  }

  /** A capacitor reports its whole capacity, the base buffer included (it counted only the bonuses). */
  @GameTest(template = EMPTY)
  public static void capacitorReportsItsWholeCapacity(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.CAPACITOR.get());
    final var capacitor = (li.cil.oc.core.impl.common.blockentity.Capacitor) helper.getBlockEntity(AT);
    final String capacity = capacitor.getDeviceInfo().get(li.cil.oc.api.driver.DeviceInfo.DeviceAttribute.Capacity);
    final double expected = OCSettings.get().bufferCapacitor + OCSettings.get().bufferCapacitorAdjacencyBonus * 9;
    helper.assertTrue(capacity != null && Math.abs(Double.parseDouble(capacity) - expected) < 1e-6, "a capacitor reports a capacity of " + capacity + ", expected " + expected);
    helper.succeed();
  }
}
