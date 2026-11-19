/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.me.storage;


import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.zip.GZIPOutputStream;

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.objects.Object2LongMap;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.tile.AEBaseTile;


/**
 * Where storage cells keep their contents: a file per cell under the world's {@code AE2/cells}, named by the id
 * the cell's item carries. The item itself keeps a summary of a few hundred bytes however much the cell holds, so
 * no amount of contents can push a chunk or a player past what the game is able to save.
 * <p/>
 * Contents stay in memory while they are used and for {@link #IDLE_MILLIS} after, and then only weakly: a drive
 * still holding them is handed back the same object, never a second copy read from disk that the two would then
 * overwrite each other with.
 * <p/>
 * Writing follows the world's own saves. What changed is copied on the server thread when a world saves, a
 * chunk with AE machines in it saves (as it does when it unloads) or a player is written, and compressed and
 * written on a thread of its own; {@code /save-off} stops it along with everything else.
 */
public final class CellContentsStore {

    static final long IDLE_MILLIS = TimeUnit.MINUTES.toMillis(5);
    private static final long STOP_WAIT_MILLIS = TimeUnit.MINUTES.toMillis(1);

    private static final String FORMAT_TAG = "format";
    private static final int FORMAT = 1;
    private static final String CONTENTS_TAG = "contents";

    /** Queued in place of a snapshot for contents that became empty. Compared by identity. */
    private static final NBTTagCompound DELETED = new NBTTagCompound();

    @Nullable
    private static CellContentsStore current;

    private final File directory;
    private final LongSupplier clock;
    // Cells change wherever their grid ticks, which on a server that ticks regions at once is any of its threads
    private final Map<UUID, CellContents> active = new HashMap<>();
    private final Map<UUID, WeakReference<CellContents>> idle = new HashMap<>();
    // Handed to the writer and not on disk yet. A load reads these first, or it would read the file being replaced.
    private final Map<UUID, NBTTagCompound> pending = new ConcurrentHashMap<>();
    // Writes the disk refused. Their snapshot stays pending, and the next save tries it again.
    private final Set<UUID> failed = ConcurrentHashMap.newKeySet();
    // Cells with a write waiting for the writer, which will take whatever snapshot is pending by then.
    private final Set<UUID> queued = ConcurrentHashMap.newKeySet();
    // Files that could not be read nor set aside. Never written over: what they hold is not what this cell shows.
    private final Set<UUID> unreadable = ConcurrentHashMap.newKeySet();
    private final ExecutorService writer;
    /** A chunk holding AE machines, or a player, was saved this tick. */
    private volatile boolean saveRequested;

    CellContentsStore(final File directory, final LongSupplier clock) {
        this.directory = directory;
        this.clock = clock;
        this.writer = Executors.newSingleThreadExecutor(task -> {
            final Thread thread = new Thread(task, "AE2 Cell Storage");
            thread.setDaemon(true);
            return thread;
        });
    }

    public static CellContentsStore start(final File directory) {
        stop();
        current = new CellContentsStore(directory, System::currentTimeMillis);
        return current;
    }

    /**
     * Waits for what was already queued to reach the disk. Nothing new is copied: the world's last save did that,
     * or was told not to.
     */
    public static void stop() {
        final CellContentsStore stopped = current;
        current = null;
        if (stopped != null) {
            stopped.close(STOP_WAIT_MILLIS);
        }
    }

    /**
     * @return the store, or null off the server thread - the client, and any other thread, only ever sees a
     *         cell's summary.
     */
    @Nullable
    public static CellContentsStore current() {
        final CellContentsStore store = current;
        return store != null && FMLCommonHandler.instance().getEffectiveSide().isServer() ? store : null;
    }

    static void setCurrent(@Nullable final CellContentsStore store) {
        current = store;
    }

    /** The contents with this id, read from disk if they are not in memory, and empty if there are none. */
    public synchronized CellContents getOrLoad(final UUID id) {
        final CellContents found = this.find(id);
        if (found != null) {
            return found;
        }

        final CellContents loaded = new CellContents(id, this);
        this.read(id, loaded);
        this.activate(loaded);
        return loaded;
    }

    /** Like {@link #getOrLoad}, but keeps nothing for an id that has no contents saved. */
    @Nullable
    public synchronized CellContents getIfSaved(final UUID id) {
        final CellContents found = this.find(id);
        if (found != null) {
            return found;
        }

        final NBTTagCompound queued = this.pending.get(id);
        if (queued == DELETED || queued == null && !this.fileFor(id).isFile()) {
            return null;
        }
        return this.getOrLoad(id);
    }

    /** Gives detached contents an id of their own, so they are saved from now on. */
    public synchronized void attach(final CellContents contents) {
        contents.attach(UUID.randomUUID(), this);
        this.activate(contents);
    }

