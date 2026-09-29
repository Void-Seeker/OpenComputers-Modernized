package li.cil.oc.core.impl.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import li.cil.oc.core.impl.client.Textures;
import li.cil.oc.core.impl.common.blockentity.Charger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.InventoryMenu;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;

public class ChargerRenderer implements BlockEntityRenderer<Charger> {
  @SuppressWarnings("unused")
  public ChargerRenderer(BlockEntityRendererProvider.Context ignoredContext) {
  }

  @Override
  public void render(@NotNull Charger charger, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
    if (charger.chargeSpeed <= 0) return;

    poseStack.pushPose();
    poseStack.translate(0.5, 0.5, 0.5);

    Direction yaw = charger.facing();
    switch (yaw) {
      case WEST -> poseStack.mulPose(new Quaternionf().rotateY((float) Math.toRadians(-90)));
      case NORTH -> poseStack.mulPose(new Quaternionf().rotateY((float) Math.toRadians(180)));
      case EAST -> poseStack.mulPose(new Quaternionf().rotateY((float) Math.toRadians(90)));
    }

    poseStack.translate(-0.5, 0.5, 0.5);
    poseStack.scale(1, -1, 1);

    int fullBright = 0xF000F0;
    double inverse = 1 - charger.chargeSpeed;
    var atlas = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);

    {
      var sprite = atlas.apply(Textures.blockChargerFrontOnSprite);
      float u0 = sprite.getU0(), u1 = sprite.getU1();
      float v0 = sprite.getV0(), v1 = sprite.getV1();
      VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
      var matrix = poseStack.last().pose();
      consumer.vertex(matrix, 0, 1, 0.005f).color(255, 255, 255, 255).uv(u0, v1).overlayCoords(packedOverlay).uv2(fullBright).normal(0, 1, 0).endVertex();
      consumer.vertex(matrix, 1, 1, 0.005f).color(255, 255, 255, 255).uv(u1, v1).overlayCoords(packedOverlay).uv2(fullBright).normal(0, 1, 0).endVertex();
      consumer.vertex(matrix, 1, (float) inverse, 0.005f).color(255, 255, 255, 255).uv(u1, (float) (v0 + (v1 - v0) * inverse)).overlayCoords(packedOverlay).uv2(fullBright).normal(0, 1, 0).endVertex();
      consumer.vertex(matrix, 0, (float) inverse, 0.005f).color(255, 255, 255, 255).uv(u0, (float) (v0 + (v1 - v0) * inverse)).overlayCoords(packedOverlay).uv2(fullBright).normal(0, 1, 0).endVertex();
    }

    if (charger.hasPower) {
      var sprite = atlas.apply(Textures.blockChargerSideOnSprite);
      float u0 = sprite.getU0(), u1 = sprite.getU1();
      float v0 = sprite.getV0(), v1 = sprite.getV1();
      VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
      var matrix = poseStack.last().pose();

      // Left side
      consumer.vertex(matrix, -0.005f, 1, -1).color(255, 255, 255, 255).uv(u0, v1).overlayCoords(packedOverlay).uv2(fullBright).normal(0, 1, 0).endVertex();
      consumer.vertex(matrix, -0.005f, 1, 0).color(255, 255, 255, 255).uv(u1, v1).overlayCoords(packedOverlay).uv2(fullBright).normal(0, 1, 0).endVertex();
      consumer.vertex(matrix, -0.005f, 0, 0).color(255, 255, 255, 255).uv(u1, v0).overlayCoords(packedOverlay).uv2(fullBright).normal(0, 1, 0).endVertex();
      consumer.vertex(matrix, -0.005f, 0, -1).color(255, 255, 255, 255).uv(u0, v0).overlayCoords(packedOverlay).uv2(fullBright).normal(0, 1, 0).endVertex();

      // Back side
      consumer.vertex(matrix, 1, 1, -1.01f).color(255, 255, 255, 255).uv(u0, v1).overlayCoords(packedOverlay).uv2(fullBright).normal(0, 1, 0).endVertex();
      consumer.vertex(matrix, 0, 1, -1.01f).color(255, 255, 255, 255).uv(u1, v1).overlayCoords(packedOverlay).uv2(fullBright).normal(0, 1, 0).endVertex();
      consumer.vertex(matrix, 0, 0, -1.01f).color(255, 255, 255, 255).uv(u1, v0).overlayCoords(packedOverlay).uv2(fullBright).normal(0, 1, 0).endVertex();
      consumer.vertex(matrix, 1, 0, -1.01f).color(255, 255, 255, 255).uv(u0, v0).overlayCoords(packedOverlay).uv2(fullBright).normal(0, 1, 0).endVertex();

      // Right side
      consumer.vertex(matrix, 1.005f, 1, 0).color(255, 255, 255, 255).uv(u0, v1).overlayCoords(packedOverlay).uv2(fullBright).normal(0, 1, 0).endVertex();
      consumer.vertex(matrix, 1.005f, 1, -1).color(255, 255, 255, 255).uv(u1, v1).overlayCoords(packedOverlay).uv2(fullBright).normal(0, 1, 0).endVertex();
      consumer.vertex(matrix, 1.005f, 0, -1).color(255, 255, 255, 255).uv(u1, v0).overlayCoords(packedOverlay).uv2(fullBright).normal(0, 1, 0).endVertex();
      consumer.vertex(matrix, 1.005f, 0, 0).color(255, 255, 255, 255).uv(u0, v0).overlayCoords(packedOverlay).uv2(fullBright).normal(0, 1, 0).endVertex();
    }

    poseStack.popPose();
  }
}
