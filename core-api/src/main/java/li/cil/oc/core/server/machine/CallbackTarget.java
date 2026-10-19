package li.cil.oc.core.server.machine;

/**
 * A node host whose callbacks are another object's, and are called on it: the host OC gives a block entity implementing
 * {@link li.cil.oc.api.network.SimpleComponent} (OC 1.12 made the block entity itself the host).
 */
public interface CallbackTarget {
  Object callbackTarget();
}
