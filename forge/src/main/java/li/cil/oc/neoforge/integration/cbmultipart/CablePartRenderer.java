package li.cil.oc.neoforge.integration.cbmultipart;

import codechicken.lib.render.CCRenderState;
import codechicken.multipart.api.part.render.PartRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import li.cil.oc.neoforge.common.init.Blocks;
import li.cil.oc.neoforge.model.CableModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.QuadTransformers;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Draws a multipart cable as a block cable is drawn: the block cable model's quads, reaching where the block cable's
 * connections say, the part's colour on the cable and none on its plugs and caps (OC 1.12), lit by vanilla's block
 * renderer as the block cable is (smooth lighting and ambient occlusion as set).
 */
@SuppressWarnings("unused")
public class CablePartRenderer implements PartRenderer<CablePart> {
  /**
   * CBMultipart 1.20.1 renders parts into CodeChickenLib's render state rather than asking for quads; the quads go
   * through vanilla's block renderer into its consumer with the render region CB gives, as CB's own baked model parts do.
   * The layer is null for the breaking overlay (it was left out: no cracks while mining). The quads were built with
   * CodeChickenLib's BakedQuadVertexBuilder, whose quads have no vertices until reset: the first vertex threw, and no
   * multipart cable was ever drawn.
   */
  @Override
  public void renderStatic(@NotNull CablePart part, @Nullable RenderType layer, @NotNull CCRenderState ccrs) {
    if ((layer != null && layer != RenderType.cutout()) || !part.hasLevel()) return;
    final Level level = part.level();
    final BlockPos pos = part.pos();
    final int mask = li.cil.oc.neoforge.common.block.Cable.connections(level, pos);
    // the colour in the quads: vanilla takes tints from the block entity at the place, the multipart tile here
    final var colour = QuadTransformers.applyingColor(0xFF000000 | part.getColor());
    final List<BakedQuad> quads = new ArrayList<>();
    for (final BakedQuad quad : CableModel.bakeCable(mask, CableModel.computeCableNeighbors(level, pos))) {
      if (!quad.isTinted()) {
        quads.add(quad); // (plugs and caps)
      } else {
        final BakedQuad coloured = colour.process(quad);
        quads.add(new BakedQuad(coloured.getVertices(), -1, coloured.getDirection(), coloured.getSprite(), coloured.isShade(), coloured.hasAmbientOcclusion()));
      }
    }
    // lit as a block cable is lit, its state for the light rules
    final BlockState state = Blocks.CABLE.get().defaultBlockState();
    Minecraft.getInstance().getBlockRenderer().getModelRenderer().tesselateBlock(ccrs.lightMatrix.access, new Quads(quads), state, pos,
      new PoseStack(), ccrs.getConsumer(), false, RandomSource.create(), 42L, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, layer);
  }

  /** The quads, unculled, as a model for the block renderer. */
  private record Quads(List<BakedQuad> quads) implements BakedModel {
    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand) {
      return side == null ? quads : List.of();
    }

    @Override
    public boolean useAmbientOcclusion() {
      return true; // (as the block cable's model)
    }

    @Override
    public boolean isGui3d() {
      return false;
    }

    @Override
    public boolean usesBlockLight() {
      return true;
    }

    @Override
    public boolean isCustomRenderer() {
      return false;
    }

    @Override
    public @NotNull TextureAtlasSprite getParticleIcon() {
      return quads.isEmpty() ? Minecraft.getInstance().getModelManager().getMissingModel().getParticleIcon() : quads.get(0).getSprite();
    }

    @Override
    public @NotNull ItemOverrides getOverrides() {
      return ItemOverrides.EMPTY;
    }
  }
}
