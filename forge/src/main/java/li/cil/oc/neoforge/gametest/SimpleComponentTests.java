package li.cil.oc.neoforge.gametest;

import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import li.cil.oc.api.driver.DeviceInfo;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.ManagedPeripheral;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.SidedComponent;
import li.cil.oc.api.network.SimpleComponent;
import li.cil.oc.core.Constants;
import li.cil.oc.core.impl.common.blockentity.Case;
import li.cil.oc.core.impl.server.machine.ArgumentsImpl;
import li.cil.oc.core.impl.util.BlockPosition;
import li.cil.oc.neoforge.OpenComputers;
import li.cil.oc.neoforge.common.capability.OCBlockCapabilities;
import li.cil.oc.neoforge.common.init.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Other mods' block entities implementing SimpleComponent are components, as OC 1.12 made them by class transformation:
 * they join the network next to them, computers call their methods on them, their own onConnect, onDisconnect and
 * onMessage are called, their nodes keep their addresses and leave the network with them, and what took an Environment
 * in OC 1.12 (cables, the MFU, the debug card, the analyzer) takes them.
 */
@GameTestHolder(OpenComputers.ID)
@PrefixGameTestTemplate(false)
public final class SimpleComponentTests {
  private static final String EMPTY = "empty";
  private static final BlockPos AT = new BlockPos(2, 2, 2);

  private SimpleComponentTests() {
  }

  /**
   * Another mod's block entity made a component the simple way: a callback (no ManagedPeripheral: its callbacks are
   * found once per class), device info, sides it may close, and OC's onConnect, onDisconnect and onMessage of its own.
   */
  public static class SimpleFurnace extends FurnaceBlockEntity implements SimpleComponent, SidedComponent, DeviceInfo {
    public String name = "simple_furnace";
    // the side it does not connect on
    public Direction closed;
    public int greeted;
    public int changed;
    public final List<Node> connected = new ArrayList<>();
    public final List<Node> disconnected = new ArrayList<>();
    public final List<String> messages = new ArrayList<>();
    // its capability, kept, as the docs advise, to know its own node by
    public li.cil.oc.api.capability.SimpleComponentProvider provider;
    public int ownDisconnects;

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
    public Map<String, String> getDeviceInfo() {
      return Map.of(DeviceAttribute.Class, "generic", DeviceAttribute.Description, "simple");
    }

    public void onConnect(final Node node) {
      connected.add(node);
    }

    public void onDisconnect(final Node node) {
      disconnected.add(node);
      if (provider != null && node == provider.node()) ownDisconnects++;
    }

    public void onMessage(final Message message) {
      messages.add(message.name());
    }
  }

  /** One with a peripheral method too (its callbacks are found anew each time). */
  public static final class PeripheralFurnace extends SimpleFurnace implements ManagedPeripheral {
    public PeripheralFurnace(final BlockPos pos, final BlockState state) {
      super(pos, state);
      name = "peripheral_furnace";
    }

    @Override
    public String[] methods() {
      return new String[]{"peripheral"};
    }

    @Override
    public Object[] invoke(final String method, final Context context, final Arguments args) {
      return new Object[]{"peripheral " + method};
    }
  }

  /** One of a class other code cannot reach, with an onConnect of its own. */
  private static final class HiddenFurnace extends SimpleFurnace {
    private int hiddenConnects;

    private HiddenFurnace(final BlockPos pos, final BlockState state) {
      super(pos, state);
      name = "hidden_furnace";
    }

    @Override
    public void onConnect(final Node node) {
      hiddenConnects++;
      super.onConnect(node);
    }
  }

  /** Puts the furnace where a furnace block was placed, as a mod's block would have it. */
  private static <T extends SimpleFurnace> T placeFurnace(final GameTestHelper helper, final BlockPos pos, final Direction closed,
                                                         final java.util.function.BiFunction<BlockPos, BlockState, T> make) {
    helper.setBlock(pos, net.minecraft.world.level.block.Blocks.FURNACE);
    final BlockPos abs = helper.absolutePos(pos);
    final T furnace = make.apply(abs, helper.getLevel().getBlockState(abs));
    furnace.closed = closed;
    helper.getLevel().setBlockEntity(furnace);
    return furnace;
  }

  private static SimpleFurnace placeFurnace(final GameTestHelper helper, final BlockPos pos, final Direction closed) {
    return placeFurnace(helper, pos, closed, SimpleFurnace::new);
  }

