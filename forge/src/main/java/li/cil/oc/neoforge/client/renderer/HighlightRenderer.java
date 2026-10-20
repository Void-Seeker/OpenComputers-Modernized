package li.cil.oc.neoforge.client.renderer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Random;
import li.cil.oc.api.detail.ItemInfo;
import li.cil.oc.core.Constants;
import li.cil.oc.core.impl.OCSettings;
import li.cil.oc.core.impl.client.Textures;
import li.cil.oc.core.impl.common.blockentity.Cable;
import li.cil.oc.core.impl.common.blockentity.Print;
import li.cil.oc.core.impl.util.ExtendedAABB;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;

public final class HighlightRenderer {
  private static final Random random = new Random();
  private static final ItemInfo tablet = li.cil.oc.api.Items.get(Constants.ItemName.Tablet);
  private static final RenderType HOLOGRAM_RENDER_TYPE = RenderType.create(
    "hologram_overlay",
    DefaultVertexFormat.BLOCK,
    VertexFormat.Mode.QUADS,
    RenderType.TRANSIENT_BUFFER_SIZE,
    false,
    true,
    RenderType.CompositeState.builder()
      .setShaderState(RenderType.RENDERTYPE_TRANSLUCENT_SHADER)
      .setTextureState(new RenderType.TextureStateShard(Textures.blockHologram, false, false))
      .setTransparencyState(RenderType.TRANSLUCENT_TRANSPARENCY)
      .setLightmapState(RenderType.LIGHTMAP)
      .createCompositeState(false)
  );

