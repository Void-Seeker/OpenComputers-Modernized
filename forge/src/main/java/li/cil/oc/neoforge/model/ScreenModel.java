package li.cil.oc.neoforge.model;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import li.cil.oc.core.impl.client.renderer.ScreenFrameTextures;
import li.cil.oc.core.impl.common.blockentity.Screen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * A screen, placed and as an item. Placed, each block's frame is part of the chunk, with the textures for where it sits
 * in its display (as OC 1.12's ScreenModel; the block entity renderer drew every block's frame from the display's
 * origin, so screens vanished with it: past 64 blocks, or when the origin's chunk section was culled). Its renderer
 * draws only what the display shows.
 */
public class ScreenModel implements IDynamicBakedModel {
  private static final FaceBakery BAKERY = new FaceBakery();
  private static final ResourceLocation MODEL = new ResourceLocation("opencomputers", "screen");
  private static final Vector3f FROM = new Vector3f(0, 0, 0);
  private static final Vector3f TO = new Vector3f(16, 16, 16);

  /** A placed screen's frame: each world side's texture and quarter turns, read from its block entity. */
  public static final ModelProperty<Frame> FRAME = new ModelProperty<>();

  public record Frame(String[] textures, int[] rotations) {
  }

  private final ItemOverrides overrides;
  // baked once per texture, side and rotation (chunk builder threads fill it: a race bakes the same quad twice)
  private final Map<String, BakedQuad[]> frameQuads = new ConcurrentHashMap<>();

  @SuppressWarnings("unused")
  public ScreenModel() {
    this.overrides = new ItemOverrides() {
      @Override
      public @NotNull BakedModel resolve(@NotNull BakedModel original, @NotNull ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
        return ScreenModel.this;
      }
    };
  }

  @Override
  public @NotNull ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ModelData modelData) {
    if (level.getBlockEntity(pos) instanceof Screen screen) {
      var textures = new String[6];
      var rotations = new int[6];
      for (Direction side : Direction.values()) {
        textures[side.get3DDataValue()] = ScreenFrameTextures.texture(screen, side);
        rotations[side.get3DDataValue()] = ScreenFrameTextures.rotation(screen, side);
      }
      return ModelData.builder().with(FRAME, new Frame(textures, rotations)).build();
    }
    return modelData;
  }

  @Override
  public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand, @NotNull ModelData data, @Nullable RenderType renderType) {
    if (state != null) {
      // a full cube: each face once, as its side's quad, culled against its neighbours (other screens of the display too)
      if (side == null) return List.of();
      Frame frame = data.get(FRAME);
      if (frame != null) {
        return List.of(frameQuad(frame.textures()[side.get3DDataValue()], side, frame.rotations()[side.get3DDataValue()]));
      }
      // no screen to ask (the breaking overlay): a lone screen, facing as the block state
      var facing = li.cil.oc.core.impl.common.block.Screen.FACING;
      boolean front = state.hasProperty(facing) && state.getValue(facing) == side;
      return List.of(frameQuad(ScreenFrameTextures.lone(side, front), side, 0));
    }
    if (side != null) return List.of(); // (unculled: once, not once per face asked about as well)
    List<BakedQuad> quads = new ArrayList<>();

    var frontSprite = sprite("opencomputers", "block/screen/f");
    var backSprite = sprite("opencomputers", "block/screen/b");
    var sideSprite = sprite("opencomputers", "block/screen/b2");

    Vector3f from = new Vector3f(0, 0, 0);
    Vector3f to = new Vector3f(16, 16, 16);

    for (Direction faceDir : Direction.values()) {
      TextureAtlasSprite sprite;
      switch (faceDir) {
        case SOUTH, NORTH -> sprite = frontSprite;
        case UP, DOWN -> sprite = backSprite;
        default -> sprite = sideSprite;
      }
      BlockElementFace face = new BlockElementFace(faceDir, 0, "", new BlockFaceUV(getUVs(from, to, faceDir), 0));
      BakedQuad quad = BAKERY.bakeQuad(from, to, face, sprite, faceDir, BlockModelRotation.X0_Y0, null, false, new net.minecraft.resources.ResourceLocation("opencomputers", "screen"));
      int[] vertices = quad.getVertices();
      for (int i = 0; i < 4; i++) {
        vertices[i * 8 + 3] = 0xFFFFFFFF;
        vertices[i * 8 + 6] = 0;
      }
      quads.add(quad);
    }

    return quads;
  }

  private BakedQuad frameQuad(String texture, Direction side, int rotation) {
    var quads = frameQuads.computeIfAbsent(texture, k -> new BakedQuad[6 * 4]);
    int index = side.get3DDataValue() * 4 + rotation;
    var quad = quads[index];
    if (quad == null) {
      // tinted with the screen's colour (the block colour handler); the full face's default UVs, turned as OC 1.12's
      var face = new BlockElementFace(side, 0, "", new BlockFaceUV(new float[]{0, 0, 16, 16}, rotation * 90));
      quad = BAKERY.bakeQuad(FROM, TO, face, sprite("opencomputers", "block/screen/" + texture), side, BlockModelRotation.X0_Y0, null, true, MODEL);
      quads[index] = quad;
    }
    return quad;
  }

  @Override
  public @NotNull ItemOverrides getOverrides() {
    return overrides;
  }

  @SuppressWarnings("SameParameterValue")
  private static TextureAtlasSprite sprite(String namespace, String path) {
    var atlas = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
    TextureAtlasSprite sprite = atlas.apply(new ResourceLocation(namespace, path));
    TextureAtlasSprite missing = atlas.apply(new ResourceLocation("missingno"));
    if (sprite != missing) {
      return sprite;
    }
    return atlas.apply(new ResourceLocation("block/stone"));
  }

  private static float[] getUVs(Vector3f from, Vector3f to, Direction dir) {
    float x1 = from.x();
    float y1 = from.y();
    float z1 = from.z();
    float x2 = to.x();
    float y2 = to.y();
    float z2 = to.z();
    return switch (dir) {
      case DOWN, UP -> new float[]{x1, z1, x2, z2};
      case NORTH, SOUTH -> new float[]{x1, 16 - y2, x2, 16 - y1};
      case WEST, EAST -> new float[]{z1, 16 - y2, z2, 16 - y1};
    };
  }

  @Override
  public boolean useAmbientOcclusion() {
    return true;
  }

  @Override
  public boolean isGui3d() {
    return true;
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
    return getParticleIcon(ModelData.EMPTY);
  }

  @Override
  public @NotNull TextureAtlasSprite getParticleIcon(@NotNull ModelData data) {
    return sprite("opencomputers", "block/screen/f");
  }

  @Override
  public @NotNull ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand, @NotNull ModelData data) {
    return ChunkRenderTypeSet.of(RenderType.solid()); // (opaque textures)
  }

  @Override
  public @NotNull BakedModel applyTransform(@NotNull ItemDisplayContext transformType, @NotNull PoseStack poseStack, boolean applyLeftHandTransform) {
    switch (transformType) {
      case THIRD_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND ->
        new ItemTransform(new Vector3f(75, 45, 0), new Vector3f(0, 0.1f, 0), new Vector3f(0.375f, 0.375f, 0.375f)).apply(applyLeftHandTransform, poseStack);
      case FIRST_PERSON_LEFT_HAND ->
        new ItemTransform(new Vector3f(0, 225, 0), new Vector3f(0, 0, 0), new Vector3f(0.4f, 0.4f, 0.4f)).apply(applyLeftHandTransform, poseStack);
      case FIRST_PERSON_RIGHT_HAND ->
        new ItemTransform(new Vector3f(0, 45, 0), new Vector3f(0, 0, 0), new Vector3f(0.4f, 0.4f, 0.4f)).apply(applyLeftHandTransform, poseStack);
      case GUI ->
        new ItemTransform(new Vector3f(30, 225, 0), new Vector3f(0, 0, 0), new Vector3f(0.625f, 0.625f, 0.625f)).apply(applyLeftHandTransform, poseStack);
      case GROUND ->
        new ItemTransform(new Vector3f(0, 0, 0), new Vector3f(0, 0.1875f, 0), new Vector3f(0.25f, 0.25f, 0.25f)).apply(applyLeftHandTransform, poseStack);
      case FIXED ->
        new ItemTransform(new Vector3f(0, 0, 0), new Vector3f(0, 0, 0), new Vector3f(0.5f, 0.5f, 0.5f)).apply(applyLeftHandTransform, poseStack);
    }
    return this;
  }
}
