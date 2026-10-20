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
import net.minecraft.world.level.block.entity.BlockEntityType;
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
  public interface BlockEntityProvider<T, BE extends BlockEntity> {
    @Nullable T getCapability(BE blockEntity, @Nullable Direction side);
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
  private final Map<BlockEntityType<?>, List<BlockRegistration<?>>> blockEntityTypeRegistrations = new ConcurrentHashMap<>();
  private final Map<Item, List<ItemRegistration<?>>> itemRegistrations = new ConcurrentHashMap<>();

  private CapabilityRegistrar() {
  }

  public <T> void registerBlock(final BlockCapability<T, @Nullable Direction> capability, final BlockProvider<T> provider, final Block... blocks) {
    final BlockRegistration<T> registration = new BlockRegistration<>(capability.capability(), provider);
    for (final Block block : blocks) {
      blockRegistrations.computeIfAbsent(block, b -> new CopyOnWriteArrayList<>()).add(registration);
    }
  }

  /**
   * Registers a provider for all block entities of a type, like NeoForge's {@code registerBlockEntity}.
   */
  @SuppressWarnings("unchecked")
  public <T, BE extends BlockEntity> void registerBlockEntity(final BlockCapability<T, @Nullable Direction> capability, final BlockEntityType<BE> type, final BlockEntityProvider<T, ? super BE> provider) {
    final BlockRegistration<T> registration = new BlockRegistration<>(capability.capability(),
      (level, pos, state, blockEntity, side) -> blockEntity != null && blockEntity.getType() == type ? ((BlockEntityProvider<T, BlockEntity>) provider).getCapability(blockEntity, side) : null);
    blockEntityTypeRegistrations.computeIfAbsent(type, t -> new CopyOnWriteArrayList<>()).add(registration);
  }

  public <T> void registerItem(final ItemCapability<T, @Nullable Void> capability, final ItemProvider<T> provider, final ItemLike... items) {
    final ItemRegistration<T> registration = new ItemRegistration<>(capability.capability(), provider);
    for (final ItemLike item : items) {
      itemRegistrations.computeIfAbsent(item.asItem(), i -> new CopyOnWriteArrayList<>()).add(registration);
    }
  }

  public void onAttachBlockEntity(final AttachCapabilitiesEvent<BlockEntity> event) {
    final BlockEntity blockEntity = event.getObject();
    final List<BlockRegistration<?>> byBlock = blockRegistrations.get(blockEntity.getBlockState().getBlock());
    final List<BlockRegistration<?>> byType = blockEntityTypeRegistrations.get(blockEntity.getType());
    if (byBlock != null || byType != null) {
      final List<BlockRegistration<?>> registrations = new java.util.ArrayList<>();
      if (byBlock != null) registrations.addAll(byBlock);
      if (byType != null) registrations.addAll(byType);
      final BlockEntityCapabilityProvider provider = new BlockEntityCapabilityProvider(blockEntity, registrations);
      event.addCapability(PROVIDER_ID, provider);
      event.addListener(provider::invalidate); // (Forge calls it when the block entity is removed or its chunk unloads)
    }
  }

  public void onAttachItemStack(final AttachCapabilitiesEvent<ItemStack> event) {
    final List<ItemRegistration<?>> registrations = itemRegistrations.get(event.getObject().getItem());
    if (registrations != null) {
      event.addCapability(PROVIDER_ID, new ItemStackProvider(event.getObject(), registrations));
    }
  }

  /**
   * Hands out one optional per capability and side, invalidated when the block entity goes (a new one per lookup was
   * never invalidated: pipes and Create funnels that keep theirs went on putting items and energy into a removed or
   * unloaded block, and the items were lost). The providers are still asked on every lookup; one that no longer gives
   * the capability (a side turned to the front) has its optional invalidated. A different answer for the same side is
   * not: the answers are wrappers around the block entity and its side, equal in use.
   */
  private static final class BlockEntityCapabilityProvider implements ICapabilityProvider {
    private final BlockEntity blockEntity;
    private final List<BlockRegistration<?>> registrations;
    private final Map<Capability<?>, LazyOptional<?>[]> handedOut = new java.util.HashMap<>(); // by side, null last

    BlockEntityCapabilityProvider(final BlockEntity blockEntity, final List<BlockRegistration<?>> registrations) {
      this.blockEntity = blockEntity;
      this.registrations = registrations;
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(final @NotNull Capability<T> capability, final @Nullable Direction side) {
      final Level level = blockEntity.getLevel();
      if (level == null) return LazyOptional.empty();
      Object value = null;
      for (final BlockRegistration<?> registration : registrations) {
        if (registration.capability() == capability) {
          value = registration.provider().getCapability(level, blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity, side);
          if (value != null) break;
        }
      }
      final int index = side == null ? Direction.values().length : side.ordinal();
      final LazyOptional<?> invalid;
      synchronized (this) {
        final LazyOptional<?>[] bySide = handedOut.get(capability);
        final LazyOptional<?> known = bySide != null ? bySide[index] : null;
        if (value != null) {
          if (known != null && known.isPresent()) return known.cast();
          final Object given = value;
          final LazyOptional<?> optional = LazyOptional.of(() -> given);
          handedOut.computeIfAbsent(capability, c -> new LazyOptional<?>[Direction.values().length + 1])[index] = optional;
          return optional.cast();
        }
        if (known == null) return LazyOptional.empty();
        bySide[index] = null;
        invalid = known;
      }
      invalid.invalidate(); // (outside the lock: its listeners may look again)
      return LazyOptional.empty();
    }

    void invalidate() {
      final List<LazyOptional<?>> given = new java.util.ArrayList<>();
      synchronized (this) {
        for (final LazyOptional<?>[] bySide : handedOut.values()) {
          for (final LazyOptional<?> optional : bySide) if (optional != null) given.add(optional);
        }
        handedOut.clear();
      }
      given.forEach(LazyOptional::invalidate);
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
