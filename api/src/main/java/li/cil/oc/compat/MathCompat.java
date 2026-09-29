package li.cil.oc.compat;

/**
 * 1.20.1 backport glue: Java 17 stand-ins for Java 21's {@code Math.clamp} overloads.
 * Minecraft 1.20.1 packs commonly run on Java 17, so the backport targets it.
 */
public final class MathCompat {
  private MathCompat() {
  }

  public static int clamp(final long value, final int min, final int max) {
    if (min > max) throw new IllegalArgumentException(min + " > " + max);
    return (int) Math.min(max, Math.max(value, min));
  }

  public static long clamp(final long value, final long min, final long max) {
    if (min > max) throw new IllegalArgumentException(min + " > " + max);
    return Math.min(max, Math.max(value, min));
  }

  public static double clamp(final double value, final double min, final double max) {
    if (!(min <= max)) throw new IllegalArgumentException(min + " > " + max);
    return Math.min(max, Math.max(value, min));
  }

  public static float clamp(final float value, final float min, final float max) {
    if (!(min <= max)) throw new IllegalArgumentException(min + " > " + max);
    return Math.min(max, Math.max(value, min));
  }
}
