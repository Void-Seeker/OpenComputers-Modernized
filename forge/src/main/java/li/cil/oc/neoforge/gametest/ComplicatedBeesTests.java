package li.cil.oc.neoforge.gametest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import li.cil.oc.core.Constants;
import li.cil.oc.neoforge.OpenComputers;
import li.cil.oc.neoforge.integration.complicatedbees.ConverterBee;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * The Complicated Bees converter, on bee NBT written the way the mod (3.4.6) writes it. The mod is not in the test
 * run, so marked paper stands in for its bee items.
 */
@GameTestHolder(OpenComputers.ID)
@PrefixGameTestTemplate(false)
public final class ComplicatedBeesTests {
  private static final String EMPTY = "empty";
  private static final String MARK = "oc_test_bee";

  private ComplicatedBeesTests() {
  }

  private static CompoundTag gene(final Tag data, final @Nullable Boolean dominant) {
    final CompoundTag gene = new CompoundTag();
    gene.put("data", data);
    if (dominant != null) gene.putBoolean("dominant", dominant);
    return gene;
  }

  private static CompoundTag chromosome(final String species, final boolean speciesDominant, final String lifespan, final int fertility, final boolean weatherproof) {
    final CompoundTag c = new CompoundTag();
    c.put("complicated_bees:species", gene(StringTag.valueOf(species), speciesDominant));
    c.put("complicated_bees:lifespan", gene(StringTag.valueOf(lifespan), true));
    c.put("complicated_bees:fertility", gene(IntTag.valueOf(fertility), true));
    final CompoundTag temperature = gene(StringTag.valueOf("normal"), true);
    temperature.putString("tolerance", "both_1");
    c.put("complicated_bees:temperature", temperature);
    c.put("complicated_bees:cave_dwelling", gene(ByteTag.valueOf(false), false));
    c.put("complicated_bees:weatherproof", gene(ByteTag.valueOf(weatherproof), false));
    c.put("complicated_bees:territory", gene(new IntArrayTag(new int[]{4, 2}), null)); // no flag: dominant
    c.put("otheraddon:shine", gene(StringTag.valueOf("glow"), true));
    return c;
  }

  /** A hybrid: forest (dominant) over meadows (recessive). */
  private static CompoundTag hybrid(final boolean analyzed) {
    final CompoundTag tag = new CompoundTag();
    tag.put("chromosome_a", chromosome("complicated_bees:forest", true, "average", 2, false));
    tag.put("chromosome_b", chromosome("complicated_bees:meadows", false, "short", 3, true));
    tag.putString("species", "complicated_bees:forest");
    if (analyzed) tag.putBoolean("analyzed", true);
    return tag;
  }

