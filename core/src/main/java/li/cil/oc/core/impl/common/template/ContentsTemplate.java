package li.cil.oc.core.impl.common.template;

import java.util.ArrayList;
import java.util.List;
import li.cil.oc.api.Items;
import li.cil.oc.core.Constants;
import li.cil.oc.core.impl.OCSettings;
import li.cil.oc.core.impl.common.Registrar;
import li.cil.oc.core.impl.common.item.data.RaidData;
import li.cil.oc.core.impl.integration.opencomputers.Item;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * Items with a recipe that keep others in their data: what they hold comes out with their parts, as a server's does
 * (matched by their recipe whatever their data, a raid's drives, a mountable's floppy and a generator upgrade's fuel
 * were destroyed with them, silently when a hopper or a robot fed them in). A tank upgrade holding fluid is refused:
 * there is no item to give that back as.
 */
public final class ContentsTemplate {
  private ContentsTemplate() {
  }

  @SuppressWarnings("unused")
  public static boolean selectDisassembler(ItemStack stack) {
    var descriptor = Items.get(stack);
    return descriptor != null && (descriptor == Items.get(Constants.BlockName.Raid)
      || descriptor == Items.get(Constants.ItemName.DiskDriveMountable)
      || descriptor == Items.get(Constants.ItemName.GeneratorUpgrade)
      || descriptor == Items.get(Constants.ItemName.TankUpgrade));
  }

  @SuppressWarnings("unused")
  public static Object[] disassemble(ItemStack stack, ItemStack[] ingredients) {
    var descriptor = Items.get(stack);
    var data = Item.peekDataTag(stack);
    List<ItemStack> held = new ArrayList<>();
    if (descriptor == Items.get(Constants.BlockName.Raid)) {
      held.addAll(new RaidData(stack).disks);
    } else if (descriptor == Items.get(Constants.ItemName.DiskDriveMountable)) {
      var items = data.getList(OCSettings.namespace + "items", Tag.TAG_COMPOUND); // (as Inventory saves them)
      for (int i = 0; i < items.size(); i++) {
        var entry = items.getCompound(i);
        held.add(ItemStack.of(entry.contains("slot") ? entry.getCompound("item") : entry));
      }
    } else if (descriptor == Items.get(Constants.ItemName.GeneratorUpgrade)) {
      if (data.contains("inventory")) held.add(ItemStack.of(data.getCompound("inventory")));
    } else if (holdsFluid(data)) {
      return new Object[]{new ItemStack[0], null}; // (no parts: not taken)
    }
    held.removeIf(s -> s == null || s.isEmpty());
    return new Object[]{ingredients, held.toArray(new ItemStack[0])};
  }

  // as UpgradeTankBase saves it, or the old way
  private static boolean holdsFluid(CompoundTag data) {
    var fluid = data.contains("fluid") ? data.getCompound("fluid") : data;
    return fluid.getInt("Amount") > 0;
  }

  public static void register() {
    Registrar.registerDisassemblerTemplate("Contents",
      "li.cil.oc.core.impl.common.template.ContentsTemplate.selectDisassembler",
      "li.cil.oc.core.impl.common.template.ContentsTemplate.disassemble");
  }
}
