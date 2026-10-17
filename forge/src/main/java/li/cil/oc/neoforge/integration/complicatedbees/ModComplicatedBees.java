package li.cil.oc.neoforge.integration.complicatedbees;

import li.cil.oc.neoforge.integration.ModProxy;
import li.cil.oc.neoforge.integration.Mods;

@SuppressWarnings("unused")
public final class ModComplicatedBees implements ModProxy {
  @Override
  public Mods.ModBase getMod() {
    return Mods.ComplicatedBees;
  }

  @Override
  public void initialize() {
    li.cil.oc.api.API.driver.add(new ConverterBee());
  }
}
