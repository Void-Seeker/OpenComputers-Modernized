package li.cil.oc.core.impl.common.blockentity.traits;

import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.SidedEnvironment;
import li.cil.oc.core.impl.OCSettings;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;


public interface PowerBalancer extends PowerInformation, SidedEnvironment {
  double globalBuffer();

  void globalBuffer(double value);

  double globalBufferSize();

  void globalBufferSize(double value);

  boolean isServer();

  @SuppressWarnings("unused")
  boolean isConnected();

  @SuppressWarnings("unused")
  void updateEntity();

  /**
   * Evens out the energy of the networks at its sides, every tickFrequency ticks (OC's PowerBalancer.updateEntity; only
   * the power distributor did it: a rack's side networks never shared their energy).
   */
  default void balancePower(Level level) {
    if (level == null || level.isClientSide) return;
    if (!isConnected()) return;
    if (level.getGameTime() % OCSettings.get().tickFrequency != 0) return;

    var connectors = new Connector[6];
    for (var side : Direction.values()) {
      if (sidedNode(side) instanceof Connector c) connectors[side.ordinal()] = c;
    }

    // (power changes in other threads too, e.g. screens in a computer's executor; this locks only in the server thread)
    var lock0 = networkLock(connectors[0]);
    var lock1 = networkLock(connectors[1]);
    var lock2 = networkLock(connectors[2]);
    var lock3 = networkLock(connectors[3]);
    var lock4 = networkLock(connectors[4]);
    var lock5 = networkLock(connectors[5]);

    synchronized (lock0) {
      synchronized (lock1) {
        synchronized (lock2) {
          synchronized (lock3) {
            synchronized (lock4) {
              synchronized (lock5) {
                double sumBuffer = 0, sumSize = 0;
                var primary = new java.util.ArrayList<Connector>();
                for (int i = 0; i < 6; i++) {
                  var c = connectors[i];
                  if (c != null && isPrimary(connectors, c)) {
                    primary.add(c);
                    sumBuffer += c.globalBuffer();
                    sumSize += c.globalBufferSize();
                  }
                }
                if (sumSize > 0) {
                  double ratio = sumBuffer / sumSize;
                  for (var c : primary) {
                    c.changeBuffer(c.globalBufferSize() * ratio - c.globalBuffer());
                  }
                }
                globalBuffer(sumBuffer);
                globalBufferSize(sumSize);
              }
            }
          }
        }
      }
    }
    updatePowerInformation();
  }

  private Object networkLock(Connector c) {
    return (c != null && c.network() != null) ? c.network() : this;
  }

  // the first of its connectors in that network
  private static boolean isPrimary(Connector[] connectors, Connector c) {
    for (var other : connectors) {
      if (other != null && other.network() == c.network()) return other == c;
    }
    return true;
  }
}
