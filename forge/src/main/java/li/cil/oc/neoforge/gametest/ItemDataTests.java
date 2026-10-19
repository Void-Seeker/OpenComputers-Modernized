package li.cil.oc.neoforge.gametest;

import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import li.cil.oc.core.Constants;
import li.cil.oc.core.impl.OCSettings;
import li.cil.oc.core.impl.common.item.data.PrintData;
import li.cil.oc.core.impl.common.item.data.TabletData;
import li.cil.oc.core.impl.integration.opencomputers.Item;
import li.cil.oc.core.impl.server.machine.ArgumentsImpl;
import li.cil.oc.neoforge.OpenComputers;
import li.cil.oc.neoforge.common.init.Blocks;
import li.cil.oc.neoforge.common.init.Items;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Items keep their data and their energy right: through crafting, charging, moving, hosts saving them and tooltips.
 */
@GameTestHolder(OpenComputers.ID)
@PrefixGameTestTemplate(false)
public final class ItemDataTests {
  private static final String EMPTY = "empty";
  private static final BlockPos AT = new BlockPos(2, 2, 2);
  private static final String NS = OCSettings.namespace;

  private ItemDataTests() {
  }

  private static ItemStack item(final String name) {
    return li.cil.oc.api.Items.get(name).createItemStack(1);
  }

