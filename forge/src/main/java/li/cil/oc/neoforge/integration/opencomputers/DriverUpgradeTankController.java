package li.cil.oc.neoforge.integration.opencomputers;

import li.cil.oc.api.driver.EnvironmentProvider;
import li.cil.oc.api.driver.item.HostAware;
import li.cil.oc.api.internal.Adapter;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.core.Constants;
import li.cil.oc.core.common.Slot;
import li.cil.oc.core.common.Tier;
import li.cil.oc.core.impl.common.entity.Drone;
import li.cil.oc.core.impl.integration.opencomputers.Item;
import li.cil.oc.neoforge.common.blockentity.Robot;
import net.minecraft.world.item.ItemStack;

@SuppressWarnings("unused")
public final class DriverUpgradeTankController extends Item implements HostAware {
  @Override
  public boolean worksWith(ItemStack stack) {
    return isOneOf(stack, li.cil.oc.api.Items.get(Constants.ItemName.TankControllerUpgrade));
  }

  @Override
  public li.cil.oc.api.network.ManagedEnvironment createEnvironment(ItemStack stack, EnvironmentHost host) {
    if (host.level() != null && host.level().isClientSide()) return null;
    if (host instanceof Adapter adapter) {
      return new li.cil.oc.core.impl.server.component.UpgradeTankController.Adapter(host);
    } else if (host instanceof Drone drone) {
      return new li.cil.oc.core.impl.server.component.UpgradeTankController.Drone(drone);
    } else if (host instanceof Robot robot) {
      return new li.cil.oc.neoforge.server.component.UpgradeTankController.Robot(robot);
    } else {
      return null;
    }
  }

  @Override
  public String slot(ItemStack stack) {
    return Slot.Upgrade;
  }

  @Override
  public int tier(ItemStack stack) {
    return Tier.Two;
  }

  private static final DriverUpgradeTankController INSTANCE = new DriverUpgradeTankController();

  public static final class Provider implements EnvironmentProvider {
    @Override
    public Class<?> getEnvironment(ItemStack stack) {
      if (INSTANCE.worksWith(stack)) {
        return li.cil.oc.neoforge.server.component.UpgradeTankController.Robot.class;
      }
      return null;
    }
  }
}
