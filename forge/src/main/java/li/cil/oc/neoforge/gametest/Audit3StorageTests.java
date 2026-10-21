package li.cil.oc.neoforge.gametest;

import static li.cil.oc.neoforge.gametest.TestMachines.when;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPartHost;
import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import java.io.File;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import li.cil.oc.api.fs.Mode;
import li.cil.oc.core.impl.OCSettings;
import li.cil.oc.core.impl.server.machine.ArgumentsImpl;
import li.cil.oc.core.impl.util.StateSaveManager;
import li.cil.oc.neoforge.OpenComputers;
import li.cil.oc.neoforge.integration.appeng.DriverBlockInterface;
import li.cil.oc.neoforge.integration.appeng.DriverExportBus;
import li.cil.oc.neoforge.integration.appeng.DriverImportBus;
import li.cil.oc.neoforge.integration.appeng.DriverPartInterface;
import li.cil.oc.neoforge.integration.appeng.NetworkControl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * File systems, machine state files and AE2 crafting through OC (third audit).
 */
@GameTestHolder(OpenComputers.ID)
@PrefixGameTestTemplate(false)
public final class Audit3StorageTests {
  private static final String EMPTY = "empty";
  private static final ArgumentsImpl NO_ARGS = new ArgumentsImpl(new Object[0]);

  private Audit3StorageTests() {
  }

  private static File worldRoot(final GameTestHelper helper) {
    return helper.getLevel().getServer().getWorldPath(LevelResource.ROOT).toFile();
  }

  private static void writeFile(final li.cil.oc.api.fs.FileSystem fs, final String path, final String content) {
    final int handle = fs.open(path, Mode.Write);
    fs.getHandle(handle).write(content.getBytes(StandardCharsets.UTF_8));
    fs.getHandle(handle).close();
  }

  private static String read(final File file) throws Exception {
    return Files.readString(file.toPath());
  }

  // ----------------------------------------------------------------------- //
  // file systems

  /**
   * Closing a file system closes every handle open for writing (each closed handle left the map being walked: two threw,
   * a RAID's disk taken out was lost with them, a drive kept its files undeletable, a tmpfs its space used).
   */
  @GameTest(template = EMPTY)
  public static void closingAFileSystemClosesItsWriteHandles(final GameTestHelper helper) {
    final var ram = li.cil.oc.api.FileSystem.fromMemory(64 * 1024);
    final long empty = ram.spaceUsed();
    final int a = ram.open("a", Mode.Write);
    final int b = ram.open("b", Mode.Write);
    ram.getHandle(a).write(new byte[100]);
    ram.getHandle(b).write(new byte[100]);
    ram.close();
    helper.assertTrue(ram.getHandle(a) == null && ram.getHandle(b) == null, "the tmpfs kept handles open after closing");
    helper.assertTrue(ram.spaceUsed() == empty, "the emptied tmpfs still uses " + ram.spaceUsed() + " instead of " + empty);
    final String root = "oc-test-" + UUID.randomUUID();
    final var buffered = li.cil.oc.api.FileSystem.fromSaveDirectory(root, 64 * 1024, true);
    try {
      buffered.open("a", Mode.Write);
      buffered.open("b", Mode.Write);
      buffered.close();
      helper.assertTrue(buffered.delete("a") && buffered.delete("b"), "files of a closed drive are still open");
    } finally {
      org.apache.commons.io.FileUtils.deleteQuietly(new File(worldRoot(helper), OCSettings.savePath + root));
    }
    helper.succeed();
  }

  /** makeDirectory on a path there and remove on a path not there say false, as OC 1.12 (both said true). */
  @GameTest(template = EMPTY)
  public static void makeDirectoryAndRemoveSayWhetherTheyDid(final GameTestHelper helper) {
    final var env = (li.cil.oc.core.impl.server.component.FileSystem) li.cil.oc.api.FileSystem.asManagedEnvironment(li.cil.oc.api.FileSystem.fromMemory(64 * 1024));
    final java.util.function.Function<String, Object> mkdir = path -> env.makeDirectory(null, new ArgumentsImpl(new Object[]{path}))[0];
    final java.util.function.Function<String, Object> remove = path -> env.remove(null, new ArgumentsImpl(new Object[]{path}))[0];
    helper.assertTrue(Boolean.TRUE.equals(mkdir.apply("/d")), "making a directory failed");
    helper.assertTrue(Boolean.FALSE.equals(mkdir.apply("/d")), "making a directory that is there said it did");
    helper.assertTrue(Boolean.TRUE.equals(mkdir.apply("/x/y/z")), "making nested directories failed");
    helper.assertTrue(Boolean.FALSE.equals(remove.apply("/missing")), "removing a path not there said it did");
    helper.assertTrue(Boolean.TRUE.equals(remove.apply("/x")), "removing a directory tree failed");
    helper.assertTrue(Boolean.FALSE.equals(remove.apply("/x")), "removing it again said it did");
    helper.succeed();
  }

  private static li.cil.oc.core.impl.util.SafeThreadPool fileSavePool() {
    try { // the pool comes with the first buffered drive (none made yet, in some test orders)
      Class.forName("li.cil.oc.core.impl.server.fs.FileSystem$BufferedFileSystem", true, Audit3StorageTests.class.getClassLoader());
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException(e);
    }
    for (final var pool : li.cil.oc.core.impl.util.ThreadPoolFactory.safePools) {
      if ("FileSystem".equals(pool.name())) return pool;
    }
    throw new IllegalStateException("no file system save pool");
  }

  private static void saveAndWait(final GameTestHelper helper, final li.cil.oc.api.fs.FileSystem fs) throws Exception {
    fs.save(new CompoundTag(), helper.getLevel().registryAccess());
    fileSavePool().withPool(pool -> pool.submit(() -> {
    })).get(10, TimeUnit.SECONDS);
    Thread.sleep(5); // (a change in the same millisecond as the save is not newer than the files written)
  }

  private static Object fileKey(final File file) throws Exception {
    return Files.readAttributes(file.toPath(), BasicFileAttributes.class).fileKey();
  }

  /**
   * A drive's save writes the files changed since the last one, moved, or gone from its folder, and only those (every
   * save rewrote every file of every drive, holding the drive's lock).
   */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void drivesSaveOnlyWhatChanged(final GameTestHelper helper) throws Exception {
    final String root = "oc-test-" + UUID.randomUUID();
    final File folder = new File(worldRoot(helper), OCSettings.savePath + root);
    final var fs = li.cil.oc.api.FileSystem.fromSaveDirectory(root, 64 * 1024, true);
    try {
      writeFile(fs, "same", "unchanged");
      writeFile(fs, "edited", "first");
      fs.makeDirectory("d");
      writeFile(fs, "d/moved", "moved");
      saveAndWait(helper, fs);
      final Object same = fileKey(new File(folder, "same"));
      writeFile(fs, "edited", "second");
      fs.rename("d", "e");
      saveAndWait(helper, fs);
      helper.assertTrue(same.equals(fileKey(new File(folder, "same"))), "an unchanged file was written again");
      helper.assertTrue("second".equals(read(new File(folder, "edited"))), "a changed file was not saved");
      helper.assertTrue(!new File(folder, "d").exists() && "moved".equals(read(new File(folder, "e/moved"))), "a moved directory was not saved where it went");
      Files.delete(new File(folder, "same").toPath());
      saveAndWait(helper, fs);
      helper.assertTrue(new File(folder, "same").exists() && "unchanged".equals(read(new File(folder, "same"))), "a file gone from the folder was not written again");
    } finally {
      fs.close();
      org.apache.commons.io.FileUtils.deleteQuietly(folder);
    }
    helper.succeed();
  }

  private static void stateSaveAndWait(final String dimension, final ChunkPos chunk, final String name, final byte[] data) throws Exception {
    StateSaveManager.scheduleSave(dimension, chunk, name, data);
    StateSaveManager.stateSaveHandler.withPool(pool -> pool.submit(() -> {
    })).get(10, TimeUnit.SECONDS);
  }

  /**
   * A machine's state file is replaced by a whole new one, never written over (a kill mid-save left it cut: "could not
   * restore the computer's state").
   */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void stateFilesAreReplacedWhole(final GameTestHelper helper) throws Exception {
    final String dimension = "oc-test-" + UUID.randomUUID();
    final ChunkPos chunk = new ChunkPos(12345, 54321);
    final String name = UUID.randomUUID().toString();
    final File dimensionFolder = new File(StateSaveManager.statePath(), dimension);
    final File file = new File(dimensionFolder, chunk.x + "." + chunk.z + "/" + name);
    final File holder = new File(file.getParentFile(), name + ".old");
    try {
      final byte[] old = "old state".getBytes(StandardCharsets.UTF_8);
      stateSaveAndWait(dimension, chunk, name, old);
      // what a reader of the old file still has: written over, it was cut (to nothing, first) while the new one was written
      Files.createLink(holder.toPath(), file.toPath());
      stateSaveAndWait(dimension, chunk, name, "new state".getBytes(StandardCharsets.UTF_8));
      helper.assertTrue("new state".equals(read(file)), "the new state was not saved: " + read(file));
      helper.assertTrue("old state".equals(read(holder)), "the state file was written over, not replaced");
      helper.assertTrue(!new File(file.getParentFile(), name + ".part").exists(), "a .part file was left behind");
    } finally {
      org.apache.commons.io.FileUtils.deleteQuietly(dimensionFolder);
    }
    helper.succeed();
  }

  private static int staticCollectionSizes(final Class<?> type) throws Exception {
    int size = 0;
    for (final var field : type.getDeclaredFields()) {
      if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
      field.setAccessible(true);
      final Object value = field.get(null);
      if (value instanceof java.util.Collection<?> c) size += c.size();
      if (value instanceof java.util.Map<?, ?> m) size += m.size();
    }
    return size;
  }

