package li.cil.oc.core.impl.util;

import li.cil.oc.compat.MathCompat;

@SuppressWarnings("unused")
public final class Rarity {
  private static final net.minecraft.world.item.Rarity[] lookup = {
    net.minecraft.world.item.Rarity.COMMON,
    net.minecraft.world.item.Rarity.UNCOMMON,
    net.minecraft.world.item.Rarity.RARE,
    net.minecraft.world.item.Rarity.EPIC
  };

  public static net.minecraft.world.item.Rarity byTier(int tier) {
    return lookup[MathCompat.clamp(tier, 0, lookup.length - 1)];
  }
}
