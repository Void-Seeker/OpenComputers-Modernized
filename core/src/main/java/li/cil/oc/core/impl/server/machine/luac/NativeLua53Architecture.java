package li.cil.oc.core.impl.server.machine.luac;

@li.cil.oc.api.machine.Architecture.Name("Lua 5.3")
public class NativeLua53Architecture extends NativeLuaArchitecture {
  public NativeLua53Architecture(li.cil.oc.api.machine.Machine machine) {
    super(machine);
  }

  // the one factory (a new one per start rewrote the native library, moved it over the loaded one and made a test state)
  @Override
  public LuaStateFactory factory() {
    return LuaStateFactory.Lua53.INSTANCE;
  }
}
