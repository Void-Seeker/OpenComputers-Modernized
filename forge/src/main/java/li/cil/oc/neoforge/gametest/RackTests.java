package li.cil.oc.neoforge.gametest;

import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import li.cil.oc.core.Constants;
import li.cil.oc.core.impl.common.blockentity.Rack;
import li.cil.oc.core.impl.server.component.ServerBase;
import li.cil.oc.neoforge.OpenComputers;
import li.cil.oc.neoforge.common.init.Blocks;
import li.cil.oc.neoforge.common.init.Items;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Racks, their servers and drives, the assembler and disassembler: nothing runs on, opens, turns or breaks wrong.
 */
@GameTestHolder(OpenComputers.ID)
@PrefixGameTestTemplate(false)
public final class RackTests {
  private static final String EMPTY = "empty";
  private static final BlockPos AT = new BlockPos(2, 2, 2);
  private static final String IDLE = "while true do computer.pullSignal(1) end\n";

  private RackTests() {
  }

  private static ItemStack item(final String name) {
    return li.cil.oc.api.Items.get(name).createItemStack(1);
  }

  private static FakePlayer playerAt(final GameTestHelper helper, final BlockPos abs) {
    final FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "oc-rack"));
    player.setPos(abs.getX() + 0.5, abs.getY() + 1, abs.getZ() + 1.5);
    return player;
  }

  /** A server in the rack's slot 0 with CPU, RAM and an EEPROM running the given Lua; then is called once it runs. */
  private static void runServer(final GameTestHelper helper, final Rack rack, final String lua, final Consumer<ServerBase> then) {
    rack.setItem(0, item(Constants.ItemName.ServerTier1));
    final var server = (ServerBase) rack.getMountable(0);
    helper.assertTrue(server != null, "the rack made no server mountable");
    for (final ItemStack part : List.of(item(Constants.ItemName.CPUTier1), item(Constants.ItemName.RAMTier2),
      li.cil.oc.api.Items.registerEEPROM("rack test", lua.getBytes(java.nio.charset.StandardCharsets.UTF_8), null, false))) {
      int slot = -1;
      for (int i = 0; i < server.getContainerSize() && slot < 0; i++) {
        if (server.getItem(i).isEmpty() && server.canPlaceItem(i, part)) slot = i;
      }
      helper.assertTrue(slot >= 0, "no server slot takes " + part);
      server.setItem(slot, part);
    }
    helper.runAfterDelay(5, () -> {
      ((li.cil.oc.api.network.Connector) server.machine().node()).changeBuffer(1000);
      helper.assertTrue(server.machine().start(), "the server did not start: " + server.machine().lastError());
      helper.runAfterDelay(20, () -> {
        helper.assertTrue(server.machine().isRunning(), "the server stopped: " + server.machine().lastError());
        then.accept(server);
      });
    });
  }

  private static Rack rack(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.RACK.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    return (Rack) helper.getBlockEntity(AT);
  }

  /**
   * A racked server is running when its chunk is saved on unload (Forge posts the unload event before the save, and
   * the servers were stopped there: they came back off). In a chunk of its own, so no other test sees the event.
   */
  @GameTest(template = EMPTY, timeoutTicks = 400)
  public static void rackedServerIsSavedRunningOnUnload(final GameTestHelper helper) {
    final ServerLevel level = helper.getLevel();
    final BlockPos far = new BlockPos(24000 + helper.getLevel().random.nextInt(64) * 16, 100, 24000);
    final ChunkPos chunk = new ChunkPos(far);
    level.setChunkForced(chunk.x, chunk.z, true);
    helper.runAfterDelay(5, () -> {
      level.setBlock(far, Blocks.RACK.get().defaultBlockState(), 3);
      final Rack rack = (Rack) level.getBlockEntity(far);
      runServer(helper, rack, IDLE, server -> {
        try {
          MinecraftForge.EVENT_BUS.post(new ChunkEvent.Unload(level.getChunk(chunk.x, chunk.z)));
          helper.assertTrue(server.machine().isRunning(), "the racked server was stopped before its chunk was saved");
        } finally {
          level.removeBlockEntity(far);
          level.setBlock(far, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
          level.setChunkForced(chunk.x, chunk.z, false);
        }
        helper.succeed();
      });
    });
  }

  /** The menus of a server and a drive close once they leave the rack (they went on handing out their parts). */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void mountableMenusCloseWhenTheyLeaveTheRack(final GameTestHelper helper) {
    final Rack rack = rack(helper);
    final FakePlayer player = playerAt(helper, helper.absolutePos(AT));
    helper.runAfterDelay(2, () -> {
      rack.setItem(0, item(Constants.ItemName.ServerTier1));
      rack.setItem(1, item(Constants.ItemName.DiskDriveMountable));
      final var server = (ServerBase) rack.getMountable(0);
      final var drive = (li.cil.oc.core.impl.server.component.DiskDriveMountableBase) rack.getMountable(1);
      helper.assertTrue(server.stillValid(player) && drive.stillValid(player), "the menus are not valid while in the rack");
      rack.removeItem(0, 1);
      rack.removeItem(1, 1);
      helper.assertTrue(!server.stillValid(player), "a server's menu stays open after the server left the rack");
      helper.assertTrue(!drive.stillValid(player), "a drive's menu stays open after the drive left the rack");
      helper.succeed();
    });
  }

  /** A wrenched rack turns all of it: block state, front, the plugs its server is on, and the save. */
  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void wrenchedRackTurnsWhole(final GameTestHelper helper) {
    final Rack rack = rack(helper);
    final BlockPos abs = helper.absolutePos(AT);
    rack.setItem(0, item(Constants.ItemName.ServerTier1));
    helper.runAfterDelay(5, () -> {
      final var server = (ServerBase) rack.getMountable(0);
      rack.connect(0, -1, Direction.EAST);
      final Direction before = rack.facing();
      final var oldPlug = rack.sidedNode(rack.toGlobal(Direction.EAST));
      helper.assertTrue(server.node() != null && oldPlug != null && contains(server.node().neighbors(), oldPlug), "the server is not on its plug before turning");
      helper.getLevel().getChunkAt(abs).setUnsaved(false);
      final FakePlayer player = playerAt(helper, abs);
      final ItemStack wrench = new ItemStack(Items.WRENCH.get());
      player.setItemInHand(InteractionHand.MAIN_HAND, wrench);
      wrench.getItem().onItemUseFirst(wrench, new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false)));
      final Direction after = rack.facing();
      final var state = helper.getBlockState(AT);
      helper.assertTrue(after != before && state.getValue(BlockStateProperties.HORIZONTAL_FACING) == after,
        "the rack faces " + after + " (was " + before + "), its block " + state.getValue(BlockStateProperties.HORIZONTAL_FACING));
      helper.assertTrue(rack.sidedNode(after) == null, "the new front has a plug");
      final var newPlug = rack.sidedNode(rack.toGlobal(Direction.EAST));
      helper.assertTrue(newPlug != null && contains(server.node().neighbors(), newPlug) && (newPlug == oldPlug || !contains(server.node().neighbors(), oldPlug)),
        "after turning the server is on the old side's plug, not its side's");
      helper.assertTrue(helper.getLevel().getChunkAt(abs).isUnsaved(), "the turn is not saved");
      helper.succeed();
    });
  }

  private static boolean contains(final Iterable<li.cil.oc.api.network.Node> nodes, final li.cil.oc.api.network.Node node) {
    for (final var n : nodes) if (n == node) return true;
    return false;
  }

  /** The relay switch is saved (the chunk was not marked: it reverted after a restart). */
  @GameTest(template = EMPTY)
  public static void rackRelaySwitchIsSaved(final GameTestHelper helper) {
    final Rack rack = rack(helper);
    final var chunk = helper.getLevel().getChunkAt(helper.absolutePos(AT));
    chunk.setUnsaved(false);
    rack.setRelayEnabled(!rack.isRelayEnabled);
    helper.assertTrue(chunk.isUnsaved(), "switching the relay left the chunk unsaved");
    helper.succeed();
  }

  /** A server taken out while its computer is busy still closes once it is free (it kept its Lua state forever). */
  @GameTest(template = EMPTY, timeoutTicks = 400)
  public static void busyServerTakenOutStillCloses(final GameTestHelper helper) {
    final Rack rack = rack(helper);
    runServer(helper, rack, IDLE, server -> {
      final var machine = (li.cil.oc.core.impl.server.machine.MachineBase) server.machine();
      final java.util.concurrent.locks.ReentrantLock lock;
      try {
        final var field = li.cil.oc.core.impl.server.machine.MachineBase.class.getDeclaredField("executionLock");
        field.setAccessible(true);
        lock = (java.util.concurrent.locks.ReentrantLock) field.get(machine);
      } catch (ReflectiveOperationException e) {
        throw new RuntimeException(e);
      }
      final var held = new java.util.concurrent.CountDownLatch(1);
      final Thread holder = new Thread(() -> {
        lock.lock();
        try {
          held.countDown();
          Thread.sleep(1000);
        } catch (InterruptedException ignored) {
        } finally {
          lock.unlock();
        }
      }, "busy server");
      holder.start();
      try {
        held.await();
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      rack.removeItem(0, 1);
      helper.runAfterDelay(60, () -> {
        helper.assertTrue(!lock.isLocked(), "the test still holds the lock");
        helper.assertTrue("Stopped".equals(String.valueOf(machine.state().peek())), "the server taken out while busy never closed: " + machine.state());
        helper.succeed();
      });
    });
  }

  /** A creative player's disassembly loses no part (the break chance applied to it). */
  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void creativeDisassemblyLosesNothing(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.DISASSEMBLER.get());
    final var disassembler = (li.cil.oc.core.impl.common.blockentity.Disassembler) helper.getBlockEntity(AT);
    final FakePlayer player = playerAt(helper, helper.absolutePos(AT));
    player.setGameMode(GameType.CREATIVE);
    final ItemStack cpu = item(Constants.ItemName.CPUTier1);
    int parts = 0;
    for (final ItemStack part : li.cil.oc.core.impl.util.ItemUtils.getIngredients(cpu)) parts += part.getCount();
    final int expected = parts;
    helper.assertTrue(expected > 1, "a CPU has " + expected + " parts");
    disassembler.setItem(0, cpu);
    disassembler.onSetInventorySlotContents(player, 0, cpu);
    final boolean[] notInstant = {false};
    helper.onEachTick(() -> {
      if (!disassembler.queue.isEmpty() && !disassembler.instantJob) notInstant[0] = true;
    });
    helper.runAfterDelay(60, () -> {
      helper.assertTrue(!notInstant[0], "the creative job was not instant while it ran");
      int dropped = 0;
      for (final ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(6, 6, 6))).inflate(2))) {
        dropped += e.getItem().getCount();
      }
      helper.assertTrue(disassembler.queue.isEmpty() && dropped == expected, "a creative disassembly of " + expected + " parts gave " + dropped);
      helper.succeed();
    });
  }

  /** Parts are queued one by one, each with its own break chance and energy (a merged stack was one roll for all). */
  @GameTest(template = EMPTY)
  public static void disassemblyQueuesSingleParts(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.DISASSEMBLER.get());
    final var disassembler = (li.cil.oc.core.impl.common.blockentity.Disassembler) helper.getBlockEntity(AT);
    final ItemStack cpu = item(Constants.ItemName.CPUTier1);
    int parts = 0;
    boolean merged = false;
    for (final ItemStack part : li.cil.oc.core.impl.util.ItemUtils.getIngredients(cpu)) {
      parts += part.getCount();
      merged |= part.getCount() > 1;
    }
    helper.assertTrue(merged, "a CPU's ingredients come unmerged: nothing to check");
    disassembler.disassemble(cpu, false);
    helper.assertTrue(disassembler.queue.size() == parts && disassembler.queue.stream().allMatch(s -> s.getCount() == 1),
      "a CPU of " + parts + " parts queued " + disassembler.queue);
    disassembler.queue.clear();
    helper.succeed();
  }

  /** A creative build waiting for its tick keeps its case slot closed (a case put in then was overwritten). */
  @GameTest(template = EMPTY)
  public static void instantAssemblyKeepsItsSlotClosed(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.ASSEMBLER.get());
    final var assembler = (li.cil.oc.core.impl.common.blockentity.Assembler) helper.getBlockEntity(AT);
    assembler.setItem(0, item(Constants.BlockName.CaseTier1));
    for (final ItemStack part : List.of(item(Constants.ItemName.CPUTier1), item(Constants.ItemName.RAMTier1))) {
      for (int i = 1; i < assembler.getContainerSize(); i++) {
        if (assembler.getItem(i).isEmpty() && assembler.canPlaceItem(i, part)) {
          assembler.setItem(i, part);
          break;
        }
      }
    }
    helper.assertTrue(assembler.start(true), "the creative build did not start");
    helper.assertTrue(!assembler.canPlaceItem(0, item(Constants.BlockName.CaseTier1)), "the case slot takes a case while a creative build waits");
    helper.succeed();
  }

  /** Simulated energy into a rack changes nothing (it was added: mods that simulate first delivered twice). */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void rackSimulatedEnergyChangesNothing(final GameTestHelper helper) {
    final Rack rack = rack(helper);
    rack.setItem(0, item(Constants.ItemName.ServerTier1));
    helper.runAfterDelay(5, () -> {
      rack.connect(0, -1, Direction.UP);
      final double before = rack.globalBuffer(Direction.UP);
      helper.assertTrue(rack.globalBufferSize(Direction.UP) > before + 10, "the rack's server has no room for energy: " + before + " of " + rack.globalBufferSize(Direction.UP));
      final double offered = rack.tryChangeBuffer(Direction.UP, 10, false);
      helper.assertTrue(offered > 0 && rack.globalBuffer(Direction.UP) == before, "a simulation took " + offered + " and changed the buffer from " + before + " to " + rack.globalBuffer(Direction.UP));
      helper.succeed();
    });
  }

  /** A bound terminal types into its terminal server only in range (any distance worked on the server side). */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void terminalServerKeyboardHasARange(final GameTestHelper helper) {
    final Rack rack = rack(helper);
    final BlockPos abs = helper.absolutePos(AT);
    rack.setItem(0, item(Constants.ItemName.TerminalServer));
    helper.runAfterDelay(5, () -> {
      final var terminalServer = (li.cil.oc.core.impl.common.component.TerminalServer) rack.getMountable(0);
      terminalServer.sidedKeys().add("test-key");
      final ItemStack terminal = item(Constants.ItemName.Terminal);
      terminal.getOrCreateTag().putString(li.cil.oc.core.impl.OCSettings.namespace + "key", "test-key");
      final FakePlayer player = playerAt(helper, abs);
      player.setItemInHand(InteractionHand.MAIN_HAND, terminal);
      final var keyboard = (li.cil.oc.core.impl.server.component.Keyboard) terminalServer.keyboard();
      helper.assertTrue(keyboard.isUsableByPlayer(player), "a bound terminal next to the rack cannot type");
      player.setPos(abs.getX() + 2000, abs.getY(), abs.getZ());
      helper.assertTrue(!keyboard.isUsableByPlayer(player), "a bound terminal 2000 blocks away can type");
      helper.succeed();
    });
  }
}
