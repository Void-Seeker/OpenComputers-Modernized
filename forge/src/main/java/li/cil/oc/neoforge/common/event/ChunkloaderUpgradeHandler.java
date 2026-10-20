package li.cil.oc.neoforge.common.event;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import li.cil.oc.api.event.RobotMoveEvent;
import li.cil.oc.api.network.Node;
import li.cil.oc.core.impl.util.BlockPosition;
import li.cil.oc.neoforge.OpenComputers;
import li.cil.oc.neoforge.server.component.UpgradeChunkloader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Loaders force chunks with Forge tickets of OC's, one owner per loader (its node's address), as OC's tickets: the
 * vanilla forced flag is one per chunk for everyone, and a loader letting go of a chunk unforced it for a player's
 * /forceload or another loader too (and the flags, saved with no owner, stayed after anything went wrong).
 */
public final class ChunkloaderUpgradeHandler {
  /** Ticks a restored ticket's loader has to take it back once the chunk it was in is loaded, entities and all. */
  public static final int RECLAIM_TICKS = 100;

  private static final Map<Level, Map<UUID, Set<Long>>> worldChunks = new HashMap<>();
  // tickets saved with a level, kept (they load their loaders' chunks) until their loaders take them back, as OC's
  // restoredTickets; the ones nothing takes back are let go
  private static final Map<Level, Map<UUID, Restored>> restoredTickets = new HashMap<>();

  private static final class Restored {
    final Set<Long> chunks;
    final long center;
    int ticks;

    Restored(Set<Long> chunks) {
      this.chunks = chunks;
      int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
      for (long chunk : chunks) {
        minX = Math.min(minX, ChunkPos.getX(chunk));
        maxX = Math.max(maxX, ChunkPos.getX(chunk));
        minZ = Math.min(minZ, ChunkPos.getZ(chunk));
        maxZ = Math.max(maxZ, ChunkPos.getZ(chunk));
      }
      this.center = ChunkPos.asLong((minX + maxX) >> 1, (minZ + maxZ) >> 1);
    }
  }

  private static UUID owner(UpgradeChunkloader loader) {
    String address = loader.node.address();
    try {
      return UUID.fromString(address);
    } catch (IllegalArgumentException e) {
      return UUID.nameUUIDFromBytes(address.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
  }

  // not ticking: loaded as by vanilla's flag, without the random ticks and spawns of a player nearby
  private static void force(ServerLevel level, UUID owner, long chunk, boolean add) {
    ForgeChunkManager.forceChunk(level, OpenComputers.ID, owner, ChunkPos.getX(chunk), ChunkPos.getZ(chunk), add, false);
  }

  public static void updateLoadedChunk(UpgradeChunkloader loader) {
    var host = loader.host;
    if (!(host.level() instanceof ServerLevel level)) return;

    var pos = BlockPosition.apply(host);
    int centerChunkX = pos.x() >> 4;
    int centerChunkZ = pos.z() >> 4;
    UUID owner = owner(loader);

    var chunks = worldChunks.computeIfAbsent(level, k -> new HashMap<>());

    Set<Long> newChunks = new HashSet<>();
    for (int dx = -1; dx <= 1; dx++) {
      for (int dz = -1; dz <= 1; dz++) {
        newChunks.add(ChunkPos.asLong(centerChunkX + dx, centerChunkZ + dz));
      }
    }

    Set<Long> oldChunks = chunks.get(owner);
    if (oldChunks == null) {
      var restored = restoredTickets.get(level);
      var own = restored == null ? null : restored.remove(owner); // taken back
      oldChunks = own == null ? Set.of() : own.chunks;
    }

    for (long chunk : oldChunks) {
      if (!newChunks.contains(chunk)) force(level, owner, chunk, false);
    }

    for (long chunk : newChunks) {
      if (!oldChunks.contains(chunk)) force(level, owner, chunk, true);
    }

    chunks.put(owner, newChunks);
  }

  public static void releaseLoadedChunk(UpgradeChunkloader loader) {
    var host = loader.host;
    if (!(host.level() instanceof ServerLevel level)) return;

    var worldMap = worldChunks.get(level);
    if (worldMap == null) return;

    UUID owner = owner(loader);
    var oldChunks = worldMap.remove(owner);
    if (oldChunks == null) return;

    for (long chunk : oldChunks) {
      force(level, owner, chunk, false);
    }

    if (worldMap.isEmpty()) {
      worldChunks.remove(level);
    }
  }

  /** The tickets saved with a level, as it loads (before they force their chunks): kept for their loaders. */
  public static void validateTickets(ServerLevel level, ForgeChunkManager.TicketHelper tickets) {
    var held = worldChunks.getOrDefault(level, Map.of());
    var restored = restoredTickets.computeIfAbsent(level, k -> new HashMap<>());
    for (var entry : tickets.getEntityTickets().entrySet()) {
      var chunks = entry.getValue().getFirst(); // (none ticking)
      if (!chunks.isEmpty() && !held.containsKey(entry.getKey())) restored.put(entry.getKey(), new Restored(new HashSet<>(chunks)));
    }
    if (restored.isEmpty()) restoredTickets.remove(level);
  }

  // a restored ticket nothing took back once the chunk its loader was in has loaded and ticked a while is let go (OC let
  // them go at the next save, which may come before the chunks load: a quit right after loading)
  @SubscribeEvent
  public static void onLevelTick(TickEvent.LevelTickEvent e) {
    if (e.phase != TickEvent.Phase.END || !(e.level instanceof ServerLevel level)) return;
    var restored = restoredTickets.get(level);
    if (restored == null) return;
    restored.entrySet().removeIf(entry -> {
      var ticket = entry.getValue();
      var center = new ChunkPos(ticket.center);
      if (!level.areEntitiesLoaded(ticket.center) || !level.isPositionEntityTicking(center.getWorldPosition()) || ++ticket.ticks < RECLAIM_TICKS) {
        return false;
      }
      OpenComputers.log().info("Releasing the orphaned chunk loader ticket of {} around chunk {}.", entry.getKey(), center);
      for (long chunk : ticket.chunks) {
        force(level, entry.getKey(), chunk, false);
      }
      return true;
    });
    if (restored.isEmpty()) restoredTickets.remove(level);
  }

  // only an active loader's chunks go along (every move forced chunks, for free, which nothing let go of again)
  @SubscribeEvent
  public static void onMove(RobotMoveEvent.Post e) {
    Node machineNode = e.agent.machine().node();
    for (Node node : machineNode.reachableNodes()) {
      if (node.host() instanceof UpgradeChunkloader loader && loader.active()) {
        updateLoadedChunk(loader);
      }
    }
  }

  // the stopped server's levels are let go (they were kept, and with them every loader's chunks of a world left)
  @SubscribeEvent
  public static void onServerStopped(ServerStoppedEvent e) {
    worldChunks.clear();
    restoredTickets.clear();
  }

}
