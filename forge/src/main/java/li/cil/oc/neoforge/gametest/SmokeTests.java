package li.cil.oc.neoforge.gametest;

import li.cil.oc.compat.CustomData;
import li.cil.oc.neoforge.OpenComputers;
import li.cil.oc.neoforge.common.capability.OCBlockCapabilities;
import li.cil.oc.neoforge.common.init.Blocks;
import li.cil.oc.neoforge.common.init.Items;
import li.cil.oc.neoforge.compat.Capabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Smoke tests for the 1.20.1 backport. Run with {@code ./gradlew :forge:runGameTestServer}.
 */
@GameTestHolder(OpenComputers.ID)
@PrefixGameTestTemplate(false)
public final class SmokeTests {
  private static final String EMPTY = "empty";

  private SmokeTests() {
  }

  @GameTest(template = EMPTY)
  public static void recipesLoad(final GameTestHelper helper) {
    final long count = helper.getLevel().getRecipeManager().getRecipes().stream()
      .filter(recipe -> recipe.getId().getNamespace().equals(OpenComputers.ID))
      .count();
    helper.assertTrue(count > 150, "expected OC recipes to load, got " + count);
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void caseCapabilities(final GameTestHelper helper) {
    final BlockPos pos = new BlockPos(2, 2, 2);
    helper.setBlock(pos, Blocks.CASE_TIER_1.get());
    helper.assertTrue(helper.getBlockEntity(pos) != null, "case has no block entity");
    final BlockPos absolute = helper.absolutePos(pos);
    helper.assertTrue(Capabilities.ItemHandler.BLOCK.getCapability(helper.getLevel(), absolute, Direction.UP) != null,
      "case exposes no item handler");
    helper.assertTrue(OCBlockCapabilities.ENVIRONMENT.getCapability(helper.getLevel(), absolute, null) != null,
      "case exposes no environment");
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void chargeableItemEnergy(final GameTestHelper helper) {
    final ItemStack tablet = new ItemStack(Items.TABLET.get());
    helper.assertTrue(Capabilities.EnergyStorage.ITEM.getCapability(tablet) != null, "tablet exposes no energy storage");
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void customDataKeepsVanillaKeys(final GameTestHelper helper) {
    final ItemStack stack = new ItemStack(Items.FLOPPY.get());
    stack.setHoverName(Component.literal("named"));
    final CompoundTag data = new CompoundTag();
    data.putString("oc:test", "value");
    CustomData.set(stack, CustomData.of(data));
    helper.assertTrue(stack.hasCustomHoverName() && stack.getHoverName().getString().equals("named"),
      "custom data write clobbered the stack name");
    final CustomData read = CustomData.get(stack);
    helper.assertTrue(read != null && read.contains("oc:test") && !read.contains("display"),
      "custom data read did not round trip: " + read);
    helper.succeed();
  }

  /**
   * Items in OC block inventories survive a save and reload. Several block entities never saved them,
   * because the Java Inventory interface cannot hook into the base class's NBT methods like the Scala traits did.
   */
  @GameTest(template = EMPTY)
  public static void blockInventoriesPersist(final GameTestHelper helper) {
    final java.util.List<net.minecraftforge.registries.RegistryObject<? extends net.minecraft.world.level.block.Block>> blocks = java.util.List.of(
      Blocks.DISK_DRIVE, Blocks.ASSEMBLER, Blocks.ADAPTER, Blocks.DISASSEMBLER, Blocks.PRINTER, Blocks.RELAY, Blocks.CHARGER, Blocks.CASE_TIER_1);
    final StringBuilder failures = new StringBuilder();
    for (int i = 0; i < blocks.size(); i++) {
      final BlockPos pos = new BlockPos(1 + i, 2, 1);
      helper.setBlock(pos, blocks.get(i).get());
      final var be = helper.getBlockEntity(pos);
      final var name = blocks.get(i).getId().getPath();
      if (!(be instanceof li.cil.oc.core.impl.common.inventory.Inventory inventory)) {
        failures.append(' ').append(name).append(" (no inventory)");
        continue;
      }
      inventory.updateItems(0, new ItemStack(net.minecraft.world.item.Items.DIAMOND));
      final var tag = be.saveWithFullMetadata();
      final var fresh = be.getType().create(helper.absolutePos(pos), be.getBlockState());
      fresh.setLevel(helper.getLevel());
      fresh.load(tag);
      final var items = ((li.cil.oc.core.impl.common.inventory.Inventory) fresh).items();
      if (items.length == 0 || items[0] == null || !items[0].is(net.minecraft.world.item.Items.DIAMOND)) {
        failures.append(' ').append(name);
      }
    }
    helper.assertTrue(failures.length() == 0, "inventory lost on reload:" + failures);
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void powerConverterAcceptsForgeEnergy(final GameTestHelper helper) {
    final BlockPos pos = new BlockPos(2, 2, 2);
    helper.setBlock(pos, Blocks.POWER_CONVERTER.get());
    final var storage = Capabilities.EnergyStorage.BLOCK.getCapability(helper.getLevel(), helper.absolutePos(pos), Direction.NORTH);
    helper.assertTrue(storage != null, "power converter exposes no Forge Energy storage");
    helper.assertTrue(storage.canReceive(), "power converter does not accept Forge Energy");
    // The block entity needs a tick to join OC's network before it has a buffer to fill.
    helper.runAfterDelay(5, () -> {
      final var later = Capabilities.EnergyStorage.BLOCK.getCapability(helper.getLevel(), helper.absolutePos(pos), Direction.NORTH);
      helper.assertTrue(later != null && later.receiveEnergy(1000, true) > 0, "power converter accepted no Forge Energy");
      helper.succeed();
    });
  }
}
