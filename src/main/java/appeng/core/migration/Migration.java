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

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import net.minecraft.block.Block;
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

    /**
     * The world's own number of each renamed block, to the name of the block it becomes.
     */
    private static final Int2ObjectMap<String> renamedBlocks = new Int2ObjectOpenHashMap<>();

    @Nullable
    private static volatile Int2IntMap blockNumbers;

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
            blockNumbers = null;
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
                        blockNumbers = null;
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
     * Sections keep a block as 12 bits, the low 8 in {@code Blocks} and the high 4 in {@code Add}; its metadata,
     * in {@code Data}, stays.
     */
    private static void renumberBlocks(final NBTTagCompound chunk) {
        if (!LegacyRewrites.isLegacyWorld()) {
            return;
        }
        final Int2IntMap numbers = blockNumbers();
        if (numbers.isEmpty()) {
            return;
        }
        final NBTTagList sections = chunk.getCompoundTag("Level").getTagList("Sections", Constants.NBT.TAG_COMPOUND);
        for (int s = 0; s < sections.tagCount(); s++) {
            final NBTTagCompound section = sections.getCompoundTagAt(s);
            final byte[] blocks = section.getByteArray("Blocks");
            if (blocks.length != 4096) {
                continue;
            }
            byte[] add = section.hasKey("Add", Constants.NBT.TAG_BYTE_ARRAY) ? section.getByteArray("Add") : null;
            for (int i = 0; i < blocks.length; i++) {
                final int high = add == null ? 0 : add[i >> 1] >> ((i & 1) * 4) & 0xF;
                final int number = high << 8 | blocks[i] & 0xFF;
                if (!numbers.containsKey(number)) {
                    continue;
                }
                final int renumbered = numbers.get(number);
                blocks[i] = (byte) renumbered;
                if (add == null && renumbered > 0xFF) {
                    add = new byte[2048];
                    section.setByteArray("Add", add);
                }
                if (add != null) {
                    final int shift = (i & 1) * 4;
                    add[i >> 1] = (byte) (add[i >> 1] & ~(0xF << shift) | (renumbered >> 8 & 0xF) << shift);
                }
            }
        }
    }

    /**
     * Built on the first chunk: the blocks' own numbers are final only once the world's registries have loaded.
     */
    private static Int2IntMap blockNumbers() {
        Int2IntMap numbers = blockNumbers;
        if (numbers != null) {
            return numbers;
        }
        synchronized (renamedBlocks) {
            numbers = new Int2IntOpenHashMap();
            for (final Int2ObjectMap.Entry<String> entry : renamedBlocks.int2ObjectEntrySet()) {
                final Block target = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(entry.getValue()));
                numbers.put(entry.getIntKey(), Block.getIdFromBlock(target));
            }
            blockNumbers = numbers;
            return numbers;
        }
    }
}
