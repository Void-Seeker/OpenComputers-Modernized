package li.cil.oc.neoforge.integration.opencomputers;

import li.cil.oc.compat.CustomData;

import li.cil.oc.api.driver.EnvironmentProvider;
import li.cil.oc.api.driver.item.HostAware;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.core.Constants;
import li.cil.oc.core.common.Slot;
import li.cil.oc.core.common.Tier;
import li.cil.oc.core.impl.integration.opencomputers.Item;
import li.cil.oc.core.impl.util.BlockPosition;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;


@SuppressWarnings("unused")
public final class DriverUpgradeMF extends Item implements HostAware {
  @Override
  public boolean worksWith(ItemStack stack) {
    return isOneOf(stack, li.cil.oc.api.Items.get(Constants.ItemName.MFU));
  }

  @Override
  public boolean worksWith(ItemStack stack, Class<? extends EnvironmentHost> host) {
    return INSTANCE.worksWith(stack) && isAdapter(host);
  }

  @Override
  public String slot(ItemStack stack) {
    return Slot.Upgrade;
  }

  @Override
  public int tier(ItemStack stack) {
    return Tier.Three;
  }

  @Override
  public ManagedEnvironment createEnvironment(ItemStack stack, EnvironmentHost host) {
    if (host.level() != null && !host.level().isClientSide()) {
      var customData = CustomData.get(stack);
      if (customData != null && !customData.isEmpty()) {
        // written by UpgradeMF.onItemUseFirst: x, y, z, dimension hash, side
        var coord = customData.copyTag().getIntArray(li.cil.oc.core.impl.OCSettings.namespace + "coord");
        var server = host.level().getServer();
        if (coord.length >= 5 && server != null) {
          for (var level : server.getAllLevels()) {
            if (level.dimension().location().hashCode() == coord[3]) {
              return new li.cil.oc.neoforge.server.component.UpgradeMF(host, BlockPosition.apply(coord[0], coord[1], coord[2], level), Direction.from3DDataValue(coord[4]));
            }
          }
        }
      }
    }
    return null;
  }

  private static final DriverUpgradeMF INSTANCE = new DriverUpgradeMF();

  public static final class Provider implements EnvironmentProvider {
    @Override
    public Class<?> getEnvironment(ItemStack stack) {
      if (INSTANCE.worksWith(stack)) {
        return li.cil.oc.neoforge.server.component.UpgradeMF.class;
      }
      return null;
    }
  }
}
