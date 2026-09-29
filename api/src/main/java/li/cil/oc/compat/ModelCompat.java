package li.cil.oc.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;

/**
 * 1.20.1 backport glue: 1.21 passes model tint as one packed ARGB int, 1.20.1 as four floats.
 */
public final class ModelCompat {
  private ModelCompat() {
  }

  public static void render(final ModelPart part, final PoseStack poseStack, final VertexConsumer consumer, final int packedLight, final int packedOverlay, final int argb) {
    part.render(poseStack, consumer, packedLight, packedOverlay,
      ((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f, ((argb >>> 24) & 0xFF) / 255f);
  }

  public static int pack(final float red, final float green, final float blue, final float alpha) {
    return ((int) (alpha * 255) & 0xFF) << 24 | ((int) (red * 255) & 0xFF) << 16 | ((int) (green * 255) & 0xFF) << 8 | ((int) (blue * 255) & 0xFF);
  }
}
