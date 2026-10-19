package li.cil.oc.neoforge.gametest;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import li.cil.oc.api.driver.DeviceInfo;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.ManagedPeripheral;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.SidedComponent;
import li.cil.oc.api.network.SimpleComponent;
import li.cil.oc.core.Constants;
import li.cil.oc.core.impl.common.blockentity.Case;
import li.cil.oc.neoforge.OpenComputers;
import li.cil.oc.neoforge.common.capability.OCBlockCapabilities;
import li.cil.oc.neoforge.common.capability.SimpleComponentCapability;
import li.cil.oc.neoforge.common.init.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Other mods' block entities implementing SimpleComponent are components, as OC 1.12 made them by class transformation:
 * they join the network next to them, computers call their methods on them, and their nodes keep their addresses and
 * leave the network with them.
 */
@GameTestHolder(OpenComputers.ID)
@PrefixGameTestTemplate(false)
public final class SimpleComponentTests {
  private static final String EMPTY = "empty";
  private static final BlockPos AT = new BlockPos(2, 2, 2);

  private SimpleComponentTests() {
  }

  /** Another mod's block entity made a component the simple way, with a callback, a peripheral method and device info. */
  public static final class SimpleFurnace extends FurnaceBlockEntity implements SimpleComponent, SidedComponent, ManagedPeripheral, DeviceInfo {
    public String name = "simple_furnace";
    // the side it does not connect on
    public Direction closed;
    public int greeted;
    public int changed;

    public SimpleFurnace(final BlockPos pos, final BlockState state) {
      super(pos, state);
    }

    @Override
    public String getComponentName() {
      return name;
    }

    @Override
    public void setChanged() {
      changed++;
      super.setChanged();
    }

    @Override
    public boolean canConnectNode(final Direction side) {
      return side != closed;
    }

    @Callback(doc = "function(name:string):string -- Greets.")
    public Object[] greet(final Context context, final Arguments args) {
      greeted++;
      return new Object[]{"hello " + args.checkString(0)};
    }

    @Override
    public String[] methods() {
      return new String[]{"peripheral"};
    }

    @Override
    public Object[] invoke(final String method, final Context context, final Arguments args) {
      return new Object[]{"peripheral " + method};
    }

    @Override
    public Map<String, String> getDeviceInfo() {
      return Map.of(DeviceAttribute.Class, "generic", DeviceAttribute.Description, "simple");
    }
  }

  /** Puts a simple furnace where a furnace was placed, as a mod's block would have it. */
  private static SimpleFurnace placeFurnace(final GameTestHelper helper, final BlockPos pos, final Direction closed) {
    helper.setBlock(pos, net.minecraft.world.level.block.Blocks.FURNACE);
    final BlockPos abs = helper.absolutePos(pos);
    final SimpleFurnace furnace = new SimpleFurnace(abs, helper.getLevel().getBlockState(abs));
    furnace.closed = closed;
    helper.getLevel().setBlockEntity(furnace);
    return furnace;
  }

  private static Context context() {
    return (Context) java.lang.reflect.Proxy.newProxyInstance(Context.class.getClassLoader(),
      new Class<?>[]{Context.class}, (proxy, method, args) -> method.getReturnType() == boolean.class ? false : null);
  }

  private static Node node(final SimpleFurnace furnace) {
    final var provider = OCBlockCapabilities.SIMPLE_COMPONENT_PROVIDER.getCapability(furnace, null);
    return provider != null ? provider.node() : null;
  }

  private static Node capacitor(final GameTestHelper helper, final BlockPos pos) {
    return ((li.cil.oc.api.network.Environment) helper.getBlockEntity(pos)).node();
  }

