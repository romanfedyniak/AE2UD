/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.worldgen.meteorite;


import appeng.core.AEConfig;
import appeng.util.Platform;
import appeng.worldgen.meteorite.fallout.FalloutMode;
import appeng.worldgen.meteorite.settings.CraterLakeState;
import appeng.worldgen.meteorite.settings.CraterType;
import appeng.worldgen.meteorite.settings.PlacedMeteoriteSettings;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.util.ReportedException;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.structure.MapGenStructure;
import net.minecraft.world.gen.structure.StructureStart;
import net.minecraftforge.common.BiomeDictionary;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Random;


/**
 * Where the meteorites are, worked out from the world seed alone.
 * <p>
 * The world is cut into square cells of {@code minMeteoriteDistance} blocks. Each cell holds one meteorite,
 * at a position drawn from a generator seeded by the cell's coordinates - so a chunk can answer "is there a
 * meteorite in me" without knowing anything about its neighbours, and the answer is the same however the
 * world is walked. What it replaced kept a list of the meteorites already placed and measured distances
 * against it, which made the answer depend on the order chunks happened to be generated in.
 */
public class MapGenMeteorite extends MapGenStructure {

    public static final String ID = "ae2_meteorite";

    /** How far a meteorite of a cluster lands from the one before it. */
    private static final int CLUSTER_MIN_DISTANCE = 10;
    private static final int CLUSTER_MAX_DISTANCE = 30;

    /** A run of them has to stop somewhere, however lucky the rolls. */
    private static final int MAX_CLUSTERED = 3;

    @Override
    @NotNull
    public String getStructureName() {
        return ID;
    }

    @Override
    @Nullable
    public BlockPos getNearestStructurePos(@NotNull final World worldIn, @NotNull final BlockPos pos,
            final boolean findUnexplored) {
        // The compass finds meteorites by its own record; nothing asks this.
        return null;
    }

    @Override
    protected boolean canSpawnStructureAtCoords(final int chunkX, final int chunkZ) {
        final int gridCellSize = gridCellSize();
        final BlockPos pos = drawPosition(this.rand, this.world.getSeed(),
                Math.floorDiv(chunkX << 4, gridCellSize), Math.floorDiv(chunkZ << 4, gridCellSize));

        return (pos.getX() >> 4) == chunkX && (pos.getZ() >> 4) == chunkZ;
    }

    /**
     * Where the meteorite of one cell lands, leaving the generator seeded on that cell - which is what
     * {@link #getStructureStart} then draws the meteorite's own seed from.
     */
    private static BlockPos drawPosition(final Random rand, final long worldSeed, final int gridX,
            final int gridZ) {
        final int size = gridCellSize();
        final int margin = gridCellMargin(size);

        // The cell's own seed, mixed the way the chunk seed FML hands out is mixed.
        Platform.seedFromGrid(rand, worldSeed, gridX, gridZ, 0);

        return new BlockPos(
                gridX * size + rand.nextInt(size - 2 * margin) + margin,
                MeteorConstants.UNSET_HEIGHT,
                gridZ * size + rand.nextInt(size - 2 * margin) + margin);
    }

    /** Where the meteorite of one cell lands, asked from outside the generator. */
    public static BlockPos meteoriteIn(final long worldSeed, final int gridX, final int gridZ) {
        return drawPosition(new Random(), worldSeed, gridX, gridZ);
    }

    /** The seed the cell holding this position would have given its meteorite. */
    public static long seedFor(final long worldSeed, final BlockPos pos) {
        final int size = gridCellSize();
        final Random rand = new Random();

        // The cell of the chunk this position is in, which is how canSpawnStructureAtCoords asks.
        drawPosition(rand, worldSeed, Math.floorDiv((pos.getX() >> 4) << 4, size),
                Math.floorDiv((pos.getZ() >> 4) << 4, size));

        long meteorSeed = rand.nextLong();
        while (meteorSeed == 0) {
            meteorSeed = rand.nextLong();
        }

        return meteorSeed;
    }

    public static int gridCellSize() {
        return Math.max(8, AEConfig.instance().getMinMeteoriteDistance());
    }

