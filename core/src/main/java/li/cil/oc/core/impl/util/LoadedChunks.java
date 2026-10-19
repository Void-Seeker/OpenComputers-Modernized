package li.cil.oc.core.impl.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public final class LoadedChunks {
  private LoadedChunks() {
  }

  /**
   * Whether the chunk holding the position is loaded, not only on its way (hasChunk only asks the ticket level: a chunk
   * still loading was then loaded there and then, the server thread waiting for it).
   */
  public static boolean isReady(Level level, BlockPos pos) {
    if (level == null) return false;
    int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
    if (level instanceof ServerLevel server && server.getServer().isSameThread()) {
      return server.getChunkSource().getChunkNow(cx, cz) != null;
    }
    return level.hasChunk(cx, cz);
  }
}
