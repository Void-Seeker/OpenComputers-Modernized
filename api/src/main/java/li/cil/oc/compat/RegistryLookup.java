package li.cil.oc.compat;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.function.Supplier;

/**
 * 1.20.1 backport glue.
 * <br>
 * Minecraft 1.21 passes a {@link HolderLookup.Provider} into every NBT load/save
 * method. 1.20.1 does not, so callbacks that are driven by vanilla (block entity
 * load/save, update tags, ...) fetch one from here and hand it down to OC's own
 * load/save methods, which keep their 1.21 signatures.
 */
public final class RegistryLookup {
  private static final HolderLookup.Provider BUILTIN = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);

  private static Supplier<HolderLookup.Provider> current = () -> null;

  private RegistryLookup() {
  }

  /**
   * Set by the platform module to return the registry access of the running server, if any.
   */
  public static void setCurrent(final Supplier<HolderLookup.Provider> supplier) {
    current = supplier;
  }

  public static HolderLookup.Provider get() {
    final HolderLookup.Provider provider = current.get();
    return provider != null ? provider : BUILTIN;
  }
}
