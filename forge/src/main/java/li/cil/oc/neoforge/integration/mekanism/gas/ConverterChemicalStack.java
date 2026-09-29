package li.cil.oc.neoforge.integration.mekanism.gas;

import java.util.Map;
import li.cil.oc.api.driver.Converter;
import li.cil.oc.core.impl.OCSettings;
import mekanism.api.chemical.ChemicalStack;

@SuppressWarnings("unused")
public final class ConverterChemicalStack implements Converter {
  @Override
  public void convert(Object value, Map<Object, Object> output) {
    if (value instanceof ChemicalStack stack) {
      var key = stack.getTypeRegistryName();
      if (key != null) {
        if (OCSettings.get().insertIdsInConverters) {
          output.put("id", key.toString());
        }
        output.put("name", key.getPath());
        output.put("label", stack.getTextComponent().getString());
      }
      output.put("amount", stack.getAmount());
    }
  }
}