  @SubscribeEvent(priority = EventPriority.HIGH)
  @SuppressWarnings("unused")
  public static void onDrawBlockHighlight(RenderHighlightEvent.Block e) {
    var hitInfo = e.getTarget();
    if (!(e.getCamera().getEntity() instanceof Player player)) return;

    var level = player.level();
    var blockPos = hitInfo.getBlockPos();

    if (hitInfo.getType() == HitResult.Type.BLOCK && li.cil.oc.api.Items.get(player.getItemInHand(InteractionHand.MAIN_HAND)) == tablet) {
      if (!level.isEmptyBlock(blockPos)) {
        var state = level.getBlockState(blockPos);
        var shape = state.getShape(level, blockPos);
        var bounds = shape.bounds();
        var sideHit = hitInfo.getDirection();

        var camera = e.getCamera();
        var camPos = camera.getPosition();

        var pose = e.getPoseStack();
        pose.pushPose();
        pose.translate(blockPos.getX() - camPos.x, blockPos.getY() - camPos.y, blockPos.getZ() - camPos.z);
        pose.scale(1.002f, 1.002f, 1.002f);

        if (OCSettings.get().hologramFlickerFrequency > 0 && random.nextDouble() < OCSettings.get().hologramFlickerFrequency) {
          double sx = 1 - Math.abs(sideHit.getStepX());
          double sy = 1 - Math.abs(sideHit.getStepY());
          double sz = 1 - Math.abs(sideHit.getStepZ());
          pose.scale(1 + (float) (random.nextGaussian() * 0.01), 1 + (float) (random.nextGaussian() * 0.001), 1 + (float) (random.nextGaussian() * 0.01));
          pose.translate(random.nextGaussian() * 0.01 * sx, random.nextGaussian() * 0.01 * sy, random.nextGaussian() * 0.01 * sz);
        }

        var bufferSource = e.getMultiBufferSource();
        VertexConsumer b = bufferSource.getBuffer(HOLOGRAM_RENDER_TYPE);

        var minX = (float) bounds.minX;
        var minY = (float) bounds.minY;
        var minZ = (float) bounds.minZ;
        var maxX = (float) bounds.maxX;
        var maxY = (float) bounds.maxY;
        var maxZ = (float) bounds.maxZ;

        int r = 0, g = 255, bCol = 0, a = 102;
        int fullLight = 15728880;

        switch (sideHit) {
          case UP -> {
            b.vertex(pose.last().pose(), maxX, maxY + 0.002f, maxZ).color(r, g, bCol, a).uv(maxZ * 16, maxX * 16).uv2(fullLight).normal(pose.last().normal(), 0.0F, 1.0F, 0.0F).endVertex();
            b.vertex(pose.last().pose(), maxX, maxY + 0.002f, minZ).color(r, g, bCol, a).uv(minZ * 16, maxX * 16).uv2(fullLight).normal(pose.last().normal(), 0.0F, 1.0F, 0.0F).endVertex();
            b.vertex(pose.last().pose(), minX, maxY + 0.002f, minZ).color(r, g, bCol, a).uv(minZ * 16, minX * 16).uv2(fullLight).normal(pose.last().normal(), 0.0F, 1.0F, 0.0F).endVertex();
            b.vertex(pose.last().pose(), minX, maxY + 0.002f, maxZ).color(r, g, bCol, a).uv(maxZ * 16, minX * 16).uv2(fullLight).normal(pose.last().normal(), 0.0F, 1.0F, 0.0F).endVertex();
          }
          case DOWN -> {
            b.vertex(pose.last().pose(), maxX, minY - 0.002f, minZ).color(r, g, bCol, a).uv(minZ * 16, maxX * 16).uv2(fullLight).normal(pose.last().normal(), 0.0F, -1.0F, 0.0F).endVertex();
            b.vertex(pose.last().pose(), maxX, minY - 0.002f, maxZ).color(r, g, bCol, a).uv(maxZ * 16, maxX * 16).uv2(fullLight).normal(pose.last().normal(), 0.0F, -1.0F, 0.0F).endVertex();
            b.vertex(pose.last().pose(), minX, minY - 0.002f, maxZ).color(r, g, bCol, a).uv(maxZ * 16, minX * 16).uv2(fullLight).normal(pose.last().normal(), 0.0F, -1.0F, 0.0F).endVertex();
            b.vertex(pose.last().pose(), minX, minY - 0.002f, minZ).color(r, g, bCol, a).uv(minZ * 16, minX * 16).uv2(fullLight).normal(pose.last().normal(), 0.0F, -1.0F, 0.0F).endVertex();
          }
          case EAST -> {
            b.vertex(pose.last().pose(), maxX + 0.002f, maxY, minZ).color(r, g, bCol, a).uv(minZ * 16, maxY * 16).uv2(fullLight).normal(pose.last().normal(), 1.0F, 0.0F, 0.0F).endVertex();
            b.vertex(pose.last().pose(), maxX + 0.002f, maxY, maxZ).color(r, g, bCol, a).uv(maxZ * 16, maxY * 16).uv2(fullLight).normal(pose.last().normal(), 1.0F, 0.0F, 0.0F).endVertex();
            b.vertex(pose.last().pose(), maxX + 0.002f, minY, maxZ).color(r, g, bCol, a).uv(maxZ * 16, minY * 16).uv2(fullLight).normal(pose.last().normal(), 1.0F, 0.0F, 0.0F).endVertex();
            b.vertex(pose.last().pose(), maxX + 0.002f, minY, minZ).color(r, g, bCol, a).uv(minZ * 16, minY * 16).uv2(fullLight).normal(pose.last().normal(), 1.0F, 0.0F, 0.0F).endVertex();
          }
          case WEST -> {
            b.vertex(pose.last().pose(), minX - 0.002f, maxY, maxZ).color(r, g, bCol, a).uv(maxZ * 16, maxY * 16).uv2(fullLight).normal(pose.last().normal(), -1.0F, 0.0F, 0.0F).endVertex();
            b.vertex(pose.last().pose(), minX - 0.002f, maxY, minZ).color(r, g, bCol, a).uv(minZ * 16, maxY * 16).uv2(fullLight).normal(pose.last().normal(), -1.0F, 0.0F, 0.0F).endVertex();
            b.vertex(pose.last().pose(), minX - 0.002f, minY, minZ).color(r, g, bCol, a).uv(minZ * 16, minY * 16).uv2(fullLight).normal(pose.last().normal(), -1.0F, 0.0F, 0.0F).endVertex();
            b.vertex(pose.last().pose(), minX - 0.002f, minY, maxZ).color(r, g, bCol, a).uv(maxZ * 16, minY * 16).uv2(fullLight).normal(pose.last().normal(), -1.0F, 0.0F, 0.0F).endVertex();
          }
          case SOUTH -> {
            b.vertex(pose.last().pose(), maxX, maxY, maxZ + 0.002f).color(r, g, bCol, a).uv(maxX * 16, maxY * 16).uv2(fullLight).normal(pose.last().normal(), 0.0F, 0.0F, 1.0F).endVertex();
            b.vertex(pose.last().pose(), minX, maxY, maxZ + 0.002f).color(r, g, bCol, a).uv(minX * 16, maxY * 16).uv2(fullLight).normal(pose.last().normal(), 0.0F, 0.0F, 1.0F).endVertex();
            b.vertex(pose.last().pose(), minX, minY, maxZ + 0.002f).color(r, g, bCol, a).uv(minX * 16, minY * 16).uv2(fullLight).normal(pose.last().normal(), 0.0F, 0.0F, 1.0F).endVertex();
            b.vertex(pose.last().pose(), maxX, minY, maxZ + 0.002f).color(r, g, bCol, a).uv(maxX * 16, minY * 16).uv2(fullLight).normal(pose.last().normal(), 0.0F, 0.0F, 1.0F).endVertex();
          }
          default -> {
            b.vertex(pose.last().pose(), minX, maxY, minZ - 0.002f).color(r, g, bCol, a).uv(minX * 16, maxY * 16).uv2(fullLight).normal(pose.last().normal(), 0.0F, 0.0F, -1.0F).endVertex();
            b.vertex(pose.last().pose(), maxX, maxY, minZ - 0.002f).color(r, g, bCol, a).uv(maxX * 16, maxY * 16).uv2(fullLight).normal(pose.last().normal(), 0.0F, 0.0F, -1.0F).endVertex();
            b.vertex(pose.last().pose(), maxX, minY, minZ - 0.002f).color(r, g, bCol, a).uv(maxX * 16, minY * 16).uv2(fullLight).normal(pose.last().normal(), 0.0F, 0.0F, -1.0F).endVertex();
            b.vertex(pose.last().pose(), minX, minY, minZ - 0.002f).color(r, g, bCol, a).uv(minX * 16, minY * 16).uv2(fullLight).normal(pose.last().normal(), 0.0F, 0.0F, -1.0F).endVertex();
          }
        }
        pose.popPose();
      }
    }

    if (hitInfo.getType() == HitResult.Type.BLOCK) {
      var te = level.getBlockEntity(blockPos);
      if (te instanceof Print print) {
        var state = level.getBlockState(blockPos);
        var facing = print.facing();
        var pose = e.getPoseStack();
        var camera = e.getCamera();
        var camPos = camera.getPosition();
        float expansion = 0.002f;

        var bufferSource = e.getMultiBufferSource();
        VertexConsumer lineBuilder = bufferSource.getBuffer(RenderType.lines());

        var shapes = print.state ? print.data.stateOn : print.data.stateOff;

        pose.pushPose();
        pose.translate(blockPos.getX() - camPos.x, blockPos.getY() - camPos.y, blockPos.getZ() - camPos.z);

        for (var shape : shapes) {
          var aabb = ExtendedAABB.rotateTowards(shape.bounds(), facing).inflate(expansion, expansion, expansion);
          LevelRenderer.renderLineBox(pose, lineBuilder, aabb, 0.0f, 0.0f, 0.0f, 0.4f);
        }
        pose.popPose();

        e.setCanceled(true);
      }
    }

    if (hitInfo.getType() == HitResult.Type.BLOCK) {
      var te = level.getBlockEntity(blockPos);
      boolean isCable = te instanceof Cable;
      boolean isMultipartCable = !isCable && li.cil.oc.neoforge.common.MultipartHooks.isCableHit(te, hitInfo);
      if (isCable || isMultipartCable) {
        var pose = e.getPoseStack();
        var camera = e.getCamera();
        var camPos = camera.getPosition();

        pose.pushPose();
        pose.translate(blockPos.getX() - camPos.x, blockPos.getY() - camPos.y, blockPos.getZ() - camPos.z);

        var bufferSource = e.getMultiBufferSource();
        VertexConsumer lineBuilder = bufferSource.getBuffer(RenderType.lines());

        int mask = getCableConnections(level, blockPos);
        drawCableOverlay(lineBuilder, pose, mask);

        pose.popPose();

        if (!isMultipartCable) {
          e.setCanceled(true);
        }
      }
    }
  }

