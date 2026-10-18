package li.cil.oc.core.impl.integration.opencomputers;

import li.cil.oc.compat.CustomData;

import li.cil.oc.api.driver.DriverItem;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.core.common.Tier;
import li.cil.oc.core.impl.OCSettings;
import li.cil.oc.core.impl.server.driver.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

@SuppressWarnings("unused")
public abstract class Item implements DriverItem {
  /**
   * The item's own "oc:data" tag (made if missing): what is written into it stays (it was a copy whenever the item
   * already had a tag - a crafted linked card's tunnel was never stored, every pair shared the "creative" one).
   */
  public static CompoundTag getDataTag(ItemStack stack) {
    CompoundTag nbt = stack.getOrCreateTag();
    if (!nbt.contains(OCSettings.namespace + "data", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
      nbt.put(OCSettings.namespace + "data", new CompoundTag());
    }
    return nbt.getCompound(OCSettings.namespace + "data");
  }

  /** The data, to read: the live tag if there is one, else an empty one not put into the stack (tooltips are client). */
  public static CompoundTag peekDataTag(ItemStack stack) {
    CompoundTag nbt = stack.getTag();
    return nbt != null && nbt.contains(OCSettings.namespace + "data", net.minecraft.nbt.Tag.TAG_COMPOUND)
      ? nbt.getCompound(OCSettings.namespace + "data") : new CompoundTag();
  }

  public static String address(ItemStack stack) {
    String addressKey = "address";
    CompoundTag tag = getTag(stack, new String[]{OCSettings.namespace + "data", "node"});
    if (tag != null && tag.contains(addressKey)) {
      return tag.getString(addressKey);
    }
    return null;
  }

  private static CompoundTag getTag(CompoundTag tagCompound, String[] keys) {
    if (keys.length == 0) {
      return tagCompound;
    } else if (!tagCompound.contains(keys[0])) {
      return null;
    } else {
      String[] remaining = new String[keys.length - 1];
      System.arraycopy(keys, 1, remaining, 0, keys.length - 1);
      return getTag(tagCompound.getCompound(keys[0]), remaining);
    }
  }

  private static CompoundTag getTag(ItemStack stack, String[] keys) {
    if (stack == null || stack.getCount() == 0) {
      return null;
    }
    var customData = CustomData.get(stack);
    if (customData == null || customData.isEmpty()) {
      return null;
    }
    return getTag(customData.copyTag(), keys);
  }

  public boolean worksWith(ItemStack stack, Class<? extends EnvironmentHost> host) {
    return worksWith(stack) && !Registry.INSTANCE.isBlacklisted(stack, host);
  }

  @Override
  public CompoundTag dataTag(ItemStack stack) {
    return getDataTag(stack);
  }

  public int tier(ItemStack stack) {
    return Tier.One;
  }

  protected boolean isOneOf(ItemStack stack, li.cil.oc.api.detail.ItemInfo... items) {
    for (li.cil.oc.api.detail.ItemInfo item : items) {
      if (item != null && li.cil.oc.api.Items.get(stack) == item) {
        return true;
      }
    }
    return false;
  }

  protected boolean isAdapter(Class<? extends EnvironmentHost> host) {
    return li.cil.oc.api.internal.Adapter.class.isAssignableFrom(host);
  }

  protected boolean isComputer(Class<? extends EnvironmentHost> host) {
    return li.cil.oc.api.internal.Case.class.isAssignableFrom(host);
  }

  protected boolean isRobot(Class<? extends EnvironmentHost> host) {
    return li.cil.oc.api.internal.Robot.class.isAssignableFrom(host);
  }

  protected boolean isRotatable(Class<? extends EnvironmentHost> host) {
    return li.cil.oc.api.internal.Rotatable.class.isAssignableFrom(host);
  }

  protected boolean isServer(Class<? extends EnvironmentHost> host) {
    return li.cil.oc.api.internal.Server.class.isAssignableFrom(host);
  }

  protected boolean isTablet(Class<? extends EnvironmentHost> host) {
    return li.cil.oc.api.internal.Tablet.class.isAssignableFrom(host);
  }

  protected boolean isMicrocontroller(Class<? extends EnvironmentHost> host) {
    return li.cil.oc.api.internal.Microcontroller.class.isAssignableFrom(host);
  }

  protected boolean isDrone(Class<? extends EnvironmentHost> host) {
    return li.cil.oc.api.internal.Drone.class.isAssignableFrom(host);
  }
}
