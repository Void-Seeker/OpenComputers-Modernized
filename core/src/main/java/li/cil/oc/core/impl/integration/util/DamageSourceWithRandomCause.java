package li.cil.oc.core.impl.integration.util;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

public class DamageSourceWithRandomCause extends DamageSource {
  private final String name;
  private final int numCauses;

  public DamageSourceWithRandomCause(Holder<DamageType> type, String name, int numCauses) {
    super(type);
    this.name = name;
    this.numCauses = numCauses;
  }

  /**
   * A source of the registered damage type (data/opencomputers/damage_type): an unregistered one (Holder.direct, as
   * this was) has no id, and the damage event sent to the client carried -1, which the client cannot look up.
   */
  public static DamageSource of(net.minecraft.world.level.Level level, String type, String name, int numCauses) {
    var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE, new net.minecraft.resources.ResourceLocation("opencomputers", type));
    var holder = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolder(key);
    return new DamageSourceWithRandomCause(holder.<Holder<DamageType>>map(h -> h).orElseGet(() -> level.damageSources().magic().typeHolder()), name, numCauses);
  }

  public static DamageSource hungry(net.minecraft.world.level.Level level) {
    return of(level, "nanomachines_hungry", "oc.nanomachinesHungry", 3);
  }

  public static DamageSource overload(net.minecraft.world.level.Level level) {
    return of(level, "nanomachines_overload", "oc.nanomachinesOverload", 3);
  }

  @Override
  public boolean is(@NotNull TagKey<DamageType> tag) {
    if (tag == DamageTypeTags.BYPASSES_ARMOR ||
      tag == DamageTypeTags.BYPASSES_EFFECTS ||
      tag == DamageTypeTags.BYPASSES_RESISTANCE ||
      tag == DamageTypeTags.BYPASSES_ENCHANTMENTS) {
      return true;
    }
    return super.is(tag);
  }

  @Override
  public @NotNull Component getLocalizedDeathMessage(LivingEntity damagee) {
    LivingEntity damager = damagee.getKillCredit();
    String format = "death.attack." + name + "." + (damagee.level().random.nextInt(numCauses) + 1);
    String withCauseFormat = format + ".player";
    if (damager != null && net.minecraft.locale.Language.getInstance().has(withCauseFormat)) {
      return Component.translatable(withCauseFormat, damagee.getDisplayName(), damager.getDisplayName());
    } else {
      return Component.translatable(format, damagee.getDisplayName());
    }
  }
}