  /** Saving machine states keeps nothing per save (each added its chunk's folder to a list nothing read). */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void stateSavesKeepNothingPerSave(final GameTestHelper helper) throws Exception {
    final String dimension = "oc-test-" + UUID.randomUUID();
    final ChunkPos chunk = new ChunkPos(12345, 54321);
    final String name = UUID.randomUUID().toString();
    try {
      stateSaveAndWait(dimension, chunk, name, new byte[]{1});
      final int before = staticCollectionSizes(StateSaveManager.class);
      for (int i = 0; i < 20; i++) stateSaveAndWait(dimension, chunk, name, new byte[]{1});
      final int after = staticCollectionSizes(StateSaveManager.class);
      helper.assertTrue(after == before, "twenty saves of one state grew the save manager's collections from " + before + " to " + after);
    } finally {
      org.apache.commons.io.FileUtils.deleteQuietly(new File(StateSaveManager.statePath(), dimension));
    }
    helper.succeed();
  }

  // ----------------------------------------------------------------------- //
  // AE2

  private static final BlockPos CONTROLLER = new BlockPos(2, 2, 2);
  private static final BlockPos ON_TOP = CONTROLLER.above();

  private static net.minecraft.world.level.block.Block ae2Block(final String name) {
    return ForgeRegistries.BLOCKS.getValue(new ResourceLocation("ae2", name));
  }

  private static net.minecraft.world.item.Item ae2Item(final String name) {
    return ForgeRegistries.ITEMS.getValue(new ResourceLocation("ae2", name));
  }

