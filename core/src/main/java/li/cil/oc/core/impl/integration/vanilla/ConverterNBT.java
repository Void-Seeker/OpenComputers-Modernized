package li.cil.oc.core.impl.integration.vanilla;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import li.cil.oc.api.driver.Converter;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

@SuppressWarnings("unused")
public final class ConverterNBT implements Converter {
  private static Object convert(Tag nbt) {
    if (nbt instanceof ByteTag tag) {
      return tag.getAsByte();
    } else if (nbt instanceof ShortTag tag) {
      return tag.getAsShort();
    } else if (nbt instanceof IntTag tag) {
      return tag.getAsInt();
    } else if (nbt instanceof LongTag tag) {
      return tag.getAsLong();
    } else if (nbt instanceof FloatTag tag) {
      return tag.getAsFloat();
    } else if (nbt instanceof DoubleTag tag) {
      return tag.getAsDouble();
    } else if (nbt instanceof ByteArrayTag tag) {
      return tag.getAsByteArray();
    } else if (nbt instanceof StringTag tag) {
      return tag.getAsString();
    } else if (nbt instanceof ListTag tag) {
      var copy = tag.copy();
      var list = new ArrayList<>();
      while (!copy.isEmpty()) {
        list.add(convert(copy.remove(0)));
      }
      return list.toArray();
    } else if (nbt instanceof CompoundTag tag) {
      var map = new HashMap<>();
      for (String key : tag.getAllKeys()) {
        map.put(key, convert(tag.get(key)));
      }
      return map;
    } else if (nbt instanceof IntArrayTag tag) {
      return tag.getAsIntArray();
    } else {
      return null;
    }
  }

  @Override
  public void convert(Object value, Map<Object, Object> output) {
    if (value instanceof CompoundTag nbt) {
      output.put("oc:flatten", convert(nbt));
    }
  }
}
