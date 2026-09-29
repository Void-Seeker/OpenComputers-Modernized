package li.cil.oc.neoforge.compat;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

/**
 * Stand-in for 1.21's {@code Tesselator.begin(mode, format)}, which returns the started builder.
 * Finish with {@code builder.end()} (1.21: {@code build()}/{@code buildOrThrow()}).
 */
public final class Tesselators {
  private Tesselators() {
  }

  public static BufferBuilder begin(final Tesselator tesselator, final VertexFormat.Mode mode, final VertexFormat format) {
    final BufferBuilder builder = tesselator.getBuilder();
    builder.begin(mode, format);
    return builder;
  }
}
