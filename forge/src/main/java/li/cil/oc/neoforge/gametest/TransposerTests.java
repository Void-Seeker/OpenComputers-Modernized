package li.cil.oc.neoforge.gametest;

import java.util.List;
import li.cil.oc.core.Constants;
import li.cil.oc.core.impl.util.BlockPosition;
import li.cil.oc.core.impl.util.FluidUtils;
import li.cil.oc.neoforge.OpenComputers;
import li.cil.oc.neoforge.util.FluidUtilsOriginal;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Transposer fluid moves with vanilla blocks, and its right-click.
 */
@GameTestHolder(OpenComputers.ID)
@PrefixGameTestTemplate(false)
public final class TransposerTests {
  private static final String EMPTY = "empty";

  private TransposerTests() {
  }

  private static int transfer(final GameTestHelper helper, final BlockPos from, final BlockPos to) {
    final BlockPos a = helper.absolutePos(from);
    final BlockPos b = helper.absolutePos(to);
    return FluidUtils.transferBetweenFluidHandlersAt(
      new BlockPosition(a.getX(), a.getY(), a.getZ(), helper.getLevel()), null,
      new BlockPosition(b.getX(), b.getY(), b.getZ(), helper.getLevel()), null, Integer.MAX_VALUE, -1);
  }

  private static BlockState state(final GameTestHelper helper, final BlockPos pos) {
    return helper.getLevel().getBlockState(helper.absolutePos(pos));
  }

  @GameTest(template = EMPTY)
  public static void fluidsMoveWithVanillaCauldronsAndSourceBlocks(final GameTestHelper helper) {
    final BlockPos lava = new BlockPos(0, 1, 0), empty = new BlockPos(2, 1, 0);
    helper.setBlock(lava, net.minecraft.world.level.block.Blocks.LAVA_CAULDRON);
    helper.setBlock(empty, net.minecraft.world.level.block.Blocks.CAULDRON);
    helper.assertTrue(transfer(helper, lava, empty) == 1000, "lava did not move from cauldron to cauldron");
    helper.assertTrue(state(helper, lava).is(net.minecraft.world.level.block.Blocks.CAULDRON), "source cauldron is not empty: " + state(helper, lava));
    helper.assertTrue(state(helper, empty).is(net.minecraft.world.level.block.Blocks.LAVA_CAULDRON), "sink cauldron holds no lava: " + state(helper, empty));
    helper.assertTrue(transfer(helper, empty, empty.above()) == 0, "lava went into air");

    // like a bucket: a partly filled cauldron cannot be drained, but shows its water
    final BlockPos partial = new BlockPos(0, 1, 2), empty2 = new BlockPos(2, 1, 2);
    helper.setBlock(partial, net.minecraft.world.level.block.Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 2));
    helper.setBlock(empty2, net.minecraft.world.level.block.Blocks.CAULDRON);
    final BlockPos partialAbs = helper.absolutePos(partial);
    final FluidStack seen = FluidUtilsOriginal.fluidHandlerAt(new BlockPosition(partialAbs.getX(), partialAbs.getY(), partialAbs.getZ(), helper.getLevel())).getFluidInTank(0);
    helper.assertTrue(seen.getFluid().isSame(Fluids.WATER) && seen.getAmount() == 666, "two thirds of a cauldron read as " + seen.getAmount() + " " + seen.getFluid());
    helper.assertTrue(transfer(helper, partial, empty2) == 0, "a partly filled cauldron was drained");
    helper.assertTrue(state(helper, empty2).is(net.minecraft.world.level.block.Blocks.CAULDRON), "partial water appeared in the sink");

