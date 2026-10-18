package li.cil.oc.neoforge.common.event;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import li.cil.oc.api.event.RobotMoveEvent;
import li.cil.oc.api.network.Node;
import li.cil.oc.core.impl.util.BlockPosition;
import li.cil.oc.neoforge.server.component.UpgradeChunkloader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class ChunkloaderUpgradeHandler {
  private static final Map<Level, Map<String, Set<Long>>> worldChunks = new HashMap<>();

  public static void updateLoadedChunk(UpgradeChunkloader loader) {
    var host = loader.host;
    var level = host.level();
    if (!(level instanceof ServerLevel serverLevel)) return;

    var pos = BlockPosition.apply(host);
    int centerChunkX = pos.x() >> 4;
    int centerChunkZ = pos.z() >> 4;
    String address = loader.node.address();

    var chunks = worldChunks.computeIfAbsent(level, k -> new HashMap<>());

    Set<Long> newChunks = new HashSet<>();
    for (int dx = -1; dx <= 1; dx++) {
      for (int dz = -1; dz <= 1; dz++) {
        newChunks.add(ChunkPos.asLong(centerChunkX + dx, centerChunkZ + dz));
      }
    }

    Set<Long> oldChunks = chunks.getOrDefault(address, Set.of());

    for (long chunk : oldChunks) {
      if (!newChunks.contains(chunk) && !claimedByOthers(chunks, address, chunk)) {
        serverLevel.setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), false);
      }
    }

    for (long chunk : newChunks) {
      if (!oldChunks.contains(chunk)) {
        serverLevel.setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), true);
      }
    }

    chunks.put(address, newChunks);
  }

  // Forced chunks are one flag per chunk for everyone: a loader leaving a chunk unforced it while another loader there
  // still needed it (and swarms of drones crossing each other's areas did so all the time).
  private static boolean claimedByOthers(Map<String, Set<Long>> chunks, String address, long chunk) {
    for (var entry : chunks.entrySet()) {
      if (!entry.getKey().equals(address) && entry.getValue().contains(chunk)) return true;
    }
    return false;
  }

  public static void releaseLoadedChunk(UpgradeChunkloader loader) {
    var host = loader.host;
    var level = host.level();
    if (!(level instanceof ServerLevel serverLevel)) return;

    var worldMap = worldChunks.get(level);
    if (worldMap == null) return;

    var oldChunks = worldMap.remove(loader.node.address());
    if (oldChunks == null) return;

    for (long chunk : oldChunks) {
      if (!claimedByOthers(worldMap, null, chunk)) {
        serverLevel.setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), false);
      }
    }

    if (worldMap.isEmpty()) {
      worldChunks.remove(level);
    }
  }

  @SubscribeEvent
  public static void onMove(RobotMoveEvent.Post e) {
    Node machineNode = e.agent.machine().node();
    for (Node node : machineNode.reachableNodes()) {
      if (node.host() instanceof UpgradeChunkloader) {
        updateLoadedChunk((UpgradeChunkloader) node.host());
      }
    }
  }

}
