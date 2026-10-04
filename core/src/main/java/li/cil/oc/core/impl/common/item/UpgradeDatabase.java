package li.cil.oc.core.impl.common.item;

import java.util.List;
import li.cil.oc.core.common.GuiType;
import li.cil.oc.core.common.item.traits.ItemTier;
import li.cil.oc.core.impl.OCSettings;
import li.cil.oc.core.impl.util.ContainerProviderDelegate;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import li.cil.oc.compat.CustomData;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class UpgradeDatabase extends DelegateItem implements ItemTier {

  private final int tier;

  public UpgradeDatabase(Properties properties, int tier) {
    super(properties);
    this.tier = tier;
  }

  public int tier() {
    return tier;
  }

  @Override
  protected List<Object> tooltipData() {
    return List.of(OCSettings.get().databaseEntriesPerTier[tier]);
  }

  @Override
  public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level world, Player player, @NotNull InteractionHand hand) {
    ItemStack stack = player.getItemInHand(hand);
    if (!player.isShiftKeyDown()) {
      if (!world.isClientSide) {
        ContainerProviderDelegate.get().openMenu(player, GuiType.Database, world, 0, 0, 0);
      }
      player.swing(hand);
    } else {
      CustomData cd = CustomData.get(stack);
      if (cd != null && !cd.isEmpty() && (cd.copyTag().contains(OCSettings.namespace + "items") || cd.copyTag().getCompound(OCSettings.namespace + "data").contains(OCSettings.namespace + "items"))) {
        CustomData.set(stack, CustomData.EMPTY);
        player.swing(hand);
      }
    }
    return InteractionResultHolder.success(stack);
  }
}
