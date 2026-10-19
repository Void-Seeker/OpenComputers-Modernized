package li.cil.oc.core.impl.integration.opencomputers;

import java.util.Map;
import li.cil.oc.api.detail.ItemInfo;
import li.cil.oc.api.driver.Converter;
import li.cil.oc.core.Constants;
import net.minecraft.world.item.ItemStack;

@SuppressWarnings("unused")
public final class ConverterLinkedCard implements Converter {
  private final ItemInfo linkedCard = li.cil.oc.api.Items.get(Constants.ItemName.LinkedCard);

  @Override
  public void convert(Object value, Map<Object, Object> output) {
    if (value instanceof ItemStack stack && linkedCard.equals(li.cil.oc.api.Items.get(stack))) {
      // the card's own channel (a new card's default was given for every card: "creative")
      var data = Item.peekDataTag(stack);
      var key = li.cil.oc.core.impl.OCSettings.namespace + "tunnel";
      output.put("linkChannel", data.contains(key) ? data.getString(key) : new li.cil.oc.core.impl.server.component.LinkedCard().tunnel);
    }
  }
}
