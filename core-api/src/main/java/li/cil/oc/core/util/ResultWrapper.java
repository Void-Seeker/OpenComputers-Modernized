package li.cil.oc.core.util;

import java.util.Arrays;
import java.util.function.Predicate;

public final class ResultWrapper {
  // (core-api knows no item stacks: the platform tells which values are empty stacks)
  private static Predicate<Object> isEmptyStack = value -> false;

  public static void setEmptyStackCheck(Predicate<Object> check) {
    isEmptyStack = check;
  }

  /** The values as results; an empty item stack is nil, as OC 1.12 (it was an "air" table). */
  public static Object[] result(Object... args) {
    Object[] unwrapped = Arrays.copyOf(args, args.length, Object[].class);
    for (int i = 0; i < unwrapped.length; i++) {
      if (unwrapped[i] != null && isEmptyStack.test(unwrapped[i])) unwrapped[i] = null;
    }
    return unwrapped;
  }
}
