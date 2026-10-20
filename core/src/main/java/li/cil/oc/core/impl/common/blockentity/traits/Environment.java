package li.cil.oc.core.impl.common.blockentity.traits;

import li.cil.oc.api.network.Node;


public interface Environment extends li.cil.oc.api.network.Environment, li.cil.oc.api.network.EnvironmentHost {
  @SuppressWarnings("unused")
  boolean isConnected();

  @SuppressWarnings("unused")
  void initialize();

  @SuppressWarnings("unused")
  void dispose();

  @SuppressWarnings("unused")
  void readFromNBTForServer(net.minecraft.nbt.CompoundTag nbt);

  @SuppressWarnings("unused")
  void writeToNBTForServer(net.minecraft.nbt.CompoundTag nbt);

  @SuppressWarnings("unused")
  void readFromNBTForClient(net.minecraft.nbt.CompoundTag nbt);

  @SuppressWarnings("unused")
  void writeToNBTForClient(net.minecraft.nbt.CompoundTag nbt);

  @SuppressWarnings("unused")
  Object result(Object... args);

  default long getLastOperation() {
    return 0;
  }

  // A connector that leaves takes its energy along. OC set its buffer size to 0 and back here, to push its energy into
  // the network: it kept the energy and the network got it as well (all of it when the rest was empty) - on a chunk
  // unload, the block saved with it and the loaded part gained it.
  @Override
  default void onDisconnect(Node node) {
  }
}
