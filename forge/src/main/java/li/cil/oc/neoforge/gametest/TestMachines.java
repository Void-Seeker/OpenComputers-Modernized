package li.cil.oc.neoforge.gametest;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;
import li.cil.oc.core.impl.common.blockentity.Microcontroller;
import li.cil.oc.core.impl.common.item.data.MicrocontrollerData;
import li.cil.oc.neoforge.common.init.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayerFactory;

/**
 * Runs Lua on a microcontroller in a game test.
 */
final class TestMachines {
  private TestMachines() {
  }

  /**
   * Places a microcontroller built from the given parts, boots it with the program as its EEPROM and hands it to
   * the check five seconds later (tests using it need a longer timeout than the default 100 ticks). The program should end in an endless loop; an error stops the machine.
   */
  static void runMicrocontroller(final GameTestHelper helper, final BlockPos pos, final List<String> parts, final String program, final Consumer<Microcontroller> check) {
    final var data = new MicrocontrollerData();
    for (final String part : parts) {
      data.components.add(li.cil.oc.api.Items.get(part).createItemStack(1));
    }
    data.components.add(ItemStack.EMPTY);
    helper.setBlock(pos, Blocks.MICROCONTROLLER.get());
    final BlockPos abs = helper.absolutePos(pos);
    Blocks.MICROCONTROLLER.get().setPlacedBy(helper.getLevel(), abs, helper.getLevel().getBlockState(abs), FakePlayerFactory.getMinecraft(helper.getLevel()), data.createItemStack());
    helper.runAfterDelay(5, () -> {
      final var mc = (Microcontroller) helper.getLevel().getBlockEntity(abs);
      mc.changeEEPROM(li.cil.oc.api.Items.registerEEPROM("test", program.getBytes(StandardCharsets.UTF_8), null, false));
      ((li.cil.oc.api.network.Connector) mc.snooperNode).changeBuffer(1000);
      helper.assertTrue(mc.machine().start(), "machine did not start");
      helper.runAfterDelay(100, () -> {
        helper.assertTrue(mc.machine().isRunning(), "program stopped: " + mc.machine().lastError());
        check.accept(mc);
      });
    });
  }
}
