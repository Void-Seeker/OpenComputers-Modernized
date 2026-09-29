package li.cil.oc.core.impl.common;

import li.cil.oc.compat.CustomData;

import li.cil.oc.api.detail.ItemInfo;
import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class Achievement {
  private Achievement() {
  }

  public static void onAssemble(ItemStack stack, Player player) {
    if (player instanceof ServerPlayer serverPlayer) {
      grantAssemblyAdvancement(serverPlayer, stack);
    }
  }

  public static void onCraft(ItemStack stack, Player player) {
    if (player instanceof ServerPlayer serverPlayer) {
      grantCraftAdvancement(serverPlayer, stack);
    }
  }

  private static void grantAssemblyAdvancement(ServerPlayer player, ItemStack stack) {
    ItemInfo info = li.cil.oc.api.Items.get(stack);
    if (info == null) return;
    String name = info.name();
    String advName = switch (name) {
      case "microcontroller", "robot", "drone", "tablet" -> name;
      default -> null;
    };
    if (advName != null) {
      grant(player, advName);
    }
  }

  private static void grantCraftAdvancement(ServerPlayer player, ItemStack stack) {
    if (stack.getCount() <= 0) return;
    ItemInfo info = li.cil.oc.api.Items.get(stack);
    if (info == null) return;
    String name = info.name();
    if ("floppy".equals(name)) {
      var data = CustomData.get(stack);
      if (data != null && !data.isEmpty()) {
        String factory = data.copyTag().getString("oc:lootFactory");
        if ("opencomputers:openos".equals(factory)) {
          grant(player, "openos");
        }
      }
    }
  }

  private static void grant(ServerPlayer player, String advancementName) {
    Advancement adv = player.server.getAdvancements().getAdvancement(new ResourceLocation("opencomputers", advancementName));
    if (adv != null) {
      player.getAdvancements().award(adv, "manual");
    }
  }
}
