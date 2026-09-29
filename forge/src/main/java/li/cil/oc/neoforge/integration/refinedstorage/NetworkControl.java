package li.cil.oc.neoforge.integration.refinedstorage;

import com.refinedmods.refinedstorage.api.autocrafting.ICraftingManager;
import com.refinedmods.refinedstorage.api.autocrafting.ICraftingPattern;
import com.refinedmods.refinedstorage.api.autocrafting.task.ICraftingTask;
import com.refinedmods.refinedstorage.api.network.INetwork;
import com.refinedmods.refinedstorage.api.util.StackListEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import li.cil.oc.api.Persistable;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.prefab.AbstractValue;
import li.cil.oc.core.impl.server.driver.Registry;
import li.cil.oc.core.impl.util.DatabaseAccess;
import li.cil.oc.core.impl.util.SideTracker;
import li.cil.oc.core.util.ResultWrapper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * The {@code rs_controller} component API on top of Refined Storage 1.x networks.
 */
public interface NetworkControl extends Persistable, ManagedEnvironment {
  BlockEntity tile();

  @SuppressWarnings("EmptyMethod")
  Node node();

  default @Nullable INetwork network() {
    return RSUtil.networkOf(tile());
  }

  default @Nullable ICraftingManager crafting() {
    var network = network();
    return network != null ? network.getCraftingManager() : null;
  }

  default HashMap<Object, Object> convert(ItemStack stack, long size, boolean isCraftable, @Nullable ICraftingManager crafting) {
    var hash = new HashMap<>();
    if (stack == null || stack.isEmpty()) return hash;
    long potentialAmount = size;
    if (size <= 0 && isCraftable && crafting != null) {
      potentialAmount = getPatternOutputAmount(stack, crafting);
    }
    var itemStack = stack.copy();
    itemStack.setCount((int) Math.max(1, Math.min(potentialAmount, Integer.MAX_VALUE)));
    var converted = Registry.INSTANCE.convertRecursively(itemStack, new java.util.IdentityHashMap<>());
    if (converted instanceof Map<?, ?> map) {
      for (var entry : map.entrySet()) {
        if (entry.getKey() instanceof String s) {
          hash.put(s, entry.getValue());
        }
      }
    }
    hash.put("isCraftable", isCraftable);
    hash.put("size", size);
    return hash;
  }

  static long getPatternOutputAmount(ItemStack stack, ICraftingManager crafting) {
    ICraftingPattern pattern = crafting.getPattern(stack);
    if (pattern == null) return 0;
    for (var output : pattern.getOutputs()) {
      if (ItemStack.isSameItemSameTags(output, stack)) {
        return output.getCount();
      }
    }
    return 0;
  }

  static boolean isCraftable(ItemStack stack, @Nullable ICraftingManager crafting) {
    return crafting != null && crafting.getPattern(stack) != null;
  }

  @Callback(doc = "function([filter:table]):table -- Get a list of the stored items in the network.")
  default Object[] getItemsInNetwork(Context context, Arguments args) {
    var filter = parseFilter(args);
    var result = new ArrayList<>();
    var network = network();
    if (network == null) return ResultWrapper.result((Object) result.toArray());
    var crafting = network.getCraftingManager();
    for (StackListEntry<ItemStack> entry : network.getItemStorageCache().getList().getStacks()) {
      var stack = entry.getStack();
      var converted = convert(stack, stack.getCount(), isCraftable(stack, crafting), crafting);
      if (matches(converted, filter)) {
        result.add(converted);
      }
    }
    return ResultWrapper.result((Object) result.toArray());
  }

  @Callback(doc = "function():table -- Get a list of the stored fluids in the network.")
  default Object[] getFluidsInNetwork(Context context, Arguments args) {
    var result = new ArrayList<>();
    var network = network();
    if (network == null) return ResultWrapper.result((Object) result.toArray());
    for (StackListEntry<FluidStack> entry : network.getFluidStorageCache().getList().getStacks()) {
      result.add(entry.getStack().copy());
    }
    return ResultWrapper.result((Object) result.toArray());
  }

