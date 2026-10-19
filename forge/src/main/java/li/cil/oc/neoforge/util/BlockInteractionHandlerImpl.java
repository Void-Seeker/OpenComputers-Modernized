package li.cil.oc.neoforge.util;

import li.cil.oc.core.impl.OCSettings;
import li.cil.oc.core.impl.util.BlockInteractionHandler;
import li.cil.oc.core.impl.util.BlockPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;

public final class BlockInteractionHandlerImpl implements BlockInteractionHandler {
  @Override
  public Player getFakePlayer(Level level, BlockPosition pos) {
    Player player = FakePlayerFactory.get((ServerLevel) level, OCSettings.get().fakePlayerProfile);
    player.setPos(pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5);
    return player;
  }

  @Override
  public boolean mayInteract(Level level, BlockPosition blockPos, Direction face) {
    return mayInteract(level, blockPos, face, getFakePlayer(level, blockPos));
  }

  @Override
  public boolean mayInteract(Level level, BlockPosition blockPos, Direction face, Player player) {
    var pos = new BlockPos(blockPos.x(), blockPos.y(), blockPos.z());
    var hitVec = new net.minecraft.world.phys.BlockHitResult(
      new net.minecraft.world.phys.Vec3(blockPos.x() + 0.5, blockPos.y() + 0.5, blockPos.z() + 0.5),
      face, pos, false
    );
    PlayerInteractEvent.RightClickBlock event = new PlayerInteractEvent.RightClickBlock(
      player, net.minecraft.world.InteractionHand.MAIN_HAND, pos, hitVec
    );
    MinecraftForge.EVENT_BUS.post(event);
    // a denied use of the block is a no as well, as in OC (claims deny that way and it was let through)
    return !event.isCanceled() && event.getUseBlock() != net.minecraftforge.eventbus.api.Event.Result.DENY;
  }

  @Override
  public boolean checkBlockBreak(Level level, BlockPos pos, BlockState state) {
    BlockPosition bPos = new BlockPosition(pos.getX(), pos.getY(), pos.getZ(), level);
    return checkBlockBreak(level, pos, state, getFakePlayer(level, bPos));
  }

  @Override
  public boolean checkBlockBreak(Level level, BlockPos pos, BlockState state, Player player) {
    var event = new BlockEvent.BreakEvent(level, pos, state, player);
    MinecraftForge.EVENT_BUS.post(event);
    return event.isCanceled();
  }
}