  private static final double CABLE_MIN = 0.375;
  private static final double CABLE_MAX = 0.625;
  private static final double CABLE_EXPAND = 0.002;

  // a block or multipart cable's: as its shape and model
  private static int getCableConnections(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
    return li.cil.oc.neoforge.common.block.Cable.connections(level, pos);
  }

  @SuppressWarnings("unused")
  private static void drawCableOverlay(VertexConsumer consumer, com.mojang.blaze3d.vertex.PoseStack pose, int mask) {
    var last = pose.last();

    for (Direction side : Direction.values()) {
      if ((mask & (1 << side.get3DDataValue())) != 0) {
        double offset = side.getAxisDirection() == net.minecraft.core.Direction.AxisDirection.NEGATIVE ? -CABLE_EXPAND : 1 + CABLE_EXPAND;
        double centre = side.getAxisDirection() == net.minecraft.core.Direction.AxisDirection.NEGATIVE ? CABLE_MIN : CABLE_MAX;

        drawLineAdjacent(consumer, last, side.getAxis(), offset, CABLE_MIN, CABLE_MIN, CABLE_MIN, CABLE_MAX);
        drawLineAdjacent(consumer, last, side.getAxis(), offset, CABLE_MIN, CABLE_MAX, CABLE_MAX, CABLE_MAX);
        drawLineAdjacent(consumer, last, side.getAxis(), offset, CABLE_MAX, CABLE_MAX, CABLE_MAX, CABLE_MIN);
        drawLineAdjacent(consumer, last, side.getAxis(), offset, CABLE_MAX, CABLE_MIN, CABLE_MIN, CABLE_MIN);

        drawLineAlong(consumer, last, side.getAxis(), CABLE_MIN, CABLE_MIN, offset, centre);
        drawLineAlong(consumer, last, side.getAxis(), CABLE_MAX, CABLE_MIN, offset, centre);
        drawLineAlong(consumer, last, side.getAxis(), CABLE_MAX, CABLE_MAX, offset, centre);
        drawLineAlong(consumer, last, side.getAxis(), CABLE_MIN, CABLE_MAX, offset, centre);
      }
    }

    drawCore(consumer, last, mask, Direction.WEST, Direction.DOWN, Direction.Axis.Z);
    drawCore(consumer, last, mask, Direction.WEST, Direction.UP, Direction.Axis.Z);
    drawCore(consumer, last, mask, Direction.EAST, Direction.DOWN, Direction.Axis.Z);
    drawCore(consumer, last, mask, Direction.EAST, Direction.UP, Direction.Axis.Z);
    drawCore(consumer, last, mask, Direction.WEST, Direction.NORTH, Direction.Axis.Y);
    drawCore(consumer, last, mask, Direction.WEST, Direction.SOUTH, Direction.Axis.Y);
    drawCore(consumer, last, mask, Direction.EAST, Direction.NORTH, Direction.Axis.Y);
    drawCore(consumer, last, mask, Direction.EAST, Direction.SOUTH, Direction.Axis.Y);
    drawCore(consumer, last, mask, Direction.DOWN, Direction.NORTH, Direction.Axis.X);
    drawCore(consumer, last, mask, Direction.DOWN, Direction.SOUTH, Direction.Axis.X);
    drawCore(consumer, last, mask, Direction.UP, Direction.NORTH, Direction.Axis.X);
    drawCore(consumer, last, mask, Direction.UP, Direction.SOUTH, Direction.Axis.X);
  }