  private static Context context() {
    return (Context) java.lang.reflect.Proxy.newProxyInstance(Context.class.getClassLoader(),
      new Class<?>[]{Context.class}, (proxy, method, args) -> method.getReturnType() == boolean.class ? false : null);
  }

  /** The node, as a neighbour looks it up. */
  private static Node node(final SimpleFurnace furnace) {
    final var provider = OCBlockCapabilities.SIMPLE_COMPONENT_PROVIDER.getCapability(furnace, null);
    return provider != null ? provider.node() : null;
  }

  private static Node capacitor(final GameTestHelper helper, final BlockPos pos) {
    return ((li.cil.oc.api.network.Environment) helper.getBlockEntity(pos)).node();
  }

  /** Runs the action with the player taken for a real one by the class's fakePlayerCheck (the analyzer's, the debugger's). */
  private static void asRealPlayer(final Class<?> owner, final Player player, final Runnable action) {
    try {
      final var check = owner.getDeclaredField("fakePlayerCheck");
      check.setAccessible(true);
      @SuppressWarnings("unchecked") final Predicate<Player> previous = (Predicate<Player>) check.get(null);
      check.set(null, (Predicate<Player>) p -> p != player && previous.test(p));
      try {
        action.run();
      } finally {
        check.set(null, previous);
      }
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  /** What the analyzer tells a player about the block entity: the arguments of its messages. */
  private static List<Object> analyze(final GameTestHelper helper, final Object blockEntity) {
    final List<Object> said = new ArrayList<>();
    final FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "oc-analyzer")) {
      @Override
      public void sendSystemMessage(final Component message) {
        if (message.getContents() instanceof TranslatableContents translatable) said.addAll(List.of(translatable.getArgs()));
      }
    };
    asRealPlayer(li.cil.oc.core.impl.common.item.Analyzer.class, player, () ->
      helper.assertTrue(li.cil.oc.core.impl.common.item.Analyzer.analyze(blockEntity, player, Direction.UP, 0, 0, 0), "the analyzer does not take " + blockEntity));
    return said;
  }

