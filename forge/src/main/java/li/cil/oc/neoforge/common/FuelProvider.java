package li.cil.oc.neoforge.common;

import li.cil.oc.core.impl.server.component.UpgradeGenerator;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;

public class FuelProvider implements UpgradeGenerator.IFuelProvider {
  public static final FuelProvider INSTANCE = new FuelProvider();

  private FuelProvider() {
  }

  // 1.20.1: ItemStack.getBurnTime is only an item's own override (-1 for every vanilla fuel - the generator took no
  // coal, no lava); the furnace's burn time, with vanilla's table and Forge's event, is ForgeHooks.getBurnTime
  @Override
  public boolean isFuel(ItemStack stack) {
    return getBurnTime(stack) > 0;
  }

  @Override
  public int getBurnTime(ItemStack stack) {
    return net.minecraftforge.common.ForgeHooks.getBurnTime(stack, RecipeType.SMELTING);
  }
}
