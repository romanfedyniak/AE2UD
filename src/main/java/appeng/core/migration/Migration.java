/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.core.migration;

import java.util.Map;

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.datafix.FixTypes;
import net.minecraft.util.datafix.IFixableData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.ModFixs;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import appeng.api.migration.LegacyRewrites;
import appeng.api.migration.MigrationLog;
import appeng.core.AppEng;
import appeng.core.migration.uel.UelMigration;

/**
 * Applies {@link LegacyRewrites} to a world from UEL: renamed blocks in each chunk's block data and tile entities as
 * the chunk loads, stacks as they are read, by a mixin on {@code ItemStack}.
 * <p>
 * Forge is never asked to remap an id: a block remapped onto one that exists frees the old number, and a block new
 * to the world takes it, so every renamed block in the world would load as that new one. The old ids are ignored
 * instead, which keeps their numbers reserved, and the blocks are renumbered here.
 */
public final class Migration {

    /**
     * The data version AE2UD writes into every chunk it saves. A chunk saved before it is rewritten once.
     */
    private static final int DATA_VERSION = 1;

    private static final String REWRITTEN = "ae2ud:rewritten";

    /**
     * The world's own number of each renamed block, to the name of the block it becomes.
     */
    private static final Int2ObjectMap<String> renamedBlocks = new Int2ObjectOpenHashMap<>();

    /**
     * The world's own number of each renamed block that takes one metadata, to that metadata.
     */
    private static final Int2IntMap renamedMetas = new Int2IntOpenHashMap();

    @Nullable
    private static volatile Renames blockRenames;

    private Migration() {
    }

    public static void preInit() {
        final ModFixs fixes = FMLCommonHandler.instance().getDataFixer().init(AppEng.MOD_ID, DATA_VERSION);
        fixes.registerFix(FixTypes.CHUNK, new IFixableData() {
            @Override
            public int getFixVersion() {
                return DATA_VERSION;
            }

            @Override
            public NBTTagCompound fixTagCompound(final NBTTagCompound chunk) {
                renumberBlocks(chunk);
                if (LegacyRewrites.isLegacyWorld()) {
                    LegacyRewrites.enterChunk(chunk.getCompoundTag("Level"));
                    chunk.getCompoundTag("Level").setBoolean(REWRITTEN, true);
                }
                return chunk;
            }
        });
        fixes.registerFix(FixTypes.BLOCK_ENTITY, new IFixableData() {
            @Override
            public int getFixVersion() {
                return DATA_VERSION;
            }

            @Override
            public NBTTagCompound fixTagCompound(final NBTTagCompound tile) {
                return LegacyRewrites.rewriteTile(tile);
            }
        });
        MinecraftForge.EVENT_BUS.register(new Migration());
        MinecraftForge.EVENT_BUS.register(new Spills());
        UelMigration.register();
    }

    static void onWorldChange() {
        synchronized (renamedBlocks) {
            renamedBlocks.clear();
            renamedMetas.clear();
            blockRenames = null;
        }
    }

    @SubscribeEvent
    public void onMissingBlocks(final RegistryEvent.MissingMappings<Block> event) {
        if (!LegacyRewrites.isLegacyWorld()) {
            return;
        }
        final MigrationLog log = LegacyRewrites.log();
        for (final RegistryEvent.MissingMappings.Mapping<Block> mapping : event.getAllMappings()) {
            final String old = mapping.key.toString();
            final String target = LegacyRewrites.getBlockRemaps().get(old);
            if (target != null) {
                if (ForgeRegistries.BLOCKS.containsKey(new ResourceLocation(target))) {
                    synchronized (renamedBlocks) {
                        renamedBlocks.put(mapping.id, target);
                        final int meta = LegacyRewrites.getBlockMeta(old);
                        if (meta >= 0) {
                            renamedMetas.put(mapping.id, meta);
                        }
                        blockRenames = null;
                    }
                } else {
                    log.lost("block " + old + ": " + target + ", which it becomes, is not registered; every one is gone");
                }
                mapping.ignore();
            } else if (LegacyRewrites.getRemoved().contains(old)) {
                log.lost("block " + old + " has no equivalent; every one is gone");
                mapping.ignore();
            }
        }
    }

    /**
     * A chunk the game does not change is not saved, and would be rewritten again on every load, spilling what
     * its tiles held each time; one that was rewritten is saved once, with the data version that marks it done.
     */
    @SubscribeEvent
    public void onChunkLoad(final ChunkDataEvent.Load event) {
        final NBTTagCompound level = event.getData().getCompoundTag("Level");
        if (level.getBoolean(REWRITTEN)) {
            level.removeTag(REWRITTEN);
            event.getChunk().markDirty();
        }
    }

    /**
     * Stacks of a renamed or rewritten item are rewritten by name as they are read, so its number does not matter.
     */
    @SubscribeEvent
    public void onMissingItems(final RegistryEvent.MissingMappings<Item> event) {
        if (!LegacyRewrites.isLegacyWorld()) {
            return;
        }
        for (final RegistryEvent.MissingMappings.Mapping<Item> mapping : event.getAllMappings()) {
            final String old = mapping.key.toString();
            if (LegacyRewrites.getRemoved().contains(old)) {
                LegacyRewrites.log().lost("item " + old + " has no equivalent; every one is gone");
                mapping.ignore();
            } else if (LegacyRewrites.getRewrittenItems().contains(old)) {
                mapping.ignore();
            }
        }
    }

