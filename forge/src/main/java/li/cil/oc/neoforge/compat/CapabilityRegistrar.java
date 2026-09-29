package li.cil.oc.neoforge.compat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import li.cil.oc.core.Tags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Stand-in for NeoForge's {@code RegisterCapabilitiesEvent}: collects block and item capability
 * providers in NeoForge style and exposes them through Forge's {@link AttachCapabilitiesEvent}.
 * <br>
 * Providers are queried on every lookup, like NeoForge does, so they may return different
 * values over time (e.g. depending on side configuration).
 */
public final class CapabilityRegistrar {
  public static final CapabilityRegistrar INSTANCE = new CapabilityRegistrar();

  private static final ResourceLocation PROVIDER_ID = new ResourceLocation(Tags.MOD_ID, "capabilities");

  @FunctionalInterface
  public interface BlockProvider<T> {
    @Nullable T getCapability(Level level, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, @Nullable Direction side);
  }

  @FunctionalInterface
  public interface ItemProvider<T> {
    @Nullable T getCapability(ItemStack stack, @Nullable Void context);
  }

  private record BlockRegistration<T>(Capability<T> capability, BlockProvider<T> provider) {
  }

  private record ItemRegistration<T>(Capability<T> capability, ItemProvider<T> provider) {
  }

  // Concurrent: registration runs during parallel mod setup, while other mods may already create stacks.
  private final Map<Block, List<BlockRegistration<?>>> blockRegistrations = new ConcurrentHashMap<>();
  private final Map<Item, List<ItemRegistration<?>>> itemRegistrations = new ConcurrentHashMap<>();

  private CapabilityRegistrar() {
  }

  public <T> void registerBlock(final BlockCapability<T, @Nullable Direction> capability, final BlockProvider<T> provider, final Block... blocks) {
    final BlockRegistration<T> registration = new BlockRegistration<>(capability.capability(), provider);
    for (final Block block : blocks) {
      blockRegistrations.computeIfAbsent(block, b -> new CopyOnWriteArrayList<>()).add(registration);
    }
  }

  public <T> void registerItem(final ItemCapability<T, @Nullable Void> capability, final ItemProvider<T> provider, final ItemLike... items) {
    final ItemRegistration<T> registration = new ItemRegistration<>(capability.capability(), provider);
    for (final ItemLike item : items) {
      itemRegistrations.computeIfAbsent(item.asItem(), i -> new CopyOnWriteArrayList<>()).add(registration);
    }
  }

  public void onAttachBlockEntity(final AttachCapabilitiesEvent<BlockEntity> event) {
    final BlockEntity blockEntity = event.getObject();
    final List<BlockRegistration<?>> registrations = blockRegistrations.get(blockEntity.getBlockState().getBlock());
    if (registrations != null) {
      event.addCapability(PROVIDER_ID, new BlockEntityProvider(blockEntity, registrations));
    }
  }

  public void onAttachItemStack(final AttachCapabilitiesEvent<ItemStack> event) {
    final List<ItemRegistration<?>> registrations = itemRegistrations.get(event.getObject().getItem());
    if (registrations != null) {
      event.addCapability(PROVIDER_ID, new ItemStackProvider(event.getObject(), registrations));
    }
  }

  private record BlockEntityProvider(BlockEntity blockEntity, List<BlockRegistration<?>> registrations) implements ICapabilityProvider {
    @Override
    public <T> @NotNull LazyOptional<T> getCapability(final @NotNull Capability<T> capability, final @Nullable Direction side) {
      final Level level = blockEntity.getLevel();
      if (level == null) return LazyOptional.empty();
      for (final BlockRegistration<?> registration : registrations) {
        if (registration.capability() == capability) {
          final Object value = registration.provider().getCapability(level, blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity, side);
          if (value != null) return LazyOptional.of(() -> value).cast();
        }
      }
      return LazyOptional.empty();
    }
  }

  private record ItemStackProvider(ItemStack stack, List<ItemRegistration<?>> registrations) implements ICapabilityProvider {
    @Override
    public <T> @NotNull LazyOptional<T> getCapability(final @NotNull Capability<T> capability, final @Nullable Direction side) {
      for (final ItemRegistration<?> registration : registrations) {
        if (registration.capability() == capability) {
          final Object value = registration.provider().getCapability(stack, null);
          if (value != null) return LazyOptional.of(() -> value).cast();
        }
      }
      return LazyOptional.empty();
    }
  }
}
