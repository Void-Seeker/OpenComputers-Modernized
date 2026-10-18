package li.cil.oc.neoforge.gametest;

import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import li.cil.oc.core.impl.common.nanomachines.ControllerImpl;
import li.cil.oc.neoforge.OpenComputers;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Nanomachines keep what a portal should not take, answer with the right numbers and work after a debug reset.
 */
@GameTestHolder(OpenComputers.ID)
@PrefixGameTestTemplate(false)
public final class NanomachineTests {
  private static final String EMPTY = "empty";

  private NanomachineTests() {
  }

  private static FakePlayer player(final GameTestHelper helper) {
    final FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "oc-nano"));
    player.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(2, 2, 2)));
    return player;
  }

  /** Leaving the End keeps the nanomachines' energy (it was drained as on death). */
  @GameTest(template = EMPTY)
  public static void leavingTheEndKeepsTheEnergy(final GameTestHelper helper) {
    final FakePlayer player = player(helper);
    final var controller = (ControllerImpl) li.cil.oc.api.Nanomachines.installController(player);
    try {
      controller.changeBuffer(500);
      final double before = controller.getLocalBuffer();
      MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(player, true));
      helper.assertTrue(before > 0 && controller.getLocalBuffer() == before, "leaving the End took the energy from " + before + " to " + controller.getLocalBuffer());
      MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(player, false));
      helper.assertTrue(controller.getLocalBuffer() == 0, "a respawn after death left " + controller.getLocalBuffer() + " energy");
    } finally {
      li.cil.oc.api.Nanomachines.uninstallController(player);
    }
    helper.succeed();
  }

  /** getAge answers seconds since the player's last action (it mixed clocks: about 1.7 billion). */
  @GameTest(template = EMPTY)
  public static void nanomachinesTellTheAgeInSeconds(final GameTestHelper helper) {
    final FakePlayer player = player(helper);
    final var controller = (ControllerImpl) li.cil.oc.api.Nanomachines.installController(player);
    final Object[][] reply = {null};
    final li.cil.oc.api.network.WirelessEndpoint listener = new li.cil.oc.api.network.WirelessEndpoint() {
      @Override
      public int x() {
        return player.getBlockX();
      }

      @Override
      public int y() {
        return player.getBlockY();
      }

      @Override
      public int z() {
        return player.getBlockZ();
      }

      @Override
      public net.minecraft.world.level.Level world() {
        return player.level();
      }

      @Override
      public void receivePacket(li.cil.oc.api.network.Packet packet, li.cil.oc.api.network.WirelessEndpoint sender) {
        reply[0] = packet.data();
      }
    };
    li.cil.oc.api.Network.joinWirelessNetwork(listener);
    try {
      controller.changeBuffer(1000);
      controller.responsePort = 1;
      controller.receivePacket(li.cil.oc.api.Network.newPacket("oc-test", null, 1,
        new Object[]{"nanomachines".getBytes(StandardCharsets.UTF_8), "getAge".getBytes(StandardCharsets.UTF_8)}), listener);
      controller.commandDelay = 1;
      controller.update();
      helper.assertTrue(reply[0] != null && reply[0].length >= 3, "no answer to getAge: " + java.util.Arrays.toString(reply[0]));
      final double age = ((Number) reply[0][2]).doubleValue();
      helper.assertTrue(age >= 0 && age < 3600, "a player just made is " + age + " seconds old");
    } finally {
      li.cil.oc.api.Network.leaveWirelessNetwork(listener);
      li.cil.oc.api.Nanomachines.uninstallController(player);
    }
    helper.succeed();
  }

  /** After /oc_debugNanomachines each input drives its behavior (the behaviors counted no inputs at all). */
  @GameTest(template = EMPTY)
  public static void debuggedNanomachinesCountTheirInputs(final GameTestHelper helper) {
    final FakePlayer player = player(helper);
    final var controller = (ControllerImpl) li.cil.oc.api.Nanomachines.installController(player);
    try {
      controller.debug();
      helper.assertTrue(!controller.configuration.behaviors.isEmpty(), "the debug configuration has no behaviors");
      final var behavior = controller.configuration.behaviors.get(0).behavior;
      controller.setInput(0, true);
      helper.assertTrue(controller.getInputCount(behavior) == 1, "with its input on, " + behavior.getNameHint() + " counts " + controller.getInputCount(behavior) + " inputs");
    } finally {
      li.cil.oc.api.Nanomachines.uninstallController(player);
    }
    helper.succeed();
  }
}
