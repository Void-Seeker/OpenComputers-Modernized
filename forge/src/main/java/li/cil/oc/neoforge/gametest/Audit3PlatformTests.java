package li.cil.oc.neoforge.gametest;

import com.mojang.authlib.GameProfile;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import li.cil.oc.api.event.FileSystemAccessEvent;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.Node;
import li.cil.oc.core.Constants;
import li.cil.oc.core.impl.OCSettings;
import li.cil.oc.core.impl.common.PacketBuilderBase;
import li.cil.oc.core.impl.common.blockentity.Disassembler;
import li.cil.oc.core.impl.common.template.DisassemblerTemplates;
import li.cil.oc.core.impl.server.component.DebugCardBase;
import li.cil.oc.core.impl.server.machine.ArgumentsImpl;
import li.cil.oc.neoforge.OpenComputers;
import li.cil.oc.neoforge.common.init.Blocks;
import li.cil.oc.neoforge.integration.vanilla.DriverInventory;
import li.cil.repack.com.typesafe.config.ConfigValueFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.PlayerMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Platform glue (third audit): disk activity packets turned off, debug messages and the debug card's whitelist and time,
 * which players block entity packets go to, the disassembler and items its template takes apart into nothing, and
 * inventory names.
 */
@GameTestHolder(OpenComputers.ID)
@PrefixGameTestTemplate(false)
public final class Audit3PlatformTests {
  private static final String EMPTY = "empty";
  private static final BlockPos AT = new BlockPos(2, 2, 2);
  private static final String NOTHING = "oc_test_disassembles_into_nothing";

  private Audit3PlatformTests() {
  }

  private static ArgumentsImpl noArguments() {
    return new ArgumentsImpl(new Object[0]);
  }

  // ----------------------------------------------------------------------- //

