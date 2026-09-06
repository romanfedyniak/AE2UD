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


import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.ChunkGeneratorSettings;
import net.minecraft.world.gen.NoiseGeneratorOctaves;
import net.minecraftforge.event.terraingen.InitNoiseGensEvent;

import java.util.Arrays;
import java.util.EnumMap;


/**
 * Works out how high the ground would be in a chunk by running the same noise the overworld generator runs.
 * <p>
 * This is a copy of {@code ChunkGeneratorOverworld.setBlocksInChunk} and {@code generateHeightmap} that fills
 * an array of heights instead of placing blocks. It exists because 1.12 has no way to ask about a chunk it has
 * not built, and asking a chunk that happens to be loaded makes the answer depend on which those are - which
 * is precisely what a meteorite's position must not depend on.
 */
public class NoiseBasedChunkAccessor extends BaseChunkAccessor {

    private static final int CHUNK_SIZE = 16;

    private static final float[] BIOME_WEIGHTS = new float[25];

    static {
        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                BIOME_WEIGHTS[i + 2 + (j + 2) * 5] = 10.0F / MathHelper.sqrt((float) (i * i + j * j) + 0.2F);
            }
        }
    }

    private final ChunkGeneratorSettings settings;
    private final NoiseGeneratorOctaves minLimitPerlinNoise;
    private final NoiseGeneratorOctaves maxLimitPerlinNoise;
    private final NoiseGeneratorOctaves mainPerlinNoise;
    private final NoiseGeneratorOctaves depthNoise;

    private Biome[] biomesForGeneration;

    public NoiseBasedChunkAccessor(final World world, final InitNoiseGensEvent.Context ctx) {
        super(world);

        this.settings = ChunkGeneratorSettings.Factory
                .jsonToFactory(world.getWorldInfo().getGeneratorOptions()).build();
        this.minLimitPerlinNoise = ctx.getLPerlin1();
        this.maxLimitPerlinNoise = ctx.getLPerlin2();
        this.mainPerlinNoise = ctx.getPerlin();
        this.depthNoise = ctx.getDepth();
    }

    @Override
    protected EnumMap<HeightMapType, int[]> generateHeightMap(final ChunkPos pos) {
        this.biomesForGeneration = this.world.getBiomeProvider()
                .getBiomesForGeneration(this.biomesForGeneration, pos.x * 4 - 2, pos.z * 4 - 2, 10, 10);

        final double[] densityMap = this.generateDensities(pos.x * 4, pos.z * 4);
        final int[] surface = new int[CHUNK_SIZE * CHUNK_SIZE];
        final int[] oceanFloor = new int[CHUNK_SIZE * CHUNK_SIZE];
        final int seaLevel = this.world.getSeaLevel();

        Arrays.fill(oceanFloor, -1);

        for (int i = 0; i < 4; i++) {
            final int j = i * 5;
            final int k = (i + 1) * 5;

            for (int l = 0; l < 4; l++) {
                final int i1 = (j + l) * 33;
                final int j1 = (j + l + 1) * 33;
                final int k1 = (k + l) * 33;
                final int l1 = (k + l + 1) * 33;

                for (int i2 = 0; i2 < 32; i2++) {
                    double d1 = densityMap[i1 + i2];
                    double d2 = densityMap[j1 + i2];
                    double d3 = densityMap[k1 + i2];
                    double d4 = densityMap[l1 + i2];
                    final double d5 = (densityMap[i1 + i2 + 1] - d1) * 0.125D;
                    final double d6 = (densityMap[j1 + i2 + 1] - d2) * 0.125D;
                    final double d7 = (densityMap[k1 + i2 + 1] - d3) * 0.125D;
                    final double d8 = (densityMap[l1 + i2 + 1] - d4) * 0.125D;

                    for (int j2 = 0; j2 < 8; j2++) {
                        double d10 = d1;
                        double d11 = d2;
                        final double d12 = (d3 - d1) * 0.25D;
                        final double d13 = (d4 - d2) * 0.25D;

                        for (int k2 = 0; k2 < 4; k2++) {
                            final double d16 = (d11 - d10) * 0.25D;
                            double density = d10 - d16;

                            for (int l2 = 0; l2 < 4; l2++) {
                                final int blockY = i2 * 8 + j2;
                                final int index = (l * 4 + l2) << 4 | (i * 4 + k2);

                                if ((density += d16) > 0.0D) {
                                    surface[index] = blockY;
                                } else if (blockY < seaLevel && oceanFloor[index] == -1) {
                                    oceanFloor[index] = Math.max(0, blockY - 1);
                                }
                            }

                            d10 += d12;
                            d11 += d13;
                        }

                        d1 += d5;
                        d2 += d6;
                        d3 += d7;
                        d4 += d8;
                    }
                }
            }
        }

        // Only the submerged columns got an ocean floor; everywhere else the two are the same.
        for (int i = 0; i < surface.length; i++) {
            if (oceanFloor[i] == -1 || surface[i] >= seaLevel) {
                oceanFloor[i] = surface[i];
            }
        }

        final EnumMap<HeightMapType, int[]> result = new EnumMap<>(HeightMapType.class);
        result.put(HeightMapType.WORLD_SURFACE, surface);
        result.put(HeightMapType.OCEAN_FLOOR, oceanFloor);
        return result;
    }

    @Override
    public Type getAccessorType() {
        return Type.NOISE_BASED;
    }

    private double[] generateDensities(final int x, final int z) {
        final double[] densities = new double[5 * 33 * 5];

        final double[] depthRegion = this.depthNoise.generateNoiseOctaves(null, x, z, 5, 5,
                this.settings.depthNoiseScaleX, this.settings.depthNoiseScaleZ,
                this.settings.depthNoiseScaleExponent);

        final double[] mainNoiseRegion = this.mainPerlinNoise.generateNoiseOctaves(null, x, 0, z, 5, 33, 5,
                this.settings.coordinateScale / this.settings.mainNoiseScaleX,
                this.settings.heightScale / this.settings.mainNoiseScaleY,
                this.settings.coordinateScale / this.settings.mainNoiseScaleZ);

        final double[] minLimitRegion = this.minLimitPerlinNoise.generateNoiseOctaves(null, x, 0, z, 5, 33, 5,
                this.settings.coordinateScale, this.settings.heightScale, this.settings.coordinateScale);

        final double[] maxLimitRegion = this.maxLimitPerlinNoise.generateNoiseOctaves(null, x, 0, z, 5, 33, 5,
                this.settings.coordinateScale, this.settings.heightScale, this.settings.coordinateScale);

        int i = 0;
        int j = 0;

        for (int k = 0; k < 5; k++) {
            for (int l = 0; l < 5; l++) {
                float totalScale = 0;
                float totalDepth = 0;
                float totalWeight = 0;
                final Biome centerBiome = this.biomesForGeneration[k + 2 + (l + 2) * 10];

                for (int m = -2; m <= 2; m++) {
                    for (int n = -2; n <= 2; n++) {
                        final Biome biome = this.biomesForGeneration[k + m + 2 + (l + n + 2) * 10];
                        float depth = this.settings.biomeDepthOffSet
                                + biome.getBaseHeight() * this.settings.biomeDepthWeight;
                        float scale = this.settings.biomeScaleOffset
                                + biome.getHeightVariation() * this.settings.biomeScaleWeight;

                        if (this.world.getWorldInfo().getTerrainType() == WorldType.AMPLIFIED && depth > 0) {
                            depth = 1.0F + depth * 2.0F;
                            scale = 1.0F + scale * 4.0F;
                        }

                        float weight = BIOME_WEIGHTS[m + 2 + (n + 2) * 5] / (depth + 2.0F);
                        if (biome.getBaseHeight() > centerBiome.getBaseHeight()) {
                            weight /= 2.0F;
                        }

                        totalScale += scale * weight;
                        totalDepth += depth * weight;
                        totalWeight += weight;
                    }
                }

                totalScale = (totalScale / totalWeight) * 0.9F + 0.1F;
                totalDepth = ((totalDepth / totalWeight) * 4.0F - 1.0F) / 8.0F;

                double depthNoiseVal = depthRegion[j++] / 8000.0D;
                if (depthNoiseVal < 0.0D) {
                    depthNoiseVal = -depthNoiseVal * 0.3D;
                }
                depthNoiseVal = depthNoiseVal * 3.0D - 2.0D;

                if (depthNoiseVal < 0.0D) {
                    depthNoiseVal = Math.max(-1.0D, depthNoiseVal / 2.0D) / 1.4D / 2.0D;
                } else {
                    depthNoiseVal = Math.min(1.0D, depthNoiseVal) / 8.0D;
                }

                final double finalDepth = totalDepth + depthNoiseVal * 0.2D;
                final double offset = this.settings.baseSize
                        + (finalDepth * this.settings.baseSize / 8.0D) * 4.0D;

                for (int o = 0; o < 33; o++) {
                    double yScale = (o - offset) * this.settings.stretchY * 128.0D / 256.0D / totalScale;
                    if (yScale < 0.0D) {
                        yScale *= 4.0D;
                    }

                    final double minNoise = minLimitRegion[i] / this.settings.lowerLimitScale;
                    final double maxNoise = maxLimitRegion[i] / this.settings.upperLimitScale;
                    final double mainBlend = (mainNoiseRegion[i] / 10.0D + 1.0D) / 2.0D;

                    double noiseLerp = MathHelper.clampedLerp(minNoise, maxNoise, mainBlend) - yScale;

                    if (o > 29) {
                        final double falloff = (o - 29) / 3.0D;
                        noiseLerp = noiseLerp * (1.0D - falloff) + -10.0D * falloff;
                    }

                    densities[i++] = noiseLerp;
                }
            }
        }

        return densities;
    }
}
