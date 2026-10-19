package li.cil.oc.neoforge.gametest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import li.cil.oc.core.Constants;
import li.cil.oc.core.impl.util.BlockPosition;
import li.cil.oc.core.impl.util.FluidUtils;
import li.cil.oc.core.impl.util.InventoryAccess;
import li.cil.oc.core.impl.util.InventoryUtils;
import li.cil.oc.neoforge.OpenComputers;
import li.cil.oc.neoforge.util.FluidUtilsOriginal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.EnchantmentTableBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Transposer fluid moves with vanilla blocks, its right-click, and item moves with inventories that are only Forge
 * item handlers.
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
      // what the clicks made (the test framework's own buttons can drop nearby when neighbouring tests are cleared)
      final var loose = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(pos)).inflate(4),
        item -> item.getItem().is(Items.BUCKET) || item.getItem().is(Items.LAVA_BUCKET) || item.getItem().is(Items.BONE_MEAL));
      helper.assertTrue(loose.isEmpty(), "items were left lying around: " + loose);
      helper.succeed();
    });
  }

  @GameTest(template = EMPTY, timeoutTicks = 300)
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
      + "error('done:all', 0)\n"; // every step passed
    TestMachines.runMicrocontroller(helper, pos, List.of(Constants.ItemName.CPUTier1, Constants.ItemName.RAMTier2, Constants.BlockName.Transposer), program, mc -> TestMachines.when(helper, () -> !mc.machine().isRunning(), 100, () -> {
      helper.assertTrue(String.valueOf(mc.machine().lastError()).contains("done:all"), "the program did not get through: " + mc.machine().lastError());
      helper.assertTrue(state(helper, place).is(net.minecraft.world.level.block.Blocks.STONE), "no stone was placed: " + state(helper, place));
      helper.assertTrue(box.getItem(0).is(Items.STONE) && box.getItem(0).getCount() == 1, "stone slot holds " + box.getItem(0));
      helper.assertTrue(box.getItem(1).is(Items.BONE_MEAL) && box.getItem(1).getCount() == 3, "bone meal slot holds " + box.getItem(1));
      // anywhere in the test area: the test world is terrain, and water can push the item around
      int dropped = 0;
      for (final ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(5, 5, 5))).inflate(2))) {
        if (item.getItem().is(Items.BONE_MEAL)) dropped += item.getItem().getCount();
      }
      helper.assertTrue(dropped == 2, dropped + " bone meal were dropped instead of 2");
      helper.assertTrue(state(helper, water).getValue(LayeredCauldronBlock.LEVEL) == 3, "the water did not come back into the cauldron");
      helper.assertTrue(!state(helper, pour).getFluidState().isSource(), "the poured water source is still there");
      helper.succeed();
    }));
  }

  /**
   * Like the Embers bore: nine slots, but everything inserted goes to the fuel slot (9th), which takes only fuel
   * while empty; spent fuel (a bucket) comes out of it, burning fuel does not.
   */
  private static final class FuelSlotHandler extends ItemStackHandler {
    FuelSlotHandler() {
      super(9);
    }

    @Override
    public ItemStack insertItem(final int slot, final ItemStack stack, final boolean simulate) {
      if (getStackInSlot(8).isEmpty() && ForgeHooks.getBurnTime(stack, null) <= 0) return stack;
      return super.insertItem(8, stack, simulate);
    }

    @Override
    public ItemStack extractItem(final int slot, final int amount, final boolean simulate) {
      if (slot == 8) {
        final ItemStack fuel = getStackInSlot(8);
        if (fuel.getCount() > 1 || ForgeHooks.getBurnTime(fuel, null) != 0) return ItemStack.EMPTY;
      }
      return super.extractItem(slot, amount, simulate);
    }
  }

  /**
   * Places an enchanting table (a block entity that is no Container) carrying the item handler as its Forge item
   * capability on the given faces. The capability is attached only while this places it.
   */
  private static <H extends IItemHandler> H placeItemHandler(final GameTestHelper helper, final BlockPos pos, final H handler, final Direction... faces) {
    final LazyOptional<IItemHandler> holder = LazyOptional.of(() -> handler);
    final ICapabilityProvider provider = new ICapabilityProvider() {
      @Override
      public <T> LazyOptional<T> getCapability(final Capability<T> cap, final @Nullable Direction side) {
        return cap == ForgeCapabilities.ITEM_HANDLER && (side == null || List.of(faces).contains(side)) ? holder.cast() : LazyOptional.empty();
      }
    };
    final BlockPos abs = helper.absolutePos(pos);
    final Consumer<AttachCapabilitiesEvent<BlockEntity>> attach = event -> {
      if (event.getObject() instanceof EnchantmentTableBlockEntity && event.getObject().getBlockPos().equals(abs)) {
        event.addCapability(new ResourceLocation(OpenComputers.ID, "test_item_handler"), provider);
      }
    };
    MinecraftForge.EVENT_BUS.addGenericListener(BlockEntity.class, attach);
    try {
      helper.setBlock(pos, net.minecraft.world.level.block.Blocks.ENCHANTING_TABLE);
    } finally {
      MinecraftForge.EVENT_BUS.unregister(attach);
    }
    helper.assertTrue(helper.getBlockEntity(pos).getCapability(ForgeCapabilities.ITEM_HANDLER, faces[0]).isPresent(), "the test item handler was not attached");
    return handler;
  }

  private static Map<String, Integer> count(final Container box) {
    final Map<String, Integer> counts = new HashMap<>();
    for (int i = 0; i < box.getContainerSize(); i++) {
      if (!box.getItem(i).isEmpty()) counts.merge(box.getItem(i).getItem().toString(), box.getItem(i).getCount(), Integer::sum);
    }
    return counts;
  }

  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void transposerMovesItemsThroughItemHandlersByTheirRules(final GameTestHelper helper) {
    final BlockPos pos = new BlockPos(2, 2, 2);
    final BlockPos bore = pos.below(), aside = pos.east(), chest = pos.north(), bucketHolder = pos.above(), lava = pos.south();
    final FuelSlotHandler fuel = placeItemHandler(helper, bore, new FuelSlotHandler(), Direction.UP);
    fuel.setStackInSlot(0, new ItemStack(Items.GLOWSTONE_DUST, 5));
    fuel.setStackInSlot(8, new ItemStack(Items.BUCKET));
    placeItemHandler(helper, aside, new FuelSlotHandler(), Direction.UP);
    final ItemStackHandler plain = placeItemHandler(helper, bucketHolder, new ItemStackHandler(2), Direction.values());
    plain.setStackInSlot(0, new ItemStack(Items.BUCKET));
    helper.setBlock(lava, net.minecraft.world.level.block.Blocks.LAVA_CAULDRON);
    helper.setBlock(chest, net.minecraft.world.level.block.Blocks.CHEST);
    final Container box = (Container) helper.getBlockEntity(chest);
    box.setItem(0, new ItemStack(Items.LAVA_BUCKET));
    box.setItem(1, new ItemStack(Items.COBBLESTONE, 3));
    // sides: down 0, up 1, north 2, south 3, west 4, east 5
    final String program = "local tp = component.proxy(component.list(\"transposer\")())\n"
      + "local function expect(what, want, got, why) if got ~= want then error(what .. \": \" .. tostring(got) .. \" \" .. tostring(why), 0) end end\n"
      + "expect(\"slots seen from the top\", 9, tp.getInventorySize(0))\n"
      + "expect(\"a face without the handler\", nil, tp.getInventorySize(5))\n"
      + "expect(\"not fuel\", false, tp.transferItem(2, 0, 64, 2))\n"
      + "expect(\"fuel onto the spent bucket\", false, tp.transferItem(2, 0, 64, 1))\n"
      + "expect(\"the spent bucket out\", true, tp.transferItem(0, 2, 1, 9))\n"
      + "expect(\"fuel in\", true, tp.transferItem(2, 0, 64, 1))\n"
      + "expect(\"burning fuel out\", false, tp.transferItem(0, 2, 1, 9))\n"
      + "expect(\"burning fuel swapped out\", false, tp.swap(0, 2, 9, 2))\n"
      + "expect(\"the output out\", true, tp.transferItem(0, 2))\n"
      + "expect(\"nothing else comes out\", false, tp.transferItem(0, 2))\n"
      + "local seen = tp.getStackInSlot(0, 9)\n"
      + "expect(\"the fuel seen\", \"minecraft:lava_bucket\", seen and seen.name)\n"
      + "local _, moved = tp.transferFluidFromTankToContainer(3, 1, 1)\n"
      + "expect(\"lava into the held bucket\", 1000, moved)\n"
      + "computer.pullSignal(0.5)\n"
      + "local held, cauldron = tp.getStackInSlot(1, 1), tp.getFluidInTank(3, 1)\n"
      + "expect(\"the filled bucket in its slot\", \"minecraft:lava_bucket\", held and held.name, cauldron and (tostring(cauldron.name) .. \" \" .. tostring(cauldron.amount)))\n"
      + "expect(\"the drained cauldron\", 0, cauldron and cauldron.amount or 0)\n"
      + "_, moved = tp.transferFluidFromContainerToTank(1, 1, 3)\n"
      + "expect(\"lava out of the held bucket\", 1000, moved)\n"
      + "while true do computer.pullSignal(1) end\n";
    TestMachines.runMicrocontroller(helper, pos, List.of(Constants.ItemName.CPUTier1, Constants.ItemName.RAMTier2, Constants.BlockName.Transposer), program, mc -> {
      helper.assertTrue(fuel.getStackInSlot(8).is(Items.LAVA_BUCKET), "the fuel slot holds " + fuel.getStackInSlot(8));
      for (int i = 0; i < 8; i++) helper.assertTrue(fuel.getStackInSlot(i).isEmpty(), "slot " + (i + 1) + " still holds " + fuel.getStackInSlot(i));
      helper.assertTrue(count(box).equals(Map.of("bucket", 1, "cobblestone", 3, "glowstone_dust", 5)), "the chest holds " + count(box));
      helper.assertTrue(plain.getStackInSlot(0).is(Items.BUCKET) && plain.getStackInSlot(0).getCount() == 1 && plain.getStackInSlot(1).isEmpty(),
        "the bucket holder holds " + plain.getStackInSlot(0) + ", " + plain.getStackInSlot(1));
      helper.assertTrue(state(helper, lava).is(net.minecraft.world.level.block.Blocks.LAVA_CAULDRON), "the lava did not come back: " + state(helper, lava));
      helper.succeed();
    });
  }

  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void transposerKeepsItemsWithinOneInventoryFillsCompostersAndBuckets(final GameTestHelper helper) {
    // a plain vanilla container (no item capability, like a modded block entity that is only a Container)
    final var plain = new net.minecraft.world.SimpleContainer(4);
    plain.setItem(0, new ItemStack(Items.PUMPKIN_PIE, 2));
    plain.setItem(1, new ItemStack(Items.DIRT, 10));
    final InventoryAccess access = InventoryAccess.of(plain, Direction.NORTH);
    InventoryUtils.transferBetweenInventoriesSlots(access, 1, access, null, 64);
    InventoryUtils.transferBetweenInventories(access, access, 64);
    helper.assertTrue(count(plain).equals(Map.of("pumpkin_pie", 2, "dirt", 10)), "moving within a container left " + count(plain));

    final BlockPos pos = new BlockPos(2, 2, 2);
    final BlockPos chest = pos.north(), below = pos.below(), aside = pos.east();
    helper.setBlock(chest, net.minecraft.world.level.block.Blocks.CHEST);
    final Container box = (Container) helper.getBlockEntity(chest);
    box.setItem(0, new ItemStack(Items.PUMPKIN_PIE, 2));
    box.setItem(1, new ItemStack(Items.DIRT, 10));
    box.setItem(2, new ItemStack(Items.BUCKET, 2));
    final BlockPos water = pos.west();
    helper.setBlock(water, net.minecraft.world.level.block.Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
    helper.setBlock(below, net.minecraft.world.level.block.Blocks.COMPOSTER);
    helper.setBlock(aside, net.minecraft.world.level.block.Blocks.COMPOSTER);
    final String program = "local tp = component.proxy(component.list(\"transposer\")())\n"
      + "local function expect(what, want, got, why) if got ~= want then error(what .. \": \" .. tostring(got) .. \" \" .. tostring(why), 0) end end\n"
      + "tp.transferItem(2, 2, 64, 2)\n"
      + "tp.transferItem(2, 2)\n"
      + "expect(\"a pie into the composter from above\", true, tp.transferItem(2, 0, 1, 1))\n"
      + "expect(\"a pie into a composter from its side\", false, tp.transferItem(2, 5, 1, 1))\n"
      + "local _, moved = tp.transferFluidFromTankToContainer(4, 2, 3)\n"
      + "expect(\"water into a bucket in the chest\", 1000, moved)\n"
      + "while true do computer.pullSignal(1) end\n";
    TestMachines.runMicrocontroller(helper, pos, List.of(Constants.ItemName.CPUTier1, Constants.ItemName.RAMTier2, Constants.BlockName.Transposer), program, mc -> {
      helper.assertTrue(count(box).equals(Map.of("pumpkin_pie", 1, "dirt", 10, "bucket", 1, "water_bucket", 1)), "the chest holds " + count(box));
      helper.assertTrue(state(helper, water).is(net.minecraft.world.level.block.Blocks.CAULDRON), "the cauldron still holds water: " + state(helper, water));
      helper.assertTrue(state(helper, below).getValue(ComposterBlock.LEVEL) == 1, "the composter below is at level " + state(helper, below).getValue(ComposterBlock.LEVEL));
      helper.assertTrue(state(helper, aside).getValue(ComposterBlock.LEVEL) == 0, "the composter aside took the pie");
      helper.succeed();
    });
  }
}
