package li.cil.oc.neoforge.common;

import io.netty.buffer.Unpooled;
import li.cil.oc.core.impl.common.network.OCPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/**
 * The channel carrying OC's single packet type. Handlers run on the main thread,
 * like the NeoForge payload handlers they replace.
 */
public final class Network {
  private static final String PROTOCOL_VERSION = "1";

  private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
    OCPayload.ID, () -> PROTOCOL_VERSION, PROTOCOL_VERSION::equals, PROTOCOL_VERSION::equals);

  private Network() {
  }

  public static void register() {
    CHANNEL.registerMessage(0, OCPayload.class, OCPayload::encode, OCPayload::decode, Network::handle);
  }

  public static void sendToAllPlayers(final OCPayload payload) {
    CHANNEL.send(PacketDistributor.ALL.noArg(), payload);
  }

  public static void sendToPlayer(final ServerPlayer player, final OCPayload payload) {
    CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
  }

  public static void sendToServer(final OCPayload payload) {
    CHANNEL.sendToServer(payload);
  }

  private static void handle(final OCPayload payload, final Supplier<NetworkEvent.Context> contextSupplier) {
    final NetworkEvent.Context context = contextSupplier.get();
    if (context.getDirection().getReceptionSide().isClient()) {
      context.enqueueWork(() -> li.cil.oc.neoforge.client.ClientPacketHandler.INSTANCE.onPacket(Unpooled.wrappedBuffer(payload.data())));
    } else {
      final ServerPlayer player = context.getSender();
      context.enqueueWork(() -> li.cil.oc.neoforge.server.PacketHandler.INSTANCE.onPacketData(Unpooled.wrappedBuffer(payload.data()), player));
    }
    context.setPacketHandled(true);
  }
}
