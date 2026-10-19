package li.cil.oc.core.impl.server.component;

import li.cil.oc.compat.CustomData;

import java.util.ArrayList;
import java.util.Map;
import li.cil.oc.api.Machine;
import li.cil.oc.api.driver.DeviceInfo;
import li.cil.oc.api.machine.MachineHost;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import li.cil.oc.core.Constants;
import li.cil.oc.core.impl.OCSettings;
import li.cil.oc.core.impl.common.inventory.ComponentInventory;
import li.cil.oc.core.impl.common.item.TabletWrapper;
import li.cil.oc.core.impl.util.RotationHelper;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class TabletHostBase implements ComponentInventory, MachineHost, Environment, li.cil.oc.api.internal.Tablet, TabletWrapper, DeviceInfo {
  private final Map<String, String> deviceInfo;
  private ManagedEnvironment[] _components;
  private final ArrayList<ManagedEnvironment> updatingComponents = new ArrayList<>();
  private boolean isInitialized = false;
  private boolean lastRunning = false;
  private li.cil.oc.api.machine.Machine _machine;
  private Tablet _tabletComponent;
  public Level creationLevel;
  // the energy last written into the item: more in the item is what a charger put there since
  private double savedEnergy = Double.NaN;
  // OC: false when its state was written for its successor (a dimension change), which its removal must not undo
  public boolean autoSave = true;
  // dropped from the cache: its stack may be its successor's (its last slice's scheduled write put back old parts)
  private volatile boolean released = false;

  protected TabletHostBase() {
    deviceInfo = Map.of(
      DeviceAttribute.Class, DeviceClass.System,
      DeviceAttribute.Description, "Tablet",
      DeviceAttribute.Vendor, Constants.DeviceInfo.DefaultVendor,
      DeviceAttribute.Product, "Jogger");
  }

  public abstract ItemStack getStack();

  /**
   * The stack the tablet is now (OC: wrapper.stack = stack). Every move in an inventory, a drop and a pick-up makes a
   * new stack object: the machine went on saving its energy and state into the old, empty one, its GUI closed at once,
   * and what it did after the move was lost when it unloaded (its energy came back: a dupe).
   */
  public void rebind(@NotNull ItemStack current) {
  }

  /** The tablet's menus are valid while the tablet is the item in hand; moving or throwing it closes them. */
  @Override
  public boolean stillValid(net.minecraft.world.entity.player.@org.jetbrains.annotations.NotNull Player player) {
    return player.getMainHandItem() == getStack();
  }

  protected abstract CompoundTag loadMachineTag();

  protected abstract void saveMachineTag(CompoundTag nbt);

  public abstract String containerSlotType();

  public abstract int containerSlotTier();

  @Override
  public li.cil.oc.api.machine.Machine machine() {
    if (_machine == null) {
      _machine = Machine.create(this);
      if (_machine != null && !level().isClientSide()) {
        var tag = loadMachineTag();
        if (!tag.isEmpty()) {
          var provider = level().registryAccess();
          try {
            _machine.load(tag, provider);
          } catch (Throwable e) {
            ComponentInventory.LOGGER.warn("Failed to load tablet machine data.", e);
          }
        }
        var node = _machine.node();
        if (node != null && node.network() == null) {
          li.cil.oc.api.Network.joinNewNetwork(node);
        }
        var tabletComponent = serverComponent();
        if (tabletComponent != null) {
          var tabletData = new li.cil.oc.core.impl.common.item.data.TabletData(getStack());
          if (tabletComponent.node instanceof Connector connector) {
            // the item's energy is the tablet's (written every tick, and by chargers); the component's buffer is the
            // last full save's: max(0, energy - buffer) kept an older, fuller buffer (back from a chest with more)
            connector.changeBuffer(tabletData.energy - connector.localBuffer());
            savedEnergy = connector.localBuffer();
          }
        }
        persistMachineState(true);
      }
    }
    return _machine;
  }

  /**
   * Writes the machine, its parts and its energy into the stack together (OC: writeToNBT). Without its state if
   * clearState, as OC: the stack may be one the machine no longer follows (moved, dropped), and a tablet resumed from
   * an older save ran its program on a newer drive - it comes back off.
   */
  public void writeToNBT(boolean clearState) {
    if (level().isClientSide()) return;
    persistMachineState(clearState); // first: its save pauses it, so the parts are saved as it left them
    saveComponents(level().registryAccess());
    setChanged(); // the parts with their saved data into the stack (they were saved into the copies only)
    var data = new li.cil.oc.core.impl.common.item.data.TabletData(getStack());
    saveEnergy(data);
    data.save(getStack());
  }

  private void saveEnergy(li.cil.oc.core.impl.common.item.data.TabletData data) {
    // (not before its machine took the item's energy: until then the buffer is the last full save's)
    if (Double.isNaN(savedEnergy)) return;
    var c = serverComponent();
    if (c != null && c.node instanceof Connector connector) {
      // chargers charge the item (Tablet.charge); this overwrote that with the machine's buffer every tick, so a
      // carried tablet never charged while the charger's energy was spent
      if (data.energy > savedEnergy) {
        connector.changeBuffer(data.energy - savedEnergy);
      }
      data.energy = connector.localBuffer();
      data.maxEnergy = connector.localBufferSize();
      savedEnergy = data.energy;
    }
  }

  /** It was dropped from the cache: it writes nothing more into its stack. */
  public void release() {
    released = true;
  }

  private void persistMachineState(boolean clearState) {
    if (_machine != null) {
      if (!level().isClientSide() && _machine.node() != null) {
        var tag = new CompoundTag();
        _machine.save(tag, level().registryAccess());
        if (clearState) tag.remove("state");
        saveMachineTag(tag);
      }
    }
    if (_tabletComponent != null) {
      if (!level().isClientSide()) {
        var componentTag = new CompoundTag();
        _tabletComponent.save(componentTag, level().registryAccess());
        var stack = getStack();
        var existing = CustomData.get(stack);
        CompoundTag t;
        if (existing != null && !existing.isEmpty()) {
          t = existing.copyTag();
        } else {
          t = new CompoundTag();
        }
        t.put(OCSettings.namespace + "component", componentTag);
        CustomData.set(stack, li.cil.oc.compat.CustomData.of(t));
      }
    }
  }

  public @Nullable Tablet serverComponent() {
    if (_tabletComponent == null) {
      if (!level().isClientSide()) {
        _tabletComponent = new Tablet(this);
        var stack = getStack();
        var existing = CustomData.get(stack);
        if (existing != null && !existing.isEmpty()) {
          var t = existing.copyTag();
          if (t.contains(OCSettings.namespace + "component")) {
            _tabletComponent.load(t.getCompound(OCSettings.namespace + "component"), level().registryAccess());
          }
        }
      }
    }
    return _tabletComponent;
  }

  @Override
  public Map<String, String> getDeviceInfo() {
    return deviceInfo;
  }

  @Override
  public Node node() {
    var m = machine();
    return m.node();
  }

  @Override
  public void onConnect(Node node) {
    if (node == node()) {
      connectComponents();
      var c = serverComponent();
      if (c != null) node.connect(c.node);
    } else if (node.host() instanceof li.cil.oc.api.internal.TextBuffer buffer) {
      buffer.setMaximumColorDepth(li.cil.oc.api.internal.TextBuffer.ColorDepth.FourBit);
      buffer.setMaximumResolution(80, 25);
    }
  }

  @Override
  public void connectItemNode(Node node) {
    ComponentInventory.super.connectItemNode(node);
    if (node == null) return;
    if (node.host() instanceof li.cil.oc.api.internal.TextBuffer buffer) {
      for (ManagedEnvironment env : componentEnvironments()) {
        if (env instanceof li.cil.oc.api.internal.Keyboard keyboard) {
          buffer.node().connect(keyboard.node());
        }
      }
    } else if (node.host() instanceof li.cil.oc.api.internal.Keyboard keyboard) {
      for (ManagedEnvironment env : componentEnvironments()) {
        if (env instanceof li.cil.oc.api.internal.TextBuffer buffer) {
          keyboard.node().connect(buffer.node());
        }
      }
    }
  }

  @Override
  public void onDisconnect(Node node) {
    if (node == node()) {
      disconnectComponents();
      var c = serverComponent();
      if (c != null) c.node.remove();
    }
  }

  @Override
  public void onMessage(Message message) {
  }

  @Override
  public ManagedEnvironment[] _components() {
    return _components;
  }

  @Override
  public void _components(ManagedEnvironment[] value) {
    _components = value;
  }

  @Override
  public ArrayList<ManagedEnvironment> updatingComponents() {
    return updatingComponents;
  }

  @Override
  public EnvironmentHost host() {
    return this;
  }

  @Override
  public boolean isSizeInventoryReady() {
    return true;
  }

  @Override
  public void onMachineConnect(Node node) {
    onConnect(node);
  }

  @Override
  public void onMachineDisconnect(Node node) {
    onDisconnect(node);
  }

  @Override
  public void setChanged() {
    if (!level().isClientSide()) {
      new li.cil.oc.core.impl.common.item.data.TabletData(getStack()).save(getStack());
    }
  }

  @Override
  public void load(CompoundTag nbt, HolderLookup.Provider provider) {
  }

  @Override
  public void save(CompoundTag nbt, HolderLookup.Provider provider) {
    writeToNBT(true);
  }

  public void update() {
    var level = level();
    if (!isInitialized) {
      isInitialized = true;
      connectComponents();
      if (level.isClientSide) {
        for (ManagedEnvironment env : componentEnvironments()) {
          if (env instanceof li.cil.oc.api.internal.TextBuffer buffer) {
            buffer.setMaximumColorDepth(li.cil.oc.api.internal.TextBuffer.ColorDepth.FourBit);
            buffer.setMaximumResolution(80, 25);
          }
        }
      }
    }
    if (!level.isClientSide) {
      var m = machine();
      if (isCreative() && level.getGameTime() % (long) OCSettings.get().tickFrequency == 0) {
        if (m.node() instanceof Connector conn) {
          conn.changeBuffer(Double.POSITIVE_INFINITY);
        }
      }
      m.update();
      updateComponents();
      var data = new li.cil.oc.core.impl.common.item.data.TabletData(getStack());
      var running = m.isRunning();
      data.isRunning = running;
      saveEnergy(data);
      data.save(getStack());
      if (player() != null) {
        player().getInventory().setChanged();
      }
      if (lastRunning != running) {
        lastRunning = running;
        persistMachineState(true);
        if (player() instanceof net.minecraft.server.level.ServerPlayer sp) {
          li.cil.oc.core.impl.common.PacketSender.sendMachineItemState(sp, getStack(), running);
        }
        if (running) {
          for (ManagedEnvironment env : componentEnvironments()) {
            if (env instanceof li.cil.oc.api.internal.TextBuffer buffer) {
              buffer.setPowerState(true);
            }
          }
        }
      }
    }
  }

  public void startMachine() {
    var m = machine();
    if (!m.isRunning()) {
      m.start();
      var lastError = m.lastError();
      if (lastError != null) {
        var p = player();
        if (p != null) {
          p.sendSystemMessage(net.minecraft.network.chat.Component.translatable("gui.opencomputers.analyzer.lasterror", net.minecraft.network.chat.Component.translatable(lastError)));
        }
      }
    }
  }

  public void stopMachine() {
    var m = machine();
    if (m.isRunning()) {
      m.stop();
    }
  }

  @SuppressWarnings("unused")
  public void player(Player p) {
  }

  private final java.util.concurrent.atomic.AtomicBoolean changeScheduled = new java.util.concurrent.atomic.AtomicBoolean();

  // called from the computer thread on every slice and GPU draw; the item's tag belongs to the server thread
  @Override
  public void markChanged() {
    if (!changeScheduled.getAndSet(true)) {
      li.cil.oc.core.impl.util.EventHandlerDelegate.get().scheduleServer(() -> {
        changeScheduled.set(false);
        if (!released) setChanged();
      });
    }
  }

  @Override
  public boolean canPlaceItem(int slot, @NotNull ItemStack stack) {
    if (slot != getContainerSize() - 1) return false;
    if (stack.isEmpty()) return false;
    var driver = li.cil.oc.api.API.driver.driverFor(stack, li.cil.oc.api.internal.Tablet.class);
    if (driver == null) return false;
    if (driver instanceof li.cil.oc.core.impl.integration.opencomputers.DriverScreen) return false;
    return driver.slot(stack).equals(containerSlotType()) && driver.tier(stack) <= containerSlotTier();
  }

  public boolean isCreative() {
    var data = new li.cil.oc.core.impl.common.item.data.TabletData(getStack());
    return data.tier == li.cil.oc.core.common.Tier.Four;
  }

  @Override
  public Iterable<ItemStack> internalComponents() {
    var items = new java.util.ArrayList<ItemStack>();
    for (int i = 0; i < getContainerSize(); i++) {
      var stack = getItem(i);
      if (!stack.isEmpty() && isComponentSlot(i, stack)) {
        items.add(stack);
      }
    }
    return items;
  }

  @Override
  public int componentSlot(String address) {
    var comps = componentEnvironments();
    for (int i = 0; i < comps.length; i++) {
      if (comps[i] != null && comps[i].node() != null && address.equals(comps[i].node().address())) {
        return i;
      }
    }
    return -1;
  }

  @Override
  public double xPosition() {
    var p = player();
    return p != null ? p.getX() : 0;
  }

  @Override
  public double yPosition() {
    var p = player();
    return p != null ? p.getY() + p.getEyeHeight() : 0;
  }

  @Override
  public double zPosition() {
    var p = player();
    return p != null ? p.getZ() : 0;
  }

  @Override
  public Level world() {
    return creationLevel;
  }

  @Override
  public Direction facing() {
    var p = player();
    return p != null ? RotationHelper.fromYaw(p.getYRot()) : Direction.NORTH;
  }

  @Override
  public Direction toLocal(Direction value) {
    return RotationHelper.toLocal(Direction.NORTH, facing(), value);
  }

  @Override
  public Direction toGlobal(Direction value) {
    return RotationHelper.toGlobal(Direction.NORTH, facing(), value);
  }

}
