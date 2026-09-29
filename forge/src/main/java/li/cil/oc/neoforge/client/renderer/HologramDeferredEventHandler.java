package li.cil.oc.neoforge.client.renderer;

import li.cil.oc.neoforge.client.renderer.blockentity.HologramRenderer;
import net.minecraft.client.Minecraft;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;

public final class HologramDeferredEventHandler {

  @SubscribeEvent
  @SuppressWarnings("unused")
  public static void onRenderLevelStage(RenderLevelStageEvent e) {
    if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

    var bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
    HologramRenderer.drawPending(bufferSource);
  }
}
