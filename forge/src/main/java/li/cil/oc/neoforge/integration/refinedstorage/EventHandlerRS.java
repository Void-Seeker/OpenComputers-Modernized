package li.cil.oc.neoforge.integration.refinedstorage;

import com.refinedmods.refinedstorage.RSItems;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

@SuppressWarnings("unused")
public final class EventHandlerRS {
  private EventHandlerRS() {
  }

  public static boolean useWrench(Player player, int ignoredX, int ignoredY, int ignoredZ, boolean ignoredChangeDurability) {
    return isWrench(player.getMainHandItem());
  }

  public static boolean isWrench(ItemStack stack) {
    return !stack.isEmpty() && stack.is(RSItems.WRENCH.get());
  }
}
