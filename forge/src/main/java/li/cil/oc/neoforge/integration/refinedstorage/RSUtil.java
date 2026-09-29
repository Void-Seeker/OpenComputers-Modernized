package li.cil.oc.neoforge.integration.refinedstorage;

import com.refinedmods.refinedstorage.RSBlocks;
import com.refinedmods.refinedstorage.api.network.INetwork;
import com.refinedmods.refinedstorage.block.ControllerBlock;
import com.refinedmods.refinedstorage.blockentity.ControllerBlockEntity;
import com.refinedmods.refinedstorage.blockentity.ExporterBlockEntity;
import com.refinedmods.refinedstorage.blockentity.ImporterBlockEntity;
import com.refinedmods.refinedstorage.blockentity.InterfaceBlockEntity;
import com.refinedmods.refinedstorage.blockentity.NetworkNodeBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

public final class RSUtil {
  private RSUtil() {
  }

  public static Class<?> controllerClass() {
    return ControllerBlockEntity.class;
  }

  public static Class<?> importerClass() {
    return ImporterBlockEntity.class;
  }

  public static Class<?> exporterClass() {
    return ExporterBlockEntity.class;
  }

  public static Class<?> interfaceClass() {
    return InterfaceBlockEntity.class;
  }

  public static boolean isController(ItemStack stack) {
    return stack != null && !stack.isEmpty() && Block.byItem(stack.getItem()) instanceof ControllerBlock;
  }

  public static boolean isImporter(ItemStack stack) {
    return stack != null && !stack.isEmpty() && stack.is(RSBlocks.IMPORTER.get().asItem());
  }

  public static boolean isExporter(ItemStack stack) {
    return stack != null && !stack.isEmpty() && stack.is(RSBlocks.EXPORTER.get().asItem());
  }

  public static boolean isInterface(ItemStack stack) {
    return stack != null && !stack.isEmpty() && stack.is(RSBlocks.INTERFACE.get().asItem());
  }

  @Nullable
  public static INetwork networkOf(@Nullable BlockEntity tile) {
    if (tile instanceof ControllerBlockEntity controller) {
      return controller.getNetwork();
    }
    if (tile instanceof NetworkNodeBlockEntity<?> node) {
      return node.getNode().getNetwork();
    }
    return null;
  }
}
