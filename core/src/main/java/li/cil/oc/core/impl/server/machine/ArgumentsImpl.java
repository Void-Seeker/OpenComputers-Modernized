package li.cil.oc.core.impl.server.machine;

import java.util.Iterator;
import java.util.Map;
import li.cil.oc.api.machine.Arguments;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import li.cil.oc.compat.CustomData;
import org.jetbrains.annotations.NotNull;

public class ArgumentsImpl implements Arguments {
  private final Object[] args;

  public ArgumentsImpl(Object[] args) {
    this.args = args != null ? args : new Object[0];
  }

  @Override
  public Object[] toArray() {
    Object[] result = new Object[args.length];
    for (int i = 0; i < args.length; i++) {
      if (args[i] instanceof byte[]) {
        result[i] = new String((byte[]) args[i], java.nio.charset.StandardCharsets.UTF_8);
      } else {
        result[i] = args[i];
      }
    }
    return result;
  }

  @Override
  public @NotNull Iterator<Object> iterator() {
    return java.util.Arrays.asList(args).iterator();
  }

  @Override
  public int count() {
    return args.length;
  }

  // Errors and opt* as OC 1.12's ArgumentsImpl: "bad argument #2 (number expected, got string)", 1-based and in Lua's
  // type names; an opt* argument given (not nil) must have the type, else it is an error (it was the default).

  private boolean isDefined(int index) {
    return index >= 0 && index < args.length && args[index] != null;
  }

  private void checkIndex(int index, String name) {
    if (index < 0) throw new IndexOutOfBoundsException();
    if (args.length <= index) throw new IllegalArgumentException("bad arguments #" + (index + 1) + " (" + name + " expected, got no value)"); // ("arguments": OC's words)
  }

  private static IllegalArgumentException typeError(int index, Object have, String want) {
    return new IllegalArgumentException("bad argument #" + (index + 1) + " (" + want + " expected, got " + typeName(have) + ")");
  }

  private static IllegalArgumentException intError(int index, Object have) {
    return new IllegalArgumentException("bad argument #" + (index + 1) + " (" + typeName(have) + " has no integer representation)");
  }

