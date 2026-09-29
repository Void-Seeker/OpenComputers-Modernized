package li.cil.oc.core.impl.util;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import li.cil.oc.api.machine.Value;
import li.cil.oc.core.impl.OCSettings;
import li.cil.repack.com.naef.jnlua.LuaState;
import li.cil.repack.com.naef.jnlua.LuaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ExtendedLuaState {
  private static final Logger LOGGER = LoggerFactory.getLogger(ExtendedLuaState.class);

  public static void pushScalaFunction(LuaState lua, final java.util.function.Function<LuaState, Integer> f) {
    lua.pushJavaFunction(f::apply);
  }

  public static void pushValue(LuaState lua, Object value) {
    pushValue(lua, value, new IdentityHashMap<>());
  }

  @SuppressWarnings("unused")
  public static void pushValue(LuaState lua, Object value, IdentityHashMap<Object, Integer> memo) {
    boolean recursive = !memo.isEmpty();
    int oldTop = lua.getTop();
    if (memo.containsKey(value)) {
      lua.pushValue(memo.get(value));
    } else {

      if (value == null) {
        lua.pushNil();
      } else if (value instanceof Boolean b) {
        lua.pushBoolean(b);
      } else if (value instanceof Byte b) {
        lua.pushInteger(b);
      } else if (value instanceof Character c) {
        lua.pushString(String.valueOf(value));
      } else if (value instanceof Short aShort) {
        lua.pushInteger(aShort);
      } else if (value instanceof Integer integer) {
        lua.pushInteger(integer);
      } else if (value instanceof Long l) {
        lua.pushInteger(l);
      } else if (value instanceof Float v) {
        lua.pushNumber(v);
      } else if (value instanceof Double v) {
        lua.pushNumber(v);
      } else if (value instanceof String s) {
        lua.pushString(s);
      } else if (value instanceof byte[] bytes) {
        lua.pushByteArray(bytes);
      } else if (value instanceof float[] arr) {
        pushPrimitiveArray(lua, value, arr, i -> arr[i], arr.length, memo);
      } else if (value instanceof double[] arr) {
        pushPrimitiveArray(lua, value, arr, i -> arr[i], arr.length, memo);
      } else if (value instanceof int[] arr) {
        pushPrimitiveArray(lua, value, arr, i -> arr[i], arr.length, memo);
      } else if (value instanceof short[] arr) {
        pushPrimitiveArray(lua, value, arr, i -> arr[i], arr.length, memo);
      } else if (value instanceof long[] arr) {
        pushPrimitiveArray(lua, value, arr, i -> arr[i], arr.length, memo);
      } else if (value instanceof char[] arr) {
        pushPrimitiveArray(lua, value, arr, i -> arr[i], arr.length, memo);
      } else if (value instanceof boolean[] arr) {
        pushPrimitiveArray(lua, value, arr, i -> arr[i], arr.length, memo);
      } else if (value instanceof Object[] arr) {
        java.util.List<Map.Entry<Object, Integer>> list = new java.util.ArrayList<>();
        for (int i = 0; i < arr.length; i++) {
          list.add(new java.util.AbstractMap.SimpleEntry<>(arr[i], i));
        }
        pushList(lua, value, list.iterator(), memo);
      } else if (value instanceof Value && (OCSettings.get().allowUserdata)) {
        lua.pushJavaObjectRaw(value);
      } else if (value instanceof Map<?, ?> map) {
        pushTableFromJavaMap(lua, value, map, memo);
      } else if (value instanceof li.cil.oc.core.impl.server.machine.luaj.ScalaClosure.LuaCallable f) {
        pushLuaCallable(lua, f, memo);
      } else if (value instanceof Iterable<?> iterable) {
        java.util.List<java.util.Map.Entry<Object, Integer>> list = new java.util.ArrayList<>();
        int idx = 0;
        for (Object item : iterable) {
          list.add(new java.util.AbstractMap.SimpleEntry<>(item, idx++));
        }
        pushList(lua, value, list.iterator(), memo);
      } else {
        LOGGER.warn("Tried to push an unsupported value of type to Lua: {}.", value.getClass().getName());
        lua.pushNil();
      }

      if (!recursive) {
        lua.setTop(oldTop + 1);
      }
    }
  }

  private static void pushPrimitiveArray(LuaState lua, Object obj, Object ignoredArray, java.util.function.IntFunction<Object> get, int length, IdentityHashMap<Object, Integer> memo) {
    java.util.List<Map.Entry<Object, Integer>> list = new java.util.ArrayList<>();
    for (int i = 0; i < length; i++) {
      list.add(new java.util.AbstractMap.SimpleEntry<>(get.apply(i), i));
    }
    pushList(lua, obj, list.iterator(), memo);
  }

  private static void pushList(LuaState lua, Object obj, Iterator<java.util.Map.Entry<Object, Integer>> list, IdentityHashMap<Object, Integer> memo) {
    lua.newTable();
    int tableIndex = lua.getTop();
    memo.put(obj, tableIndex);
    while (list.hasNext()) {
      java.util.Map.Entry<Object, Integer> entry = list.next();
      pushValue(lua, entry.getKey(), memo);
      lua.rawSet(tableIndex, entry.getValue() + 1);
    }
    lua.pushValue(tableIndex);
  }

  private static void pushTableFromJavaMap(LuaState lua, Object obj, java.util.Map<?, ?> map, IdentityHashMap<Object, Integer> memo) {
    lua.newTable(0, map.size());
    int tableIndex = lua.getTop();
    memo.put(obj, tableIndex);
    for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
      Object key = entry.getKey();
      Object value = entry.getValue();
      if (key != null) {
        pushValue(lua, key, memo);
        int keyIndex = lua.getTop();
        pushValue(lua, value, memo);
        lua.pushValue(keyIndex);
        lua.insert(-2);
        lua.setTable(tableIndex);
      }
    }
    lua.pushValue(tableIndex);
  }

  private static void pushLuaCallable(LuaState lua, li.cil.oc.core.impl.server.machine.luaj.ScalaClosure.LuaCallable func, IdentityHashMap<Object, Integer> memo) {
    lua.pushJavaFunction(l -> {
      try {
        Object[] javaArgs = toSimpleJavaObjects(l, 1).toArray();
        Object[] result = func.call(javaArgs);
        if (result == null || result.length == 0) {
          l.pushBoolean(true);
          return 1;
        }
        for (Object r : result) {
          pushValue(l, r, memo);
        }
        return result.length;
      } catch (Exception e) {
        l.pushBoolean(false);
        l.pushString(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        return 2;
      }
    });
  }

  public static Object toSimpleJavaObject(LuaState lua, int index) {
    return switch (lua.type(index)) {
      case BOOLEAN -> lua.toBoolean(index);
      case NUMBER -> {
        if (lua.isInteger(index)) yield lua.toInteger(index);
        yield lua.toNumber(index);
      }
      case STRING -> lua.toByteArray(index);
      case TABLE -> lua.toJavaObject(index, Map.class);
      case USERDATA -> lua.toJavaObjectRaw(index);
      default -> null;
    };
  }

  public static java.util.List<Object> toSimpleJavaObjects(LuaState lua, int start) {
    java.util.List<Object> result = new java.util.ArrayList<>();
    for (int index = start; index <= lua.getTop(); index++) {
      result.add(toSimpleJavaObject(lua, index));
    }
    return result;
  }
}
