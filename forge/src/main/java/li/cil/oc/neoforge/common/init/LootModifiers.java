package li.cil.oc.neoforge.common.init;

import com.mojang.serialization.Codec;
import li.cil.oc.neoforge.OpenComputers;
import li.cil.oc.neoforge.common.loot.OCLootModifier;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public final class LootModifiers {
  public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> GLM_CODECS =
    DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, OpenComputers.ID);

  @SuppressWarnings("unused")
  public static final RegistryObject<Codec<OCLootModifier>> OC_LOOT =
    GLM_CODECS.register("oc_loot", () -> OCLootModifier.CODEC);

  private LootModifiers() {
  }
}
