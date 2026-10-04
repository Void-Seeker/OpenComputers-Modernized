package li.cil.oc.core.impl.common.block;

import java.util.List;
import li.cil.oc.core.impl.common.block.traits.CustomDrops;
import li.cil.oc.core.impl.common.block.traits.PowerAcceptor;
import li.cil.oc.core.impl.util.Tooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public class SimpleBlock extends AbstractBlock {

  public SimpleBlock() {
    super();
  }

  public SimpleBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties properties) {
    super(properties);
  }

  @Override
  protected void tooltipBody(int metadata, ItemStack stack, List<Component> tooltip, boolean advanced) {
    tooltip.addAll(Tooltip.get(getClass().getSimpleName()));
  }

  @Override
  public void tooltipTail(int metadata, ItemStack stack, List<Component> tooltip, boolean advanced) {
    if (this instanceof PowerAcceptor acceptor) {
      tooltip.addAll(Tooltip.extended("PowerAcceptor", (int) acceptor.energyThroughput()));
    }
  }

  @Override
  @SuppressWarnings({"rawtypes", "unchecked"})
  public void setPlacedBy(@NotNull Level world, @NotNull BlockPos pos, @NotNull BlockState state, LivingEntity placer, @NotNull ItemStack stack) {
    super.setPlacedBy(world, pos, state, placer, stack);
    if (!world.isClientSide && this instanceof CustomDrops customDrops) {
      BlockEntity te = world.getBlockEntity(pos);
      if (te != null && customDrops.getBlockClass().isInstance(te)) {
        customDrops.doCustomInit(te, placer, stack);
      }
    }
  }

  // The loot tables of CustomDrops blocks are empty: they drop through doCustomDrops. Upstream only calls it from
  // the platform SimpleBlock, which cables, prints, RAIDs and microcontrollers do not extend, so they dropped nothing.
  @Override
  @SuppressWarnings({"rawtypes", "unchecked"})
  public void playerWillDestroy(@NotNull Level world, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull Player player) {
    if (!world.isClientSide && this instanceof CustomDrops customDrops) {
      BlockEntity te = world.getBlockEntity(pos);
      if (te != null && customDrops.getBlockClass().isInstance(te)) {
        customDrops.doCustomDrops(te, player, true);
      }
    }
    super.playerWillDestroy(world, pos, state, player);
  }
}