    /**
     * A section keeps its blocks one of two ways. Saved plainly, a block is its number in {@code Blocks}, with
     * the high bits in {@code Add} and, past 4095, in {@code Add2}, and its metadata in {@code Data}. Saved by
     * RoughlyEnoughIDs, it has a {@code Palette} of block states, number and metadata, and the other arrays
     * only point into it; then only the palette changes.
     */
    private static void renumberBlocks(final NBTTagCompound chunk) {
        if (!LegacyRewrites.isLegacyWorld()) {
            return;
        }
        final Renames renames = renames();
        if (renames.numbers.isEmpty()) {
            return;
        }
        final NBTTagList sections = chunk.getCompoundTag("Level").getTagList("Sections", Constants.NBT.TAG_COMPOUND);
        for (int s = 0; s < sections.tagCount(); s++) {
            final NBTTagCompound section = sections.getCompoundTagAt(s);
            if (section.hasKey("Palette", Constants.NBT.TAG_INT_ARRAY)) {
                renumberPalette(section, renames);
            } else {
                renumberPlain(section, renames);
            }
        }
    }

    private static void renumberPalette(final NBTTagCompound section, final Renames renames) {
        final int[] palette = section.getIntArray("Palette").clone();
        for (int p = 0; p < palette.length; p++) {
            final int number = palette[p] >>> 4;
            if (renames.numbers.containsKey(number)) {
                final int meta = renames.metas.containsKey(number) ? renames.metas.get(number) : palette[p] & 0xF;
                palette[p] = renames.numbers.get(number) << 4 | meta;
            }
        }
        section.setIntArray("Palette", palette);
    }

    private static void renumberPlain(final NBTTagCompound section, final Renames renames) {
        final byte[] blocks = section.getByteArray("Blocks");
        final byte[] data = section.getByteArray("Data");
        if (blocks.length != 4096 || data.length != 2048) {
            return;
        }
        byte[] add = section.hasKey("Add", Constants.NBT.TAG_BYTE_ARRAY) ? section.getByteArray("Add") : null;
        byte[] add2 = section.hasKey("Add2", Constants.NBT.TAG_BYTE_ARRAY) ? section.getByteArray("Add2") : null;
        for (int i = 0; i < blocks.length; i++) {
            final int number = nibble(add2, i) << 12 | nibble(add, i) << 8 | blocks[i] & 0xFF;
            if (!renames.numbers.containsKey(number)) {
                continue;
            }
            final int renumbered = renames.numbers.get(number);
            blocks[i] = (byte) renumbered;
            if (renames.metas.containsKey(number)) {
                setNibble(data, i, renames.metas.get(number));
            }
            if (add == null && renumbered > 0xFF) {
                add = new byte[2048];
                section.setByteArray("Add", add);
            }
            if (add != null) {
                setNibble(add, i, renumbered >> 8);
            }
            if (add2 == null && renumbered > 0xFFF) {
                add2 = new byte[2048];
                section.setByteArray("Add2", add2);
            }
            if (add2 != null) {
                setNibble(add2, i, renumbered >> 12);
            }
        }
    }

    private static int nibble(final byte[] array, final int i) {
        return array == null ? 0 : array[i >> 1] >> ((i & 1) * 4) & 0xF;
    }

    private static void setNibble(final byte[] array, final int i, final int value) {
        final int shift = (i & 1) * 4;
        array[i >> 1] = (byte) (array[i >> 1] & ~(0xF << shift) | (value & 0xF) << shift);
    }

    /**
     * Built on the first chunk: the blocks' own numbers are final only once the world's registries have loaded.
     * <p>
     * A block missing on the first load is reported as missing, but Forge saves it as a dummy and on every load
     * after puts an air block of that name back under its old number, without asking again. A chunk the game
     * never changed is not saved again either, so it is rewritten on every load, and its old blocks are found
     * by those dummies.
     */
    private static Renames renames() {
        Renames renames = blockRenames;
        if (renames != null) {
            return renames;
        }
        synchronized (renamedBlocks) {
            final Int2IntMap numbers = new Int2IntOpenHashMap();
            final Int2IntMap metas = new Int2IntOpenHashMap(renamedMetas);
            for (final Int2ObjectMap.Entry<String> entry : renamedBlocks.int2ObjectEntrySet()) {
                final Block target = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(entry.getValue()));
                numbers.put(entry.getIntKey(), Block.getIdFromBlock(target));
            }
            for (final Map.Entry<String, String> remap : LegacyRewrites.getBlockRemaps().entrySet()) {
                final ResourceLocation old = new ResourceLocation(remap.getKey());
                final ResourceLocation target = new ResourceLocation(remap.getValue());
                if (!ForgeRegistries.BLOCKS.containsKey(old) || !ForgeRegistries.BLOCKS.containsKey(target)) {
                    continue;
                }
                final Block dummy = ForgeRegistries.BLOCKS.getValue(old);
                // Only a stand-in: a mod that is still installed keeps its blocks.
                if (!(dummy instanceof BlockAir) || dummy == Blocks.AIR) {
                    continue;
                }
                final int number = Block.getIdFromBlock(dummy);
                numbers.putIfAbsent(number, Block.getIdFromBlock(ForgeRegistries.BLOCKS.getValue(target)));
                final int meta = LegacyRewrites.getBlockMeta(remap.getKey());
                if (meta >= 0) {
                    metas.putIfAbsent(number, meta);
                }
            }
            renames = new Renames(numbers, metas);
            blockRenames = renames;
            return renames;
        }
    }

    /**
     * The world's number of each renamed block, to the number of the block it becomes and, when it becomes one
     * metadata of it, to that metadata.
     */
    private static final class Renames {
        private final Int2IntMap numbers;
        private final Int2IntMap metas;

        private Renames(final Int2IntMap numbers, final Int2IntMap metas) {
            this.numbers = numbers;
            this.metas = metas;
        }
    }
}
