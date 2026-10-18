package li.cil.oc.core.impl.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * A robot's inventory as what its fake player carries during a tool tick or an action: copies go in, and what the
 * holder changed comes back - an item whose data changed (an ember jar or a mana tablet drawn on) in place, without
 * inventory_changed (like a tool's wear), anything else replaced. Afterwards the holder carries what it carried
 * before, so nothing can be drawn on for free later.
 */
public final class CarriedInventory {
  /**
   * Botania's mana items (a mana tablet): their mana is an int "mana" in their tag.
   */
  public static final Predicate<ItemStack> BOTANIA_MANA = stack ->
    "botania".equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace())
      && stack.getTag() != null && stack.getTag().contains("mana", Tag.TAG_INT);

  /**
   * The mana items robots get their wear discount on (game tests point it at a stand-in: Botania is not loaded there).
   */
  public static Predicate<ItemStack> manaItems = BOTANIA_MANA;

  private final Container robot;
  private final NonNullList<ItemStack> carried;
  private final int offset, skip, n;
  private final ItemStack[] snapshot, previously;

  /**
   * @param offset where in the carried list robot slot 0 goes (1 when the tool takes the first place)
   * @param skip   a robot slot to leave out (the one an action is using), or -1
   */
  private CarriedInventory(Container robot, NonNullList<ItemStack> carried, int offset, int skip) {
    this.robot = robot;
    this.carried = carried;
    this.offset = offset;
    this.skip = skip;
    this.n = Math.max(0, Math.min(carried.size() - offset, robot.getContainerSize()));
    this.snapshot = new ItemStack[n];
    this.previously = new ItemStack[carried.size()];
    for (int i = 0; i < carried.size(); i++) previously[i] = carried.get(i);
    for (int i = 0; i < n; i++) {
      if (i == skip) continue;
      snapshot[i] = robot.getItem(i).copy();
      carried.set(i + offset, snapshot[i].copy());
    }
  }

  public static CarriedInventory carry(Container robot, NonNullList<ItemStack> carried, int offset, int skip) {
    return new CarriedInventory(robot, carried, offset, skip);
  }

  /**
   * Writes what the holder changed back into the robot. Of the mana a mana item lost only the share {@code manaKept}
   * is spent (rounded at random, so it averages out), the rest goes back into the item. A slot the robot changed
   * itself in the meantime is left as the robot has it. Returns whether something changed in place (that needs the
   * robot saved; replacing an item does that by itself).
   */
  public boolean bringBack(double manaKept, Predicate<ItemStack> isManaItem, RandomSource random) {
    boolean changed = false;
    for (int i = 0; i < n; i++) {
      if (i == skip) continue;
      ItemStack now = carried.get(i + offset), before = snapshot[i], had = robot.getItem(i);
      if (ItemStack.matches(now, before) || !ItemStack.matches(had, before)) continue;
      if (!before.isEmpty() && ItemStack.isSameItem(now, before) && now.getCount() == before.getCount()) {
        CompoundTag tag = now.getTag() == null ? null : now.getTag().copy();
        if (manaKept < 1 && tag != null && isManaItem.test(before)) {
          int was = before.getTag().getInt("mana"), is = tag.getInt("mana");
          if (is < was) {
            double spent = (was - is) * Math.max(0, manaKept);
            int whole = (int) Math.floor(spent);
            tag.putInt("mana", was - whole - (random.nextDouble() < spent - whole ? 1 : 0));
          }
        }
        had.setTag(tag);
        changed = true;
      } else {
        robot.setItem(i, now.copy());
      }
    }
    return changed;
  }

  /**
   * What the holder got where nothing of the robot's is carried - past the robot's slots, or in the slot left out -
   * as vanilla code gives a player items (a bucket of milk, an empty bottle): taken out, for the robot to get.
   */
  public List<ItemStack> extras() {
    var result = new ArrayList<ItemStack>();
    for (int i = offset; i < carried.size(); i++) {
      int slot = i - offset;
      if (slot < n && slot != skip) continue;
      ItemStack now = carried.get(i);
      if (!now.isEmpty() && !ItemStack.matches(now, previously[i])) {
        result.add(now);
        carried.set(i, previously[i]);
      }
    }
    return result;
  }

  /**
   * Runs f with the agent's inventory carried by its player (in the player's own slots), for vanilla code that gives a
   * player items, takes them or reads its inventory; afterwards what changed goes back into the agent, and what the
   * player got anywhere else goes into the agent or, when it is full, onto the ground.
   */
  public static <T> T withAgentInventory(li.cil.oc.api.internal.Agent agent, Player player, Supplier<T> f) {
    var carried = carry(agent.mainInventory(), player.getInventory().items, 0, -1);
    try {
      return f.get();
    } finally {
      if (carried.bringBack(1, manaItems, player.getRandom())) agent.mainInventory().setChanged();
      for (ItemStack extra : carried.extras()) giveToAgent(agent, extra);
      carried.restore();
    }
  }

  /**
   * Moves what it can of the stack into the agent's inventory as a robot picks things up: the selected slot first,
   * onto matching stacks before empty slots. The stack keeps what did not fit; whether anything went in.
   */
  public static boolean insertIntoAgent(li.cil.oc.api.internal.Agent agent, ItemStack stack) {
    if (stack.isEmpty()) return false;
    Container inventory = agent.mainInventory();
    int size = inventory.getContainerSize(), selected = Math.max(0, agent.selectedSlot()), before = stack.getCount();
    for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
      for (int k = 0; k < size && !stack.isEmpty(); k++) {
        int slot = (selected + k) % size;
        ItemStack there = inventory.getItem(slot);
        int max = Math.min(stack.getMaxStackSize(), inventory.getMaxStackSize());
        if (pass == 0 && !there.isEmpty() && ItemStack.isSameItemSameTags(there, stack) && there.getCount() < max) {
          int moved = Math.min(stack.getCount(), max - there.getCount());
          there.grow(moved);
          inventory.setItem(slot, there);
          stack.shrink(moved);
        } else if (pass == 1 && there.isEmpty() && inventory.canPlaceItem(slot, stack)) {
          inventory.setItem(slot, stack.split(Math.min(stack.getCount(), max)));
        }
      }
    }
    return stack.getCount() < before;
  }

  /**
   * Gives the agent the stack: into its inventory, what does not fit onto the ground at it.
   */
  public static void giveToAgent(li.cil.oc.api.internal.Agent agent, ItemStack stack) {
    insertIntoAgent(agent, stack);
    if (!stack.isEmpty()) InventoryUtils.spawnStackInWorld(BlockPosition.apply(agent), stack, null, null);
  }

  /**
   * The holder carries what it carried before.
   */
  public void restore() {
    for (int i = 0; i < previously.length; i++) carried.set(i, previously[i]);
  }
}
