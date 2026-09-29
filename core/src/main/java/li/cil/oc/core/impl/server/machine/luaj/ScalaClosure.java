package li.cil.oc.core.impl.server.machine.luaj;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import li.cil.oc.api.machine.Value;
import li.cil.oc.core.impl.OCSettings;
import li.cil.repack.org.luaj.vm2.LuaString;
import li.cil.repack.org.luaj.vm2.LuaTable;
import li.cil.repack.org.luaj.vm2.LuaValue;
import li.cil.repack.org.luaj.vm2.Varargs;
import li.cil.repack.org.luaj.vm2.lib.VarArgFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ScalaClosure {
  private static final Logger LOGGER = LoggerFactory.getLogger(ScalaClosure.class);

  @FunctionalInterface
  public interface LuaCallable {
    Object[] call(Object[] args) throws Exception;
  }

  public static LuaValue wrapClosure(Function<Varargs, Varargs> f) {
    return new VarArgFunction() {
      @Override
      public Varargs invoke(Varargs args) {
        return f.apply(args);
      }
    };
  }

  public static LuaValue wrapVarArgClosure(Function<Varargs, Varargs> f) {
    return new VarArgFunction() {
      @Override
      public Varargs invoke(Varargs args) {
        return f.apply(args);
      }
    };
  }

  public static List<Object> toSimpleJavaObjects(Varargs args, int start) {
    List<Object> result = new ArrayList<>();
    for (int i = start; i <= args.narg(); i++) {
      result.add(toSimpleJavaObject(args.arg(i)));
    }
    return result;
  }

  public static Object toSimpleJavaObject(LuaValue value) {
    switch (value.type()) {
      case LuaValue.TBOOLEAN:
        return value.toboolean();
      case LuaValue.TNUMBER:
        return value.todouble();
      case LuaValue.TSTRING:
        if (value instanceof LuaString s) {
          byte[] bytes = new byte[s.m_length];
          System.arraycopy(s.m_bytes, s.m_offset, bytes, 0, s.m_length);
          return bytes;
        }
        return value.tojstring();
      case LuaValue.TTABLE:
        LuaTable table = value.checktable();
        Map<Object, Object> map = new LinkedHashMap<>();
        LuaValue k = LuaValue.NIL;
        while (true) {
          Varargs n = table.next(k);
          k = n.arg1();
          if (k.isnil()) break;
          map.put(toSimpleJavaObject(k), toSimpleJavaObject(n.arg(2)));
        }
        return map;
      case LuaValue.TUSERDATA:
        return value.touserdata();
      default:
        return null;
    }
  }

  public static LuaValue toLuaValue(Object value) {
    if (value == null) {
      return LuaValue.NIL;
    } else if (value instanceof Boolean b) {
      return LuaValue.valueOf(b);
    } else if (value instanceof Byte b) {
      return LuaValue.valueOf(b);
    } else if (value instanceof Character c) {
      return LuaValue.valueOf(String.valueOf(c));
    } else if (value instanceof Short s) {
      return LuaValue.valueOf(s);
    } else if (value instanceof Integer i) {
      return LuaValue.valueOf(i);
    } else if (value instanceof Long l) {
      return LuaValue.valueOf(l);
    } else if (value instanceof Float f) {
      return LuaValue.valueOf(f);
    } else if (value instanceof Double d) {
      return LuaValue.valueOf(d);
    } else if (value instanceof String s) {
      return LuaValue.valueOf(s);
    } else if (value instanceof byte[] b) {
      return LuaValue.valueOf(b);
    } else if (value instanceof Value v && (OCSettings.get().allowUserdata)) {
      return LuaValue.userdataOf(v);
    } else if (value instanceof Object[] a) {
      return toLuaList(java.util.Arrays.asList(a));
    } else if (value instanceof Map<?, ?> m) {
      return toLuaTable(m);
    } else if (value instanceof Iterable<?> it) {
      return toLuaList(it);
    } else if (value instanceof LuaCallable f) {
      return wrapLuaFunction(f);
    } else {
      if (value.getClass().isArray()) {
        int len = java.lang.reflect.Array.getLength(value);
        List<Object> list = new ArrayList<>(len);
        for (int i = 0; i < len; i++) {
          list.add(java.lang.reflect.Array.get(value, i));
        }
        return toLuaList(list);
      }
      LOGGER.warn("Tried to push an unsupported value of type to Lua: {}.", value.getClass().getName());
      return LuaValue.NIL;
    }
  }

  private static LuaValue toLuaList(Iterable<?> iterable) {
    List<LuaValue> values = new ArrayList<>();
    for (Object item : iterable) {
      values.add(toLuaValue(item));
    }
    return LuaValue.listOf(values.toArray(new LuaValue[0]));
  }

  private static LuaValue toLuaTable(Map<?, ?> map) {
    LuaTable table = new LuaTable();
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      table.set(toLuaValue(entry.getKey()), toLuaValue(entry.getValue()));
    }
    return table;
  }

  private static LuaValue wrapLuaFunction(LuaCallable func) {
    return new VarArgFunction() {
      @Override
      public Varargs invoke(Varargs args) {
        try {
          Object[] javaArgs = toSimpleJavaObjects(args, 1).toArray();
          Object[] result = func.call(javaArgs);
          if (result == null || result.length == 0) return LuaValue.TRUE;
          if (result.length == 1) return toLuaValue(result[0]);
          LuaValue[] luaResults = new LuaValue[result.length + 1];
          luaResults[0] = LuaValue.TRUE;
          for (int i = 0; i < result.length; i++) {
            luaResults[i + 1] = toLuaValue(result[i]);
          }
          return LuaValue.varargsOf(luaResults);
        } catch (Exception e) {
          return LuaValue.varargsOf(LuaValue.FALSE, LuaValue.valueOf(e.getMessage()));
        }
      }
    };
  }
}
