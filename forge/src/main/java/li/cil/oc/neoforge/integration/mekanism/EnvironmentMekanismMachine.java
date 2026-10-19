package li.cil.oc.neoforge.integration.mekanism;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

import li.cil.oc.api.driver.NamedBlock;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.DocumentedPeripheral;
import li.cil.oc.api.network.ManagedPeripheral;
import li.cil.oc.core.impl.integration.ManagedBlockEntityEnvironment;
import li.cil.oc.core.util.ResultWrapper;
import mekanism.common.integration.computer.BoundMethodHolder;
import mekanism.common.integration.computer.ComputerException;
import mekanism.common.integration.computer.Convertable;
import mekanism.common.integration.computer.IComputerTile;
import mekanism.common.integration.computer.MethodHelpData;

public class EnvironmentMekanismMachine extends ManagedBlockEntityEnvironment<IComputerTile> implements ManagedPeripheral, NamedBlock, DocumentedPeripheral {

  private volatile Holder holder = new Holder();
  private long boundAt = Long.MIN_VALUE;

  public EnvironmentMekanismMachine(final IComputerTile tile) {
    super(tile, componentName(tile));
    tile.getComputerMethods(holder);
    boundAt = gameTime();
  }

  // A multiblock (induction matrix, dynamic tank, boiler, turbine, reactor ports) binds its methods to the multiblock
  // formed at that moment, and says so with isComputerCapabilityPersistent() == false: bound once, the methods were
  // missing when the adapter came before the multiblock, and stale after it re-formed. Bound again once a tick, on
  // the server thread (the method list is also read from computer threads: they only read what was bound).
  private Holder holder() {
    return holder;
  }

  private void rebind() {
    if (!isMultiblock() || BlockEntity.isComputerCapabilityPersistent()) return;
    final long now = gameTime();
    if (now == boundAt) return;
    boundAt = now;
    final Holder fresh = new Holder();
    BlockEntity.getComputerMethods(fresh);
    holder = fresh;
  }

  // Only multiblocks bind to something that changes (without ComputerCraft or OC2 every Mekanism tile says it is not
  // persistent: all of them rebinding every tick was a waste). A program that made a proxy before the multiblock
  // formed calls the new methods with component.invoke or a proxy made again.
  private boolean isMultiblock() {
    // only those whose methods follow the multiblock (reactor ports, which do not, re-bound every tick)
    return BlockEntity instanceof mekanism.common.tile.prefab.TileEntityMultiblock<?> multiblock && multiblock.exposesMultiblockToComputer();
  }

  @Override
  public boolean canUpdate() {
    return isMultiblock() && !BlockEntity.isComputerCapabilityPersistent();
  }

  @Override
  public void update() {
    super.update();
    rebind();
  }

  private long gameTime() {
    return BlockEntity instanceof net.minecraft.world.level.block.entity.BlockEntity be && be.getLevel() != null ? be.getLevel().getGameTime() : Long.MIN_VALUE + 1;
  }

  @Override
  public String doc(final String method) {
    final var candidates = holder().methods(method);
    if (candidates.isEmpty()) {
      return "";
    }
    final var data = candidates.iterator().next();
    if (data.isHelpMethod()) {
      return "";
    }
    final MethodHelpData help = MethodHelpData.from(data);
    final StringBuilder sb = new StringBuilder("function(");
    final List<MethodHelpData.Param> params = help.params();
    if (params != null && !params.isEmpty()) {
      for (int i = 0; i < params.size(); i++) {
        if (i > 0) {
          sb.append(", ");
        }
        final MethodHelpData.Param param = params.get(i);
        sb.append(param.name()).append(": ").append(shortType(param.type()));
      }
    }
    sb.append(")");
    final String returnType = shortType(help.returns().type());
    if (!"nothing".equals(returnType)) {
      sb.append(": ").append(returnType);
    }
    final String description = help.description();
    if (description != null && !description.isEmpty()) {
      sb.append(" -- ").append(description.trim());
    }
    return sb.toString();
  }

  private static String shortType(final String type) {
    final int space = type.indexOf(' ');
    final String first = space < 0 ? type : type.substring(0, space);
    return first.toLowerCase(Locale.ROOT);
  }

  @Override
  public String preferredName() {
    return componentName(BlockEntity);
  }

  @Override
  public int priority() {
    return 1;
  }

  private static String componentName(final IComputerTile tile) {
    final String name = tile.getComputerName();
    if (name != null && !name.isEmpty()) {
      return name;
    }
    if (tile instanceof net.minecraft.world.level.block.entity.BlockEntity blockEntity) {
      final var key = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(blockEntity.getBlockState().getBlock());
      return key.toString();
    }
    return "mekanism_machine";
  }

  @Override
  public String[] methods() {
    return holder().methodNames();
  }

  @Override
  public Object[] invoke(final String method, final Context context, final Arguments args) {
    rebind(); // on the server thread (the callbacks are not direct): the multiblock as it is this tick
    final Collection<BoundMethodHolder.BoundMethodData<?>> candidates = holder().methods(method);
    if (candidates.isEmpty()) {
      throw new IllegalArgumentException("no such method: " + method);
    }
    final BoundMethodHolder.BoundMethodData<?> data = select(candidates, args.count());
    try {
      final Object result = call(data, new OCComputerHelper(args));
      return result == OCComputerHelper.VOID ? ResultWrapper.result() : ResultWrapper.result(result);
    } catch (ComputerException e) {
      throw new IllegalArgumentException(e.getMessage());
    }
  }

  private static BoundMethodHolder.BoundMethodData<?> select(final Collection<BoundMethodHolder.BoundMethodData<?>> candidates, final int argCount) {
    for (final BoundMethodHolder.BoundMethodData<?> md : candidates) {
      if (md.argumentNames().length == argCount) {
        return md;
      }
    }
    final StringBuilder expected = new StringBuilder();
    for (final BoundMethodHolder.BoundMethodData<?> md : candidates) {
      if (!expected.isEmpty()) {
        expected.append(" or ");
      }
      expected.append(md.argumentNames().length);
    }
    throw new IllegalArgumentException(String.format("Found %d arguments, expected %s", argCount, expected));
  }

  private static Object call(final BoundMethodHolder.BoundMethodData<?> data, final OCComputerHelper helper) throws ComputerException {
    final Object result = data.call(helper);
    if (result instanceof Convertable<?> convertable) {
      return convertable.convert(helper);
    }
    return result;
  }

  private static final class Holder extends BoundMethodHolder {
    String[] methodNames() {
      return methodNames.get();
    }

    Collection<BoundMethodHolder.BoundMethodData<?>> methods(final String name) {
      return methods.get(name);
    }
  }
}
