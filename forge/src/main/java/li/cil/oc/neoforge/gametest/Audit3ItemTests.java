package li.cil.oc.neoforge.gametest;

import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import li.cil.oc.core.Constants;
import li.cil.oc.core.common.GuiType;
import li.cil.oc.core.impl.OCSettings;
import li.cil.oc.core.impl.common.LootManager;
import li.cil.oc.core.impl.common.blockentity.Disassembler;
import li.cil.oc.core.impl.common.blockentity.Printer;
import li.cil.oc.core.impl.common.blockentity.Waypoint;
import li.cil.oc.core.impl.common.item.data.NavigationUpgradeData;
import li.cil.oc.core.impl.common.item.data.PrintData;
import li.cil.oc.core.impl.common.recipe.LootDiskCyclingRecipe;
import li.cil.oc.core.impl.integration.opencomputers.Item;
import li.cil.oc.core.impl.server.machine.ArgumentsImpl;
import li.cil.oc.core.impl.util.EventHandlerDelegate;
import li.cil.oc.core.impl.util.ItemUtils;
import li.cil.oc.neoforge.OpenComputers;
import li.cil.oc.neoforge.common.GuiHandler;
import li.cil.oc.neoforge.common.init.Blocks;
import li.cil.oc.neoforge.common.init.Items;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.network.PlayMessages;
import org.jetbrains.annotations.Nullable;

/**
 * Items: what the disassembler gives back, item menus, the database's item inventory, recipe remainders, the wrench;
 * printer shapes, geolyzer and transposer readings, waypoint labels (third audit).
 */
@GameTestHolder(OpenComputers.ID)
@PrefixGameTestTemplate(false)
public final class Audit3ItemTests {
  private static final String EMPTY = "empty";
  private static final BlockPos AT = new BlockPos(2, 2, 2);
  private static final String NS = OCSettings.namespace;

  private Audit3ItemTests() {
  }

  private static ItemStack item(final String name) {
    return li.cil.oc.api.Items.get(name).createItemStack(1);
  }

  private static boolean is(final ItemStack stack, final String name) {
    return !stack.isEmpty() && li.cil.oc.api.Items.get(stack) == li.cil.oc.api.Items.get(name);
  }

  private static int count(final List<ItemStack> stacks, final net.minecraft.world.item.Item item) {
    int n = 0;
    for (final ItemStack s : stacks) if (s.is(item)) n += s.getCount();
    return n;
  }

  private static int count(final ItemStack[] stacks, final net.minecraft.world.item.Item item) {
    return count(List.of(stacks), item);
  }

  private static ItemStack craft(final GameTestHelper helper, final String recipe, final ItemStack... grid) {
    final var found = helper.getLevel().getRecipeManager().byKey(new ResourceLocation(OpenComputers.ID, recipe));
    helper.assertTrue(found.isPresent() && found.get() instanceof CraftingRecipe, "no crafting recipe " + recipe);
    return ((CraftingRecipe) found.get()).assemble(crafting(grid), helper.getLevel().registryAccess());
  }

