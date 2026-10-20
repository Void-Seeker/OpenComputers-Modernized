package li.cil.oc.neoforge.gametest;

import li.cil.oc.core.Constants;
import li.cil.oc.core.impl.OCSettings;
import li.cil.oc.core.server.network.QuantumNetwork;
import li.cil.oc.neoforge.OpenComputers;
import li.cil.oc.neoforge.common.init.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Linked cards stay in their tunnel, relays relay wirelessly and on time, and failed connections say why.
 */
@GameTestHolder(OpenComputers.ID)
@PrefixGameTestTemplate(false)
public final class NetworkTests {
  private static final String EMPTY = "empty";
  private static final BlockPos AT = new BlockPos(2, 2, 2);

  private NetworkTests() {
  }

  private static ItemStack item(final String name) {
    return li.cil.oc.api.Items.get(name).createItemStack(1);
  }

  private static QuantumNetwork.QuantumNode endpoint(final String tunnel) {
    return new QuantumNetwork.QuantumNode() {
      @Override
      public String tunnel() {
        return tunnel;
      }

      @Override
      public void receivePacket(li.cil.oc.api.network.Packet packet) {
      }
    };
  }

  /** A linked card that sends stays in its tunnel (it took itself out of the registry: the reply reached nobody). */
  @GameTest(template = EMPTY)
  public static void linkedCardsStayInTheirTunnel(final GameTestHelper helper) {
    final String tunnel = "test-" + java.util.UUID.randomUUID();
    final var a = endpoint(tunnel);
    final var b = endpoint(tunnel);
    QuantumNetwork.add(a);
    QuantumNetwork.add(b);
    try {
      final var others = QuantumNetwork.getEndpoints(tunnel);
      others.remove(a); // what LinkedCard.send does before sending to the others
      helper.assertTrue(QuantumNetwork.getEndpoints(tunnel).contains(a) && QuantumNetwork.getEndpoints(tunnel).contains(b),
        "after sending, the tunnel holds " + QuantumNetwork.getEndpoints(tunnel).size() + " of its 2 cards");
    } finally {
      QuantumNetwork.remove(a);
      QuantumNetwork.remove(b);
    }
    helper.succeed();
  }

  /** A relay given a wireless card relays at the card's range (its strength stayed 0: nothing went out wirelessly). */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void relayWithAWirelessCardTransmits(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.RELAY.get());
    final var relay = (li.cil.oc.core.impl.common.blockentity.Relay) helper.getBlockEntity(AT);
    TestMachines.later(helper, 2, () -> {
      int slot = -1;
      final ItemStack card = item(Constants.ItemName.WirelessNetworkCardTier2);
      for (int i = 0; i < relay.getContainerSize() && slot < 0; i++) {
        if (relay.canPlaceItem(i, card)) slot = i;
      }
      helper.assertTrue(slot >= 0, "no relay slot takes a wireless card");
      relay.setItem(slot, card);
      helper.assertTrue(relay.strength == OCSettings.get().maxWirelessRange[li.cil.oc.core.common.Tier.Two],
        "a relay with a tier 2 wireless card sends at strength " + relay.strength);
      helper.succeed();
    });
  }

  /** A packet arriving at an idle relay waits the relay delay (after the first, packets went out at once). */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void relayDelayAppliesAgainWhenIdle(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.RELAY.get());
    final var relay = (li.cil.oc.core.impl.common.blockentity.Relay) helper.getBlockEntity(AT);
    helper.assertTrue(relay.relayDelay > 1, "the relay delay is " + relay.relayDelay + ": nothing to wait");
    final java.util.function.Supplier<li.cil.oc.api.network.Packet> packet = () -> li.cil.oc.api.Network.newPacket("test", null, 1, new Object[]{"ping"});
    relay.tryEnqueuePacket(null, packet.get());
    TestMachines.later(helper, relay.relayDelay + 5, () -> {
      helper.assertTrue(relay.packetQueue.isEmpty(), "the first packet was not relayed");
      relay.tryEnqueuePacket(null, packet.get());
      helper.assertTrue(relay.relayCooldown > 0, "a packet at an idle relay waits " + relay.relayCooldown + " ticks");
      helper.succeed();
    });
  }

  /** A refused connection says why (it closed and then said only "connection lost"). */
  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void failedConnectionSaysWhy(final GameTestHelper helper) throws Exception {
    final var card = new li.cil.oc.core.impl.server.component.InternetCard();
    final var socket = new li.cil.oc.core.impl.server.component.InternetCard.TCPSocket(card, new java.net.URI("oc://10.0.0.1:80"), 80);
    final String[] reason = {null};
    final int[] polls = {0};
    helper.onEachTick(() -> {
      if (reason[0] != null || polls[0]++ > 150) return;
      try {
        socket.finishConnect(null, new li.cil.oc.core.impl.server.machine.ArgumentsImpl(new Object[0]));
      } catch (RuntimeException e) {
        reason[0] = String.valueOf(e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
      }
    });
    TestMachines.later(helper, 160, () -> {
      helper.assertTrue(reason[0] != null && reason[0].contains("not allowed"), "a refused connection says " + reason[0]);
      helper.succeed();
    });
  }

  /** A relay saved with a wireless card and no strength (before cards got their range) sends at its range once loaded. */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void oldRelayWithAWirelessCardGetsItsRange(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.RELAY.get());
    final var relay = (li.cil.oc.core.impl.common.blockentity.Relay) helper.getBlockEntity(AT);
    TestMachines.later(helper, 2, () -> {
      int slot = -1;
      final ItemStack card = item(Constants.ItemName.WirelessNetworkCardTier2);
      for (int i = 0; i < relay.getContainerSize() && slot < 0; i++) {
        if (relay.canPlaceItem(i, card)) slot = i;
      }
      relay.setItem(slot, card);
      relay.strength = 0;
      final var tag = relay.saveWithFullMetadata();
      tag.remove(OCSettings.namespace + "strengthKnown");
      final var loaded = (li.cil.oc.core.impl.common.blockentity.Relay) net.minecraft.world.level.block.entity.BlockEntity.loadStatic(relay.getBlockPos(), relay.getBlockState(), tag);
      helper.assertTrue(loaded != null && loaded.strength == OCSettings.get().maxWirelessRange[li.cil.oc.core.common.Tier.Two],
        "an old relay with a tier 2 card loads with strength " + (loaded == null ? null : loaded.strength));
      helper.succeed();
    });
  }
}
