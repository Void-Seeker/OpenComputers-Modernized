package li.cil.oc.core.impl.server.network;

import java.util.HashMap;
import java.util.Map;
import li.cil.oc.api.network.Node;
import li.cil.oc.core.impl.OCSettings;
import li.cil.oc.core.impl.common.blockentity.Waypoint;
import li.cil.oc.core.impl.util.BlockPosition;
import li.cil.oc.core.util.RTree;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class WaypointManager {
  private static final Map<ResourceKey<Level>, RTree<Waypoint>> dimensions = new HashMap<>();

  private WaypointManager() {
  }

  public record WaypointData(BlockPosition position, Direction facing, int maxInput, String label, Node node) {
  }

  public static void add(Waypoint waypoint) {
    if (!waypoint.isRemoved() && waypoint.level() != null && !waypoint.level().isClientSide()) {
      dimensions.computeIfAbsent(dimension(waypoint), k -> new RTree<>(OCSettings.get().rTreeMaxEntries, wp -> new double[]{wp.xPosition() + 0.5, wp.yPosition() + 0.5, wp.zPosition() + 0.5}))
        .add(waypoint);
    }
  }

  public static void remove(Waypoint waypoint) {
    if (waypoint.level() != null && !waypoint.level().isClientSide()) {
      RTree<Waypoint> set = dimensions.get(dimension(waypoint));
      if (set != null) set.remove(waypoint);
    }
  }

  public static void removeDimension(ResourceKey<Level> dimension) {
    dimensions.remove(dimension);
  }

  public static Iterable<WaypointData> findWaypoints(BlockPosition pos, double range) {
    ResourceKey<Level> dim = pos.level() != null ? pos.level().dimension() : Level.OVERWORLD;
    RTree<Waypoint> set = dimensions.get(dim);
    if (set != null) {
      // the whole range around (the box reached half of it, and the distance filter after it the whole)
      double[] min = {pos.x() - range, pos.y() - range, pos.z() - range};
      double[] max = {pos.x() + 1 + range, pos.y() + 1 + range, pos.z() + 1 + range};
      Iterable<Waypoint> results = set.query(min, max);
      return () -> com.google.common.collect.Iterators.transform(results.iterator(), tile ->
        new WaypointData(
          BlockPosition.apply(tile.xPosition(), tile.yPosition(), tile.zPosition(), tile.level()),
          tile.facing(), tile.maxInput(), tile.label, tile.node()));
    }
    return java.util.Collections.emptyList();
  }

  private static ResourceKey<Level> dimension(Waypoint waypoint) {
    return waypoint.level().dimension();
  }
}
