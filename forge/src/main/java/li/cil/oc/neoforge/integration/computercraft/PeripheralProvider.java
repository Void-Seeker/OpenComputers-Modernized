package li.cil.oc.neoforge.integration.computercraft;

import dan200.computercraft.api.peripheral.PeripheralCapability;
import li.cil.oc.core.impl.common.blockentity.Relay;
import li.cil.oc.core.impl.integration.computercraft.RelayPeripheral;
import li.cil.oc.neoforge.common.init.Blocks;
import li.cil.oc.neoforge.compat.CapabilityRegistrar;

public final class PeripheralProvider {
  private PeripheralProvider() {
  }

  public static void registerCapabilities(CapabilityRegistrar event) {
    try {
      event.registerBlock(PeripheralCapability.get(),
        (level, pos, state, blockEntity, side) -> {
          if (blockEntity instanceof Relay relay) {
            return new RelayPeripheral(relay);
          }
          return null;
        },
        Blocks.RELAY.get()
      );
    } catch (Exception | NoClassDefFoundError ignored) {
    }
  }
}
