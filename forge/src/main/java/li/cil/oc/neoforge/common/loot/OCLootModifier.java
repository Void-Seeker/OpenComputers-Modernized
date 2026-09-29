package li.cil.oc.neoforge.common.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import li.cil.oc.core.impl.OCSettings;
import li.cil.oc.core.impl.common.LootManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import org.jetbrains.annotations.NotNull;

public class OCLootModifier extends LootModifier {
  @SuppressWarnings("unused")
  public OCLootModifier(LootItemCondition[] conditions) {
    super(conditions);
  }

  @Override
  protected @NotNull ObjectArrayList<ItemStack> doApply(@NotNull ObjectArrayList<ItemStack> generatedLoot, @NotNull LootContext context) {
    if (!LootManager.disksForSampling.isEmpty() && context.getRandom().nextInt(100) < OCSettings.get().lootProbability) {
      var disk = LootManager.disksForSampling.get(context.getRandom().nextInt(LootManager.disksForSampling.size()));
      generatedLoot.add(disk.copy());
    }
    return generatedLoot;
  }

  @Override
  public @NotNull Codec<? extends IGlobalLootModifier> codec() {
    return CODEC;
  }

  public static final Codec<OCLootModifier> CODEC = RecordCodecBuilder.create(instance ->
    codecStart(instance).apply(instance, OCLootModifier::new));
}
