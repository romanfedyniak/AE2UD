/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2UD contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package appeng.api.migration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

/**
 * What a world saved by AE2 UEL and its addons turns into on AE2UD. AE2UD, its addons and the migrator register
 * here, during pre-init or init; AE2UD applies it.
 * <p>
 * All of it is off unless the world was last saved by UEL, which AE2UD decides once per world and keeps in
 * {@code AE2/migration.dat}. Ids are registry names, {@code modid:name}.
 */
public final class LegacyRewrites {

    private static final Map<String, String> blockRemaps = new HashMap<>();
    private static final Set<String> removed = new HashSet<>();
    private static final Map<String, StackEntry> stacks = new HashMap<>();
    private static final Map<String, List<TileRewriter>> tiles = new HashMap<>();

    private static final MigrationLog SILENT = new MigrationLog() {
        @Override
        public void note(final String message) {
        }

        @Override
        public void lost(final String message) {
        }

        @Override
        public void failed(final String what, final NBTTagCompound original, final Throwable error) {
        }
    };

    @Nullable
    private static volatile MigrationLog log;

    private LegacyRewrites() {
    }

    /**
     * A block that was renamed, and its item with it. Its metadata and tile entity are kept as they are.
     */
    public static void remapBlock(final String oldId, final String newId) {
        blockRemaps.put(id(oldId), id(newId));
        remapItem(oldId, newId);
    }

    /**
     * An item that was renamed. Saved stacks get the new id; their metadata and tag are kept, unless a
     * {@link StackRewriter} registered for the old id changes them.
     */
    public static void remapItem(final String oldId, final String newId) {
        entry(oldId).newId = id(newId);
    }

    /**
     * A block or item with no equivalent. The block becomes air, and every stack of it that is read is logged and
     * dropped. A block that held something should be remapped to a chest instead, with a {@link TileRewriter}.
     */
    public static void remove(final String oldId) {
        removed.add(id(oldId));
        entry(oldId).removed = true;
    }

    /**
     * Rewriters of one id run in the order they were added, after the stack has taken its new id, if it has one.
     */
    public static void addStackRewriter(final String itemId, final StackRewriter rewriter) {
        entry(itemId).rewriters.add(rewriter);
    }

    public static void addTileRewriter(final String tileId, final TileRewriter rewriter) {
        tiles.computeIfAbsent(id(tileId), k -> new ArrayList<>()).add(rewriter);
    }

    /**
     * True while a world that came from UEL is running.
     */
    public static boolean isLegacyWorld() {
        return log != null;
    }

    /**
     * The world's migration log; one that writes nothing when the world did not come from UEL.
     */
    public static MigrationLog log() {
        final MigrationLog current = log;
        return current == null ? SILENT : current;
    }

    /**
     * AE2UD's own: called on every {@code ItemStack} read from NBT.
     */
    public static void rewriteStack(final NBTTagCompound stack) {
        final MigrationLog current = log;
        if (current == null) {
            return;
        }
        final StackEntry entry = stacks.get(stack.getString("id"));
        if (entry == null) {
            return;
        }

        if (entry.removed) {
            current.lost("item " + stack);
            clear(stack);
            return;
        }
        if (entry.rewriters.isEmpty()) {
            stack.setString("id", entry.newId);
            return;
        }

        final NBTTagCompound original = stack.copy();
        try {
            if (entry.newId != null) {
                stack.setString("id", entry.newId);
            }
            for (final StackRewriter rewriter : entry.rewriters) {
                rewriter.rewrite(stack);
            }
        } catch (final RuntimeException | LinkageError e) {
            current.failed("item " + original.getString("id"), original, e);
            clear(stack);
        }
    }

    /**
     * AE2UD's own: called on every tile entity of a chunk that has not been rewritten yet.
     */
    public static NBTTagCompound rewriteTile(final NBTTagCompound tile) {
        final MigrationLog current = log;
        if (current == null) {
            return tile;
        }
        final List<TileRewriter> rewriters = tiles.get(tile.getString("id"));
        if (rewriters == null) {
            return tile;
        }

        final NBTTagCompound original = tile.copy();
        try {
            NBTTagCompound result = tile;
            for (final TileRewriter rewriter : rewriters) {
                result = rewriter.rewrite(result);
            }
            return result;
        } catch (final RuntimeException | LinkageError e) {
            current.failed("tile " + original.getString("id") + " at " + original.getInteger("x") + " "
                    + original.getInteger("y") + " " + original.getInteger("z"), original, e);
            // What is left loads as a new, empty tile of the same block.
            final NBTTagCompound bare = new NBTTagCompound();
            for (final String key : new String[] { "id", "x", "y", "z" }) {
                if (original.hasKey(key)) {
                    bare.setTag(key, original.getTag(key).copy());
                }
            }
            return bare;
        }
    }

    public static Map<String, String> getBlockRemaps() {
        return Collections.unmodifiableMap(blockRemaps);
    }

    public static Set<String> getRemoved() {
        return Collections.unmodifiableSet(removed);
    }

    /**
     * Item ids with a rewriter or a new id: what their saved stacks become is known, so the registry need not ask.
     */
    public static Set<String> getRewrittenItems() {
        return Collections.unmodifiableSet(stacks.keySet());
    }

    /**
     * AE2UD's own: a world is starting, and it came from UEL when the log is not null.
     */
    public static void startWorld(@Nullable final MigrationLog worldLog) {
        log = worldLog;
    }

    public static void stopWorld() {
        log = null;
    }

    private static StackEntry entry(final String itemId) {
        return stacks.computeIfAbsent(id(itemId), k -> new StackEntry());
    }

    private static String id(final String id) {
        return new ResourceLocation(id).toString();
    }

    private static void clear(final NBTTagCompound stack) {
        for (final String key : new ArrayList<>(stack.getKeySet())) {
            stack.removeTag(key);
        }
        stack.setString("id", "minecraft:air");
        stack.setByte("Count", (byte) 0);
    }

    private static final class StackEntry {
        @Nullable
        private String newId;
        private boolean removed;
        private final List<StackRewriter> rewriters = new ArrayList<>(1);
    }
}
