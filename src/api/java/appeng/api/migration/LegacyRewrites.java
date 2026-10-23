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

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants;

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
    private static final Map<String, List<PartRewriter>> parts = new HashMap<>();
    private static final String[] CABLE_BUS_TILES = { "appliedenergistics2:blockcablebus",
            "appliedenergistics2:clientonly_tesr_cablebus" };

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

    /**
     * Where a tile keeps what {@link #spill} was handed for it, until AE2UD puts it into a chest beside the tile.
     */
    public static final String SPILL_KEY = "ae2ud:spill";

    private static final ThreadLocal<NBTTagList> spilled = new ThreadLocal<>();

    // Kept until the next chunk: the walk over a chunk's tile entities, which comes after it, has no end to hook.
    private static final ThreadLocal<NBTTagCompound> chunk = new ThreadLocal<>();

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
     * Rewriters of the parts of one item id, on every cable bus. They run in the order they were added.
     */
    public static void addPartRewriter(final String itemId, final PartRewriter rewriter) {
        if (parts.isEmpty()) {
            for (final String tile : CABLE_BUS_TILES) {
                addTileRewriter(tile, LegacyRewrites::rewriteCableBus);
            }
        }
        parts.computeIfAbsent(id(itemId), k -> new ArrayList<>()).add(rewriter);
    }

    /**
     * Something the tile or part being rewritten held that has no place in what it becomes. AE2UD puts it into a
     * chest beside the tile once the chunk has loaded, or drops it there when there is no room. Only from inside
     * a {@link TileRewriter} or a {@link PartRewriter}.
     */
    public static void spill(final NBTTagCompound stack) {
        final NBTTagList list = spilled.get();
        if (list == null) {
            log().lost("item with nowhere to go, spilled outside a tile: " + stack);
            return;
        }
        list.appendTag(stack);
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
            apply(entry, stack);
        } catch (final RuntimeException | LinkageError e) {
            current.failed("item " + original.getString("id"), original, e);
            clear(stack);
        }
    }

    /**
     * Hands a stack a rewriter has given another id, and that id's UEL shape, to that id's own renaming and
     * rewriters. Only from inside a {@link StackRewriter}.
     */
    public static void forwardStack(final NBTTagCompound stack) {
        final StackEntry entry = stacks.get(stack.getString("id"));
        if (entry == null) {
            return;
        }
        if (entry.removed) {
            log().lost("item " + stack);
            clear(stack);
            return;
        }
        apply(entry, stack);
    }

    private static void apply(final StackEntry entry, final NBTTagCompound stack) {
        if (entry.newId != null) {
            stack.setString("id", entry.newId);
        }
        for (final StackRewriter rewriter : entry.rewriters) {
            rewriter.rewrite(stack);
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
        final NBTTagList spill = new NBTTagList();
        spilled.set(spill);
        try {
            final NBTTagCompound result = apply(rewriters, tile);
            if (!spill.isEmpty()) {
                final NBTTagList kept = result.getTagList(SPILL_KEY, Constants.NBT.TAG_COMPOUND);
                for (int i = 0; i < spill.tagCount(); i++) {
                    kept.appendTag(spill.get(i));
                }
                result.setTag(SPILL_KEY, kept);
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
        } finally {
            spilled.remove();
        }
    }

    /**
     * Hands a tile a rewriter has given another id, and that id's UEL shape, to that id's rewriters. Only from
     * inside a {@link TileRewriter}.
     *
     * @return the tile's new tag
     */
    public static NBTTagCompound forwardTile(final NBTTagCompound tile) {
        final List<TileRewriter> rewriters = tiles.get(tile.getString("id"));
        return rewriters == null ? tile : apply(rewriters, tile);
    }

    private static NBTTagCompound apply(final List<TileRewriter> rewriters, final NBTTagCompound tile) {
        NBTTagCompound result = tile;
        for (final TileRewriter rewriter : rewriters) {
            result = rewriter.rewrite(result);
        }
        return result;
    }

    /**
     * Hands a part whose item a rewriter has given another id, and whose data that id's UEL shape, to that id's
     * part rewriters. Only from inside a {@link PartRewriter}.
     */
    public static void forwardPart(final NBTTagCompound item, final NBTTagCompound part) {
        final List<PartRewriter> rewriters = parts.get(item.getString("id"));
        if (rewriters == null) {
            return;
        }
        for (final PartRewriter rewriter : rewriters) {
            rewriter.rewrite(item, part);
        }
    }

    /**
     * The block a tile being rewritten stands in, a renamed one already under its new name. Only from inside a
     * {@link TileRewriter}; null outside one, or for a tile that came from somewhere other than a chunk.
     */
    @Nullable
    public static Block blockAt(final NBTTagCompound tile) {
        final NBTTagCompound level = chunk.get();
        if (level == null) {
            return null;
        }
        final int x = tile.getInteger("x");
        final int y = tile.getInteger("y");
        final int z = tile.getInteger("z");
        if (x >> 4 != level.getInteger("xPos") || z >> 4 != level.getInteger("zPos") || y < 0 || y > 255) {
            return null;
        }
        final NBTTagList sections = level.getTagList("Sections", Constants.NBT.TAG_COMPOUND);
        for (int s = 0; s < sections.tagCount(); s++) {
            final NBTTagCompound section = sections.getCompoundTagAt(s);
            if (section.getByte("Y") != y >> 4) {
                continue;
            }
            final byte[] blocks = section.getByteArray("Blocks");
            if (blocks.length != 4096) {
                return null;
            }
            final int i = (y & 15) << 8 | (z & 15) << 4 | x & 15;
            int number = blocks[i] & 0xFF;
            if (section.hasKey("Add", Constants.NBT.TAG_BYTE_ARRAY)) {
                number |= (section.getByteArray("Add")[i >> 1] >> ((i & 1) * 4) & 0xF) << 8;
            }
            return Block.getBlockById(number);
        }
        return Blocks.AIR;
    }

    /**
     * AE2UD's own: the chunk, its {@code Level}, whose tile entities are rewritten next on this thread. Its blocks
     * have been renumbered already.
     */
    public static void enterChunk(final NBTTagCompound level) {
        chunk.set(level);
    }

    private static NBTTagCompound rewriteCableBus(final NBTTagCompound bus) {
        for (int side = 0; side < 7; side++) {
            final NBTTagCompound item = bus.getCompoundTag("def:" + side);
            final List<PartRewriter> rewriters = parts.get(item.getString("id"));
            if (rewriters == null) {
                continue;
            }
            final NBTTagCompound part = bus.getCompoundTag("extra:" + side);
            final NBTTagCompound originalItem = item.copy();
            final NBTTagCompound originalPart = part.copy();
            try {
                for (final PartRewriter rewriter : rewriters) {
                    rewriter.rewrite(item, part);
                }
                bus.setTag("extra:" + side, part);
            } catch (final RuntimeException | LinkageError e) {
                final NBTTagCompound original = new NBTTagCompound();
                original.setTag("def", originalItem);
                original.setTag("extra", originalPart);
                log().failed("part " + originalItem.getString("id") + "@" + originalItem.getShort("Damage") + " at "
                        + bus.getInteger("x") + " " + bus.getInteger("y") + " " + bus.getInteger("z"), original, e);
                bus.removeTag("def:" + side);
                bus.removeTag("extra:" + side);
            }
        }
        return bus;
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