  private static void drawCore(VertexConsumer consumer, com.mojang.blaze3d.vertex.PoseStack.Pose pose, int mask, Direction a, Direction b, Direction.Axis other) {
    if (((mask >> a.get3DDataValue()) & 1) != ((mask >> b.get3DDataValue()) & 1)) return;
    double offA = a.getAxisDirection() == net.minecraft.core.Direction.AxisDirection.NEGATIVE ? CABLE_MIN : CABLE_MAX;
    double offB = b.getAxisDirection() == net.minecraft.core.Direction.AxisDirection.NEGATIVE ? CABLE_MIN : CABLE_MAX;
    drawLineAlong(consumer, pose, other, offA, offB, CABLE_MIN, CABLE_MAX);
  }

  private static void drawLineAlong(VertexConsumer consumer, com.mojang.blaze3d.vertex.PoseStack.Pose pose, Direction.Axis axis, double offA, double offB, double start, double end) {
    switch (axis) {
      case X -> line(consumer, pose, start, offA, offB, end, offA, offB);
      case Y -> line(consumer, pose, offA, start, offB, offA, end, offB);
      case Z -> line(consumer, pose, offA, offB, start, offA, offB, end);
    }
  }

  private static void drawLineAdjacent(VertexConsumer consumer, com.mojang.blaze3d.vertex.PoseStack.Pose pose, Direction.Axis axis, double offset, double startA, double startB, double endA, double endB) {
    switch (axis) {
      case X -> line(consumer, pose, offset, startA, startB, offset, endA, endB);
      case Y -> line(consumer, pose, startA, offset, startB, endA, offset, endB);
      case Z -> line(consumer, pose, startA, startB, offset, endA, endB, offset);
    }
  }

  // as LevelRenderer.renderShape: the lines shader widens each line across its direction, given as the normal (one
  // fixed normal for all made every line along it a zero-width line: the selection box lost its horizontal edges)
  private static void line(VertexConsumer consumer, com.mojang.blaze3d.vertex.PoseStack.Pose pose, double x0, double y0, double z0, double x1, double y1, double z1) {
    float dx = (float) (x1 - x0);
    float dy = (float) (y1 - y0);
    float dz = (float) (z1 - z0);
    float length = net.minecraft.util.Mth.sqrt(dx * dx + dy * dy + dz * dz);
    dx /= length;
    dy /= length;
    dz /= length;
    consumer.vertex(pose.pose(), (float) x0, (float) y0, (float) z0).color(0, 0, 0, 0.4f).normal(pose.normal(), dx, dy, dz).endVertex();
    consumer.vertex(pose.pose(), (float) x1, (float) y1, (float) z1).color(0, 0, 0, 0.4f).normal(pose.normal(), dx, dy, dz).endVertex();
  }
}
