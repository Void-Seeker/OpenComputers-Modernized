package li.cil.oc.neoforge.gametest;

import java.util.List;
import li.cil.oc.core.Constants;
import li.cil.oc.neoforge.OpenComputers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * The redstone card.
 */
@GameTestHolder(OpenComputers.ID)
@PrefixGameTestTemplate(false)
public final class RedstoneTests {
  private static final String EMPTY = "empty";

  private RedstoneTests() {
  }

  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void comparatorInputReadsContainersAndBlocks(final GameTestHelper helper) {
    final BlockPos pos = new BlockPos(2, 2, 2);
    // a hopper with one full stack of its five slots reads 3; it points sideways so it leaves the microcontroller alone
    helper.setBlock(pos.above(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.NORTH));
    ((Container) helper.getBlockEntity(pos.above())).setItem(0, new ItemStack(Items.STONE, 64));
    helper.setBlock(pos.below(), Blocks.COMPOSTER.defaultBlockState().setValue(ComposterBlock.LEVEL, 8));
    // up and down are the same for the card's facing-relative sides and the world
    TestMachines.runMicrocontroller(helper, pos, List.of(Constants.ItemName.CPUTier1, Constants.ItemName.RAMTier2, Constants.ItemName.RedstoneCardTier1),
      "local rs = component.proxy(component.list(\"redstone\")())\n"
        + "local hopper, composter = rs.getComparatorInput(1), rs.getComparatorInput(0)\n"
        + "if hopper ~= 3 or composter ~= 8 then error(\"hopper reads \" .. tostring(hopper) .. \", composter \" .. tostring(composter), 0) end\n"
        + "while true do computer.pullSignal(1) end\n",
      mc -> helper.succeed());
  }

  @GameTest(template = EMPTY)
  public static void redstoneIoReadsComparatorInput(final GameTestHelper helper) {
    final BlockPos pos = new BlockPos(2, 2, 2);
    helper.setBlock(pos.above(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.NORTH));
    ((Container) helper.getBlockEntity(pos.above())).setItem(0, new ItemStack(Items.STONE, 64));
    helper.setBlock(pos.below(), Blocks.COMPOSTER.defaultBlockState().setValue(ComposterBlock.LEVEL, 8));
    helper.setBlock(pos, li.cil.oc.neoforge.common.init.Blocks.REDSTONE.get());
    final var io = (li.cil.oc.core.impl.common.blockentity.Redstone) helper.getBlockEntity(pos);
    final var card = (li.cil.oc.core.impl.server.component.RedstoneVanilla) io.instance;
    final Object hopper = card.getComparatorInput(null, new li.cil.oc.core.impl.server.machine.ArgumentsImpl(new Object[]{1.0}))[0];
    final Object composter = card.getComparatorInput(null, new li.cil.oc.core.impl.server.machine.ArgumentsImpl(new Object[]{0.0}))[0];
    helper.assertTrue(((Number) hopper).intValue() == 3 && ((Number) composter).intValue() == 8, "hopper reads " + hopper + ", composter " + composter);
    helper.succeed();
  }
}
