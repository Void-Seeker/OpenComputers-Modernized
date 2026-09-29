package li.cil.oc.neoforge.server;

import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.level.LevelEvent;

public final class ComponentTracker extends li.cil.oc.core.impl.common.ComponentTracker {
  public static final ComponentTracker INSTANCE = new ComponentTracker();

  private ComponentTracker() {
  }

  @SubscribeEvent
  @SuppressWarnings("unused")
  public void onWorldUnload(LevelEvent.Unload e) {
    if (!e.getLevel().isClientSide()) {
      clear((Level) e.getLevel());
    }
  }

  @Override
  public void clear(Level world) {
    if (!world.isClientSide) super.clear(world);
  }
}
