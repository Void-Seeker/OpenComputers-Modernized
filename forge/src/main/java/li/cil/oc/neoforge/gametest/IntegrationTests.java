package li.cil.oc.neoforge.gametest;

import java.util.function.Consumer;
import li.cil.oc.core.impl.util.BlockPosition;
import li.cil.oc.core.impl.util.FluidUtils;
import li.cil.oc.core.impl.util.InventoryUtils;
import li.cil.oc.neoforge.OpenComputers;
import li.cil.oc.neoforge.common.init.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
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
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Other mods' blocks and items through their Forge capabilities: the face asked, the handler's own rules, the data
 * reported.
 */
@GameTestHolder(OpenComputers.ID)
@PrefixGameTestTemplate(false)
public final class IntegrationTests {
  private static final String EMPTY = "empty";
  private static final BlockPos AT = new BlockPos(2, 2, 2);

  private IntegrationTests() {
  }

  /** An enchanting table at pos carrying the given capability provider (attached while it is placed). */
  private static void placeWithCapability(final GameTestHelper helper, final BlockPos pos, final ICapabilityProvider provider, final String name) {
    placeWithCapability(helper, pos, provider, name, net.minecraft.world.level.block.Blocks.ENCHANTING_TABLE);
  }

  /** The given block (one with a block entity of its own and no capabilities: an enchanting table, a bell) with the provider's. */
  private static void placeWithCapability(final GameTestHelper helper, final BlockPos pos, final ICapabilityProvider provider, final String name,
                                          final net.minecraft.world.level.block.Block block) {
    final BlockPos abs = helper.absolutePos(pos);
    final Consumer<AttachCapabilitiesEvent<BlockEntity>> attach = event -> {
      if (event.getObject().getBlockPos().equals(abs)) {
        event.addCapability(new ResourceLocation(OpenComputers.ID, name), provider);
      }
    };
    MinecraftForge.EVENT_BUS.addGenericListener(BlockEntity.class, attach);
    try {
      helper.setBlock(pos, block);
    } finally {
      MinecraftForge.EVENT_BUS.unregister(attach);
    }
  }

  private static BlockPosition at(final GameTestHelper helper, final BlockPos pos) {
    final BlockPos abs = helper.absolutePos(pos);
    return new BlockPosition(abs.getX(), abs.getY(), abs.getZ(), helper.getLevel());
  }