  /**
   * misc.diskActivitySoundDelay -1 turns the disk activity packets off, as OC: the cache of a negative expiry threw, out
   * of every file system call (that had already acted) which made one.
   */
  @GameTest(template = EMPTY)
  public static void diskActivityTurnedOffSendsNothing(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.CASE_TIER_1.get());
    final var host = (EnvironmentHost) helper.getBlockEntity(AT);
    // a node not seen before (each has its own cache, made with the delay of its first activity)
    final Node node = li.cil.oc.api.Network.newNode(new li.cil.oc.api.network.Environment() {
      @Override
      public Node node() {
        return null;
      }

      @Override
      public void onConnect(final Node node) {
      }

      @Override
      public void onDisconnect(final Node node) {
      }

      @Override
      public void onMessage(final li.cil.oc.api.network.Message message) {
      }
    }, li.cil.oc.api.network.Visibility.None).create();
    final List<FileSystemAccessEvent> events = new ArrayList<>();
    final java.util.function.Consumer<FileSystemAccessEvent.Server> listener = events::add;
    final OCSettings settings = OCSettings.get();
    Throwable thrown = null;
    try {
      final Field delay = OCSettings.class.getField("diskActivitySoundDelay");
      delay.setAccessible(true);
      final int before = delay.getInt(settings);
      MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, FileSystemAccessEvent.Server.class, listener);
      try {
        delay.setInt(settings, -1);
        li.cil.oc.core.impl.common.PacketSender.sendFileSystemActivity(node, host, "hdd");
      } catch (RuntimeException e) {
        thrown = e;
      } finally {
        delay.setInt(settings, before);
        MinecraftForge.EVENT_BUS.unregister(listener);
      }
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
    helper.assertTrue(thrown == null, "disk activity with the delay at -1 threw " + thrown);
    helper.assertTrue(events.isEmpty(), "disk activity with the delay at -1 was announced " + events.size() + " times");
    helper.succeed();
  }

  // ----------------------------------------------------------------------- //

  /** /oc_sendDebugMessage reaches the debug card of the address: it refused every address (they have dashes). */
  @GameTest(template = EMPTY, timeoutTicks = 10_000_000)
  public static void debugMessagesReachTheirCard(final GameTestHelper helper) {
    TestMachines.runMicrocontroller(helper, AT, List.of(Constants.ItemName.CPUTier1, Constants.ItemName.RAMTier2, Constants.ItemName.DebugCard), """
      while true do
        local name, card, from, port, distance, text = computer.pullSignal()
        if name == "debug_message" then error("done:" .. tostring(from) .. "," .. tostring(text)) end
      end""", mc -> {
      final var machine = mc.machine();
      String card = null;
      for (final var component : machine.components().entrySet()) {
        if ("debug".equals(component.getValue())) card = component.getKey();
      }
      helper.assertTrue(card != null, "the microcontroller has no debug card: " + machine.components());
      final var server = helper.getLevel().getServer();
      server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "oc_sendDebugMessage " + card + " hello");
      final String address = card;
      TestMachines.whenWithin(helper, () -> String.valueOf(machine.lastError()).contains("done:"), 100, 10, () -> {
        helper.assertTrue(String.valueOf(machine.lastError()).contains("done:Server,hello"), "a debug message to " + address + " gave " + machine.lastError());
        helper.succeed();
      });
    });
  }

  /** The debug card whitelist is config/opencomputers/debug_card_whitelist.txt, as OC (it was config/config/opencomputers). */
  @GameTest(template = EMPTY)
  public static void debugCardWhitelistIsInTheConfigFolder(final GameTestHelper helper) {
    final File configDir;
    try {
      configDir = Files.createTempDirectory("oc-test-config").toFile();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    try {
      final File list = new File(configDir, "opencomputers" + File.separator + "debug_card_whitelist.txt");
      org.apache.commons.io.FileUtils.writeStringToFile(list, "oc-tester 0123456789abcdef0123456789abcdef\n", StandardCharsets.UTF_8);
      // (given the config folder, as by both platforms)
      final var settings = new OCSettings(OCSettings.get().config.withValue("debug.debugCardAccess", ConfigValueFactory.fromAnyRef("whitelist")), configDir);
      helper.assertTrue(settings.debugCardAccess instanceof OCSettings.DebugCardAccess.Whitelist whitelist && whitelist.isWhitelisted("oc-tester"),
        "the whitelist in " + list + " was not read (access " + settings.debugCardAccess + ")");
    } catch (IOException e) {
      throw new IllegalStateException(e);
    } finally {
      org.apache.commons.io.FileUtils.deleteQuietly(configDir);
    }
    helper.succeed();
  }

  /**
   * The debug card whitelist gives a player 32 lower case hex digits as a nonce, as OC (made with commons-codec, which a
   * dedicated server has not: adding a player threw NoClassDefFoundError there), and a new one when invalidated.
   */
  @GameTest(template = EMPTY)
  public static void debugCardWhitelistMakesNoncesOnADedicatedServer(final GameTestHelper helper) {
    final File configDir;
    try {
      configDir = Files.createTempDirectory("oc-test-nonces").toFile();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    try {
      final File list = new File(configDir, "debug_card_whitelist.txt");
      final var whitelist = new OCSettings.DebugCardAccess.Whitelist(list);
      whitelist.add("OC-Tester");
      final String nonce = whitelist.nonce("oc-tester");
      helper.assertTrue(nonce != null && nonce.matches("[0-9a-f]{32}"), "the nonce is " + nonce);
      helper.assertTrue(whitelist.checkAccess(new OCSettings.AccessContext("OC-Tester", nonce)) == null, "the nonce gives no access");
      whitelist.invalidate("OC-Tester");
      final String renewed = whitelist.nonce("oc-tester");
      helper.assertTrue(renewed != null && renewed.matches("[0-9a-f]{32}") && !renewed.equals(nonce), "the invalidated nonce " + nonce + " became " + renewed);
      final String saved = Files.readString(list.toPath(), StandardCharsets.UTF_8).trim();
      helper.assertTrue(("oc-tester " + renewed).equals(saved), "the whitelist file holds '" + saved + "'");
    } catch (IOException e) {
      throw new IllegalStateException(e);
    } finally {
      org.apache.commons.io.FileUtils.deleteQuietly(configDir);
    }
    helper.succeed();
  }

  /** The debug card's world time is the time of day, as OC (getWorldTime) and what setTime sets: it was the game's age. */
  @GameTest(template = EMPTY)
  public static void debugCardTimeIsTheTimeOfDay(final GameTestHelper helper) {
    final ServerLevel level = helper.getLevel();
    // (eight days on: the same time of day and moon)
    if (level.getDayTime() == level.getGameTime()) level.setDayTime(level.getDayTime() + 8 * 24000L);
    final Object[] time = new DebugCardBase.WorldValue(level, null).getTime(null, noArguments());
    helper.assertTrue(time.length == 1 && time[0] instanceof Number n && n.longValue() == level.getDayTime(),
      "getTime gave " + Arrays.toString(time) + " at the day time " + level.getDayTime() + ", game time " + level.getGameTime());
    helper.succeed();
  }

  // ----------------------------------------------------------------------- //

  private static PlayerMap playerMap(final ServerLevel level) {
    try {
      for (final Field field : ChunkMap.class.getDeclaredFields()) {
        if (field.getType() == PlayerMap.class) {
          field.setAccessible(true);
          return (PlayerMap) field.get(level.getChunkSource().chunkMap);
        }
      }
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
    throw new IllegalStateException("no player map");
  }

  /** Runs the action with the player in the level and, if seeing, sent the chunks around it (all in this tick). */
  static void inLevel(final ServerLevel level, final ServerPlayer player, final boolean seeing, final Runnable action) {
    final PlayerMap players = playerMap(level);
    final long chunk = ChunkPos.asLong(player.blockPosition());
    player.setLastSectionPos(SectionPos.of(player));
    level.players().add(player);
    if (seeing) players.addPlayer(chunk, player, false);
    try {
      action.run();
    } finally {
      level.players().remove(player);
      if (seeing) players.removePlayer(chunk, player);
    }
  }

  /**
   * A block entity's packets go to the players its chunk is sent to, as OC (isPlayerWatchingChunk): a player near it in
   * the same dimension whom the chunk is not sent to got them too (every screen update went to every player in reach
   * of its 400 squared blocks once the chunk was loaded).
   */
  @GameTest(template = EMPTY)
  public static void blockEntityPacketsGoToPlayersSeeingTheChunk(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.CASE_TIER_1.get());
    final var blockEntity = helper.getBlockEntity(AT);
    final ServerLevel level = helper.getLevel();
    final Vec3 at = Vec3.atCenterOf(blockEntity.getBlockPos());
    final ServerPlayer away = FakePlayerFactory.get(level, new GameProfile(UUID.nameUUIDFromBytes("oc-test-not-seeing".getBytes(StandardCharsets.UTF_8)), "[OC not seeing]"));
    final ServerPlayer seeing = FakePlayerFactory.get(level, new GameProfile(UUID.nameUUIDFromBytes("oc-test-seeing".getBytes(StandardCharsets.UTF_8)), "[OC seeing]"));
    for (final ServerPlayer player : List.of(away, seeing)) player.setPos(at.x, at.y, at.z);
    final List<Player> sentTo = new ArrayList<>();
    try (PacketBuilderBase<ByteArrayOutputStream> packet = new PacketBuilderBase<>(new ByteArrayOutputStream()) {
      @Override
      protected byte[] getPayloadBytes() {
        return new byte[0];
      }

      @Override
      public void sendToAllPlayers() {
        throw new UnsupportedOperationException();
      }

      @Override
      public void sendToPlayer(final Player player) {
        sentTo.add(player);
      }

      @Override
      public void sendToServer() {
        throw new UnsupportedOperationException();
      }
    }) {
      inLevel(level, away, false, () -> inLevel(level, seeing, true, () -> packet.sendToPlayersNearBlockEntity(blockEntity)));
    }
    helper.assertTrue(sentTo.equals(List.of(seeing)), "a block entity's packet went to " + sentTo.stream().map(p -> p.getGameProfile().getName()).toList()
      + " (only [OC seeing] is sent its chunk)");
    helper.succeed();
  }

  // ----------------------------------------------------------------------- //

  /** A template taking marked dirt apart into nothing (as an empty server's, with its recipe gone). */
  public static boolean selectNothing(final ItemStack stack) {
    return stack.is(Items.DIRT) && stack.getTag() != null && stack.getTag().getBoolean(NOTHING);
  }

  public static ItemStack[] disassembleIntoNothing(final ItemStack stack, final ItemStack[] ingredients) {
    return new ItemStack[0];
  }

  @SuppressWarnings("unchecked")
  private static List<DisassemblerTemplates.Template> templates() {
    try {
      final Field field = DisassemblerTemplates.class.getDeclaredField("templates");
      field.setAccessible(true);
      return (List<DisassemblerTemplates.Template>) field.get(null);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * The disassembler takes no item its template takes apart into nothing, and gives one back that was in it already:
   * it was used up for nothing (an empty server or a navigation upgrade whose recipe a datapack removed).
   */
  @GameTest(template = EMPTY, timeoutTicks = 10_000_000)
  public static void disassemblerKeepsWhatItsTemplateGivesNothingFor(final GameTestHelper helper) {
    final DisassemblerTemplates.Template nothing;
    try {
      final Method select = Audit3PlatformTests.class.getMethod("selectNothing", ItemStack.class);
      final Method disassemble = Audit3PlatformTests.class.getMethod("disassembleIntoNothing", ItemStack.class, ItemStack[].class);
      nothing = new DisassemblerTemplates.Template(select, disassemble);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
    final ItemStack item = new ItemStack(Items.DIRT);
    item.getOrCreateTag().putBoolean(NOTHING, true);
    final BlockPos chestPos = AT.east();
    helper.setBlock(AT, Blocks.DISASSEMBLER.get());
    helper.setBlock(chestPos, net.minecraft.world.level.block.Blocks.CHEST);
    final var disassembler = (Disassembler) helper.getBlockEntity(AT);
    final Container chest = (Container) helper.getBlockEntity(chestPos);
    templates().add(nothing);
    try {
      helper.assertTrue(!disassembler.canPlaceItem(0, item), "the disassembler takes an item its template takes apart into nothing");
      disassembler.setItem(0, item.copy()); // (put in while it had a recipe)
    } catch (RuntimeException | AssertionError e) {
      templates().remove(nothing);
      throw e;
    }
    TestMachines.whenWithin(helper, () -> disassembler.getItem(0).isEmpty(), 100, 10, () -> {
      templates().remove(nothing);
      int back = 0;
      for (int i = 0; i < chest.getContainerSize(); i++) {
        if (selectNothing(chest.getItem(i))) back += chest.getItem(i).getCount();
      }
      helper.assertTrue(disassembler.getItem(0).isEmpty(), "the disassembler did not take the item out of its slot");
      helper.assertTrue(back == 1 && disassembler.queue.isEmpty(), "the item its template takes apart into nothing was used up: " + back
        + " came back, parts " + disassembler.queue);
      helper.succeed();
    });
  }

  // ----------------------------------------------------------------------- //

  /** An inventory's name is its untranslated key, as OC 1.12 and the adapter's component names, or its custom name. */
  @GameTest(template = EMPTY)
  public static void inventoryNamesAreTheirKeys(final GameTestHelper helper) {
    final BlockPos chest = new BlockPos(1, 1, 1), named = new BlockPos(3, 1, 1);
    helper.setBlock(chest, net.minecraft.world.level.block.Blocks.CHEST);
    helper.setBlock(named, net.minecraft.world.level.block.Blocks.BARREL);
    ((BaseContainerBlockEntity) helper.getBlockEntity(named)).setCustomName(Component.literal("Ore Store"));
    final Function<BlockPos, Object> name = pos ->
      new DriverInventory.Environment((Container) helper.getBlockEntity(pos), helper.getLevel()).getInventoryName(null, noArguments())[0];
    final String names = name.apply(chest) + "," + name.apply(named);
    helper.assertTrue("container.chest,Ore Store".equals(names), "a chest and a renamed barrel are named " + names);
    helper.succeed();
  }
}