  /**
   * A powered grid in the middle of the structure: a controller, a creative energy cell, a 1k crafting CPU, a drive with
   * cobblestone and a pattern provider with a processing pattern of cobblestone to stone, which nothing ever does (a job
   * for stone stays running). Then gets the grid once stone is craftable; the requester goes on top of the controller.
   */
  private static void craftingGrid(final GameTestHelper helper, final Consumer<IGrid> then) {
    helper.setBlock(CONTROLLER, ae2Block("controller"));
    helper.setBlock(CONTROLLER.west(), ae2Block("creative_energy_cell"));
    helper.setBlock(CONTROLLER.east(), ae2Block("1k_crafting_storage"));
    helper.setBlock(CONTROLLER.north(), ae2Block("pattern_provider"));
    helper.setBlock(CONTROLLER.below(), ae2Block("drive"));
    final var provider = (appeng.blockentity.crafting.PatternProviderBlockEntity) helper.getBlockEntity(CONTROLLER.north());
    provider.getLogic().getPatternInv().addItems(PatternDetailsHelper.encodeProcessingPattern(
      new GenericStack[]{new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1)},
      new GenericStack[]{new GenericStack(AEItemKey.of(Items.STONE), 1)}));
    ((appeng.blockentity.storage.DriveBlockEntity) helper.getBlockEntity(CONTROLLER.below())).getInternalInventory()
      .addItems(new ItemStack(ae2Item("item_storage_cell_1k")));
    final var controller = (appeng.me.helpers.IGridConnectedBlockEntity) helper.getBlockEntity(CONTROLLER);
    final boolean[] stocked = {false};
    final java.util.function.BooleanSupplier ready = () -> {
      final var grid = controller.getMainNode().getGrid();
      if (grid == null || !controller.getMainNode().isActive()) return false;
      if (!stocked[0]) {
        stocked[0] = grid.getStorageService().getInventory().insert(AEItemKey.of(Items.COBBLESTONE), 16, Actionable.MODULATE, IActionSource.empty()) > 0;
      }
      return stocked[0] && grid.getCraftingService().isCraftable(AEItemKey.of(Items.STONE)) && !grid.getCraftingService().getCpus().isEmpty();
    };
    when(helper, ready, 200, () -> {
      final var g = controller.getMainNode().getGrid();
      helper.assertTrue(ready.getAsBoolean(), "the AE2 grid did not come up (active " + controller.getMainNode().isActive() + ", stocked " + stocked[0]
        + ", nodes " + (g != null ? g.size() : -1) + ", cpus " + (g != null ? g.getCraftingService().getCpus().size() : -1) + ")");
      then.accept(controller.getMainNode().getGrid());
    });
  }

  // the jobs of the test's grid, whichever grid its controller is in by now (a job left running holds its CPU, craftable
  // and grid on)
  private static void cancelJobs(final GameTestHelper helper) {
    if (helper.getLevel().getBlockEntity(helper.absolutePos(CONTROLLER)) instanceof appeng.me.helpers.IGridConnectedBlockEntity controller
      && controller.getMainNode().getGrid() != null) {
      controller.getMainNode().getGrid().getCraftingService().getCpus().forEach(ICraftingCPU::cancelJob);
    }
  }

  private static boolean cpuBusy(final IGrid grid) {
    return grid.getCraftingService().getCpus().stream().anyMatch(ICraftingCPU::isBusy);
  }

  private static NetworkControl.Craftable stoneCraftable(final GameTestHelper helper, final NetworkControl<?> env) {
    final Object[] craftables = (Object[]) env.getCraftables(null, NO_ARGS)[0];
    helper.assertTrue(craftables.length == 1 && craftables[0] instanceof NetworkControl.Craftable, "no craftable stone: " + java.util.Arrays.toString(craftables));
    return (NetworkControl.Craftable) craftables[0];
  }

  // whether the request is past computing, and its job submitted
  private static boolean submitted(final NetworkControl.CraftingStatus status) {
    return status.isDone(null, NO_ARGS).length == 1;
  }

  private static NetworkControl.CraftingStatus request(final GameTestHelper helper, final NetworkControl.Craftable craftable) {
    final Object[] result = craftable.request(null, new ArgumentsImpl(new Object[]{1}));
    helper.assertTrue(result[0] instanceof NetworkControl.CraftingStatus, "the request failed: " + java.util.Arrays.toString(result));
    return (NetworkControl.CraftingStatus) result[0];
  }

  /** Requests stone through an ME interface on the controller; then gets the craftable and its status once the job runs. */
  private static void runningJob(final GameTestHelper helper, final IGrid grid,
                                 final java.util.function.BiConsumer<NetworkControl.Craftable, NetworkControl.CraftingStatus> then) {
    runningJob(helper, grid, ON_TOP, then);
  }

  /** The same, through an ME interface at the position. */
  private static void runningJob(final GameTestHelper helper, final IGrid grid, final BlockPos at,
                                 final java.util.function.BiConsumer<NetworkControl.Craftable, NetworkControl.CraftingStatus> then) {
    helper.setBlock(at, ae2Block("interface"));
    final var iface = (appeng.blockentity.misc.InterfaceBlockEntity) helper.getBlockEntity(at);
    when(helper, () -> iface.getMainNode().isActive(), 100, () -> {
      final var craftable = stoneCraftable(helper, new DriverBlockInterface.Environment(iface));
      final var status = request(helper, craftable);
      TestMachines.atEnd(helper, () -> cancelJobs(helper));
      when(helper, () -> submitted(status) && cpuBusy(grid), 200, () -> {
        helper.assertTrue(submitted(status) && cpuBusy(grid), "the job did not start: " + java.util.Arrays.toString(status.isDone(null, NO_ARGS)));
        then.accept(craftable, status);
      });
    });
  }

  /**
   * An interface broken while a craft it requested runs gets the job cancelled (AE2 asked the requester for its node, got
   * null, and the server crashed).
   */
  @GameTest(template = EMPTY, timeoutTicks = 600)
  public static void aCraftOutlivingItsInterfaceIsCancelled(final GameTestHelper helper) {
    craftingGrid(helper, grid -> runningJob(helper, grid, (craftable, status) -> {
      helper.setBlock(ON_TOP, Blocks.AIR);
      helper.assertTrue(craftable.getActionableNode() != null, "a requester whose interface is gone has no node (AE2 crashes on it)");
      when(helper, () -> !cpuBusy(grid), 100, () -> {
        helper.assertTrue(!cpuBusy(grid), "the job of a requester gone runs on");
        helper.assertTrue(Boolean.TRUE.equals(status.isCanceled(null, NO_ARGS)[0]), "the job is not reported cancelled");
        helper.succeed();
      });
    }));
  }

  /**
   * A status restored after a restart whose job no craftable came back with follows the job on its CPU, found unasked
   * (AE2 runs a job with no requester on for its grace ticks and cancels it unless it ends first, and its CPU lets go of
   * a job over): it said cancelled, and a job done meanwhile was reported cancelled. A CPU built again is followed again;
   * a copy loaded again this session follows it too, and the status cancels it.
   */
  @GameTest(template = EMPTY, timeoutTicks = 800)
  public static void aRestoredStatusFollowsItsJobOnItsCpu(final GameTestHelper helper) {
    craftingGrid(helper, grid -> runningJob(helper, grid, (craftable, status) -> {
      final UUID id = craftable.getRequestedJobs().iterator().next().getCraftingID();
      final var registries = helper.getLevel().registryAccess();
      final CompoundTag statusTag = new CompoundTag();
      statusTag.putBoolean("computing", false);
      statusTag.putString("link", id.toString());
      statusTag.putString("session", UUID.randomUUID().toString()); // (saved by the last server)
      final var restored = loadedStatus(statusTag, registries);
      final var cluster = grid.getMachines(appeng.blockentity.crafting.CraftingBlockEntity.class).iterator().next().getCluster();
      when(helper, () -> linkCache("orphan", id) == cluster.craftingLogic.getLastLink(), 20, () -> {
        helper.assertTrue(linkCache("orphan", id) == cluster.craftingLogic.getLastLink(), "the job of a restored status was not found on its CPU: " + linkCache("orphan", id));
        final var rebuilt = (appeng.api.networking.crafting.ICraftingLink) java.lang.reflect.Proxy.newProxyInstance(
          appeng.api.networking.crafting.ICraftingLink.class.getClassLoader(), new Class<?>[]{appeng.api.networking.crafting.ICraftingLink.class},
          (proxy, method, arguments) -> switch (method.getName()) {
            case "getCraftingID" -> id;
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == arguments[0];
            default -> method.getReturnType() == boolean.class ? false : null;
          });
        linkCache("orphan", rebuilt, cluster); // (the link of its CPU before it was built again)
        when(helper, () -> linkCache("orphan", id) == cluster.craftingLogic.getLastLink(), 20, () -> {
          helper.assertTrue(linkCache("orphan", id) == cluster.craftingLogic.getLastLink(), "the job of a CPU built again was not followed again");
          TestMachines.later(helper, 110, () -> { // (past the time a job is looked for)
            helper.assertTrue(cpuBusy(grid), "the job stopped by itself");
            helper.assertTrue(java.util.Arrays.equals(restored.isCanceled(null, NO_ARGS), new Object[]{false}), "a restored status of a job on its CPU says "
              + java.util.Arrays.toString(restored.isCanceled(null, NO_ARGS)));
            final CompoundTag hereTag = statusTag.copy();
            hereTag.putString("session", linkCache("session").toString());
            final var here = loadedStatus(hereTag, registries); // (a copy loaded again this session)
            helper.assertTrue(java.util.Arrays.equals(here.isCanceled(null, NO_ARGS), new Object[]{false}), "a status loaded again of a job on its CPU says "
              + java.util.Arrays.toString(here.isCanceled(null, NO_ARGS)));
            helper.assertTrue(java.util.Arrays.equals(restored.cancel(null, NO_ARGS), new Object[]{true}), "the restored status could not cancel its job");
            when(helper, () -> !cpuBusy(grid), 100, () -> {
              helper.assertTrue(!cpuBusy(grid), "the restored status did not cancel its job");
              helper.assertTrue(java.util.Arrays.equals(restored.isCanceled(null, NO_ARGS), new Object[]{true}), "the restored status does not report the cancel");
              helper.assertTrue(java.util.Arrays.equals(here.isCanceled(null, NO_ARGS), new Object[]{true}), "the copy loaded again does not report the cancel");
              helper.succeed();
            });
          });
        });
      });
    }));
  }

  // LinkCache's (package-private): session(), orphan(id), orphan(link, cluster)
  private static Object linkCache(final String name, final Object... arguments) {
    try {
      for (final var method : Class.forName("li.cil.oc.neoforge.integration.appeng.NetworkControl$LinkCache").getDeclaredMethods()) {
        if (!method.getName().equals(name) || method.getParameterCount() != arguments.length) continue;
        method.setAccessible(true);
        return method.invoke(null, arguments);
      }
      throw new IllegalStateException("no LinkCache." + name + " of " + arguments.length);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * A craftable and its status restored (the computer's chunk loaded again) follow the job still running: the status
   * cancels it (AE2 was never told of the restored link: it reported nothing, cancelled nothing, and after a reload of the
   * grid the job was cancelled for want of a requester).
   */
  @GameTest(template = EMPTY, timeoutTicks = 600)
  public static void aRestoredCraftingStatusFollowsItsJob(final GameTestHelper helper) {
    final var registries = helper.getLevel().registryAccess();
    craftingGrid(helper, grid -> runningJob(helper, grid, (craftable, status) -> {
      final CompoundTag craftableTag = new CompoundTag();
      final CompoundTag statusTag = new CompoundTag();
      craftable.save(craftableTag, registries);
      status.save(statusTag, registries);
      TestMachines.atEnd(helper, () -> cancelJobs(helper)); // (the status's cancel is what is tested)
      final var restoredStatus = new NetworkControl.CraftingStatus();
      restoredStatus.load(statusTag, registries);
      final var restored = new NetworkControl.Craftable();
      restored.load(craftableTag, registries);
      when(helper, () -> restored.requesting(null, NO_ARGS)[0] instanceof Number, 60, () -> {
        helper.assertTrue(restored.requesting(null, NO_ARGS)[0] instanceof Number, "the restored craftable did not find its interface");
        helper.assertTrue(Boolean.TRUE.equals(restoredStatus.cancel(null, NO_ARGS)[0]), "the restored status could not cancel");
        when(helper, () -> !cpuBusy(grid), 60, () -> {
          helper.assertTrue(!cpuBusy(grid), "cancelling through the restored status left the job running");
          helper.assertTrue(Boolean.TRUE.equals(restoredStatus.isCanceled(null, NO_ARGS)[0]), "the restored status does not report the cancel");
          helper.succeed();
        });
      });
    }));
  }

  // As at a reload: AE2's job keeps no requester (it waits 60 ticks for one to come back), and the craftable that asked
  // is gone (it would follow the job along).
  private static void forgetRequester(final IGrid grid, final NetworkControl.Craftable craftable) {
    try {
      final var links = appeng.me.service.CraftingService.class.getDeclaredField("craftingLinks");
      links.setAccessible(true);
      for (final Object nexus : ((java.util.Map<?, ?>) links.get(grid.getCraftingService())).values()) {
        if (((appeng.crafting.CraftingLinkNexus) nexus).isRequester(craftable)) ((appeng.crafting.CraftingLinkNexus) nexus).removeNode();
      }
      final var own = NetworkControl.Craftable.class.getDeclaredField("links");
      own.setAccessible(true);
      ((java.util.Set<?>) own.get(craftable)).clear();
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private static boolean runsOn(final NetworkControl.CraftingStatus status) {
    return Boolean.FALSE.equals(status.isCanceled(null, NO_ARGS)[0]) && Boolean.FALSE.equals(status.isDone(null, NO_ARGS)[0]);
  }

  /**
   * A job whose only requester is the craftable restored (the one that asked gone, as after a reload) runs on past the
   * 60 ticks AE2 waits for one: AE2 was never told of the restored one, and cancelled the job.
   */
  @GameTest(template = EMPTY, timeoutTicks = 1000)
  public static void aJobRunsOnForItsRestoredRequesterAlone(final GameTestHelper helper) {
    final var registries = helper.getLevel().registryAccess();
    craftingGrid(helper, grid -> runningJob(helper, grid, (craftable, status) -> {
      final CompoundTag craftableTag = new CompoundTag();
      final CompoundTag statusTag = new CompoundTag();
      craftable.save(craftableTag, registries);
      status.save(statusTag, registries);
      forgetRequester(grid, craftable);
      final var restoredStatus = new NetworkControl.CraftingStatus();
      restoredStatus.load(statusTag, registries);
      final var restored = new NetworkControl.Craftable();
      restored.load(craftableTag, registries);
      TestMachines.later(helper, 150, () -> {
        try {
          helper.assertTrue(restored.requesting(null, NO_ARGS)[0] instanceof Number, "the restored craftable did not find its interface");
          helper.assertTrue(cpuBusy(grid) && runsOn(restoredStatus), "the job of the restored requester alone was "
            + (cpuBusy(grid) ? "reported over" : "cancelled") + " (cancelled " + restoredStatus.isCanceled(null, NO_ARGS)[0] + ")");
        } finally {
          restoredStatus.cancel(null, NO_ARGS);
        }
        helper.succeed();
      });
    }));
  }

  /**
   * A restored craftable whose interface is in a grid of its own (the network still forming at login) and then joins the
   * main one keeps its job there: AE2 moves a job's requester along between grids only for grid nodes, the job lost its
   * requester and was cancelled after 60 ticks, while the status, tied to the grid left, said neither done nor cancelled.
   */
  @GameTest(template = EMPTY, timeoutTicks = 1000)
  public static void aRestoredJobFollowsItsInterfaceIntoTheMainGrid(final GameTestHelper helper) {
    final ServerLevel level = helper.getLevel();
    final var registries = level.registryAccess();
    final BlockPos bridge = ON_TOP, at = ON_TOP.above();
    final Runnable cable = () -> helper.assertTrue(PartHelper.setPart(level, helper.absolutePos(bridge), null, null, (IPartItem<?>) ae2Item("fluix_glass_cable")) != null,
      "the cable could not be placed");
    final java.util.function.Supplier<IGrid> main = () -> ((appeng.me.helpers.IGridConnectedBlockEntity) helper.getBlockEntity(CONTROLLER)).getMainNode().getGrid();
    craftingGrid(helper, grid -> {
      cable.run();
      runningJob(helper, grid, at, (craftable, status) -> {
        final CompoundTag craftableTag = new CompoundTag();
        final CompoundTag statusTag = new CompoundTag();
        craftable.save(craftableTag, registries);
        status.save(statusTag, registries);
        forgetRequester(grid, craftable);
        helper.setBlock(bridge, Blocks.AIR); // the interface in a grid of its own
        final var iface = (appeng.blockentity.misc.InterfaceBlockEntity) helper.getBlockEntity(at);
        final var restoredStatus = new NetworkControl.CraftingStatus();
        restoredStatus.load(statusTag, registries);
        final var restored = new NetworkControl.Craftable();
        restored.load(craftableTag, registries);
        when(helper, () -> restored.requesting(null, NO_ARGS)[0] instanceof Number, 20, () -> {
          helper.assertTrue(restored.requesting(null, NO_ARGS)[0] instanceof Number, "the restored craftable did not find its interface");
          helper.assertTrue(iface.getMainNode().getGrid() != main.get(), "the interface is still in the main grid");
          cable.run(); // the network whole again
          when(helper, () -> iface.getMainNode().getGrid() == main.get(), 20, () -> {
            helper.assertTrue(iface.getMainNode().getGrid() == main.get(), "the interface did not join the main grid again");
            TestMachines.later(helper, 120, () -> {
              try {
                helper.assertTrue(restored.requesting(null, NO_ARGS)[0] instanceof Number, "the restored craftable lost its interface");
                helper.assertTrue(cpuBusy(main.get()) && runsOn(restoredStatus), "the job the restored requester followed into the main grid was "
                  + (cpuBusy(main.get()) ? "reported over" : "cancelled") + " (status cancelled " + restoredStatus.isCanceled(null, NO_ARGS)[0] + ")");
              } finally {
                restoredStatus.cancel(null, NO_ARGS);
              }
              helper.succeed();
            });
          });
        });
      });
    });
  }

  // whether AE2 tells the craftable of the job's end: the requester of the job's link
  private static boolean requesterOfTheJob(final IGrid grid, final NetworkControl.Craftable craftable) {
    try {
      final var links = appeng.me.service.CraftingService.class.getDeclaredField("craftingLinks");
      links.setAccessible(true);
      return ((java.util.Map<?, ?>) links.get(grid.getCraftingService())).values().stream()
        .anyMatch(nexus -> ((appeng.crafting.CraftingLinkNexus) nexus).isRequester(craftable));
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * A craftable restored (its computer's chunk loaded again) takes its job over from the one left behind, which lives
   * on until collected (the job's link holds it). Both followed the grid: at a grid change both told AE2, the last one
   * had the job, and its end could go to the one left behind - the restored one then followed the job for good, and its
   * status said cancelled after a later reload.
   */
  @GameTest(template = EMPTY, timeoutTicks = 1000)
  public static void aRestoredCraftableTakesItsJobOver(final GameTestHelper helper) {
    final var registries = helper.getLevel().registryAccess();
    craftingGrid(helper, grid -> runningJob(helper, grid, (craftable, status) -> {
      final CompoundTag craftableTag = new CompoundTag();
      final CompoundTag statusTag = new CompoundTag();
      craftable.save(craftableTag, registries);
      status.save(statusTag, registries);
      final var restoredStatus = new NetworkControl.CraftingStatus();
      restoredStatus.load(statusTag, registries);
      final var restored = new NetworkControl.Craftable();
      restored.load(craftableTag, registries);
      TestMachines.atEnd(helper, () -> cancelJobs(helper)); // (a status cancels through its link: one never told AE2 only flags it)
      when(helper, () -> restored.requesting(null, NO_ARGS)[0] instanceof Number, 20, () -> {
        helper.assertTrue(restored.requesting(null, NO_ARGS)[0] instanceof Number, "the restored craftable did not find its interface");
        TestMachines.later(helper, 2, () -> {
          // a grid change, as each follower sees it: the restored one tells AE2, then the one left behind (their order
          // within a tick is the scheduler's)
          followGridChange(restored);
          followGridChange(craftable);
          helper.assertTrue(requesterOfTheJob(grid, restored), "after a grid change AE2 tells the craftable left behind of the job ("
            + requesterOfTheJob(grid, craftable) + "), not the restored one");
          grid.getCraftingService().getCpus().forEach(ICraftingCPU::cancelJob); // as from AE2's crafting status screen
          when(helper, () -> !cpuBusy(grid), 20, () -> {
            helper.assertTrue(!cpuBusy(grid), "the job was not cancelled");
            helper.assertTrue(Boolean.TRUE.equals(restoredStatus.isCanceled(null, NO_ARGS)[0]), "the restored status does not report the cancel");
            helper.assertTrue(restored.getRequestedJobs().isEmpty(), "the restored craftable was not told of the job's end: it follows it for good");
            helper.assertTrue(craftable.getRequestedJobs().isEmpty(), "the craftable left behind still holds the job");
            helper.succeed();
          });
        });
      });
    }));
  }

  /**
   * Of two craftables restored from one save (a computer's chunk loaded, unloaded and loaded again before the first one
   * was collected), the newer one keeps the job though the older, waiting for its interface's chunk, tells AE2 of it
   * after the newer: the last one to tell took the job, the newer let go of it for good, and its status said cancelled
   * after a grid change while the job ran ("no link" after a save).
   */
  @GameTest(template = EMPTY, timeoutTicks = 1000)
  public static void theNewestRestoredCraftableKeepsItsJob(final GameTestHelper helper) {
    final var registries = helper.getLevel().registryAccess();
    craftingGrid(helper, grid -> runningJob(helper, grid, (craftable, status) -> {
      final CompoundTag craftableTag = new CompoundTag();
      final CompoundTag statusTag = new CompoundTag();
      craftable.save(craftableTag, registries);
      status.save(statusTag, registries);
      TestMachines.atEnd(helper, () -> grid.getCraftingService().getCpus().forEach(ICraftingCPU::cancelJob));
      final var older = new NetworkControl.Craftable();
      older.load(craftableTag, registries);
      TestMachines.later(helper, 1, () -> { // (the machine loaded again, at a later tick)
        final var newer = new NetworkControl.Craftable();
        newer.load(craftableTag, registries);
        final var newerStatus = new NetworkControl.CraftingStatus();
        newerStatus.load(statusTag, registries);
        // the newer one finds its interface now, the older one at its first retry
        retryNow(newer);
        helper.assertTrue(newer.requesting(null, NO_ARGS)[0] instanceof Number, "the newer craftable did not find its interface");
        helper.assertTrue(!(older.requesting(null, NO_ARGS)[0] instanceof Number), "the older craftable found its interface first");
        when(helper, () -> older.requesting(null, NO_ARGS)[0] instanceof Number, 20, () -> {
          helper.assertTrue(older.requesting(null, NO_ARGS)[0] instanceof Number, "the older craftable did not find its interface");
          TestMachines.later(helper, 2, () -> { // (each follower had its tick)
            helper.assertTrue(requesterOfTheJob(grid, newer), "AE2 tells the job to the older craftable, which told it last ("
              + requesterOfTheJob(grid, older) + "), not the newer one");
            helper.assertTrue(!newer.getRequestedJobs().isEmpty(), "the newer craftable let go of its job");
            helper.assertTrue(older.getRequestedJobs().isEmpty() && craftable.getRequestedJobs().isEmpty(), "an older craftable still holds the job");
            // a grid change, as each follower sees it
            followGridChange(newer);
            followGridChange(older);
            followGridChange(craftable);
            helper.assertTrue(requesterOfTheJob(grid, newer), "after a grid change AE2 tells an older craftable of the job");
            grid.getCraftingService().getCpus().forEach(ICraftingCPU::cancelJob);
            when(helper, () -> !cpuBusy(grid), 20, () -> {
              helper.assertTrue(!cpuBusy(grid), "the job was not cancelled");
              helper.assertTrue(Boolean.TRUE.equals(newerStatus.isCanceled(null, NO_ARGS)[0]), "the newer status does not report the cancel");
              helper.assertTrue(newer.getRequestedJobs().isEmpty(), "the newer craftable was not told of the job's end");
              helper.succeed();
            });
          });
        });
      });
    }));
  }

  /**
   * A craftable loaded before (its machine loaded again since) follows its job on until the newer one has told AE2 of
   * it (that one may wait for its interface's chunk: the job was no one's at a grid change), then lets go of it for good.
   */
  @GameTest(template = EMPTY, timeoutTicks = 1000)
  public static void anOlderCraftableHandsItsJobToTheNewerOne(final GameTestHelper helper) {
    final var registries = helper.getLevel().registryAccess();
    craftingGrid(helper, grid -> runningJob(helper, grid, (craftable, status) -> {
      final CompoundTag craftableTag = new CompoundTag();
      final CompoundTag statusTag = new CompoundTag();
      craftable.save(craftableTag, registries);
      status.save(statusTag, registries);
      TestMachines.atEnd(helper, () -> grid.getCraftingService().getCpus().forEach(ICraftingCPU::cancelJob));
      final var older = loadedCraftable(craftableTag, registries);
      retryNow(older);
      helper.assertTrue(requesterOfTheJob(grid, older), "the older craftable did not tell AE2 of the job");
      TestMachines.later(helper, 1, () -> { // (the machine loaded again, at a later tick)
        final var newer = loadedCraftable(craftableTag, registries);
        final var newerStatus = loadedStatus(statusTag, registries);
        // a grid change, as the older one's follower sees it, before the newer one found its interface
        followGridChange(older);
        helper.assertTrue(!older.getRequestedJobs().isEmpty() && requesterOfTheJob(grid, older),
          "the older craftable let go of its job before the newer one told AE2 of it");
        retryNow(newer);
        helper.assertTrue(requesterOfTheJob(grid, newer), "the newer craftable did not tell AE2 of the job");
        followGridChange(older);
        helper.assertTrue(requesterOfTheJob(grid, newer), "the older craftable took the job back (" + requesterOfTheJob(grid, older) + ")");
        helper.assertTrue(older.getRequestedJobs().isEmpty(), "the older craftable still holds the job");
        helper.assertTrue(!newer.getRequestedJobs().isEmpty(), "the newer craftable let go of the job");
        grid.getCraftingService().getCpus().forEach(ICraftingCPU::cancelJob);
        when(helper, () -> !cpuBusy(grid), 20, () -> {
          helper.assertTrue(!cpuBusy(grid), "the job was not cancelled");
          helper.assertTrue(Boolean.TRUE.equals(newerStatus.isCanceled(null, NO_ARGS)[0]), "the newer status does not report the cancel");
          helper.assertTrue(newer.getRequestedJobs().isEmpty(), "the newer craftable was not told of the job's end");
          helper.succeed();
        });
      });
    }));
  }

  /**
   * Copies of a craftable loaded together (a machine saved during a call on one saves it twice: the program's copy and
   * the call's) share its job: either tells AE2 of it, the program's copy keeps it to save, and statuses loaded before
   * and after them follow it across grid changes to its end. Loaded with a link each, the one AE2 was told of last had
   * the job: the program's copy let go of it (its status stale, saved again without the job: "no link" and the job
   * cancelled after a restart).
   */
  @GameTest(template = EMPTY, timeoutTicks = 1000)
  public static void copiesOfACraftableLoadedTogetherShareItsJob(final GameTestHelper helper) {
    final ServerLevel level = helper.getLevel();
    final var registries = level.registryAccess();
    final BlockPos bridge = ON_TOP, at = ON_TOP.above();
    final Runnable cable = () -> helper.assertTrue(PartHelper.setPart(level, helper.absolutePos(bridge), null, null, (IPartItem<?>) ae2Item("fluix_glass_cable")) != null,
      "the cable could not be placed");
    final java.util.function.Supplier<IGrid> main = () -> ((appeng.me.helpers.IGridConnectedBlockEntity) helper.getBlockEntity(CONTROLLER)).getMainNode().getGrid();
    craftingGrid(helper, grid -> {
      cable.run();
      helper.setBlock(PUSHED_TO, Blocks.CHEST);
      runningJob(helper, grid, at, (craftable, status) -> {
        final CompoundTag craftableTag = new CompoundTag();
        final CompoundTag statusTag = new CompoundTag();
        craftable.save(craftableTag, registries);
        status.save(statusTag, registries);
        forgetRequester(grid, craftable);
        final var iface = (appeng.blockentity.misc.InterfaceBlockEntity) helper.getBlockEntity(at);
        // as a machine loads them: the program's status before its craftable, the call's after
        final var kernelStatus = loadedStatus(statusTag, registries);
        final var kernel = loadedCraftable(craftableTag, registries);
        final var stack = loadedCraftable(craftableTag, registries);
        final var stackStatus = loadedStatus(statusTag, registries);
        TestMachines.atEnd(helper, () -> main.get().getCraftingService().getCpus().forEach(ICraftingCPU::cancelJob));
        final var job = kernel.getRequestedJobs();
        helper.assertTrue(job.size() == 1 && job.equals(stack.getRequestedJobs()), "copies loaded together hold links of their own");
        final Object link = job.iterator().next();
        when(helper, () -> kernel.requesting(null, NO_ARGS)[0] instanceof Number && stack.requesting(null, NO_ARGS)[0] instanceof Number, 20, () -> {
          helper.assertTrue(kernel.requesting(null, NO_ARGS)[0] instanceof Number && stack.requesting(null, NO_ARGS)[0] instanceof Number,
            "a copy did not find its interface (they wait for it together)");
          helper.assertTrue(jobRequest(grid) == link, "AE2 does not hold the shared link of the job");
          // the interface in a grid of its own: the call's copy tells AE2 there, its follower first
          helper.setBlock(bridge, Blocks.AIR);
          final IGrid alone = iface.getMainNode().getGrid();
          helper.assertTrue(alone != null && alone != main.get(), "the interface is still in the main grid");
          followGridChange(stack);
          helper.assertTrue(jobRequest(alone) == link, "the call's copy did not tell AE2 of the job in the interface's grid");
          cable.run(); // the network whole again: the program's copy tells AE2, its follower first
          when(helper, () -> iface.getMainNode().getGrid() == main.get(), 20, () -> {
            helper.assertTrue(iface.getMainNode().getGrid() == main.get(), "the interface did not join the main grid again");
            followGridChange(kernel);
            helper.assertTrue(jobRequest(main.get()) == link, "the program's copy did not tell AE2 of the job in the main grid");
            TestMachines.later(helper, 120, () -> { // (past the 60 ticks AE2 waits for a job's requester)
              helper.assertTrue(cpuBusy(main.get()) && runsOn(kernelStatus) && runsOn(stackStatus), "the job was "
                + (cpuBusy(main.get()) ? "reported over" : "cancelled") + " (statuses cancelled " + kernelStatus.isCanceled(null, NO_ARGS)[0]
                + ", " + stackStatus.isCanceled(null, NO_ARGS)[0] + ")");
              final CompoundTag saved = new CompoundTag();
              kernel.save(saved, registries);
              helper.assertTrue(saved.getList("links", net.minecraft.nbt.Tag.TAG_COMPOUND).size() == 1, "the program's copy saves no job: " + saved);
              deliver(main.get());
              when(helper, () -> !cpuBusy(main.get()) && kernel.getRequestedJobs().isEmpty() && stack.getRequestedJobs().isEmpty(), 20, () -> {
                helper.assertTrue(!cpuBusy(main.get()), "the job did not take its stone");
                helper.assertTrue(Boolean.TRUE.equals(kernelStatus.isDone(null, NO_ARGS)[0]) && Boolean.TRUE.equals(stackStatus.isDone(null, NO_ARGS)[0]),
                  "a status does not report the job done (" + kernelStatus.isDone(null, NO_ARGS)[0] + ", " + stackStatus.isDone(null, NO_ARGS)[0] + ")");
                helper.assertTrue(Boolean.FALSE.equals(kernelStatus.isCanceled(null, NO_ARGS)[0]) && Boolean.FALSE.equals(stackStatus.isCanceled(null, NO_ARGS)[0]),
                  "a status reports the job done cancelled");
                helper.assertTrue(kernel.getRequestedJobs().isEmpty() && stack.getRequestedJobs().isEmpty(), "a copy still follows the job done");
                helper.succeed();
              });
            });
          });
        });
      });
    });
  }

  /**
   * A job's end reaches only the copy of a craftable its shared link was loaded for (AE2 tells the link's requester), held
   * by no program here; the other copy lets go of it too (it followed the job for good).
   */
  @GameTest(template = EMPTY, timeoutTicks = 1000)
  public static void everyCopyLetsGoOfAJobOver(final GameTestHelper helper) {
    final var registries = helper.getLevel().registryAccess();
    craftingGrid(helper, grid -> runningJob(helper, grid, (craftable, status) -> {
      final CompoundTag craftableTag = new CompoundTag();
      final CompoundTag statusTag = new CompoundTag();
      craftable.save(craftableTag, registries);
      status.save(statusTag, registries);
      forgetRequester(grid, craftable);
      TestMachines.atEnd(helper, () -> grid.getCraftingService().getCpus().forEach(ICraftingCPU::cancelJob));
      final var first = new WeakReference<>(loadedCraftable(craftableTag, registries));
      final var other = loadedCraftable(craftableTag, registries);
      final var otherStatus = loadedStatus(statusTag, registries);
      when(helper, () -> other.requesting(null, NO_ARGS)[0] instanceof Number, 20, () -> {
        helper.assertTrue(other.requesting(null, NO_ARGS)[0] instanceof Number, "the copy did not find its interface");
        System.gc();
        final var requester = first.get();
        helper.assertTrue(requester != null, "the copy the shared link was loaded for is gone while another holds the job");
        helper.assertTrue(requesterOfTheJob(grid, requester) && !requesterOfTheJob(grid, other), "AE2's requester of the job is not the first copy");
        grid.getCraftingService().getCpus().forEach(ICraftingCPU::cancelJob);
        when(helper, () -> !cpuBusy(grid) && other.getRequestedJobs().isEmpty(), 20, () -> {
          helper.assertTrue(!cpuBusy(grid), "the job was not cancelled");
          helper.assertTrue(Boolean.TRUE.equals(otherStatus.isCanceled(null, NO_ARGS)[0]), "the status does not report the cancel");
          helper.assertTrue(requester.getRequestedJobs().isEmpty(), "the job's requester was not told of its end");
          helper.assertTrue(other.getRequestedJobs().isEmpty() && !following(other), "the other copy follows the job cancelled for good");
          helper.succeed();
        });
      });
    }));
  }

  /** A status saved after its job was cancelled says so once loaded again (no craftable saves a job over: "no link"). */
  @GameTest(template = EMPTY, timeoutTicks = 600)
  public static void aStatusSavedAfterItsJobWasCancelledSaysSo(final GameTestHelper helper) {
    statusSavedAfterItsJobEnded(helper, false);
  }

  /** A status saved after its job was done says so once loaded again. */
  @GameTest(template = EMPTY, timeoutTicks = 600)
  public static void aStatusSavedAfterItsJobWasDoneSaysSo(final GameTestHelper helper) {
    statusSavedAfterItsJobEnded(helper, true);
  }

  private static void statusSavedAfterItsJobEnded(final GameTestHelper helper, final boolean done) {
    final var registries = helper.getLevel().registryAccess();
    craftingGrid(helper, grid -> {
      if (done) helper.setBlock(PUSHED_TO, Blocks.CHEST);
      runningJob(helper, grid, (craftable, status) -> {
        final Runnable end = done ? () -> deliverWhenPushed(helper, grid, () -> {
        }) : () -> grid.getCraftingService().getCpus().forEach(ICraftingCPU::cancelJob);
        end.run();
        when(helper, () -> !cpuBusy(grid) && craftable.getRequestedJobs().isEmpty(), 60, () -> {
          helper.assertTrue(!cpuBusy(grid) && craftable.getRequestedJobs().isEmpty(), "the job did not end");
          final CompoundTag craftableTag = new CompoundTag();
          final CompoundTag statusTag = new CompoundTag();
          craftable.save(craftableTag, registries);
          status.save(statusTag, registries);
          TestMachines.later(helper, 1, () -> { // (the machine loaded again)
            loadedCraftable(craftableTag, registries);
            final var restored = loadedStatus(statusTag, registries);
            helper.assertTrue(Boolean.valueOf(done).equals(restored.isDone(null, NO_ARGS)[0]) && Boolean.valueOf(!done).equals(restored.isCanceled(null, NO_ARGS)[0]),
              "loaded again, the status of a job " + (done ? "done" : "cancelled") + " says done " + java.util.Arrays.toString(restored.isDone(null, NO_ARGS))
                + ", cancelled " + java.util.Arrays.toString(restored.isCanceled(null, NO_ARGS)));
            helper.succeed();
          });
        });
      });
    });
  }

  // where the provider puts the pattern's cobblestone: with a chest there, the job waits for its stone
  private static final BlockPos PUSHED_TO = CONTROLLER.north().north();

  // the stone the job waits for: it is done
  private static void deliver(final IGrid grid) {
    grid.getStorageService().getInventory().insert(AEItemKey.of(Items.STONE), 1, Actionable.MODULATE, IActionSource.empty());
  }

  // the stone, once the job waits for it (the CPU pushes the pattern at its tick: stone came before went to storage)
  private static void deliverWhenPushed(final GameTestHelper helper, final IGrid grid, final Runnable then) {
    final java.util.function.BooleanSupplier pushed = () -> helper.getBlockEntity(PUSHED_TO) instanceof net.minecraft.world.Container chest
      && chest.hasAnyOf(java.util.Set.of(Items.COBBLESTONE));
    when(helper, pushed, 40, () -> {
      helper.assertTrue(pushed.getAsBoolean(), "the pattern was not pushed");
      deliver(grid);
      then.run();
    });
  }

  private static int nexuses(final IGrid grid) {
    try {
      final var links = appeng.me.service.CraftingService.class.getDeclaredField("craftingLinks");
      links.setAccessible(true);
      return ((java.util.Map<?, ?>) links.get(grid.getCraftingService())).size();
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * A restored status first asked once its job is done and the job's link collected (no craftable, AE2 nor CPU holds it
   * then: a program polling now and then after a restart) says done, and saves so: it said "no link" for good.
   */
  @GameTest(template = EMPTY, timeoutTicks = 1000)
  public static void aRestoredStatusSaysHowItsJobEndedOnceItsLinkIsGone(final GameTestHelper helper) {
    final var registries = helper.getLevel().registryAccess();
    craftingGrid(helper, grid -> {
      helper.setBlock(PUSHED_TO, Blocks.CHEST);
      runningJob(helper, grid, (craftable, status) -> {
        final CompoundTag craftableTag = new CompoundTag();
        final CompoundTag statusTag = new CompoundTag();
        craftable.save(craftableTag, registries);
        status.save(statusTag, registries);
        forgetRequester(grid, craftable); // (as after a restart)
        final var restored = loadedCraftable(craftableTag, registries);
        final var restoredStatus = loadedStatus(statusTag, registries); // (not asked before the link is gone)
        when(helper, () -> requesterOfTheJob(grid, restored), 20, () -> {
          helper.assertTrue(requesterOfTheJob(grid, restored), "the restored craftable did not tell AE2 of the job");
          final var link = new WeakReference<>(restored.getRequestedJobs().iterator().next());
          deliverWhenPushed(helper, grid, () -> when(helper, () -> {
            System.gc();
            return link.get() == null;
          }, 40, () -> {
            helper.assertTrue(!cpuBusy(grid) && restored.getRequestedJobs().isEmpty(), "the job was not done");
            helper.assertTrue(link.get() == null, "the job's link is still held");
            helper.assertTrue(Boolean.TRUE.equals(restoredStatus.isDone(null, NO_ARGS)[0]) && Boolean.FALSE.equals(restoredStatus.isCanceled(null, NO_ARGS)[0]),
              "asked once its job's link was gone, the status says done " + java.util.Arrays.toString(restoredStatus.isDone(null, NO_ARGS)));
            final CompoundTag saved = new CompoundTag();
            restoredStatus.save(saved, registries);
            helper.assertTrue(saved.getBoolean("done"), "the status saves no ending: " + saved);
            helper.succeed();
          }));
        });
      });
    });
  }

  /**
   * A job done after its machine was saved, its old craftable about (the computer's chunk unloaded, AE2's loaded), is not
   * told AE2 again by the machine loaded again, and its status says done: AE2 made it a nexus with no CPU and cancelled
   * it, the status said cancelled (a program requesting again on a cancel crafted twice).
   */
  @GameTest(template = EMPTY, timeoutTicks = 1000)
  public static void aJobDoneBeforeItsMachineLoadedAgainStaysDone(final GameTestHelper helper) {
    final var registries = helper.getLevel().registryAccess();
    craftingGrid(helper, grid -> {
      helper.setBlock(PUSHED_TO, Blocks.CHEST);
      runningJob(helper, grid, (craftable, status) -> {
        final CompoundTag craftableTag = new CompoundTag();
        final CompoundTag statusTag = new CompoundTag();
        craftable.save(craftableTag, registries); // (the machine's chunk unloads: the craftable lives on, the job's requester)
        status.save(statusTag, registries);
        deliverWhenPushed(helper, grid, () -> when(helper, () -> !cpuBusy(grid) && craftable.getRequestedJobs().isEmpty() && nexuses(grid) == 0, 20, () -> {
          helper.assertTrue(!cpuBusy(grid) && craftable.getRequestedJobs().isEmpty() && nexuses(grid) == 0, "the job was not done");
          TestMachines.later(helper, 1, () -> { // (the machine loaded again)
            final var restored = loadedCraftable(craftableTag, registries);
            final var restoredStatus = loadedStatus(statusTag, registries);
            retryNow(restored);
            helper.assertTrue(restored.requesting(null, NO_ARGS)[0] instanceof Number, "the restored craftable did not find its interface");
            helper.assertTrue(restored.getRequestedJobs().isEmpty() && nexuses(grid) == 0, "the machine loaded again told AE2 of the job done");
            TestMachines.later(helper, 70, () -> { // (AE2 cancels a nexus with no CPU after 61 ticks)
              helper.assertTrue(Boolean.TRUE.equals(restoredStatus.isDone(null, NO_ARGS)[0]) && Boolean.FALSE.equals(restoredStatus.isCanceled(null, NO_ARGS)[0]),
                "the job done says done " + java.util.Arrays.toString(restoredStatus.isDone(null, NO_ARGS)) + ", cancelled "
                  + java.util.Arrays.toString(restoredStatus.isCanceled(null, NO_ARGS)));
              helper.succeed();
            });
          });
        }));
      });
    });
  }

  /**
   * A craftable with two jobs whose interface is broken cancels both and stops following: cancelling the first told it
   * of the end at once, which took the link out of the set being gone through (the follower died, marked following,
   * still waiting: "waiting for ae network to load" for good).
   */
  @GameTest(template = EMPTY, timeoutTicks = 600)
  public static void aCraftableLosingItsInterfaceCancelsAllItsJobs(final GameTestHelper helper) {
    craftingGrid(helper, grid -> {
      helper.setBlock(CONTROLLER.south(), ae2Block("1k_crafting_storage")); // (a second CPU)
      helper.setBlock(ON_TOP, ae2Block("interface"));
      final var iface = (appeng.blockentity.misc.InterfaceBlockEntity) helper.getBlockEntity(ON_TOP);
      TestMachines.atEnd(helper, () -> cancelJobs(helper));
      when(helper, () -> iface.getMainNode().isActive() && grid.getCraftingService().getCpus().size() == 2, 100, () -> {
        helper.assertTrue(grid.getCraftingService().getCpus().size() == 2, "the second CPU did not form");
        final var craftable = stoneCraftable(helper, new DriverBlockInterface.Environment(iface));
        final var first = request(helper, craftable);
        final var second = request(helper, craftable);
        when(helper, () -> submitted(first) && submitted(second) && craftable.getRequestedJobs().size() == 2, 200, () -> {
          helper.assertTrue(craftable.getRequestedJobs().size() == 2, "the jobs did not start: " + java.util.Arrays.toString(first.isDone(null, NO_ARGS))
            + ", " + java.util.Arrays.toString(second.isDone(null, NO_ARGS)));
          helper.setBlock(ON_TOP, Blocks.AIR);
          when(helper, () -> craftable.getRequestedJobs().isEmpty() && !following(craftable), 20, () -> {
            helper.assertTrue(Boolean.TRUE.equals(first.isCanceled(null, NO_ARGS)[0]) && Boolean.TRUE.equals(second.isCanceled(null, NO_ARGS)[0]),
              "a job of the interface broken was not cancelled");
            helper.assertTrue(craftable.getRequestedJobs().isEmpty() && !following(craftable), "the craftable's follower is stuck ("
              + craftable.getRequestedJobs().size() + " jobs, following " + following(craftable) + ")");
            final Object[] again = craftable.request(null, new ArgumentsImpl(new Object[]{1}));
            helper.assertTrue(again.length > 1 && "no controller".equals(again[1]), "with its interface broken the craftable says " + java.util.Arrays.toString(again));
            helper.succeed();
          });
        });
      });
    });
  }

  /**
   * A craftable restored whose controller is gone for good (nothing where it stood, another block, an unknown dimension,
   * no position saved) cancels its jobs: AE2 cancelled them for want of a requester, and their statuses said neither
   * done nor cancelled for good.
   */
  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void restoredCraftablesWithTheirControllerGoneCancelTheirJobs(final GameTestHelper helper) {
    final ServerLevel level = helper.getLevel();
    final var registries = level.registryAccess();
    helper.setBlock(CONTROLLER, Blocks.CHEST);
    final String here = level.dimension().location().toString();
    final Object[][] places = {
      {"nothing", here, helper.absolutePos(ON_TOP)},
      {"a chest", here, helper.absolutePos(CONTROLLER)},
      {"in an unknown dimension", "oc_test:nowhere", helper.absolutePos(CONTROLLER)},
      {"nowhere saved", null, null}};
    final java.util.List<NetworkControl.Craftable> craftables = new java.util.ArrayList<>(); // (their retries hold them weakly)
    final java.util.List<NetworkControl.CraftingStatus> statuses = new java.util.ArrayList<>();
    for (final Object[] place : places) {
      final UUID id = UUID.randomUUID();
      final CompoundTag link = new CompoundTag();
      link.putUUID("craftId", id);
      link.putBoolean("req", true);
      final var links = new net.minecraft.nbt.ListTag();
      links.add(link);
      final CompoundTag craftableTag = new CompoundTag();
      craftableTag.put("links", links);
      if (place[1] != null) {
        final BlockPos pos = (BlockPos) place[2];
        craftableTag.putString("dimension", (String) place[1]);
        craftableTag.putInt("x", pos.getX());
        craftableTag.putInt("y", pos.getY());
        craftableTag.putInt("z", pos.getZ());
      }
      final CompoundTag statusTag = new CompoundTag();
      statusTag.putBoolean("computing", false);
      statusTag.putString("link", id.toString());
      statusTag.putString("session", linkCache("session").toString()); // (this server's: only its craftable tells it the job's end)
      statuses.add(loadedStatus(statusTag, registries));
      craftables.add(loadedCraftable(craftableTag, registries));
    }
    when(helper, () -> statuses.stream().allMatch(s -> Boolean.TRUE.equals(s.isCanceled(null, NO_ARGS)[0])), 20, () -> {
      for (int i = 0; i < places.length; i++) {
        helper.assertTrue(Boolean.TRUE.equals(statuses.get(i).isCanceled(null, NO_ARGS)[0]), "the status of a craftable whose controller is "
          + places[i][0] + " says " + java.util.Arrays.toString(statuses.get(i).isCanceled(null, NO_ARGS)) + " (" + craftables.get(i).getRequestedJobs().size() + " jobs)");
      }
      helper.succeed();
    });
  }

  private static boolean computing(final NetworkControl.CraftingStatus status) {
    final Object[] result = status.isDone(null, NO_ARGS);
    return result.length == 2 && "computing".equals(result[1]);
  }

  /**
   * A status saved while computing is not computing for good once loaded again (OC 1.12's was): a request whose status
   * nothing knows of any more (a server's restart since) failed.
   */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void aStatusSavedComputingIsNotComputingForGood(final GameTestHelper helper) {
    final var registries = helper.getLevel().registryAccess();
    final CompoundTag unknown = new CompoundTag();
    unknown.putBoolean("computing", true);
    unknown.putString("request", UUID.randomUUID().toString());
    final CompoundTag older = new CompoundTag(); // (saved without its request's token)
    older.putBoolean("computing", true);
    for (final CompoundTag tag : new CompoundTag[]{unknown, older}) {
      final Object[] result = loadedStatus(tag, registries).isDone(null, NO_ARGS);
      helper.assertTrue(result.length == 2 && Boolean.FALSE.equals(result[0]) && String.valueOf(result[1]).contains("reloaded while computing"),
        "a status saved computing, its request unknown, says " + java.util.Arrays.toString(result));
    }
    helper.succeed();
  }

  /**
   * A request whose status was saved while computing and loaded again before the job was submitted submits nothing: the
   * job would have been the old craftable's, which its machine no longer has (saved without it), and no status followed it.
   * The status loaded again says why it failed.
   */
  @GameTest(template = EMPTY, timeoutTicks = 600)
  public static void aRequestLoadedAgainWhileComputingSubmitsNothing(final GameTestHelper helper) {
    final var registries = helper.getLevel().registryAccess();
    craftingGrid(helper, grid -> {
      helper.setBlock(ON_TOP, ae2Block("interface"));
      final var iface = (appeng.blockentity.misc.InterfaceBlockEntity) helper.getBlockEntity(ON_TOP);
      when(helper, () -> iface.getMainNode().isActive(), 100, () -> {
        final var status = request(helper, stoneCraftable(helper, new DriverBlockInterface.Environment(iface)));
        TestMachines.atEnd(helper, () -> grid.getCraftingService().getCpus().forEach(ICraftingCPU::cancelJob));
        final CompoundTag statusTag = new CompoundTag();
        status.save(statusTag, registries); // (the submission comes from the server's task queue, after this step)
        helper.assertTrue(statusTag.getBoolean("computing"), "the status was saved past computing");
        final var restored = loadedStatus(statusTag, registries);
        when(helper, () -> !computing(status), 200, () -> {
          helper.assertTrue(!computing(status), "the request never came back");
          TestMachines.later(helper, 5, () -> {
            helper.assertTrue(!cpuBusy(grid), "a job was submitted for the craftable its machine no longer has");
            final Object[] result = restored.isDone(null, NO_ARGS);
            helper.assertTrue(result.length == 2 && Boolean.FALSE.equals(result[0]) && String.valueOf(result[1]).contains("reloaded while computing"),
              "the status loaded again says " + java.util.Arrays.toString(result));
            helper.succeed();
          });
        });
      });
    });
  }

  /**
   * A request whose status was saved while computing, and loaded again after the job was submitted, is followed by the
   * status loaded again (it said computing for good).
   */
  @GameTest(template = EMPTY, timeoutTicks = 600)
  public static void aStatusSavedComputingFollowsTheJobSubmittedSince(final GameTestHelper helper) {
    final var registries = helper.getLevel().registryAccess();
    craftingGrid(helper, grid -> {
      helper.setBlock(ON_TOP, ae2Block("interface"));
      final var iface = (appeng.blockentity.misc.InterfaceBlockEntity) helper.getBlockEntity(ON_TOP);
      when(helper, () -> iface.getMainNode().isActive(), 100, () -> {
        final var status = request(helper, stoneCraftable(helper, new DriverBlockInterface.Environment(iface)));
        TestMachines.atEnd(helper, () -> grid.getCraftingService().getCpus().forEach(ICraftingCPU::cancelJob));
        final CompoundTag statusTag = new CompoundTag();
        status.save(statusTag, registries);
        helper.assertTrue(statusTag.getBoolean("computing"), "the status was saved past computing");
        when(helper, () -> submitted(status) && cpuBusy(grid), 200, () -> {
          helper.assertTrue(submitted(status) && cpuBusy(grid), "the job did not start: " + java.util.Arrays.toString(status.isDone(null, NO_ARGS)));
          final var restored = loadedStatus(statusTag, registries);
          helper.assertTrue(submitted(restored) && runsOn(restored), "the status saved computing does not follow the job submitted since: "
            + java.util.Arrays.toString(restored.isDone(null, NO_ARGS)));
          helper.assertTrue(Boolean.TRUE.equals(restored.cancel(null, NO_ARGS)[0]), "the status saved computing could not cancel the job");
          when(helper, () -> !cpuBusy(grid), 20, () -> {
            helper.assertTrue(!cpuBusy(grid), "the job was not cancelled");
            helper.assertTrue(Boolean.TRUE.equals(restored.isCanceled(null, NO_ARGS)[0]), "the status saved computing does not report the cancel");
            helper.succeed();
          });
        });
      });
    });
  }

  // what a chunk's unload and load again does to a block entity: it comes back anew from its saved data
  private static void unloadAndLoadAgain(final GameTestHelper helper, final BlockPos at) {
    final ServerLevel level = helper.getLevel();
    final BlockPos abs = helper.absolutePos(at);
    final var old = level.getBlockEntity(abs);
    final CompoundTag saved = old.saveWithFullMetadata();
    old.onChunkUnloaded();
    level.removeBlockEntity(abs);
    level.getChunkAt(abs).addAndRegisterBlockEntity(net.minecraft.world.level.block.entity.BlockEntity.loadStatic(abs, level.getBlockState(abs), saved));
  }

  // as when the network comes back from its chunks: the job, restored with its CPU, waits 60 ticks for its requester
  private static void jobWaitsForItsRequester(final IGrid grid) {
    try {
      final var links = appeng.me.service.CraftingService.class.getDeclaredField("craftingLinks");
      links.setAccessible(true);
      for (final Object nexus : ((java.util.Map<?, ?>) links.get(grid.getCraftingService())).values()) {
        ((appeng.crafting.CraftingLinkNexus) nexus).removeNode();
      }
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * A craftable whose interface's chunk unloaded and loaded again while its machine stayed finds the interface again
   * where it stood: it held the old one, said "no ae grid" and "no controller" for good, and its job, the network loaded
   * again around it, was cancelled for want of a requester.
   */
  @GameTest(template = EMPTY, timeoutTicks = 1000)
  public static void aCraftableFindsItsInterfaceLoadedAgain(final GameTestHelper helper) {
    craftingGrid(helper, grid -> runningJob(helper, grid, (craftable, status) -> {
      final var idle = stoneCraftable(helper, new DriverBlockInterface.Environment((appeng.blockentity.misc.InterfaceBlockEntity) helper.getBlockEntity(ON_TOP)));
      TestMachines.atEnd(helper, () -> grid.getCraftingService().getCpus().forEach(ICraftingCPU::cancelJob));
      unloadAndLoadAgain(helper, ON_TOP);
      jobWaitsForItsRequester(grid);
      TestMachines.later(helper, 120, () -> { // (past the 60 ticks AE2 waits for a job's requester)
        helper.assertTrue(cpuBusy(grid) && runsOn(status) && requesterOfTheJob(grid, craftable), "the job of a craftable whose interface was loaded again was "
          + (cpuBusy(grid) ? "left to no requester" : "cancelled") + " (status " + java.util.Arrays.toString(status.isDone(null, NO_ARGS)) + ")");
        final Object[] requesting = idle.requesting(null, NO_ARGS);
        helper.assertTrue(requesting[0] instanceof Number, "a craftable with no job did not find its interface loaded again: " + java.util.Arrays.toString(requesting));
        helper.succeed();
      });
    }));
  }

  private static NetworkControl.Craftable loadedCraftable(final CompoundTag tag, final net.minecraft.core.HolderLookup.Provider registries) {
    final var craftable = new NetworkControl.Craftable();
    craftable.load(tag, registries);
    return craftable;
  }

  private static NetworkControl.CraftingStatus loadedStatus(final CompoundTag tag, final net.minecraft.core.HolderLookup.Provider registries) {
    final var status = new NetworkControl.CraftingStatus();
    status.load(tag, registries);
    return status;
  }

  // the link AE2 holds as the request of the grid's one job
  private static Object jobRequest(final IGrid grid) {
    try {
      final var links = appeng.me.service.CraftingService.class.getDeclaredField("craftingLinks");
      links.setAccessible(true);
      final var nexuses = ((java.util.Map<?, ?>) links.get(grid.getCraftingService())).values();
      return nexuses.size() == 1 ? ((appeng.crafting.CraftingLinkNexus) nexuses.iterator().next()).getRequest() : null;
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private static boolean following(final NetworkControl.Craftable craftable) {
    try {
      final var following = NetworkControl.Craftable.class.getDeclaredField("following");
      following.setAccessible(true);
      return following.getBoolean(craftable);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  // a restored craftable's retry for its interface, now
  private static void retryNow(final NetworkControl.Craftable craftable) {
    try {
      final var retry = NetworkControl.Craftable.class.getDeclaredMethod("delayLoadGrid");
      retry.setAccessible(true);
      retry.invoke(craftable);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  // what the craftable's follower does on its next tick after its interface went to another grid
  private static void followGridChange(final NetworkControl.Craftable craftable) {
    try {
      final var linked = NetworkControl.Craftable.class.getDeclaredField("linkedGrid");
      linked.setAccessible(true);
      linked.set(craftable, null);
      final var follow = NetworkControl.Craftable.class.getDeclaredMethod("followGrid");
      follow.setAccessible(true);
      follow.invoke(craftable);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * A restored craftable still waiting for its controller's chunk is saved with where it waits (saved without, it never
   * looked again: "no controller" for good, and AE2 cancelled its jobs).
   */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void waitingCraftablesAreSavedWithWhereTheyWait(final GameTestHelper helper) {
    final ServerLevel level = helper.getLevel();
    final var registries = level.registryAccess();
    final ChunkPos chunk = new ChunkPos(60000 + level.random.nextInt(5000), -60000 - level.random.nextInt(5000));
    final BlockPos pos = chunk.getMiddleBlockPosition(64);
    helper.assertTrue(level.getChunkSource().getChunkNow(chunk.x, chunk.z) == null, "the far chunk is loaded already");
    final CompoundTag tag = new CompoundTag();
    tag.putString("dimension", level.dimension().location().toString());
    tag.putInt("x", pos.getX());
    tag.putInt("y", pos.getY());
    tag.putInt("z", pos.getZ());
    tag.putInt("side", Direction.UP.get3DDataValue());
    final var waiting = new NetworkControl.Craftable();
    waiting.load(tag, registries);
    final CompoundTag saved = new CompoundTag();
    waiting.save(saved, registries);
    helper.assertTrue(level.dimension().location().toString().equals(saved.getString("dimension")) && saved.getInt("x") == pos.getX()
      && saved.getInt("y") == pos.getY() && saved.getInt("z") == pos.getZ() && saved.contains("side") && saved.getInt("side") == Direction.UP.get3DDataValue(),
      "a craftable waiting for its controller was saved as " + saved);
    final var again = new NetworkControl.Craftable();
    again.load(saved, registries);
    final Object[] result = again.request(null, new ArgumentsImpl(new Object[]{1}));
    helper.assertTrue(result.length > 1 && "waiting for ae network to load".equals(result[1]), "loaded again, the craftable says " + java.util.Arrays.toString(result));
    helper.succeed();
  }

  /**
   * An interface part on a cable crafts, and a craftable restored finds it again (it is no block entity: the request
   * threw for want of a level, and no position was saved).
   */
  @GameTest(template = EMPTY, timeoutTicks = 600)
  public static void partInterfacesCraftAndAreFoundAgain(final GameTestHelper helper) {
    final ServerLevel level = helper.getLevel();
    final var registries = level.registryAccess();
    craftingGrid(helper, grid -> {
      final BlockPos abs = helper.absolutePos(ON_TOP);
      helper.assertTrue(PartHelper.setPart(level, abs, null, null, (IPartItem<?>) ae2Item("fluix_glass_cable")) != null, "the cable could not be placed");
      final var part = PartHelper.setPart(level, abs, Direction.UP, null, (IPartItem<?>) ae2Item("cable_interface"));
      helper.assertTrue(part instanceof appeng.api.networking.security.IActionHost, "the interface part could not be placed");
      final var host = (appeng.api.networking.security.IActionHost) part;
      when(helper, () -> host.getActionableNode() != null && host.getActionableNode().isActive(), 100, () -> {
        final var env = new DriverPartInterface.Environment((IPartHost) level.getBlockEntity(abs), Direction.UP);
        final var craftable = stoneCraftable(helper, env);
        final var status = request(helper, craftable);
        TestMachines.atEnd(helper, () -> cancelJobs(helper));
        when(helper, () -> submitted(status) && cpuBusy(grid), 200, () -> {
          helper.assertTrue(submitted(status) && cpuBusy(grid), "the job did not start: " + java.util.Arrays.toString(status.isDone(null, NO_ARGS)));
          final CompoundTag tag = new CompoundTag();
          craftable.save(tag, registries);
          final var restored = new NetworkControl.Craftable();
          restored.load(tag, registries);
          when(helper, () -> restored.requesting(null, NO_ARGS)[0] instanceof Number, 60, () -> {
            helper.assertTrue(restored.requesting(null, NO_ARGS)[0] instanceof Number, "the restored craftable did not find its interface part: " + tag);
            status.cancel(null, NO_ARGS);
            helper.succeed();
          });
        });
      });
    });
  }

  // a craftable as a machine restores it, for a controller at pos; held weakly
  private static WeakReference<NetworkControl.Craftable> restoredCraftable(final ServerLevel level, final BlockPos pos) {
    final CompoundTag tag = new CompoundTag();
    tag.putString("dimension", level.dimension().location().toString());
    tag.putInt("x", pos.getX());
    tag.putInt("y", pos.getY());
    tag.putInt("z", pos.getZ());
    final var craftable = new NetworkControl.Craftable();
    craftable.load(tag, level.registryAccess());
    return new WeakReference<>(craftable);
  }

  private static void chunkStaysUnloaded(final GameTestHelper helper, final ChunkPos chunk, final int ticks, final Runnable then) {
    helper.assertTrue(helper.getLevel().getChunkSource().getChunkNow(chunk.x, chunk.z) == null, "a restored craftable loaded its controller's chunk");
    if (ticks <= 0) then.run();
    else TestMachines.later(helper, 1, () -> chunkStaysUnloaded(helper, chunk, ticks - 1, then));
  }

  private static void collected(final GameTestHelper helper, final WeakReference<?> ref, final int ticks) {
    System.gc();
    if (ref.get() == null) {
      helper.succeed();
      return;
    }
    helper.assertTrue(ticks > 0, "a restored craftable no machine holds is kept by its retries");
    TestMachines.later(helper, 1, () -> collected(helper, ref, ticks - 1));
  }

  /**
   * A restored craftable waits for its controller's chunk without loading it, and stops waiting with its machine (it
   * loaded the chunk, and retried, holding the craftable, forever).
   */
  @GameTest(template = EMPTY, timeoutTicks = 200)
  public static void restoredCraftablesLoadNoChunksAndEndWithTheirMachine(final GameTestHelper helper) {
    final ServerLevel level = helper.getLevel();
    final ChunkPos chunk = new ChunkPos(60000 + level.random.nextInt(5000), 60000 + level.random.nextInt(5000));
    helper.assertTrue(level.getChunkSource().getChunkNow(chunk.x, chunk.z) == null, "the far chunk is loaded already");
    final var ref = restoredCraftable(level, chunk.getMiddleBlockPosition(64));
    chunkStaysUnloaded(helper, chunk, 30, () -> collected(helper, ref, 60));
  }

  /** An export or import bus's configuration is the configured item, one of it, as OC 1.12 (a bus keeps types only: air). */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void busConfigurationsGiveTheirItem(final GameTestHelper helper) {
    final BlockPos abs = helper.absolutePos(CONTROLLER);
    final var export = PartHelper.setPart(helper.getLevel(), abs, Direction.WEST, null, (IPartItem<?>) ae2Item("export_bus"));
    final var importBus = PartHelper.setPart(helper.getLevel(), abs, Direction.EAST, null, (IPartItem<?>) ae2Item("import_bus"));
    helper.assertTrue(export instanceof appeng.helpers.IConfigInvHost && importBus instanceof appeng.helpers.IConfigInvHost, "the buses could not be placed");
    ((appeng.helpers.IConfigInvHost) export).getConfig().setStack(0, new GenericStack(AEItemKey.of(Items.DIAMOND), 1));
    ((appeng.helpers.IConfigInvHost) importBus).getConfig().setStack(0, new GenericStack(AEItemKey.of(Items.EMERALD), 1));
    final var host = (IPartHost) helper.getLevel().getBlockEntity(abs);
    final Object exported = new DriverExportBus.Environment(host).getExportConfiguration(null, new ArgumentsImpl(new Object[]{Direction.WEST.get3DDataValue()}))[0];
    final Object imported = new DriverImportBus.Environment(host).getImportConfiguration(null, new ArgumentsImpl(new Object[]{Direction.EAST.get3DDataValue()}))[0];
    helper.assertTrue(exported instanceof ItemStack s && s.is(Items.DIAMOND) && s.getCount() == 1, "the export bus's configuration is " + exported);
    helper.assertTrue(imported instanceof ItemStack s && s.is(Items.EMERALD) && s.getCount() == 1, "the import bus's configuration is " + imported);
    helper.succeed();
  }
}
