package li.cil.oc.neoforge.gametest;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import li.cil.oc.api.fs.Mode;
import li.cil.oc.core.Constants;
import li.cil.oc.core.impl.OCSettings;
import li.cil.oc.core.impl.integration.opencomputers.Item;
import li.cil.oc.core.impl.server.machine.ArgumentsImpl;
import li.cil.oc.neoforge.OpenComputers;
import li.cil.oc.neoforge.common.init.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Drives save what is written to them, move and rename like file systems do, and stay inside their folder.
 */
@GameTestHolder(OpenComputers.ID)
@PrefixGameTestTemplate(false)
public final class FileSystemTests {
  private static final String EMPTY = "empty";
  private static final BlockPos AT = new BlockPos(2, 2, 2);

  private FileSystemTests() {
  }

  private static ItemStack item(final String name) {
    return li.cil.oc.api.Items.get(name).createItemStack(1);
  }

  private static li.cil.oc.api.machine.Context context() {
    final var node = li.cil.oc.api.Network.newNode(null, li.cil.oc.api.network.Visibility.None).create();
    return (li.cil.oc.api.machine.Context) java.lang.reflect.Proxy.newProxyInstance(li.cil.oc.api.machine.Context.class.getClassLoader(),
      new Class<?>[]{li.cil.oc.api.machine.Context.class}, (proxy, method, args) -> switch (method.getName()) {
        case "node" -> node;
        default -> method.getReturnType() == boolean.class ? false : null;
      });
  }

  private static File worldRoot(final GameTestHelper helper) {
    return helper.getLevel().getServer().getWorldPath(LevelResource.ROOT).toFile();
  }

  private static void writeFile(final li.cil.oc.api.fs.FileSystem fs, final String path, final String content) throws Exception {
    final int handle = fs.open(path, Mode.Write);
    fs.getHandle(handle).write(content.getBytes(StandardCharsets.UTF_8));
    fs.getHandle(handle).close();
  }

