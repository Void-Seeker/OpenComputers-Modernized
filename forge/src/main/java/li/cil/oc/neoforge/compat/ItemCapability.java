package li.cil.oc.neoforge.compat;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import org.jetbrains.annotations.Nullable;

/**
 * Stand-in for NeoForge's {@code ItemCapability}: an item stack view of a Forge {@link Capability}.
 *
 * @param <T> the capability type.
 * @param <C> the context type, always {@link Void} here.
 */
public final class ItemCapability<T, C> {
  private final Capability<T> capability;

  private ItemCapability(final Capability<T> capability) {
    this.capability = capability;
  }

  public static <T> ItemCapability<T, @Nullable Void> of(final Capability<T> capability) {
    return new ItemCapability<>(capability);
  }

  public Capability<T> capability() {
    return capability;
  }

  /**
   * Equivalent of NeoForge's {@code stack.getCapability(capability)}.
   */
  public @Nullable T getCapability(final ItemStack stack) {
    return stack.getCapability(capability).resolve().orElse(null);
  }
}
