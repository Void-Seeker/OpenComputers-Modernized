package li.cil.oc.neoforge.gametest;

import li.cil.oc.core.Constants;
import li.cil.oc.core.impl.common.blockentity.Hologram;
import li.cil.oc.core.impl.common.blockentity.Print;
import li.cil.oc.core.impl.common.blockentity.Printer;
import li.cil.oc.core.impl.common.item.data.PrintData;
import li.cil.oc.core.impl.server.machine.ArgumentsImpl;
import li.cil.oc.neoforge.OpenComputers;
import li.cil.oc.neoforge.common.init.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Printers, prints, holograms and disk drives keep what they make, hold and are told, through work, reloads and
 * breaking.
 */
@GameTestHolder(OpenComputers.ID)
@PrefixGameTestTemplate(false)
public final class MachineryTests {
  private static final String EMPTY = "empty";
  private static final BlockPos AT = new BlockPos(2, 2, 2);

  private MachineryTests() {
  }

  private static ItemStack item(final String name, final int count) {
    final ItemStack stack = li.cil.oc.api.Items.get(name).createItemStack(1);
    stack.setCount(count);
    return stack;
  }

  private static PrintData cube() {
    final PrintData data = new PrintData();
    data.stateOff.add(new PrintData.Shape(new AABB(0, 0, 0, 1, 1, 1), "minecraft:block/stone", null));
    return data;
  }

  private static li.cil.oc.api.machine.Context context() {
    return (li.cil.oc.api.machine.Context) java.lang.reflect.Proxy.newProxyInstance(li.cil.oc.api.machine.Context.class.getClassLoader(),
      new Class<?>[]{li.cil.oc.api.machine.Context.class}, (proxy, method, args) -> method.getReturnType() == boolean.class ? false : null);
  }

