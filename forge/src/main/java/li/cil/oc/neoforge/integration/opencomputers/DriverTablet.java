package li.cil.oc.neoforge.integration.opencomputers;

import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.core.Constants;
import li.cil.oc.core.common.Slot;
import li.cil.oc.core.impl.OCSettings;
import li.cil.oc.core.impl.common.item.data.TabletData;
import li.cil.oc.core.impl.integration.opencomputers.Item;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import li.cil.oc.compat.CustomData;


@SuppressWarnings("unused")
public final class DriverTablet extends Item {
  @Override
  public boolean worksWith(ItemStack stack) {
    return isOneOf(stack, li.cil.oc.api.Items.get(Constants.ItemName.Tablet));
  }

  @Override
  public li.cil.oc.api.network.ManagedEnvironment createEnvironment(ItemStack stack, EnvironmentHost host) {
    if (host.level() != null && host.level().isClientSide()) return null;
    // as OC: the tablet's machine saves into this stack and goes, its drive with it - left running, its unsaved files
    // were missing here, and this file system's later save put back what the tablet had deleted since
    li.cil.oc.core.impl.util.TabletCache.get().invalidate(stack);
    TabletData data = new TabletData(stack);
    for (ItemStack fs : data.items) {
      if (fs != null && !fs.isEmpty()) {
        if (new DriverFileSystem().worksWith(fs)) {
          li.cil.oc.api.network.ManagedEnvironment environment = new DriverFileSystem().createEnvironment(fs, host);
          if (environment != null && environment.node() instanceof Component component) {
            component.setVisibility(Visibility.Network);
            environment.save(dataTag(stack), host.level().registryAccess());
            return environment;
          }
        }
      }
    }
    return null;
  }

  @Override
  public String slot(ItemStack stack) {
    return Slot.Tablet;
  }

  public CompoundTag dataTag(ItemStack stack) {
    TabletData data = new TabletData(stack);
    int slot = -1;
    for (int i = 0; i < data.items.size(); i++) {
      ItemStack item = data.items.get(i);
      if (item != null && !item.isEmpty()) {
        if (new DriverFileSystem().worksWith(item)) {
          slot = i;
          break;
        }
      }
    }
    // the stack's own tag, not a copy: what the HDD's file system saves here (a tablet in a charger) has to stay
    if (slot >= 0) {
      CompoundTag rootTag = stack.getTag();
      if (rootTag != null) {
        if (rootTag.contains(OCSettings.namespace + "items")) {
          net.minecraft.nbt.ListTag baseTag = rootTag.getList(OCSettings.namespace + "items", Tag.TAG_COMPOUND);
          for (int i = 0; i < baseTag.size(); i++) {
            CompoundTag entryTag = baseTag.getCompound(i);
            if (entryTag.getByte("slot") == (byte) slot) {
              if (!entryTag.contains("item")) {
                entryTag.put("item", new CompoundTag());
              }
              CompoundTag itemTag = entryTag.getCompound("item");
              if (!itemTag.contains("tag")) {
                itemTag.put("tag", new CompoundTag());
              }
              CompoundTag stackTag = itemTag.getCompound("tag");
              if (!stackTag.contains(OCSettings.namespace + "data")) {
                stackTag.put(OCSettings.namespace + "data", new CompoundTag());
              }
              return stackTag.getCompound(OCSettings.namespace + "data");
            }
          }
        }
      }
    }
    return new CompoundTag();
  }
}
