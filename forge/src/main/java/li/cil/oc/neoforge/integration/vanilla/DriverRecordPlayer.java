package li.cil.oc.neoforge.integration.vanilla;

import li.cil.oc.api.driver.EnvironmentProvider;
import li.cil.oc.api.driver.NamedBlock;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.prefab.DriverSidedBlockEntity;
import li.cil.oc.core.impl.integration.ManagedBlockEntityEnvironment;
import li.cil.oc.core.util.ResultWrapper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;

@SuppressWarnings("unused")
public final class DriverRecordPlayer extends DriverSidedBlockEntity {
  @Override
  public Class<?> getBlockEntityClass() {
    return JukeboxBlockEntity.class;
  }

  @Override
  public ManagedEnvironment createEnvironment(Level world, BlockPos pos, Direction side) {
    return new Environment((JukeboxBlockEntity) world.getBlockEntity(pos));
  }

  public static final class Environment extends ManagedBlockEntityEnvironment<JukeboxBlockEntity> implements NamedBlock {
    public Environment(JukeboxBlockEntity BlockEntity) {
      super(BlockEntity, "jukebox");
    }

    @Override
    public String preferredName() {
      return "jukebox";
    }

    @Override
    public int priority() {
      return 0;
    }

    @Callback(doc = "function():string -- Get the title of the record currently in the jukebox.")
    public Object[] getRecord(Context context, Arguments args) {
      var record = BlockEntity.getItem(0);
      if (record.getItem() instanceof net.minecraft.world.item.RecordItem recordItem) {
        return ResultWrapper.result(recordItem.getDisplayName().getString());
      }
      return null;
    }

    // the jukebox's own start and stop: a bare level event 1010 left it not playing (no comparator signal, no end of
    // the song, allays and parrots deaf to it) and 1010 with no record never stopped the music (1011 does)
    @Callback(doc = "function() -- Start playing the record currently in the jukebox.")
    public Object[] play(Context context, Arguments args) {
      var record = BlockEntity.getItem(0);
      if (!record.isEmpty() && record.getItem() instanceof net.minecraft.world.item.RecordItem && BlockEntity.getLevel() != null) {
        BlockEntity.startPlaying();
        return ResultWrapper.result(true);
      }
      return null;
    }

    @Callback(doc = "function() -- Stop playing the record currently in the jukebox.")
    @SuppressWarnings("SameReturnValue")
    public Object[] stop(Context context, Arguments args) {
      if (BlockEntity.isRecordPlaying() && BlockEntity.getLevel() != null) {
        BlockEntity.stopPlaying();
      }
      return null;
    }
  }

  public static final class Provider implements EnvironmentProvider {
    @Override
    public Class<?> getEnvironment(ItemStack stack) {
      if (stack != null && Block.byItem(stack.getItem()) == Blocks.JUKEBOX) {
        return Environment.class;
      }
      return null;
    }
  }
}
