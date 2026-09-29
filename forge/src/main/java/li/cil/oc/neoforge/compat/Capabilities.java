package li.cil.oc.neoforge.compat;

import net.minecraft.core.Direction;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Stand-in for NeoForge's {@code Capabilities}, backed by {@link ForgeCapabilities}.
 */
public final class Capabilities {
  private Capabilities() {
  }

  public static final class EnergyStorage {
    public static final BlockCapability<IEnergyStorage, @Nullable Direction> BLOCK = BlockCapability.of(ForgeCapabilities.ENERGY);
    public static final ItemCapability<IEnergyStorage, @Nullable Void> ITEM = ItemCapability.of(ForgeCapabilities.ENERGY);

    private EnergyStorage() {
    }
  }

  public static final class FluidHandler {
    public static final BlockCapability<IFluidHandler, @Nullable Direction> BLOCK = BlockCapability.of(ForgeCapabilities.FLUID_HANDLER);
    public static final ItemCapability<IFluidHandlerItem, @Nullable Void> ITEM = ItemCapability.of(ForgeCapabilities.FLUID_HANDLER_ITEM);

    private FluidHandler() {
    }
  }

  public static final class ItemHandler {
    public static final BlockCapability<IItemHandler, @Nullable Direction> BLOCK = BlockCapability.of(ForgeCapabilities.ITEM_HANDLER);
    public static final ItemCapability<IItemHandler, @Nullable Void> ITEM = ItemCapability.of(ForgeCapabilities.ITEM_HANDLER);

    private ItemHandler() {
    }
  }
}