  /**
   * A computer calls a simple component placed next to it: its callback (on the block entity), its peripheral method,
   * its device info and the analyzer reach it (it joined no network when placed next to one, and its node had no host:
   * every call failed).
   */
  @GameTest(template = EMPTY, timeoutTicks = 2400)
  public static void computersCallSimpleComponents(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.CASE_CREATIVE.get());
    final var computer = (Case) helper.getBlockEntity(AT);
    final String program = "local addr = component.list('simple_furnace')()\n"
      + "if not addr then error('done:no simple_furnace', 0) end\n"
      + "local p = component.proxy(addr)\n"
      + "local methods = component.methods(addr)\n"
      + "error('done:' .. tostring(p.greet('oc')) .. '|' .. tostring(p.peripheral()) .. '|' .. tostring(methods.greet ~= nil and methods.peripheral ~= nil) .. '|' .. addr, 0)\n";
    for (final ItemStack part : List.of(li.cil.oc.api.Items.get(Constants.ItemName.CPUTier1).createItemStack(1),
      li.cil.oc.api.Items.get(Constants.ItemName.RAMTier2).createItemStack(1),
      li.cil.oc.api.Items.registerEEPROM("simple component test", program.getBytes(StandardCharsets.UTF_8), null, false))) {
      int slot = -1;
      for (int i = 0; i < computer.getContainerSize() && slot < 0; i++) {
        if (computer.getItem(i).isEmpty() && computer.canPlaceItem(i, part)) slot = i;
      }
      helper.assertTrue(slot >= 0, "no case slot takes " + part);
      computer.setItem(slot, part);
    }
    // placed next to the computer once that is in its network
    helper.runAfterDelay(5, () -> {
      final SimpleFurnace furnace = placeFurnace(helper, AT.east(), null);
      helper.runAfterDelay(5, () -> {
        helper.assertTrue(computer.machine().start(), "the case did not start: " + computer.machine().lastError());
        TestMachines.when(helper, () -> !computer.machine().isRunning(), 2000, () -> {
          final Node node = node(furnace);
          final String error = String.valueOf(computer.machine().lastError());
          helper.assertTrue(node != null && node.address() != null, "the simple furnace has no node with an address: " + node);
          helper.assertTrue(error.contains("done:hello oc|peripheral peripheral|true|" + node.address()), "the computer got " + error);
          helper.assertTrue(furnace.greeted == 1, "the callback ran " + furnace.greeted + " times on the block entity");
          // (called here: the Lua call pauses the machine a second)
          final var devices = (Map<?, ?>) ((li.cil.oc.core.impl.server.machine.MachineBase) computer.machine()).getDeviceInfo(context(), new li.cil.oc.core.impl.server.machine.ArgumentsImpl(new Object[0]))[0];
          final var info = (Map<?, ?>) devices.get(node.address());
          helper.assertTrue(info != null && "simple".equals(info.get(DeviceInfo.DeviceAttribute.Description)), "the computer's device info for the simple furnace is " + info);
          final var player = FakePlayerFactory.getMinecraft(helper.getLevel());
          helper.assertTrue(li.cil.oc.core.impl.common.item.Analyzer.analyze(furnace, player, Direction.UP, 0, 0, 0), "the analyzer does not read the simple furnace");
          helper.succeed();
        });
      });
    });
  }

  /**
   * A simple component keeps its address through a save and load, and its node leaves the network with it (its node
   * was not saved, and stayed in the network after the block was gone).
   */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void simpleComponentsKeepTheirAddressAndLeaveWithTheirBlock(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.CAPACITOR.get());
    final SimpleFurnace furnace = placeFurnace(helper, AT.east(), null);
    helper.runAfterDelay(5, () -> {
      final Node network = capacitor(helper, AT);
      final Node node = node(furnace);
      helper.assertTrue(node != null && node.network() != null && node.network() == network.network(), "the simple furnace is not in the capacitor's network: " + node);
      final String address = node.address();
      final BlockPos abs = helper.absolutePos(AT.east());
      final var saved = furnace.saveWithFullMetadata();
      helper.getLevel().removeBlockEntity(abs);
      helper.assertTrue(node.network() == null && network.network().node(address) == null, "the removed simple furnace's node stayed in the network");
      final SimpleFurnace loaded = new SimpleFurnace(abs, helper.getLevel().getBlockState(abs));
      loaded.load(saved);
      helper.getLevel().getChunkAt(abs).addAndRegisterBlockEntity(loaded);
      loaded.changed = 0;
      helper.runAfterDelay(5, () -> {
        final Node again = node(loaded);
        helper.assertTrue(again != null && again.network() == network.network(), "the loaded simple furnace is not in the capacitor's network: " + again);
        helper.assertTrue(address.equals(again.address()), "the loaded simple furnace has the address " + again.address() + " instead of " + address);
        helper.assertTrue(loaded.changed == 0, "the loaded simple furnace, with its saved address, was marked changed " + loaded.changed + " times");
        // a chunk unload (what LevelChunk.clearAllBlockEntities does)
        loaded.onChunkUnloaded();
        loaded.setRemoved();
        helper.assertTrue(again.network() == null && network.network().node(address) == null, "the unloaded simple furnace's node stayed in the network");
        helper.succeed();
      });
    });
  }

  /** A simple component without a name leaves the network, and comes back with its address. */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void simpleComponentsWithoutANameLeaveTheNetwork(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.CAPACITOR.get());
    final SimpleFurnace furnace = placeFurnace(helper, AT.east(), null);
    helper.runAfterDelay(5, () -> {
      final Node network = capacitor(helper, AT);
      final Node node = node(furnace);
      helper.assertTrue(node != null && node.network() == network.network(), "the simple furnace is not in the capacitor's network: " + node);
      final String address = node.address();
      furnace.name = "";
      helper.assertTrue(node(furnace) == null && node.network() == null, "the simple furnace without a name kept its node in the network");
      furnace.name = "simple_furnace";
      li.cil.oc.api.Network.joinOrCreateNetwork(furnace);
      final Node again = node(furnace);
      helper.assertTrue(again != null && again.network() == network.network() && address.equals(again.address()),
        "the named simple furnace came back as " + (again == null ? null : again.address()) + " instead of " + address);
      helper.succeed();
    });
  }

  /**
   * A simple component made before it was in its level (a chunk being generated) joins when its chunk loads (its join
   * found no level, and nothing joined it later).
   */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void simpleComponentsJoinWhenTheirChunkLoads(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.CAPACITOR.get());
    helper.setBlock(AT.east(), net.minecraft.world.level.block.Blocks.FURNACE);
    final BlockPos abs = helper.absolutePos(AT.east());
    final SimpleFurnace furnace = new SimpleFurnace(abs, helper.getLevel().getBlockState(abs));
    helper.runAfterDelay(3, () -> {
      final var chunk = helper.getLevel().getChunkAt(abs);
      chunk.addAndRegisterBlockEntity(furnace);
      SimpleComponentCapability.onChunkLoad(new ChunkEvent.Load(chunk, false));
      helper.runAfterDelay(3, () -> {
        final Node node = node(furnace);
        helper.assertTrue(node != null && node.network() == capacitor(helper, AT).network(), "the simple furnace did not join when its chunk loaded: " + node);
        helper.succeed();
      });
    });
  }

  /** A simple component does not connect on a side its SidedComponent closes (it connected on all). */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void simpleComponentsConnectOnlyOnOpenSides(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.CAPACITOR.get());
    final SimpleFurnace furnace = placeFurnace(helper, AT.east(), Direction.WEST);
    helper.runAfterDelay(5, () -> {
      final Node network = capacitor(helper, AT);
      final Node node = node(furnace);
      helper.assertTrue(node == null || node.network() != network.network(), "the simple furnace connected on its closed side");
      helper.succeed();
    });
  }

  /**
   * A new simple component's address is saved: it marks itself changed (a chunk is saved only when marked). The block
   * entity's own marks are counted: the chunk's flag is shared, and reset by the server's incremental saving.
   */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void newSimpleComponentAddressesAreSaved(final GameTestHelper helper) {
    final SimpleFurnace furnace = placeFurnace(helper, AT, null);
    furnace.changed = 0;
    helper.runAfterDelay(3, () -> {
      final Node node = node(furnace);
      helper.assertTrue(node != null && node.address() != null, "the simple furnace has no address: " + node);
      helper.assertTrue(furnace.changed == 1, "the simple furnace's new address marked it changed " + furnace.changed + " times instead of once");
      helper.succeed();
    });
  }

  /** A block entity that is an environment itself as well. */
  public static final class EnvironmentFurnace extends FurnaceBlockEntity implements SimpleComponent, li.cil.oc.api.network.Environment {
    public EnvironmentFurnace(final BlockPos pos, final BlockState state) {
      super(pos, state);
    }

    @Override
    public String getComponentName() {
      return "environment_furnace";
    }

    @Override
    public Node node() {
      return null;
    }

    @Override
    public void onConnect(final Node node) {
    }

    @Override
    public void onDisconnect(final Node node) {
    }

    @Override
    public void onMessage(final li.cil.oc.api.network.Message message) {
    }
  }

  /** A simple component that is an environment itself gets no second node (it got one, joined and saved). */
  @GameTest(template = EMPTY)
  public static void environmentsGetNoSimpleComponentNode(final GameTestHelper helper) {
    helper.setBlock(AT, net.minecraft.world.level.block.Blocks.FURNACE);
    final BlockPos abs = helper.absolutePos(AT);
    final var furnace = new EnvironmentFurnace(abs, helper.getLevel().getBlockState(abs));
    helper.assertTrue(OCBlockCapabilities.SIMPLE_COMPONENT_PROVIDER.getCapability(furnace, null) == null && !furnace.saveWithFullMetadata().toString().contains("oc:node"),
      "an environment got a simple component's node as well");
    helper.succeed();
  }
}