  @Callback(doc = "function([filter:table]):table -- Get a list of known item recipes. These can be used to issue crafting requests.")
  default Object[] getCraftables(Context context, Arguments args) {
    var filter = parseFilter(args);
    var builder = new ArrayList<>();
    var network = network();
    if (network == null) return ResultWrapper.result((Object) builder.toArray());
    var crafting = network.getCraftingManager();
    for (StackListEntry<ItemStack> entry : network.getItemStorageCache().getCraftablesList().getStacks()) {
      var stack = entry.getStack();
      long patternAmount = getPatternOutputAmount(stack, crafting);
      var converted = convert(stack, 0, true, crafting);
      if (filter.isEmpty() || matches(converted, filter)) {
        builder.add(new NetworkControl.Craftable(tile(), stack, patternAmount));
      }
    }
    return ResultWrapper.result((Object) builder.toArray());
  }

  @Callback(doc = "function([filter:table, dbAddress:string, startSlot:number, count:number]): bool -- Store items in the network matching the specified filter in the database with the specified address.")
  default Object[] store(Context context, Arguments args) {
    var filter = parseFilter(args);
    var database = args.optString(1, null);
    var db = database != null
      ? DatabaseAccess.database(node(), database)
      : DatabaseAccess.databases(node()).stream().findFirst()
      .orElseThrow(() -> new IllegalArgumentException("no database upgrade found"));
    var network = network();
    if (network == null) return ResultWrapper.result(false);
    var crafting = network.getCraftingManager();
    var items = new ArrayList<ItemStack>();
    for (StackListEntry<ItemStack> entry : network.getItemStorageCache().getList().getStacks()) {
      var stack = entry.getStack();
      boolean craftable = isCraftable(stack, crafting);
      if (matches(convert(stack, stack.getCount(), craftable, crafting), filter)) {
        items.add(stack.copy());
      }
    }
    var offset = Math.max(0, args.optInteger(2, 1) - 1);
    int count = args.optInteger(3, Integer.MAX_VALUE);
    count = Math.max(0, Math.min(db.size() - offset, count));
    count = Math.min(count, items.size());
    int slot = offset;
    for (int i = 0; i < count; i++) {
      var itemStack = items.get(i);
      if (itemStack.isEmpty()) continue;
      while (slot < db.size() && !db.getStackInSlot(slot).isEmpty()) slot++;
      if (slot >= db.size()) break;
      db.setStackInSlot(slot, itemStack);
    }
    return ResultWrapper.result(true);
  }

  @Callback(doc = "function():number -- Get the average power usage of the network.")
  default Object[] getAvgPowerUsage(Context context, Arguments args) {
    var network = network();
    return ResultWrapper.result(network != null ? (double) network.getEnergyUsage() : 0.0);
  }

  @Callback(doc = "function():number -- Get the maximum stored power in the network.")
  default Object[] getMaxStoredPower(Context context, Arguments args) {
    var network = network();
    return ResultWrapper.result(network != null ? (double) network.getEnergyStorage().getMaxEnergyStored() : 0.0);
  }

  @Callback(doc = "function():number -- Get the stored power in the network.")
  default Object[] getStoredPower(Context context, Arguments args) {
    var network = network();
    return ResultWrapper.result(network != null ? (double) network.getEnergyStorage().getEnergyStored() : 0.0);
  }

  @Callback(doc = "function():boolean -- True if the RS network is considered online")
  default Object[] isNetworkPowered(Context context, Arguments args) {
    var network = network();
    return ResultWrapper.result(network != null && network.canRun());
  }

  @Callback(doc = "function():table -- Get a list of the crafting tasks in the network.")
  default Object[] getCraftingTasks(Context context, Arguments args) {
    var result = new ArrayList<>();
    var crafting = crafting();
    if (crafting == null) return ResultWrapper.result((Object) result.toArray());
    for (ICraftingTask task : crafting.getTasks()) {
      var map = new LinkedHashMap<>();
      map.put("id", task.getId().toString());
      map.put("quantity", task.getQuantity());
      map.put("state", "RUNNING"); // Refined Storage 1.x only lists running tasks
      map.put("completion", task.getCompletionPercentage());
      var requested = task.getRequested().getItem();
      if (requested != null && !requested.isEmpty()) {
        map.put("resource", convert(requested, task.getQuantity(), true, crafting));
      }
      result.add(map);
    }
    return ResultWrapper.result((Object) result.toArray());
  }

