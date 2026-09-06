/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.worldgen.meteorite.heightmap;


import appeng.api.features.IWorldGen;
import appeng.core.AELog;
import appeng.core.features.registries.WorldGenRegistry;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.world.World;
import net.minecraft.world.gen.NoiseGeneratorOctaves;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraftforge.event.terraingen.InitNoiseGensEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;


/**
 * Which way each dimension's ground height is worked out.
 * <p>
 * A dimension generated the way the overworld is can be asked about a chunk that does not exist yet, by
 * running the same noise the generator would have run - and that is what makes where a meteorite sits depend
 * on the seed alone. A dimension generated some other way has no such shortcut, and falls back to reading
 * whichever chunks are loaded, which is not the same on every visit.
 */
public final class HeightMapAccessors {

    private static final int MIN_OCTAVES = 16;
    private static final int MAX_OCTAVES = 16;
    private static final int MAIN_OCTAVES = 8;
    private static final int DEPTH_OCTAVES = 16;

    private static final Int2ObjectMap<IHeightMapGeneratableAccessor> ACCESSORS = new Int2ObjectOpenHashMap<>();

    /** Dimensions already complained about, so the warning is one line per world rather than per meteorite. */
    private static final IntSet WARNED = new IntOpenHashSet();

    private HeightMapAccessors() {
    }

    public static IHeightAccessor get(final World world, final StructureBoundingBox loadedBB,
            final StructureBoundingBox centerBB) {
        final int id = world.provider.getDimension();
        IHeightMapGeneratableAccessor accessor = ACCESSORS.get(id);

        if (accessor == null) {
            if (WARNED.add(id)) {
                AELog.info("Dimension %d does not generate its terrain the way the overworld does, so meteorites "
                        + "there are placed from the chunks that happen to be loaded and are not reproducible.", id);
            }

            accessor = new LoadedChunkAccessor(world);
            ACCESSORS.put(id, accessor);
        }

        accessor.determineArea(loadedBB, centerBB);
        accessor.generateHeightMaps();
        return accessor;
    }

    @SubscribeEvent
    public static void gatherNoiseGens(final InitNoiseGensEvent<?> event) {
        // Jabel erases the generic, so the context has to be checked rather than declared.
        if (!(event.getNewValues() instanceof InitNoiseGensEvent.ContextOverworld)) {
            return;
        }

        final InitNoiseGensEvent.ContextOverworld ctx = (InitNoiseGensEvent.ContextOverworld) event.getNewValues();
        final World world = event.getWorld();

        if (world.isRemote
                || !WorldGenRegistry.INSTANCE.isWorldGenEnabled(IWorldGen.WorldGenType.METEORITES, world)
                || !hasKnownNoiseGens(ctx)) {
            return;
        }

        final int id = world.provider.getDimension();
        AELog.info("Meteorites in dimension %d are placed from the world seed alone.", id);
        ACCESSORS.put(id, new NoiseBasedChunkAccessor(world, ctx));
    }

    @SubscribeEvent
    public static void removeHeightMapAccessor(final WorldEvent.Unload event) {
        final World world = event.getWorld();

        if (!world.isRemote) {
            final int id = world.provider.getDimension();
            ACCESSORS.remove(id);
            WARNED.remove(id);
        }
    }

    /**
     * Whether the noise this world was handed is still the noise the copied generation expects. Another mod
     * may have swapped a generator out, and running our copy against something else would be worse than
     * falling back.
     */
    private static boolean hasKnownNoiseGens(final InitNoiseGensEvent.ContextOverworld ctx) {
        if (ctx.getLPerlin1() == null || ctx.getLPerlin2() == null
                || ctx.getPerlin() == null || ctx.getDepth() == null) {
            return false;
        }

        return ctx.getLPerlin1().getClass() == NoiseGeneratorOctaves.class
                && ctx.getLPerlin2().getClass() == NoiseGeneratorOctaves.class
                && ctx.getPerlin().getClass() == NoiseGeneratorOctaves.class
                && ctx.getDepth().getClass() == NoiseGeneratorOctaves.class
                && ctx.getLPerlin1().octaves == MIN_OCTAVES
                && ctx.getLPerlin2().octaves == MAX_OCTAVES
                && ctx.getPerlin().octaves == MAIN_OCTAVES
                && ctx.getDepth().octaves == DEPTH_OCTAVES;
    }
}
