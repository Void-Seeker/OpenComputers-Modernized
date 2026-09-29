package li.cil.oc.neoforge.integration.jade;

import li.cil.oc.core.impl.common.block.AbstractBlock;
import li.cil.oc.core.impl.common.entity.Drone;
import li.cil.oc.neoforge.common.init.Items;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import org.jetbrains.annotations.Nullable;
import snownee.jade.addon.core.ObjectNameProvider;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.Identifiers;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.ui.IElementHelper;

@WailaPlugin
@SuppressWarnings("unused")
public class OCJadePlugin implements IWailaPlugin {
  @Override
  public void register(final IWailaCommonRegistration registration) {
    // Jade 11 (1.20.1) keys server data and item storage on the block entity class, not the block.
    registration.registerBlockDataProvider(OCDataProvider.INSTANCE, li.cil.oc.core.impl.common.blockentity.traits.BlockEntity.class);
    registration.registerItemStorage(OCItemSuppressionProvider.INSTANCE, li.cil.oc.core.impl.common.blockentity.traits.BlockEntity.class);
    registration.registerItemStorage(OCItemSuppressionProvider.INSTANCE, Drone.class);
  }

  @Override
  public void registerClient(final IWailaClientRegistration registration) {
    registration.registerBlockComponent(OCDataProvider.INSTANCE, AbstractBlock.class);
    registration.registerBlockComponent(OCBlockNameProvider.INSTANCE, AbstractBlock.class);
    registration.registerEntityIcon(DroneIconProvider.INSTANCE, Drone.class);
    registration.registerEntityComponent(OCEntityNameProvider.INSTANCE, Drone.class);
    registration.registerItemStorageClient(OCItemSuppressionProvider.INSTANCE);
  }

  /**
   * Jade 11 (1.20.1) has no {@code ITooltip#replace}; swap the core object name line for ours.
   */
  private static void replaceObjectName(final ITooltip tooltip, final Component name) {
    tooltip.remove(Identifiers.CORE_OBJECT_NAME);
    tooltip.add(0, name, Identifiers.CORE_OBJECT_NAME);
  }

  private enum OCEntityNameProvider implements IEntityComponentProvider {
    INSTANCE;

    private static final ResourceLocation UID = new ResourceLocation("opencomputers:drone_name");

    @Override
    public ResourceLocation getUid() {
      return UID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
      if (!(accessor.getEntity() instanceof Drone drone)) return;
      net.minecraft.world.item.Rarity rarity = li.cil.oc.core.impl.util.Rarity.byTier(drone.tier());
      if (rarity == Rarity.COMMON) return;
      String ocName = drone.name();
      MutableComponent name = Component.empty().append(
          ocName.isEmpty()
            ? ObjectNameProvider.getEntityName(drone)
            : Component.literal(ocName))
        .withStyle(rarity.getStyleModifier());
      replaceObjectName(tooltip, name);
    }

    @Override
    public int getDefaultPriority() {
      return ObjectNameProvider.INSTANCE.getDefaultPriority() + 10;
    }
  }

  private enum OCBlockNameProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final ResourceLocation UID = new ResourceLocation("opencomputers:block_name");

    @Override
    public ResourceLocation getUid() {
      return UID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
      ItemStack picked = accessor.getPickedResult();
      if (picked.isEmpty() || picked.getRarity() == Rarity.COMMON) return;
      MutableComponent name = Component.empty().append(picked.getHoverName()).withStyle(picked.getRarity().getStyleModifier());
      replaceObjectName(tooltip, name);
    }

    @Override
    public int getDefaultPriority() {
      return ObjectNameProvider.INSTANCE.getDefaultPriority() + 10;
    }
  }

  private enum DroneIconProvider implements IEntityComponentProvider {
    INSTANCE;

    private static final ItemStack DRONE_ICON = new ItemStack(Items.DRONE.get());
    private static final ResourceLocation UID = new ResourceLocation("opencomputers:drone_icon");

    @Override
    public ResourceLocation getUid() {
      return UID;
    }

    @Nullable
    @Override
    public IElement getIcon(EntityAccessor accessor, IPluginConfig config, IElement currentIcon) {
      return IElementHelper.get().item(DRONE_ICON);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
    }

    @Override
    public int getDefaultPriority() {
      return 100;
    }
  }
}
