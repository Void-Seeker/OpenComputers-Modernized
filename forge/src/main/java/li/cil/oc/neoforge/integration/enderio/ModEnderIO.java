package li.cil.oc.neoforge.integration.enderio;

import com.enderio.api.misc.ColorControl;
import com.enderio.conduits.common.conduit.block.ConduitBlockEntity;
import com.enderio.conduits.common.conduit.connection.DynamicConnectionState;
import com.enderio.conduits.common.init.EIOConduitTypes;
import li.cil.oc.core.impl.integration.util.BundledRedstone;
import li.cil.oc.core.impl.util.BlockPosition;
import li.cil.oc.neoforge.integration.ModProxy;
import li.cil.oc.neoforge.integration.Mods;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BlockEntity;

@SuppressWarnings("unused")
public final class ModEnderIO implements ModProxy, BundledRedstone.RedstoneProvider {
  @Override
  public Mods.ModBase getMod() {
    return Mods.EnderIO;
  }

  @Override
  public void initialize() {
    BundledRedstone.addProvider(this);

    li.cil.oc.core.impl.common.Registrar.registerWrenchTool("li.cil.oc.neoforge.integration.enderio.EventHandlerEnderIO.useWrench");
    li.cil.oc.core.impl.common.Registrar.registerWrenchToolCheck("li.cil.oc.neoforge.integration.enderio.EventHandlerEnderIO.isWrench");
  }

  @Override
  public int computeInput(BlockPosition pos, Direction side) {
    return 0;
  }

  @Override
  @SuppressWarnings("UnstableApiUsage")
  public int[] computeBundledInput(BlockPosition pos, Direction side) {
    var level = pos.level();
    if (level == null) return null;
    var conduitPos = pos.offset(side).toBlockPos();
    BlockEntity be = level.getBlockEntity(conduitPos);
    if (!(be instanceof ConduitBlockEntity conduit)) return null;

    // EnderIO 6.2 (1.20.1): redstone conduit sides that insert into our block carry the network's channels.
    var redstoneConduit = EIOConduitTypes.REDSTONE.get();
    if (!conduit.getBundle().hasType(redstoneConduit)) return null;

    var conduitSide = side.getOpposite();
    if (!(conduit.getBundle().getConnectionState(conduitSide, redstoneConduit) instanceof DynamicConnectionState state) || !state.isInsert()) {
      return null;
    }

    var node = conduit.getBundle().getNodeFor(redstoneConduit);
    if (node == null) return null;
    var data = node.getConduitData();

    int[] result = new int[16];
    for (DyeColor color : DyeColor.values()) {
      result[color.getId()] = data.isActive(ColorControl.valueOf(color.name())) ? 255 : 0;
    }
    return result;
  }
}