  private static int loose(final GameTestHelper helper, final String itemName) {
    final var item = li.cil.oc.api.Items.get(itemName).item();
    int n = 0;
    for (final ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(6, 6, 6))).inflate(2))) {
      if (e.getItem().is(item)) n += e.getItem().getCount();
    }
    return n;
  }

  /** A printer whose output slot is full waits (it printed on and threw the prints away, chamelium and ink spent). */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void printerWaitsForRoomInItsOutput(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.PRINTER.get());
    final Printer printer = (Printer) helper.getBlockEntity(AT);
    printer.data = cube();
    final ItemStack full = printer.data.createItemStack();
    full.setCount(full.getMaxStackSize());
    printer.setItem(Printer.SLOT_OUTPUT, full);
    printer.amountMaterial = 10000;
    printer.amountInk = 10000;
    printer.limit = 3;
    printer.isActive = true;
    TestMachines.later(helper, 40, () -> {
      helper.assertTrue(printer.amountMaterial == 10000 && printer.amountInk == 10000 && printer.getItem(Printer.SLOT_OUTPUT).getCount() == full.getMaxStackSize(),
        "with a full output the printer used material " + (10000 - printer.amountMaterial) + ", ink " + (10000 - printer.amountInk));
      helper.succeed();
    });
  }

  /** A stack of ink cartridges: one is used, the rest stay, its empty one drops (one empty cartridge replaced them all). */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void printerKeepsTheRestOfAnInkStack(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.PRINTER.get());
    final Printer printer = (Printer) helper.getBlockEntity(AT);
    printer.setItem(Printer.SLOT_INK, item(Constants.ItemName.InkCartridge, 16));
    printer.amountInk = 0;
    TestMachines.later(helper, 5, () -> {
      final ItemStack ink = printer.getItem(Printer.SLOT_INK);
      final int empties = loose(helper, Constants.ItemName.InkCartridgeEmpty);
      helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ink.getItem()).getPath().equals("inkcartridge")
          && empties >= 1 && ink.getCount() + empties == 16,
        "after one cartridge the ink slot holds " + ink + ", " + loose(helper, Constants.ItemName.InkCartridgeEmpty) + " empty cartridges dropped");
      helper.succeed();
    });
  }

  /** A beacon base print stays one through a reload (it came back, and dropped, as a plain print). */
  @GameTest(template = EMPTY)
  public static void beaconBasePrintSurvivesAReload(final GameTestHelper helper) {
    final PrintData data = cube();
    data.isBeaconBase = true;
    final ItemStack stack = li.cil.oc.api.Items.get(Constants.BlockName.BeaconBasePrint).createItemStack(1);
    data.save(stack);
    helper.setBlock(AT, Blocks.BEACON_BASE_PRINT.get());
    final BlockPos abs = helper.absolutePos(AT);
    Blocks.BEACON_BASE_PRINT.get().setPlacedBy(helper.getLevel(), abs, helper.getBlockState(AT), FakePlayerFactory.getMinecraft(helper.getLevel()), stack);
    final var tag = helper.getBlockEntity(AT).saveWithFullMetadata();
    final BlockEntity loaded = BlockEntity.loadStatic(abs, helper.getBlockState(AT), tag);
    helper.assertTrue(loaded instanceof Print, "not a print: " + loaded);
    final ItemStack drop = ((Print) loaded).data.createItemStack();
    helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(drop.getItem()).getPath().equals("beaconbaseprint"), "a reloaded beacon base print drops as " + drop);
    helper.succeed();
  }

  /** setCollidable(false, ...) makes the print walk-through (it was paid for and ignored). */
  @GameTest(template = EMPTY)
  public static void printSetNotCollidableHasNoCollision(final GameTestHelper helper) {
    final PrintData data = cube();
    data.noclipOff = true;
    final ItemStack stack = data.createItemStack();
    helper.setBlock(AT, Blocks.PRINT.get());
    final BlockPos abs = helper.absolutePos(AT);
    Blocks.PRINT.get().setPlacedBy(helper.getLevel(), abs, helper.getBlockState(AT), FakePlayerFactory.getMinecraft(helper.getLevel()), stack);
    final var collision = helper.getBlockState(AT).getCollisionShape(helper.getLevel(), abs);
    helper.assertTrue(collision.isEmpty(), "a print set not to collide has the collision shape " + collision);
    helper.succeed();
  }

  /** A tier 2 hologram is one after a reload (it came back as tier 1: colours 2 and 3 gone, set() refusing them). */
  @GameTest(template = EMPTY)
  public static void tier2HologramSurvivesAReload(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.HOLOGRAM_TIER_2.get());
    final BlockPos abs = helper.absolutePos(AT);
    final var tag = helper.getBlockEntity(AT).saveWithFullMetadata();
    final BlockEntity loaded = BlockEntity.loadStatic(abs, helper.getBlockState(AT), tag);
    helper.assertTrue(loaded instanceof Hologram h && h.colors.length == 3, "a reloaded tier 2 hologram has " + (loaded instanceof Hologram h ? h.colors.length : -1) + " colours");
    helper.succeed();
  }

  /** hologram.copy to a target entirely off the edge changes nothing (it filled column 1 from outside the area). */
  @GameTest(template = EMPTY)
  public static void hologramCopyOffTheEdgeChangesNothing(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.HOLOGRAM_TIER_1.get());
    final Hologram hologram = (Hologram) helper.getBlockEntity(AT);
    final var ctx = context();
    hologram.set(ctx, new ArgumentsImpl(new Object[]{11, 1, 1, 1}));
    hologram.copy(ctx, new ArgumentsImpl(new Object[]{1, 1, 5, 1, -10, 0}));
    final Object[] at1 = hologram.get(ctx, new ArgumentsImpl(new Object[]{1, 1, 1}));
    helper.assertTrue(at1 != null && ((Number) at1[0]).intValue() == 0, "copying columns 1-5 by -10 set column 1 to " + (at1 == null ? null : at1[0]));
    helper.succeed();
  }

  /** A floppy dropped from a broken disk drive keeps the label set on it (it dropped a copy taken before saving it). */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void diskDriveDropKeepsTheFloppysLabel(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.DISK_DRIVE.get());
    final var drive = (li.cil.oc.core.impl.common.blockentity.DiskDrive) helper.getBlockEntity(AT);
    drive.setItem(0, item(Constants.ItemName.Floppy, 1));
    TestMachines.later(helper, 5, () -> {
      li.cil.oc.core.impl.server.component.FileSystem fs = null;
      for (final var env : drive.componentEnvironments()) {
        if (env instanceof li.cil.oc.core.impl.server.component.FileSystem f) fs = f;
      }
      helper.assertTrue(fs != null && fs.label != null, "no floppy file system in the drive");
      fs.label.setLabel("keepme");
      final var player = FakePlayerFactory.getMinecraft(helper.getLevel());
      player.setGameMode(GameType.SURVIVAL);
      player.gameMode.destroyBlock(helper.absolutePos(AT));
      final var floppy = li.cil.oc.api.Items.get(Constants.ItemName.Floppy).item();
      final var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(AT)).inflate(3)).stream()
        .map(ItemEntity::getItem).filter(s -> s.is(floppy)).toList();
      helper.assertTrue(drops.size() == 1 && String.valueOf(drops.get(0).getTag()).contains("keepme"),
        "the dropped floppy " + (drops.isEmpty() ? "is missing" : "has the tag " + drops.get(0).getTag()));
      helper.succeed();
    });
  }
}
