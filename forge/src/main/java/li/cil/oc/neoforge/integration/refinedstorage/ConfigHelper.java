package li.cil.oc.neoforge.integration.refinedstorage;

import com.refinedmods.refinedstorage.apiimpl.network.node.NetworkNode;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.network.Node;
import li.cil.oc.core.impl.util.DatabaseAccess;
import li.cil.oc.core.util.ResultWrapper;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

/**
 * Refined Storage 1.x keeps importer/exporter/interface filters in plain item handlers.
 */
public final class ConfigHelper {
  private ConfigHelper() {
  }

  private static int slot(Arguments args, int arg) {
    return Math.max(0, args.optInteger(arg, 1) - 1);
  }

  public static Object[] getFilter(IItemHandler filters, Arguments args) {
    int slot = slot(args, 0);
    if (filters == null || slot >= filters.getSlots()) return ResultWrapper.result(ItemStack.EMPTY);
    return ResultWrapper.result(filters.getStackInSlot(slot).copy());
  }

  public static Object[] setFilter(NetworkNode rsNode, IItemHandler filters, Arguments args, Node node) {
    int slot;
    int valOffset;
    if (args.isInteger(0)) {
      slot = args.checkInteger(0) - 1;
      valOffset = 1;
    } else {
      slot = 0;
      valOffset = 0;
    }
    if (!(filters instanceof IItemHandlerModifiable modifiable) || slot < 0 || slot >= filters.getSlots()) {
      throw new IllegalArgumentException("invalid slot");
    }
    ItemStack stack = args.count() > 1 ? DatabaseAccess.getStackFromDatabase(node, args, valOffset) : ItemStack.EMPTY;
    modifiable.setStackInSlot(slot, stack != null ? stack.copy() : ItemStack.EMPTY);
    rsNode.markDirty();
    return ResultWrapper.result(true);
  }
}