  /**
   * Fluids move into a tank through the face toward the transposer: asked for no side, a tank like Mekanism's hands out
   * a read-only view, and nothing moved.
   */
  @GameTest(template = EMPTY)
  public static void fluidsMoveThroughTheFacingSide(final GameTestHelper helper) {
    final FluidTank tank = new FluidTank(4000);
    final IFluidHandler readOnly = new IFluidHandler() {
      @Override
      public int getTanks() {
        return 1;
      }

      @Override
      public @NotNull FluidStack getFluidInTank(int t) {
        return tank.getFluid();
      }

      @Override
      public int getTankCapacity(int t) {
        return tank.getCapacity();
      }

      @Override
      public boolean isFluidValid(int t, @NotNull FluidStack stack) {
        return false;
      }

      @Override
      public int fill(FluidStack resource, FluidAction action) {
        return 0;
      }

      @Override
      public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
        return FluidStack.EMPTY;
      }

      @Override
      public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
        return FluidStack.EMPTY;
      }
    };
    final LazyOptional<IFluidHandler> sided = LazyOptional.of(() -> tank);
    final LazyOptional<IFluidHandler> unsided = LazyOptional.of(() -> readOnly);
    final BlockPos sink = AT.above();
    placeWithCapability(helper, sink, new ICapabilityProvider() {
      @Override
      public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap != ForgeCapabilities.FLUID_HANDLER) return LazyOptional.empty();
        return side == null ? unsided.cast() : sided.cast();
      }
    }, "test_fluid_tank");
    helper.setBlock(AT, net.minecraft.world.level.block.Blocks.WATER_CAULDRON.defaultBlockState().setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 3));
    // what the transposer below the tank and beside the cauldron asks: each block's face toward it
    final int moved = FluidUtils.transferBetweenFluidHandlersAt(at(helper, AT), Direction.UP, at(helper, sink), Direction.DOWN, 1000, -1);
    helper.assertTrue(moved == 1000 && tank.getFluidAmount() == 1000, "moved " + moved + " mB, the tank holds " + tank.getFluidAmount());
    helper.succeed();
  }

  // bundles carry the handler while the listener is registered (copies of a stack gather their capabilities anew)
  private static Consumer<AttachCapabilitiesEvent<ItemStack>> bundlesCarry(final ItemStackHandler handler) {
    final LazyOptional<net.minecraftforge.items.IItemHandler> holder = LazyOptional.of(() -> handler);
    final ICapabilityProvider provider = new ICapabilityProvider() {
      @Override
      public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return cap == ForgeCapabilities.ITEM_HANDLER ? holder.cast() : LazyOptional.empty();
      }
    };
    final Consumer<AttachCapabilitiesEvent<ItemStack>> attach = event -> {
      if (event.getObject().is(Items.BUNDLE)) event.addCapability(new ResourceLocation(OpenComputers.ID, "test_backpack"), provider);
    };
    MinecraftForge.EVENT_BUS.addGenericListener(ItemStack.class, attach);
    return attach;
  }

  /**
   * An item with an item handler (a backpack) is used through the handler: what it refuses stays where it was, and
   * what is added to a stack in it is saved by it (as a container, refused items were deleted and stacks changed in
   * place were never saved: lost or duplicated).
   */
  @GameTest(template = EMPTY)
  public static void itemInventoriesGoThroughTheirHandler(final GameTestHelper helper) {
    final int[] changes = {0};
    final ItemStackHandler handler = new ItemStackHandler(2) {
      @Override
      public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return stack.is(Items.STICK);
      }

      @Override
      protected void onContentsChanged(int slot) {
        changes[0]++;
      }
    };
    handler.setStackInSlot(0, new ItemStack(Items.STICK, 10));
    final var attach = bundlesCarry(handler);
    try {
      checkBackpack(helper, handler, changes);
    } finally {
      MinecraftForge.EVENT_BUS.unregister(attach);
    }
    helper.succeed();
  }

  private static void checkBackpack(final GameTestHelper helper, final ItemStackHandler handler, final int[] changes) {
    final ItemStack backpack = new ItemStack(Items.BUNDLE);
    final var inventory = InventoryUtils.itemInventoryFor(backpack, null);
    helper.assertTrue(inventory != null, "the backpack has no item inventory");
    final ItemStack dirt = new ItemStack(Items.DIRT, 4);
    InventoryUtils.insertIntoInventorySlot(dirt, inventory, 1, 64);
    helper.assertTrue(dirt.getCount() == 4 && handler.getStackInSlot(1).isEmpty(), "the backpack refused dirt, and " + (4 - dirt.getCount()) + " of 4 are gone");
    changes[0] = 0;
    final ItemStack sticks = new ItemStack(Items.STICK, 3);
    InventoryUtils.insertIntoInventorySlot(sticks, inventory, 0, 64);
    helper.assertTrue(handler.getStackInSlot(0).getCount() == 13 && changes[0] > 0,
      "3 sticks onto 10: the backpack holds " + handler.getStackInSlot(0).getCount() + " and saved " + changes[0] + " times");
  }

  /** A fresh tool has no tag for programs (1.20.1 writes Damage:0 into every tool: all reported hasTag). */
  @GameTest(template = EMPTY)
  public static void toolsReportNoTagUntilTheyHaveOne(final GameTestHelper helper) {
    final var converter = new li.cil.oc.core.impl.integration.vanilla.ConverterItemStack();
    final java.util.Map<Object, Object> fresh = new java.util.HashMap<>();
    converter.convert(new ItemStack(Items.DIAMOND_PICKAXE), fresh);
    final ItemStack named = new ItemStack(Items.DIAMOND_PICKAXE);
    named.setHoverName(net.minecraft.network.chat.Component.literal("digger"));
    final java.util.Map<Object, Object> renamed = new java.util.HashMap<>();
    converter.convert(named, renamed);
    helper.assertTrue(Boolean.FALSE.equals(fresh.get("hasTag")) && Boolean.TRUE.equals(renamed.get("hasTag")),
      "a fresh pickaxe reports hasTag " + fresh.get("hasTag") + ", a renamed one " + renamed.get("hasTag"));
    helper.succeed();
  }

  /** The adapter asks a block about its face toward the adapter (it asked about the far face). */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void adapterAsksTheFacingSide(final GameTestHelper helper) {
    final var storage = new net.minecraftforge.energy.EnergyStorage(1000);
    final LazyOptional<net.minecraftforge.energy.IEnergyStorage> energy = LazyOptional.of(() -> storage);
    final BlockPos device = AT.east();
    placeWithCapability(helper, device, new ICapabilityProvider() {
      @Override
      public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return cap == ForgeCapabilities.ENERGY && side == Direction.WEST ? energy.cast() : LazyOptional.empty();
      }
    }, "test_energy_west");
    helper.setBlock(AT, Blocks.ADAPTER.get());
    helper.runAfterDelay(10, () -> {
      final var adapter = (li.cil.oc.core.impl.common.blockentity.Adapter) helper.getBlockEntity(AT);
      adapter.neighborChanged(Direction.EAST);
      boolean found = false;
      for (final var node : adapter.node().reachableNodes()) {
        if (node instanceof li.cil.oc.api.network.Component c && "energy_device".equals(c.name())) found = true;
      }
      helper.assertTrue(found, "an adapter west of a block with energy on its west face found no energy_device");
      helper.succeed();
    });
  }

  /**
   * The address alone can be taken over (the adapter does for the same block made up of other drivers now); the data
   * of another set of drivers is not loaded, as in OC.
   */
  @GameTest(template = EMPTY)
  public static void compoundBlockKeepsItsAddressWhenItsDriversChange(final GameTestHelper helper) {
    final java.util.function.Supplier<li.cil.oc.api.network.ManagedEnvironment> env = () -> new li.cil.oc.api.prefab.AbstractManagedEnvironment() {
    };
    final var before = new li.cil.oc.core.impl.server.driver.CompoundBlockEnvironment("test", new String[]{"one", "two"},
      new li.cil.oc.api.network.ManagedEnvironment[]{env.get(), new li.cil.oc.api.prefab.AbstractManagedEnvironment() {
      }});
    li.cil.oc.api.Network.joinNewNetwork(before.node());
    final String address = before.node().address();
    final var tag = new net.minecraft.nbt.CompoundTag();
    before.save(tag, helper.getLevel().registryAccess());
    before.node().remove();
    final var other = new li.cil.oc.core.impl.server.driver.CompoundBlockEnvironment("test", new String[]{"one"}, new li.cil.oc.api.network.ManagedEnvironment[]{env.get()});
    other.load(tag, helper.getLevel().registryAccess());
    helper.assertTrue(!address.equals(other.node().address()), "another set of drivers took over the address");
    final var after = new li.cil.oc.core.impl.server.driver.CompoundBlockEnvironment("test", new String[]{"one"}, new li.cil.oc.api.network.ManagedEnvironment[]{env.get()});
    after.loadAddress(tag, helper.getLevel().registryAccess());
    helper.assertTrue(address != null && address.equals(after.node().address()), "the address " + address + " became " + after.node().address());
    helper.succeed();
  }

  private static String blockAddress(final li.cil.oc.core.impl.common.blockentity.Adapter adapter) {
    for (final var node : adapter.node().reachableNodes()) {
      if (node.host() instanceof li.cil.oc.core.impl.server.driver.CompoundBlockEnvironment) return node.address();
    }
    return null;
  }

  /** Where the test driver works: a block there is made up of one more driver (the tank and inventory drivers are off by default). */
  private static final java.util.Set<BlockPos> SECOND_DRIVER_AT = java.util.concurrent.ConcurrentHashMap.newKeySet();
  private static boolean secondDriverRegistered;

  private static synchronized void registerSecondDriver() {
    if (secondDriverRegistered) return;
    secondDriverRegistered = true;
    li.cil.oc.core.impl.server.driver.Registry.INSTANCE.setLocked(false);
    try {
      li.cil.oc.api.API.driver.add(new li.cil.oc.api.driver.DriverBlock() {
        @Override
        public boolean worksWith(final net.minecraft.world.level.Level world, final BlockPos pos, final Direction side) {
          return SECOND_DRIVER_AT.contains(pos);
        }

        @Override
        public li.cil.oc.api.network.ManagedEnvironment createEnvironment(final net.minecraft.world.level.Level world, final BlockPos pos, final Direction side) {
          return new li.cil.oc.api.prefab.AbstractManagedEnvironment() {
          };
        }
      });
    } finally {
      li.cil.oc.core.impl.server.driver.Registry.INSTANCE.setLocked(true);
    }
  }

  private static ICapabilityProvider energyProvider() {
    final var storage = new net.minecraftforge.energy.EnergyStorage(1000);
    final LazyOptional<net.minecraftforge.energy.IEnergyStorage> energy = LazyOptional.of(() -> storage);
    return new ICapabilityProvider() {
      @Override
      public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return cap == ForgeCapabilities.ENERGY ? energy.cast() : LazyOptional.empty();
      }
    };
  }

  /**
   * A block placed where another was keeps no address of the old one, also one made up of the same drivers (it took
   * over the old one's); the same block placed again gets its own back, also when other drivers make it up now.
   */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void adapterGivesAReplacedBlockItsOwnAddress(final GameTestHelper helper) {
    registerSecondDriver();
    final ICapabilityProvider provider = energyProvider();
    final BlockPos device = AT.east();
    final BlockPos deviceAbs = helper.absolutePos(device);
    placeWithCapability(helper, device, provider, "test_energy_any");
    helper.setBlock(AT, Blocks.ADAPTER.get());
    helper.runAfterDelay(10, () -> {
      final var adapter = (li.cil.oc.core.impl.common.blockentity.Adapter) helper.getBlockEntity(AT);
      final Runnable replace = () -> {
        helper.setBlock(device, net.minecraft.world.level.block.Blocks.AIR);
        adapter.neighborChanged(Direction.EAST);
      };
      adapter.neighborChanged(Direction.EAST);
      final String first = blockAddress(adapter);
      replace.run();
      placeWithCapability(helper, device, provider, "test_energy_any");
      adapter.neighborChanged(Direction.EAST);
      final String again = blockAddress(adapter);
      replace.run();
      SECOND_DRIVER_AT.add(deviceAbs);
      placeWithCapability(helper, device, provider, "test_energy_any");
      adapter.neighborChanged(Direction.EAST);
      final String otherDrivers = blockAddress(adapter);
      SECOND_DRIVER_AT.remove(deviceAbs);
      replace.run();
      helper.setBlock(device, net.minecraft.world.level.block.Blocks.FURNACE);
      adapter.neighborChanged(Direction.EAST);
      final String furnace = blockAddress(adapter);
      replace.run();
      placeWithCapability(helper, device, provider, "test_energy_any");
      adapter.neighborChanged(Direction.EAST);
      final String table = blockAddress(adapter);
      replace.run();
      placeWithCapability(helper, device, provider, "test_energy_any", net.minecraft.world.level.block.Blocks.BELL);
      adapter.neighborChanged(Direction.EAST);
      final String bell = blockAddress(adapter);
      helper.assertTrue(first != null && first.equals(again), "the same block placed again got " + again + " instead of " + first);
      helper.assertTrue(first.equals(otherDrivers), "the same block made up of other drivers got " + otherDrivers + " instead of " + first);
      helper.assertTrue(furnace != null && !furnace.equals(first), "a furnace placed where the block was got its address " + furnace);
      helper.assertTrue(table != null && bell != null && !bell.equals(table), "a bell placed where an enchanting table of the same drivers was got " + bell + " (the table had " + table + ")");
      helper.succeed();
    });
  }

  /** A block whose drivers change while the game runs keeps its address (it got a new one; across a reload it did not). */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void adapterKeepsTheAddressWhenTheDriversChangeLive(final GameTestHelper helper) {
    registerSecondDriver();
    final BlockPos device = AT.east();
    final BlockPos deviceAbs = helper.absolutePos(device);
    placeWithCapability(helper, device, energyProvider(), "test_energy_toggle");
    helper.setBlock(AT, Blocks.ADAPTER.get());
    helper.runAfterDelay(10, () -> {
      final var adapter = (li.cil.oc.core.impl.common.blockentity.Adapter) helper.getBlockEntity(AT);
      adapter.neighborChanged(Direction.EAST);
      final String before = blockAddress(adapter);
      SECOND_DRIVER_AT.add(deviceAbs);
      adapter.neighborChanged(Direction.EAST);
      SECOND_DRIVER_AT.remove(deviceAbs);
      final String after = blockAddress(adapter);
      helper.assertTrue(before != null && before.equals(after), "the block got " + after + " instead of " + before + " when its drivers changed");
      helper.succeed();
    });
  }

  /** An adapter saved before it recorded the blocks gives the block its address back (taken as the same block). */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void adapterSavedWithoutBlockIdsKeepsTheAddress(final GameTestHelper helper) {
    final BlockPos device = AT.east();
    placeWithCapability(helper, device, energyProvider(), "test_energy_old");
    helper.setBlock(AT, Blocks.ADAPTER.get());
    helper.runAfterDelay(10, () -> {
      final var adapter = (li.cil.oc.core.impl.common.blockentity.Adapter) helper.getBlockEntity(AT);
      adapter.neighborChanged(Direction.EAST);
      final String before = blockAddress(adapter);
      final var tag = adapter.saveWithFullMetadata();
      final var blocks = tag.getList(li.cil.oc.core.impl.OCSettings.namespace + "adapter.blocks", net.minecraft.nbt.Tag.TAG_COMPOUND);
      for (int i = 0; i < blocks.size(); i++) blocks.getCompound(i).remove("block");
      final BlockPos abs = helper.absolutePos(AT);
      helper.getLevel().removeBlockEntity(abs);
      final var loaded = (li.cil.oc.core.impl.common.blockentity.Adapter) BlockEntity.loadStatic(abs, helper.getLevel().getBlockState(abs), tag);
      helper.getLevel().getChunkAt(abs).addAndRegisterBlockEntity(loaded);
      helper.runAfterDelay(10, () -> {
        loaded.neighborChanged(Direction.EAST);
        final String after = blockAddress(loaded);
        helper.assertTrue(before != null && before.equals(after), "an old adapter gave the block " + after + " instead of " + before);
        helper.succeed();
      });
    });
  }
}
