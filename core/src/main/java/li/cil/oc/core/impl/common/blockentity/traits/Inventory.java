package li.cil.oc.core.impl.common.blockentity.traits;


public interface Inventory extends li.cil.oc.core.impl.common.inventory.Inventory {
  /** Menus close when the player walks away or the block is gone, like vanilla containers. */
  @Override
  default boolean stillValid(net.minecraft.world.entity.player.@org.jetbrains.annotations.NotNull Player player) {
    return this instanceof net.minecraft.world.level.block.entity.BlockEntity be && net.minecraft.world.Container.stillValidBlockEntity(be, player);
  }

  @SuppressWarnings("unused")
  void readFromNBTForServer(net.minecraft.nbt.CompoundTag nbt);

  @SuppressWarnings("unused")
  void writeToNBTForServer(net.minecraft.nbt.CompoundTag nbt);

  @SuppressWarnings("unused")
  boolean isUseableByPlayer(net.minecraft.world.entity.player.Player player);

  @SuppressWarnings("unused")
  void dropSlot(int slot);

  @SuppressWarnings("unused")
  void dropSlot(int slot, int count, net.minecraft.core.Direction direction);

  void dropAllSlots();

  @SuppressWarnings("unused")
  void spawnStackInWorld(net.minecraft.world.item.ItemStack stack);

  @SuppressWarnings("unused")
  void spawnStackInWorld(net.minecraft.world.item.ItemStack stack, net.minecraft.core.Direction direction);

  @SuppressWarnings("unused")
  int x();

  @SuppressWarnings("unused")
  int y();

  @SuppressWarnings("unused")
  int z();

  @SuppressWarnings("unused")
  net.minecraft.world.level.Level getLevel();
}