  private static String typeName(Object value) {
    if (value == null) return "nil";
    if (value instanceof Boolean) return "boolean";
    if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) return "integer";
    if (value instanceof Number) return "number";
    if (value instanceof String || value instanceof byte[]) return "string";
    if (value instanceof Map) return "table";
    return value.getClass().getSimpleName();
  }

  @Override
  public Object checkAny(int index) {
    checkIndex(index, "value");
    return args[index];
  }

  @Override
  public boolean checkBoolean(int index) {
    checkIndex(index, "boolean");
    Object value = args[index];
    if (value instanceof Boolean) return (Boolean) value;
    throw typeError(index, value, "boolean");
  }

  @Override
  public double checkDouble(int index) {
    checkIndex(index, "number");
    Object value = args[index];
    if (value instanceof Number) return ((Number) value).doubleValue();
    throw typeError(index, value, "number");
  }

  @Override
  public int checkInteger(int index) {
    checkIndex(index, "integer");
    Object value = args[index];
    if (!(value instanceof Number number)) throw typeError(index, value, "integer");
    double d = number.doubleValue();
    if (Double.isNaN(d)) throw intError(index, value);
    if (d > Integer.MAX_VALUE) return Integer.MAX_VALUE;
    if (d < Integer.MIN_VALUE) return Integer.MIN_VALUE;
    return (int) d;
  }

  @Override
  public String checkString(int index) {
    checkIndex(index, "string");
    Object value = args[index];
    if (value instanceof String) return (String) value;
    if (value instanceof byte[]) return new String((byte[]) value, java.nio.charset.StandardCharsets.UTF_8); // not the platform's (Windows: Cp1252)
    throw typeError(index, value, "string");
  }

  @Override
  public byte[] checkByteArray(int index) {
    checkIndex(index, "string");
    Object value = args[index];
    if (value instanceof byte[]) return (byte[]) value;
    if (value instanceof String) return ((String) value).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    throw typeError(index, value, "string");
  }

  @Override
  public Object optAny(int index, Object defaultVal) {
    return isDefined(index) ? checkAny(index) : defaultVal;
  }

  @Override
  public boolean optBoolean(int index, boolean defaultVal) {
    return isDefined(index) ? checkBoolean(index) : defaultVal;
  }

  @Override
  public double optDouble(int index, double defaultVal) {
    return isDefined(index) ? checkDouble(index) : defaultVal;
  }

  @Override
  public int optInteger(int index, int defaultVal) {
    return isDefined(index) ? checkInteger(index) : defaultVal;
  }

  @Override
  public String optString(int index, String defaultVal) {
    return isDefined(index) ? checkString(index) : defaultVal;
  }

  @Override
  public byte[] optByteArray(int index, byte[] defaultVal) {
    return isDefined(index) ? checkByteArray(index) : defaultVal;
  }

  @Override
  public boolean isString(int index) {
    if (index < 0 || index >= count()) return false;
    return args[index] instanceof String || args[index] instanceof byte[];
  }

  @Override
  public long checkLong(int index) {
    checkIndex(index, "integer");
    Object value = args[index];
    if (!(value instanceof Number number)) throw typeError(index, value, "integer");
    if (value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte) return number.longValue(); // (exact beyond 2^53)
    double d = number.doubleValue();
    if (Double.isNaN(d)) throw intError(index, value);
    if (d > Long.MAX_VALUE) return Long.MAX_VALUE;
    if (d < Long.MIN_VALUE) return Long.MIN_VALUE;
    return (long) d;
  }

  @SuppressWarnings("rawtypes")
  @Override
  public Map checkTable(int index) {
    checkIndex(index, "table");
    Object value = args[index];
    if (value instanceof Map) return (Map) value;
    throw typeError(index, value, "table");
  }

  @Override
  public ItemStack checkItemStack(int index) {
    Map<?, ?> map = checkTable(index);
    Object nameObj = map.get("name");
    if (!(nameObj instanceof String name)) {
      throw new IllegalArgumentException("invalid item stack");
    }
    int damage = 0;
    Object damageObj = map.get("damage");
    if (damageObj instanceof Number number) {
      damage = number.intValue();
    }
    CompoundTag tag = null;
    Object tagObj = map.get("tag");
    try {
      if (tagObj instanceof byte[] ba) {
        tag = li.cil.oc.compat.NbtCompat.readCompressed(new java.io.ByteArrayInputStream(ba), new NbtAccounter(0x200000L));
      } else if (tagObj instanceof String s) {
        tag = li.cil.oc.compat.NbtCompat.readCompressed(new java.io.ByteArrayInputStream(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)), new NbtAccounter(0x200000L));
      }
    } catch (java.io.IOException ignored) {
    }
    ResourceLocation location;
    try {
      location = new ResourceLocation(name);
    } catch (Exception e) {
      throw new IllegalArgumentException("invalid item stack");
    }
    Item item = BuiltInRegistries.ITEM.get(location);
    if (item == Items.AIR) {
      throw new IllegalArgumentException("invalid item stack");
    }
    ItemStack stack = new ItemStack(item, 1);
    if (damage > 0) {
      stack.setDamageValue(damage);
    }
    if (tag != null && !tag.isEmpty()) {
      CustomData.set(stack, CustomData.of(tag));
    }
    return stack;
  }

  @Override
  public long optLong(int index, long def) {
    return isDefined(index) ? checkLong(index) : def;
  }

  @SuppressWarnings("rawtypes")
  @Override
  public Map optTable(int index, Map def) {
    return isDefined(index) ? checkTable(index) : def;
  }

  @Override
  public ItemStack optItemStack(int index, ItemStack def) {
    return isDefined(index) ? checkItemStack(index) : def;
  }

  @Override
  public boolean isBoolean(int index) {
    if (index < 0 || index >= count()) return false;
    return args[index] instanceof Boolean;
  }

  @Override
  public boolean isInteger(int index) {
    if (index < 0 || index >= count()) return false;
    var val = args[index];
    if (val instanceof Double d) return !d.isNaN();
    if (val instanceof Float f) return !f.isNaN();
    return val instanceof Number;
  }

  @Override
  public boolean isLong(int index) {
    return isInteger(index);
  }

  @Override
  public boolean isDouble(int index) {
    if (index < 0 || index >= count()) return false;
    return args[index] instanceof Number;
  }

  @Override
  public boolean isByteArray(int index) {
    if (index < 0 || index >= count()) return false;
    return args[index] instanceof byte[] || args[index] instanceof String;
  }

  @Override
  public boolean isTable(int index) {
    if (index < 0 || index >= count()) return false;
    return args[index] instanceof Map;
  }

  @Override
  public boolean isItemStack(int index) {
    if (index < 0 || index >= count()) return false;
    if (!(args[index] instanceof Map<?, ?> map)) return false;
    Object name = map.get("name");
    return name instanceof String || name instanceof byte[];
  }
}
