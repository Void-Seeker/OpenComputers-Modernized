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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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

    // placing breaks nothing: lava into air, water waterlogs a slab; nothing onto a source block or over a torch
    final Block cauldron = net.minecraft.world.level.block.Blocks.CAULDRON;
    final BlockState fullWater = net.minecraft.world.level.block.Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3);
    final BlockPos lava2 = new BlockPos(1, 1, 4), air = new BlockPos(1, 2, 4);
    helper.setBlock(lava2, net.minecraft.world.level.block.Blocks.LAVA_CAULDRON);
    helper.assertTrue(transfer(helper, lava2, air) == 1000, "lava was not poured into air");
    helper.assertTrue(state(helper, air).getFluidState().isSource() && state(helper, air).getFluidState().is(Fluids.LAVA), "no lava source in the air: " + state(helper, air));
    final BlockPos water = new BlockPos(3, 3, 3), slab = new BlockPos(3, 3, 1);
    helper.setBlock(water, fullWater);
    helper.setBlock(slab, net.minecraft.world.level.block.Blocks.OAK_SLAB);
    helper.assertTrue(transfer(helper, water, slab) == 1000, "water did not go into the slab");
    helper.assertTrue(state(helper, slab).getValue(BlockStateProperties.WATERLOGGED), "the slab is not waterlogged");
    final BlockPos water2 = new BlockPos(0, 3, 4), torch = new BlockPos(1, 3, 1), pool = new BlockPos(3, 1, 0);
    helper.setBlock(water2, fullWater);
    helper.setBlock(torch, net.minecraft.world.level.block.Blocks.TORCH);
    helper.setBlock(pool, net.minecraft.world.level.block.Blocks.WATER);
    helper.assertTrue(transfer(helper, water2, torch) == 0 && state(helper, torch).is(net.minecraft.world.level.block.Blocks.TORCH), "water washed the torch away");
    helper.assertTrue(transfer(helper, water2, pool) == 0 && state(helper, water2).equals(fullWater), "water was poured onto a source block");
    // sucking: the waterlogged slab gives its water back
    final BlockPos empty4 = new BlockPos(4, 3, 2);
    helper.setBlock(empty4, cauldron);
    helper.assertTrue(transfer(helper, slab, empty4) == 1000, "the waterlogged slab gave no water");
    helper.assertTrue(!state(helper, slab).getValue(BlockStateProperties.WATERLOGGED) && state(helper, empty4).equals(fullWater), "slab " + state(helper, slab) + ", cauldron " + state(helper, empty4));

    // picking the source tank must not change what the tank holds
    final FluidTank from = new FluidTank(4000), to = new FluidTank(1000);
    from.fill(new FluidStack(Fluids.WATER, 2000), IFluidHandler.FluidAction.EXECUTE);
    final int moved = FluidUtilsOriginal.doTransfer(from, null, to, null, 500, 0);
    helper.assertTrue(moved == 500 && from.getFluidAmount() == 1500 && to.getFluidAmount() == 500,
      "moved " + moved + ", source tank has " + from.getFluidAmount() + ", sink tank " + to.getFluidAmount());
    helper.succeed();
  }

  @GameTest(template = EMPTY, timeoutTicks = 200)
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

  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void transposerDropsItemsPlacesBlocksAndMovesWorldFluid(final GameTestHelper helper) {
    final BlockPos pos = new BlockPos(2, 2, 2);
    final BlockPos chest = pos.north(), stone = pos.above(), drop = pos.east(), place = pos.west(), water = pos.below(), pour = pos.south();
    helper.setBlock(chest, net.minecraft.world.level.block.Blocks.CHEST);
    final Container box = (Container) helper.getBlockEntity(chest);
    box.setItem(0, new ItemStack(Items.STONE, 2));
    box.setItem(1, new ItemStack(Items.BONE_MEAL, 5));
    helper.setBlock(stone, net.minecraft.world.level.block.Blocks.STONE);
    helper.setBlock(water, net.minecraft.world.level.block.Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
    // sides: down 0, up 1, north 2, south 3, west 4, east 5
    final String program = "local tp = component.proxy(component.list(\"transposer\")())\n"
      + "local function expect(what, want, got, why) if got ~= want then error(what .. \": \" .. tostring(got) .. \" \" .. tostring(why), 0) end end\n"
      // the water first: its current would carry the dropped items off
      + "local _, moved = tp.transferFluid(0, 3)\n"
      + "expect(\"pour water\", 1000, moved)\n"
      + "_, moved = tp.transferFluid(3, 0)\n"
      + "expect(\"suck water\", 1000, moved)\n"
      + "computer.pullSignal(2)\n"
      + "expect(\"drop 2 bone meal\", 2, tp.dropItem(2, 5, 2, 2))\n"
      + "local n, why = tp.dropItem(2, 1)\n"
      + "expect(\"drop into stone\", \"blocked\", why, n)\n"
      + "expect(\"place stone\", true, tp.placeBlock(2, 4))\n"
      + "local ok, why = tp.placeBlock(2, 4)\n"
      + "expect(\"place onto stone\", \"blocked\", why, ok)\n"
      + "ok, why = tp.placeBlock(2, 3, 2)\n"
      + "expect(\"place bone meal\", \"not a block\", why, ok)\n"
      + "while true do computer.pullSignal(1) end\n";
    TestMachines.runMicrocontroller(helper, pos, List.of(Constants.ItemName.CPUTier1, Constants.ItemName.RAMTier2, Constants.BlockName.Transposer), program, mc -> {
      helper.assertTrue(state(helper, place).is(net.minecraft.world.level.block.Blocks.STONE), "no stone was placed: " + state(helper, place));
      helper.assertTrue(box.getItem(0).is(Items.STONE) && box.getItem(0).getCount() == 1, "stone slot holds " + box.getItem(0));
      helper.assertTrue(box.getItem(1).is(Items.BONE_MEAL) && box.getItem(1).getCount() == 3, "bone meal slot holds " + box.getItem(1));
      // anywhere in the test area: the test world is terrain, and water can push the item around
      int dropped = 0;
      for (final ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(5, 5, 5))).inflate(6))) {
        if (item.getItem().is(Items.BONE_MEAL)) dropped += item.getItem().getCount();
      }
      helper.assertTrue(dropped == 2, dropped + " bone meal were dropped instead of 2");
      helper.assertTrue(state(helper, water).getValue(LayeredCauldronBlock.LEVEL) == 3, "the water did not come back into the cauldron");
      helper.assertTrue(!state(helper, pour).getFluidState().isSource(), "the poured water source is still there");
      helper.succeed();
    });
  }
}
