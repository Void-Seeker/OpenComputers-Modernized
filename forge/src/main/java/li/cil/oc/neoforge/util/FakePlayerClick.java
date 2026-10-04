package li.cil.oc.neoforge.util;

import java.util.ArrayList;
import java.util.List;
import li.cil.oc.core.impl.OCSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;

/**
 * Right-clicks a block like a player standing in the block next to it, and collects everything the click produces:
 * what is left in the hand, what the block gives the player, and items it drops into the world around it.
 */
public final class FakePlayerClick {
  public record Outcome(InteractionResult result, List<ItemStack> items) {
  }

  private static Level captureLevel;
  private static AABB captureBounds;
  private static List<ItemStack> captured;

  private FakePlayerClick() {
  }

  /** Right-click the block on the given side, holding the stack. */
  public static Outcome click(ServerLevel level, BlockPos from, Direction side, ItemStack held) {
    FakePlayer player = prepare(level, from, side, held);
    BlockHitResult hit = faceTowards(from, side);
    List<ItemStack> dropped = new ArrayList<>();
    captureLevel = level;
    captureBounds = new AABB(from).minmax(new AABB(hit.getBlockPos())).inflate(1);
    captured = dropped;
    InteractionResult result;
    try {
      result = player.gameMode.useItemOn(player, level, held, InteractionHand.MAIN_HAND, hit);
      ItemStack inHand = player.getMainHandItem();
      if (!result.consumesAction() && !inHand.isEmpty()) {
        InteractionResult used = player.gameMode.useItem(player, level, inHand, InteractionHand.MAIN_HAND);
        if (used.consumesAction() || result == InteractionResult.PASS) result = used;
      }
      if (player.isUsingItem()) player.stopUsingItem();
      if (player.containerMenu != player.inventoryMenu) player.closeContainer();
    } finally {
      captured = null;
      captureBounds = null;
      captureLevel = null;
    }
    return new Outcome(result, collect(player, dropped));
  }

  /**
   * Place the held block into the block space on the given side, as if clicking the face of it; nothing is clicked
   * or replaced there, so the space must hold only air or fluid.
   */
  public static Outcome place(ServerLevel level, BlockPos from, Direction side, ItemStack held) {
    FakePlayer player = prepare(level, from, side, held);
    // ItemStack.useOn fires Forge's block place event and undoes the placement when it is cancelled
    InteractionResult result = held.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, faceTowards(from, side)));
    return new Outcome(result, collect(player, List.of()));
  }

  private static FakePlayer prepare(ServerLevel level, BlockPos from, Direction side, ItemStack held) {
    FakePlayer player = FakePlayerFactory.get(level, OCSettings.get().fakePlayerProfile);
    // Creative mode would hand back what the click used up.
    if (player.gameMode.getGameModeForPlayer() != GameType.SURVIVAL) player.setGameMode(GameType.SURVIVAL);
    player.getInventory().clearContent();
    // Eyes on the face between the two blocks, so item ray casts start in the target block, not in our own.
    Vec3 eye = Vec3.atCenterOf(from).relative(side, 0.51);
    float pitch = side == Direction.UP ? -90 : side == Direction.DOWN ? 90 : 0;
    float yaw = side.getAxis().isHorizontal() ? side.toYRot() : 0;
    player.moveTo(eye.x, eye.y - player.getEyeHeight(), eye.z, yaw, pitch);
    player.setYHeadRot(yaw);
    player.setItemInHand(InteractionHand.MAIN_HAND, held);
    return player;
  }

  // The face of the target block that looks at us.
  private static BlockHitResult faceTowards(BlockPos from, Direction side) {
    BlockPos target = from.relative(side);
    return new BlockHitResult(Vec3.atCenterOf(target).relative(side.getOpposite(), 0.5), side.getOpposite(), target, false);
  }

  private static List<ItemStack> collect(FakePlayer player, List<ItemStack> dropped) {
    Inventory inventory = player.getInventory();
    List<ItemStack> collected = new ArrayList<>();
    for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
      ItemStack stack = inventory.removeItemNoUpdate(slot);
      if (!stack.isEmpty()) collected.add(stack);
    }
    collected.addAll(dropped);
    return collected;
  }

  /** Registered on the Forge bus: swallows items that appear around a block while it is being clicked. */
  public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
    if (captured != null && event.getLevel() == captureLevel && event.getEntity() instanceof ItemEntity item
      && captureBounds.contains(item.position()) && !item.getItem().isEmpty()) {
      captured.add(item.getItem().copy());
      event.setCanceled(true);
    }
  }
}
