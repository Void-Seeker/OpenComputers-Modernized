package li.cil.oc.neoforge.integration.computercraft;

import dan200.computercraft.api.peripheral.IPeripheral;
import li.cil.oc.neoforge.compat.BlockCapability;
import net.minecraft.core.Direction;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import org.jetbrains.annotations.Nullable;

/**
 * CC:Tweaked 1.20.1 exposes peripherals as a Forge capability of {@link IPeripheral} but keeps the
 * capability object internal. Forge capabilities are keyed by type, so a token yields the same one.
 * Stand-in for CC:Tweaked's NeoForge {@code PeripheralCapability}.
 */
public final class PeripheralCapability {
  private static final BlockCapability<IPeripheral, @Nullable Direction> CAPABILITY =
    BlockCapability.of(CapabilityManager.get(new CapabilityToken<IPeripheral>() {
    }));

  private PeripheralCapability() {
  }

  public static BlockCapability<IPeripheral, @Nullable Direction> get() {
    return CAPABILITY;
  }
}
