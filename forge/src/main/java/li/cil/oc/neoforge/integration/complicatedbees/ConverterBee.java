package li.cil.oc.neoforge.integration.complicatedbees;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import li.cil.oc.api.driver.Converter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Complicated Bees bees (princess, drone, queen) as Lua sees them: a {@code bee} table with the type, whether the bee
 * has been analyzed and its species (which its name shows anyway). Once analyzed, like in the mod's analyzer, also
 * both chromosomes - {@code active}, the one the bee expresses (the mod reads every trait from it), and
 * {@code inactive} - with each gene's dominance, and for a queen the genome of the drone she mated with.
 * <p>
 * Everything comes from the item's NBT as the mod writes it (3.4.6): {@code species}, {@code analyzed},
 * {@code chromosome_a}/{@code chromosome_b} holding {@code <gene id> -> {data, dominant[, tolerance]}}, and a queen's
 * {@code mate}. Nothing of the mod is linked against.
 */
public final class ConverterBee implements Converter {
  private static final String MOD = "complicated_bees";
  private static final Set<String> TYPES = Set.of("princess", "drone", "queen");

  private final Function<ItemStack, String> beeType;

  public ConverterBee() {
    this(ConverterBee::complicatedBeesType);
  }

  /**
   * @param beeType the bee type of a stack ("princess", "drone", "queen"), or null if it is no bee.
   */
  public ConverterBee(final Function<ItemStack, String> beeType) {
    this.beeType = beeType;
  }

  private static @Nullable String complicatedBeesType(final ItemStack stack) {
    final ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
    return MOD.equals(id.getNamespace()) && TYPES.contains(id.getPath()) ? id.getPath() : null;
  }

  @Override
  public void convert(final Object value, final Map<Object, Object> output) {
    if (value instanceof ItemStack stack && stack.getTag() != null) {
      final String type = beeType.apply(stack);
      if (type != null) {
        final Map<String, Object> bee = describe(type, stack.getTag());
        if (bee != null) output.put("bee", bee);
      }
    }
  }

  /**
   * The {@code bee} table of a bee of the type with this tag, or null if the tag holds no genome.
   */
  public static @Nullable Map<String, Object> describe(final String type, final CompoundTag tag) {
    if (!tag.contains("chromosome_a", Tag.TAG_COMPOUND)) return null;
    final Map<String, Object> bee = new HashMap<>();
    bee.put("type", type);
    final boolean analyzed = tag.getBoolean("analyzed");
    bee.put("analyzed", analyzed);
    final String species = tag.contains("species", Tag.TAG_STRING)
      ? tag.getString("species")
      : tag.getCompound("chromosome_a").getCompound(MOD + ":species").getString("data");
    if (!species.isEmpty()) bee.put("species", species);
    if (analyzed) {
      genome(tag, bee);
      if (tag.contains("mate", Tag.TAG_COMPOUND)) {
        final Map<String, Object> mate = new HashMap<>();
        genome(tag.getCompound("mate"), mate);
        bee.put("mate", mate);
      }
    }
    return bee;
  }

  private static void genome(final CompoundTag tag, final Map<String, Object> out) {
    final Map<String, Object> activeDominant = new HashMap<>(), inactiveDominant = new HashMap<>();
    out.put("active", chromosome(tag.getCompound("chromosome_a"), activeDominant));
    out.put("inactive", chromosome(tag.getCompound("chromosome_b"), inactiveDominant));
    out.put("dominant", Map.of("active", activeDominant, "inactive", inactiveDominant));
  }

  private static Map<String, Object> chromosome(final CompoundTag chromosome, final Map<String, Object> dominant) {
    final Map<String, Object> genes = new HashMap<>();
    for (final String id : chromosome.getAllKeys()) {
      final CompoundTag gene = chromosome.getCompound(id);
      if (!gene.contains("data")) continue;
      final String name = id.startsWith(MOD + ":") ? id.substring(MOD.length() + 1) : id;
      genes.put(name, value(gene.get("data")));
      // the mod reads a gene without the flag as dominant
      dominant.put(name, !gene.contains("dominant") || gene.getBoolean("dominant"));
      if (gene.contains("tolerance", Tag.TAG_STRING)) genes.put(name + "_tolerance", gene.getString("tolerance"));
    }
    return genes;
  }

  private static Object value(final Tag data) {
    if (data instanceof ByteTag b) return b.getAsByte() != 0;          // the yes/no genes
    if (data instanceof NumericTag n) return n.getAsNumber();
    if (data instanceof IntArrayTag radii && radii.size() == 2) {      // territory: radii, shown as (2r+1) sizes
      final int[] r = radii.getAsIntArray();
      return Map.of("width", 2 * r[0] + 1, "height", 2 * r[1] + 1);
    }
    return data.getAsString();
  }
}