  /**
   * The environment of a floppy put into a disk drive in a chunk of its own (other tests' blocks mark a shared chunk);
   * then is called with it and the drive's chunk, and the block and the chunk's ticket go away afterwards.
   */
  private static void floppyInDrive(final GameTestHelper helper, final ItemStack floppy,
                                    final java.util.function.BiConsumer<li.cil.oc.api.network.ManagedEnvironment, net.minecraft.world.level.chunk.LevelChunk> then) {
    final var level = helper.getLevel();
    final BlockPos at = new BlockPos(28000 + level.random.nextInt(2000) * 16, 100, 28000 + level.random.nextInt(2000) * 16);
    final var chunkPos = new net.minecraft.world.level.ChunkPos(at);
    level.setChunkForced(chunkPos.x, chunkPos.z, true);
    helper.runAfterDelay(3, () -> {
      level.setBlock(at, Blocks.DISK_DRIVE.get().defaultBlockState(), 3);
      final var drive = (li.cil.oc.core.impl.common.blockentity.DiskDrive) level.getBlockEntity(at);
      drive.setItem(0, floppy);
      helper.runAfterDelay(5, () -> {
        li.cil.oc.api.network.ManagedEnvironment found = null;
        for (final var env : drive.componentEnvironments()) {
          if (env != null) found = env;
        }
        helper.assertTrue(found != null, "no environment for the floppy in the drive");
        final var env = found;
        final Runnable cleanUp = () -> {
          drive.setItem(0, ItemStack.EMPTY);
          level.removeBlockEntity(at);
          level.setBlock(at, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
          level.setChunkForced(chunkPos.x, chunkPos.z, false);
        };
        try {
          then.accept(env, level.getChunkAt(at));
        } catch (Throwable t) {
          cleanUp.run();
          throw t;
        }
        helper.runAfterDelay(15, cleanUp);
      });
    });
  }

  // a host that counts what it is told: the component has to say it changed (chunks are no measure here - other
  // tests' blocks and fresh terrain mark them)
  private static li.cil.oc.api.network.EnvironmentHost countingHost(final GameTestHelper helper, final java.util.concurrent.atomic.AtomicInteger changes) {
    return (li.cil.oc.api.network.EnvironmentHost) java.lang.reflect.Proxy.newProxyInstance(li.cil.oc.api.network.EnvironmentHost.class.getClassLoader(),
      new Class<?>[]{li.cil.oc.api.network.EnvironmentHost.class}, (proxy, method, args) -> switch (method.getName()) {
        case "world", "level" -> helper.getLevel();
        case "xPosition", "yPosition", "zPosition" -> 0.0;
        case "markChanged" -> {
          changes.incrementAndGet();
          yield null;
        }
        case "hashCode" -> System.identityHashCode(proxy);
        case "equals" -> proxy == args[0];
        case "toString" -> "counting host";
        default -> method.getReturnType() == boolean.class ? false : null;
      });
  }

  private static void writeTellsTheHost(final GameTestHelper helper, final ItemStack floppy, final java.util.function.Consumer<li.cil.oc.api.network.ManagedEnvironment> write) {
    final var changes = new java.util.concurrent.atomic.AtomicInteger();
    final var env = li.cil.oc.api.API.driver.driverFor(floppy).createEnvironment(floppy, countingHost(helper, changes));
    helper.assertTrue(env != null, "no environment for " + floppy);
    li.cil.oc.api.Network.joinNewNetwork(env.node());
    try {
      final int before = changes.get();
      write.accept(env);
      helper.assertTrue(changes.get() > before, "writing to " + floppy + " did not tell its host it changed");
    } finally {
      env.node().remove();
    }
    helper.succeed();
  }

  /** Writing to a floppy tells its host (a disk drive) to save (only the computer's block was: the writes were lost). */
  @GameTest(template = EMPTY)
  public static void floppyWritesTellTheirHost(final GameTestHelper helper) {
    writeTellsTheHost(helper, item(Constants.ItemName.Floppy),
      env -> ((li.cil.oc.core.impl.server.component.FileSystem) env).makeDirectory(context(), new ArgumentsImpl(new Object[]{"/saved"})));
  }

  /** Writing to an unmanaged floppy tells its host too. */
  @GameTest(template = EMPTY)
  public static void unmanagedWritesTellTheirHost(final GameTestHelper helper) {
    final ItemStack floppy = item(Constants.ItemName.Floppy);
    li.cil.oc.core.impl.common.item.data.DriveData.setUnmanaged(floppy, true);
    writeTellsTheHost(helper, floppy,
      env -> ((li.cil.oc.core.impl.server.component.Drive) env).writeByte(context(), new ArgumentsImpl(new Object[]{1, 65})));
  }

  /**
   * markChanged from a computer thread does not touch the chunk there (the disk drive and RAID called setChanged:
   * off the server thread that waits on the server thread - here, the server thread waiting on it).
   */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void markChangedOffThreadDoesNotWait(final GameTestHelper helper) throws Exception {
    final java.util.List<net.minecraft.world.level.block.Block> blocks = java.util.List.of(Blocks.DISK_DRIVE.get(), Blocks.RAID.get(), Blocks.MOTION_SENSOR.get(), Blocks.CABLE.get(), Blocks.NET_SPLITTER.get());
    for (final var block : blocks) {
      helper.setBlock(AT, block);
      final var host = (li.cil.oc.core.impl.common.blockentity.traits.BlockEntity) helper.getBlockEntity(AT);
      final Thread computer = new Thread(host::markChanged, "computer");
      computer.start();
      computer.join(1000);
      helper.assertTrue(!computer.isAlive(), block + ": markChanged from a computer thread waits on the server thread");
    }
    helper.succeed();
  }

  /** rename refuses a directory into itself, a file over a directory and the like, and onto itself frees nothing. */
  @GameTest(template = EMPTY)
  public static void renameFollowsFileSystemRules(final GameTestHelper helper) throws Exception {
    final var ram = li.cil.oc.api.FileSystem.fromMemory(64 * 1024);
    checkRename(helper, ram, "tmpfs");
    final String root = "oc-test-" + UUID.randomUUID();
    final var buffered = li.cil.oc.api.FileSystem.fromSaveDirectory(root, 64 * 1024, true);
    try {
      checkRename(helper, buffered, "buffered");
    } finally {
      buffered.close();
      org.apache.commons.io.FileUtils.deleteQuietly(new File(worldRoot(helper), OCSettings.savePath + root));
    }
    helper.succeed();
  }

  private static void checkRename(final GameTestHelper helper, final li.cil.oc.api.fs.FileSystem fs, final String kind) throws Exception {
    helper.assertTrue(fs.makeDirectory("d"), kind + ": no directory");
    writeFile(fs, "d/x", "content");
    helper.assertTrue(!fs.rename("d", "d/e") && fs.exists("d/x"), kind + ": a directory moved into itself (d/x is " + (fs.exists("d/x") ? "there" : "gone") + ")");
    final long used = fs.spaceUsed();
    for (int i = 0; i < 5; i++) fs.rename("d/x", "d/x");
    helper.assertTrue(fs.spaceUsed() == used && fs.exists("d/x"), kind + ": renaming a file onto itself changed the space used from " + used + " to " + fs.spaceUsed());
    writeFile(fs, "y", "file");
    helper.assertTrue(!fs.rename("y", "d") && fs.exists("d/x"), kind + ": a file replaced a non-empty directory");
    helper.assertTrue(!fs.rename("d", "y") && fs.exists("y") && !fs.isDirectory("y"), kind + ": a directory replaced a file");
  }

  /** Switching a drive's mode gives it a new address (the change was made to a copy: contents and unlocking stayed). */
  @GameTest(template = EMPTY)
  public static void switchingDriveModeWipesIt(final GameTestHelper helper) {
    final ItemStack floppy = item(Constants.ItemName.Floppy);
    final CompoundTag node = new CompoundTag();
    node.putString("address", UUID.randomUUID().toString());
    Item.getDataTag(floppy).put("node", node);
    li.cil.oc.core.impl.common.item.data.DriveData.setUnmanaged(floppy, true);
    helper.assertTrue(Item.address(floppy) == null, "the floppy kept its address " + Item.address(floppy) + " through a mode switch");
    helper.succeed();
  }

  /** Drive addresses from items cannot name a place outside the save folder (../../x read and wrote anywhere). */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void driveAddressesStayInTheSaveFolder(final GameTestHelper helper) {
    final String escape = "oc-escape-" + UUID.randomUUID();
    final File outside = new File(worldRoot(helper), OCSettings.savePath + "../../" + escape);
    try {
      helper.assertTrue(li.cil.oc.api.FileSystem.fromSaveDirectory("../../" + escape, 1024, true) == null && !outside.exists(),
        "a managed file system was made outside the save folder");
    } finally {
      org.apache.commons.io.FileUtils.deleteQuietly(outside);
    }
    final ItemStack floppy = item(Constants.ItemName.Floppy);
    li.cil.oc.core.impl.common.item.data.DriveData.setUnmanaged(floppy, true);
    final File outsideBin = new File(worldRoot(helper), OCSettings.savePath + "../../" + escape + ".bin");
    floppyInDrive(helper, floppy, (env, chunk) -> {
      try {
        final CompoundTag crafted = new CompoundTag();
        env.save(crafted, helper.getLevel().registryAccess());
        crafted.getCompound("node").putString("address", "../../" + escape);
        env.load(crafted, helper.getLevel().registryAccess());
        env.save(new CompoundTag(), helper.getLevel().registryAccess());
        helper.assertTrue(!outsideBin.exists(), "an unmanaged drive wrote " + outsideBin);
      } finally {
        org.apache.commons.io.FileUtils.deleteQuietly(outsideBin);
      }
      helper.runAfterDelay(20, helper::succeed);
    });
  }

  private static li.cil.oc.core.impl.util.SafeThreadPool fileSavePool() {
    for (final var pool : li.cil.oc.core.impl.util.ThreadPoolFactory.safePools) {
      if ("FileSystem".equals(pool.name())) return pool;
    }
    throw new IllegalStateException("no file system save pool");
  }

  /** /reload keeps the disk saves still queued (commands are registered again, and the pools were made anew). */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void reloadKeepsQueuedSaves(final GameTestHelper helper) {
    final AtomicBoolean ran = new AtomicBoolean();
    fileSavePool().withPool(pool -> pool.schedule(() -> ran.set(true), 300, TimeUnit.MILLISECONDS));
    final var server = helper.getLevel().getServer();
    MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.RegisterCommandsEvent(new com.mojang.brigadier.CommandDispatcher<>(),
      net.minecraft.commands.Commands.CommandSelection.INTEGRATED,
      net.minecraft.commands.CommandBuildContext.configurable(server.registryAccess(), server.getWorldData().enabledFeatures())));
    helper.runAfterDelay(30, () -> {
      helper.assertTrue(ran.get(), "a save queued before the commands were registered again never ran");
      helper.succeed();
    });
  }

