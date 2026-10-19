package li.cil.oc.core.impl.integration.util;

import java.util.ArrayList;
import java.util.List;
import li.cil.oc.core.impl.util.BlockPosition;
import net.minecraft.core.Direction;

public final class BundledRedstone {
  private static final List<RedstoneProvider> providers = new ArrayList<>();

  private BundledRedstone() {
  }

  public static void addProvider(RedstoneProvider provider) {
    providers.add(provider);
  }

  public static boolean isAvailable() {
    return !providers.isEmpty();
  }

  /**
   * Whether what lies beyond that side can be read now. A chunk next to one not loaded yet (every chunk passes the
   * border on its way in) read 0 there: the saved input changed to 0, a spurious change the computers heard, and it
   * stayed 0 while the side was powered.
   */
  public static boolean canRead(BlockPosition pos, Direction side) {
    var level = pos.level();
    var beyond = pos.offset(side).toBlockPos();
    if (!li.cil.oc.core.impl.util.LoadedChunks.isReady(level, beyond)) return false;
    // And what its signal is read from: a solid block's comes from its neighbours, powered dust's from what is around
    // it. One in a chunk not loaded yet was loaded there and then, the server thread waiting, beyond the loaded area.
    // Other blocks (levers, torches, redstone blocks) give their own: no more chunks for them.
    var state = level.getBlockState(beyond);
    if (weakPower.check(state, level, beyond, side)) {
      for (Direction around : Direction.Plane.HORIZONTAL) {
        var next = beyond.relative(around);
        if (!li.cil.oc.core.impl.util.LoadedChunks.isReady(level, next)) return false;
        if (poweredWire(level.getBlockState(next)) && !aroundReady(level, next)) return false;
      }
    } else if (poweredWire(state)) {
      return aroundReady(level, beyond);
    }
    return true;
  }

  /** Whether a block's signal is read from its neighbours (Forge's shouldCheckWeakPower, set by the platform). */
  public interface WeakPower {
    boolean check(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, Direction side);
  }

  public static WeakPower weakPower = (state, level, pos, side) -> state.isRedstoneConductor(level, pos);

  // dust, modded too, that reads what is around it (unpowered, its signal is 0 before that)
  private static boolean poweredWire(net.minecraft.world.level.block.state.BlockState state) {
    return state.getBlock() instanceof net.minecraft.world.level.block.RedStoneWireBlock
      && (!state.hasProperty(net.minecraft.world.level.block.RedStoneWireBlock.POWER) || state.getValue(net.minecraft.world.level.block.RedStoneWireBlock.POWER) > 0);
  }

  private static boolean aroundReady(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
    for (Direction around : Direction.Plane.HORIZONTAL) {
      if (!li.cil.oc.core.impl.util.LoadedChunks.isReady(level, pos.relative(around))) return false;
    }
    return true;
  }

  public static int computeInput(BlockPosition pos, Direction side) {
    var brPos = pos.offset(side).toBlockPos();
    if (pos.level().hasChunk(brPos.getX() >> 4, brPos.getZ() >> 4)) {
      int max = 0;
      for (RedstoneProvider provider : providers) {
        max = Math.max(max, provider.computeInput(pos, side));
      }
      return max;
    }
    return 0;
  }

  public static int[] computeBundledInput(BlockPosition pos, Direction side) {
    var bundPos = pos.offset(side).toBlockPos();
    if (pos.level().hasChunk(bundPos.getX() >> 4, bundPos.getZ() >> 4)) {
      int[] result = null;
      for (RedstoneProvider provider : providers) {
        int[] input = provider.computeBundledInput(pos, side);
        if (input != null) {
          if (result == null) {
            result = input.clone();
          } else {
            for (int i = 0; i < Math.min(result.length, input.length); i++) {
              result[i] = Math.max(result[i], input[i]);
            }
          }
        }
      }
      return result;
    }
    return null;
  }

  public interface RedstoneProvider {
    int computeInput(BlockPosition pos, Direction side);

    int[] computeBundledInput(BlockPosition ignoredPos, Direction ignoredSide);
  }
}
