package li.cil.oc.core.impl.util;

import li.cil.oc.compat.CustomData;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import li.cil.oc.core.impl.OCSettings;
import li.cil.oc.core.impl.server.component.TabletHostBase;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public abstract class TabletCache {
  private static TabletCache serverInstance;
  private static TabletCache clientInstance;

  public static TabletCache get() {
    if (serverInstance == null) {
      serverInstance = new ServerTabletCache();
    }
    return serverInstance;
  }

  public static TabletCache forSide(final boolean isClientSide) {
    if (isClientSide) {
      if (clientInstance == null) {
        clientInstance = new ClientTabletCache();
      }
      return clientInstance;
    }
    return get();
  }

  private static final String ID_TAG = OCSettings.namespace + "tablet";

  public static String getOrCreateId(ItemStack stack) {
    var tag = CustomData.get(stack);
    CompoundTag data;
    if (tag != null && !tag.isEmpty()) {
      data = tag.copyTag();
    } else {
      data = new CompoundTag();
    }
    if (!data.contains(ID_TAG)) {
      data.putString(ID_TAG, UUID.randomUUID().toString());
      CustomData.set(stack, li.cil.oc.compat.CustomData.of(data));
    }
    return data.getString(ID_TAG);
  }

  private final Cache<String, TabletHostBase> cache;

  protected TabletCache(long timeoutSeconds) {
    this.cache = CacheBuilder.newBuilder()
      .expireAfterAccess(timeoutSeconds, TimeUnit.SECONDS)
      .removalListener((com.google.common.cache.RemovalNotification<String, TabletHostBase> notification) -> {
        var wrapper = notification.getValue();
        if (wrapper != null) {
          wrapper.stopMachine();
          var level = wrapper.level();
          if (level != null && !level.isClientSide() && wrapper.autoSave) {
            wrapper.writeToNBT(true);
          }
          wrapper.release();
          if (wrapper.node() != null && wrapper.node().network() != null) {
            wrapper.node().remove();
          }
        }
      })
      .build();
  }

  protected abstract TabletHostBase createHost(ItemStack stack, Player player);

  public TabletHostBase get(ItemStack stack, Player player) {
    var id = getOrCreateId(stack);
    try {
      var host = cache.get(id, () -> createHost(stack, player));
      var dimChanged = host.creationLevel != null && !host.creationLevel.dimension().equals(player.level().dimension());
      if (dimChanged) {
        // the old host leaves from its own level: set to the new one first, its wireless card left the new
        // dimension's network and stayed in the old one's (with the whole tablet machine)
        if (!player.level().isClientSide) {
          // it runs on there, as in OC (it was stopped): paused first, as no slice may run while it is saved (mid-slice
          // its save is the one before); busy, it moves once the slice is over
          if (host.getStack() != stack) host.rebind(stack);
          var machine = host.machine();
          if (machine.isRunning() && ((machine instanceof li.cil.oc.core.impl.server.machine.MachineBase mb && mb.isExecuting())
            || (!machine.pause(0.05) && !machine.isPaused()))) {
            return host;
          }
          host.writeToNBT(false);
          host.autoSave = false;
        }
        cache.invalidate(id);
        cache.cleanUp();
        host = cache.get(id, () -> createHost(stack, player));
      }
      if (host.getStack() != stack) host.rebind(stack);
      return host;
    } catch (Exception e) {
      // the cached one (or one cached now): a new one each call made a machine and a Lua state each time
      var host = cache.getIfPresent(id);
      if (host == null) {
        host = createHost(stack, player);
        cache.put(id, host);
      }
      return host;
    }
  }

  public void saveAll(Level level) {
    for (var entry : cache.asMap().entrySet()) {
      var wrapper = entry.getValue();
      if (wrapper.level() == level && !level.isClientSide()) {
        wrapper.writeToNBT(true);
      }
    }
  }

  /**
   * Writes the player's tablets into their stacks as the player is saved (LevelEvent.Save comes after the players are
   * written: what a tablet did since the save before was lost at quitting).
   */
  public void saveAll(Player player) {
    if (player.level().isClientSide()) return;
    for (var wrapper : cache.asMap().values()) {
      var holder = wrapper.player();
      if (holder != null && holder.getUUID().equals(player.getUUID())) {
        wrapper.writeToNBT(true);
      }
    }
  }

  public void clear(Level level) {
    synchronized (cache) {
      var tabletsInWorld = cache.asMap().entrySet().stream()
        .filter(e -> e.getValue().level() == level)
        .map(java.util.Map.Entry::getKey)
        .toList();
      cache.invalidateAll(tabletsInWorld);
      cache.cleanUp();
    }
  }

  public void cleanUp() {
    synchronized (cache) {
      cache.cleanUp();
    }
  }

  @SuppressWarnings("UnusedReturnValue")
  public ImmutableMap<String, TabletHostBase> keepAlive() {
    return cache.getAllPresent(cache.asMap().keySet());
  }

  /** Drops the stack's tablet machine, which saves into this stack (it may follow an older one). */
  public void invalidate(ItemStack stack) {
    var id = getId(stack);
    if (id != null) {
      var host = cache.getIfPresent(id);
      if (host != null && host.getStack() != stack) host.rebind(stack);
      cache.invalidate(id);
    }
  }

  /** The tablet id of a stack, without giving it one. */
  public static String getId(ItemStack stack) {
    var tag = CustomData.get(stack);
    if (tag != null && !tag.isEmpty()) {
      var data = tag.copyTag();
      if (data.contains(ID_TAG)) {
        return data.getString(ID_TAG);
      }
    }
    return null;
  }
}
