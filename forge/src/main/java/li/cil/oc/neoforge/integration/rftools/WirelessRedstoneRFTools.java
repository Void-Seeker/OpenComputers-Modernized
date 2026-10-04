package li.cil.oc.neoforge.integration.rftools;

import li.cil.oc.core.impl.integration.util.WirelessRedstone.WirelessRedstoneSystem;
import li.cil.oc.core.impl.server.component.RedstoneWireless;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import li.cil.oc.core.impl.common.blockentity.traits.RedstoneAware;
import mcjty.rftoolsutility.modules.logic.tools.RedstoneChannels;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

@SuppressWarnings("unused")
public final class WirelessRedstoneRFTools implements WirelessRedstoneSystem {
  // RFTools has no change callbacks: the channels are polled once a tick for the registered receivers
  private final Set<RedstoneWireless> receivers = ConcurrentHashMap.newKeySet();
  private final Map<RedstoneWireless, Boolean> lastInput = new ConcurrentHashMap<>();

  public WirelessRedstoneRFTools() {
    MinecraftForge.EVENT_BUS.addListener(this::onServerTick);
  }

  @Override
  public String name() {
    return "rftools";
  }

  @Override
  public void addReceiver(RedstoneWireless rs) {
    receivers.add(rs);
    lastInput.put(rs, getInput(rs));
  }

  @Override
  public void removeReceiver(RedstoneWireless rs) {
    receivers.remove(rs);
    lastInput.remove(rs);
  }

  private void onServerTick(TickEvent.ServerTickEvent e) {
    if (e.phase != TickEvent.Phase.END || receivers.isEmpty()) return;
    for (var rs : receivers) {
      boolean now = getInput(rs);
      Boolean before = lastInput.put(rs, now);
      if (before != null && before != now) {
        rs.onRedstoneChanged(new RedstoneAware.RedstoneChangedEventArgs(null, before ? 15 : 0, now ? 15 : 0, -1));
      }
    }
  }

  @Override
  public void updateOutput(RedstoneWireless rs) {
    Level level = rs.redstone().level();
    if (level == null || level.isClientSide()) return;
    RedstoneChannels channels = RedstoneChannels.getChannels(level);
    RedstoneChannels.RedstoneChannel ch = channels.getOrCreateChannel(rs.getFreq());
    ch.setValue(rs.getWirelessOutputValue() ? 15 : 0);
    channels.save();
  }

  @Override
  public void removeTransmitter(RedstoneWireless rs) {
    Level level = rs.redstone().level();
    if (level == null || level.isClientSide()) return;
    RedstoneChannels channels = RedstoneChannels.getChannels(level);
    RedstoneChannels.RedstoneChannel ch = channels.getChannel(rs.getFreq());
    if (ch != null) {
      ch.setValue(0);
      channels.save();
    }
  }

  @Override
  public boolean getInput(RedstoneWireless rs) {
    Level level = rs.redstone().level();
    if (level == null || level.isClientSide()) return false;
    RedstoneChannels channels = RedstoneChannels.getChannels(level);
    RedstoneChannels.RedstoneChannel ch = channels.getChannel(rs.getFreq());
    return ch != null && ch.getValue() > 0;
  }

  @Override
  public void resetRedstone(RedstoneWireless rs) {
    lastInput.remove(rs); // the next tick sets a fresh baseline without an event
  }

  @Override
  public boolean canHandleFrequency(int frequency) {
    return true;
  }
}
