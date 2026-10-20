package li.cil.oc.neoforge.client.renderer;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Oculus (Iris), asked through its public API without depending on it: its shadow pass renders block entities and
 * translucent terrain with the shadow camera, before the main pass.
 */
public final class IrisCompat {
  private static final MethodHandle IS_RENDERING_SHADOW_PASS = find();

  private IrisCompat() {
  }

  public static boolean isRenderingShadowPass() {
    if (IS_RENDERING_SHADOW_PASS == null) return false;
    try {
      return (boolean) IS_RENDERING_SHADOW_PASS.invokeExact();
    } catch (Throwable t) {
      return false;
    }
  }

  private static MethodHandle find() {
    try {
      var api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
      var instance = api.getMethod("getInstance").invoke(null);
      return MethodHandles.publicLookup().findVirtual(api, "isRenderingShadowPass", MethodType.methodType(boolean.class)).bindTo(instance);
    } catch (Throwable t) {
      return null; // not installed
    }
  }
}