    synchronized void changed(final CellContents contents) {
        contents.dirty = true;
        contents.lastAccess = this.clock.getAsLong();
        if (this.active.put(contents.getId(), contents) == null) {
            this.idle.remove(contents.getId());
        }
    }

    /** Queues everything that changed, and lets go of what has not been used for a while. */
    public synchronized void save() {
        final long now = this.clock.getAsLong();

        for (final Iterator<UUID> retry = this.failed.iterator(); retry.hasNext();) {
            final UUID id = retry.next();
            retry.remove();
            final NBTTagCompound tag = this.pending.get(id);
            if (tag != null) {
                this.queue(id, tag);
            }
        }

        final Iterator<CellContents> it = this.active.values().iterator();
        while (it.hasNext()) {
            final CellContents contents = it.next();
            if (contents.dirty) {
                contents.dirty = false;
                this.queue(contents.getId(), contents.isEmpty() ? DELETED : write(contents));
            } else if (now - contents.lastAccess >= IDLE_MILLIS) {
                this.idle.put(contents.getId(), new WeakReference<>(contents));
                it.remove();
            }
        }

        this.idle.values().removeIf(ref -> ref.get() == null);
    }

    /**
     * Every world's save, not only the overworld's: a dimension that unloads saves its chunks and then itself, and
     * the cells in its drives have to go with them.
     */
    @SubscribeEvent
    public void onWorldSave(final WorldEvent.Save event) {
        if (!event.getWorld().isRemote) {
            this.save();
        }
    }

    /**
     * A chunk is saved on its own when it unloads, drives and the chests beside them with it; the cells in those
     * drives are taken at the end of the same tick rather than at the next world save, which a crash may not
     * reach.
     */
    @SubscribeEvent
    public void onChunkSave(final ChunkDataEvent.Save event) {
        if (event.getWorld().isRemote || this.saveRequested) {
            return;
        }
        for (final TileEntity tile : event.getChunk().getTileEntityMap().values()) {
            if (tile instanceof AEBaseTile) {
                this.saveRequested = true;
                return;
            }
        }
    }

    /** A player's inventory, portable cells and what they took out of a network included, is written at once. */
    @SubscribeEvent
    public void onPlayerSave(final PlayerEvent.SaveToFile event) {
        if (event.getEntityPlayer().world instanceof WorldServer world && !world.disableLevelSaving) {
            this.saveRequested = true;
        }
    }

