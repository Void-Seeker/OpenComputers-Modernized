package li.cil.oc.neoforge.integration.mekanism;

import mekanism.common.item.ItemConfigurator;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

@SuppressWarnings("unused")
public final class EventHandlerMekanism {
  private EventHandlerMekanism() {
  }

  public static boolean useWrench(Player player, int ignoredX, int ignoredY, int ignoredZ, boolean ignoredChangeDurability) {
    ItemStack held = player.getMainHandItem();
    if (held.isEmpty()) {
      return false;
    }
    return isWrench(held);
  }

  public static boolean isWrench(ItemStack stack) {
    if (stack.isEmpty()) {
      return false;
    }
    // Mekanism 10.4 has no wrench item ability yet; the configurator acts as wrench in wrench mode.
    return stack.getItem() instanceof ItemConfigurator configurator
      && configurator.getMode(stack) == ItemConfigurator.ConfiguratorMode.WRENCH;
  }
}
