package li.cil.oc.neoforge.common.event;

import java.util.HashMap;
import java.util.Map;
import li.cil.oc.core.impl.util.BlockPosition;
import li.cil.oc.neoforge.common.EventHandler;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

public final class BlockChangeHandler {
  private static final Map<ChangeListener, BlockPosition> changeListeners = new HashMap<>();

  public static void addListener(ChangeListener listener, BlockPosition coord) {
    EventHandler.scheduleServer(() -> changeListeners.put(listener, coord));
  }

  public static void removeListener(ChangeListener listener) {
    EventHandler.scheduleServer(() -> changeListeners.remove(listener));
  }

  // a stopped server's listeners go: their removal, scheduled as their nodes left, never ran (the queue is dropped),
  // and each kept its MFU's host and level for the rest of the game
  @SuppressWarnings("unused")
  @SubscribeEvent
  public static void onServerStopped(ServerStoppedEvent e) {
    changeListeners.clear();
  }

  @SuppressWarnings("unused")
  @SubscribeEvent
  public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent e) {
    BlockPosition current = BlockPosition.apply(e.getPos().getX(), e.getPos().getY(), e.getPos().getZ(), (Level) e.getLevel());
    for (Map.Entry<ChangeListener, BlockPosition> entry : changeListeners.entrySet()) {
      if (entry.getValue().equals(current)) {
        entry.getKey().onBlockChanged();
      }
    }
  }

  public interface ChangeListener {
    void onBlockChanged();
  }
}
