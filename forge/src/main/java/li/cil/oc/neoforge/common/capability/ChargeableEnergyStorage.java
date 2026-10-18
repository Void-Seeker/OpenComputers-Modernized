package li.cil.oc.neoforge.common.capability;

import li.cil.oc.api.driver.item.Chargeable;
import li.cil.oc.core.impl.OCSettings;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.energy.IEnergyStorage;

public final class ChargeableEnergyStorage implements IEnergyStorage {
  private final ItemStack stack;
  private final Chargeable item;

  public ChargeableEnergyStorage(ItemStack stack, Chargeable item) {
    this.stack = stack;
    this.item = item;
  }

  private static double ratio() {
    return OCSettings.get().ratioRedstoneFlux();
  }

  // FE to OC is a multiplication by the ratio (OC per FE), as for blocks (Power.fromRF); this divided, the other
  // way round: items were charged for a hundredth of the price
  @Override
  public int receiveEnergy(int maxReceive, boolean simulate) {
    double ocAmount = maxReceive * ratio();
    double unused = item.charge(stack, ocAmount, simulate);
    double usedOc = ocAmount - unused;
    return (int) Math.ceil(usedOc / ratio() - 1e-6);
  }

  @Override
  public int extractEnergy(int maxExtract, boolean simulate) {
    if (!canExtract()) return 0;
    double ocAmount = -maxExtract * ratio();
    double unused = item.charge(stack, ocAmount, simulate);
    double extractedOc = Math.abs(ocAmount - unused);
    return (int) Math.floor(extractedOc / ratio() + 1e-6);
  }

  // what it holds and can hold, also for the items with only the API's Chargeable (tablets, hover boots: 0 of 0 before,
  // and other mods' chargers skipped them)
  @Override
  public int getEnergyStored() {
    return (int) (charge(false) / ratio());
  }

  @Override
  public int getMaxEnergyStored() {
    return (int) (charge(true) / ratio());
  }

  private double charge(boolean max) {
    // per item in the stack (battery upgrades stack)
    if (item instanceof li.cil.oc.core.impl.common.item.traits.Chargeable c) return (max ? c.maxCharge(stack) : c.getCharge(stack)) * Math.max(1, stack.getCount());
    if (item instanceof li.cil.oc.core.impl.common.item.HoverBoots b) return max ? b.maxCharge(stack) : b.getCharge(stack);
    if (item instanceof li.cil.oc.core.impl.common.item.Tablet) {
      var data = new li.cil.oc.core.impl.common.item.data.TabletData(stack);
      return max ? data.maxEnergy : data.energy;
    }
    return 0;
  }

  @Override
  public boolean canExtract() {
    if (item instanceof li.cil.oc.core.impl.common.item.traits.Chargeable coreChargeable) {
      return coreChargeable.canExtract(stack);
    }
    return stack.getItem().getClass().getName().contains("UpgradeBattery");
  }

  @Override
  public boolean canReceive() {
    return item.canCharge(stack);
  }
}
