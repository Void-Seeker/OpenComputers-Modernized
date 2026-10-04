package li.cil.oc.neoforge.gametest;

import java.util.List;
import li.cil.oc.core.Constants;
import li.cil.oc.neoforge.OpenComputers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * The redstone card.
 */
@GameTestHolder(OpenComputers.ID)
@PrefixGameTestTemplate(false)
public final class RedstoneTests {
  private static final String EMPTY = "empty";

  private RedstoneTests() {
  }

  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void comparatorInputReadsContainersAndBlocks(final GameTestHelper helper) {
    final BlockPos pos = new BlockPos(2, 2, 2);
    // a hopper with one full stack of its five slots reads 3; it points sideways so it leaves the microcontroller alone
    helper.setBlock(pos.above(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.NORTH));
    ((Container) helper.getBlockEntity(pos.above())).setItem(0, new ItemStack(Items.STONE, 64));
    helper.setBlock(pos.below(), Blocks.COMPOSTER.defaultBlockState().setValue(ComposterBlock.LEVEL, 8));
    // up and down are the same for the card's facing-relative sides and the world
    TestMachines.runMicrocontroller(helper, pos, List.of(Constants.ItemName.CPUTier1, Constants.ItemName.RAMTier2, Constants.ItemName.RedstoneCardTier1),
      "local rs = component.proxy(component.list(\"redstone\")())\n"
        + "local hopper, composter = rs.getComparatorInput(1), rs.getComparatorInput(0)\n"
        + "if hopper ~= 3 or composter ~= 8 then error(\"hopper reads \" .. tostring(hopper) .. \", composter \" .. tostring(composter), 0) end\n"
        + "while true do computer.pullSignal(1) end\n",
      mc -> helper.succeed());
  }

  @GameTest(template = EMPTY)
  public static void redstoneIoReadsComparatorInput(final GameTestHelper helper) {
    final BlockPos pos = new BlockPos(2, 2, 2);
    helper.setBlock(pos.above(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.NORTH));
    ((Container) helper.getBlockEntity(pos.above())).setItem(0, new ItemStack(Items.STONE, 64));
    helper.setBlock(pos.below(), Blocks.COMPOSTER.defaultBlockState().setValue(ComposterBlock.LEVEL, 8));
    helper.setBlock(pos, li.cil.oc.neoforge.common.init.Blocks.REDSTONE.get());
    final var io = (li.cil.oc.core.impl.common.blockentity.Redstone) helper.getBlockEntity(pos);
    final var card = (li.cil.oc.core.impl.server.component.RedstoneVanilla) io.instance;
    final Object hopper = card.getComparatorInput(null, new li.cil.oc.core.impl.server.machine.ArgumentsImpl(new Object[]{1.0}))[0];
    final Object composter = card.getComparatorInput(null, new li.cil.oc.core.impl.server.machine.ArgumentsImpl(new Object[]{0.0}))[0];
    helper.assertTrue(((Number) hopper).intValue() == 3 && ((Number) composter).intValue() == 8, "hopper reads " + hopper + ", composter " + composter);
    helper.succeed();
  }

  /** The front is closed to cables and power only: the redstone card and the transposer work through it. */
  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void microcontrollerUsesItsFront(final GameTestHelper helper) {
    final BlockPos pos = new BlockPos(2, 2, 2);
    final Direction[] front = new Direction[1];
    TestMachines.runMicrocontroller(helper, pos,
      List.of(Constants.ItemName.CPUTier1, Constants.ItemName.RAMTier2, Constants.ItemName.RedstoneCardTier1, Constants.BlockName.Transposer),
      mc -> {
        front[0] = mc.facing();
        final Direction back = front[0].getOpposite();
        helper.setBlock(pos.relative(back), Blocks.CHEST);
        ((Container) helper.getBlockEntity(pos.relative(back))).setItem(0, new ItemStack(Items.STONE, 3));
        helper.setBlock(pos.relative(front[0]), Blocks.CHEST);
        final int f = front[0].get3DDataValue(), b = back.get3DDataValue();
        // the transposer counts sides absolutely, the redstone card relative to the facing: 3 is sides.front
        return "local tp = component.proxy(component.list(\"transposer\")())\n"
          + "local rs = component.proxy(component.list(\"redstone\")())\n"
          + "local function expect(what, want, got, why) if got ~= want then error(what .. \": \" .. tostring(got) .. \" \" .. tostring(why), 0) end end\n"
          + "expect(\"into the chest in front\", true, tp.transferItem(" + b + ", " + f + ", 1))\n"
          + "expect(\"out of the chest in front\", true, tp.transferItem(" + f + ", " + b + ", 1))\n"
          + "expect(\"into the chest in front again\", true, tp.transferItem(" + b + ", " + f + ", 1))\n"
          + "expect(\"drop to the front\", 1, tp.dropItem(" + b + ", " + f + ", 1))\n"
          + "rs.setOutput(3, 15)\n"
          + "while true do computer.pullSignal(1) end\n";
      },
      mc -> {
        final BlockPos abs = helper.absolutePos(pos);
        final StringBuilder signals = new StringBuilder();
        for (final Direction d : Direction.values()) {
          signals.append(d).append('=').append(helper.getLevel().getSignal(abs, d.getOpposite())).append(' ');
        }
        helper.assertTrue(helper.getLevel().getSignal(abs, front[0].getOpposite()) == 15 && helper.getLevel().getSignal(abs, front[0]) == 0,
          "side 3 should power only the front (" + front[0] + "); outputs: " + signals);
        final ItemStack inFront = ((Container) helper.getBlockEntity(pos.relative(front[0]))).getItem(0);
        helper.assertTrue(inFront.is(Items.STONE) && inFront.getCount() == 1, "the chest in front holds " + inFront);
        int dropped = 0;
        for (final ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(abs.relative(front[0])).inflate(0.5))) {
          dropped += item.getItem().getCount();
        }
        helper.assertTrue(dropped == 1, dropped + " items were dropped in front instead of 1");
        helper.succeed();
      });
  }

  /** setOutput with a table sets every listed side in one call (brew.lua pulses its dropper like that). */
  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void setOutputTakesATable(final GameTestHelper helper) {
    final BlockPos pos = new BlockPos(2, 2, 2);
    TestMachines.runMicrocontroller(helper, pos, List.of(Constants.ItemName.CPUTier1, Constants.ItemName.RAMTier2, Constants.ItemName.RedstoneCardTier1),
      "local rs = component.proxy(component.list(\"redstone\")())\n"
        + "rs.setOutput({[0] = 15, 15, 15, 15, 15, 15})\n"
        + "while true do computer.pullSignal(1) end\n",
      mc -> {
        final BlockPos abs = helper.absolutePos(pos);
        final StringBuilder signals = new StringBuilder();
        boolean all = true;
        for (final Direction d : Direction.values()) {
          final int signal = helper.getLevel().getSignal(abs, d.getOpposite());
          signals.append(d).append('=').append(signal).append(' ');
          all &= signal == 15;
        }
        helper.assertTrue(all, "a table of 15s should power every side; outputs: " + signals);
        helper.succeed();
      });
  }
}
