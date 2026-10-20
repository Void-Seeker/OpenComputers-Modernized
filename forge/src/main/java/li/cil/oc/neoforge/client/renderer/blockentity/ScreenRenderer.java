package li.cil.oc.neoforge.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import li.cil.oc.core.impl.OCSettings;
import li.cil.oc.core.impl.client.ClientDistanceHelper;
import li.cil.oc.core.impl.client.renderer.TextBufferRenderCache;
import li.cil.oc.core.impl.common.blockentity.Screen;
import li.cil.oc.core.impl.integration.util.Wrench;
import li.cil.oc.core.impl.util.BlockPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public class ScreenRenderer implements BlockEntityRenderer<Screen> {

  @SuppressWarnings("unused")
  public ScreenRenderer(BlockEntityRendererProvider.Context ignoredCtx) {
  }

  // what the display shows, drawn from its origin (each block's frame is its block model)
  @Override
  public void render(Screen screen, float partialTick, @NotNull PoseStack poseStack,
                     @NotNull MultiBufferSource buffers, int packedLight, int packedOverlay) {
    if (screen.getLevel() == null) return;
    if (!screen.isOrigin()) return;

    renderScreenContent(screen, partialTick, poseStack, buffers, packedLight);

    Player player = Minecraft.getInstance().player;
    if (player != null && !player.getMainHandItem().isEmpty()) {
      if (screen.facing() == Direction.UP || screen.facing() == Direction.DOWN) {
        ItemStack held = player.getMainHandItem();
        var info = li.cil.oc.api.Items.get(held);
        boolean isScreenBlock = info != null && info.block() instanceof li.cil.oc.core.impl.common.block.Screen;
        boolean isWrench = Wrench.holdsApplicableWrench(player, BlockPosition.apply(screen.getBlockPos().getX(), screen.getBlockPos().getY(), screen.getBlockPos().getZ(), screen.getLevel()));
        if (isScreenBlock || isWrench) {
          drawOverlay(screen, poseStack, buffers);
        }
      }
    }
  }

  // as far as text is drawn (maxScreenTextRenderDistance per block of the display's smaller side), not vanilla's 64
  @Override
  public int getViewDistance() {
    var settings = OCSettings.get();
    double text = settings.maxScreenTextRenderDistance * Math.min(settings.maxScreenWidth, settings.maxScreenHeight);
    return Math.max(64, (int) Math.ceil(text) + 2);
  }

  // a display beyond its origin's block is drawn while any of it is in view (not only while its origin's section is)
  @Override
  public boolean shouldRenderOffScreen(@NotNull Screen screen) {
    return screen.isOrigin() && (screen.width > 1 || screen.height > 1);
  }

  private void drawOverlay(Screen screen, PoseStack poseStack, MultiBufferSource buffers) {
    poseStack.pushPose();
    poseStack.translate(0.5, 0.5, 0.5);

    applyTransform(screen, poseStack);

    float cx = screen.width / 2.0f - 0.5f;
    float cy = screen.height / 2.0f - 0.5f;
    poseStack.translate(cx, cy, 0.01f);

    TextureAtlasSprite sprite = Minecraft.getInstance()
      .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
      .apply(new ResourceLocation("opencomputers", "block/overlay/screen_up_indicator"));
    if (sprite == null) {
      poseStack.popPose();
      return;
    }

    Matrix4f m = poseStack.last().pose();
    float u0 = sprite.getU0(), u1 = sprite.getU1();
    float v0 = sprite.getV0(), v1 = sprite.getV1();

    VertexConsumer vc = buffers.getBuffer(RenderType.translucent());
    var normal = poseStack.last();
    vc.vertex(m, 0, 1, 0).color(1.0f, 1.0f, 1.0f, 1.0f).uv(u0, v1).uv2(0xF000F0).normal(normal.normal(), 0, 0, 1).endVertex();
    vc.vertex(m, 1, 1, 0).color(1.0f, 1.0f, 1.0f, 1.0f).uv(u1, v1).uv2(0xF000F0).normal(normal.normal(), 0, 0, 1).endVertex();
    vc.vertex(m, 1, 0, 0).color(1.0f, 1.0f, 1.0f, 1.0f).uv(u1, v0).uv2(0xF000F0).normal(normal.normal(), 0, 0, 1).endVertex();
    vc.vertex(m, 0, 0, 0).color(1.0f, 1.0f, 1.0f, 1.0f).uv(u0, v0).uv2(0xF000F0).normal(normal.normal(), 0, 0, 1).endVertex();

    poseStack.popPose();
  }

  private void renderScreenContent(Screen screen, float partialTick, PoseStack poseStack,
                                   MultiBufferSource buffers, int ignoredLight) {
    Player p = Minecraft.getInstance().player;
    if (p == null) return;
    Vec3 eyePos = p.getEyePosition(partialTick);
    Direction screenFacing = screen.facing().getOpposite();
    Vec3 center = ClientDistanceHelper.project(screen.getLevel(),
      new Vec3(screen.getBlockPos().getX() + 0.5, screen.getBlockPos().getY() + 0.5, screen.getBlockPos().getZ() + 0.5));
    double dx = center.x - eyePos.x;
    double dy = center.y - eyePos.y;
    double dz = center.z - eyePos.z;

    if (screenFacing.getStepX() * dx + screenFacing.getStepY() * dy + screenFacing.getStepZ() * dz < -1.5) return;

    float textAlpha = 1.0f;
    double distSq = ClientDistanceHelper.distanceSquared(screen.getLevel(),
      screen.getBlockPos().getX() + 0.5, screen.getBlockPos().getY() + 0.5, screen.getBlockPos().getZ() + 0.5, p);
    double distance = Math.sqrt(distSq) / Math.min(screen.width, screen.height);
    double maxDist = OCSettings.get().maxScreenTextRenderDistance;
    double fadeStart = OCSettings.get().screenTextFadeStartDistance;
    if (distance > maxDist) return;
    if (distance > fadeStart) {
      textAlpha = (float) Math.max(0, 1 - (distance - fadeStart) / (maxDist - fadeStart));
    }
    if (textAlpha <= 0) return;

    poseStack.pushPose();
    poseStack.translate(0.5, 0.5, 0.5);

    applyTransform(screen, poseStack);

    float innerWidth = screen.buffer().renderWidth();
    float innerHeight = screen.buffer().renderHeight();

    float sx = screen.width;
    float sy = screen.height;

    float isx = sx - (4.5f / 16.0f);
    float isy = sy - (4.5f / 16.0f);

    float scaleX = isx / innerWidth;
    float scaleY = isy / innerHeight;

    float scale;
    if (scaleX > scaleY) {
      scale = scaleY;
      poseStack.translate(innerWidth * 0.5f * (scaleX - scaleY), 0, 0);
    } else {
      scale = scaleX;
      poseStack.translate(0, innerHeight * 0.5f * (scaleY - scaleX), 0);
    }
    poseStack.scale(scale, scale, 1.0f);
    poseStack.translate(2.25f / 16.0f / scale, 2.25f / 16.0f / scale, 0.001f);

    Matrix4f textTransform = new Matrix4f(poseStack.last().pose());
    renderScreenText(screen, buffers, textTransform, textAlpha);

    poseStack.popPose();
  }

  private void renderScreenText(Screen screen, MultiBufferSource buffers, Matrix4f textTransform, float alpha) {
    var buffer = screen.buffer;

    if (buffer.isRenderingEnabled() && buffer.hasLitContent()) {
      if (buffer.isBufferDirty()) {
        for (var line : buffer.data.buffer) {
          TextBufferRenderCache.renderer.generateChars(line);
        }
        buffer.clearBufferDirty();
      }

      ScreenBufferTextRenderer.render(
        buffers,
        buffer.data,
        buffer.getViewportWidth(),
        buffer.getViewportHeight(),
        textTransform,
        alpha
      );
    }
  }

  private void applyTransform(Screen screen, PoseStack stack) {
    Direction yaw = screen.yaw();
    if (yaw == Direction.WEST) stack.mulPose(new Quaternionf().rotationY((float) Math.toRadians(-90)));
    else if (yaw == Direction.NORTH) stack.mulPose(new Quaternionf().rotationY((float) Math.toRadians(180)));
    else if (yaw == Direction.EAST) stack.mulPose(new Quaternionf().rotationY((float) Math.toRadians(90)));

    Direction facing = screen.facing();
    if (facing == Direction.DOWN) stack.mulPose(new Quaternionf().rotationX((float) Math.toRadians(90)));
    else if (facing == Direction.UP) stack.mulPose(new Quaternionf().rotationX((float) Math.toRadians(-90)));

    stack.translate(-0.5f, -0.5f, 0.5f);
    stack.translate(0, screen.height, 0);
    stack.scale(1, -1, 1);
  }
}
