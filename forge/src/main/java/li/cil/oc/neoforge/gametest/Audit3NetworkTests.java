package li.cil.oc.neoforge.gametest;

import static li.cil.oc.neoforge.gametest.TestMachines.when;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IAEPowerStorage;
import java.util.Comparator;
import java.util.UUID;
import li.cil.oc.api.network.Connector;
import li.cil.oc.core.Constants;
import li.cil.oc.core.impl.OCSettings;
import li.cil.oc.core.impl.common.blockentity.Capacitor;
import li.cil.oc.core.impl.common.blockentity.Case;
import li.cil.oc.core.impl.common.blockentity.Charger;
import li.cil.oc.core.impl.common.blockentity.Relay;
import li.cil.oc.core.impl.integration.opencomputers.Item;
import li.cil.oc.core.impl.integration.util.Power;
import li.cil.oc.core.impl.server.machine.ArgumentsImpl;
import li.cil.oc.neoforge.OpenComputers;
import li.cil.oc.neoforge.common.init.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Relays, racks, energy and capabilities (third audit, network and power): parts keep their own state, side networks
 * share energy, no energy is made or lost, no chunk is loaded early and nothing is left behind by a block that went.
 */
@GameTestHolder(OpenComputers.ID)
@PrefixGameTestTemplate(false)
public final class Audit3NetworkTests {
  private static final String EMPTY = "empty";
  private static final BlockPos AT = new BlockPos(2, 2, 2);
  private static final String NS = OCSettings.namespace;
  // the relay's slots: CPU, memory, drive, card
  private static final int CPU_SLOT = 0, MEMORY_SLOT = 1, HDD_SLOT = 2, CARD_SLOT = 3;
  private static final TicketType<ChunkPos> LOADING = TicketType.create("oc_test_loading", Comparator.comparingLong(ChunkPos::toLong));

  private Audit3NetworkTests() {
  }

  private static ItemStack item(final String name) {
    return li.cil.oc.api.Items.get(name).createItemStack(1);
  }

  private static ItemStack linkedCard(final String tunnel) {
    final ItemStack card = item(Constants.ItemName.LinkedCard);
    Item.getDataTag(card).putString(NS + "tunnel", tunnel);
    return card;
  }

  private static String tunnel(final ItemStack card) {
    return Item.peekDataTag(card).getString(NS + "tunnel");
  }

  private static ItemStack drive(final String address) {
    final ItemStack drive = item(Constants.ItemName.HDDTier1);
    final CompoundTag node = new CompoundTag();
    node.putString("address", address);
    Item.getDataTag(drive).put("node", node);
    return drive;
  }

  private static Connector connector(final li.cil.oc.api.network.Environment environment) {
    return (Connector) environment.node();
  }

  /** Runs then once ready holds or after the seconds, counted while the server ticks (generating chunks takes real time). */
  private static void within(final GameTestHelper helper, final double seconds, final java.util.function.BooleanSupplier ready, final Runnable then) {
    final long[] clock = {System.nanoTime(), 0}; // last tick, time counted
    when(helper, () -> {
      final long now = System.nanoTime();
      clock[1] += Math.min(now - clock[0], 50_000_000L);
      clock[0] = now;
      return ready.getAsBoolean() || clock[1] > seconds * 1e9;
    }, 10_000_000, then);
  }

  private static net.minecraft.world.level.block.Block ae2Block(final String name) {
    return ForgeRegistries.BLOCKS.getValue(new ResourceLocation("ae2", name));
  }