  /** A drive loaded again waits for its folder's save under way, whichever instance made it (it read the old files). */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void driveLoadWaitsForItsFoldersSave(final GameTestHelper helper) throws Exception {
    final String root = "oc-test-" + UUID.randomUUID();
    final var first = li.cil.oc.api.FileSystem.fromSaveDirectory(root, 64 * 1024, true);
    try {
      writeFile(first, "f", "saved");
      fileSavePool().withPool(pool -> pool.submit(() -> {
        try {
          Thread.sleep(500);
        } catch (InterruptedException ignored) {
        }
      }));
      first.save(new CompoundTag(), helper.getLevel().registryAccess());
      final var second = li.cil.oc.api.FileSystem.fromSaveDirectory(root, 64 * 1024, true);
      second.load(new CompoundTag(), helper.getLevel().registryAccess());
      helper.assertTrue(second.exists("f"), "a drive loaded while its folder was being saved does not see the file");
      second.close();
    } finally {
      first.close();
      helper.runAfterDelay(20, () -> org.apache.commons.io.FileUtils.deleteQuietly(new File(worldRoot(helper), OCSettings.savePath + root)));
    }
    helper.runAfterDelay(25, helper::succeed);
  }

  /** An unmanaged disk image that cannot be read is moved aside, not overwritten with an empty disk at the next save. */
  @GameTest(template = EMPTY, timeoutTicks = 100)
  public static void unreadableDiskImagesAreKeptAside(final GameTestHelper helper) {
    final ItemStack floppy = item(Constants.ItemName.Floppy);
    li.cil.oc.core.impl.common.item.data.DriveData.setUnmanaged(floppy, true);
    floppyInDrive(helper, floppy, (env, chunk) -> {
      final String address = env.node().address();
      final File image = new File(worldRoot(helper), OCSettings.savePath + address + ".bin");
      final File folder = image.getParentFile();
      try {
        java.nio.file.Files.write(image.toPath(), new byte[]{1, 2, 3, 4});
        final CompoundTag tag = new CompoundTag();
        env.save(tag, helper.getLevel().registryAccess());
        java.nio.file.Files.write(image.toPath(), new byte[]{1, 2, 3, 4}); // cut, as after a kill mid-write
        env.load(tag, helper.getLevel().registryAccess());
        final File[] aside = folder.listFiles((dir, name) -> name.startsWith(address + ".bin.unreadable-"));
        helper.assertTrue(aside != null && aside.length == 1, "the unreadable image was not kept aside: " + java.util.Arrays.toString(folder.list()));
        env.save(new CompoundTag(), helper.getLevel().registryAccess());
        helper.assertTrue(!new File(folder, address + ".bin.part").exists(), "a .part file was left behind");
      } catch (java.io.IOException e) {
        throw new RuntimeException(e);
      } finally {
        final File[] leftovers = folder.listFiles((dir, name) -> name.startsWith(address + ".bin"));
        if (leftovers != null) for (final File f : leftovers) f.delete();
      }
      helper.runAfterDelay(20, helper::succeed);
    });
  }
}
