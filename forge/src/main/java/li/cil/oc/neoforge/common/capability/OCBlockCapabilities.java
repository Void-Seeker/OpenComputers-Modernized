package li.cil.oc.neoforge.common.capability;

import li.cil.oc.api.capability.SimpleComponentProvider;
import li.cil.oc.api.internal.Colored;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.SidedComponent;
import li.cil.oc.api.network.SidedEnvironment;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import li.cil.oc.neoforge.compat.BlockCapability;
import li.cil.oc.neoforge.compat.CapabilityRegistrar;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.Nullable;

public final class OCBlockCapabilities {
  public static final BlockCapability<Environment, @Nullable Direction> ENVIRONMENT =
    BlockCapability.of(CapabilityManager.get(new CapabilityToken<Environment>() {
    }));

  public static final BlockCapability<SidedEnvironment, @Nullable Direction> SIDED_ENVIRONMENT =
    BlockCapability.of(CapabilityManager.get(new CapabilityToken<SidedEnvironment>() {
    }));

  public static final BlockCapability<Colored, @Nullable Direction> COLORED =
    BlockCapability.of(CapabilityManager.get(new CapabilityToken<Colored>() {
    }));

  public static final BlockCapability<SimpleComponentProvider, @Nullable Direction> SIMPLE_COMPONENT_PROVIDER =
    BlockCapability.of(CapabilityManager.get(new CapabilityToken<SimpleComponentProvider>() {
    }));

  private OCBlockCapabilities() {
  }

  /**
   * Registers OC's capability types with Forge (mod event bus).
   */
  public static void registerTypes(final RegisterCapabilitiesEvent event) {
    event.register(Environment.class);
    event.register(SidedEnvironment.class);
    event.register(Colored.class);
    event.register(SimpleComponentProvider.class);
  }

  public static void register(final CapabilityRegistrar event, final Block[] blocks) {
    event.registerBlock(ENVIRONMENT,
      (level, pos, state, blockEntity, side) -> blockEntity instanceof Environment environment ? environment : null,
      blocks);

    event.registerBlock(SIDED_ENVIRONMENT,
      (level, pos, state, blockEntity, side) -> {
        if (blockEntity instanceof SidedEnvironment sidedEnvironment) return sidedEnvironment;
        if (blockEntity instanceof Environment environment && blockEntity instanceof SidedComponent sidedComponent) {
          return new SidedComponentEnvironment(environment, sidedComponent);
        }
        return null;
      },
      blocks);

    event.registerBlock(COLORED,
      (level, pos, state, blockEntity, side) -> blockEntity instanceof Colored colored ? colored : null,
      blocks);
  }

  private record SidedComponentEnvironment(Environment environment,
                                           SidedComponent sidedComponent) implements SidedEnvironment {
    @Override
    public Node sidedNode(final Direction side) {
      return sidedComponent.canConnectNode(side) ? environment.node() : null;
    }

    @Override
    public boolean canConnect(final Direction side) {
      return sidedComponent.canConnectNode(side);
    }
  }
}