    // a source block is used up, flowing fluid gives nothing
    final BlockPos source = new BlockPos(4, 1, 4), empty3 = new BlockPos(4, 1, 2);
    helper.setBlock(source, net.minecraft.world.level.block.Blocks.LAVA);
    helper.setBlock(empty3, net.minecraft.world.level.block.Blocks.CAULDRON);
    helper.assertTrue(transfer(helper, source, empty3) == 1000, "lava did not move from the source block");
    helper.assertTrue(state(helper, source).isAir(), "the lava source block is still there: " + state(helper, source));
    helper.setBlock(empty3, net.minecraft.world.level.block.Blocks.CAULDRON);
    helper.setBlock(source, Fluids.FLOWING_LAVA.getFlowing(4, false).createLegacyBlock());
    helper.assertTrue(transfer(helper, source, empty3) == 0, "flowing lava filled a cauldron");

    // picking the source tank must not change what the tank holds
    final FluidTank from = new FluidTank(4000), to = new FluidTank(1000);
    from.fill(new FluidStack(Fluids.WATER, 2000), IFluidHandler.FluidAction.EXECUTE);
    final int moved = FluidUtilsOriginal.doTransfer(from, null, to, null, 500, 0);
    helper.assertTrue(moved == 500 && from.getFluidAmount() == 1500 && to.getFluidAmount() == 500,
      "moved " + moved + ", source tank has " + from.getFluidAmount() + ", sink tank " + to.getFluidAmount());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void transposerUseCollectsWhatTheClickProduces(final GameTestHelper helper) {
    final BlockPos pos = new BlockPos(2, 2, 2);
    final BlockPos chest = pos.north(), lava = pos.east(), tank = pos.south(), composter = pos.west();
    helper.setBlock(chest, net.minecraft.world.level.block.Blocks.CHEST);
    ((Container) helper.getBlockEntity(chest)).setItem(0, new ItemStack(Items.BUCKET));
    helper.setBlock(lava, net.minecraft.world.level.block.Blocks.LAVA_CAULDRON);
    helper.setBlock(tank, net.minecraft.world.level.block.Blocks.CAULDRON);
    helper.setBlock(composter, net.minecraft.world.level.block.Blocks.COMPOSTER.defaultBlockState().setValue(ComposterBlock.LEVEL, 8));

    // sides: north 2, south 3, west 4, east 5
    final String program = "local tp = component.proxy(component.list(\"transposer\")())\n"
      + "local function check(what, ok, how) if not ok then error(what .. \": \" .. tostring(how), 0) end end\n"
      + "check(\"bucket on lava\", tp.use(5, 2, 1, 3))\n"
      + "check(\"hand on composter\", tp.use(4, 2))\n"
      + "local ok, how = tp.use(5)\n"
      + "if ok then error(\"empty hand on an empty cauldron did something: \" .. tostring(how), 0) end\n"
      + "while true do computer.pullSignal(1) end\n";
    TestMachines.runMicrocontroller(helper, pos, List.of(Constants.ItemName.CPUTier1, Constants.ItemName.RAMTier2, Constants.BlockName.Transposer), program, mc -> {
      helper.assertTrue(state(helper, lava).is(net.minecraft.world.level.block.Blocks.CAULDRON), "lava was not scooped: " + state(helper, lava));
      helper.assertTrue(state(helper, tank).is(net.minecraft.world.level.block.Blocks.LAVA_CAULDRON), "the scooped lava did not go into the tank: " + state(helper, tank));
      helper.assertTrue(state(helper, composter).getValue(ComposterBlock.LEVEL) == 0, "composter was not emptied");
      final Container box = (Container) helper.getBlockEntity(chest);
      helper.assertTrue(box.getItem(0).is(Items.BUCKET) && box.getItem(0).getCount() == 1, "the empty bucket is not back in its slot: " + box.getItem(0));
      int boneMeal = 0;
      for (int i = 0; i < box.getContainerSize(); i++) {
        if (box.getItem(i).is(Items.BONE_MEAL)) boneMeal += box.getItem(i).getCount();
      }
      helper.assertTrue(boneMeal == 1, "the chest got " + boneMeal + " bone meal from the composter instead of 1");
      final var loose = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(pos)).inflate(4));
      helper.assertTrue(loose.isEmpty(), "items were left lying around: " + loose);
      helper.succeed();
    });
  }
}
