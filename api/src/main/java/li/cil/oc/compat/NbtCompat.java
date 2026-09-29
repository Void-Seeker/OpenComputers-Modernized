package li.cil.oc.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

/**
 * 1.20.1 backport glue: 1.20.1's {@code NbtIo.readCompressed} has no size-limited variant.
 */
public final class NbtCompat {
  private NbtCompat() {
  }

  public static CompoundTag readCompressed(final InputStream stream, final NbtAccounter accounter) throws IOException {
    try (DataInputStream input = new DataInputStream(new BufferedInputStream(new GZIPInputStream(stream)))) {
      return NbtIo.read(input, accounter);
    }
  }
}
