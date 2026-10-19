package li.cil.oc.core.impl.server.component;

import java.util.Map;
import java.util.function.Consumer;
import li.cil.oc.api.Network;
import li.cil.oc.api.driver.DeviceInfo;
import li.cil.oc.api.driver.DriverBlock;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import li.cil.oc.core.Constants;
import li.cil.oc.core.impl.OCSettings;
import li.cil.oc.core.impl.util.BlockPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

public abstract class UpgradeMFBase extends AbstractManagedEnvironment implements DeviceInfo {
  public final EnvironmentHost host;
  public final BlockPosition coord;
  public final Direction dir;

  public final Node node = Network.newNode(this, Visibility.None)
    .withConnector()
    .create();
  private final Map<String, String> deviceInfo = new java.util.HashMap<>() {{
    put(DeviceAttribute.Class, DeviceClass.Bus);
    put(DeviceAttribute.Description, "Remote Adapter");
    put(DeviceAttribute.Vendor, Constants.DeviceInfo.Scummtech);
    put(DeviceAttribute.Product, "ERR NAME NOT FOUND");
  }};
  protected Environment otherEnv = null;
  protected li.cil.oc.api.network.ManagedEnvironment otherDrvEnv = null;
  protected DriverBlock otherDrvDriver = null;
  // the block entity the bound block's environment was made for
  private BlockEntity boundBlockEntity = null;
  protected BlockData blockData = null;

  public UpgradeMFBase(EnvironmentHost host, BlockPosition coord, Direction dir) {
    this.host = host;
    this.coord = coord;
    this.dir = dir;
  }

  @Override
  public Map<String, String> getDeviceInfo() {
    return deviceInfo;
  }

  @Override
  public boolean canUpdate() {
    return true;
  }

  protected abstract void connectToTileNode(BlockEntity tile, Consumer<Node> consumer);

  protected abstract void registerBlockChangeListener();

  protected abstract void unregisterBlockChangeListener();

  protected abstract boolean consumeEnergy(double amount);

  protected void updateBoundState() {
    if (node.network() != null && coord.level() != null && coord.level().dimension().location().hashCode() == host.level().dimension().location().hashCode() && coord.toVec3().distanceTo(new Vec3(host.xPosition(), host.yPosition(), host.zPosition())) <= OCSettings.get().mfuRange) {
      BlockPos blockPos = new BlockPos(coord.x(), coord.y(), coord.z());
      BlockEntity te = host.level().getBlockEntity(blockPos);
      if (otherEnv != null && otherEnv instanceof BlockEntity) {
        connectToTileNode((BlockEntity) otherEnv, node::disconnect);
        otherEnv = null;
      }
      if (te instanceof Environment) {
        letGoOfBlock(true);
        otherEnv = (Environment) te;
        connectToTileNode(te, node::connect);
      } else {
        var w = coord.level();
        DriverBlock newDriver = li.cil.oc.api.API.driver.driverFor(w, blockPos, dir);
        if (newDriver != null) {
          // as OC: by equality (a driver is made anew for each look: every block update at the bound place, a furnace
          // lighting up, gave the block a new address). Other drivers: another block, a new address, as OC. The same
          // drivers with another block entity (the block replaced by its like): made anew with its data and address
          // (the environment held on to the removed block entity).
          if (otherDrvEnv != null) {
            boolean driversChanged = !java.util.Objects.equals(otherDrvDriver, newDriver);
            if (driversChanged || te != boundBlockEntity) {
              letGoOfBlock(!driversChanged);
              if (driversChanged) blockData = null;
            }
          }
          if (otherDrvEnv == null) {
            // a block not yet known gets its saved data back (its address came anew with each load)
            var environment = newDriver.createEnvironment(w, blockPos, dir);
            if (environment != null) {
              otherDrvEnv = environment;
              otherDrvDriver = newDriver;
              boundBlockEntity = te;
              if (blockData != null && blockData.name.equals(environment.getClass().getName())) {
                environment.load(blockData.data, host.level().registryAccess());
              }
              blockData = new BlockData(environment.getClass().getName(), new CompoundTag());
              node.connect(environment.node());
              host.markChanged();
            }
          }
        } else {
          letGoOfBlock(true);
        }
      }
    }
  }

  /**
   * Lets go of the bound block's environment, keeping its data for when it comes back if asked. Taken from the field
   * first: the disconnect clears the field (onDisconnect), and the lines after it threw.
   */
  private void letGoOfBlock(boolean keepData) {
    var env = otherDrvEnv;
    otherDrvEnv = null;
    otherDrvDriver = null;
    boundBlockEntity = null;
    if (env == null) return;
    if (keepData && blockData != null) env.save(blockData.data, host.level().registryAccess());
    node.disconnect(env.node());
    if (env.node() != null) env.node().remove();
    host.markChanged();
  }

  private void disconnect() {
    if (otherEnv != null && otherEnv instanceof BlockEntity) {
      connectToTileNode((BlockEntity) otherEnv, node::disconnect);
      otherEnv = null;
    }
    letGoOfBlock(true);
  }

  @Override
  public void update() {
    super.update();
    if (otherDrvEnv != null && otherDrvEnv.canUpdate()) {
      otherDrvEnv.update();
    }
    if (host.level().getGameTime() % OCSettings.get().tickFrequency == 0) {
      if (!consumeEnergy(OCSettings.get().mfuCost * OCSettings.get().tickFrequency *
        coord.toVec3().distanceTo(new Vec3(host.xPosition(), host.yPosition(), host.zPosition())))) {
        disconnect();
      }
    }
  }

  @Override
  public void onConnect(Node node) {
    super.onConnect(node);
    if (node == this.node) {
      registerBlockChangeListener();
      updateBoundState();
    }
  }

  @Override
  public void onDisconnect(Node node) {
    super.onDisconnect(node);
    if (otherEnv != null && otherEnv instanceof BlockEntity) {
      connectToTileNode((BlockEntity) otherEnv, otherNode -> {
        if (node == otherNode) otherEnv = null;
      });
    }
    if (otherDrvEnv != null && node == otherDrvEnv.node()) {
      otherDrvEnv = null;
      otherDrvDriver = null;
    }
    if (node == this.node) {
      unregisterBlockChangeListener();
    }
  }

  @Override
  public void load(CompoundTag nbt, HolderLookup.Provider provider) {
    super.load(nbt, provider);
    CompoundTag blockNbt = nbt.getCompound(OCSettings.namespace + "adapter.block");
    if (blockNbt.contains("name") && blockNbt.contains("data")) {
      blockData = new BlockData(blockNbt.getString("name"), blockNbt.getCompound("data"));
    }
  }

  @Override
  public void save(CompoundTag nbt, HolderLookup.Provider provider) {
    super.save(nbt, provider);
    CompoundTag blockNbt = new CompoundTag();
    if (blockData != null) {
      if (otherDrvEnv != null) otherDrvEnv.save(blockData.data, provider);
      blockNbt.putString("name", blockData.name);
      blockNbt.put("data", blockData.data.copy()); // the chunk is written later, off the server thread
    }
    nbt.put(OCSettings.namespace + "adapter.block", blockNbt);
  }

  protected record BlockData(String name, CompoundTag data) {
  }
}
