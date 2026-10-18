package li.cil.oc.neoforge.gametest;

import li.cil.oc.core.Constants;
import li.cil.oc.core.impl.common.template.ServerTemplate;
import li.cil.oc.core.impl.common.template.TabletTemplate;
import li.cil.oc.neoforge.OpenComputers;
import li.cil.oc.neoforge.common.init.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Assembling and disassembling gives back what went in - no more, no less - and nothing it handles breaks saving.
 */
@GameTestHolder(OpenComputers.ID)
@PrefixGameTestTemplate(false)
public final class AssemblyTests {
  private static final String EMPTY = "empty";
  private static final BlockPos AT = new BlockPos(2, 2, 2);

  private AssemblyTests() {
  }

  private static ItemStack item(final String name) {
    return li.cil.oc.api.Items.get(name).createItemStack(1);
  }

  private static boolean is(final ItemStack stack, final String name) {
    return !stack.isEmpty() && li.cil.oc.api.Items.get(stack) == li.cil.oc.api.Items.get(name);
  }

  private static int count(final Object[] parts, final String name) {
    int n = 0;
    for (final Object part : parts) {
      if (part instanceof ItemStack s && is(s, name)) n += s.getCount();
    }
    return n;
  }

  /** A tier 1 tablet gives its card back once (twice before), a tier 2 one keeps its first part installed. */
  @GameTest(template = EMPTY)
  public static void tabletAssemblyKeepsEveryPartOnce(final GameTestHelper helper) {
    for (final String caseName : new String[]{Constants.ItemName.TabletCaseTier1, Constants.ItemName.TabletCaseTier2}) {
      final var inventory = new SimpleContainer(22);
      inventory.setItem(0, item(caseName));
      inventory.setItem(13, item(Constants.ItemName.RedstoneCardTier1));
      inventory.setItem(16, item(Constants.ItemName.CPUTier1));
      inventory.setItem(17, item(Constants.ItemName.RAMTier1));
      final Object[] assembled = TabletTemplate.assemble(inventory);
      final ItemStack tablet = (ItemStack) assembled[0];
      final var data = new li.cil.oc.core.impl.common.item.data.TabletData(tablet);
      final long installedCards = data.items.stream().filter(s -> s != null && is(s, Constants.ItemName.RedstoneCardTier1)).count();
      helper.assertTrue(data.container == null && installedCards == 1,
        caseName + ": container " + data.container + ", the card installed " + installedCards + " times");
      final Object[] parts = TabletTemplate.disassemble(tablet, new ItemStack[0]);
      helper.assertTrue(count(parts, Constants.ItemName.RedstoneCardTier1) == 1 && count(parts, Constants.ItemName.CPUTier1) == 1,
        caseName + " disassembled into " + count(parts, Constants.ItemName.RedstoneCardTier1) + " cards, " + count(parts, Constants.ItemName.CPUTier1) + " CPUs");
    }
    helper.succeed();
  }

  /** Disassembling a server gives its parts (it threw, and the server was gone with them). */
  @GameTest(template = EMPTY)
  public static void serverDisassemblyGivesItsParts(final GameTestHelper helper) {
    final ItemStack server = item(Constants.ItemName.ServerTier1);
    final var inventory = new li.cil.oc.core.impl.common.inventory.ItemServerInventory(server);
    inventory.setItem(0, item(Constants.ItemName.CPUTier1));
    inventory.setChanged(null);
    final Object[] result = ServerTemplate.disassemble(server, new ItemStack[0]);
    final Object[] drops = (Object[]) result[1];
    helper.assertTrue(drops != null && count(drops, Constants.ItemName.CPUTier1) == 1, "a server with a CPU disassembled into " + java.util.Arrays.toString(drops));
    helper.succeed();
  }

  /** One transistor (a recipe makes 8) is not worth the whole recipe; no recipe makes an item from itself. */
  @GameTest(template = EMPTY)
  public static void disassembledItemsGiveTheirShareOfTheRecipe(final GameTestHelper helper) {
    final ItemStack[] transistor = li.cil.oc.core.impl.util.ItemUtils.getIngredients(item(Constants.ItemName.Transistor));
    int parts = 0;
    for (final ItemStack s : transistor) parts += s.getCount();
    helper.assertTrue(parts == 0, "one transistor disassembles into " + parts + " items: " + java.util.Arrays.toString(transistor));
    final ItemStack[] eeprom = li.cil.oc.core.impl.util.ItemUtils.getIngredients(item(Constants.ItemName.EEPROM));
    helper.assertTrue(java.util.Arrays.stream(eeprom).noneMatch(s -> is(s, Constants.ItemName.EEPROM)), "an EEPROM disassembles into an EEPROM: " + java.util.Arrays.toString(eeprom));
    helper.succeed();
  }

  /** A disassembler working on a microcontroller without EEPROM still saves (a null part threw: the chunk did not save). */
  @GameTest(template = EMPTY)
  public static void disassemblerWithAMicrocontrollerWithoutEepromSaves(final GameTestHelper helper) {
    final var data = new li.cil.oc.core.impl.common.item.data.MicrocontrollerData();
    data.components.add(item(Constants.ItemName.CPUTier1));
    data.components.add(item(Constants.ItemName.RAMTier1));
    final ItemStack microcontroller = data.createItemStack();
    final var reloaded = new li.cil.oc.core.impl.common.item.data.MicrocontrollerData(microcontroller);
    helper.assertTrue(reloaded.components.stream().noneMatch(java.util.Objects::isNull), "a microcontroller without EEPROM loads a null part");
    helper.setBlock(AT, Blocks.DISASSEMBLER.get());
    final var disassembler = (li.cil.oc.core.impl.common.blockentity.Disassembler) helper.getBlockEntity(AT);
    disassembler.disassemble(microcontroller, false);
    try {
      disassembler.saveWithFullMetadata();
    } catch (Throwable t) {
      helper.fail("the disassembler did not save: " + t);
    }
    helper.succeed();
  }

  /** A tier 3 robot's second HDD slot (the 9th component slot) takes an HDD (it took nothing). */
  @GameTest(template = EMPTY)
  public static void assemblerFillsTheNinthComponentSlot(final GameTestHelper helper) {
    helper.setBlock(AT, Blocks.ASSEMBLER.get());
    final var assembler = (li.cil.oc.core.impl.common.blockentity.Assembler) helper.getBlockEntity(AT);
    assembler.setItem(0, item(Constants.BlockName.CaseTier3));
    helper.assertTrue(assembler.canPlaceItem(21, item(Constants.ItemName.HDDTier2)), "the 9th component slot of a tier 3 case refuses a tier 2 HDD");
    helper.succeed();
  }

  /** What is written into an item's data stays in the item (a copy was handed out: linked cards lost their tunnel). */
  @GameTest(template = EMPTY)
  public static void itemDataIsTheItems(final GameTestHelper helper) {
    final ItemStack card = item(Constants.ItemName.LinkedCard);
    card.getOrCreateTag().putString("unrelated", "x");
    li.cil.oc.core.impl.integration.opencomputers.Item.getDataTag(card).putString(li.cil.oc.core.impl.OCSettings.namespace + "tunnel", "pair-7");
    final var driver = li.cil.oc.api.API.driver.driverFor(card);
    helper.assertTrue(driver != null && "pair-7".equals(driver.dataTag(card).getString(li.cil.oc.core.impl.OCSettings.namespace + "tunnel")),
      "the tunnel written into the card's data is not in the card: " + card.getTag());
    helper.succeed();
  }
}
