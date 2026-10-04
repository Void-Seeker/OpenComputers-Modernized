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

  private static String networkOf(final li.cil.oc.core.impl.common.blockentity.Microcontroller mc) {
    final var envs = mc.componentEnvironments();
    final StringBuilder sb = new StringBuilder("environments [");
    for (int k = 0; k < envs.length; k++) sb.append(k).append('=').append(envs[k] == null ? "null" : envs[k].getClass().getSimpleName()).append(' ');
    sb.append("] machine network [");
    final var network = mc.machine().node().network();
    if (network != null) for (var n : network.nodes()) sb.append(n instanceof li.cil.oc.api.network.Component c ? c.name() : n.getClass().getSimpleName()).append(' ');
    return sb.append(']').toString();
  }

  private static boolean machineSees(final li.cil.oc.core.impl.common.blockentity.Microcontroller mc, final String componentName) {
    final var network = mc.machine().node().network();
    return network != null && java.util.stream.StreamSupport.stream(network.nodes().spliterator(), false)
      .anyMatch(n -> n instanceof li.cil.oc.api.network.Component c && componentName.equals(c.name()));
  }

  /**
   * Swapping an EEPROM into a placed microcontroller (sneak + right-click) connects it to the machine, so the BIOS
   * finds it, and keeps the machine connected to its other parts.
   */
  @GameTest(template = EMPTY)
  public static void microcontrollerSeesSwappedEeprom(final GameTestHelper helper) {
    final var data = new MicrocontrollerData();
    for (final String part : List.of(Constants.ItemName.CPUTier1, Constants.ItemName.RAMTier1, Constants.ItemName.RedstoneCardTier1)) {
      data.components.add(item(part));
    }
    data.components.add(ItemStack.EMPTY);  // the assembler leaves the EEPROM slot empty when none was inserted
    final BlockPos pos = new BlockPos(2, 3, 2);
    helper.setBlock(pos.below(), Blocks.CABLE.get());
    helper.setBlock(pos, Blocks.MICROCONTROLLER.get());
    final BlockPos abs = helper.absolutePos(pos);
    Blocks.MICROCONTROLLER.get().setPlacedBy(helper.getLevel(), abs, helper.getLevel().getBlockState(abs), player(helper), data.createItemStack());
    helper.runAfterDelay(5, () -> {
      final var mc = (li.cil.oc.core.impl.common.blockentity.Microcontroller) helper.getLevel().getBlockEntity(abs);
      final String before = networkOf(mc);
      helper.assertTrue(machineSees(mc, "redstone"), "parts not connected even before the swap: " + before);
      mc.changeEEPROM(item(Constants.ItemName.EEPROM));
      helper.runAfterDelay(2, () -> {
        final String after = networkOf(mc);
        helper.assertTrue(machineSees(mc, "eeprom") && machineSees(mc, "redstone"),
          "after the swap the machine should see the EEPROM and still its redstone card. Before: " + before + " | after: " + after);
        helper.succeed();
      });
    });
  }

  /**
   * The full brew setup in miniature: a microcontroller boots from a swapped-in EEPROM, and the program's redstone
   * card powers the block above it (where the dropper sits).
   */
  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void microcontrollerRunsEepromAndPowersRedstone(final GameTestHelper helper) {
    final var data = new MicrocontrollerData();
    for (final String part : List.of(Constants.ItemName.CPUTier1, Constants.ItemName.RAMTier2, Constants.ItemName.RedstoneCardTier1)) {
      data.components.add(item(part));
    }
    data.components.add(ItemStack.EMPTY);
    final BlockPos pos = new BlockPos(2, 2, 2);
    helper.setBlock(pos, Blocks.MICROCONTROLLER.get());
    final BlockPos abs = helper.absolutePos(pos);
    Blocks.MICROCONTROLLER.get().setPlacedBy(helper.getLevel(), abs, helper.getLevel().getBlockState(abs), player(helper), data.createItemStack());
    final byte[] code = ("local rs = component.proxy(component.list(\"redstone\")())\n"
      + "for s = 0, 5 do rs.setOutput(s, 15) end\n"
      + "while true do computer.pullSignal(1) end\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
    helper.runAfterDelay(5, () -> {
      final var mc = (li.cil.oc.core.impl.common.blockentity.Microcontroller) helper.getLevel().getBlockEntity(abs);
      mc.changeEEPROM(li.cil.oc.api.Items.registerEEPROM("redstone test", code, null, false));
      ((li.cil.oc.api.network.Connector) mc.snooperNode).changeBuffer(1000);
      helper.assertTrue(mc.machine().start(), "machine did not start");
      helper.runAfterDelay(60, () -> {
        final int signal = helper.getLevel().getBestNeighborSignal(abs.above());
        helper.assertTrue(signal == 15, "block above the microcontroller receives redstone " + signal + " instead of 15; machine running: "
          + mc.machine().isRunning() + ", last error: " + mc.machine().lastError() + "; " + networkOf(mc));
        helper.succeed();
      });
    });
  }
}
