package li.cil.oc.neoforge.integration.cbmultipart;

import codechicken.lib.render.CCRenderState;
import codechicken.multipart.api.part.render.PartRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import li.cil.oc.neoforge.model.CableModel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * Draws a multipart cable as a block cable is drawn: the block cable model's quads, reaching where the block cable's
 * connections say, the part's colour on the cable and none on its plugs and caps (OC 1.12), lit flat as vanilla lights
 * a block.
 */
@SuppressWarnings("unused")
public class CablePartRenderer implements PartRenderer<CablePart> {
  /**
   * CBMultipart 1.20.1 renders parts into CodeChickenLib's render state rather than asking for quads; the quads are
   * emitted block-relative, like its baked model renderer does. (They were built with CodeChickenLib's
   * BakedQuadVertexBuilder, whose quads have no vertices until reset: the first vertex threw, and no multipart cable was
   * ever drawn. Their light was also packed at the wrong scale.)
   */
  @Override
  public void renderStatic(@NotNull CablePart part, @NotNull RenderType layer, @NotNull CCRenderState ccrs) {
    if (layer != RenderType.cutout() || !part.hasLevel()) return;
    final Level level = part.level();
    final BlockPos pos = part.pos();
    final int mask = li.cil.oc.neoforge.common.block.Cable.connections(level, pos);
    final int color = part.getColor();
    final float r = ((color >> 16) & 0xFF) / 255f;
    final float g = ((color >> 8) & 0xFF) / 255f;
    final float b = (color & 0xFF) / 255f;
    final int light = LevelRenderer.getLightColor(level, pos);
    final int[] lights = {light, light, light, light};
    final VertexConsumer consumer = ccrs.getConsumer();
    final PoseStack.Pose pose = new PoseStack().last();
    for (final BakedQuad quad : CableModel.bakeCable(mask, CableModel.computeCableNeighbors(level, pos))) {
      final float shade = level.getShade(quad.getDirection(), quad.isShade());
      final boolean tinted = quad.isTinted(); // (the cable; its plugs and caps are not)
      consumer.putBulkData(pose, quad, new float[]{shade, shade, shade, shade}, tinted ? r : 1f, tinted ? g : 1f, tinted ? b : 1f,
        lights, OverlayTexture.NO_OVERLAY, true);
    }
  }
}
