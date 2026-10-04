package li.cil.oc.neoforge.gametest;

import java.util.List;
import li.cil.oc.core.Constants;
import li.cil.oc.core.impl.OCSettings;
import li.cil.oc.core.impl.common.blockentity.DiskDrive;
import li.cil.oc.core.impl.common.blockentity.Printer;
import li.cil.oc.core.impl.server.machine.ArgumentsImpl;
import li.cil.oc.neoforge.OpenComputers;
import li.cil.oc.neoforge.common.init.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Regressions found by the port audit.
 */
@GameTestHolder(OpenComputers.ID)
@PrefixGameTestTemplate(false)
public final class AuditTests {
  private static final String EMPTY = "empty";

  private AuditTests() {
  }

  private static ItemStack item(final String name) {
    return li.cil.oc.api.Items.get(name).createItemStack(1);
  }

  /** Block entities keep their node, so component addresses and energy buffers survive a reload. */
  @GameTest(template = EMPTY)
  public static void blockEntitiesSaveTheirNode(final GameTestHelper helper) {
    final List<Block> blocks = List.of(Blocks.ASSEMBLER.get(), Blocks.DISK_DRIVE.get(), Blocks.NET_SPLITTER.get(), Blocks.WAYPOINT.get(),
      Blocks.CHARGER.get(), Blocks.DISASSEMBLER.get(), Blocks.POWER_CONVERTER.get());
    for (int i = 0; i < blocks.size(); i++) helper.setBlock(new BlockPos(i % 4, 1 + 2 * (i / 4), 1), blocks.get(i));
    helper.runAfterDelay(10, () -> {
      for (int i = 0; i < blocks.size(); i++) {
        final BlockEntity be = helper.getBlockEntity(new BlockPos(i % 4, 1 + 2 * (i / 4), 1));
        final var node = ((li.cil.oc.api.network.Environment) be).node();
        final var saved = be.saveWithoutMetadata().getCompound(OCSettings.namespace + "node");
        helper.assertTrue(!saved.isEmpty(), blocks.get(i) + " saves no node");
        if (node.address() != null) {
          helper.assertTrue(node.address().equals(saved.getString("address")), blocks.get(i) + " saves address " + saved.getString("address") + " instead of " + node.address());
        }
      }
      helper.succeed();
    });
  }

