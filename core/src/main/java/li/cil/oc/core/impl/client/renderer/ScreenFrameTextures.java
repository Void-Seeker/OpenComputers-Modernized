package li.cil.oc.core.impl.client.renderer;

import li.cil.oc.core.impl.common.blockentity.Screen;
import net.minecraft.core.Direction;

/**
 * The frame of a screen block: each face's texture (under block/screen/) and its quarter turns, from where the block
 * sits in its display, as OC 1.12's ScreenModel and Textures.Block.Screen chose them. No client classes: the chunk
 * builder threads ask it through the block model, and the game tests can.
 */
public final class ScreenFrameTextures {
  private ScreenFrameTextures() {
  }

  /** The texture of the screen's face on the world side. */
  public static String texture(Screen screen, Direction side) {
    final int width = screen.width;
    final int height = screen.height;
    final Direction facing = screen.facing();
    final Direction local = screen.toLocal(side);
    final int[] position = screen.localPosition();
    int px = xy2part(position[0], width - 1);
    int py = xy2part(position[1], height - 1);
    if ((side == Direction.DOWN || facing == Direction.DOWN) && side != facing) {
      px = 2 - px;
      py = 2 - py;
    }
    final int pitch = screen.pitch() == Direction.NORTH ? 0 : 1;
    final boolean front = local == Direction.SOUTH;
    if (width == 1 && height == 1) return front ? SINGLE_FRONT[pitch] : SINGLE[side.get3DDataValue()];
    if (width == 1) return front ? VERTICAL_FRONT[pitch][py] : VERTICAL[pitch][py][local.get3DDataValue()];
    if (height == 1) return front ? HORIZONTAL_FRONT[pitch][px] : HORIZONTAL[pitch][px][local.get3DDataValue()];
    return front ? MULTI_FRONT[pitch][py][px] : MULTI[pitch][py][px][local.get3DDataValue()];
  }

  /** The quarter turns of the face's texture: the top and bottom turn with the screen's yaw. */
  public static int rotation(Screen screen, Direction side) {
    final int yaw = screen.yaw().get2DDataValue();
    if (side == Direction.UP) return yaw;
    if (side == Direction.DOWN) return (4 - yaw) % 4;
    return 0;
  }

  /** The texture of a lone screen's face, for a block with no screen to ask. */
  public static String lone(Direction side, boolean front) {
    return front ? SINGLE_FRONT[0] : SINGLE[side.get3DDataValue()];
  }

  // 2 at the start (left, bottom), 0 at the end, 1 between
  private static int xy2part(int value, int high) {
    return value == 0 ? 2 : value == high ? 0 : 1;
  }

  // OC 1.12's tables: by pitch (forward 0, up or down 1), the part along y and/or x, then the local side (SINGLE: the
  // world side)
  private static final String[] SINGLE = {"b", "b", "b2", "b2", "b2", "b2"};

  private static final String[] SINGLE_FRONT = {"f", "f2"};

  private static final String[][][] HORIZONTAL = {
    {
      {"bht", "bhb", "bht2", "bht2", "b2", "b2"},
      {"bhm", "bhm", "bhm2", "bhm2", "b", "b"},
      {"bhb", "bht", "bhb2", "bhb2", "b2", "b2"}
    },
    {
      {"bhb2", "bht2", "bht", "bhb", "b2", "b2"},
      {"bhm2", "bhm2", "bhm", "bhm", "b", "b"},
      {"bht2", "bhb2", "bhb", "bht", "b2", "b2"}
    }
  };

  private static final String[][] HORIZONTAL_FRONT = {
    {"fhb2", "fhm2", "fht2"},
    {"fhb", "fhm", "fht"}
  };

  private static final String[][][] VERTICAL = {
    {
      {"b", "b", "bvt", "bvt", "bvt", "bvt"},
      {"b", "b", "bvm", "bvm", "bvm", "bvm"},
      {"b", "b", "bvb2", "bvb2", "bvb2", "bvb2"}
    },
    {
      {"b2", "b2", "bvt", "bvt", "bht2", "bhb2"},
      {"b", "b", "bvm", "bvm", "bhm2", "bhm2"},
      {"b2", "b2", "bvb", "bvb", "bhb2", "bht2"}
    }
  };

  private static final String[][] VERTICAL_FRONT = {
    {"fvt", "fvm", "fvb2"},
    {"fvt", "fvm", "fvb"}
  };

  private static final String[][][][] MULTI = {
    {
      {
        {"bht", "bhb", "btl", "btr", "bvb", "bvt"},
        {"bhm", "bhm", "btm", "btm", "b", "b"},
        {"bhb", "bht", "btr", "btl", "bvt", "bvb"}
      },
      {
        {"b", "b", "bml", "bmr", "bvm", "bvm"},
        {"b", "b", "bmm", "bmm", "b", "b"},
        {"b", "b", "bmr", "bml", "bvm", "bvt"}
      },
      {
        {"bht", "bhb", "bbl2", "bbr2", "bvt", "bvb2"},
        {"bhm", "bhm", "bbm2", "bbm2", "b", "b"},
        {"bhb", "bht", "bbr2", "bbl2", "bvb2", "bvt"}
      }
    },
    {
      {
        {"bhb2", "bht2", "btl", "btr", "bht2", "bhb2"},
        {"bhm2", "bhm2", "btm", "btm", "b", "b"},
        {"bht2", "bhb2", "btr", "btl", "bht2", "bhb2"}
      },
      {
        {"b", "b", "bml", "bml", "bhm2", "bhm2"},
        {"b", "b", "bmm", "bmm", "b", "b"},
        {"b", "b", "bmr", "bmr", "bhm2", "bhm2"}
      },
      {
        {"bhb2", "bht2", "bbl", "bbr", "bhb2", "bht2"},
        {"bhm2", "bhm2", "bbm", "bbm", "b", "b"},
        {"bht2", "bhb2", "bbr", "bbl", "bhb2", "bht2"}
      }
    }
  };

  private static final String[][][] MULTI_FRONT = {
    {
      {"ftr", "ftm", "ftl"},
      {"fmr", "fmm", "fml"},
      {"fbr2", "fbm2", "fbl2"}
    },
    {
      {"ftr", "ftm", "ftl"},
      {"fmr", "fmm", "fml"},
      {"fbr", "fbm", "fbl"}
    }
  };
}