    /** How far from the edge of its cell a meteorite must stay, so two in neighbouring cells never touch. */
    private static int gridCellMargin(final int gridCellSize) {
        return Math.max(1, gridCellSize / 10);
    }

    @Override
    @NotNull
    protected StructureStart getStructureStart(final int chunkX, final int chunkZ) {
        // Drawn from the cell generator, which canSpawnStructureAtCoords has just seeded and used twice.
        long meteorSeed = this.rand.nextLong();
        while (meteorSeed == 0) {
            meteorSeed = this.rand.nextLong();
        }

        return new Start(this.world, meteorSeed, chunkX, chunkZ);
    }

    /**
     * Adds a meteorite read out of an older save, which already knows everything about itself.
     */
    public synchronized void addOldMeteor(final World worldIn, final int chunkX, final int chunkZ,
            final PlacedMeteoriteSettings settings) {
        this.initializeStructureData(worldIn);

        if (this.structureMap.containsKey(ChunkPos.asLong(chunkX, chunkZ))) {
            return;
        }

        try {
            final StructureStart start = new Start(chunkX, chunkZ, settings);
            this.structureMap.put(ChunkPos.asLong(chunkX, chunkZ), start);

            if (start.isSizeableStructure()) {
                this.setStructureStart(chunkX, chunkZ, start);
            }
        } catch (final Throwable throwable) {
            final CrashReport report = CrashReport.makeCrashReport(throwable, "Exception preparing structure feature");
            final CrashReportCategory category = report.makeCategory("Feature being prepared");
            category.addDetail("Is feature chunk", () -> "True (Manually added)");
            category.addCrashSection("Chunk location", String.format("%d,%d", chunkX, chunkZ));
            category.addDetail("Structure type", () -> MapGenMeteorite.class.getCanonicalName());
            throw new ReportedException(report);
        }
    }

    public static class Start extends StructureStart {

        /** Required by the structure registry. */
        public Start() {
        }

        /**
         * Everything that can be decided without the terrain. The chunk this is for has almost certainly not
         * been generated yet, so nothing here may read a block.
         */
        public Start(final World worldIn, final long seed, final int chunkX, final int chunkZ) {
            super(chunkX, chunkZ);

            final Random rng = new Random(seed);
            final float radius = rng.nextFloat()
                    * (MeteorConstants.MAX_METEOR_RADIUS - MeteorConstants.MIN_METEOR_RADIUS)
                    + MeteorConstants.MIN_METEOR_RADIUS;

            final BlockPos centerPos = new BlockPos((chunkX << 4) + rng.nextInt(16), MeteorConstants.UNSET_HEIGHT,
                    (chunkZ << 4) + rng.nextInt(16));

            // The biome can be had without the chunk; the ground under it cannot.
            final Biome spawnBiome = worldIn.getBiomeProvider().getBiome(centerPos);

            this.components.add(new MeteoriteStructurePiece(
                    seed,
                    centerPos,
                    radius,
                    determineCraterType(spawnBiome, rng),
                    rng.nextFloat() > .9f,
                    CraterLakeState.UNSET,
                    FalloutMode.fromBiome(spawnBiome)));

            this.addCluster(worldIn, rng, centerPos);
            this.updateBoundingBox();
        }

        /**
         * A cell may hold a huddle of meteorites rather than one. They are components of the same
         * structure, so the game builds them together and nothing here has to keep its own record; and
         * they are drawn from the cell's own generator after the first meteorite is settled, so adding
         * this moves none of the meteorites that were there before it.
         * <p>
         * A cluster is deliberately closer than {@code minMeteoriteDistance}: that is what it is.
         */
        private void addCluster(final World worldIn, final Random rng, final BlockPos firstPos) {
            final double clusterChance = AEConfig.instance().getMeteoriteClusterChance();
            BlockPos previous = firstPos;

            for (int i = 0; i < MAX_CLUSTERED && rng.nextDouble() < clusterChance; i++) {
                previous = nearby(previous, rng);

                long clusterSeed = rng.nextLong();
                while (clusterSeed == 0) {
                    clusterSeed = rng.nextLong();
                }

                // Its own seed, so a companion is as varied as a meteorite that landed alone.
                final Random companion = new Random(clusterSeed);
                final float radius = companion.nextFloat()
                        * (MeteorConstants.MAX_METEOR_RADIUS - MeteorConstants.MIN_METEOR_RADIUS)
                        + MeteorConstants.MIN_METEOR_RADIUS;
                final Biome biome = worldIn.getBiomeProvider().getBiome(previous);

                this.components.add(new MeteoriteStructurePiece(
                        clusterSeed,
                        previous,
                        radius,
                        determineCraterType(biome, companion),
                        companion.nextFloat() > .9f,
                        CraterLakeState.UNSET,
                        FalloutMode.fromBiome(biome)));
            }
        }

