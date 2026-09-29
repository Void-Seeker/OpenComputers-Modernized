package li.cil.oc.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.function.Consumer;

/**
 * 1.20.1 backport glue: emulates 1.21's {@code minecraft:custom_data} item component
 * on top of the single 1.20.1 item stack tag.
 * <br>
 * In 1.21 custom data lives apart from damage, display name, enchantments etc.
 * In 1.20.1 all of those share {@link ItemStack#getTag()}. To keep the 1.21 code's
 * semantics, reads hide the keys vanilla owns, and writes keep them, so code that
 * replaces the custom data wholesale cannot wipe e.g. a stack's name or damage.
 */
public final class CustomData {
  public static final CustomData EMPTY = new CustomData(new CompoundTag());

  /**
   * Top-level stack tag keys owned by vanilla/Forge in 1.20.1, which 1.21 moved into their own components.
   */
  private static final Set<String> RESERVED_KEYS = Set.of(
    "Damage", "Unbreakable", "display", "Enchantments", "StoredEnchantments", "RepairCost",
    "AttributeModifiers", "HideFlags", "CanDestroy", "CanPlaceOn", "BlockEntityTag", "BlockStateTag",
    "CustomModelData", "Trim");

  private final CompoundTag tag;

  private CustomData(final CompoundTag tag) {
    this.tag = tag;
  }

  public static CustomData of(final CompoundTag tag) {
    return new CustomData(tag.copy());
  }

  /**
   * Equivalent of 1.21's {@code stack.get(DataComponents.CUSTOM_DATA)}.
   */
  public static @Nullable CustomData get(final ItemStack stack) {
    final CompoundTag stackTag = stack.getTag();
    if (stackTag == null) return null;
    final CompoundTag custom = stackTag.copy();
    for (final String key : RESERVED_KEYS) custom.remove(key);
    return custom.isEmpty() ? null : new CustomData(custom);
  }

  /**
   * Equivalent of 1.21's {@code stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)}.
   */
  public static CustomData getOrEmpty(final ItemStack stack) {
    final CustomData data = get(stack);
    return data != null ? data : EMPTY;
  }

  /**
   * Equivalent of 1.21's {@code stack.set(DataComponents.CUSTOM_DATA, data)}.
   */
  public static void set(final ItemStack stack, final @Nullable CustomData data) {
    final CompoundTag result = data != null ? data.tag.copy() : new CompoundTag();
    for (final String key : RESERVED_KEYS) result.remove(key);
    final CompoundTag old = stack.getTag();
    if (old != null) {
      for (final String key : RESERVED_KEYS) {
        if (old.contains(key)) result.put(key, old.get(key).copy());
      }
    }
    stack.setTag(result.isEmpty() ? null : result);
  }

  /**
   * Equivalent of 1.21's {@code stack.remove(DataComponents.CUSTOM_DATA)}.
   */
  public static void remove(final ItemStack stack) {
    set(stack, null);
  }

  /**
   * Equivalent of 1.21's {@code stack.has(DataComponents.CUSTOM_DATA)}.
   */
  public static boolean has(final ItemStack stack) {
    return get(stack) != null;
  }

  /**
   * Equivalent of 1.21's {@code CustomData.update(DataComponents.CUSTOM_DATA, stack, updater)}.
   */
  public static void update(final ItemStack stack, final Consumer<CompoundTag> updater) {
    final CompoundTag tag = getOrEmpty(stack).copyTag();
    updater.accept(tag);
    set(stack, new CustomData(tag));
  }

  public CompoundTag copyTag() {
    return tag.copy();
  }

  /**
   * Direct view of the wrapped tag. Must not be modified.
   */
  public CompoundTag getUnsafe() {
    return tag;
  }

  public boolean isEmpty() {
    return tag.isEmpty();
  }

  public boolean contains(final String key) {
    return tag.contains(key);
  }

  public CustomData update(final Consumer<CompoundTag> updater) {
    final CompoundTag copy = tag.copy();
    updater.accept(copy);
    return new CustomData(copy);
  }

  @Override
  public boolean equals(final Object obj) {
    return obj == this || (obj instanceof CustomData other && tag.equals(other.tag));
  }

  @Override
  public int hashCode() {
    return tag.hashCode();
  }

  @Override
  public String toString() {
    return tag.toString();
  }
}
