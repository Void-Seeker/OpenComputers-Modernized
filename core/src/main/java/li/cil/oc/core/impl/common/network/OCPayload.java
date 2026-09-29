package li.cil.oc.core.impl.common.network;

import li.cil.oc.core.Tags;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * The single packet type OC uses, carrying an opaque byte payload. The platform
 * module registers it with its networking API using {@link #encode}/{@link #decode}.
 */
public record OCPayload(byte[] data) {
  public static final ResourceLocation ID = new ResourceLocation(Tags.MOD_ID, "packet");

  public void encode(final FriendlyByteBuf buf) {
    buf.writeBytes(data);
  }

  public static OCPayload decode(final FriendlyByteBuf buf) {
    byte[] data = new byte[buf.readableBytes()];
    buf.readBytes(data);
    return new OCPayload(data);
  }
}