  @Callback(doc = "function():table -- Get a list of the patterns known to the network.")
  default Object[] getPatterns(Context context, Arguments args) {
    var result = new ArrayList<>();
    var crafting = crafting();
    if (crafting == null) return ResultWrapper.result((Object) result.toArray());
    for (ICraftingPattern pattern : crafting.getPatterns()) {
      var map = new LinkedHashMap<>();
      map.put("patternType", pattern.isProcessing() ? "PROCESSING" : "CRAFTING");
      var outputs = new ArrayList<>();
      for (var output : pattern.getOutputs()) {
        if (!output.isEmpty()) {
          outputs.add(convert(output, output.getCount(), true, crafting));
        }
      }
      map.put("outputs", outputs.toArray());
      if (!outputs.isEmpty()) {
        map.put("primaryOutput", outputs.get(0));
      }
      var inputs = new ArrayList<>();
      for (List<ItemStack> options : pattern.getInputs()) {
        var converted = new ArrayList<>();
        for (var option : options) {
          if (!option.isEmpty()) {
            converted.add(convert(option, option.getCount(), isCraftable(option, crafting), crafting));
          }
        }
        inputs.add(converted.toArray());
      }
      map.put("inputs", inputs.toArray());
      result.add(map);
    }
    return ResultWrapper.result((Object) result.toArray());
  }

  private static HashMap<Object, Object> parseFilter(Arguments args) {
    var hash = new HashMap<>();
    var table = args.optTable(0, Collections.emptyMap());
    var converted = Registry.INSTANCE.convertRecursively(table, new java.util.IdentityHashMap<>());
    if (converted instanceof Map<?, ?> map) {
      for (var entry : map.entrySet()) {
        hash.put(reduceLuaValue(entry.getKey()), reduceLuaValue(entry.getValue()));
      }
    }
    return hash;
  }

  private static Object reduceLuaValue(Object any) {
    if (any instanceof Map<?, ?> map) {
      if (isSequentialTable(map)) {
        return reduceSequentialTable(map);
      } else {
        return new HashMap<>(map);
      }
    }
    return any;
  }

  private static boolean isSequentialTable(Map<?, ?> map) {
    for (var entry : map.entrySet()) {
      var key = entry.getKey();
      var value = entry.getValue();
      if (key instanceof String s) {
        if (!s.equals("n") || !(value instanceof Number)) return false;
      } else if (key instanceof Number n) {
        if (n.intValue() < 1) return false;
      } else {
        return false;
      }
    }
    return true;
  }

  private static Object[] reduceSequentialTable(Map<?, ?> map) {
    var list = new ArrayList<>();
    for (var entry : map.entrySet()) {
      if (!(entry.getKey() instanceof String s) || !s.equals("n")) {
        list.add(reduceLuaValue(entry.getValue()));
      }
    }
    return list.toArray();
  }

  default boolean matches(Map<Object, Object> stack, Map<Object, Object> filter) {
    if (stack == null) return false;
    for (var entry : filter.entrySet()) {
      if (!contains(stack, entry.getKey(), entry.getValue())) return false;
    }
    return true;
  }

  private static boolean contains(Map<Object, Object> stack, Object key, Object value) {
    return stack.containsKey(key) && valueMatch(value, stack.get(key));
  }

