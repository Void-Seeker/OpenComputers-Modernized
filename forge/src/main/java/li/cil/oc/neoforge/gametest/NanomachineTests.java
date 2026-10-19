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

  /**
   * Leaving the End keeps the nanomachines' energy and inputs: the exit portal makes a new player object for the same
   * player, and the controller moves to it (it was drained and reset as on death).
   */
  @GameTest(template = EMPTY)
  public static void leavingTheEndKeepsEnergyAndInputs(final GameTestHelper helper) {
    final FakePlayer player = player(helper);
    final var controller = (ControllerImpl) li.cil.oc.api.Nanomachines.installController(player);
    final FakePlayer respawned = new FakePlayer(helper.getLevel(), player.getGameProfile());
    respawned.setId(player.getId()); // what PlayerList.respawn does: the same entity id, so the same nanomachines
    respawned.getPersistentData().put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG,
      player.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG)); // and Forge's restoreFrom
    try {
      controller.reconfigure();
      controller.changeBuffer(500);
      controller.setInput(0, true);
      final double before = controller.getLocalBuffer();
      MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(respawned, true));
      MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END, respawned));
      final var moved = (ControllerImpl) li.cil.oc.api.Nanomachines.getController(respawned);
      helper.assertTrue(moved != null && moved != controller && moved.player == respawned, "the nanomachines did not move to the new player object");
      helper.assertTrue(moved.getLocalBuffer() == before, "leaving the End took the energy from " + before + " to " + moved.getLocalBuffer());
      helper.assertTrue(moved.getInput(0), "leaving the End switched the inputs off");
    } finally {
      li.cil.oc.api.Nanomachines.uninstallController(respawned);
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
