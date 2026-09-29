package li.cil.oc.neoforge.integration.jade;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import li.cil.oc.api.internal.Agent;
import li.cil.oc.core.impl.common.blockentity.Case;
import li.cil.oc.core.impl.common.blockentity.Microcontroller;
import li.cil.oc.core.impl.common.blockentity.RobotBase;
import li.cil.oc.neoforge.common.blockentity.RobotProxy;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import snownee.jade.api.Accessor;
import snownee.jade.api.view.ClientViewGroup;
import snownee.jade.api.view.IClientExtensionProvider;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ItemView;
import snownee.jade.api.view.ViewGroup;

@SuppressWarnings("unused")
public enum OCItemSuppressionProvider implements IServerExtensionProvider<Object, ItemStack>, IClientExtensionProvider<ItemStack, ItemView> {
  INSTANCE;

  private static final ResourceLocation UID = new ResourceLocation("opencomputers:item_storage");

  @Override
  public ResourceLocation getUid() {
    return UID;
  }

  @Nullable
  @Override
  public List<ViewGroup<ItemStack>> getGroups(ServerPlayer player, ServerLevel level, Object target, boolean showDetails) {
    if (target instanceof Case || target instanceof Microcontroller) {
      return List.of();
    }
    if (target instanceof RobotBase robot) {
      return buildFilteredContainerView(robot, robot.componentSlots());
    }
    if (target instanceof RobotProxy proxy) {
      return buildFilteredContainerView(proxy, proxy.robot.componentSlots());
    }
    if (target instanceof Agent agent) {
      return buildAgentItemView(agent);
    }
    return null;
  }

  @Override
  public List<ClientViewGroup<ItemView>> getClientGroups(Accessor<?> accessor, List<ViewGroup<ItemStack>> groups) {
    return ClientViewGroup.map(groups, ItemView::new, null);
  }

  private List<ViewGroup<ItemStack>> buildFilteredContainerView(Container container, Set<Integer> excludedSlots) {
    List<ItemStack> items = new ArrayList<>();
    for (int i = 0; i < container.getContainerSize(); i++) {
      if (excludedSlots.contains(i)) continue;
      ItemStack stack = container.getItem(i);
      if (!stack.isEmpty()) {
        boolean merged = false;
        for (ItemStack existing : items) {
          if (ItemStack.isSameItemSameTags(existing, stack)) {
            existing.grow(stack.getCount());
            merged = true;
            break;
          }
        }
        if (!merged) {
          items.add(stack.copy());
        }
      }
    }
    return List.of(new ViewGroup<>(items));
  }

  private List<ViewGroup<ItemStack>> buildAgentItemView(Agent agent) {
    List<ItemStack> items = new ArrayList<>();
    addItems(items, agent.equipmentInventory());
    addItems(items, agent.mainInventory());
    return List.of(new ViewGroup<>(items));
  }

  private void addItems(List<ItemStack> items, Container container) {
    for (int i = 0; i < container.getContainerSize(); i++) {
      ItemStack stack = container.getItem(i);
      if (!stack.isEmpty()) {
        boolean merged = false;
        for (ItemStack existing : items) {
          if (ItemStack.isSameItemSameTags(existing, stack)) {
            existing.grow(stack.getCount());
            merged = true;
            break;
          }
        }
        if (!merged) {
          items.add(stack.copy());
        }
      }
    }
  }

  @Override
  public int getDefaultPriority() {
    return 100;
  }
}