  /** eject() takes the floppy out through the inventory, so its filesystem leaves the network. */
  @GameTest(template = EMPTY)
  public static void diskDriveEjectUnmountsTheFloppy(final GameTestHelper helper) {
    final BlockPos pos = new BlockPos(2, 2, 2);
    helper.setBlock(pos, Blocks.DISK_DRIVE.get());
    helper.runAfterDelay(5, () -> {
      final DiskDrive drive = (DiskDrive) helper.getBlockEntity(pos);
      drive.setItem(0, item(Constants.ItemName.Floppy));
      helper.runAfterDelay(5, () -> {
        helper.assertTrue(drive.componentEnvironments()[0] != null, "the inserted floppy got no filesystem component");
        final Object[] result = drive.eject(null, new ArgumentsImpl(new Object[]{0.0}));
        helper.assertTrue(Boolean.TRUE.equals(result[0]), "eject reported " + result[0]);
        helper.runAfterDelay(2, () -> {
          helper.assertTrue(drive.getItem(0).isEmpty(), "the floppy is still in the drive: " + drive.getItem(0));
          helper.assertTrue(drive.componentEnvironments()[0] == null, "the ejected floppy's filesystem is still mounted");
          final var dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(pos)).inflate(2));
          helper.assertTrue(dropped.stream().anyMatch(e -> li.cil.oc.api.Items.get(e.getItem()) != null && Constants.ItemName.Floppy.equals(li.cil.oc.api.Items.get(e.getItem()).name())), "no floppy item was ejected: " + dropped);
          helper.succeed();
        });
      });
    });
  }

  /** A broken printer drops whole stacks, not one item per slot. */
  @GameTest(template = EMPTY)
  public static void brokenPrinterDropsWholeStacks(final GameTestHelper helper) {
    final BlockPos pos = new BlockPos(2, 2, 2);
    helper.setBlock(pos, Blocks.PRINTER.get());
    helper.runAfterDelay(5, () -> {
      final Printer printer = (Printer) helper.getBlockEntity(pos);
      final ItemStack chamelium = item(Constants.ItemName.Chamelium);
      chamelium.setCount(10);
      printer.setItem(0, chamelium);
      helper.getLevel().destroyBlock(helper.absolutePos(pos), false);
      helper.runAfterDelay(2, () -> {
        int count = 0;
        for (final ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(pos)).inflate(3))) {
          final var info = li.cil.oc.api.Items.get(entity.getItem());
          if (info != null && Constants.ItemName.Chamelium.equals(info.name())) count += entity.getItem().getCount();
        }
        helper.assertTrue(count == 10, count + " chamelium dropped instead of 10");
        helper.succeed();
      });
    });
  }

  /** What a program writes into its EEPROM (here the label) is in the saved microcontroller. */
  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void microcontrollerSavesItsEepromState(final GameTestHelper helper) {
    final BlockPos pos = new BlockPos(2, 2, 2);
    TestMachines.runMicrocontroller(helper, pos, List.of(Constants.ItemName.CPUTier1, Constants.ItemName.RAMTier2),
      "component.proxy(component.list(\"eeprom\")()).setLabel(\"mcprobe42\")\n"
        + "while true do computer.pullSignal(1) end\n",
      mc -> {
        final String saved = mc.saveWithoutMetadata().getCompound(OCSettings.namespace + "info").toString();
        helper.assertTrue(saved.contains("mcprobe42"), "the saved microcontroller does not hold the EEPROM label: " + saved);
        helper.succeed();
      });
  }

  /** Hoppers use the item handler capability; it must honour the block's side rules (a microcontroller gives nothing). */
  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void hopperCannotStripAMicrocontroller(final GameTestHelper helper) {
    final BlockPos pos = new BlockPos(2, 2, 2);
    helper.setBlock(pos.below(), net.minecraft.world.level.block.Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, net.minecraft.core.Direction.DOWN));
    TestMachines.runMicrocontroller(helper, pos, List.of(Constants.ItemName.CPUTier1, Constants.ItemName.RAMTier2, Constants.ItemName.RedstoneCardTier1),
      "while true do computer.pullSignal(1) end\n",
      mc -> {
        final Container hopper = (Container) helper.getBlockEntity(pos.below());
        for (int i = 0; i < hopper.getContainerSize(); i++) {
          helper.assertTrue(hopper.getItem(i).isEmpty(), "the hopper pulled " + hopper.getItem(i) + " out of the microcontroller");
        }
        helper.assertTrue(!mc.info.components.get(0).isEmpty(), "the microcontroller lost its CPU");
        helper.succeed();
      });
  }

  /** A redstone change next to a microcontroller reaches its program as redstone_changed. */
  @GameTest(template = EMPTY, timeoutTicks = 300)
  public static void microcontrollerGetsRedstoneChangedEvents(final GameTestHelper helper) {
    final BlockPos pos = new BlockPos(2, 2, 2);
    TestMachines.runMicrocontroller(helper, pos, List.of(Constants.ItemName.CPUTier1, Constants.ItemName.RAMTier2, Constants.ItemName.RedstoneCardTier1),
      "local rs = component.proxy(component.list(\"redstone\")())\n"
        + "while true do\n"
        + "  local e, _, side, old, new = computer.pullSignal(1)\n"
        + "  if e == \"redstone_changed\" and new == 15 then rs.setOutput(1, 15) end\n"
        + "end\n",
      mc -> {
        final BlockPos abs = helper.absolutePos(pos);
        helper.assertTrue(helper.getLevel().getBestNeighborSignal(abs.above()) == 0, "the output is on before any input change");
        helper.setBlock(pos.relative(mc.facing().getOpposite()), net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(60, () -> {
          final int signal = helper.getLevel().getBestNeighborSignal(abs.above());
          helper.assertTrue(signal == 15, "the program did not react to redstone_changed (output above is " + signal + "); running: " + mc.machine().isRunning() + " " + mc.machine().lastError());
          helper.succeed();
        });
      });
  }

  /** A microcontroller's sides are network plugs: it shares a network with a neighbouring OC block and draws its power. */
  @GameTest(template = EMPTY, timeoutTicks = 300)
  public static void microcontrollerPlugsJoinNetworksAndDrawPower(final GameTestHelper helper) {
    final BlockPos pos = new BlockPos(2, 2, 2);
    final li.cil.oc.api.network.Node[] capacitorNode = new li.cil.oc.api.network.Node[1];
    final BlockPos[] behind = new BlockPos[1];
    TestMachines.runMicrocontroller(helper, pos, List.of(Constants.ItemName.CPUTier1, Constants.ItemName.RAMTier2),
      mc -> {
        behind[0] = pos.relative(mc.facing().getOpposite());
        helper.setBlock(behind[0], Blocks.CAPACITOR.get());
        return "while true do computer.pullSignal(1) end\n";
      },
      mc -> {
        capacitorNode[0] = ((li.cil.oc.api.network.Environment) helper.getBlockEntity(behind[0])).node();
        helper.assertTrue(capacitorNode[0].network() != null, "the capacitor joined no network");
        final var plug = mc.sidedNode(mc.facing().getOpposite());
        helper.assertTrue(plug != null && plug.network() == capacitorNode[0].network(), "the microcontroller's plug is not in the capacitor's network");
        final var connector = (li.cil.oc.api.network.Connector) capacitorNode[0];
        connector.changeBuffer(connector.localBufferSize());
        final double before = connector.localBuffer();
        ((li.cil.oc.api.network.Connector) mc.snooperNode).changeBuffer(-((li.cil.oc.api.network.Connector) mc.snooperNode).localBuffer() / 2);
        helper.runAfterDelay(60, () -> {
          helper.assertTrue(connector.localBuffer() < before, "the microcontroller drew no power from the capacitor (" + connector.localBuffer() + " of " + before + ")");
          helper.succeed();
        });
      });
  }

  /** A robot removed by anything but a player (explosion, other mods) still drops itself and its inventory. */
  @GameTest(template = EMPTY)
  public static void destroyedRobotDropsItselfAndItsInventory(final GameTestHelper helper) {
    final var data = new li.cil.oc.core.impl.common.item.data.RobotData();
    data.name = "boom";
    data.components.add(item(Constants.ItemName.InventoryUpgrade));
    final BlockPos pos = new BlockPos(2, 2, 2);
    helper.setBlock(pos, Blocks.ROBOT.get());
    final BlockPos abs = helper.absolutePos(pos);
    final var player = net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(), new com.mojang.authlib.GameProfile(java.util.UUID.fromString("5d1e2a6e-3f2b-4c39-9a7e-1d2f3b4c5d6f"), "oc-test-placer"));
    Blocks.ROBOT.get().setPlacedBy(helper.getLevel(), abs, helper.getLevel().getBlockState(abs), player, data.createItemStack());
    final var proxy = (li.cil.oc.core.impl.common.blockentity.RobotProxy) helper.getLevel().getBlockEntity(abs);
    proxy.robot.mainInventory().setItem(0, new ItemStack(net.minecraft.world.item.Items.DIAMOND, 5));
    helper.getLevel().destroyBlock(abs, false); // no player involved
    helper.runAfterDelay(2, () -> {
      int robots = 0, diamonds = 0, parts = -1;
      for (final ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(3))) {
        final var info = li.cil.oc.api.Items.get(entity.getItem());
        if (info != null && Constants.BlockName.Robot.equals(info.name())) {
          robots += entity.getItem().getCount();
          parts = new li.cil.oc.core.impl.common.item.data.RobotData(entity.getItem()).components.size();
        }
        if (entity.getItem().is(net.minecraft.world.item.Items.DIAMOND)) diamonds += entity.getItem().getCount();
      }
      helper.assertTrue(robots == 1 && diamonds == 5 && parts == 1, "dropped " + robots + " robot(s) with " + parts + " part(s) and " + diamonds + " diamonds; expected 1 robot with 1 part and 5 diamonds");
      helper.succeed();
    });
  }

  /** Cables and prints removed without a player (explosions, other mods) drop themselves, once. */
  @GameTest(template = EMPTY)
  public static void destroyedCableAndPrintDropThemselves(final GameTestHelper helper) {
    final BlockPos cable = new BlockPos(1, 2, 2), print = new BlockPos(3, 2, 2);
    helper.setBlock(cable, Blocks.CABLE.get());
    helper.setBlock(print, Blocks.PRINT.get());
    helper.runAfterDelay(5, () -> {
      helper.getLevel().destroyBlock(helper.absolutePos(cable), false);
      helper.getLevel().destroyBlock(helper.absolutePos(print), false);
      helper.runAfterDelay(2, () -> {
        int cables = 0, prints = 0;
        for (final ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(new BlockPos(2, 2, 2))).inflate(4))) {
          final var info = li.cil.oc.api.Items.get(entity.getItem());
          if (info == null) continue;
          if (Constants.BlockName.Cable.equals(info.name())) cables += entity.getItem().getCount();
          if (Constants.BlockName.Print.equals(info.name())) prints += entity.getItem().getCount();
        }
        helper.assertTrue(cables == 1 && prints == 1, "dropped " + cables + " cable(s) and " + prints + " print(s) instead of one each");
        helper.succeed();
      });
    });
  }

  /**
   * The chunk unload sequence (event, save, remove) applied to a microcontroller next to a capacitor: its nodes leave
   * the capacitor's network and its machine stops; a block entity loaded from the saved data gets its addresses back.
   */
  @GameTest(template = EMPTY, timeoutTicks = 400)
  public static void unloadedBlocksLeaveTheNetworkAndKeepTheirAddresses(final GameTestHelper helper) {
    final BlockPos pos = new BlockPos(2, 2, 2);
    final BlockPos[] behind = new BlockPos[1];
    TestMachines.runMicrocontroller(helper, pos, List.of(Constants.ItemName.CPUTier1, Constants.ItemName.RAMTier2),
      mc -> {
        behind[0] = pos.relative(mc.facing().getOpposite());
        helper.setBlock(behind[0], Blocks.CAPACITOR.get());
        return "while true do computer.pullSignal(1) end\n";
      },
      mc -> {
        final BlockPos abs = helper.absolutePos(pos);
        final var capacitor = ((li.cil.oc.api.network.Environment) helper.getBlockEntity(behind[0])).node();
        final var plug = mc.sidedNode(mc.facing().getOpposite());
        helper.assertTrue(plug != null && plug.network() == capacitor.network(), "the microcontroller is not in the capacitor's network");
        final String plugAddress = plug.address(), snooperAddress = mc.snooperNode.address();
        helper.assertTrue(plugAddress != null && snooperAddress != null, "nodes without addresses");
        // what ChunkMap.scheduleUnload does: the unload event, the save, then the removal of the block entities
        mc.markUnloading();
        final var saved = mc.saveWithoutMetadata();
        helper.getLevel().removeBlockEntity(abs);
        helper.runAfterDelay(3, () -> {
          helper.assertTrue(capacitor.network().node(plugAddress) == null, "the unloaded microcontroller's plug is still in the capacitor's network");
          helper.assertTrue(plug.network() == null, "the plug still has a network");
          helper.assertTrue(!mc.machine().isRunning(), "the unloaded microcontroller's machine is still running");
          // the chunk comes back: a fresh block entity loads the saved data
          final var reloaded = (li.cil.oc.core.impl.common.blockentity.Microcontroller) helper.getLevel().getBlockEntity(abs);
          helper.assertTrue(reloaded != null && reloaded != mc, "no fresh block entity after the removal");
          reloaded.load(saved);
          helper.runAfterDelay(40, () -> {
            final var newPlug = reloaded.sidedNode(reloaded.facing().getOpposite());
            helper.assertTrue(newPlug != null && plugAddress.equals(newPlug.address()), "the plug came back as " + (newPlug == null ? null : newPlug.address()) + " instead of " + plugAddress);
            helper.assertTrue(capacitor.network().node(plugAddress) == newPlug, "the capacitor's network does not hold the reloaded plug under its address");
            helper.assertTrue(snooperAddress.equals(reloaded.snooperNode.address()), "the microcontroller component came back as " + reloaded.snooperNode.address() + " instead of " + snooperAddress);
            helper.assertTrue(reloaded.machine().isRunning(), "the reloaded machine did not resume: " + reloaded.machine().lastError());
            helper.succeed();
          });
        });
      });
  }
}