        private static BlockPos nearby(final BlockPos from, final Random rng) {
            final double angle = rng.nextDouble() * Math.PI * 2;
            final int distance = CLUSTER_MIN_DISTANCE
                    + rng.nextInt(CLUSTER_MAX_DISTANCE - CLUSTER_MIN_DISTANCE + 1);

            return new BlockPos(
                    from.getX() + (int) Math.round(Math.cos(angle) * distance),
                    MeteorConstants.UNSET_HEIGHT,
                    from.getZ() + (int) Math.round(Math.sin(angle) * distance));
        }

        /** For a meteorite converted out of an older save. */
        public Start(final int chunkX, final int chunkZ, final PlacedMeteoriteSettings settings) {
            super(chunkX, chunkZ);

            this.components.add(new MeteoriteStructurePiece(settings));
            this.updateBoundingBox();
        }

        /**
         * What the crater is filled with, from the biome. Temperature by elevation is not considered, since
         * the meteorite has no elevation yet.
         */
        private static CraterType determineCraterType(final Biome biome, final Random random) {
            final float temp = biome.getDefaultTemperature();

            if (BiomeDictionary.hasType(biome, BiomeDictionary.Type.OCEAN)) {
                return CraterType.NONE;
            }

            // Half of them are just a hole in the ground.
            if (random.nextFloat() <= .5f) {
                return CraterType.NORMAL;
            }

            if (temp >= 1) {
                final boolean lava = random.nextFloat() > .5f;
                final boolean obsidian = random.nextFloat() > .75f;

                if (!(biome.canRain() || biome.getEnableSnow())) {
                    return lava ? CraterType.LAVA : CraterType.NORMAL;
                } else if (biome.canRain()) {
                    return lava ? (obsidian ? CraterType.OBSIDIAN : CraterType.LAVA) : CraterType.NORMAL;
                }
            }

            if (temp < 1 && temp >= 0.2) {
                final boolean lake = random.nextFloat() > .25f;
                final boolean lava = random.nextFloat() > .8f;

                if (!(biome.canRain() || biome.getEnableSnow())) {
                    return lava ? CraterType.LAVA : CraterType.NORMAL;
                } else if (biome.canRain()) {
                    final boolean obsidian = random.nextFloat() > .75f;
                    final CraterType alternativeObsidian = obsidian ? CraterType.OBSIDIAN : CraterType.LAVA;
                    final CraterType craterLake = lake ? CraterType.WATER : CraterType.NORMAL;
                    return lava ? alternativeObsidian : craterLake;
                } else {
                    final boolean snow = random.nextFloat() > .75f;
                    return snow ? CraterType.SNOW : (lake ? CraterType.WATER : CraterType.NORMAL);
                }
            }

            if (temp < 0.2) {
                final boolean lake = random.nextFloat() > .25f;
                final boolean lava = random.nextFloat() > .95f;
                final boolean frozen = random.nextFloat() > .25f;

                if (!(biome.canRain() || biome.getEnableSnow())) {
                    return lava ? CraterType.LAVA : CraterType.NORMAL;
                } else if (biome.canRain()) {
                    final CraterType frozenLake = frozen ? CraterType.ICE : CraterType.WATER;
                    return lava ? CraterType.LAVA : (lake ? frozenLake : CraterType.NORMAL);
                } else {
                    return lava ? CraterType.LAVA : (lake ? CraterType.SNOW : CraterType.NORMAL);
                }
            }

            return CraterType.NORMAL;
        }
    }
}
