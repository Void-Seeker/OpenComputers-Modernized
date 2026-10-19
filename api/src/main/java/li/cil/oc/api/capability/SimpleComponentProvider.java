package li.cil.oc.api.capability;

import li.cil.oc.api.network.Node;

/**
 * Attached by OC to block entities implementing
 * {@link li.cil.oc.api.network.SimpleComponent}.
 * <br>
 * This replaces the ASM-based class transformation previously used to inject
 * {@link li.cil.oc.api.network.Environment} methods into SimpleComponent block
 * entities: the capability holds the block entity's node (hosting it for the
 * block entity, whose callbacks it serves) and saves it with the block entity.
 * OC's network code finds the node through it, e.g. in
 * {@link li.cil.oc.api.Network#joinOrCreateNetwork}.
 */
public interface SimpleComponentProvider {
  /**
   * Returns the OC network node for the wrapped block entity.
   */
  @SuppressWarnings("unused")
  Node node();

  /**
   * Returns the component name.
   */
  @SuppressWarnings("unused")
  String getComponentName();
}
