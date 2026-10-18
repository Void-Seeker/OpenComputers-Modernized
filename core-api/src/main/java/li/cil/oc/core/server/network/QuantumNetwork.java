package li.cil.oc.core.server.network;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import li.cil.oc.api.network.Packet;

public final class QuantumNetwork {
  private static final Map<String, Map<QuantumNode, Boolean>> tunnels = new HashMap<>();

  private QuantumNetwork() {
  }

  public static void add(QuantumNode card) {
    tunnels.computeIfAbsent(card.tunnel(), k -> Collections.synchronizedMap(new WeakHashMap<>())).put(card, true);
  }

  public static void remove(QuantumNode card) {
    Map<QuantumNode, Boolean> map = tunnels.get(card.tunnel());
    if (map != null) map.remove(card);
  }

  /** A copy: the linked card removed itself from the live key set, and was out of its tunnel after its first send. */
  public static Collection<QuantumNode> getEndpoints(String tunnel) {
    Map<QuantumNode, Boolean> map = tunnels.get(tunnel);
    if (map == null) return Collections.emptyList();
    synchronized (map) {
      return new java.util.ArrayList<>(map.keySet());
    }
  }

  public interface QuantumNode {
    String tunnel();

    void receivePacket(Packet packet);
  }
}
