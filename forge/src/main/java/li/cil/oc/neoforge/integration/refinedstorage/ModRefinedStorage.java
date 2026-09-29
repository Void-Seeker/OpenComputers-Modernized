package li.cil.oc.neoforge.integration.refinedstorage;

import li.cil.oc.neoforge.integration.ModProxy;
import li.cil.oc.neoforge.integration.Mods;

@SuppressWarnings("unused")
public final class ModRefinedStorage implements ModProxy {
  @Override
  public Mods.ModBase getMod() {
    return Mods.RefinedStorage2; // same mod id ("refinedstorage") for Refined Storage 1.x
  }

  @Override
  public void initialize() {
    li.cil.oc.core.impl.common.Registrar.registerWrenchTool("li.cil.oc.neoforge.integration.refinedstorage.EventHandlerRS.useWrench");
    li.cil.oc.core.impl.common.Registrar.registerWrenchToolCheck("li.cil.oc.neoforge.integration.refinedstorage.EventHandlerRS.isWrench");

    li.cil.oc.api.API.driver.add(new DriverController());
    li.cil.oc.api.API.driver.add(new DriverImporter());
    li.cil.oc.api.API.driver.add(new DriverExporter());
    li.cil.oc.api.API.driver.add(new DriverInterface());

    li.cil.oc.api.API.driver.add(new DriverController.Provider());
    li.cil.oc.api.API.driver.add(new DriverImporter.Provider());
    li.cil.oc.api.API.driver.add(new DriverExporter.Provider());
    li.cil.oc.api.API.driver.add(new DriverInterface.Provider());
  }
}
