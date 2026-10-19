package li.cil.oc.core.impl.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public interface BlockInteractionHandler {
  Player getFakePlayer(Level level, BlockPosition ignoredPos);

  boolean mayInteract(Level level, BlockPosition pos, Direction face);

  boolean checkBlockBreak(Level level, BlockPos pos, BlockState state);

  /** As the given player (an agent's, its owner's: claims let robots do what their owners may). */
  default boolean mayInteract(Level level, BlockPosition pos, Direction face, Player player) {
    return mayInteract(level, pos, face);
  }

  default boolean checkBlockBreak(Level level, BlockPos pos, BlockState state, Player player) {
    return checkBlockBreak(level, pos, state);
  }
}
