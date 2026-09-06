/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
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

package appeng.worldgen;


import appeng.api.features.IWorldGen.WorldGenType;
import appeng.core.features.registries.WorldGenRegistry;
import appeng.worldgen.meteorite.MapGenMeteorite;
import appeng.worldgen.meteorite.MeteoriteStructurePiece;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceArraySet;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.gen.MapGenBase;
import net.minecraft.world.gen.structure.MapGenStructureIO;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.terraingen.InitMapGenEvent;
import net.minecraftforge.event.terraingen.TerrainGen;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.IWorldGenerator;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Random;
import java.util.Set;


/**
 * Hangs the meteorite structure generator off the world.
 * <p>
 * A meteorite is a vanilla structure now, which means the game itself keeps the record of where they are and
 * builds each of them a chunk at a time as those chunks come into being. What that replaces was a list of
 * meteorites in files of AE2's own, replayed into whichever neighbouring chunks happened to be loaded at the
 * time - so a meteorite could be left cut off at a chunk border, and where the next one went depended on the
 * order the world was walked in.
 */
public final class MeteoriteWorldGen implements IWorldGenerator {

    /** Where blocks are being broken to dig a crater, so what they drop can be thrown away. */
    public final Set<StructureBoundingBox> captureDropAreas = new ReferenceArraySet<>();

    private final Int2ObjectMap<MapGenMeteorite> meteoriteGenerators = new Int2ObjectOpenHashMap<>();

    public void registerStructure() {
        MapGenStructureIO.registerStructure(MapGenMeteorite.Start.class, MapGenMeteorite.ID);
        MapGenStructureIO.registerStructureComponent(MeteoriteStructurePiece.class, MeteoriteStructurePiece.ID);
    }

    public MapGenMeteorite getGenerator(final World world) {
        final int key = world.provider.getDimension();
        MapGenMeteorite generator = this.meteoriteGenerators.get(key);

        if (generator == null) {
            generator = new MapGenMeteorite();

            // Let another mod wrap or replace it, the way vanilla structures can be.
            final MapGenBase modded = TerrainGen.getModdedMapGen(generator, InitMapGenEvent.EventType.CUSTOM);
            if (modded instanceof MapGenMeteorite) {
                generator = (MapGenMeteorite) modded;
            }

            this.meteoriteGenerators.put(key, generator);
        }

        return generator;
    }

    @Override
    public void generate(final Random random, final int chunkX, final int chunkZ, final World world,
            final IChunkGenerator chunkGenerator, final IChunkProvider chunkProvider) {
        if (WorldGenRegistry.INSTANCE.isWorldGenEnabled(WorldGenType.METEORITES, world)) {
            this.getGenerator(world).generateStructure(world, world.rand, new ChunkPos(chunkX, chunkZ));
        }
    }

    @SubscribeEvent
    public void detachMeteoriteGen(final WorldEvent.Unload event) {
        final World world = event.getWorld();

        if (!world.isRemote) {
            this.meteoriteGenerators.remove(world.provider.getDimension());
        }
    }

    /**
     * Tells the generator about a chunk that has just been built. Forge offers no hook inside
     * {@code IChunkGenerator.generateChunk}, and an unpopulated chunk being loaded is the next best moment.
     */
    @SubscribeEvent
    public void onChunkPostGenerated(final ChunkEvent.Load event) {
        final Chunk chunk = event.getChunk();
        final World world = event.getWorld();

        if (chunk.getWorld().isRemote || chunk.isTerrainPopulated()
                || !WorldGenRegistry.INSTANCE.isWorldGenEnabled(WorldGenType.METEORITES, world)) {
            return;
        }

        this.getGenerator(world).generate(world, chunk.x, chunk.z, null);
    }

    /**
     * The same for a chunk being read back off disk, where Forge offers no hook into
     * {@code IChunkGenerator.recreateStructures} either.
     */
    @SubscribeEvent
    public void onChunkRecreateStructures(final ChunkDataEvent.Load event) {
        final Chunk chunk = event.getChunk();
        final World world = event.getWorld();

        if (!WorldGenRegistry.INSTANCE.isWorldGenEnabled(WorldGenType.METEORITES, world)) {
            return;
        }

        this.getGenerator(world).generate(world, chunk.x, chunk.z, null);
    }

    /**
     * A crater is dug by breaking blocks, and a chest full of what a forest dropped is not a crater.
     */
    @SubscribeEvent
    public void onItemDrop(final EntityJoinWorldEvent event) {
        if (event.getWorld().isRemote || !(event.getEntity() instanceof EntityItem)) {
            return;
        }

        for (final StructureBoundingBox area : this.captureDropAreas) {
            if (area.isVecInside(event.getEntity().getPosition())) {
                event.setCanceled(true);
                return;
            }
        }
    }
}