  private static ItemStack marked(final String type, final CompoundTag tag) {
    final ItemStack stack = new ItemStack(Items.PAPER);
    final CompoundTag copy = tag.copy();
    copy.putString(MARK, type);
    stack.setTag(copy);
    return stack;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(final Object value) {
    return (Map<String, Object>) value;
  }

  @GameTest(template = EMPTY)
  public static void beeConverterShowsGenomesOfAnalyzedBeesOnly(final GameTestHelper helper) {
    final Map<String, Object> bee = ConverterBee.describe("princess", hybrid(true));
    helper.assertTrue(bee != null, "no bee table");
    helper.assertTrue("princess".equals(bee.get("type")) && Boolean.TRUE.equals(bee.get("analyzed")) && "complicated_bees:forest".equals(bee.get("species")), "bee " + bee);
    final Map<String, Object> active = map(bee.get("active")), inactive = map(bee.get("inactive"));
    final Map<String, Object> dominant = map(bee.get("dominant"));
    helper.assertTrue("complicated_bees:forest".equals(active.get("species")) && "complicated_bees:meadows".equals(inactive.get("species")), "species " + active + " / " + inactive);
    helper.assertTrue(Boolean.TRUE.equals(map(dominant.get("active")).get("species")) && Boolean.FALSE.equals(map(dominant.get("inactive")).get("species")), "dominance " + dominant);
    helper.assertTrue(Integer.valueOf(2).equals(active.get("fertility")) && "short".equals(inactive.get("lifespan")), "values " + active + " / " + inactive);
    helper.assertTrue("normal".equals(active.get("temperature")) && "both_1".equals(active.get("temperature_tolerance")), "tolerance " + active);
    helper.assertTrue(Boolean.FALSE.equals(active.get("weatherproof")) && Boolean.TRUE.equals(inactive.get("weatherproof")), "yes/no genes " + active + " / " + inactive);
    helper.assertTrue(Map.of("width", 9, "height", 5).equals(active.get("territory")), "territory " + active.get("territory"));
    helper.assertTrue(Boolean.TRUE.equals(map(dominant.get("active")).get("territory")), "a gene without the flag is not dominant");
    helper.assertTrue("glow".equals(active.get("otheraddon:shine")), "an addon's gene " + active);

    // not analyzed: only what the bee's name shows
    final Map<String, Object> drone = ConverterBee.describe("drone", hybrid(false));
    helper.assertTrue(drone != null && drone.keySet().equals(java.util.Set.of("type", "analyzed", "species")) && Boolean.FALSE.equals(drone.get("analyzed")),
      "an unanalyzed bee shows " + drone);

    // a queen shows her mate once analyzed
    final CompoundTag queen = hybrid(true);
    final CompoundTag mate = new CompoundTag();
    mate.put("chromosome_a", chromosome("complicated_bees:meadows", true, "long", 1, false));
    mate.put("chromosome_b", chromosome("complicated_bees:meadows", true, "long", 1, false));
    queen.put("mate", mate);
    final Map<String, Object> queenBee = ConverterBee.describe("queen", queen);
    helper.assertTrue(queenBee != null && "complicated_bees:meadows".equals(map(map(queenBee.get("mate")).get("active")).get("species")), "queen " + queenBee);

    helper.assertTrue(ConverterBee.describe("princess", new CompoundTag()) == null, "a tag without genome made a bee");
    final Map<Object, Object> out = new HashMap<>();
    final ItemStack paper = marked("princess", hybrid(true));
    new ConverterBee().convert(paper, out);
    helper.assertTrue(out.isEmpty(), "paper is no Complicated Bees bee, but got " + out);
    helper.succeed();
  }

  @GameTest(template = EMPTY, timeoutTicks = 10_000_000)
  public static void transposerReadsBeeGenomes(final GameTestHelper helper) throws ReflectiveOperationException {
    // the integration only loads with the mod; register a converter for marked paper for this test (until its end,
    // passed or failed: the game tests ship in the jar)
    final var field = li.cil.oc.core.impl.server.driver.Registry.class.getDeclaredField("converters");
    field.setAccessible(true);
    @SuppressWarnings("unchecked") final List<li.cil.oc.api.driver.Converter> converters = (List<li.cil.oc.api.driver.Converter>) field.get(li.cil.oc.api.API.driver);
    final ConverterBee converter = new ConverterBee(stack -> stack.is(Items.PAPER) && stack.getTag() != null && stack.getTag().contains(MARK) ? stack.getTag().getString(MARK) : null);
    converters.add(converter);
    TestMachines.atEnd(helper, () -> converters.remove(converter));

    final BlockPos pos = new BlockPos(2, 2, 2), chest = pos.north();
    helper.setBlock(chest, net.minecraft.world.level.block.Blocks.CHEST);
    final Container box = (Container) helper.getBlockEntity(chest);
    box.setItem(0, marked("princess", hybrid(true)));
    box.setItem(1, marked("drone", hybrid(false)));
    box.setItem(2, new ItemStack(Items.PAPER));
    final String program = "local tp = component.proxy(component.list(\"transposer\")())\n"
      + "local function expect(what, want, got) if got ~= want then error(what .. \": \" .. tostring(got), 0) end end\n"
      + "local p = tp.getStackInSlot(2, 1).bee\n"
      + "expect(\"type\", \"princess\", p.type)\n"
      + "expect(\"analyzed\", true, p.analyzed)\n"
      + "expect(\"active species\", \"complicated_bees:forest\", p.active.species)\n"
      + "expect(\"inactive species\", \"complicated_bees:meadows\", p.inactive.species)\n"
      + "expect(\"recessive\", false, p.dominant.inactive.species)\n"
      + "expect(\"fertility\", 2, p.active.fertility)\n"
      + "expect(\"tolerance\", \"both_1\", p.active.temperature_tolerance)\n"
      + "expect(\"weatherproof\", true, p.inactive.weatherproof)\n"
      + "expect(\"territory\", 9, p.active.territory.width)\n"
      + "local d = tp.getStackInSlot(2, 2).bee\n"
      + "expect(\"drone species\", \"complicated_bees:forest\", d.species)\n"
      + "expect(\"unanalyzed genes\", nil, d.active)\n"
      + "expect(\"plain paper\", nil, tp.getStackInSlot(2, 3).bee)\n"
      + "error(\"done:all\", 0)\n"; // every step passed
    TestMachines.runMicrocontroller(helper, pos, List.of(Constants.ItemName.CPUTier1, Constants.ItemName.RAMTier2, Constants.BlockName.Transposer), program, mc -> {
      helper.assertTrue(String.valueOf(mc.machine().lastError()).contains("done:all"), "the program did not get through: " + mc.machine().lastError());
      helper.succeed();
    });
  }
}