    @SubscribeEvent
    public void onServerTick(final TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && this.saveRequested) {
            this.saveRequested = false;
            this.save();
        }
    }

    synchronized int countActive() {
        return this.active.size();
    }

    File fileFor(final UUID id) {
        final String name = id.toString();
        return new File(new File(this.directory, name.substring(0, 2)), name + ".dat");
    }

    /** Blocks until everything queued so far has been written. */
    void awaitWrites() throws InterruptedException {
        final CountDownLatch done = new CountDownLatch(1);
        this.writer.execute(done::countDown);
        done.await();
    }

    /**
     * Waits for the disk, but not past the deadline: a server that does not stop is killed by its host, which loses
     * more than the few cells still unwritten. The writer is a daemon thread; what it has not written when the game
     * exits is lost, and named in the log.
     *
     * @return the cells not on disk when the wait ended
     */
    Set<UUID> close(final long timeoutMillis) {
        this.writer.shutdown();
        try {
            if (!this.writer.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS)) {
                final Set<UUID> unwritten = new HashSet<>(this.pending.keySet());
                AELog.error("Storage cells were still being written %d s after the server stopped; %d not saved: %s",
                        TimeUnit.MILLISECONDS.toSeconds(timeoutMillis), unwritten.size(), unwritten);
                return unwritten;
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return new HashSet<>(this.pending.keySet());
    }

    @Nullable
    private CellContents find(final UUID id) {
        CellContents contents = this.active.get(id);
        if (contents == null) {
            final WeakReference<CellContents> ref = this.idle.remove(id);
            contents = ref == null ? null : ref.get();
            if (contents == null) {
                return null;
            }
            this.active.put(id, contents);
        }

        contents.lastAccess = this.clock.getAsLong();
        return contents;
    }

    private void activate(final CellContents contents) {
        contents.lastAccess = this.clock.getAsLong();
        this.active.put(contents.getId(), contents);
    }

    /**
     * Writes the newest snapshot of a cell, once: a cell that changes at every save while the disk is slow would
     * otherwise have every one of its snapshots waiting in line, each written over the last.
     */
    private void queue(final UUID id, final NBTTagCompound tag) {
        if (this.unreadable.contains(id)) {
            return;
        }
        this.pending.put(id, tag);
        if (!this.queued.add(id)) {
            return; // a write of this cell has not started yet, and takes the snapshot just put
        }
        this.writer.execute(() -> {
            this.queued.remove(id);
            final NBTTagCompound latest = this.pending.get(id);
            if (latest == null) {
                return;
            }
            try {
                if (latest == DELETED) {
                    Files.deleteIfExists(this.fileFor(id).toPath());
                } else {
                    this.writeFile(id, latest);
                }
                // Only if nothing newer was queued meanwhile.
                this.pending.remove(id, latest);
            } catch (final IOException | RuntimeException e) {
                AELog.error(e, "Could not save storage cell " + id + "; trying again on the next save");
                this.failed.add(id);
            }
        });
    }

    private void writeFile(final UUID id, final NBTTagCompound tag) throws IOException {
        final File file = this.fileFor(id);
        final File parent = file.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Could not create " + parent);
        }

        // Written beside and moved over, so a crash mid-write leaves the old file whole.
        final File temp = new File(parent, file.getName() + ".tmp");
        try {
            try (FileOutputStream out = new FileOutputStream(temp)) {
                // Not writeCompressed: it closes the file, and the file has to be synced first.
                final GZIPOutputStream zip = new GZIPOutputStream(out);
                final DataOutputStream data = new DataOutputStream(new BufferedOutputStream(zip));
                CompressedStreamTools.write(tag, data);
                data.flush();
                zip.finish();
                // On disk before the rename, or a power cut can leave the new name over unwritten blocks.
                out.getFD().sync();
                data.close();
            }
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (final AtomicMoveNotSupportedException e) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp.toPath());
        }
    }

    private void read(final UUID id, final CellContents into) {
        NBTTagCompound tag = this.pending.get(id);
        if (tag == DELETED) {
            return;
        }

        if (tag == null) {
            final File file = this.fileFor(id);
            if (!file.isFile()) {
                return;
            }

            try (InputStream in = new FileInputStream(file)) {
                tag = CompressedStreamTools.readCompressed(in);
            } catch (final IOException | RuntimeException e) {
                // Kept for whoever wants to look at it; the cell reads as empty.
                final File kept = new File(file.getPath() + ".corrupt");
                if (file.renameTo(kept)) {
                    AELog.error(e, "Storage cell " + id + " could not be read; its file was renamed to .corrupt");
                    return;
                }
                try {
                    Files.copy(file.toPath(), kept.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    AELog.error(e, "Storage cell " + id + " could not be read; its file was copied to .corrupt");
                } catch (final IOException | RuntimeException notCopied) {
                    // Held open by another program, which on Windows also keeps it from being renamed or read.
                    // Left as it is, and not written over by the empty cell this reads as.
                    this.unreadable.add(id);
                    AELog.error(e, "Storage cell " + id + " could not be read, renamed or copied; it is left as it "
                            + "is and will not be saved until the server starts again");
                }
                return;
            }
        }

        if (readList(tag.getTagList(CONTENTS_TAG, Constants.NBT.TAG_COMPOUND), into, id.toString())) {
            // Something in it could not be loaded; write it again without that.
            into.dirty = true;
        }
    }

    private static NBTTagCompound write(final CellContents contents) {
        final NBTTagList list = new NBTTagList();
        for (final Object2LongMap.Entry<AEKey> entry : contents.amounts().object2LongEntrySet()) {
            final NBTTagCompound entryTag = new NBTTagCompound();
            GenericStack.writeTag(entryTag, new GenericStack(entry.getKey(), entry.getLongValue()));
            list.appendTag(entryTag);
        }

        final NBTTagCompound root = new NBTTagCompound();
        root.setInteger(FORMAT_TAG, FORMAT);
        root.setTag(CONTENTS_TAG, list);
        return root;
    }

    /**
     * Reads a list of {@link GenericStack} tags, the format both a file and an old cell's own NBT use.
     *
     * @return true if an entry had to be dropped
     */
    static boolean readList(final NBTTagList list, final CellContents into, final String cellName) {
        boolean dropped = false;

        for (int idx = 0; idx < list.tagCount(); idx++) {
            final GenericStack stack;
            try {
                stack = GenericStack.readTag(list.getCompoundTagAt(idx));
            } catch (final Throwable ex) {
                if (AEConfig.instance() != null && AEConfig.instance().isRemoveCrashingItemsOnLoad()) {
                    AELog.warn(ex, "Removing an item from storage cell " + cellName + " because loading it crashed.");
                    dropped = true;
                    continue;
                }
                throw ex;
            }

            if (stack == null) {
                AELog.warn("Removing an item from storage cell " + cellName + " because its type could not be found.");
                dropped = true;
                continue;
            }

            if (stack.amount() > 0) {
                into.add(stack.what(), stack.amount());
            }
        }

        return dropped;
    }
}
