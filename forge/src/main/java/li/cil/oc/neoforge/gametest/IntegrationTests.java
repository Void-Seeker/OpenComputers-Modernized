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

  /** How many drivers make up the adapter's block (to know the test driver came along). */
  private static int blockDrivers(final li.cil.oc.core.impl.common.blockentity.Adapter adapter) {
    for (final var node : adapter.node().reachableNodes()) {
      if (node.host() instanceof li.cil.oc.core.impl.server.driver.CompoundBlockEnvironment compound) return compound.environments().length;
    }
    return 0;
  }

  private static String blockAddress(final li.cil.oc.core.impl.common.blockentity.Adapter adapter) {
    for (final var node : adapter.node().reachableNodes()) {
      if (node.host() instanceof li.cil.oc.core.impl.server.driver.CompoundBlockEnvironment) return node.address();
    }
    return null;
  }

  /** Where the test driver works: a block there is made up of one more driver (the tank and inventory drivers are off by default). */
  private static final java.util.Set<BlockPos> SECOND_DRIVER_AT = java.util.concurrent.ConcurrentHashMap.newKeySet();
  /** Where the named test driver works: one more driver, with a name of its own that outranks energy's. */
  private static final java.util.Set<BlockPos> NAMED_DRIVER_AT = java.util.concurrent.ConcurrentHashMap.newKeySet();
  /** Where the generic test driver works (as inventories and tanks: a part with no name of its own). */
  private static final java.util.Set<BlockPos> GENERIC_DRIVER_AT = java.util.concurrent.ConcurrentHashMap.newKeySet();
  /** Where the other named test driver works: another device, as another machine behind the same bounding block. */
  private static final java.util.Set<BlockPos> OTHER_DRIVER_AT = java.util.concurrent.ConcurrentHashMap.newKeySet();
  private static boolean secondDriverRegistered;

  private static synchronized void registerSecondDriver() {
    if (secondDriverRegistered) return;
    secondDriverRegistered = true;
    li.cil.oc.core.impl.server.driver.Registry.INSTANCE.setLocked(false);
    try {
      li.cil.oc.api.API.driver.add(new li.cil.oc.api.driver.DriverBlock() {
        @Override
        public boolean worksWith(final net.minecraft.world.level.Level world, final BlockPos pos, final Direction side) {
          return NAMED_DRIVER_AT.contains(pos);
        }

        @Override
        public li.cil.oc.api.network.ManagedEnvironment createEnvironment(final net.minecraft.world.level.Level world, final BlockPos pos, final Direction side) {
          return new NamedTestEnvironment("test_named");
        }
      });
      li.cil.oc.api.API.driver.add(new li.cil.oc.api.driver.DriverBlock() {
        @Override
        public boolean worksWith(final net.minecraft.world.level.Level world, final BlockPos pos, final Direction side) {
          return GENERIC_DRIVER_AT.contains(pos);
        }

        @Override
        public boolean isGeneric() {
          return true;
        }

        @Override
        public li.cil.oc.api.network.ManagedEnvironment createEnvironment(final net.minecraft.world.level.Level world, final BlockPos pos, final Direction side) {
          return new li.cil.oc.api.prefab.AbstractManagedEnvironment() {
            { // (alone it makes the block: it needs a node to be seen)
              setNode(li.cil.oc.api.Network.newNode(this, li.cil.oc.api.network.Visibility.Network).create());
            }
          };
        }
      });
      li.cil.oc.api.API.driver.add(new li.cil.oc.api.driver.DriverBlock() {
        @Override
        public boolean worksWith(final net.minecraft.world.level.Level world, final BlockPos pos, final Direction side) {
          return OTHER_DRIVER_AT.contains(pos);
        }

        @Override
        public li.cil.oc.api.network.ManagedEnvironment createEnvironment(final net.minecraft.world.level.Level world, final BlockPos pos, final Direction side) {
          return new NamedTestEnvironment("test_other");
        }
      });
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

  private static final class NamedTestEnvironment extends li.cil.oc.api.prefab.AbstractManagedEnvironment implements li.cil.oc.api.driver.NamedBlock {
    private final String name;

    NamedTestEnvironment(final String name) {
      this.name = name;
    }

    @Override
    public String preferredName() {
      return name;
    }

    @Override
    public int priority() {
      return 1;
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
      final String otherDrivers;
      final int drivers;
      SECOND_DRIVER_AT.add(deviceAbs);
      try {
        placeWithCapability(helper, device, provider, "test_energy_any");
        adapter.neighborChanged(Direction.EAST);
        otherDrivers = blockAddress(adapter);
        drivers = blockDrivers(adapter);
      } finally {
        SECOND_DRIVER_AT.remove(deviceAbs);
      }
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
      helper.assertTrue(drivers == 2, "the test driver did not come along: " + drivers + " drivers");
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
      final int drivers;
      SECOND_DRIVER_AT.add(deviceAbs);
      try {
        adapter.neighborChanged(Direction.EAST);
        drivers = blockDrivers(adapter);
      } finally {
        SECOND_DRIVER_AT.remove(deviceAbs);
      }
      final String after = blockAddress(adapter);
      helper.assertTrue(drivers == 2, "the test driver did not come along: " + drivers + " drivers");
      adapter.neighborChanged(Direction.EAST); // energy alone again
      final String alone = blockAddress(adapter);
      helper.assertTrue(before != null && before.equals(after), "the block got " + after + " instead of " + before + " when its drivers changed");
      helper.assertTrue(before.equals(alone), "the block got " + alone + " instead of " + before + " when the driver went again");
      helper.succeed();
    });
  }

  /**
   * An adapter saved before it recorded the blocks gives the block still there its address back, also when other drivers
   * make it up now (it asked the far face then: a block with energy on the near face only lost its address).
   */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void adapterSavedWithoutBlockIdsKeepsTheAddress(final GameTestHelper helper) {
    registerSecondDriver();
    final BlockPos device = AT.east();
    final BlockPos deviceAbs = helper.absolutePos(device);
    placeWithCapability(helper, device, energyProvider(), "test_energy_old");
    helper.setBlock(AT, Blocks.ADAPTER.get());
    helper.runAfterDelay(10, () -> {
      final var adapter = (li.cil.oc.core.impl.common.blockentity.Adapter) helper.getBlockEntity(AT);
      adapter.neighborChanged(Direction.EAST);
      final String before = blockAddress(adapter);
      final var tag = adapter.saveWithFullMetadata();
      final var blocks = tag.getList(li.cil.oc.core.impl.OCSettings.namespace + "adapter.blocks", net.minecraft.nbt.Tag.TAG_COMPOUND);
      for (int i = 0; i < blocks.size(); i++) {
        blocks.getCompound(i).remove("block");
        blocks.getCompound(i).remove("identity");
      }
      final BlockPos abs = helper.absolutePos(AT);
      helper.getLevel().removeBlockEntity(abs);
      SECOND_DRIVER_AT.add(deviceAbs); // other drivers now
      final var loaded = (li.cil.oc.core.impl.common.blockentity.Adapter) BlockEntity.loadStatic(abs, helper.getLevel().getBlockState(abs), tag);
      helper.getLevel().getChunkAt(abs).addAndRegisterBlockEntity(loaded);
      helper.runAfterDelay(10, () -> {
        final String after;
        final int drivers;
        try {
          loaded.neighborChanged(Direction.EAST);
          after = blockAddress(loaded);
          drivers = blockDrivers(loaded);
        } finally {
          SECOND_DRIVER_AT.remove(deviceAbs);
        }
        helper.assertTrue(drivers == 2, "the test driver did not come along: " + drivers + " drivers");
        helper.assertTrue(before != null && before.equals(after), "an old adapter gave the block still there " + after + " instead of " + before);
        helper.succeed();
      });
    });
  }

  /** A block placed straight over another (no air between) is a device of its own (the live path kept the old address). */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void adapterGivesABlockPlacedOverAnotherItsOwnAddress(final GameTestHelper helper) {
    final BlockPos device = AT.east();
    final ICapabilityProvider provider = energyProvider();
    placeWithCapability(helper, device, provider, "test_energy_over");
    helper.setBlock(AT, Blocks.ADAPTER.get());
    helper.runAfterDelay(10, () -> {
      final var adapter = (li.cil.oc.core.impl.common.blockentity.Adapter) helper.getBlockEntity(AT);
      adapter.neighborChanged(Direction.EAST);
      final String table = blockAddress(adapter);
      placeWithCapability(helper, device, provider, "test_energy_over", net.minecraft.world.level.block.Blocks.BELL);
      adapter.neighborChanged(Direction.EAST);
      final String bell = blockAddress(adapter);
      helper.assertTrue(table != null && bell != null && !bell.equals(table), "a bell placed over an enchanting table of the same drivers got its address " + bell);
      helper.succeed();
    });
  }

  /**
   * Another device in the same block gets an address of its own even when both have energy (generic drivers tell
   * nothing: two machines behind the same bounding block both named energy_device too).
   */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void adapterTellsDevicesApartByTheirOwnNames(final GameTestHelper helper) {
    registerSecondDriver();
    final BlockPos device = AT.east();
    final BlockPos deviceAbs = helper.absolutePos(device);
    placeWithCapability(helper, device, energyProvider(), "test_energy_devices");
    helper.setBlock(AT, Blocks.ADAPTER.get());
    helper.runAfterDelay(10, () -> {
      final var adapter = (li.cil.oc.core.impl.common.blockentity.Adapter) helper.getBlockEntity(AT);
      final String one;
      final String other;
      try {
        NAMED_DRIVER_AT.add(deviceAbs);
        adapter.neighborChanged(Direction.EAST);
        one = blockAddress(adapter);
        NAMED_DRIVER_AT.remove(deviceAbs);
        OTHER_DRIVER_AT.add(deviceAbs);
        adapter.neighborChanged(Direction.EAST);
        other = blockAddress(adapter);
      } finally {
        NAMED_DRIVER_AT.remove(deviceAbs);
        OTHER_DRIVER_AT.remove(deviceAbs);
      }
      helper.assertTrue(one != null && other != null && !other.equals(one), "another device with energy in the same block got its address " + other);
      helper.succeed();
    });
  }

  /**
   * A block made up of generic parts alone keeps its address when they change, as a machine whose side goes from energy
   * to items (no name on either side told them apart: it got a new address).
   */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void adapterKeepsTheAddressWhenGenericPartsChange(final GameTestHelper helper) {
    registerSecondDriver();
    final BlockPos device = AT.east();
    final BlockPos deviceAbs = helper.absolutePos(device);
    final boolean[] energyOn = {true};
    final var storage = new net.minecraftforge.energy.EnergyStorage(1000);
    final LazyOptional<net.minecraftforge.energy.IEnergyStorage> energy = LazyOptional.of(() -> storage);
    placeWithCapability(helper, device, new ICapabilityProvider() {
      @Override
      public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return cap == ForgeCapabilities.ENERGY && energyOn[0] ? energy.cast() : LazyOptional.empty();
      }
    }, "test_energy_generic");
    helper.setBlock(AT, Blocks.ADAPTER.get());
    helper.runAfterDelay(10, () -> {
      final var adapter = (li.cil.oc.core.impl.common.blockentity.Adapter) helper.getBlockEntity(AT);
      adapter.neighborChanged(Direction.EAST);
      final String withEnergy = blockAddress(adapter);
      final String withOther;
      final int drivers;
      GENERIC_DRIVER_AT.add(deviceAbs);
      energyOn[0] = false;
      try {
        adapter.neighborChanged(Direction.EAST);
        withOther = blockAddress(adapter);
        drivers = blockDrivers(adapter);
      } finally {
        GENERIC_DRIVER_AT.remove(deviceAbs);
      }
      helper.assertTrue(drivers == 1, "the generic test driver did not take energy's place: " + drivers + " drivers");
      helper.assertTrue(withEnergy != null && withEnergy.equals(withOther), "with another generic part the block got " + withOther + " instead of " + withEnergy);
      helper.succeed();
    });
  }

  /**
   * A driver with a name of its own coming, going and coming again keeps the block's address (the block's name
   * changed: it got a new address).
   */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void adapterKeepsTheAddressWhenANamedDriverComesAndGoes(final GameTestHelper helper) {
    registerSecondDriver();
    final BlockPos device = AT.east();
    final BlockPos deviceAbs = helper.absolutePos(device);
    placeWithCapability(helper, device, energyProvider(), "test_energy_named");
    helper.setBlock(AT, Blocks.ADAPTER.get());
    helper.runAfterDelay(10, () -> {
      final var adapter = (li.cil.oc.core.impl.common.blockentity.Adapter) helper.getBlockEntity(AT);
      adapter.neighborChanged(Direction.EAST);
      final String before = blockAddress(adapter);
      final String named;
      final String alone;
      final String again;
      try {
        NAMED_DRIVER_AT.add(deviceAbs);
        adapter.neighborChanged(Direction.EAST);
        named = blockAddress(adapter);
        NAMED_DRIVER_AT.remove(deviceAbs);
        adapter.neighborChanged(Direction.EAST);
        alone = blockAddress(adapter);
        NAMED_DRIVER_AT.add(deviceAbs);
        adapter.neighborChanged(Direction.EAST);
        again = blockAddress(adapter);
      } finally {
        NAMED_DRIVER_AT.remove(deviceAbs);
      }
      helper.assertTrue(before != null && before.equals(named), "with a named driver coming along the block got " + named + " instead of " + before);
      helper.assertTrue(before.equals(alone), "with the named driver gone the block got " + alone + " instead of " + before);
      helper.assertTrue(before.equals(again), "with the named driver back the block got " + again + " instead of " + before);
      helper.succeed();
    });
  }

  /**
   * An adapter saved before it recorded the blocks gives the old block's address only to the same set of drivers, as OC
   * did (any block placed there took it: a furnace became the enchanting table's component).
   */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void adapterSavedBeforeRecordingBlocksFollowsOC(final GameTestHelper helper) {
    final BlockPos device = AT.east();
    final ICapabilityProvider provider = energyProvider();
    placeWithCapability(helper, device, provider, "test_energy_unrecorded");
    helper.setBlock(AT, Blocks.ADAPTER.get());
    helper.runAfterDelay(10, () -> {
      final var adapter = (li.cil.oc.core.impl.common.blockentity.Adapter) helper.getBlockEntity(AT);
      adapter.neighborChanged(Direction.EAST);
      final String table = blockAddress(adapter);
      helper.setBlock(device, net.minecraft.world.level.block.Blocks.AIR);
      adapter.neighborChanged(Direction.EAST); // the table's data stays with the adapter
      final var tag = adapter.saveWithFullMetadata();
      final var blocks = tag.getList(li.cil.oc.core.impl.OCSettings.namespace + "adapter.blocks", net.minecraft.nbt.Tag.TAG_COMPOUND);
      for (int i = 0; i < blocks.size(); i++) {
        blocks.getCompound(i).remove("block");
        blocks.getCompound(i).remove("identity");
      }
      final BlockPos abs = helper.absolutePos(AT);
      helper.getLevel().removeBlockEntity(abs);
      final var loaded = (li.cil.oc.core.impl.common.blockentity.Adapter) BlockEntity.loadStatic(abs, helper.getLevel().getBlockState(abs), tag);
      helper.getLevel().getChunkAt(abs).addAndRegisterBlockEntity(loaded);
      helper.runAfterDelay(10, () -> {
        helper.setBlock(device, net.minecraft.world.level.block.Blocks.FURNACE);
        loaded.neighborChanged(Direction.EAST);
        final String furnace = blockAddress(loaded);
        helper.assertTrue(table != null && furnace != null && !furnace.equals(table), "a furnace placed where an unrecorded enchanting table was got its address " + furnace);
        helper.succeed();
      });
    });
  }

  /** The unrecorded old adapter of adapterSavedBeforeRecordingBlocksFollowsOC, with the table's place left in the given state. */
  private static void reloadUnrecorded(final GameTestHelper helper, final BlockPos device, final net.minecraft.world.level.block.state.BlockState left,
                                       final java.util.function.BiConsumer<String, li.cil.oc.core.impl.common.blockentity.Adapter> then) {
    placeWithCapability(helper, device, energyProvider(), "test_energy_unrecorded_" + left.getBlock().getDescriptionId());
    helper.setBlock(AT, Blocks.ADAPTER.get());
    helper.runAfterDelay(10, () -> {
      final var adapter = (li.cil.oc.core.impl.common.blockentity.Adapter) helper.getBlockEntity(AT);
      adapter.neighborChanged(Direction.EAST);
      final String table = blockAddress(adapter);
      helper.setBlock(device, net.minecraft.world.level.block.Blocks.AIR);
      adapter.neighborChanged(Direction.EAST); // the table's data stays with the adapter
      helper.setBlock(device, left);
      final var tag = adapter.saveWithFullMetadata();
      final var blocks = tag.getList(li.cil.oc.core.impl.OCSettings.namespace + "adapter.blocks", net.minecraft.nbt.Tag.TAG_COMPOUND);
      for (int i = 0; i < blocks.size(); i++) {
        blocks.getCompound(i).remove("block");
        blocks.getCompound(i).remove("identity");
      }
      tag.remove(li.cil.oc.core.impl.OCSettings.namespace + "adapter.sideChanged"); // as older builds wrote it
      final BlockPos abs = helper.absolutePos(AT);
      helper.getLevel().removeBlockEntity(abs);
      final var loaded = (li.cil.oc.core.impl.common.blockentity.Adapter) BlockEntity.loadStatic(abs, helper.getLevel().getBlockState(abs), tag);
      helper.getLevel().getChunkAt(abs).addAndRegisterBlockEntity(loaded);
      helper.runAfterDelay(10, () -> then.accept(table, loaded));
    });
  }

  /**
   * A block placed straight into what the old one left (water, snow: no air between) is not the block the unrecorded
   * data was saved with (only air counted as a change: it got the old block's address).
   */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void unrecordedAdapterDataGoesNotToABlockPlacedIntoWhatWasLeft(final GameTestHelper helper) {
    final BlockPos device = AT.east();
    reloadUnrecorded(helper, device, net.minecraft.world.level.block.Blocks.STRUCTURE_VOID.defaultBlockState(), (table, loaded) -> {
      loaded.neighborChanged(Direction.EAST); // it looks at what is left
      helper.setBlock(device, net.minecraft.world.level.block.Blocks.FURNACE);
      loaded.neighborChanged(Direction.EAST);
      final String furnace = blockAddress(loaded);
      helper.assertTrue(table != null && furnace != null && !furnace.equals(table), "a furnace placed into what the table left got its address " + furnace);
      helper.succeed();
    });
  }

  /**
   * An unrecorded side seen changed stays changed across a save while nothing attaches there (a side closed): the block
   * there after the next load is not the one the data was saved with (it got its address).
   */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void unrecordedAdapterDataRemembersAChangedSide(final GameTestHelper helper) {
    final BlockPos device = AT.east();
    reloadUnrecorded(helper, device, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), (table, loaded) -> {
      loaded.neighborChanged(Direction.EAST); // seen empty
      loaded.setSideOpen(Direction.EAST, false);
      helper.setBlock(device, net.minecraft.world.level.block.Blocks.FURNACE);
      loaded.neighborChanged(Direction.EAST); // closed: nothing attaches, the data stays unrecorded
      final var tag = loaded.saveWithFullMetadata();
      final BlockPos abs = helper.absolutePos(AT);
      helper.getLevel().removeBlockEntity(abs);
      final var again = (li.cil.oc.core.impl.common.blockentity.Adapter) BlockEntity.loadStatic(abs, helper.getLevel().getBlockState(abs), tag);
      helper.getLevel().getChunkAt(abs).addAndRegisterBlockEntity(again);
      helper.runAfterDelay(10, () -> {
        again.setSideOpen(Direction.EAST, true);
        final String furnace = blockAddress(again);
        helper.assertTrue(table != null && furnace != null && !furnace.equals(table), "a furnace placed while the side was closed got the old table's address " + furnace);
        helper.succeed();
      });
    });
  }

  private static boolean saveMarked(final li.cil.oc.core.impl.common.blockentity.Adapter adapter, final boolean clear) {
    try {
      final var field = li.cil.oc.core.impl.common.blockentity.traits.BlockEntity.class.getDeclaredField("changeScheduled");
      field.setAccessible(true);
      final var mark = (java.util.concurrent.atomic.AtomicBoolean) field.get(adapter);
      return clear ? mark.getAndSet(false) : mark.get();
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * An adapter attaching or letting go of a block marks itself to be saved (it never did: with the block in another
   * chunk nothing marked the adapter's, and the block's address was lost with the chunk).
   */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void adapterMarksItselfToBeSavedWhenItsSidesChange(final GameTestHelper helper) {
    final BlockPos device = AT.east();
    helper.setBlock(AT, Blocks.ADAPTER.get());
    helper.runAfterDelay(10, () -> {
      final var adapter = (li.cil.oc.core.impl.common.blockentity.Adapter) helper.getBlockEntity(AT);
      adapter.neighborChanged(Direction.EAST);
      saveMarked(adapter, true);
      placeWithCapability(helper, device, energyProvider(), "test_energy_marked");
      adapter.neighborChanged(Direction.EAST);
      final boolean attached = saveMarked(adapter, true);
      placeWithCapability(helper, device, energyProvider(), "test_energy_marked", net.minecraft.world.level.block.Blocks.BELL);
      adapter.neighborChanged(Direction.EAST);
      final boolean replaced = saveMarked(adapter, true);
      helper.setBlock(device, net.minecraft.world.level.block.Blocks.AIR);
      adapter.neighborChanged(Direction.EAST);
      final boolean detached = saveMarked(adapter, true);
      helper.assertTrue(attached, "attaching a block did not mark the adapter to be saved");
      helper.assertTrue(replaced, "a block replaced straight by another did not mark the adapter to be saved");
      helper.assertTrue(detached, "letting go of a block did not mark the adapter to be saved");
      helper.succeed();
    });
  }

  /** An OC block swapped straight for another block counts as a change too (OC neighbours were not looked at). */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void unrecordedAdapterDataGoesNotToABlockSwappedForAnOCBlock(final GameTestHelper helper) {
    final BlockPos device = AT.east();
    reloadUnrecorded(helper, device, Blocks.CABLE.get().defaultBlockState(), (table, loaded) -> {
      loaded.neighborChanged(Direction.EAST); // it looks at the cable
      helper.setBlock(device, net.minecraft.world.level.block.Blocks.FURNACE);
      loaded.neighborChanged(Direction.EAST);
      final String furnace = blockAddress(loaded);
      helper.assertTrue(table != null && furnace != null && !furnace.equals(table), "a furnace swapped for a cable where the table was got its address " + furnace);
      helper.succeed();
    });
  }

  /**
   * Unrecorded data on a closed side, its block left in place, keeps the block's address through a save and a load (the
   * other half of unrecordedAdapterDataRemembersAChangedSide: nothing marks an unchanged side changed).
   */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void unrecordedAdapterDataOnAClosedSideKeepsItsBlock(final GameTestHelper helper) {
    final BlockPos device = AT.east();
    placeWithCapability(helper, device, energyProvider(), "test_energy_closed");
    helper.setBlock(AT, Blocks.ADAPTER.get());
    helper.runAfterDelay(10, () -> {
      final var adapter = (li.cil.oc.core.impl.common.blockentity.Adapter) helper.getBlockEntity(AT);
      adapter.neighborChanged(Direction.EAST);
      final String table = blockAddress(adapter);
      adapter.setSideOpen(Direction.EAST, false); // the table's data stays with the adapter, the table in place
      final var tag = adapter.saveWithFullMetadata();
      final var blocks = tag.getList(li.cil.oc.core.impl.OCSettings.namespace + "adapter.blocks", net.minecraft.nbt.Tag.TAG_COMPOUND);
      for (int i = 0; i < blocks.size(); i++) {
        blocks.getCompound(i).remove("block");
        blocks.getCompound(i).remove("identity");
      }
      tag.remove(li.cil.oc.core.impl.OCSettings.namespace + "adapter.sideChanged");
      final BlockPos abs = helper.absolutePos(AT);
      helper.getLevel().removeBlockEntity(abs);
      final var loaded = (li.cil.oc.core.impl.common.blockentity.Adapter) BlockEntity.loadStatic(abs, helper.getLevel().getBlockState(abs), tag);
      helper.getLevel().getChunkAt(abs).addAndRegisterBlockEntity(loaded);
      helper.runAfterDelay(10, () -> {
        loaded.neighborChanged(Direction.EAST); // closed: it only looks
        final var again = loaded.saveWithFullMetadata();
        helper.getLevel().removeBlockEntity(abs);
        final var reloaded = (li.cil.oc.core.impl.common.blockentity.Adapter) BlockEntity.loadStatic(abs, helper.getLevel().getBlockState(abs), again);
        helper.getLevel().getChunkAt(abs).addAndRegisterBlockEntity(reloaded);
        helper.runAfterDelay(10, () -> {
          reloaded.setSideOpen(Direction.EAST, true);
          final String after = blockAddress(reloaded);
          helper.assertTrue(table != null && table.equals(after), "the table that stayed got " + after + " instead of " + table);
          helper.succeed();
        });
      });
    });
  }

  /** A side whose block changes with nothing attached there (closed) marks the adapter to be saved too. */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void adapterMarksItselfToBeSavedWhenAClosedSideChanges(final GameTestHelper helper) {
    final BlockPos device = AT.east();
    placeWithCapability(helper, device, energyProvider(), "test_energy_closed_mark");
    helper.setBlock(AT, Blocks.ADAPTER.get());
    helper.runAfterDelay(10, () -> {
      final var adapter = (li.cil.oc.core.impl.common.blockentity.Adapter) helper.getBlockEntity(AT);
      adapter.neighborChanged(Direction.EAST);
      adapter.setSideOpen(Direction.EAST, false);
      saveMarked(adapter, true);
      helper.setBlock(device, net.minecraft.world.level.block.Blocks.FURNACE);
      adapter.neighborChanged(Direction.EAST);
      helper.assertTrue(saveMarked(adapter, false), "a closed side whose block changed did not mark the adapter to be saved");
      helper.succeed();
    });
  }

  /** What a database upgrade in an adapter is told to hold marks the adapter to be saved (its entries were lost). */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void adapterDatabaseWritesAreSaved(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.ADAPTER.get());
    helper.runAfterDelay(10, () -> {
      final var adapter = (li.cil.oc.core.impl.common.blockentity.Adapter) helper.getBlockEntity(AT);
      adapter.setItem(0, li.cil.oc.api.Items.get(li.cil.oc.core.Constants.ItemName.DatabaseUpgradeTier1).createItemStack(1));
      helper.runAfterDelay(5, () -> {
        li.cil.oc.core.impl.server.component.UpgradeDatabase database = null;
        for (final var env : adapter.componentEnvironments()) {
          if (env instanceof li.cil.oc.core.impl.server.component.UpgradeDatabase db) database = db;
        }
        helper.assertTrue(database != null, "the adapter made no database");
        saveMarked(adapter, true);
        database.setStackInSlot(0, new ItemStack(Items.STONE));
        helper.assertTrue(saveMarked(adapter, false), "a database entry written did not mark the adapter to be saved");
        helper.succeed();
      });
    });
  }

  /**
   * An MFU keeps its block's address through block updates there and across a load (a furnace lighting up gave it a new
   * one; so did every load).
   */
  @GameTest(template = EMPTY, timeoutTicks = 150)
  public static void mfuKeepsItsBlocksAddress(final GameTestHelper helper) {
    final BlockPos adapterPos = new BlockPos(1, 2, 2);
    final BlockPos furnacePos = new BlockPos(3, 2, 2);
    helper.setBlock(furnacePos, net.minecraft.world.level.block.Blocks.FURNACE);
    helper.setBlock(adapterPos, Blocks.ADAPTER.get());
    helper.setBlock(adapterPos.below(), Blocks.CAPACITOR.get()); // the MFU draws energy
    helper.runAfterDelay(10, () -> {
      final var capacitor = (li.cil.oc.api.network.Environment) helper.getBlockEntity(adapterPos.below());
      ((li.cil.oc.api.network.Connector) capacitor.node()).changeBuffer(100000);
      final var adapter = (li.cil.oc.core.impl.common.blockentity.Adapter) helper.getBlockEntity(adapterPos);
      final BlockPos abs = helper.absolutePos(furnacePos);
      final var mfu = li.cil.oc.api.Items.get(li.cil.oc.core.Constants.ItemName.MFU).createItemStack(1);
      final var tag = new net.minecraft.nbt.CompoundTag();
      tag.putIntArray(li.cil.oc.core.impl.OCSettings.namespace + "coord",
        new int[]{abs.getX(), abs.getY(), abs.getZ(), helper.getLevel().dimension().location().hashCode(), Direction.UP.ordinal()});
      li.cil.oc.compat.CustomData.set(mfu, li.cil.oc.compat.CustomData.of(tag));
      adapter.setItem(0, mfu);
      helper.runAfterDelay(5, () -> {
        final String first = blockAddress(adapter);
        final var furnace = helper.getBlockState(furnacePos);
        helper.setBlock(furnacePos, furnace.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT, true));
        helper.runAfterDelay(2, () -> {
          final String lit = blockAddress(adapter);
          final var saved = adapter.saveWithFullMetadata();
          final BlockPos at = helper.absolutePos(adapterPos);
          helper.getLevel().removeBlockEntity(at);
          final var loaded = (li.cil.oc.core.impl.common.blockentity.Adapter) BlockEntity.loadStatic(at, helper.getLevel().getBlockState(at), saved);
          helper.getLevel().getChunkAt(at).addAndRegisterBlockEntity(loaded);
          TestMachines.when(helper, () -> blockAddress(loaded) != null, 50, () -> {
            final String reloaded = blockAddress(loaded);
            helper.assertTrue(first != null && first.equals(lit), "the MFU's furnace got " + lit + " instead of " + first + " when it lit up");
            helper.assertTrue(first.equals(reloaded), "the MFU's furnace got " + reloaded + " instead of " + first + " after a load");
            helper.succeed();
          });
        });
      });
    });
  }

  private static final BlockPos MFU_ADAPTER = new BlockPos(1, 2, 2);
  private static final BlockPos MFU_FURNACE = new BlockPos(3, 2, 2);

  /** An adapter with an MFU bound to a furnace two blocks away (powered by a capacitor below if asked). */
  private static void mfuBoundToAFurnace(final GameTestHelper helper, final boolean powered,
                                         final java.util.function.Consumer<li.cil.oc.core.impl.common.blockentity.Adapter> then) {
    helper.setBlock(MFU_FURNACE, net.minecraft.world.level.block.Blocks.FURNACE);
    helper.setBlock(MFU_ADAPTER, Blocks.ADAPTER.get());
    if (powered) helper.setBlock(MFU_ADAPTER.below(), Blocks.CAPACITOR.get());
    helper.runAfterDelay(10, () -> {
      if (powered) {
        final var capacitor = (li.cil.oc.api.network.Environment) helper.getBlockEntity(MFU_ADAPTER.below());
        ((li.cil.oc.api.network.Connector) capacitor.node()).changeBuffer(100000);
      }
      final var adapter = (li.cil.oc.core.impl.common.blockentity.Adapter) helper.getBlockEntity(MFU_ADAPTER);
      final BlockPos abs = helper.absolutePos(MFU_FURNACE);
      final var mfu = li.cil.oc.api.Items.get(li.cil.oc.core.Constants.ItemName.MFU).createItemStack(1);
      final var tag = new net.minecraft.nbt.CompoundTag();
      tag.putIntArray(li.cil.oc.core.impl.OCSettings.namespace + "coord",
        new int[]{abs.getX(), abs.getY(), abs.getZ(), helper.getLevel().dimension().location().hashCode(), Direction.UP.ordinal()});
      li.cil.oc.compat.CustomData.set(mfu, li.cil.oc.compat.CustomData.of(tag));
      adapter.setItem(0, mfu);
      then.accept(adapter);
    });
  }

  private static li.cil.oc.api.network.Node blockNode(final li.cil.oc.core.impl.common.blockentity.Adapter adapter) {
    for (final var node : adapter.node().reachableNodes()) {
      if (node.host() instanceof li.cil.oc.core.impl.server.driver.CompoundBlockEnvironment) return node;
    }
    return null;
  }

  /** An MFU lets go of its removed block (it threw: the field it used was cleared by the disconnect) and takes it back with its address. */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void mfuLetsGoOfARemovedBlockAndTakesItBack(final GameTestHelper helper) {
    mfuBoundToAFurnace(helper, true, adapter -> helper.runAfterDelay(5, () -> {
      final var first = blockNode(adapter);
      helper.assertTrue(first != null, "the MFU bound nothing");
      saveMarked(adapter, true);
      helper.setBlock(MFU_FURNACE, net.minecraft.world.level.block.Blocks.AIR);
      helper.assertTrue(blockNode(adapter) == null && first.network() == null, "the removed furnace's node is still there");
      helper.assertTrue(saveMarked(adapter, true), "letting go did not mark the adapter to be saved");
      helper.setBlock(MFU_FURNACE, net.minecraft.world.level.block.Blocks.FURNACE);
      final var again = blockNode(adapter);
      helper.assertTrue(again != null && first.address().equals(again.address()), "the furnace put back got " + (again == null ? null : again.address()) + " instead of " + first.address());
      helper.succeed();
    }));
  }

  /** An MFU's block whose drivers change is made anew (it threw), as another block. */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void mfuSurvivesItsBlocksDriversChanging(final GameTestHelper helper) {
    registerSecondDriver();
    mfuBoundToAFurnace(helper, true, adapter -> helper.runAfterDelay(5, () -> {
      final var first = blockNode(adapter);
      final BlockPos abs = helper.absolutePos(MFU_FURNACE);
      SECOND_DRIVER_AT.add(abs);
      try {
        helper.setBlock(MFU_FURNACE, helper.getBlockState(MFU_FURNACE).setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT, true));
      } finally {
        SECOND_DRIVER_AT.remove(abs);
      }
      final var then = blockNode(adapter);
      helper.assertTrue(first != null && first.network() == null, "the old environment's node is still in a network");
      helper.assertTrue(then != null && then != first, "the MFU has no environment for the furnace's new drivers");
      helper.succeed();
    }));
  }

  /** An MFU out of energy lets go of its block (it threw) and takes it back with its address once powered. */
  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void mfuLetsGoWithoutEnergyAndTakesItBack(final GameTestHelper helper) {
    mfuBoundToAFurnace(helper, false, adapter -> {
      final var first = blockNode(adapter);
      helper.assertTrue(first != null, "the MFU bound nothing");
      helper.runAfterDelay(30, () -> {
        helper.assertTrue(blockNode(adapter) == null && first.network() == null, "the MFU kept its block without energy");
        helper.setBlock(MFU_ADAPTER.below(), Blocks.CAPACITOR.get());
        helper.runAfterDelay(5, () -> {
          final var capacitor = (li.cil.oc.api.network.Environment) helper.getBlockEntity(MFU_ADAPTER.below());
          ((li.cil.oc.api.network.Connector) capacitor.node()).changeBuffer(100000);
          helper.setBlock(MFU_FURNACE, helper.getBlockState(MFU_FURNACE).setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT, true));
          final var again = blockNode(adapter);
          helper.assertTrue(again != null && first.address().equals(again.address()), "the powered MFU gave the furnace " + (again == null ? null : again.address()) + " instead of " + first.address());
          helper.runAfterDelay(25, () -> { // powered now: it keeps the block past its energy beats
            final var still = blockNode(adapter);
            helper.assertTrue(still != null && first.address().equals(still.address()), "the powered MFU let go of its furnace again");
            helper.succeed();
          });
        });
      });
    });
  }

  /** An MFU follows its block's new block entity, keeping the address (it held on to the removed one). */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void mfuFollowsAReplacedBlockEntity(final GameTestHelper helper) {
    mfuBoundToAFurnace(helper, true, adapter -> helper.runAfterDelay(5, () -> {
      final var first = blockNode(adapter);
      final BlockPos abs = helper.absolutePos(MFU_FURNACE);
      helper.getLevel().removeBlockEntity(abs);
      final var fresh = new net.minecraft.world.level.block.entity.FurnaceBlockEntity(abs, helper.getLevel().getBlockState(abs));
      helper.getLevel().setBlockEntity(fresh);
      helper.getLevel().updateNeighborsAt(abs, helper.getLevel().getBlockState(abs).getBlock());
      final var then = blockNode(adapter);
      Object held = null;
      try {
        final var field = li.cil.oc.core.impl.integration.ManagedBlockEntityEnvironment.class.getDeclaredField("BlockEntity");
        field.setAccessible(true);
        for (final var env : ((li.cil.oc.core.impl.server.driver.CompoundBlockEnvironment) then.host()).environments()) {
          if (env instanceof li.cil.oc.core.impl.integration.ManagedBlockEntityEnvironment<?>) held = field.get(env);
        }
      } catch (ReflectiveOperationException e) {
        throw new RuntimeException(e);
      }
      helper.assertTrue(held == fresh, "the MFU's furnace environment holds " + held + ", not the furnace's block entity now");
      helper.assertTrue(first.address().equals(then.address()), "the furnace got " + then.address() + " instead of " + first.address());
      helper.succeed();
    }));
  }

  private static Object heldBlockEntity(final li.cil.oc.api.network.Node node) {
    try {
      final var field = li.cil.oc.core.impl.integration.ManagedBlockEntityEnvironment.class.getDeclaredField("BlockEntity");
      field.setAccessible(true);
      for (final var env : ((li.cil.oc.core.impl.server.driver.CompoundBlockEnvironment) node.host()).environments()) {
        if (env instanceof li.cil.oc.core.impl.integration.ManagedBlockEntityEnvironment<?>) return field.get(env);
      }
      return null;
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * An MFU follows the new block entity its block's chunk comes back with, keeping the address, though nothing updates
   * the block (it kept the removed one until the block next changed).
   */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void mfuFollowsABlockEntityReloadedWithItsChunk(final GameTestHelper helper) {
    mfuBoundToAFurnace(helper, true, adapter -> helper.runAfterDelay(5, () -> {
      final var first = blockNode(adapter);
      final BlockPos abs = helper.absolutePos(MFU_FURNACE);
      helper.getLevel().removeBlockEntity(abs); // what a chunk unload and load does, without a block update
      final var fresh = new net.minecraft.world.level.block.entity.FurnaceBlockEntity(abs, helper.getLevel().getBlockState(abs));
      helper.getLevel().setBlockEntity(fresh);
      TestMachines.when(helper, () -> blockNode(adapter) != null && heldBlockEntity(blockNode(adapter)) == fresh, 40, () -> {
        final var then = blockNode(adapter);
        helper.assertTrue(then != null && heldBlockEntity(then) == fresh, "the MFU's furnace environment holds the removed block entity");
        helper.assertTrue(first.address().equals(then.address()), "the furnace got " + then.address() + " instead of " + first.address());
        helper.succeed();
      });
    }));
  }

  /**
   * An adapter whose node leaves its network and joins again gets its MFU's block back with its address, and ticks its
   * parts again (the block's node was orphaned and the MFU thought it still bound; the MFU no longer ticked).
   */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void mfuTakesItsBlockBackWhenTheAdapterRejoins(final GameTestHelper helper) {
    mfuBoundToAFurnace(helper, true, adapter -> helper.runAfterDelay(5, () -> {
      final var first = blockNode(adapter);
      helper.assertTrue(first != null, "the MFU bound nothing");
      adapter.node().remove();
      li.cil.oc.api.Network.joinOrCreateNetwork(adapter);
      final var again = blockNode(adapter);
      helper.assertTrue(again != null && first.address().equals(again.address()), "after the rejoin the furnace is " + (again == null ? null : again.address()) + " instead of " + first.address());
      boolean ticking = false;
      for (final var component : adapter.componentEnvironments()) {
        if (component instanceof li.cil.oc.core.impl.server.component.UpgradeMFBase && adapter.updatingComponents().contains(component)) ticking = true;
      }
      helper.assertTrue(ticking, "the adapter no longer ticks its MFU after the rejoin");
      helper.succeed();
    }));
  }
}