  /**
   * A computer calls simple components placed next to it, on the block entities: callbacks found once per class or
   * anew (peripheral methods), device info, and the analyzer reads their node (they joined no network when placed
   * next to one, and their nodes had no host: every call failed).
   */
  @GameTest(template = EMPTY, timeoutTicks = 2400)
  public static void computersCallSimpleComponents(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.CASE_CREATIVE.get());
    final var computer = (Case) helper.getBlockEntity(AT);
    final String program = "local addr, paddr = component.list('simple_furnace')(), component.list('peripheral_furnace')()\n"
      + "if not addr or not paddr then error('done:missing ' .. tostring(addr) .. ' ' .. tostring(paddr), 0) end\n"
      + "local p, q = component.proxy(addr), component.proxy(paddr)\n"
      + "local methods = component.methods(addr)\n"
      + "error('done:' .. tostring(p.greet('oc')) .. '|' .. tostring(q.peripheral()) .. '|' .. tostring(q.greet('pc')) .. '|' .. tostring(methods.greet ~= nil) .. '|' .. addr, 0)\n";
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
      final PeripheralFurnace peripheral = placeFurnace(helper, AT.west(), null, PeripheralFurnace::new);
      helper.runAfterDelay(5, () -> {
        helper.assertTrue(computer.machine().start(), "the case did not start: " + computer.machine().lastError());
        TestMachines.when(helper, () -> !computer.machine().isRunning(), 2000, () -> {
          final Node node = node(furnace);
          final String error = String.valueOf(computer.machine().lastError());
          helper.assertTrue(node != null && node.address() != null, "the simple furnace has no node with an address: " + node);
          helper.assertTrue(error.contains("done:hello oc|peripheral peripheral|hello pc|true|" + node.address()), "the computer got " + error);
          helper.assertTrue(furnace.greeted == 1 && peripheral.greeted == 1, "the callbacks ran " + furnace.greeted + " and " + peripheral.greeted + " times on the block entities");
          // (called here: the Lua call pauses the machine a second)
          final var devices = (Map<?, ?>) ((li.cil.oc.core.impl.server.machine.MachineBase) computer.machine()).getDeviceInfo(context(), new ArgumentsImpl(new Object[0]))[0];
          final var info = (Map<?, ?>) devices.get(node.address());
          helper.assertTrue(info != null && "simple".equals(info.get(DeviceInfo.DeviceAttribute.Description)), "the computer's device info for the simple furnace is " + info);
          final List<Object> said = analyze(helper, furnace);
          helper.assertTrue(said.contains(node.address()), "the analyzer told " + said + ", not the simple furnace's address");
          helper.succeed();
        });
      });
    });
  }

  /**
   * A simple component keeps its address through a save and load, gets OC's onConnect, onDisconnect and onMessage
   * (OC 1.12 kept its own), and its node leaves the network with it (its node was not saved, and stayed in the network
   * after the block was gone).
   */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void simpleComponentsKeepTheirAddressAndLeaveWithTheirBlock(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.CAPACITOR.get());
    final SimpleFurnace furnace = placeFurnace(helper, AT.east(), null);
    helper.runAfterDelay(5, () -> {
      final Node network = capacitor(helper, AT);
      final Node node = node(furnace);
      helper.assertTrue(node != null && node.network() != null && node.network() == network.network(), "the simple furnace is not in the capacitor's network: " + node);
      helper.assertTrue(furnace.connected.contains(node) && furnace.connected.contains(network), "the simple furnace was told of connections to " + furnace.connected);
      network.sendToReachable("oc.test");
      helper.assertTrue(furnace.messages.contains("oc.test"), "the simple furnace got the messages " + furnace.messages);
      final String address = node.address();
      final BlockPos abs = helper.absolutePos(AT.east());
      final var saved = furnace.saveWithFullMetadata();
      furnace.provider = OCBlockCapabilities.SIMPLE_COMPONENT_PROVIDER.getCapability(furnace, null);
      helper.getLevel().removeBlockEntity(abs);
      helper.assertTrue(node.network() == null && network.network().node(address) == null, "the removed simple furnace's node stayed in the network");
      // (a removed node is told of its own removal; the others, of it)
      helper.assertTrue(furnace.disconnected.contains(node), "the removed simple furnace was told of disconnections from " + furnace.disconnected);
      helper.assertTrue(furnace.ownDisconnects == 1, "the removed simple furnace knew its own node's removal " + furnace.ownDisconnects + " times (its capability's node() was none by then)");
      helper.assertTrue(furnace.provider.node() == null, "the removed simple furnace's capability still has a node");
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

  /** The own onConnect of a simple component is called when inherited, and in a class other code cannot reach. */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void ownMethodsOfInheritingAndHiddenClassesAreCalled(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.CAPACITOR.get());
    final PeripheralFurnace inheriting = placeFurnace(helper, AT.east(), null, PeripheralFurnace::new);
    final HiddenFurnace hidden = placeFurnace(helper, AT.west(), null, HiddenFurnace::new);
    helper.runAfterDelay(5, () -> {
      helper.assertTrue(!inheriting.connected.isEmpty(), "the inherited onConnect was not called");
      helper.assertTrue(hidden.hiddenConnects > 0, "the onConnect of a class other code cannot reach was not called");
      helper.succeed();
    });
  }

  /** A simple component without a name leaves the network, keeps its address in its saved data, and comes back with it. */
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
      helper.assertTrue(furnace.saveWithFullMetadata().toString().contains(address), "the simple furnace without a name saved no address");
      furnace.name = "simple_furnace";
      li.cil.oc.api.Network.joinOrCreateNetwork(furnace);
      final Node again = node(furnace);
      helper.assertTrue(again != null && again.network() == network.network() && address.equals(again.address()),
        "the named simple furnace came back as " + (again == null ? null : again.address()) + " instead of " + address);
      helper.succeed();
    });
  }

  /**
   * A simple component named only after its join joins when it is looked up (by a neighbour), and its new address is
   * saved (it stayed out of the network: neighbours join only nodes in one; and only a join of its own marked it).
   */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void simpleComponentsNamedLaterJoinAndAreSaved(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.CAPACITOR.get());
    final SimpleFurnace furnace = placeFurnace(helper, AT.east(), null);
    furnace.name = "";
    helper.runAfterDelay(5, () -> {
      helper.assertTrue(node(furnace) == null, "the simple furnace without a name has a node");
      furnace.name = "simple_furnace";
      furnace.changed = 0;
      final Node node = node(furnace);
      helper.runAfterDelay(3, () -> {
        helper.assertTrue(node != null && node.network() == capacitor(helper, AT).network(), "the simple furnace named later did not join: " + node);
        helper.assertTrue(furnace.changed == 1, "the simple furnace's new address marked it changed " + furnace.changed + " times instead of once");
        helper.succeed();
      });
    });
  }

  /**
   * A simple component made before it was in its level (a chunk being generated) joins when its chunk loads, through
   * the event (its join found no level, and nothing joined it later). Made long enough before for its own tries to end.
   */
  @GameTest(template = EMPTY, timeoutTicks = 300)
  public static void simpleComponentsJoinWhenTheirChunkLoads(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.CAPACITOR.get());
    helper.setBlock(AT.east(), net.minecraft.world.level.block.Blocks.FURNACE);
    final BlockPos abs = helper.absolutePos(AT.east());
    final SimpleFurnace furnace = new SimpleFurnace(abs, helper.getLevel().getBlockState(abs));
    helper.runAfterDelay(150, () -> {
      final var chunk = helper.getLevel().getChunkAt(abs);
      chunk.addAndRegisterBlockEntity(furnace);
      MinecraftForge.EVENT_BUS.post(new ChunkEvent.Load(chunk, false));
      helper.runAfterDelay(3, () -> {
        final Node node = node(furnace);
        helper.assertTrue(node != null && node.network() == capacitor(helper, AT).network(), "the simple furnace did not join when its chunk loaded: " + node);
        helper.succeed();
      });
    });
  }

  /** A simple component put into its level a few ticks after it was made joins (its only join found no level). */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void simpleComponentsPutInLaterJoin(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.CAPACITOR.get());
    helper.setBlock(AT.east(), net.minecraft.world.level.block.Blocks.FURNACE);
    final BlockPos abs = helper.absolutePos(AT.east());
    final SimpleFurnace furnace = new SimpleFurnace(abs, helper.getLevel().getBlockState(abs));
    helper.runAfterDelay(5, () -> {
      helper.getLevel().setBlockEntity(furnace);
      helper.runAfterDelay(3, () -> {
        final Node node = node(furnace);
        helper.assertTrue(node != null && node.network() == capacitor(helper, AT).network(), "the simple furnace put in later did not join: " + node);
        helper.succeed();
      });
    });
  }

  /**
   * A simple component made for something else, with the level set but not in it, does not join the network next to
   * its place. Made once that network is there: a join of its own would connect to it.
   */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void simpleComponentsNotInTheLevelDoNotJoin(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.CAPACITOR.get());
    helper.setBlock(AT.east(), net.minecraft.world.level.block.Blocks.FURNACE);
    final BlockPos abs = helper.absolutePos(AT.east());
    helper.runAfterDelay(5, () -> {
      helper.assertTrue(capacitor(helper, AT).network() != null, "the capacitor is in no network");
      final SimpleFurnace furnace = new SimpleFurnace(abs, helper.getLevel().getBlockState(abs));
      furnace.setLevel(helper.getLevel());
      helper.runAfterDelay(3, () -> {
        final Node node = node(furnace);
        helper.assertTrue(node == null || node.network() != capacitor(helper, AT).network(), "a simple furnace not in the level joined the network next to its place");
        helper.succeed();
      });
    });
  }

  /** A removed simple component put back, its capabilities revived (CapabilityProvider.reviveCaps), joins again with its address. */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void revivedSimpleComponentsJoinAgain(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.CAPACITOR.get());
    final SimpleFurnace furnace = placeFurnace(helper, AT.east(), null);
    final BlockPos abs = helper.absolutePos(AT.east());
    helper.runAfterDelay(5, () -> {
      final Node node = node(furnace);
      helper.assertTrue(node != null && node.network() == capacitor(helper, AT).network(), "the simple furnace is not in the capacitor's network: " + node);
      final String address = node.address();
      helper.getLevel().removeBlockEntity(abs);
      furnace.reviveCaps();
      helper.getLevel().setBlockEntity(furnace);
      final Node looked = node(furnace); // (as a neighbour would)
      helper.runAfterDelay(3, () -> {
        final Node again = node(furnace);
        helper.assertTrue(again != null && again.network() == capacitor(helper, AT).network() && address.equals(again.address()),
          "the revived simple furnace came back as " + again + " (" + (again == null ? null : again.address()) + " instead of " + address + "), looked up as " + looked);
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

  /** An environment that is a SidedComponent too. */
  public static final class SidedNodeFurnace extends FurnaceBlockEntity implements li.cil.oc.api.network.Environment, SidedComponent {
    private final Node node = li.cil.oc.api.Network.newNode(this, li.cil.oc.api.network.Visibility.Network).create();

    public SidedNodeFurnace(final BlockPos pos, final BlockState state) {
      super(pos, state);
    }

    @Override
    public boolean canConnectNode(final Direction side) {
      return side != Direction.WEST;
    }

    @Override
    public Node node() {
      return node;
    }

    @Override
    public void onConnect(final Node node) {
    }

    @Override
    public void onDisconnect(final Node node) {
    }

    @Override
    public void onMessage(final Message message) {
    }
  }

  /** An environment's closed SidedComponent sides have no node, as in OC 1.12 (they connected). */
  @GameTest(template = EMPTY)
  public static void environmentsConnectOnlyOnOpenSides(final GameTestHelper helper) {
    helper.setBlock(AT, net.minecraft.world.level.block.Blocks.FURNACE);
    final BlockPos abs = helper.absolutePos(AT);
    final var furnace = new SidedNodeFurnace(abs, helper.getLevel().getBlockState(abs));
    helper.assertTrue(li.cil.oc.core.impl.server.network.Network.getNetworkNode(furnace, Direction.WEST) == null, "an environment has a node on its closed side");
    helper.assertTrue(li.cil.oc.core.impl.server.network.Network.getNetworkNode(furnace, Direction.EAST) == furnace.node(), "an environment has no node on its open side");
    helper.succeed();
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

  /**
   * A simple component readdressed in a merge (another node with its address joined it) is saved with its new address:
   * marked on connect (only its own join marked it, and there is none in a merge).
   */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void readdressedSimpleComponentsAreSaved(final GameTestHelper helper) {
    final SimpleFurnace furnace = placeFurnace(helper, AT, null);
    helper.runAfterDelay(3, () -> {
      final Node node = node(furnace);
      helper.assertTrue(node != null && node.network() != null, "the simple furnace is in no network: " + node);
      final String address = node.address();
      furnace.changed = 0;
      final li.cil.oc.api.network.Environment host = new li.cil.oc.api.network.Environment() {
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
        public void onMessage(final Message message) {
        }
      };
      final Node other = li.cil.oc.api.Network.newNode(host, li.cil.oc.api.network.Visibility.Network).create();
      ((li.cil.oc.core.impl.server.network.Node) other).address_$eq(address);
      li.cil.oc.api.Network.joinNewNetwork(other);
      try {
        other.connect(node); // (the joined network's duplicates take new addresses)
        helper.assertTrue(!address.equals(node.address()) && node.network() == other.network(), "the simple furnace was not readdressed into the other node's network: " + node.address());
        helper.assertTrue(furnace.changed == 1, "the readdressed simple furnace was marked changed " + furnace.changed + " times instead of once");
      } finally {
        other.remove();
      }
      helper.succeed();
    });
  }

  /**
   * An MFU bound to a simple component connects to its node, as to an environment's (it bound a driver for the block),
   * and lets go of it when out of energy (the capacitor's network has none).
   */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void mfusConnectToSimpleComponents(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.CAPACITOR.get());
    final SimpleFurnace furnace = placeFurnace(helper, AT.east(), null);
    final BlockPos abs = helper.absolutePos(AT.east());
    helper.runAfterDelay(5, () -> {
      final var mfu = new li.cil.oc.neoforge.server.component.UpgradeMF((EnvironmentHost) helper.getBlockEntity(AT),
        BlockPosition.apply(abs.getX(), abs.getY(), abs.getZ(), helper.getLevel()), Direction.WEST);
      li.cil.oc.api.Network.joinNewNetwork(mfu.node());
      boolean handedOn = false;
      try {
        final Node node = node(furnace);
        helper.assertTrue(node != null && mfu.node().isNeighborOf(node), "the MFU did not connect to the simple furnace's node");
        // (its energy is taken every tickFrequency ticks)
        final double frequency = li.cil.oc.core.impl.OCSettings.get().tickFrequency;
        handedOn = true;
        TestMachines.when(helper, () -> helper.getLevel().getGameTime() % frequency == 0, 40, () -> {
          try {
            mfu.update();
            helper.assertTrue(!mfu.node().isNeighborOf(node), "the MFU out of energy kept the simple furnace's node");
          } finally {
            mfu.node().remove();
          }
          helper.succeed();
        });
      } finally {
        if (!handedOn) mfu.node().remove();
      }
    });
  }

  /** The debug card connects to a simple component's node (it found none). */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void debugCardsConnectToSimpleComponents(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.CAPACITOR.get());
    final SimpleFurnace furnace = placeFurnace(helper, AT.east(), null);
    final BlockPos abs = helper.absolutePos(AT.east());
    helper.runAfterDelay(5, () -> {
      final var card = new li.cil.oc.neoforge.server.component.DebugCard((EnvironmentHost) helper.getBlockEntity(AT));
      li.cil.oc.api.Network.joinNewNetwork(card.node());
      try {
        final Object[] result = card.connectToBlock(context(), new ArgumentsImpl(new Object[]{abs.getX(), abs.getY(), abs.getZ()}));
        final Node node = node(furnace);
        helper.assertTrue(node != null && card.node().isNeighborOf(node), "the debug card did not connect to the simple furnace's node: " + java.util.Arrays.toString(result));
      } finally {
        card.node().remove();
      }
      helper.succeed();
    });
  }

  /**
   * A debug card whose remote block has no node any more forgets it when it joins, as in OC 1.12 (it connected to none,
   * which threw, and kept the place).
   */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void debugCardsForgetRemoteBlocksWithoutANode(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.CAPACITOR.get());
    final BlockPos air = helper.absolutePos(AT.east());
    helper.runAfterDelay(3, () -> {
      final var card = new li.cil.oc.neoforge.server.component.DebugCard((EnvironmentHost) helper.getBlockEntity(AT));
      final String prefix = li.cil.oc.core.impl.OCSettings.namespace;
      final var nbt = new net.minecraft.nbt.CompoundTag();
      nbt.putInt(prefix + "remoteX", air.getX());
      nbt.putInt(prefix + "remoteY", air.getY());
      nbt.putInt(prefix + "remoteZ", air.getZ());
      card.load(nbt, helper.getLevel().registryAccess());
      li.cil.oc.api.Network.joinNewNetwork(card.node());
      try {
        final var saved = new net.minecraft.nbt.CompoundTag();
        card.save(saved, helper.getLevel().registryAccess());
        helper.assertTrue(!saved.contains(prefix + "remoteX"), "the debug card kept a remote place with no node: " + saved);
      } finally {
        card.node().remove();
      }
      helper.succeed();
    });
  }

  /** The debugger, used on a simple component, connects to its node (it found none). */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void debuggersConnectToSimpleComponents(final GameTestHelper helper) {
    final SimpleFurnace furnace = placeFurnace(helper, AT, null);
    final BlockPos abs = helper.absolutePos(AT);
    helper.runAfterDelay(3, () -> {
      final var debugger = (li.cil.oc.core.impl.common.item.Debugger) li.cil.oc.neoforge.common.init.Items.DEBUGGER.get();
      final FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "oc-debugger"));
      try {
        asRealPlayer(li.cil.oc.core.impl.common.item.Debugger.class, player, () -> debugger.useOn(new net.minecraft.world.item.context.UseOnContext(player,
          net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(abs), Direction.UP, abs, false))));
        final Node node = node(furnace);
        helper.assertTrue(node != null && debugger.node() != null && debugger.node().isNeighborOf(node), "the debugger did not connect to the simple furnace's node");
      } finally {
        if (debugger.node() != null) debugger.node().remove();
      }
      helper.succeed();
    });
  }

  /**
   * Cables reach to what they connect to: simple components on their open sides (as environments in OC 1.12), and a
   * sided environment by the face toward the cable (it was asked about the opposite one).
   */
  @GameTest(template = EMPTY)
  public static void cablesReachToWhatTheyConnectTo(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.CABLE.get());
    placeFurnace(helper, AT.west(), null);
    placeFurnace(helper, AT.east(), Direction.WEST);
    helper.setBlock(AT.north(), Blocks.MICROCONTROLLER.get());
    ((li.cil.oc.core.impl.common.blockentity.Microcontroller) helper.getBlockEntity(AT.north())).facing(Direction.SOUTH); // its front to the cable
    helper.setBlock(AT.south(), Blocks.MICROCONTROLLER.get());
    ((li.cil.oc.core.impl.common.blockentity.Microcontroller) helper.getBlockEntity(AT.south())).facing(Direction.SOUTH); // its back to the cable
    final BlockPos abs = helper.absolutePos(AT);
    final AABB bounds = Blocks.CABLE.get().getShape(helper.getLevel().getBlockState(abs), helper.getLevel(), abs, CollisionContext.empty()).bounds();
    helper.assertTrue(bounds.minX == 0, "no arm to the simple furnace: " + bounds);
    helper.assertTrue(bounds.maxX < 1, "an arm to the simple furnace's closed side: " + bounds);
    helper.assertTrue(bounds.minZ > 0, "an arm to the microcontroller's front: " + bounds);
    helper.assertTrue(bounds.maxZ == 1, "no arm to the microcontroller's back: " + bounds);
    helper.succeed();
  }

  /**
   * Cables reach to cables of their colour or light gray, and not to other colours: the one rule for the cable's shape,
   * model and highlight.
   */
  @GameTest(template = EMPTY)
  public static void cablesReachToCablesOfTheirColour(final GameTestHelper helper) {
    final java.util.Map<BlockPos, Integer> colours = java.util.Map.of(AT, li.cil.oc.core.impl.util.Color.Red,
      AT.east(), li.cil.oc.core.impl.util.Color.Blue, AT.west(), li.cil.oc.core.impl.util.Color.LightGray, AT.north(), li.cil.oc.core.impl.util.Color.Red);
    colours.forEach((pos, colour) -> {
      helper.setBlock(pos, Blocks.CABLE.get());
      ((li.cil.oc.core.impl.common.blockentity.Cable) helper.getBlockEntity(pos)).color(colour);
    });
    final BlockPos abs = helper.absolutePos(AT);
    final int connections = li.cil.oc.neoforge.common.block.Cable.connections(helper.getLevel(), abs);
    final AABB bounds = Blocks.CABLE.get().getShape(helper.getLevel().getBlockState(abs), helper.getLevel(), abs, CollisionContext.empty()).bounds();
    helper.assertTrue((connections & (1 << Direction.EAST.get3DDataValue())) == 0 && bounds.maxX < 1, "a red cable reaches to a blue one: " + bounds);
    helper.assertTrue((connections & (1 << Direction.WEST.get3DDataValue())) != 0 && bounds.minX == 0, "a red cable does not reach to a light gray one: " + bounds);
    helper.assertTrue((connections & (1 << Direction.NORTH.get3DDataValue())) != 0 && bounds.minZ == 0, "a red cable does not reach to a red one: " + bounds);
    helper.succeed();
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
    public void onMessage(final Message message) {
    }
  }

  /** A block entity that is a sided environment itself as well. */
  public static final class SidedEnvironmentFurnace extends FurnaceBlockEntity implements SimpleComponent, li.cil.oc.api.network.SidedEnvironment {
    public SidedEnvironmentFurnace(final BlockPos pos, final BlockState state) {
      super(pos, state);
    }

    @Override
    public String getComponentName() {
      return "sided_environment_furnace";
    }

    @Override
    public Node sidedNode(final Direction side) {
      return null;
    }

    @Override
    public boolean canConnect(final Direction side) {
      return false;
    }
  }

  /** A simple component that is an environment itself gets no second node (it got one, joined and saved). */
  @GameTest(template = EMPTY)
  public static void environmentsGetNoSimpleComponentNode(final GameTestHelper helper) {
    helper.setBlock(AT, net.minecraft.world.level.block.Blocks.FURNACE);
    final BlockPos abs = helper.absolutePos(AT);
    for (final var furnace : List.of(new EnvironmentFurnace(abs, helper.getLevel().getBlockState(abs)), new SidedEnvironmentFurnace(abs, helper.getLevel().getBlockState(abs)))) {
      helper.assertTrue(OCBlockCapabilities.SIMPLE_COMPONENT_PROVIDER.getCapability(furnace, null) == null && !furnace.saveWithFullMetadata().toString().contains("oc:node"),
        "an environment (" + furnace.getClass().getSimpleName() + ") got a simple component's node as well");
    }
    helper.succeed();
  }
}
