package li.cil.oc.neoforge.common.item;

import li.cil.oc.core.impl.common.blockentity.traits.Rotatable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;

public class Wrench extends li.cil.oc.core.impl.common.item.Wrench {
  public Wrench(Properties properties) {
    super(properties);
  }

  @Override
  public boolean doesSneakBypassUse(@NotNull ItemStack stack, @NotNull LevelReader level, @NotNull BlockPos pos, @NotNull Player player) {
    return true;
  }

  @Override
  public @NotNull InteractionResult onItemUseFirst(@NotNull ItemStack ignoredStack, @NotNull UseOnContext context) {
    var world = context.getLevel();
    var pos = context.getClickedPos();
    var player = context.getPlayer();
    if (player == null) return InteractionResult.PASS;
    if (!world.isClientSide && (!player.canReach(pos, 9.0) || player.isSpectator())) {
      return InteractionResult.PASS;
    }
    if (world.hasChunk(pos.getX() >> 4, pos.getZ() >> 4) && world.isAreaLoaded(pos, 1)) {
      var state = world.getBlockState(pos);
      var face = context.getClickedFace();
      var rotation = switch (face) {
        case UP, DOWN, SOUTH -> Rotation.CLOCKWISE_90;
        case NORTH -> Rotation.COUNTERCLOCKWISE_90;
        case WEST -> Rotation.NONE;
        case EAST -> Rotation.CLOCKWISE_180;
      };
      var newState = isPart(state) ? state : state.rotate(world, pos, rotation);
      if (newState != state) {
        world.setBlock(pos, newState, 3);
        world.blockUpdated(pos, state.getBlock());
        player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        return !world.isClientSide ? InteractionResult.SUCCESS : InteractionResult.PASS;
      }
      BlockEntity te = world.getBlockEntity(pos);
      if (te instanceof Rotatable rotatable) {
        var currentFacing = rotatable.facing();
        var newFacing = face.getAxis() == Direction.Axis.Y
          ? Direction.from2DDataValue((currentFacing.get2DDataValue() + 3) & 3)
          : currentFacing.getClockWise();
        rotatable.facing(newFacing);
        world.blockUpdated(pos, state.getBlock());
        player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        return !world.isClientSide ? InteractionResult.SUCCESS : InteractionResult.PASS;
      }
    }
    return InteractionResult.PASS;
  }

  // one part of a block made of several (OC's Block.rotateBlock refused these): turned alone it no longer fits the rest
  // (a bed's other half turned to air without a drop); a door's halves follow each other
  private static boolean isPart(BlockState state) {
    return state.hasProperty(BlockStateProperties.BED_PART)
      || state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && !(state.getBlock() instanceof DoorBlock)
      || state.hasProperty(BlockStateProperties.CHEST_TYPE) && state.getValue(BlockStateProperties.CHEST_TYPE) != ChestType.SINGLE
      || state.hasProperty(BlockStateProperties.EXTENDED) && state.getValue(BlockStateProperties.EXTENDED)
      || state.getBlock() instanceof PistonHeadBlock || state.getBlock() instanceof MovingPistonBlock;
  }
}
