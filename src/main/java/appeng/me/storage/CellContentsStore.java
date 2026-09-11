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


import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.objects.Object2LongMap;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.core.AEConfig;
import appeng.core.AELog;


/**
 * Where storage cells keep their contents: a file per cell under the world's {@code AE2/cells}, named by the id
 * the cell's item carries. The item itself keeps a summary of a few hundred bytes however much the cell holds, so
 * no amount of contents can push a chunk or a player past what the game is able to save.
 * <p/>
 * Contents stay in memory while they are used and for {@link #IDLE_MILLIS} after, and then only weakly: a drive
 * still holding them is handed back the same object, never a second copy read from disk that the two would then
 * overwrite each other with.
 * <p/>
 * Writing follows the world's own saves. What changed is copied on the server thread when the overworld saves,
 * and compressed and written on a thread of its own; {@code /save-off} stops it along with everything else.
 */
public final class CellContentsStore {

    static final long IDLE_MILLIS = TimeUnit.MINUTES.toMillis(5);

    private static final String FORMAT_TAG = "format";
    private static final int FORMAT = 1;
    private static final String CONTENTS_TAG = "contents";

    /** Queued in place of a snapshot for contents that became empty. Compared by identity. */
    private static final NBTTagCompound DELETED = new NBTTagCompound();

    @Nullable
    private static CellContentsStore current;

    private final File directory;
    private final LongSupplier clock;
    private final Map<UUID, CellContents> active = new HashMap<>();
    private final Map<UUID, WeakReference<CellContents>> idle = new HashMap<>();
    // Handed to the writer and not on disk yet. A load reads these first, or it would read the file being replaced.
    private final Map<UUID, NBTTagCompound> pending = new ConcurrentHashMap<>();
    private final ExecutorService writer;

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
            stopped.close();
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
    public CellContents getOrLoad(final UUID id) {
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
    public CellContents getIfSaved(final UUID id) {
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
    public void attach(final CellContents contents) {
        contents.attach(UUID.randomUUID(), this);
        this.activate(contents);
    }

    void changed(final CellContents contents) {
        contents.dirty = true;
        contents.lastAccess = this.clock.getAsLong();
        if (this.active.put(contents.getId(), contents) == null) {
            this.idle.remove(contents.getId());
        }
    }

    /** Queues everything that changed, and lets go of what has not been used for a while. */
    public void save() {
        final long now = this.clock.getAsLong();

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

    @SubscribeEvent
    public void onWorldSave(final WorldEvent.Save event) {
        if (!event.getWorld().isRemote && event.getWorld().provider.getDimension() == 0) {
            this.save();
        }
    }

    int countActive() {
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

    private void close() {
        this.writer.shutdown();
        try {
            if (!this.writer.awaitTermination(1, TimeUnit.MINUTES)) {
                AELog.warn("Storage cells were still being written a minute after the server stopped");
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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

    private void queue(final UUID id, final NBTTagCompound tag) {
        this.pending.put(id, tag);
        this.writer.execute(() -> {
            try {
                if (tag == DELETED) {
                    Files.deleteIfExists(this.fileFor(id).toPath());
                } else {
                    this.writeFile(id, tag);
                }
            } catch (final IOException | RuntimeException e) {
                AELog.error(e, "Could not save storage cell " + id);
            } finally {
                // Only if nothing newer was queued meanwhile.
                this.pending.remove(id, tag);
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
        try (OutputStream out = new FileOutputStream(temp)) {
            CompressedStreamTools.writeCompressed(tag, out);
        }
        try {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (final AtomicMoveNotSupportedException e) {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
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
                AELog.error(e, "Storage cell " + id + " could not be read; its file was renamed to .corrupt");
                if (!file.renameTo(new File(file.getPath() + ".corrupt"))) {
                    AELog.warn("Could not rename %s", file);
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
