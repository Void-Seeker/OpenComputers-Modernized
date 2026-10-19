package li.cil.oc.core.impl.server.machine.luac;

@li.cil.oc.api.machine.Architecture.Name("Lua 5.4")
public class NativeLua54Architecture extends NativeLuaArchitecture {
  public NativeLua54Architecture(li.cil.oc.api.machine.Machine machine) {
    super(machine);
  }

  // the one factory (a new one per start rewrote the native library, moved it over the loaded one and made a test state)
  @Override
  public LuaStateFactory factory() {
    return LuaStateFactory.Lua54.INSTANCE;
  }
}
