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