  private static boolean valueMatch(Object a, Object b) {
    if ((a == null) != (b == null)) return false;
    if (a == b) return true;
    if (a instanceof Number aNum && b instanceof Number bNum) {
      return aNum.intValue() == bNum.intValue();
    }
    if (a instanceof Object[] aArr && b instanceof Object[] bArr) {
      for (var aElem : aArr) {
        if (!(aElem instanceof Map<?, ?> aMap)) return false;
        for (var aEntry : aMap.entrySet()) {
          boolean found = false;
          for (var bElem : bArr) {
            if (!(bElem instanceof Map<?, ?> bMap)) continue;
            for (var bEntry : bMap.entrySet()) {
              if (valueMatch(aEntry.getKey(), bEntry.getKey())
                && valueMatch(aEntry.getValue(), bEntry.getValue())) {
                found = true;
                break;
              }
            }
            if (found) break;
          }
          if (!found) return false;
        }
      }
      return true;
    }
    return false;
  }

  static BlockEntity resolveTile(String dimension, int x, int y, int z) {
    var server = SideTracker.getCurrentServer();
    if (server == null) return null;
    var key = net.minecraft.resources.ResourceLocation.tryParse(dimension);
    if (key == null) return null;
    var world = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, key));
    if (world == null) return null;
    return world.getBlockEntity(new BlockPos(x, y, z));
  }

  private static CompoundTag saveTile(CompoundTag nbt, @Nullable BlockEntity tile) {
    if (tile != null && tile.getLevel() != null) {
      nbt.putString("dimension", tile.getLevel().dimension().location().toString());
      nbt.putInt("x", tile.getBlockPos().getX());
      nbt.putInt("y", tile.getBlockPos().getY());
      nbt.putInt("z", tile.getBlockPos().getZ());
    }
    return nbt;
  }

  class Craftable extends AbstractValue {
    private BlockEntity tile;
    private ItemStack stack = ItemStack.EMPTY;
    private long amount;
    private String dimension;
    private int x, y, z;

    @SuppressWarnings("unused")
    public Craftable() {
    }

    public Craftable(BlockEntity tile, ItemStack stack, long amount) {
      this.tile = tile;
      this.stack = stack.copy();
      this.amount = amount;
    }

    private BlockEntity resolveTile() {
      if (tile != null && tile.isRemoved()) tile = null;
      if (tile == null && dimension != null) {
        tile = NetworkControl.resolveTile(dimension, x, y, z);
      }
      return tile;
    }

    @Callback(doc = "function():table -- Returns the item stack representation of the crafting result.")
    public Object[] getItemStack(Context ignoredContext, Arguments ignoredArgs) {
      if (stack.isEmpty()) return ResultWrapper.result((Object) null);
      var result = stack.copy();
      result.setCount((int) Math.max(1, Math.min(amount, Integer.MAX_VALUE)));
      return ResultWrapper.result(result);
    }

    @Callback(doc = "function():number -- Get the number of items of this type currently being crafted / requested in the network.")
    public Object[] requesting(Context ignoredContext, Arguments ignoredArgs) {
      var network = RSUtil.networkOf(resolveTile());
      if (network == null) return ResultWrapper.result(0);
      long requested = 0;
      for (var task : network.getCraftingManager().getTasks()) {
        var item = task.getRequested().getItem();
        if (item != null && ItemStack.isSameItemSameTags(item, stack)) {
          requested += task.getQuantity();
        }
      }
      return ResultWrapper.result(requested);
    }

    @Callback(doc = "function([amount:int=1]):userdata -- Requests item to be crafted, returning an object that allows tracking the crafting status.")
    public Object[] request(Context ignoredContext, Arguments args) {
      if (resolveTile() == null) {
        return ResultWrapper.result(null, "no controller");
      }
      var network = RSUtil.networkOf(tile);
      if (network == null) {
        return ResultWrapper.result(null, "no rs network");
      }
      var crafting = network.getCraftingManager();
      var count = Math.max(1, args.optInteger(0, 1));
      var status = new CraftingStatus(tile);
      var calculation = crafting.create(stack, count);
      if (calculation == null) {
        status.fail("no pattern");
      } else if (!calculation.isOk() || calculation.getTask() == null) {
        status.fail(calculation.getType().toString().toLowerCase(java.util.Locale.ROOT).replace('_', ' '));
      } else {
        crafting.start(calculation.getTask());
        status.track(calculation.getTask().getId());
      }
      return ResultWrapper.result(status);
    }

    @Override
    public void load(CompoundTag nbt, HolderLookup.Provider provider) {
      super.load(nbt, provider);
      if (nbt.contains("stack")) stack = ItemStack.of(nbt.getCompound("stack"));
      amount = nbt.getLong("amount");
      if (nbt.contains("dimension")) {
        dimension = nbt.getString("dimension");
        x = nbt.getInt("x");
        y = nbt.getInt("y");
        z = nbt.getInt("z");
        tile = NetworkControl.resolveTile(dimension, x, y, z);
      }
    }

    @Override
    public void save(CompoundTag nbt, HolderLookup.Provider provider) {
      super.save(nbt, provider);
      nbt.put("stack", stack.save(new CompoundTag()));
      nbt.putLong("amount", amount);
      saveTile(nbt, tile);
    }
  }

  class CraftingStatus extends AbstractValue {
    private boolean failed = false;
    private String reason = "no task";
    private UUID taskId;
    private boolean canceled;
    private BlockEntity tile;
    private String dimension;
    private int x, y, z;

    @SuppressWarnings("unused")
    public CraftingStatus() {
    }

    public CraftingStatus(BlockEntity tile) {
      this.tile = tile;
    }

    void fail(String reason) {
      failed = true;
      this.reason = "request failed (" + reason + ")";
    }

    void track(UUID taskId) {
      this.taskId = taskId;
    }

    private @Nullable ICraftingManager crafting() {
      if (tile != null && tile.isRemoved()) tile = null;
      if (tile == null && dimension != null) {
        tile = NetworkControl.resolveTile(dimension, x, y, z);
      }
      var network = RSUtil.networkOf(tile);
      return network != null ? network.getCraftingManager() : null;
    }

    @Callback(doc = "function():boolean -- Get whether the crafting request is done (the task is no longer running).")
    public Object[] isDone(Context ignoredContext, Arguments ignoredArgs) {
      if (failed) return ResultWrapper.result(false, reason);
      var crafting = crafting();
      if (crafting == null) return ResultWrapper.result(false);
      return ResultWrapper.result(taskId == null || crafting.getTask(taskId) == null);
    }

    @Callback(doc = "function():boolean -- Get whether the crafting request has been canceled through this API.")
    public Object[] isCanceled(Context ignoredContext, Arguments ignoredArgs) {
      if (failed) return ResultWrapper.result(false, reason);
      return ResultWrapper.result(canceled);
    }

    @Callback(doc = "function():boolean -- Cancels the request. Returns false if the craft cannot be canceled or nil if the link is computing")
    public Object[] cancel(Context ignoredContext, Arguments ignoredArgs) {
      if (failed) return ResultWrapper.result(false, reason);
      if (canceled) return ResultWrapper.result(false, "job already canceled");
      var crafting = crafting();
      if (crafting == null) return ResultWrapper.result(false, "no rs network");
      if (taskId == null || crafting.getTask(taskId) == null) {
        return ResultWrapper.result(false, "no task");
      }
      crafting.cancel(taskId);
      canceled = true;
      return ResultWrapper.result(true);
    }

    @Override
    public void save(CompoundTag nbt, HolderLookup.Provider provider) {
      super.save(nbt, provider);
      nbt.putBoolean("failed", failed);
      nbt.putString("reason", reason);
      if (taskId != null) nbt.putUUID("taskId", taskId);
      nbt.putBoolean("canceled", canceled);
      saveTile(nbt, tile);
    }

    @Override
    public void load(CompoundTag nbt, HolderLookup.Provider provider) {
      super.load(nbt, provider);
      failed = nbt.getBoolean("failed");
      if (nbt.contains("reason")) reason = nbt.getString("reason");
      if (nbt.hasUUID("taskId")) taskId = nbt.getUUID("taskId");
      canceled = nbt.getBoolean("canceled");
      if (nbt.contains("dimension")) {
        dimension = nbt.getString("dimension");
        x = nbt.getInt("x");
        y = nbt.getInt("y");
        z = nbt.getInt("z");
        tile = NetworkControl.resolveTile(dimension, x, y, z);
      }
    }
  }
}
