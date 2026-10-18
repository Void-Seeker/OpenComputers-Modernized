package li.cil.oc.neoforge.util;

import li.cil.oc.core.impl.util.InventoryAccess;
import li.cil.oc.neoforge.compat.Capabilities;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * A block entity's Forge item capability as OC's {@link InventoryAccess}: every move goes through the handler, so it
 * keeps its own rules (e.g. a machine that only lets spent fuel out of its fuel slot).
 */
public record ItemHandlerAccess(IItemHandler handler, @Nullable BlockEntity blockEntity) implements InventoryAccess {
  /** An item's item handler (a backpack): it saves itself when changed through it. */
  public static @Nullable InventoryAccess lookupItem(final ItemStack stack) {
    final IItemHandler handler = Capabilities.ItemHandler.ITEM.getCapability(stack);
    return handler != null ? new ItemHandlerAccess(handler, null) : null;
  }

  public static @Nullable InventoryAccess lookup(final BlockEntity blockEntity, final @Nullable Direction face) {
    final IItemHandler handler = Capabilities.ItemHandler.BLOCK.getCapability(blockEntity, face);
    return handler != null ? new ItemHandlerAccess(handler, blockEntity) : null;
  }

  @Override
  public int getSlots() {
    return handler.getSlots();
  }

  @Override
  public ItemStack getStackInSlot(final int slot) {
    return handler.getStackInSlot(slot);
  }

  @Override
  public ItemStack insertItem(final int slot, final ItemStack stack, final boolean simulate) {
    return handler.insertItem(slot, stack, simulate);
  }

  @Override
  public ItemStack extractItem(final int slot, final int amount, final boolean simulate) {
    return handler.extractItem(slot, amount, simulate);
  }

  @Override
  public int getSlotLimit(final int slot) {
    return handler.getSlotLimit(slot);
  }

  @Override
  public boolean stillValid(final Player player) {
    return blockEntity == null || !blockEntity.isRemoved();
  }

  @Override
  public void setChanged() {
    if (blockEntity != null) blockEntity.setChanged();
  }

  @Override
  public String toString() {
    return blockEntity == null ? handler.getClass().getName() + " of an item"
      : handler.getClass().getName() + " of " + blockEntity.getType() + " at " + blockEntity.getBlockPos();
  }
}
