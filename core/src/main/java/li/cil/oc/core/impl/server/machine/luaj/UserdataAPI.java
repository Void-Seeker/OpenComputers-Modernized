package li.cil.oc.core.impl.server.machine.luaj;

import java.util.Map;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Value;
import li.cil.oc.core.impl.server.machine.ArgumentsImpl;
import li.cil.oc.core.util.ConverterRegistry;
import li.cil.repack.org.luaj.vm2.LuaValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserdataAPI extends LuaJAPI {
  private static final Logger LOGGER = LoggerFactory.getLogger(UserdataAPI.class);

  public UserdataAPI(LuaJLuaArchitecture owner) {
    super(owner);
  }

  @Override
  public void initialize() {
    LuaValue userdata = LuaValue.tableOf();

    userdata.set("apply", ScalaClosure.wrapVarArgClosure(args -> {
      Value value = (Value) args.checkuserdata(1, Value.class);
      // (arguments converted within the call, as component.invoke's)
      return owner.invoke(() -> ConverterRegistry.get().convert(new Object[]{value.apply(machine, new ArgumentsImpl(ScalaClosure.toSimpleJavaObjects(args, 2).toArray()))}));
    }));

    userdata.set("unapply", ScalaClosure.wrapVarArgClosure(args -> {
      Value value = (Value) args.checkuserdata(1, Value.class);
      return owner.invoke(() -> {
        value.unapply(machine, new ArgumentsImpl(ScalaClosure.toSimpleJavaObjects(args, 2).toArray()));
        return null;
      });
    }));

    userdata.set("call", ScalaClosure.wrapVarArgClosure(args -> {
      Value value = (Value) args.checkuserdata(1, Value.class);
      return owner.invoke(() -> ConverterRegistry.get().convert(value.call(machine, new ArgumentsImpl(ScalaClosure.toSimpleJavaObjects(args, 2).toArray()))));
    }));

    userdata.set("dispose", ScalaClosure.wrapClosure(args -> {
      Value value = (Value) args.checkuserdata(1, Value.class);
      try {
        value.dispose(machine);
      } catch (Throwable t) {
        LOGGER.warn("Error in dispose method of userdata of type {}", value.getClass().getName(), t);
      }
      return LuaValue.NIL;
    }));

    userdata.set("methods", ScalaClosure.wrapClosure(args -> {
      Value value = (Value) args.checkuserdata(1, Value.class);
      Map<String, Callback> methods = machine.methods(value);
      LuaValue[] entries = new LuaValue[methods.size() * 2];
      int i = 0;
      for (Map.Entry<String, Callback> entry : methods.entrySet()) {
        entries[i++] = LuaValue.valueOf(entry.getKey());
        entries[i++] = LuaValue.valueOf(entry.getValue().direct());
      }
      return LuaValue.tableOf(entries);
    }));

    userdata.set("invoke", ScalaClosure.wrapVarArgClosure(args -> {
      Value value = (Value) args.checkuserdata(1, Value.class);
      String method = args.checkjstring(2);
      return owner.invoke(() -> machine.invoke(value, method, ScalaClosure.toSimpleJavaObjects(args, 3).toArray()));
    }));

    userdata.set("doc", ScalaClosure.wrapVarArgClosure(args -> {
      Value value = (Value) args.checkuserdata(1, Value.class);
      String method = args.checkjstring(2);
      return owner.documentation(() -> {
        Map<String, Callback> methods = machine.methods(value);
        Callback cb = methods.get(method);
        return cb != null ? cb.doc() : null;
      });
    }));

    lua().set("userdata", userdata);
  }
}
