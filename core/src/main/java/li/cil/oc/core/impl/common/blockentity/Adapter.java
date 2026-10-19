package li.cil.oc.core.impl.common.blockentity;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import li.cil.oc.api.Driver;
import li.cil.oc.api.Network;
import li.cil.oc.api.driver.DeviceInfo;
import li.cil.oc.api.driver.DriverBlock;
import li.cil.oc.api.network.Analyzable;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.core.Constants;
import li.cil.oc.core.common.Slot;
import li.cil.oc.core.impl.OCSettings;
import li.cil.oc.core.impl.common.PacketSender;
import li.cil.oc.core.impl.common.blockentity.traits.BlockEntity;
import li.cil.oc.core.impl.common.blockentity.traits.ComponentInventory;
import li.cil.oc.core.impl.common.blockentity.traits.OpenSides;
import li.cil.oc.core.impl.util.InventoryUtils;
import li.cil.oc.core.util.ResultWrapper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public class Adapter extends BlockEntity implements li.cil.oc.api.network.Environment, ComponentInventory, OpenSides, Analyzable, li.cil.oc.api.internal.Adapter, DeviceInfo {
  public static BlockEntityType<?> TYPE;

  public final Node node = Network.newNode(this, Visibility.Network).create();
  private final li.cil.oc.api.network.Environment[] blocks = new li.cil.oc.api.network.Environment[6];
  private final ArrayList<ManagedEnvironment> updatingBlocks = new ArrayList<>();
  private final BlockData[] blocksData = new BlockData[6];
  // per side, whether it has been empty since this adapter was loaded: data saved before blocks were recorded is the
  // block's there until then
  private final boolean[] seenEmpty = new boolean[6];
  private final net.minecraft.world.level.block.Block[] lastBlocks = new net.minecraft.world.level.block.Block[6];
  private final Map<String, String> deviceInfo = Map.of(
    DeviceInfo.DeviceAttribute.Class, DeviceInfo.DeviceClass.Bus,
    DeviceInfo.DeviceAttribute.Description, "Adapter",
    DeviceInfo.DeviceAttribute.Vendor, Constants.DeviceInfo.DefaultVendor,
    DeviceInfo.DeviceAttribute.Product, "Multiplug Ext.1"
  );
  private final ItemStack[] _items = new ItemStack[getContainerSize()];
  private final boolean[] _openSides = {true, true, true, true, true, true};
  private ManagedEnvironment[] _components;

  public Adapter(BlockPos pos, BlockState state) {
    super(TYPE, pos, state);
    for (int i = 0; i < 6; i++) {
      blocks[i] = null;
      blocksData[i] = null;
    }
  }

  @Override
  public Node node() {
    return node;
  }

  @Override
  public Level world() {
    return getLevel();
  }

  @Override
  public ItemStack[] items() {
    return _items;
  }

  @Override
  public void updateItems(int slot, ItemStack stack) {
    _items[slot] = stack;
  }

  @Override
  public int x() {
    return worldPosition.getX();
  }

  @Override
  public int y() {
    return worldPosition.getY();
  }

  @Override
  public int z() {
    return worldPosition.getZ();
  }

  @Override
  public double xPosition() {
    return worldPosition.getX() + 0.5;
  }

  @Override
  public double yPosition() {
    return worldPosition.getY() + 0.5;
  }

  @Override
  public double zPosition() {
    return worldPosition.getZ() + 0.5;
  }


  @Override
  public boolean isConnected() {
    return node.address() != null && node.network() != null;
  }

  @Override
  public void initialize() {
    super.initialize();
    if (level() != null && isServer()) Network.joinOrCreateNetwork(this);
  }

  @Override
  public void dispose() {
    super.dispose();
    if (isServer() && node != null) {
      node.remove();
    }
  }

  @Override
  public void onConnect(Node node) {
    if (node == this.node) {
      connectComponents();
      neighborChanged();
    }
  }

  @Override
  public void onDisconnect(Node node) {
    ComponentInventory.super.onDisconnect(node);
    if (node == this.node) updatingBlocks.clear();
  }

  @Override
  public void onMessage(li.cil.oc.api.network.Message message) {
  }

  @Override
  public Object result(Object... args) {
    return ResultWrapper.result(args);
  }

  @Override
  public java.util.Map<String, String> getDeviceInfo() {
    return deviceInfo;
  }

  @Override
  public boolean[] openSides() {
    return _openSides;
  }

  @Override
  public void openSides(boolean[] value) {
    if (value.length == _openSides.length) {
      System.arraycopy(value, 0, _openSides, 0, _openSides.length);
    }
  }

  @Override
  public boolean isSideOpen(Direction side) {
    return _openSides[side.ordinal()];
  }

  @Override
  public void setSideOpen(Direction side, boolean value) {
    _openSides[side.ordinal()] = value;
    if (isServer()) {
      setChanged();
      PacketSender.sendAdapterState(this, compressSides());
      level().playSeededSound(null, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5,
        net.minecraft.sounds.SoundEvents.PISTON_EXTEND, net.minecraft.sounds.SoundSource.BLOCKS,
        0.5f, level().random.nextFloat() * 0.25f + 0.7f, level().random.nextLong());
      level().updateNeighborsAt(worldPosition, block());
      neighborChanged(side);
    } else {
      level().sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
  }

  @Override
  public byte compressSides() {
    byte result = 0;
    for (int i = 0; i < 6; i++) {
      if (_openSides[i]) result |= (byte) (1 << i);
    }
    return result;
  }

  @Override
  public void uncompressSides(byte value) {
    for (int i = 0; i < 6; i++) {
      _openSides[i] = (value & (1 << i)) != 0;
    }
  }

  @Override
  public li.cil.oc.api.network.EnvironmentHost host() {
    return this;
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
  public java.util.ArrayList<ManagedEnvironment> updatingComponents() {
    return updatingBlocks;
  }

  @Override
  public void spawnStackInWorld(ItemStack stack) {
    if (isServer()) {
      InventoryUtils.spawnStackInWorld(position(), stack, null, null);
    }
  }

  @Override
  public void spawnStackInWorld(ItemStack stack, Direction side) {
    if (isServer()) {
      InventoryUtils.spawnStackInWorld(position(), stack, side, null);
    }
  }

  @Override
  public void dropAllSlots() {
    for (int i = 0; i < getContainerSize(); i++) {
      dropSlot(i);
    }
  }

  @Override
  public void dropSlot(int slot) {
    dropSlot(slot, 1, null);
  }

  @Override
  public void dropSlot(int slot, int count, Direction direction) {
    var removed = removeItem(slot, count);
    if (!removed.isEmpty()) {
      if (direction != null) spawnStackInWorld(removed, direction);
      else spawnStackInWorld(removed);
    }
  }

  @Override
  public boolean isUseableByPlayer(Player player) {
    return true;
  }

  @Override
  public void clearContent() {
    for (int i = 0; i < getContainerSize(); i++) {
      setItem(i, ItemStack.EMPTY);
    }
  }

  @Override
  public Node[] onAnalyze(Player player, Direction side, float hitX, float hitY, float hitZ) {
    var result = new ArrayList<Node>();
    for (var block : blocks)
      if (block != null) {
        var n = block.node();
        if (n != null) result.add(n);
      }
    ManagedEnvironment[] adapterComps = this.componentEnvironments();
    for (var comp : adapterComps)
      if (comp != null) {
        var n = comp.node();
        if (n != null) result.add(n);
      }
    return result.toArray(new Node[0]);
  }

  public void updateEntity() {
    super.updateEntity();
    if (!updatingBlocks.isEmpty()) {
      for (var block : updatingBlocks) block.update();
    }
  }

  @SuppressWarnings("SuspiciousMethodCalls")
  public void neighborChanged(Direction d) {
    if (node.network() != null) {
      var nx = worldPosition.getX() + d.getStepX();
      var ny = worldPosition.getY() + d.getStepY();
      var nz = worldPosition.getZ() + d.getStepZ();
      var neighborPos = new BlockPos(nx, ny, nz);
      var te = level().getBlockEntity(neighborPos);
      if (te instanceof Environment) return;
      var currentBlock = level().getBlockState(neighborPos).getBlock();
      if (level().getBlockState(neighborPos).isAir()) seenEmpty[d.ordinal()] = true;
      var blockChanged = lastBlocks[d.ordinal()] != currentBlock;
      // the block's own face toward the adapter, as the driver API says (this passed the far face: a block configured
      // per side was asked about its other end)
      var face = d.getOpposite();
      var newDriver = Driver.driverFor(level(), neighborPos, face);
      if (newDriver != null && isSideOpen(d)) {
        if (blocks[d.ordinal()] != null) {
          var oldEnv = blocks[d.ordinal()];
          var driver = blocksData[d.ordinal()] != null ? blocksData[d.ordinal()].driver : null;
          if (!Objects.equals(newDriver, driver) || blockChanged) {
            // the same block made up of other drivers now keeps its address, as it does across a reload (it got a new
            // one while the game ran, e.g. when a machine's side configuration changed); not another device in the same
            // block (a cable bus part swapped for another)
            CompoundTag kept = null;
            // what it went by, names carried from before included
            var keptData = blocksData[d.ordinal()];
            var keptIdentity = keptData != null && keptData.identity != null ? keptData.identity : identityOf(oldEnv);
            if (!blockChanged && oldEnv.node() != null) {
              kept = new CompoundTag();
              oldEnv.node().save(kept, level().registryAccess()); // its address
            }
            blocks[d.ordinal()] = null;
            updatingBlocks.remove(oldEnv);
            blocksData[d.ordinal()] = null;
            node.disconnect(oldEnv.node());
            var environment = newDriver.createEnvironment(level(), neighborPos, face);
            if (environment != null) {
              boolean same = kept != null && sameDevice(keptIdentity, identityOf(environment));
              if (same && environment instanceof li.cil.oc.core.impl.server.driver.CompoundBlockEnvironment compound) {
                compound.loadAddress(kept, level().registryAccess());
              }
              blocks[d.ordinal()] = environment;
              if (environment.canUpdate()) updatingBlocks.add(environment);
              blocksData[d.ordinal()] = new BlockData(environment.getClass().getName(), new CompoundTag(), newDriver, blockId(currentBlock),
                same ? carried(keptIdentity, identityOf(environment)) : identityOf(environment));
              lastBlocks[d.ordinal()] = currentBlock;
              node.connect(environment.node());
            }
          }
        } else {
          if (!isSideOpen(d)) return;
          var environment = newDriver.createEnvironment(level(), neighborPos, face);
          if (environment != null) {
            blocks[d.ordinal()] = environment;
            if (environment.canUpdate()) updatingBlocks.add(environment);
            var saved = blocksData[d.ordinal()];
            // only the same block (the same id, the same device of those it holds) gets its data and address back; its
            // address even if other drivers make it up now. A different block placed there is a device of its own (one
            // made up of the same drivers took over the old one's address and data). Saved before blocks were recorded
            // (no id, no identity): the block there since the adapter was loaded is the one it was saved with (the
            // adapter asked another face then: its drivers may differ now); one placed later, as OC, gets both only for
            // the same set of drivers, which load checks (any block placed there took the old one's address).
            boolean unrecorded = saved != null && (saved.block == null || saved.block.isEmpty()) && saved.identity == null;
            boolean sameBlock = saved != null && (unrecorded ? !seenEmpty[d.ordinal()]
              : (saved.block == null || saved.block.isEmpty() || blockId(currentBlock).equals(saved.block))
              && sameDevice(saved.identity, identityOf(environment)));
            if ((sameBlock || unrecorded) && saved.name.equals(environment.getClass().getName())) {
              environment.load(saved.data, level().registryAccess());
              if (sameBlock && environment instanceof li.cil.oc.core.impl.server.driver.CompoundBlockEnvironment compound) {
                compound.loadAddress(saved.data, level().registryAccess());
              }
            }
            blocksData[d.ordinal()] = new BlockData(environment.getClass().getName(), new CompoundTag(), newDriver, blockId(currentBlock),
              sameBlock ? carried(saved.identity, identityOf(environment)) : identityOf(environment));
            lastBlocks[d.ordinal()] = currentBlock;
            node.connect(environment.node());
          }
        }
      } else {
        if (blocks[d.ordinal()] != null) {
          var environment = (ManagedEnvironment) blocks[d.ordinal()];
          node.disconnect(environment.node());
          environment.save(blocksData[d.ordinal()].data, level().registryAccess());
          if (environment.node() != null) environment.node().remove();
          blocks[d.ordinal()] = null;
          updatingBlocks.remove(environment);
        }
        lastBlocks[d.ordinal()] = null;
      }
    }
  }

  public void neighborChanged() {
    if (node.network() != null) {
      for (var d : Direction.values()) neighborChanged(d);
    }
  }

  @Override
  public int getContainerSize() {
    return 1;
  }

  @Override
  public boolean canPlaceItem(int slot, @NotNull ItemStack stack) {
    if (slot == 0) {
      var driver = Driver.driverFor(stack, getClass());
      return driver != null && driver.slot(stack).equals(Slot.Upgrade);
    }
    return false;
  }

  @Override
  public void readFromNBTForServer(CompoundTag nbt) {
    super.readFromNBTForServer(nbt);
    // Java interfaces cannot hook into the base class's NBT methods like the original Scala traits did,
    // so the inventory has to be loaded explicitly.
    var provider = getEffectiveProvider();
    if (provider != null) load(nbt, provider);
    if (nbt.contains(OCSettings.namespace + "openSides")) {
      uncompressSides(nbt.getByte(OCSettings.namespace + "openSides"));
    }
    var blocksNbt = nbt.getList(OCSettings.namespace + "adapter.blocks", Tag.TAG_COMPOUND);
    for (int i = 0; i < Math.min(blocksNbt.size(), blocksData.length); i++) {
      var blockNbt = blocksNbt.getCompound(i);
      if (blockNbt.contains("name") && blockNbt.contains("data")) {
        java.util.Set<String> identity = null; // saved before it was recorded
        if (blockNbt.contains("identity", Tag.TAG_LIST)) {
          identity = new java.util.TreeSet<>();
          var names = blockNbt.getList("identity", Tag.TAG_STRING);
          for (int n = 0; n < names.size(); n++) identity.add(names.getString(n));
        }
        blocksData[i] = new BlockData(blockNbt.getString("name"), blockNbt.getCompound("data"), blockNbt.getString("block"), identity);
      }
    }
  }

  @Override
  public void writeToNBTForServer(CompoundTag nbt) {
    super.writeToNBTForServer(nbt);
    var provider = getEffectiveProvider();
    if (provider != null) save(nbt, provider);
    nbt.putByte(OCSettings.namespace + "openSides", compressSides());
    var blocksNbt = new ListTag();
    for (int i = 0; i < blocks.length; i++) {
      var blockNbt = new CompoundTag();
      if (blocksData[i] != null) {
        if (blocks[i] != null)
          ((ManagedEnvironment) blocks[i]).save(blocksData[i].data, getEffectiveProvider());
        blockNbt.putString("name", blocksData[i].name);
        blockNbt.put("data", blocksData[i].data.copy()); // the chunk is written later, off the server thread
        if (blocksData[i].block != null) blockNbt.putString("block", blocksData[i].block);
        if (blocksData[i].identity != null) {
          var names = new ListTag();
          for (String name : blocksData[i].identity) names.add(net.minecraft.nbt.StringTag.valueOf(name));
          blockNbt.put("identity", names);
        }
      }
      blocksNbt.add(blockNbt);
    }
    nbt.put(OCSettings.namespace + "adapter.blocks", blocksNbt);
  }

  @Override
  public void readFromNBTForClient(CompoundTag nbt) {
    super.readFromNBTForClient(nbt);
    if (nbt.contains(OCSettings.namespace + "openSides")) {
      uncompressSides(nbt.getByte(OCSettings.namespace + "openSides"));
    }
  }

  @Override
  public void writeToNBTForClient(CompoundTag nbt) {
    super.writeToNBTForClient(nbt);
    nbt.putByte(OCSettings.namespace + "openSides", compressSides());
  }

  @Override
  public ItemStack[] pendingRemovals() {
    return null;
  }

  @Override
  public ItemStack[] pendingAdds() {
    return null;
  }

  @Override
  public boolean isSizeInventoryReady() {
    return true;
  }

  // block: the id of the block the data came from; identity: the names of what it was made up of (only the same device
  // gets its address back: one id holds many, as AE2's cable bus parts or Mekanism's bounding blocks); null: unknown
  private record BlockData(String name, CompoundTag data, DriverBlock driver, String block, java.util.Set<String> identity) {
    BlockData(String name, CompoundTag data, String block, java.util.Set<String> identity) {
      this(name, data, null, block, identity);
    }
  }

  private static java.util.Set<String> identityOf(Object environment) {
    return environment instanceof li.cil.oc.core.impl.server.driver.CompoundBlockEnvironment compound ? compound.identity() : java.util.Set.of();
  }

  /**
   * The same device as before, of those in the same block: one of the names it went by is still among them (a part
   * added elsewhere on a cable bus, or energy coming and going, keeps it; an interface swapped for an export bus does
   * not). The names of generic drivers count only when one side has nothing else (energy is no device of its own when
   * both are more: two machines behind one bounding block both have it); nothing known tells nothing.
   */
  private static boolean sameDevice(java.util.Set<String> before, java.util.Set<String> now) {
    if (before == null || before.isEmpty() || now.isEmpty()) return true;
    var specificBefore = specific(before);
    var specificNow = specific(now);
    // generic parts alone on both sides: nothing tells them apart but the block, which matched (with the tank driver on,
    // a face whose energy gave way to a fluid handler got a new address)
    if (specificBefore.isEmpty() && specificNow.isEmpty()) return true;
    var a = specificBefore.isEmpty() || specificNow.isEmpty() ? before : specificBefore;
    var b = specificBefore.isEmpty() || specificNow.isEmpty() ? now : specificNow;
    for (String name : a) if (b.contains(name)) return true;
    return false;
  }

  /**
   * What the same device goes by now: a device left with generic parts alone keeps its names from before (energy alone
   * matched what came next: an export bus placed where the interface was, energy beside both, got its address).
   */
  private static java.util.Set<String> carried(java.util.Set<String> before, java.util.Set<String> now) {
    if (before == null || !specific(now).isEmpty()) return now;
    var result = new java.util.TreeSet<>(now);
    result.addAll(specific(before));
    return result;
  }

  private static java.util.Set<String> specific(java.util.Set<String> names) {
    var result = new java.util.HashSet<String>();
    for (String name : names) if (!name.startsWith(li.cil.oc.core.impl.server.driver.CompoundBlockEnvironment.GENERIC)) result.add(name);
    return result;
  }

  private static String blockId(net.minecraft.world.level.block.Block block) {
    return block == null ? "" : String.valueOf(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block));
  }

}
