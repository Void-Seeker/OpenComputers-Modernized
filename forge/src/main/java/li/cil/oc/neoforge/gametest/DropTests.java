package li.cil.oc.neoforge.gametest;

import com.mojang.authlib.GameProfile;
import li.cil.oc.core.Constants;
import li.cil.oc.core.impl.common.item.data.MicrocontrollerData;
import li.cil.oc.core.impl.common.item.data.RaidData;
import li.cil.oc.core.impl.common.item.data.RobotData;
import li.cil.oc.neoforge.OpenComputers;
import li.cil.oc.neoforge.common.init.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * What OC blocks drop when a player in survival breaks them.
 */
@GameTestHolder(OpenComputers.ID)
@PrefixGameTestTemplate(false)
public final class DropTests {
  private static final String EMPTY = "empty";
  private static final GameProfile BREAKER = new GameProfile(UUID.fromString("6e0c2a6e-3f2b-4c39-9a7e-1d2f3b4c5d6e"), "oc-test-breaker");

  private DropTests() {
  }

  private static FakePlayer player(final GameTestHelper helper) {
    final FakePlayer player = FakePlayerFactory.get(helper.getLevel(), BREAKER);
    player.setGameMode(GameType.SURVIVAL);
    return player;
  }

  private static ItemStack item(final String name) {
    return li.cil.oc.api.Items.get(name).createItemStack(1);
  }

  private static List<ItemStack> breakAndCollect(final GameTestHelper helper, final BlockPos pos) {
    final BlockPos abs = helper.absolutePos(pos);
    final AABB box = new AABB(abs).inflate(3);
    helper.getLevel().getEntitiesOfClass(ItemEntity.class, box).forEach(Entity::discard);
    player(helper).gameMode.destroyBlock(abs);
    helper.assertTrue(helper.getLevel().getBlockState(abs).isAir(), "block was not broken");
    final List<ItemStack> drops = new ArrayList<>();
    for (final ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box)) {
      drops.add(entity.getItem().copy());
      entity.discard();
    }
    return drops;
  }

  private static String describe(final List<ItemStack> drops) {
    return drops.stream().map(s -> s.getCount() + "x " + BuiltInRegistries.ITEM.getKey(s.getItem())).collect(Collectors.joining(", ", "[", "]"));
  }

  private static boolean isOC(final ItemStack stack, final String name) {
    final var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
    return key.getNamespace().equals(OpenComputers.ID) && key.getPath().equalsIgnoreCase(name);
  }

  private static ItemStack only(final GameTestHelper helper, final List<ItemStack> drops, final String name) {
    helper.assertTrue(drops.size() == 1 && isOC(drops.get(0), name),
      "expected exactly one " + name + ", dropped " + describe(drops));
    return drops.get(0);
  }

  @GameTest(template = EMPTY)
  public static void cableDropsItself(final GameTestHelper helper) {
    final BlockPos pos = new BlockPos(2, 2, 2);
    helper.setBlock(pos, Blocks.CABLE.get());
    only(helper, breakAndCollect(helper, pos), Constants.BlockName.Cable);
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void printDropsItself(final GameTestHelper helper) {
    final BlockPos pos = new BlockPos(2, 2, 2);
    helper.setBlock(pos, Blocks.PRINT.get());
    only(helper, breakAndCollect(helper, pos), Constants.BlockName.Print);
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void microcontrollerDropsAssembled(final GameTestHelper helper) {
    final var data = new MicrocontrollerData();
    for (final String part : List.of(Constants.ItemName.CPUTier1, Constants.ItemName.RAMTier1, Constants.ItemName.EEPROM, Constants.ItemName.RedstoneCardTier1)) {
      data.components.add(item(part));
    }
    final BlockPos pos = new BlockPos(2, 2, 2);
    helper.setBlock(pos, Blocks.MICROCONTROLLER.get());
    final BlockPos abs = helper.absolutePos(pos);
    Blocks.MICROCONTROLLER.get().setPlacedBy(helper.getLevel(), abs, helper.getLevel().getBlockState(abs), player(helper), data.createItemStack());
    final ItemStack dropped = only(helper, breakAndCollect(helper, pos), Constants.BlockName.Microcontroller);
    final int parts = new MicrocontrollerData(dropped).components.size();
    helper.assertTrue(parts == 4, "dropped microcontroller holds " + parts + " parts instead of 4");
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void raidDropsWithItsDisks(final GameTestHelper helper) {
    final BlockPos pos = new BlockPos(2, 2, 2);
    helper.setBlock(pos, Blocks.RAID.get());
    final var raid = (li.cil.oc.core.impl.common.blockentity.Raid) helper.getBlockEntity(pos);
    raid.setItem(0, item(Constants.ItemName.HDDTier1));
    final ItemStack dropped = only(helper, breakAndCollect(helper, pos), Constants.BlockName.Raid);
    final long disks = new RaidData(dropped).disks.stream().filter(s -> s != null && !s.isEmpty()).count();
    helper.assertTrue(disks == 1, "dropped RAID holds " + disks + " disks instead of 1");
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void robotDropsAssembledAndItsInventory(final GameTestHelper helper) {
    final var data = new RobotData();
    data.name = "test";
    data.components.add(item(Constants.ItemName.InventoryUpgrade));
    final BlockPos pos = new BlockPos(2, 2, 2);
    helper.setBlock(pos, Blocks.ROBOT.get());
    final BlockPos abs = helper.absolutePos(pos);
    Blocks.ROBOT.get().setPlacedBy(helper.getLevel(), abs, helper.getLevel().getBlockState(abs), player(helper), data.createItemStack());
    final var proxy = (li.cil.oc.core.impl.common.blockentity.RobotProxy) helper.getLevel().getBlockEntity(abs);
    proxy.robot.mainInventory().setItem(0, new ItemStack(net.minecraft.world.item.Items.DIAMOND, 5));

    final List<ItemStack> drops = breakAndCollect(helper, pos);
    final var robots = drops.stream().filter(s -> isOC(s, Constants.BlockName.Robot)).toList();
    final int diamonds = drops.stream().filter(s -> s.is(net.minecraft.world.item.Items.DIAMOND)).mapToInt(ItemStack::getCount).sum();
    helper.assertTrue(robots.size() == 1 && diamonds == 5 && drops.size() == robots.size() + drops.stream().filter(s -> s.is(net.minecraft.world.item.Items.DIAMOND)).count(),
      "expected the robot and its 5 diamonds, dropped " + describe(drops));
    final int parts = new RobotData(robots.get(0)).components.size();
    helper.assertTrue(parts == 1, "dropped robot holds " + parts + " parts instead of 1");
    helper.succeed();
  }
}
