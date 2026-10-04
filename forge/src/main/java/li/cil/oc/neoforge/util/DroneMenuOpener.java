package li.cil.oc.neoforge.util;

import li.cil.oc.core.impl.common.entity.Drone;
import li.cil.oc.core.impl.util.DroneMenuDelegate;
import li.cil.oc.neoforge.common.init.Menus;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

public class DroneMenuOpener implements DroneMenuDelegate {
  public static final DroneMenuOpener INSTANCE = new DroneMenuOpener();

  private DroneMenuOpener() {
  }

  @Override
  public void openMenu(Player player, Object droneObj) {
    if (!(droneObj instanceof Drone drone)) return;
    if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return;
    net.minecraftforge.network.NetworkHooks.openScreen(serverPlayer, new net.minecraft.world.MenuProvider() {
      @Override
      public net.minecraft.network.chat.@NotNull Component getDisplayName() {
        return net.minecraft.network.chat.Component.translatable("container.opencomputers.drone");
      }

      @Override
      public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.@NotNull Inventory inv, @NotNull Player player) {
        return new li.cil.oc.core.impl.common.container.Drone(Menus.DRONE.get(), id, inv, drone);
      }
    }, (net.minecraft.network.FriendlyByteBuf buf) -> {
      buf.writeInt(li.cil.oc.core.common.GuiType.Drone);
      buf.writeInt(drone.getId());
      buf.writeInt(0);
      buf.writeInt(0);
      buf.writeUtf("");
    });
  }
}
