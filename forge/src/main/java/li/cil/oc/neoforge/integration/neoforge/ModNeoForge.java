package li.cil.oc.neoforge.integration.neoforge;

import li.cil.oc.core.impl.common.Registrar;
import li.cil.oc.core.impl.integration.util.Power;
import li.cil.oc.neoforge.integration.Mod;
import li.cil.oc.neoforge.integration.ModProxy;
import li.cil.oc.neoforge.integration.Mods;
import net.minecraft.world.item.ItemStack;
import li.cil.oc.neoforge.compat.Capabilities;
import net.minecraftforge.energy.IEnergyStorage;

@SuppressWarnings("unused")
public final class ModNeoForge implements ModProxy {
  @Override
  public Mod getMod() {
    return Mods.Minecraft;
  }

  @Override
  public void initialize() {
    li.cil.oc.api.API.driver.add(new DriverEnergyStorage());

    Registrar.registerItemCharge(
      "MinecraftForge",
      "li.cil.oc.neoforge.integration.neoforge.ModNeoForge.canCharge",
      "li.cil.oc.neoforge.integration.neoforge.ModNeoForge.charge");

    li.cil.oc.api.API.driver.add(new DriverItemHandler());
  }

  public static boolean canCharge(ItemStack stack) {
    IEnergyStorage storage = Capabilities.EnergyStorage.ITEM.getCapability(stack);
    return storage != null && storage.canReceive();
  }

  public static double charge(ItemStack stack, double amount, boolean simulate) {
    IEnergyStorage storage = Capabilities.EnergyStorage.ITEM.getCapability(stack);
    if (storage != null) {
      int received = storage.receiveEnergy(Power.toRF(amount), simulate);
      return amount - Power.fromRF(received);
    }
    return amount;
  }
}