  // a real (not fake) player, not in the world, whose packets go nowhere
  private static ServerPlayer player(final GameTestHelper helper) {
    final var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "oc-test"));
    new net.minecraft.server.network.ServerGamePacketListenerImpl(helper.getLevel().getServer(),
      new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), player);
    return player;
  }

  private static ItemStack craft(final GameTestHelper helper, final String recipe, final ItemStack... grid) {
    final var crafting = crafting(grid);
    final var found = helper.getLevel().getRecipeManager().byKey(new ResourceLocation(OpenComputers.ID, recipe));
    helper.assertTrue(found.isPresent() && found.get() instanceof CraftingRecipe, "no crafting recipe " + recipe);
    return ((CraftingRecipe) found.get()).assemble(crafting, helper.getLevel().registryAccess());
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

  private static li.cil.oc.api.machine.Context context() {
    return (li.cil.oc.api.machine.Context) java.lang.reflect.Proxy.newProxyInstance(li.cil.oc.api.machine.Context.class.getClassLoader(),
      new Class<?>[]{li.cil.oc.api.machine.Context.class}, (proxy, method, args) -> method.getReturnType() == boolean.class ? false : null);
  }

  private static ItemStack tablet(final ItemStack... parts) {
    final var inventory = new SimpleContainer(22);
    inventory.setItem(0, item(Constants.ItemName.TabletCaseTier1));
    for (int i = 0; i < parts.length; i++) inventory.setItem(13 + i, parts[i]);
    return (ItemStack) li.cil.oc.core.impl.common.template.TabletTemplate.assemble(inventory)[0];
  }

  // ---- hover boots ----

  /** A Slow Falling potion stays on a player without boots (it was taken off every player every tick). */
  @GameTest(template = EMPTY)
  public static void hoverBootsLeaveOtherSlowFallingAlone(final GameTestHelper helper) {
    final ServerPlayer player = player(helper);
    player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 600, 0));
    player.addEffect(new MobEffectInstance(MobEffects.JUMP, 600, 3));
    li.cil.oc.neoforge.common.event.HoverBootsHandler.onLivingTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
    helper.assertTrue(player.hasEffect(MobEffects.SLOW_FALLING), "a Slow Falling potion was taken off a player without hover boots");
    helper.assertTrue(player.hasEffect(MobEffects.JUMP), "a Jump Boost IV potion was taken off a player without hover boots");
    helper.succeed();
  }

  /** Hover boots do not wear out (1.20.1's ArmorItem gave them diamond durability: they broke). */
  @GameTest(template = EMPTY)
  public static void hoverBootsDoNotWear(final GameTestHelper helper) {
    final ItemStack boots = new ItemStack(Items.HOVER_BOOTS.get());
    boots.hurt(100, helper.getLevel().random, null);
    helper.assertTrue(!boots.isDamageableItem() && boots.getDamageValue() == 0, "hover boots took " + boots.getDamageValue() + " damage");
    helper.succeed();
  }

  /** A jump with the boots' Jump Boost costs power.cost.hoverBootJump (it cost nothing). */
  @GameTest(template = EMPTY)
  public static void hoverBootJumpCostsEnergy(final GameTestHelper helper) {
    final ServerPlayer player = player(helper);
    final ItemStack boots = new ItemStack(Items.HOVER_BOOTS.get());
    final var item = (li.cil.oc.core.impl.common.item.HoverBoots) boots.getItem();
    item.setCharge(boots, item.maxCharge(boots));
    player.setItemSlot(EquipmentSlot.FEET, boots);
    player.addEffect(new MobEffectInstance(MobEffects.JUMP, 100, 3, false, false, true));
    final double before = item.getCharge(player.getItemBySlot(EquipmentSlot.FEET));
    MinecraftForge.EVENT_BUS.post(new LivingEvent.LivingJumpEvent(player));
    final double after = item.getCharge(player.getItemBySlot(EquipmentSlot.FEET));
    helper.assertTrue(Math.abs(before - after - OCSettings.get().hoverBootJump) < 1e-6, "a hover boot jump cost " + (before - after));
    helper.succeed();
  }

  // ---- Forge Energy ----

  /** 1000 FE into a battery upgrade is 100 OC (ratio 0.1 OC per FE), as for blocks (it was 10000). */
  @GameTest(template = EMPTY)
  public static void forgeEnergyChargesItemsAtTheRatio(final GameTestHelper helper) {
    final ItemStack battery = item(Constants.ItemName.BatteryUpgradeTier1);
    final var energy = battery.getCapability(ForgeCapabilities.ENERGY).orElseThrow(IllegalStateException::new);
    final int taken = energy.receiveEnergy(1000, false);
    final double stored = ((li.cil.oc.core.impl.common.item.UpgradeBattery) battery.getItem()).getCharge(battery);
    helper.assertTrue(taken == 1000 && Math.abs(stored - 100) < 1e-6, "1000 FE took " + taken + " and stored " + stored + " OC");
    final ItemStack boots = new ItemStack(Items.HOVER_BOOTS.get());
    ((li.cil.oc.core.impl.common.item.HoverBoots) boots.getItem()).setCharge(boots, 1000);
    final var bootsEnergy = boots.getCapability(ForgeCapabilities.ENERGY).orElseThrow(IllegalStateException::new);
    helper.assertTrue(bootsEnergy.getEnergyStored() == 10000 && bootsEnergy.getMaxEnergyStored() > 0,
      "hover boots with 1000 OC report " + bootsEnergy.getEnergyStored() + " of " + bootsEnergy.getMaxEnergyStored() + " FE");
    helper.succeed();
  }

  /** Charging a stack of 4 battery upgrades gives each a quarter (one battery's energy filled all four). */
  @GameTest(template = EMPTY)
  public static void batteryStackShareTheirCharge(final GameTestHelper helper) {
    final ItemStack batteries = item(Constants.ItemName.BatteryUpgradeTier1);
    batteries.setCount(4);
    final var item = (li.cil.oc.core.impl.common.item.UpgradeBattery) batteries.getItem();
    final double max = item.maxCharge(batteries);
    final double unused = item.charge(batteries, max, false);
    helper.assertTrue(Math.abs(unused) < 1e-6 && Math.abs(item.getCharge(batteries) - max / 4) < 1e-6,
      "a stack of 4 given one battery's energy holds " + item.getCharge(batteries) + " each (of " + max + "), " + unused + " left over");
    helper.succeed();
  }

  // ---- tablets ----

  /** A tablet's host follows the stack it is now (it kept writing into the stack from before a move). */
  @GameTest(template = EMPTY)
  public static void tabletFollowsItsStackWhenMoved(final GameTestHelper helper) {
    final Player player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "oc-tablet"));
    final ItemStack tablet = tablet(item(Constants.ItemName.CPUTier1), item(Constants.ItemName.RAMTier1));
    final var host = li.cil.oc.core.impl.common.item.Tablet.get(tablet, player);
    final ItemStack moved = tablet.copy();
    try {
      final var again = li.cil.oc.core.impl.common.item.Tablet.get(moved, player);
      helper.assertTrue(again == host && again.getStack() == moved, "after a move the tablet's host saves into " + (again.getStack() == moved ? "the moved stack" : "the old stack"));
    } finally {
      li.cil.oc.core.impl.util.TabletCache.get().invalidate(moved);
    }
    helper.succeed();
  }

  /** Energy a charger puts into a carried tablet reaches its machine (the next tick overwrote it). */
  @GameTest(template = EMPTY)
  public static void tabletKeepsWhatAChargerGives(final GameTestHelper helper) {
    final Player player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "oc-tablet"));
    final ItemStack tablet = tablet(item(Constants.ItemName.CPUTier1), item(Constants.ItemName.RAMTier1));
    final var host = li.cil.oc.core.impl.common.item.Tablet.get(tablet, player);
    try {
      host.update();
      final var connector = (li.cil.oc.api.network.Connector) host.serverComponent().node;
      connector.changeBuffer(-1000);
      host.update();
      final double before = connector.localBuffer();
      ((li.cil.oc.core.impl.common.item.Tablet) tablet.getItem()).charge(tablet, 500, false);
      host.update();
      helper.assertTrue(connector.localBuffer() >= before + 499, "charged 500, the tablet went from " + before + " to " + connector.localBuffer());
    } finally {
      li.cil.oc.core.impl.util.TabletCache.get().invalidate(tablet);
    }
    helper.succeed();
  }

  /** Recrafting a tablet with an EEPROM keeps its parts in their slots (the inner slot's part moved out of it). */
  @GameTest(template = EMPTY)
  public static void tabletEepromRecraftKeepsSlots(final GameTestHelper helper) {
    final var data = new TabletData();
    data.tier = 1;
    data.items = new ArrayList<>(Collections.nCopies(32, null));
    data.items.set(0, item(Constants.BlockName.ScreenTier1));
    data.items.set(3, item(Constants.ItemName.EEPROM));
    data.items.set(31, item(Constants.ItemName.HDDTier1));
    final ItemStack tablet = data.createItemStack();
    final ItemStack result = craft(helper, "tablet_eeprom", tablet, item(Constants.ItemName.EEPROM));
    final var after = new TabletData(result);
    final ItemStack inner = after.items.get(31);
    final ItemStack rom = after.items.get(3);
    helper.assertTrue(inner != null && li.cil.oc.api.Items.get(inner) == li.cil.oc.api.Items.get(Constants.ItemName.HDDTier1)
        && rom != null && li.cil.oc.api.Items.get(rom) == li.cil.oc.api.Items.get(Constants.ItemName.EEPROM),
      "after the recraft slot 31 holds " + inner + ", slot 3 " + rom);
    helper.succeed();
  }

  // ---- item components saved by their host ----

  private static li.cil.oc.api.network.ManagedEnvironment savingComponent(final String key) {
    return new li.cil.oc.api.prefab.AbstractManagedEnvironment() {
      @Override
      public void save(CompoundTag nbt, net.minecraft.core.HolderLookup.Provider provider) {
        super.save(nbt, provider);
        nbt.putString(key, "saved");
      }
    };
  }

  private static li.cil.oc.core.impl.common.inventory.ComponentInventory host(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.CASE_TIER_1.get());
    final BlockEntity entity = helper.getBlockEntity(AT);
    helper.assertTrue(entity instanceof li.cil.oc.core.impl.common.inventory.ComponentInventory, "a case is no component inventory: " + entity);
    return (li.cil.oc.core.impl.common.inventory.ComponentInventory) entity;
  }

  /** A database upgrade's entries stay when its host saves it (they were cleared with the component's data). */
  @GameTest(template = EMPTY)
  public static void databaseEntriesSurviveTheirHostSaving(final GameTestHelper helper) {
    final ItemStack database = new ItemStack(Items.DATABASE_UPGRADE_T1.get());
    final ListTag entries = new ListTag();
    final CompoundTag entry = new CompoundTag();
    entry.putByte("slot", (byte) 0);
    entry.put("item", new ItemStack(net.minecraft.world.item.Items.STONE).save(new CompoundTag()));
    entries.add(entry);
    Item.getDataTag(database).put(NS + "items", entries);
    host(helper).save(savingComponent("node-ish"), li.cil.oc.api.API.driver.driverFor(database), database, helper.getLevel().registryAccess());
    helper.assertTrue(Item.peekDataTag(database).getList(NS + "items", 10).size() == 1, "after its host saved it the database holds " + database.getTag());
    helper.succeed();
  }

  /** A tablet's HDD saves into the HDD inside it; the tablet's own machine data stays (a charger replaced it). */
  @GameTest(template = EMPTY)
  public static void tabletInAChargerKeepsItsMachine(final GameTestHelper helper) {
    final ItemStack tablet = tablet(item(Constants.ItemName.CPUTier1), item(Constants.ItemName.HDDTier1));
    Item.getDataTag(tablet).putString("machine", "state");
    final var driver = li.cil.oc.api.API.driver.driverFor(tablet);
    helper.assertTrue(driver instanceof li.cil.oc.neoforge.integration.opencomputers.DriverTablet, "no tablet driver: " + driver);
    host(helper).save(savingComponent("fs"), driver, tablet, helper.getLevel().registryAccess());
    helper.assertTrue("state".equals(Item.peekDataTag(tablet).getString("machine")), "the tablet's machine data became " + Item.peekDataTag(tablet));
    final var hdd = new TabletData(tablet).items.stream().filter(s -> s != null && li.cil.oc.api.Items.get(s) == li.cil.oc.api.Items.get(Constants.ItemName.HDDTier1)).findFirst();
    helper.assertTrue(hdd.isPresent() && "saved".equals(Item.peekDataTag(hdd.get()).getString("fs")), "the HDD in the tablet holds " + hdd.map(ItemStack::getTag).orElse(null));
    helper.succeed();
  }

  /** database.set keeps the vanilla part of the tag (an enchanted book was stored as a plain one). */
  @GameTest(template = EMPTY)
  public static void databaseSetKeepsEnchantments(final GameTestHelper helper) {
    final var entries = new SimpleContainer(9);
    final var database = new li.cil.oc.core.impl.server.component.UpgradeDatabase(entries);
    database.set(context(), new ArgumentsImpl(new Object[]{1, "minecraft:enchanted_book", 0, "{StoredEnchantments:[{id:\"minecraft:mending\",lvl:1s}]}"}));
    final ItemStack stored = entries.getItem(0);
    helper.assertTrue(stored.getTag() != null && stored.getTag().contains("StoredEnchantments"), "database.set stored " + stored + " " + stored.getTag());
    helper.succeed();
  }

  // ---- recipes ----

  /** Dyeing a floppy keeps its disk (address) and takes the new colour (the floppy came back blank, or kept its old colour). */
  @GameTest(template = EMPTY)
  public static void dyeingAFloppyKeepsItsContents(final GameTestHelper helper) {
    final ItemStack floppy = item(Constants.ItemName.Floppy);
    final CompoundTag node = new CompoundTag();
    node.putString("address", "floppy-address");
    Item.getDataTag(floppy).put("node", node);
    floppy.getOrCreateTag().putInt(NS + "color", 1);
    final ItemStack dyed = craft(helper, "floppy_blue", floppy, new ItemStack(net.minecraft.world.item.Items.BLUE_DYE));
    helper.assertTrue("floppy-address".equals(Item.address(dyed)), "the dyed floppy has the address " + Item.address(dyed) + ": " + dyed.getTag());
    helper.assertTrue(dyed.getTag() != null && dyed.getTag().getInt(NS + "color") == 4, "the dyed floppy has the colour " + (dyed.getTag() == null ? null : dyed.getTag().get(NS + "color")));
    helper.succeed();
  }

  /** Each crafted linked card pair gets its own tunnel (they all shared "creative"). */
  @GameTest(template = EMPTY)
  public static void craftedLinkedCardsHaveTheirOwnTunnel(final GameTestHelper helper) {
    final java.util.function.Supplier<ItemStack> once = () -> craft(helper, "linkedcard",
      new ItemStack(net.minecraft.world.item.Items.ENDER_EYE), null, new ItemStack(net.minecraft.world.item.Items.ENDER_EYE),
      item(Constants.ItemName.NetworkCard), item(Constants.ItemName.Interweb), item(Constants.ItemName.NetworkCard),
      item(Constants.ItemName.ChipTier3), null, item(Constants.ItemName.ChipTier3));
    final String first = Item.peekDataTag(once.get()).getString(NS + "tunnel");
    final String second = Item.peekDataTag(once.get()).getString(NS + "tunnel");
    helper.assertTrue(!first.isEmpty() && !first.equals(second), "two crafted linked cards have the tunnels '" + first + "' and '" + second + "'");
    helper.succeed();
  }

  /** The navigation upgrade keeps the map it is made with, and recrafting gives the old map back (neither happened). */
  @GameTest(template = EMPTY)
  public static void navigationUpgradeKeepsItsMap(final GameTestHelper helper) {
    final ItemStack gold = new ItemStack(net.minecraft.world.item.Items.GOLD_INGOT);
    final ItemStack map = MapItem.create(helper.getLevel(), 0, 0, (byte) 0, false, false);
    final ItemStack navigation = craft(helper, "navigationupgrade",
      gold.copy(), new ItemStack(net.minecraft.world.item.Items.COMPASS), gold.copy(),
      item(Constants.ItemName.ChipTier2), map, item(Constants.ItemName.ChipTier2),
      gold.copy(), new ItemStack(net.minecraft.world.item.Items.POTION), gold.copy());
    helper.assertTrue(Item.peekDataTag(navigation).contains(NS + "map"), "the navigation upgrade has no map: " + navigation.getTag());
    final ItemStack newMap = MapItem.create(helper.getLevel(), 1000, 1000, (byte) 0, false, false);
    final var recraft = crafting(navigation, newMap);
    final var recipe = (CraftingRecipe) helper.getLevel().getRecipeManager().byKey(new ResourceLocation(OpenComputers.ID, "navigationupgrade_recraft")).orElseThrow();
    final var remaining = recipe.getRemainingItems(recraft);
    helper.assertTrue(remaining.stream().anyMatch(s -> s.is(net.minecraft.world.item.Items.FILLED_MAP)), "recrafting the navigation upgrade gave back " + remaining);
    helper.succeed();
  }

  // ---- prints ----

  /** A crafted beacon base print drops as one after it was placed (it dropped as a plain print, the metal block gone). */
  @GameTest(template = EMPTY)
  public static void craftedBeaconBasePrintStaysOne(final GameTestHelper helper) {
    final PrintData cube = new PrintData();
    cube.stateOff.add(new PrintData.Shape(new AABB(0, 0, 0, 1, 1, 1), "minecraft:block/stone", null));
    final ItemStack crafted = craft(helper, "print_beacon", cube.createItemStack(), new ItemStack(net.minecraft.world.item.Items.IRON_BLOCK));
    helper.setBlock(AT, Blocks.BEACON_BASE_PRINT.get());
    final BlockPos abs = helper.absolutePos(AT);
    Blocks.BEACON_BASE_PRINT.get().setPlacedBy(helper.getLevel(), abs, helper.getBlockState(AT), FakePlayerFactory.getMinecraft(helper.getLevel()), crafted);
    final CompoundTag tag = helper.getBlockEntity(AT).saveWithFullMetadata();
    final ItemStack drop = ((li.cil.oc.core.impl.common.blockentity.Print) BlockEntity.loadStatic(abs, helper.getBlockState(AT), tag)).data.createItemStack();
    helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(drop.getItem()).getPath().equals("beaconbaseprint"), "a crafted and placed beacon base print drops as " + drop);
    // and one placed before crafting said what it is
    tag.getCompound(NS + "data").putBoolean("isBeaconBase", false);
    final ItemStack old = ((li.cil.oc.core.impl.common.blockentity.Print) BlockEntity.loadStatic(abs, helper.getBlockState(AT), tag)).data.createItemStack();
    helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(old.getItem()).getPath().equals("beaconbaseprint"), "an older beacon base print drops as " + old);
    helper.succeed();
  }

  // ---- blocks and their items ----

  /** A RAID whose label was set to nil still saves (it threw: the RAID left out of the chunk save, its disks gone). */
  @GameTest(template = EMPTY)
  public static void raidWithoutLabelSaves(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.RAID.get());
    final var raid = (li.cil.oc.core.impl.common.blockentity.Raid) helper.getBlockEntity(AT);
    raid.label.setLabel(null);
    try {
      raid.saveWithFullMetadata();
    } catch (Throwable t) {
      helper.fail("a RAID without label did not save: " + t);
    }
    helper.succeed();
  }

  /** A dyed chamelium block drops dyed (it dropped black). */
  @GameTest(template = EMPTY)
  public static void dyedChameliumBlockDropsDyed(final GameTestHelper helper) {
    final var state = Blocks.CHAMELIUM_BLOCK.get().defaultBlockState().setValue(li.cil.oc.core.impl.common.block.ChameliumBlock.COLOR, DyeColor.CYAN);
    helper.setBlock(AT, state);
    final List<ItemStack> drops = Block.getDrops(state, helper.getLevel(), helper.absolutePos(AT), null);
    helper.assertTrue(drops.size() == 1 && li.cil.oc.core.impl.common.block.ChameliumBlock.readDyeColor(drops.get(0)) == DyeColor.CYAN,
      "a cyan chamelium block drops " + drops + (drops.isEmpty() ? "" : " coloured " + li.cil.oc.core.impl.common.block.ChameliumBlock.readDyeColor(drops.get(0))));
    helper.succeed();
  }

  /** A robot placed in creative is a copy: its parts get their own addresses (every placed robot shared them). */
  @GameTest(template = EMPTY)
  public static void creativeRobotPlacementCopies(final GameTestHelper helper) {
    final var data = new li.cil.oc.core.impl.common.item.data.RobotData();
    data.name = "copy";
    data.tier = 0;
    final ItemStack hdd = item(Constants.ItemName.HDDTier1);
    final CompoundTag node = new CompoundTag();
    node.putString("address", "robot-hdd-address");
    Item.getDataTag(hdd).put("node", node);
    data.components.add(item(Constants.ItemName.CPUTier1));
    data.components.add(hdd);
    final ItemStack robot = data.createItemStack();
    helper.setBlock(AT.below(), net.minecraft.world.level.block.Blocks.STONE);
    final var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "oc-creative"));
    player.setGameMode(GameType.CREATIVE);
    player.setItemInHand(InteractionHand.MAIN_HAND, robot);
    final BlockPos below = helper.absolutePos(AT.below());
    robot.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(below).add(0, 0.5, 0), Direction.UP, below, false)));
    final BlockEntity placed = helper.getBlockEntity(AT);
    helper.assertTrue(placed instanceof li.cil.oc.core.impl.common.blockentity.RobotProxy, "no robot was placed: " + placed);
    final var parts = ((li.cil.oc.core.impl.common.blockentity.RobotProxy) placed).info.components;
    helper.assertTrue(parts.stream().noneMatch(s -> "robot-hdd-address".equals(Item.address(s))), "the placed robot's HDD has the item's address");
    helper.succeed();
  }

  /** An experienced robot's tooltip has its level line (it looked for the xp in the wrong place). */
  @GameTest(template = EMPTY)
  public static void robotTooltipShowsItsLevel(final GameTestHelper helper) {
    final java.util.function.Function<Boolean, Integer> lines = experienced -> {
      final var data = new li.cil.oc.core.impl.common.item.data.RobotData();
      data.name = "level";
      final ItemStack upgrade = new ItemStack(Items.UPGRADE_EXPERIENCE.get());
      if (experienced) Item.getDataTag(upgrade).putDouble(NS + "xp", 1e5);
      data.components.add(upgrade);
      final List<Component> tooltip = new ArrayList<>();
      Blocks.ROBOT.get().addInformation(0, data.createItemStack(), tooltip, false);
      return tooltip.size();
    };
    final int plain = lines.apply(false);
    final int experienced = lines.apply(true);
    helper.assertTrue(experienced > plain, "an experienced robot's tooltip has " + experienced + " lines, an unexperienced one " + plain);
    helper.succeed();
  }

  /** Drones show their tier's rarity (all were common). */
  @GameTest(template = EMPTY)
  public static void droneItemsHaveTheirTiersRarity(final GameTestHelper helper) {
    final var inventory = new SimpleContainer(20);
    inventory.setItem(0, item(Constants.ItemName.DroneCaseTier2));
    inventory.setItem(1, item(Constants.ItemName.CPUTier1));
    final ItemStack drone = (ItemStack) li.cil.oc.core.impl.common.template.DroneTemplate.assemble(inventory)[0];
    final var expected = li.cil.oc.core.impl.util.Rarity.byTier(new li.cil.oc.core.impl.common.item.data.DroneData(drone).tier);
    helper.assertTrue(expected != net.minecraft.world.item.Rarity.COMMON && drone.getRarity() == expected, "a tier 2 drone is " + drone.getRarity());
    helper.succeed();
  }

  /** Presents and upgrades stack again; floppies, drones and wrenches stay single (the wrench made everything after it single). */
  @GameTest(template = EMPTY)
  public static void itemStackSizes(final GameTestHelper helper) {
    helper.assertTrue(Items.PRESENT.get().getMaxStackSize() == 64 && Items.UPGRADE_INVENTORY.get().getMaxStackSize() == 64,
      "presents stack to " + Items.PRESENT.get().getMaxStackSize() + ", inventory upgrades to " + Items.UPGRADE_INVENTORY.get().getMaxStackSize());
    helper.assertTrue(Items.FLOPPY.get().getMaxStackSize() == 1 && Items.WRENCH.get().getMaxStackSize() == 1,
      "floppies stack to " + Items.FLOPPY.get().getMaxStackSize() + ", wrenches to " + Items.WRENCH.get().getMaxStackSize());
    helper.succeed();
  }

  /** The handheld disk drive opens its GUI (the check looked for the core class; the item is the forge one). */
  @GameTest(template = EMPTY)
  public static void handheldDiskDriveOpens(final GameTestHelper helper) {
    final var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "oc-drive"));
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DISK_DRIVE_MOUNTABLE.get()));
    final var menu = li.cil.oc.neoforge.common.GuiHandler.getServerGuiElement(0, li.cil.oc.core.common.GuiType.DiskDriveMountable, player, helper.getLevel(), 0, 0, 0, null);
    helper.assertTrue(menu != null, "the handheld disk drive has no menu");
    helper.succeed();
  }

  /** The tank upgrade's tooltip shows its fluid (it read a key the tank never writes). */
  @GameTest(template = EMPTY)
  public static void tankUpgradeTooltipShowsItsFluid(final GameTestHelper helper) {
    final ItemStack tank = new ItemStack(Items.UPGRADE_TANK.get());
    final CompoundTag fluid = new CompoundTag();
    fluid.putString("id", "minecraft:water");
    fluid.putInt("Amount", 1000);
    Item.getDataTag(tank).put("fluid", fluid);
    final List<Component> tooltip = new ArrayList<>();
    tank.getItem().appendHoverText(tank, helper.getLevel(), tooltip, TooltipFlag.NORMAL);
    helper.assertTrue(tooltip.stream().anyMatch(c -> c.getString().contains("1000/16000")), "the tank tooltip is " + tooltip.stream().map(Component::getString).toList());
    helper.succeed();
  }

  /** Hovering an experience upgrade does not give it a tag (it no longer stacked with others). */
  @GameTest(template = EMPTY)
  public static void experienceTooltipLeavesTheItemAlone(final GameTestHelper helper) {
    final ItemStack upgrade = new ItemStack(Items.UPGRADE_EXPERIENCE.get());
    upgrade.getItem().appendHoverText(upgrade, null, new ArrayList<>(), TooltipFlag.NORMAL);
    helper.assertTrue(!upgrade.hasTag(), "the hovered experience upgrade got the tag " + upgrade.getTag());
    helper.succeed();
  }

  // ---- nanomachines ----

  private static li.cil.oc.api.network.WirelessEndpoint at(final Player player) {
    return new li.cil.oc.api.network.WirelessEndpoint() {
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
      }
    };
  }

  private static li.cil.oc.api.network.Packet command(final Object... args) {
    final Object[] data = new Object[args.length + 1];
    data[0] = "nanomachines".getBytes(StandardCharsets.UTF_8);
    for (int i = 0; i < args.length; i++) data[i + 1] = args[i] instanceof String s ? s.getBytes(StandardCharsets.UTF_8) : args[i];
    return li.cil.oc.api.Network.newPacket("oc-test", null, 1, data);
  }

  /** saveConfiguration writes the configuration into a blank nanomachines item (it always answered "error"). */
  @GameTest(template = EMPTY)
  public static void nanomachinesSaveTheirConfiguration(final GameTestHelper helper) {
    final var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "oc-nano"));
    player.getInventory().clearContent();
    player.getInventory().add(new ItemStack(Items.NANOMACHINES.get()));
    final var controller = new li.cil.oc.core.impl.common.nanomachines.ControllerImpl(player);
    try {
      controller.reconfigure();
      controller.changeBuffer(1000);
      controller.receivePacket(command("saveConfiguration"), at(player));
      boolean saved = false;
      for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
        final ItemStack stack = player.getInventory().getItem(i);
        if (stack.is(Items.NANOMACHINES.get()) && new li.cil.oc.core.impl.common.item.data.NanomachineData(stack).configuration != null) saved = true;
      }
      helper.assertTrue(saved, "no nanomachines item holds the configuration");
    } finally {
      li.cil.oc.api.Network.leaveWirelessNetwork(controller);
      player.getInventory().clearContent();
    }
    helper.succeed();
  }

  /** A command that is not a string is ignored (it threw out of the sender's broadcast). */
  @GameTest(template = EMPTY)
  public static void nanomachinesIgnoreMalformedCommands(final GameTestHelper helper) {
    final var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "oc-nano"));
    final var controller = new li.cil.oc.core.impl.common.nanomachines.ControllerImpl(player);
    try {
      controller.changeBuffer(1000);
      controller.receivePacket(command(5), at(player));
    } catch (Throwable t) {
      helper.fail("a malformed nanomachines command threw " + t);
    } finally {
      li.cil.oc.api.Network.leaveWirelessNetwork(controller);
    }
    helper.succeed();
  }

  /** Nanomachine damage is a registered damage type (an unregistered one went to clients as id -1). */
  @GameTest(template = EMPTY)
  public static void nanomachineDamageIsRegistered(final GameTestHelper helper) {
    final var registry = helper.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
    final var overload = li.cil.oc.core.impl.integration.util.DamageSourceWithRandomCause.overload(helper.getLevel());
    final var hungry = li.cil.oc.core.impl.integration.util.DamageSourceWithRandomCause.hungry(helper.getLevel());
    helper.assertTrue(registry.getId(overload.type()) >= 0 && registry.getId(hungry.type()) >= 0
        && overload.typeHolder().unwrapKey().map(k -> k.location().getPath().equals("nanomachines_overload")).orElse(false),
      "nanomachine damage types: overload " + registry.getId(overload.type()) + ", hungry " + registry.getId(hungry.type()));
    helper.succeed();
  }

  /** A component save that throws leaves the item's data as it was (it was cleared first: a disk lost its address). */
  @GameTest(template = EMPTY)
  public static void aFailingComponentSaveKeepsTheItemsData(final GameTestHelper helper) {
    final ItemStack hdd = item(Constants.ItemName.HDDTier1);
    final CompoundTag node = new CompoundTag();
    node.putString("address", "keep-me");
    Item.getDataTag(hdd).put("node", node);
    final var failing = new li.cil.oc.api.prefab.AbstractManagedEnvironment() {
      @Override
      public void save(CompoundTag nbt, net.minecraft.core.HolderLookup.Provider provider) {
        nbt.putString("partial", "x");
        throw new IllegalStateException("save failed");
      }
    };
    host(helper).save(failing, li.cil.oc.api.API.driver.driverFor(hdd), hdd, helper.getLevel().registryAccess());
    helper.assertTrue("keep-me".equals(Item.address(hdd)), "after a failing save the disk's data is " + Item.peekDataTag(hdd));
    helper.succeed();
  }

  /** database.set with a tag keeps the damage given (the tag replaced it: a damaged tool was stored as a new one). */
  @GameTest(template = EMPTY)
  public static void databaseSetKeepsTheDamageWithATag(final GameTestHelper helper) {
    final var entries = new SimpleContainer(9);
    final var database = new li.cil.oc.core.impl.server.component.UpgradeDatabase(entries);
    database.set(context(), new ArgumentsImpl(new Object[]{1, "minecraft:iron_pickaxe", 100, "{display:{Name:'\"x\"'}}"}));
    final ItemStack stored = entries.getItem(0);
    helper.assertTrue(stored.getDamageValue() == 100 && stored.hasCustomHoverName(), "database.set stored " + stored + " damaged " + stored.getDamageValue() + " " + stored.getTag());
    helper.succeed();
  }

  /** A tier 1 tablet assembled before the container fix gives its first part back once (twice: the container copy). */
  @GameTest(template = EMPTY)
  public static void oldTier1TabletsGiveTheirPartsOnce(final GameTestHelper helper) {
    final var data = new TabletData();
    data.tier = li.cil.oc.core.common.Tier.One;
    data.container = item(Constants.ItemName.RedstoneCardTier1);
    data.items = new ArrayList<>(Collections.nCopies(32, null));
    data.items.set(0, item(Constants.BlockName.ScreenTier1));
    data.items.set(1, item(Constants.ItemName.RedstoneCardTier1));
    final ItemStack tablet = data.createItemStack();
    final Object[] parts = li.cil.oc.core.impl.common.template.TabletTemplate.disassemble(tablet, new ItemStack[0]);
    int cards = 0;
    for (final Object part : parts) {
      if (part instanceof ItemStack s && !s.isEmpty() && li.cil.oc.api.Items.get(s) == li.cil.oc.api.Items.get(Constants.ItemName.RedstoneCardTier1)) cards += s.getCount();
    }
    helper.assertTrue(cards == 1, "an old tier 1 tablet disassembled into " + cards + " redstone cards");
    helper.succeed();
  }

  /** A RAID's label cleared to nil stays cleared through a save (it came back as "raid"). */
  @GameTest(template = EMPTY)
  public static void raidLabelClearedStaysCleared(final GameTestHelper helper) {
    final var label = new li.cil.oc.core.impl.common.blockentity.Raid.RaidLabel();
    label.setLabel(null);
    final CompoundTag tag = new CompoundTag();
    label.save(tag, helper.getLevel().registryAccess());
    final var loaded = new li.cil.oc.core.impl.common.blockentity.Raid.RaidLabel();
    loaded.load(tag, helper.getLevel().registryAccess());
    helper.assertTrue(loaded.getLabel() == null, "a cleared RAID label came back as " + loaded.getLabel());
    helper.succeed();
  }
}