  private static TransientCraftingContainer crafting(final ItemStack... grid) {
    final var menu = new AbstractContainerMenu(null, 0) {
      @Override
      public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
      }

      @Override
      public boolean stillValid(Player player) {
        return true;
      }
    };
    final var crafting = new TransientCraftingContainer(menu, 3, 3);
    for (int i = 0; i < grid.length; i++) crafting.setItem(i, grid[i] == null ? ItemStack.EMPTY : grid[i]);
    return crafting;
  }

  private static ItemStack navigationUpgrade(final GameTestHelper helper, final ItemStack map) {
    final ItemStack gold = new ItemStack(net.minecraft.world.item.Items.GOLD_INGOT);
    return craft(helper, "navigationupgrade",
      gold.copy(), new ItemStack(net.minecraft.world.item.Items.COMPASS), gold.copy(),
      item(Constants.ItemName.ChipTier2), map, item(Constants.ItemName.ChipTier2),
      gold.copy(), new ItemStack(net.minecraft.world.item.Items.POTION), gold.copy());
  }

  private static li.cil.oc.api.machine.Context context() {
    return (li.cil.oc.api.machine.Context) java.lang.reflect.Proxy.newProxyInstance(li.cil.oc.api.machine.Context.class.getClassLoader(),
      new Class<?>[]{li.cil.oc.api.machine.Context.class}, (proxy, method, args) -> method.getReturnType() == boolean.class ? false : null);
  }

  // a real (not fake) player, not in the world, keeping what is sent to it
  private static ServerPlayer player(final GameTestHelper helper, final List<Packet<?>> sent) {
    final var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "oc-items"));
    new ServerGamePacketListenerImpl(helper.getLevel().getServer(), new Connection(PacketFlow.CLIENTBOUND) {
      @Override
      public void send(final Packet<?> packet, @Nullable final PacketSendListener listener) {
        sent.add(packet);
      }
    }, player);
    return player;
  }

  private static String payloads(final List<Packet<?>> sent, final ResourceLocation channel) {
    final StringBuilder all = new StringBuilder();
    for (final Packet<?> packet : sent) {
      if (packet instanceof ClientboundCustomPayloadPacket custom && custom.getIdentifier().equals(channel)) {
        final FriendlyByteBuf data = custom.getData();
        final byte[] bytes = new byte[data.readableBytes()];
        data.readBytes(bytes);
        all.append(new String(bytes, StandardCharsets.ISO_8859_1)).append('\n');
      }
    }
    return all.toString();
  }

  // ----------------------------------------------------------------------- //
  // disassembler

  /**
   * Items carrying data are taken apart by their recipe (it had to make them as they are: a crafted navigation upgrade,
   * which always has its map, got no parts and was destroyed; a configured server lost its own materials; a used card
   * was refused).
   */
  @GameTest(template = EMPTY)
  public static void itemsWithDataAreTakenApart(final GameTestHelper helper) {
    final ItemStack map = MapItem.create(helper.getLevel(), 0, 0, (byte) 0, false, false);
    final ItemStack navigation = navigationUpgrade(helper, map);
    helper.setBlock(AT, Blocks.DISASSEMBLER.get());
    final var disassembler = (Disassembler) helper.getBlockEntity(AT);
    helper.assertTrue(disassembler.canPlaceItem(0, navigation), "the disassembler refuses a crafted navigation upgrade");
    disassembler.disassemble(navigation, false);
    final List<ItemStack> parts = new ArrayList<>(disassembler.queue);
    final List<ItemStack> maps = parts.stream().filter(s -> s.is(net.minecraft.world.item.Items.FILLED_MAP)).toList();
    helper.assertTrue(count(parts, net.minecraft.world.item.Items.GOLD_INGOT) == 4 && maps.size() == 1 && MapItem.getMapId(maps.get(0)) != null
        && MapItem.getMapId(maps.get(0)).equals(MapItem.getMapId(map)),
      "a crafted navigation upgrade came apart into " + parts);
    disassembler.queue.clear();

    final ItemStack card = item(Constants.ItemName.NetworkCard);
    final CompoundTag data = new CompoundTag();
    final CompoundTag node = new CompoundTag();
    node.putString("address", UUID.randomUUID().toString());
    data.put("node", node);
    card.getOrCreateTag().put(NS + "data", data);
    helper.assertTrue(disassembler.canPlaceItem(0, card) && ItemUtils.getIngredients(card).length > 0, "a used network card is not taken apart");
    helper.succeed();
  }

  /** What crafting gives back is no part: acid gave its water bucket back a second time, the disassembler its lava bucket. */
  @GameTest(template = EMPTY)
  public static void disassemblyGivesNoCraftingRemainders(final GameTestHelper helper) {
    for (final String name : new String[]{Constants.ItemName.Acid, Constants.BlockName.Disassembler}) {
      final ItemStack[] parts = ItemUtils.getIngredients(item(name));
      helper.assertTrue(parts.length > 0 && java.util.Arrays.stream(parts).noneMatch(s -> s.getItem() instanceof net.minecraft.world.item.BucketItem),
        name + " came apart into " + java.util.Arrays.toString(parts));
    }
    helper.succeed();
  }

  /** A part made of any of a tag's items is its plain one: a computer case's chest a chest (any chest: an ender chest). */
  @GameTest(template = EMPTY)
  public static void disassemblyGivesTheTagsPlainItem(final GameTestHelper helper) {
    for (int i = 0; i < 20; i++) {
      final ItemStack[] parts = ItemUtils.getIngredients(item(Constants.BlockName.CaseTier1));
      helper.assertTrue(count(parts, net.minecraft.world.item.Items.CHEST) == 1 && count(parts, net.minecraft.world.item.Items.ENDER_CHEST) == 0
        && count(parts, net.minecraft.world.item.Items.TRAPPED_CHEST) == 0, "a computer case came apart into " + java.util.Arrays.toString(parts));
    }
    helper.succeed();
  }

  /** A beacon base print is not taken apart, as no print is (OC: its recipe takes the print itself). */
  @GameTest(template = EMPTY)
  public static void beaconBasePrintIsNotTakenApart(final GameTestHelper helper) {
    final PrintData cube = new PrintData();
    cube.stateOff.add(new PrintData.Shape(new AABB(0, 0, 0, 1, 1, 1), "minecraft:block/stone", null));
    final ItemStack crafted = craft(helper, "print_beacon", cube.createItemStack(), new ItemStack(net.minecraft.world.item.Items.DIAMOND_BLOCK));
    helper.assertTrue(is(crafted, Constants.BlockName.BeaconBasePrint), "the beacon print recipe made " + crafted);
    helper.setBlock(AT, Blocks.DISASSEMBLER.get());
    final var disassembler = (Disassembler) helper.getBlockEntity(AT);
    helper.assertTrue(!disassembler.canPlaceItem(0, crafted), "a beacon base print is taken apart into " + java.util.Arrays.toString(ItemUtils.getIngredients(crafted)));
    helper.succeed();
  }

  // ----------------------------------------------------------------------- //
  // navigation upgrade

  /**
   * A navigation upgrade holds one map: made with a stack of maps it kept the stack, and recrafting or taking it apart
   * gave the whole stack back. Upgrades made so before give one back too.
   */
  @GameTest(template = EMPTY)
  public static void navigationUpgradeHoldsOneMap(final GameTestHelper helper) {
    final ItemStack maps = MapItem.create(helper.getLevel(), 0, 0, (byte) 0, false, false);
    maps.setCount(5);
    final ItemStack navigation = navigationUpgrade(helper, maps);
    final CompoundTag stored = Item.peekDataTag(navigation).getCompound(NS + "map");
    helper.assertTrue(ItemStack.of(stored).getCount() == 1, "a navigation upgrade made with 5 maps holds " + ItemStack.of(stored));

    // one made before, holding a stack
    final ItemStack old = navigation.copy();
    final CompoundTag oldMap = stored.copy();
    oldMap.putByte("Count", (byte) 5);
    Item.getDataTag(old).put(NS + "map", oldMap);
    final var recraft = crafting(old, MapItem.create(helper.getLevel(), 1000, 1000, (byte) 0, false, false));
    final var recipe = (CraftingRecipe) helper.getLevel().getRecipeManager().byKey(new ResourceLocation(OpenComputers.ID, "navigationupgrade_recraft")).orElseThrow();
    final var remaining = recipe.getRemainingItems(recraft);
    helper.assertTrue(count(remaining, net.minecraft.world.item.Items.FILLED_MAP) == 1, "recrafting an upgrade holding 5 maps gave back " + remaining);
    helper.assertTrue(new NavigationUpgradeData(old).map.getCount() == 1, "an upgrade holding 5 maps has the map " + new NavigationUpgradeData(old).map);
    helper.succeed();
  }

  // ----------------------------------------------------------------------- //
  // menus

  /** The database's menu closes when its item leaves the hand (edits went to the item thrown away, silently lost). */
  @GameTest(template = EMPTY)
  public static void databaseMenuClosesWhenItsItemGoes(final GameTestHelper helper) {
    final ServerPlayer player = player(helper, new ArrayList<>());
    player.setItemInHand(InteractionHand.MAIN_HAND, item(Constants.ItemName.DatabaseUpgradeTier1));
    final var menu = GuiHandler.getServerGuiElement(0, GuiType.Database, player, helper.getLevel(), player.getInventory().selected, 0, 0, null);
    helper.assertTrue(menu != null && menu.stillValid(player), "no database menu: " + menu);
    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    helper.assertTrue(!menu.stillValid(player), "the database menu stays open with its item thrown away");
    helper.succeed();
  }

  /**
   * An item's menu on the client is built from the hotbar slot it was opened with: built from what the hand held when
   * the packet came, a scroll in between gave no menu or another item's (the screen crashed the client).
   */
  @GameTest(template = EMPTY)
  public static void itemMenuIsBuiltFromTheSlotItWasOpenedWith(final GameTestHelper helper) {
    final List<Packet<?>> sent = new ArrayList<>();
    final ServerPlayer player = player(helper, sent);
    final ItemStack database = item(Constants.ItemName.DatabaseUpgradeTier3);
    player.getInventory().selected = 4;
    player.getInventory().setItem(4, database);
    player.getInventory().setItem(5, item(Constants.ItemName.DatabaseUpgradeTier1));
    database.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
    player.getInventory().selected = 5; // scrolled before the packet came
    PlayMessages.OpenContainer open = null;
    for (final Packet<?> packet : sent) {
      if (packet instanceof ClientboundCustomPayloadPacket custom && custom.getIdentifier().equals(new ResourceLocation("fml", "play"))) {
        final FriendlyByteBuf data = custom.getData();
        if (data.readUnsignedByte() == 1) open = PlayMessages.OpenContainer.decode(data);
      }
    }
    helper.assertTrue(open != null, "no menu was opened");
    final var menu = open.getType().create(open.getWindowId(), player.getInventory(), open.getAdditionalData());
    final int entries = OCSettings.get().databaseEntriesPerTier[2];
    helper.assertTrue(menu instanceof li.cil.oc.neoforge.common.container.Database && menu.slots.size() == entries + 36,
      "the client's menu for a tier 3 database: " + menu + (menu != null ? " with " + menu.slots.size() + " slots" : ""));
    player.closeContainer();
    helper.succeed();
  }

  // ----------------------------------------------------------------------- //
  // database as an item inventory

  /** A database is no item inventory: its entries are ghosts (an inventory controller threw on it). */
  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void databaseIsNoItemInventory(final GameTestHelper helper) {
    final ItemStack database = item(Constants.ItemName.DatabaseUpgradeTier1);
    final CompoundTag data = new CompoundTag();
    final ListTag entries = new ListTag();
    final CompoundTag entry = new CompoundTag();
    entry.putByte("slot", (byte) 0);
    entry.put("item", new ItemStack(net.minecraft.world.item.Items.DIAMOND).save(new CompoundTag()));
    entries.add(entry);
    data.put(NS + "items", entries);
    database.getOrCreateTag().put(NS + "data", data);
    TestMachines.runRobot(helper, AT, List.of(Constants.ItemName.InventoryUpgrade, Constants.ItemName.InventoryControllerUpgrade),
      robot -> robot.mainInventory().setItem(0, database),
      "local ic = component.proxy(component.list('inventory_controller')()) "
        + "local n, why = ic.getItemInventorySize(1) local s, why2 = ic.suckFromItemInventory(1, 1) "
        + "error('done:' .. tostring(n) .. ',' .. tostring(why) .. ',' .. tostring(s) .. ',' .. tostring(why2))", 60, (robot, result) -> {
        int diamonds = 0;
        for (int i = 0; i < robot.mainInventory().getContainerSize(); i++) {
          if (robot.mainInventory().getItem(i).is(net.minecraft.world.item.Items.DIAMOND)) diamonds += robot.mainInventory().getItem(i).getCount();
        }
        helper.assertTrue("0,no item inventory,0,no item inventory".equals(result) && diamonds == 0,
          "a database as an item inventory: " + result + ", " + diamonds + " diamonds taken out");
        helper.succeed();
      });
  }

  // ----------------------------------------------------------------------- //
  // printer, geolyzer, transposer, waypoint

  /** A full off state leaves room for shapes of the on state (any full state refused shapes for both). */
  @GameTest(template = EMPTY)
  public static void printerTakesShapesForTheStateNotFull(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.PRINTER.get());
    final var printer = (Printer) helper.getBlockEntity(AT);
    for (int i = 0; i < OCSettings.get().maxPrintComplexity; i++) { // (distinct: the states are sets)
      printer.data.stateOff.add(new PrintData.Shape(new AABB(0, 0, 0, 1, (i + 1) / 64.0, 1), "minecraft:block/stone", null));
    }
    final Object[] on = printer.addShape(context(), new ArgumentsImpl(new Object[]{0, 0, 0, 16, 16, 16, "minecraft:block/stone", true}));
    helper.assertTrue(on != null && Boolean.TRUE.equals(on[0]) && printer.data.stateOn.size() == 1,
      "with the off state full, a shape for the on state gave " + (on == null ? null : java.util.Arrays.toString(on)));
    final Object[] off = printer.addShape(context(), new ArgumentsImpl(new Object[]{0, 0, 0, 16, 16, 16, "minecraft:block/stone", false}));
    helper.assertTrue(off != null && off.length > 1 && "model too complex".equals(off[1]) && printer.data.stateOff.size() == OCSettings.get().maxPrintComplexity,
      "a shape for the full off state gave " + (off == null ? null : java.util.Arrays.toString(off)));
    helper.succeed();
  }

  /** The geolyzer sees how far cocoa has grown (it said 0 at any age: 1.20.1's age is 0 to 2). */
  @GameTest(template = EMPTY)
  public static void geolyzerSeesCocoaGrowth(final GameTestHelper helper) {
    final BlockPos log = AT.north();
    helper.setBlock(log, net.minecraft.world.level.block.Blocks.JUNGLE_LOG);
    final BlockPos abs = helper.absolutePos(AT);
    final var host = new li.cil.oc.api.network.EnvironmentHost() {
      @Override
      public net.minecraft.world.level.Level world() {
        return helper.getLevel();
      }

      @Override
      public double xPosition() {
        return abs.getX() + 0.5;
      }

      @Override
      public double yPosition() {
        return abs.getY() + 1.5;
      }

      @Override
      public double zPosition() {
        return abs.getZ() + 0.5;
      }

      @Override
      public void markChanged() {
      }
    };
    for (final int age : new int[]{1, 2}) {
      helper.setBlock(AT, net.minecraft.world.level.block.Blocks.COCOA.defaultBlockState().setValue(CocoaBlock.FACING, Direction.NORTH).setValue(CocoaBlock.AGE, age));
      final var data = EventHandlerDelegate.get().postGeolyzerAnalyze(host, new HashMap<>(), abs.getX(), abs.getY(), abs.getZ());
      final Object growth = data == null ? null : data.get("growth");
      helper.assertTrue(growth instanceof Float f && Math.abs(f - age / 2f) < 1e-6, "cocoa of age " + age + " has grown " + growth);
    }
    helper.succeed();
  }

  /** store() says whether the database entry was taken (always true: an empty entry is no null). */
  @GameTest(template = EMPTY, timeoutTicks = 300)
  public static void storeSaysWhetherTheEntryWasTaken(final GameTestHelper helper) {
    final BlockPos pos = AT;
    helper.setBlock(pos.north(), net.minecraft.world.level.block.Blocks.CHEST);
    ((net.minecraft.world.Container) helper.getBlockEntity(pos.north())).setItem(0, new ItemStack(net.minecraft.world.item.Items.DIAMOND));
    helper.setBlock(pos.above(), net.minecraft.world.level.block.Blocks.STONE);
    final String program = "local tp = component.proxy(component.list('transposer')())\n"
      + "local geo = component.proxy(component.list('geolyzer')())\n"
      + "local db = component.proxy(component.list('database')())\n"
      + "local r = {tp.store(2, 1, db.address, 1), tp.store(2, 1, db.address, 1), geo.store(1, db.address, 2), geo.store(1, db.address, 2)}\n"
      + "error('done:' .. tostring(r[1]) .. ',' .. tostring(r[2]) .. ',' .. tostring(r[3]) .. ',' .. tostring(r[4]), 0)\n";
    TestMachines.runMicrocontroller(helper, pos, List.of(Constants.ItemName.CPUTier1, Constants.ItemName.RAMTier2, Constants.BlockName.Transposer,
      Constants.BlockName.Geolyzer, Constants.ItemName.DatabaseUpgradeTier1), program, mc -> TestMachines.when(helper, () -> !mc.machine().isRunning(), 100, () -> {
      final String error = String.valueOf(mc.machine().lastError());
      helper.assertTrue(error.contains("done:false,true,false,true"), "store() into an empty, then a taken entry (transposer, geolyzer): " + error);
      helper.succeed();
    }));
  }

  /** The level of an empty fluid container is 0 (it threw). */
  @GameTest(template = EMPTY, timeoutTicks = 300)
  public static void emptyFluidContainerHoldsNothing(final GameTestHelper helper) {
    final BlockPos pos = AT;
    helper.setBlock(pos.north(), net.minecraft.world.level.block.Blocks.CHEST);
    final var chest = (net.minecraft.world.Container) helper.getBlockEntity(pos.north());
    chest.setItem(0, new ItemStack(net.minecraft.world.item.Items.BUCKET));
    chest.setItem(1, new ItemStack(net.minecraft.world.item.Items.WATER_BUCKET));
    final String program = "local tp = component.proxy(component.list('transposer')())\n"
      + "error('done:' .. tostring(tp.getContainerLevelInSlot(2, 1)) .. ',' .. tostring(tp.getContainerLevelInSlot(2, 2)), 0)\n";
    TestMachines.runMicrocontroller(helper, pos, List.of(Constants.ItemName.CPUTier1, Constants.ItemName.RAMTier2, Constants.BlockName.Transposer), program,
      mc -> TestMachines.when(helper, () -> !mc.machine().isRunning(), 100, () -> {
        final String error = String.valueOf(mc.machine().lastError());
        helper.assertTrue(error.contains("done:0,1000"), "the levels of an empty and a water bucket: " + error);
        helper.succeed();
      }));
  }

  /** A label set from Lua reaches the players near the waypoint (only the label GUI's was sent: its text was old). */
  @GameTest(template = EMPTY)
  public static void waypointLabelReachesPlayers(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.WAYPOINT.get());
    final var waypoint = (Waypoint) helper.getBlockEntity(AT);
    final List<Packet<?>> sent = new ArrayList<>();
    final ServerPlayer player = player(helper, sent);
    final BlockPos abs = helper.absolutePos(AT);
    player.setPos(abs.getX() + 0.5, abs.getY() + 1, abs.getZ() + 0.5);
    final List<ServerPlayer> players = helper.getLevel().players();
    players.add(player); // near it, only during the call
    try {
      waypoint.setLabel(context(), new ArgumentsImpl(new Object[]{"oc-label-7"}));
    } finally {
      players.remove(player);
    }
    helper.assertTrue("oc-label-7".equals(waypoint.label) && payloads(sent, li.cil.oc.core.impl.common.network.OCPayload.ID).contains("oc-label-7"),
      "the label " + waypoint.label + " was not sent to the player near (" + sent.size() + " packets)");
    helper.succeed();
  }

  // ----------------------------------------------------------------------- //
  // recipes, wrench

  /** Cycling a loot disk with a stack of wrenches keeps the stack as it was (it grew by all but one). */
  @GameTest(template = EMPTY)
  public static void lootDiskCyclingKeepsTheWrenches(final GameTestHelper helper) {
    helper.assertTrue(!LootManager.disksForCycling().isEmpty(), "no loot disks to cycle");
    final FakePlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
    final var menu = new CraftingMenu(0, player.getInventory());
    menu.getSlot(1).set(LootManager.disksForCycling().get(0).copy());
    menu.getSlot(2).set(new ItemStack(Items.WRENCH.get(), 3)); // (as a stackable wrench)
    final var grid = (CraftingContainer) menu.getSlot(1).container;
    final var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, helper.getLevel());
    helper.assertTrue(recipe.isPresent() && recipe.get() instanceof LootDiskCyclingRecipe, "no loot disk cycling recipe: " + recipe);
    final ItemStack result = recipe.get().assemble(grid, helper.getLevel().registryAccess());
    menu.getSlot(0).onTake(player, result);
    helper.assertTrue(menu.getSlot(2).getItem().is(Items.WRENCH.get()) && menu.getSlot(2).getItem().getCount() == 3,
      "3 wrenches cycling a loot disk became " + menu.getSlot(2).getItem());
    helper.succeed();
  }

  private static void wrench(final GameTestHelper helper, final BlockPos pos) {
    final BlockPos abs = helper.absolutePos(pos);
    final FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "oc-wrench"));
    player.setPos(abs.getX() + 0.5, abs.getY() + 1, abs.getZ() + 1.5);
    final ItemStack wrench = new ItemStack(Items.WRENCH.get());
    player.setItemInHand(InteractionHand.MAIN_HAND, wrench);
    wrench.getItem().onItemUseFirst(wrench, new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false)));
  }

  /**
   * The wrench leaves a part of a block made of several alone (a turned bed half was left apart from the other, which
   * then turned to air without a drop; a double chest came apart); a door's halves turn together, a single chest turns.
   */
  @GameTest(template = EMPTY)
  public static void wrenchLeavesMultiBlockPartsAlone(final GameTestHelper helper) {
    final BlockPos foot = new BlockPos(1, 2, 1), head = foot.north();
    final BlockState bed = net.minecraft.world.level.block.Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
    helper.setBlock(foot, bed.setValue(BedBlock.PART, BedPart.FOOT));
    helper.setBlock(head, bed.setValue(BedBlock.PART, BedPart.HEAD));
    wrench(helper, foot);
    helper.assertTrue(helper.getBlockState(foot).equals(bed.setValue(BedBlock.PART, BedPart.FOOT)) && helper.getBlockState(head).equals(bed.setValue(BedBlock.PART, BedPart.HEAD)),
      "a wrenched bed: " + helper.getBlockState(foot) + ", " + helper.getBlockState(head));

    // facing south, the left half's other is to its west
    final BlockPos left = new BlockPos(4, 2, 1), right = left.west();
    final BlockState chest = net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH);
    helper.setBlock(left, chest.setValue(ChestBlock.TYPE, ChestType.LEFT));
    helper.setBlock(right, chest.setValue(ChestBlock.TYPE, ChestType.RIGHT));
    wrench(helper, left);
    helper.assertTrue(helper.getBlockState(left).equals(chest.setValue(ChestBlock.TYPE, ChestType.LEFT)) && helper.getBlockState(right).equals(chest.setValue(ChestBlock.TYPE, ChestType.RIGHT)),
      "a wrenched double chest: " + helper.getBlockState(left) + ", " + helper.getBlockState(right));

    final BlockPos lower = new BlockPos(1, 2, 3), upper = lower.above();
    final BlockState door = net.minecraft.world.level.block.Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
    helper.setBlock(lower, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
    helper.setBlock(upper, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    wrench(helper, lower);
    final BlockState turnedLower = helper.getBlockState(lower), turnedUpper = helper.getBlockState(upper);
    helper.assertTrue(turnedLower.is(net.minecraft.world.level.block.Blocks.OAK_DOOR) && turnedUpper.is(net.minecraft.world.level.block.Blocks.OAK_DOOR)
        && turnedLower.getValue(DoorBlock.FACING) != Direction.NORTH && turnedUpper.getValue(DoorBlock.FACING) == turnedLower.getValue(DoorBlock.FACING),
      "a wrenched door: " + turnedLower + ", " + turnedUpper);

    final BlockPos single = new BlockPos(3, 2, 3);
    helper.setBlock(single, chest);
    wrench(helper, single);
    helper.assertTrue(helper.getBlockState(single).getValue(ChestBlock.FACING) != Direction.SOUTH, "a wrenched single chest: " + helper.getBlockState(single));
    helper.succeed();
  }
}
