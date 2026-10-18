package li.cil.oc.core.impl.server.component;

import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.core.impl.common.blockentity.Microcontroller;
import li.cil.oc.core.impl.common.entity.Drone;
import li.cil.oc.core.impl.common.item.TabletWrapper;
import li.cil.oc.core.impl.util.BlockPosition;
import li.cil.oc.core.impl.util.ExtendedArguments;
import li.cil.oc.core.impl.util.GeolyzerHostHelper;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;

public class Geolyzer extends GeolyzerBase {
  public Geolyzer(EnvironmentHost host) {
    super(host);
  }

  @Override
  public BlockPosition position() {
    if (GeolyzerHostHelper.get() != null && GeolyzerHostHelper.get().isRobot(host))
      return GeolyzerHostHelper.get().robotPosition(host);
    if (host instanceof Drone) return BlockPosition.apply((Entity) host);
    if (host instanceof Microcontroller) return ((Microcontroller) host).position();
    if (host instanceof TabletWrapper) return BlockPosition.apply(((TabletWrapper) host).player());
    return BlockPosition.apply(host);
  }

  @Override
  public Direction checkSideForAction(Arguments args, int n) {
    Direction side = ExtendedArguments.checkSideAny(args, n);
    // a robot's, drone's or microcontroller's own sides (a robot's geolyzer has the robot, not its proxy, as host: it
    // used world sides; a microcontroller's turned the wrong way, toLocal)
    if (host instanceof li.cil.oc.api.internal.Rotatable rotatable) return rotatable.toGlobal(side);
    if (GeolyzerHostHelper.get() != null && GeolyzerHostHelper.get().isRobot(host))
      return GeolyzerHostHelper.get().robotToGlobal(host, side);
    return side;
  }
}