  /**
   * A relay's part taken out keeps its state, and the one put in keeps its own (the relay made its parts' environments
   * only when a plug connected, and saved the one taken out's into whatever went in the slot next: a linked card got the
   * tunnel of the card before it, a drive the address of the drive before it).
   */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void relayPartsKeepTheirOwnState(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.RELAY.get());
    final var relay = (Relay) helper.getBlockEntity(AT);
    final String tunnelA = "a-" + UUID.randomUUID(), tunnelB = "b-" + UUID.randomUUID();
    final String addressA = UUID.randomUUID().toString(), addressB = UUID.randomUUID().toString();
    relay.setItem(HDD_SLOT, drive(addressA));
    relay.setItem(CARD_SLOT, linkedCard(tunnelA));
    helper.runAfterDelay(3, () -> { // its plugs connected
      final ItemStack cardA = relay.removeItem(CARD_SLOT, 1);
      final ItemStack driveA = relay.removeItem(HDD_SLOT, 1);
      relay.setItem(CARD_SLOT, linkedCard(tunnelB));
      relay.setItem(HDD_SLOT, drive(addressB));
      relay.saveWithFullMetadata(); // a world save
      final String inCard = tunnel(relay.getItem(CARD_SLOT)), inDrive = Item.address(relay.getItem(HDD_SLOT));
      helper.assertTrue(tunnelB.equals(inCard), "the linked card put in has the tunnel " + inCard + ", not its own");
      helper.assertTrue(addressB.equals(inDrive), "the drive put in has the address " + inDrive + ", not its own");
      helper.assertTrue(tunnelA.equals(tunnel(cardA)), "the linked card taken out has the tunnel " + tunnel(cardA) + ", not its own");
      helper.assertTrue(addressA.equals(Item.address(driveA)), "the drive taken out has the address " + Item.address(driveA) + ", not its own");
      helper.succeed();
    });
  }

  /** A relay set to strength 0 stays at 0 as parts go in (any part put in set it to the wireless card's range). */
  @GameTest(template = EMPTY)
  public static void relayStrengthSetTo0Stays(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.RELAY.get());
    final var relay = (Relay) helper.getBlockEntity(AT);
    relay.setItem(CARD_SLOT, item(Constants.ItemName.WirelessNetworkCardTier2));
    relay.setStrength(null, new ArgumentsImpl(new Object[]{0.0}));
    relay.setItem(CPU_SLOT, item(Constants.ItemName.CPUTier1));
    relay.setItem(MEMORY_SLOT, item(Constants.ItemName.RAMTier1));
    helper.assertTrue(relay.strength == 0, "a relay set to strength 0 sends at " + relay.strength + " once parts are put in");
    helper.succeed();
  }

  /** Energy that comes into a rack's network at one side reaches the network at another side, as OC (it never did). */
  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void rackSidesShareTheirEnergy(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.RACK.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    helper.setBlock(AT.west(), Blocks.CAPACITOR.get());
    helper.setBlock(AT.east(), Blocks.CAPACITOR.get());
    final var west = (Capacitor) helper.getBlockEntity(AT.west());
    final var east = (Capacitor) helper.getBlockEntity(AT.east());
    helper.runAfterDelay(3, () -> { // joined
      helper.assertTrue(west.node().network() != null && east.node().network() != null && west.node().network() != east.node().network(),
        "the capacitors at the rack's sides are not in networks of their own");
      connector(west).changeBuffer(1000);
      when(helper, () -> connector(east).globalBuffer() > 0, 100, () -> {
        final double westRatio = connector(west).globalBuffer() / connector(west).globalBufferSize();
        final double eastRatio = connector(east).globalBuffer() / connector(east).globalBufferSize();
        helper.assertTrue(connector(east).globalBuffer() > 0, "energy at the rack's west side never reached the network at its east side");
        helper.assertTrue(Math.abs(westRatio - eastRatio) < 0.01, "the rack's side networks are filled " + westRatio + " and " + eastRatio);
        helper.succeed();
      });
    });
  }

  /** A power distributor still evens out the networks at its sides (its balancing is the one racks use now). */
  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void powerDistributorSidesShareTheirEnergy(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.POWER_DISTRIBUTOR.get());
    helper.setBlock(AT.west(), Blocks.CAPACITOR.get());
    helper.setBlock(AT.east(), Blocks.CAPACITOR.get());
    final var west = (Capacitor) helper.getBlockEntity(AT.west());
    final var east = (Capacitor) helper.getBlockEntity(AT.east());
    helper.runAfterDelay(3, () -> {
      connector(west).changeBuffer(1000);
      when(helper, () -> connector(east).globalBuffer() > 0, 100, () -> {
        final double westRatio = connector(west).globalBuffer() / connector(west).globalBufferSize();
        final double eastRatio = connector(east).globalBuffer() / connector(east).globalBufferSize();
        helper.assertTrue(Math.abs(westRatio - eastRatio) < 0.01, "the distributor's side networks are filled " + westRatio + " and " + eastRatio);
        helper.succeed();
      });
    });
  }

  /**
   * A charger charges at the speed of the redstone signal next to it, each side read on its own (each side read the best
   * signal of all sides, from blocks in chunks not loaded too).
   */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void chargersFollowRedstone(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.CHARGER.get());
    final var charger = (Charger) helper.getBlockEntity(AT);
    helper.runAfterDelay(3, () -> {
      helper.assertTrue(charger.chargeSpeed == 0, "an unpowered charger charges at " + charger.chargeSpeed);
      helper.setBlock(AT.east(), net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK);
      helper.assertTrue(charger.chargeSpeed == 1, "a charger next to a redstone block charges at " + charger.chargeSpeed);
      for (final Direction side : Direction.values()) {
        final int expected = side == Direction.EAST ? 15 : 0;
        helper.assertTrue(charger.getInput(side) == expected, "a charger with a redstone block east of it reads " + charger.getInput(side) + " at its " + side + " side");
      }
      helper.setBlock(AT.east(), net.minecraft.world.level.block.Blocks.AIR);
      helper.assertTrue(charger.chargeSpeed == 0, "a charger whose redstone block went charges at " + charger.chargeSpeed);
      helper.succeed();
    });
  }

  /**
   * AE2 energy is drawn only for the sides that take it in: a charger's or case's front, a disassembler's top (it was
   * drawn for those too, and lost).
   */
  @GameTest(template = EMPTY, timeoutTicks = 1000)
  public static void ae2EnergyIsDrawnOnlyForSidesThatTakeIt(final GameTestHelper helper) {
    final double stored = 50000;
    final BlockPos[] at = {new BlockPos(2, 1, 1), new BlockPos(2, 1, 3), new BlockPos(2, 3, 2)};
    final String[] names = {"charger", "case", "disassembler"};
    // fronts north: the third side asked (with room left, unlike the last)
    helper.setBlock(at[0], Blocks.CHARGER.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    helper.setBlock(at[1], Blocks.CASE_TIER_3.get());
    ((Case) helper.getBlockEntity(at[1])).facing(Direction.NORTH);
    helper.setBlock(at[2], Blocks.DISASSEMBLER.get());
    final IAEPowerStorage[] cells = new IAEPowerStorage[at.length];
    final Connector[] connectors = new Connector[at.length];
    for (int i = 0; i < at.length; i++) {
      helper.setBlock(at[i].west(), ae2Block("energy_cell"));
      cells[i] = (IAEPowerStorage) helper.getBlockEntity(at[i].west());
      cells[i].injectAEPower(stored, Actionable.MODULATE);
    }
    final java.util.function.BooleanSupplier full = () -> {
      for (int i = 0; i < at.length; i++) {
        if (connectors[i] == null) {
          final var node = ((li.cil.oc.api.network.Environment) helper.getBlockEntity(at[i])).node();
          if (!(node instanceof Connector c) || c.network() == null) return false;
          connectors[i] = c;
        }
        if (connectors[i].globalBuffer() < connectors[i].globalBufferSize() - 1e-6) return false;
      }
      return true;
    };
    when(helper, full, 900, () -> {
      helper.assertTrue(full.getAsBoolean(), "AE2 did not fill the blocks");
      for (int i = 0; i < at.length; i++) {
        final double drawn = Power.fromAE(stored - cells[i].getAECurrentPower());
        final double got = connectors[i].globalBuffer();
        helper.assertTrue(drawn <= got + 0.01, "the " + names[i] + " drew " + drawn + " from AE2 for the " + got + " it got");
      }
      helper.succeed();
    });
  }

  /**
   * A connector leaving its network takes its energy along (the network got it as well: on a chunk unload the block
   * was saved with its energy and the part still loaded gained it).
   */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void connectorsLeaveWithTheirEnergy(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.CHARGER.get());
    helper.setBlock(AT.east(), Blocks.CAPACITOR.get());
    final var charger = (Charger) helper.getBlockEntity(AT);
    final var capacitor = (Capacitor) helper.getBlockEntity(AT.east());
    helper.runAfterDelay(3, () -> { // joined
      helper.assertTrue(charger.node().network() != null && charger.node().network() == capacitor.node().network(), "the charger and the capacitor are not connected");
      connector(charger).changeBuffer(500); // into the charger's own buffer
      helper.assertTrue(connector(charger).localBuffer() == 500 && connector(capacitor).localBuffer() == 0,
        "the energy went to the charger " + connector(charger).localBuffer() + ", the capacitor " + connector(capacitor).localBuffer());
      // its chunk unloads: saved, then gone
      charger.saveWithFullMetadata();
      charger.onChunkUnloaded();
      helper.getLevel().removeBlockEntity(helper.absolutePos(AT));
      helper.assertTrue(connector(capacitor).globalBuffer() < 1e-6, "the network the charger left gained " + connector(capacitor).globalBuffer() + " energy");
      helper.assertTrue(connector(charger).localBuffer() == 500, "the charger left with " + connector(charger).localBuffer() + " of its 500");
      helper.succeed();
    });
  }

  /**
   * A capacitor and a charger at a chunk's edge do not load the next chunk while it is on its way (hasChunk asks only the
   * ticket level: it was loaded there and then, the server thread waiting for it), nor while it is not loaded at all
   * (setting up the charger read redstone and made its AE2 node there, which loaded it too).
   */
  @GameTest(template = EMPTY, timeoutTicks = 10_000_000)
  public static void neighboursInLoadingChunksAreNotLoaded(final GameTestHelper helper) {
    final ServerLevel level = helper.getLevel();
    final var chunks = level.getChunkSource();
    // a pair of far chunks of its own, side by side (the test world is kept: new ones each run)
    final ChunkPos a = new ChunkPos(2500 + level.random.nextInt(2000) * 3, -2500 - level.random.nextInt(2000) * 3);
    final ChunkPos b = new ChunkPos(a.x + 1, a.z);
    helper.assertTrue(chunks.getChunkNow(a.x, a.z) == null && !level.hasChunk(b.x, b.z), "the far chunks are loaded already");
    final BlockPos capacitorAt = new BlockPos(a.getMaxBlockX(), 100, a.getMinBlockZ() + 4); // at the edge: neighbours in b
    final BlockPos chargerAt = capacitorAt.south(4);
    final Runnable cleanup = () -> {
      chunks.removeRegionTicket(LOADING, b, 0, b);
      chunks.pollTask(); // (b out of reach first: removing a block entity reads the blocks around it)
      for (final BlockPos pos : new BlockPos[]{capacitorAt, chargerAt}) {
        level.removeBlockEntity(pos);
        level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2 | 16 | 64); // (no updates into b)
      }
      chunks.removeRegionTicket(LOADING, a, 0, a);
    };
    chunks.addRegionTicket(LOADING, a, 0, a); // loaded, at the border
    within(helper, 60, () -> chunks.getChunkNow(a.x, a.z) != null, () -> {
      final Capacitor capacitor;
      final Charger charger;
      try {
        helper.assertTrue(chunks.getChunkNow(a.x, a.z) != null, "the far chunk did not load");
        level.setBlock(capacitorAt, Blocks.CAPACITOR.get().defaultBlockState(), 2 | 16);
        // its back to b (AE2 looks at the sides it takes energy at, not at its front)
        level.setBlock(chargerAt, Blocks.CHARGER.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST), 2 | 16);
        capacitor = (Capacitor) level.getBlockEntity(capacitorAt);
        charger = (Charger) level.getBlockEntity(chargerAt);
      } catch (RuntimeException e) {
        cleanup.run();
        throw e;
      }
      helper.runAfterDelay(5, () -> { // set up, b not asked for yet
        try {
          helper.assertTrue(capacitor.node().network() != null, "the capacitor did not join");
          // (a chunk that was loaded once is in memory, and loaded at once when asked for)
          final var before = chunks.getChunk(b.x, b.z, net.minecraft.world.level.chunk.ChunkStatus.EMPTY, false);
          helper.assertTrue(before == null || !before.getStatus().isOrAfter(net.minecraft.world.level.chunk.ChunkStatus.FULL),
            "setting up the capacitor and the charger loaded the next chunk");
          // b asked for and its ticket level applied, nothing more (between ticks the server loads it at once)
          chunks.addRegionTicket(LOADING, b, 0, b);
          chunks.pollTask();
          helper.assertTrue(level.hasChunk(b.x, b.z) && chunks.getChunkNow(b.x, b.z) == null, "the next chunk is not on its way");
          capacitor.recomputeCapacity(true);
          helper.assertTrue(chunks.getChunkNow(b.x, b.z) == null, "the capacitor loaded the next chunk, on its way");
          charger.updateConnectors();
          helper.assertTrue(chunks.getChunkNow(b.x, b.z) == null, "the charger loaded the next chunk, on its way");
          capacitor.setRemoved(); // unloaded (it tells the capacitors around it)
          helper.assertTrue(chunks.getChunkNow(b.x, b.z) == null, "a capacitor going loaded the next chunk, on its way");
        } finally {
          cleanup.run();
        }
        helper.succeed();
      });
    });
  }

  /**
   * A capacitor at a chunk's edge, loaded before the chunk next to it, keeps what it holds, and counts a capacitor there
   * once that connects: it counted the chunk still loading as empty and shrank, the energy over its new size going to
   * its network (lost, full) on every load; and the capacitor connecting later told only those two blocks away.
   */
  @GameTest(template = EMPTY, timeoutTicks = 10_000_000)
  public static void capacitorsAtAChunksEdgeKeepWhatTheyHold(final GameTestHelper helper) {
    final ServerLevel level = helper.getLevel();
    final var chunks = level.getChunkSource();
    // a pair of far chunks of its own, side by side (another row than the loading test's)
    final ChunkPos a = new ChunkPos(2500 + level.random.nextInt(2000) * 3, 2500 + level.random.nextInt(2000) * 3);
    final ChunkPos b = new ChunkPos(a.x + 1, a.z);
    helper.assertTrue(chunks.getChunkNow(a.x, a.z) == null && !level.hasChunk(b.x, b.z), "the far chunks are loaded already");
    final BlockPos atA = new BlockPos(a.getMaxBlockX(), 100, a.getMinBlockZ() + 4);
    final BlockPos atB = atA.east(); // in b
    final double base = OCSettings.get().bufferCapacitor, bonus = OCSettings.get().bufferCapacitorAdjacencyBonus;
    final double held = base + bonus / 2; // more than it holds alone, less than beside another
    final Runnable cleanup = () -> {
      for (final BlockPos pos : new BlockPos[]{atB, atA}) {
        if (chunks.getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null) {
          level.removeBlockEntity(pos);
          level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2 | 16 | 64);
        }
      }
      chunks.removeRegionTicket(LOADING, b, 0, b);
      chunks.removeRegionTicket(LOADING, a, 0, a);
    };
    chunks.addRegionTicket(LOADING, a, 0, a);
    within(helper, 60, () -> chunks.getChunkNow(a.x, a.z) != null, () -> {
      final Capacitor first;
      try {
        helper.assertTrue(chunks.getChunkNow(a.x, a.z) != null, "the far chunk did not load");
        level.setBlock(atA, Blocks.CAPACITOR.get().defaultBlockState(), 2 | 16);
        first = (Capacitor) level.getBlockEntity(atA);
        connector(first).changeBuffer(held); // (as loaded: in no network yet)
      } catch (RuntimeException e) {
        cleanup.run();
        throw e;
      }
      within(helper, 10, () -> first.node().network() != null, () -> {
        boolean handedOver = false;
        try {
          helper.assertTrue(first.node().network() != null, "the capacitor did not join");
          helper.assertTrue(Math.abs(connector(first).localBuffer() - held) < 1e-6, "the capacitor beside a chunk not loaded kept "
            + connector(first).localBuffer() + " of the " + held + " it held (" + connector(first).localBufferSize() + " its size)");
          chunks.addRegionTicket(LOADING, b, 0, b);
          within(helper, 60, () -> chunks.getChunkNow(b.x, b.z) != null, () -> {
            final Capacitor second;
            try {
              helper.assertTrue(chunks.getChunkNow(b.x, b.z) != null, "the next chunk did not load");
              level.setBlock(atB, Blocks.CAPACITOR.get().defaultBlockState(), 2 | 16 | 64); // (no block update for the first)
              second = (Capacitor) level.getBlockEntity(atB);
            } catch (RuntimeException e) {
              cleanup.run();
              throw e;
            }
            within(helper, 10, () -> second.node().network() != null, () -> {
              try {
                helper.assertTrue(second.node().network() != null, "the second capacitor did not join");
                helper.assertTrue(connector(first).localBufferSize() == base + bonus, "the capacitor beside the one connecting later holds "
                  + connector(first).localBufferSize() + ", not " + (base + bonus));
                helper.assertTrue(Math.abs(connector(first).localBuffer() - held) < 1e-6, "the capacitor beside the one connecting later kept " + connector(first).localBuffer());
              } finally {
                cleanup.run();
              }
              helper.succeed();
            });
          });
          handedOver = true;
        } finally {
          if (!handedOver) cleanup.run();
        }
      });
    });
  }

  /**
   * The capabilities handed out for a block go with it, and a side's with the side (they were never invalidated: pipes
   * and Create funnels that keep theirs put items and energy into a removed or unloaded block, and the items were lost).
   */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void capabilitiesGoWithTheirBlock(final GameTestHelper helper) {
    final BlockPos unloadedAt = AT.west(2);
    helper.setBlock(AT, Blocks.CHARGER.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    helper.setBlock(unloadedAt, Blocks.CHARGER.get());
    final var charger = (Charger) helper.getBlockEntity(AT);
    final var items = charger.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP);
    final var energy = charger.getCapability(ForgeCapabilities.ENERGY, Direction.EAST);
    helper.assertTrue(items.isPresent() && energy.isPresent(), "the charger has no item handler or energy storage");
    helper.assertTrue(charger.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP) == items, "each lookup gives a new item handler");
    final boolean[] told = {false};
    items.addListener(gone -> told[0] = true);
    // turned: its east side is the front now, which takes no energy
    helper.getLevel().setBlock(helper.absolutePos(AT), charger.getBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST), 2);
    helper.assertTrue(!charger.getCapability(ForgeCapabilities.ENERGY, Direction.EAST).isPresent(), "the charger's front takes energy");
    helper.assertTrue(!energy.isPresent(), "the energy storage of a side turned to the front is still there");
    // unloaded
    final var other = (Charger) helper.getBlockEntity(unloadedAt);
    final var otherItems = other.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP);
    helper.assertTrue(otherItems.isPresent(), "the other charger has no item handler");
    other.onChunkUnloaded();
    helper.assertTrue(!otherItems.isPresent(), "the item handler of a charger unloaded is still there");
    // removed
    helper.setBlock(AT, net.minecraft.world.level.block.Blocks.AIR);
    helper.assertTrue(!items.isPresent() && told[0], "the item handler of a charger removed is still there");
    helper.succeed();
  }

  /** A block removed before it was set up gets no AE2 node (it got one, never destroyed, and kept it for good). */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void noAE2NodeForABlockGoneBeforeItsSetUp(final GameTestHelper helper) throws ReflectiveOperationException {
    final var field = li.cil.oc.neoforge.integration.appeng.AE2GridDelegate.class.getDeclaredField("nodes");
    field.setAccessible(true);
    final var nodes = (java.util.Map<?, ?>) field.get(li.cil.oc.core.impl.common.blockentity.traits.power.AE2Power.delegate());
    helper.setBlock(AT, Blocks.CHARGER.get());
    final var charger = (Charger) helper.getBlockEntity(AT);
    helper.setBlock(AT, net.minecraft.world.level.block.Blocks.AIR); // before its scheduled set-up
    helper.runAfterDelay(5, () -> {
      final Object node = charger.getGridNode(Direction.UP);
      final boolean kept = nodes.containsKey(charger);
      try {
        helper.assertTrue(node == null && !kept, "a charger removed before it was set up has the AE2 node " + node + (kept ? ", kept" : ""));
      } finally {
        charger.ae2Invalidate();
      }
      helper.succeed();
    });
  }
}
