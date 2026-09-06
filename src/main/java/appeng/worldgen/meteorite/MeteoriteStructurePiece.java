/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2020, AlgorithmX2, All rights reserved.
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

package appeng.worldgen.meteorite;


import appeng.core.AEConfig;
import appeng.util.StructureBoundingBoxUtils;
import appeng.worldgen.meteorite.fallout.FalloutMode;
import appeng.worldgen.meteorite.heightmap.HeightMapAccessors;
import appeng.worldgen.meteorite.heightmap.IHeightAccessor;
import appeng.worldgen.meteorite.settings.CraterLakeState;
import appeng.worldgen.meteorite.settings.CraterType;
import appeng.worldgen.meteorite.settings.PlacedMeteoriteSettings;
import com.google.common.math.Quantiles;
import com.google.common.math.StatsAccumulator;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.MutableBlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraft.world.gen.structure.template.TemplateManager;
import net.minecraftforge.common.BiomeDictionary;
import org.jetbrains.annotations.NotNull;

import java.util.Random;


/**
 * One meteorite, built a piece at a time as the chunks it covers are generated.
 */
public class MeteoriteStructurePiece extends StructureComponent {

    public static final String ID = "Ae2MP";

    private static final int WATER_SCAN_RADIUS = 32;

    /** Which of the two shapes this piece is, since an old meteorite is centred differently. */
    private static final int NEW_GEN_PIECE = 1;
    private static final int OLD_GEN_PIECE = 2;

    private PlacedMeteoriteSettings settings;

    /** Required by the structure registry. */
    @SuppressWarnings("unused")
    public MeteoriteStructurePiece() {
    }

    public MeteoriteStructurePiece(final long seed, final BlockPos center, final float radius,
            final CraterType craterType, final boolean pureCrater, final CraterLakeState craterLake,
            final FalloutMode fallout) {
        super(NEW_GEN_PIECE);

        this.settings = new PlacedMeteoriteSettings(seed, center, radius, craterType, pureCrater, craterLake, fallout);
        this.boundingBox = createBoundingBox(center, this.settings.shouldPlaceCrater(), radius);
    }

    public MeteoriteStructurePiece(final PlacedMeteoriteSettings settings) {
        super(OLD_GEN_PIECE);

        this.settings = settings;
        this.boundingBox = createBoundingBox(settings.getPos(), settings.shouldPlaceCrater(),
                settings.getMeteoriteRadius());
    }

    private static StructureBoundingBox createBoundingBox(final BlockPos origin, final boolean hasCrater,
            final float radius) {
        if (hasCrater) {
            // A crater on ordinary terrain reaches about four chunks; the box is squared off to whole chunks
            // because the crater is dug per chunk.
            final int range = 4 * 16;
            final ChunkPos chunkPos = new ChunkPos(origin);

            return new StructureBoundingBox(
                    chunkPos.getXStart() - range, origin.getY(), chunkPos.getZStart() - range,
                    chunkPos.getXEnd() + range, Integer.MAX_VALUE, chunkPos.getZEnd() + range);
        }

        // Without a crater there is nothing outside the ball of sky stone.
        final int range = MathHelper.ceil(radius);
        return new StructureBoundingBox(
                origin.getX() - range, origin.getY(), origin.getZ() - range,
                origin.getX() + range, Integer.MAX_VALUE, origin.getZ() + range);
    }

    public PlacedMeteoriteSettings getSettings() {
        return this.settings;
    }

    @Override
    protected void writeStructureToNBT(@NotNull final NBTTagCompound nbt) {
        this.settings.write(nbt);
    }

    @Override
    protected void readStructureFromNBT(@NotNull final NBTTagCompound nbt, @NotNull final TemplateManager manager) {
        this.settings = PlacedMeteoriteSettings.read(nbt);
    }

    /**
     * @param loadedBB a column over the two-by-two of chunks that are certain to be loaded
     */
    @Override
    public boolean addComponentParts(@NotNull final World world, @NotNull final Random random,
            @NotNull final StructureBoundingBox loadedBB) {
        StructureBoundingBox intersectedBB = StructureBoundingBoxUtils.intersection(loadedBB, this.boundingBox);

        if (this.settings.getPos().getY() == MeteorConstants.UNSET_HEIGHT || !this.settings.isCraterLakeSet()) {
            this.finalizeProperties(world, intersectedBB);
        }

        // The old shape was built around one chunk, the new one around the middle of a two-by-two.
        final boolean oldGen = this.getComponentType() == OLD_GEN_PIECE;
        if (oldGen) {
            intersectedBB.offset(-8, 0, -8);
        }

        // Waking neighbours during generation is how a generator ends up generating the whole world.
        MeteoritePlacer.place(world, this.settings, intersectedBB, !oldGen);
        return true;
    }

    /** The two things that cannot be known until some of the terrain around the meteorite exists. */
    private void finalizeProperties(final World world, final StructureBoundingBox intersectedBB) {
        final StructureBoundingBox loadedBB = StructureBoundingBoxUtils.expandToChunkBounds(intersectedBB);

        if (this.settings.getPos().getY() == MeteorConstants.UNSET_HEIGHT) {
            final StructureBoundingBox centerBB = StructureBoundingBoxUtils
                    .createCenteredBoundingBox(this.settings.getPos(), (int) (this.settings.getMeteoriteRadius() * 2));
            final IHeightAccessor accessor = HeightMapAccessors.get(world, loadedBB, centerBB);

            this.updateHeight(world, accessor.getAffectedArea(), accessor);
        }

        if (!this.settings.isCraterLakeSet()) {
            final StructureBoundingBox centerBB = StructureBoundingBoxUtils
                    .createCenteredBoundingBox(this.settings.getPos(), WATER_SCAN_RADIUS);
            final IHeightAccessor accessor = HeightMapAccessors.get(world, loadedBB, centerBB);

            this.settings.setCraterLake(this.locateWaterAround(world, accessor.getAffectedArea(), accessor)
                    ? CraterLakeState.TRUE : CraterLakeState.FALSE);
        }
    }

    /**
     * How deep the meteorite sits. The median rather than the mean, because a cliff or a lone pillar in the
     * area should not drag the whole thing up with it.
     */
    @SuppressWarnings("UnstableApiUsage")
    private void updateHeight(final World world, final StructureBoundingBox localBB, final IHeightAccessor accessor) {
        final int yOffset = (int) Math.ceil(this.settings.getMeteoriteRadius()) + 1;

        final boolean isOcean = BiomeDictionary.hasType(
                world.getBiomeProvider().getBiome(this.settings.getPos()), BiomeDictionary.Type.OCEAN);
        final IHeightAccessor.HeightMapType heightMapType = isOcean
                ? IHeightAccessor.HeightMapType.OCEAN_FLOOR
                : IHeightAccessor.HeightMapType.WORLD_SURFACE;

        final StatsAccumulator meanStats = new StatsAccumulator();
        final double[] heights = new double[localBB.getXSize() * localBB.getZSize()];
        final MutableBlockPos pos = new MutableBlockPos();
        int i = 0;

        for (int x = localBB.minX; x <= localBB.maxX; x++) {
            for (int z = localBB.minZ; z <= localBB.maxZ; z++) {
                pos.setPos(x, 0, z);

                final int height = accessor.getHeight(pos, heightMapType);
                heights[i++] = height;
                meanStats.add(height);
            }
        }

        int centerY = (int) Quantiles.median().compute(heights);

        final double[] deviations = new double[heights.length];
        for (int j = 0; j < heights.length; j++) {
            deviations[j] = Math.abs(heights[j] - centerY);
        }

        // Broken ground: lean the way the ground leans rather than sitting halfway up a cliff face.
        final int medianAbsDev = (int) Quantiles.median().compute(deviations);
        if (medianAbsDev > 5) {
            centerY += (int) Math.signum(meanStats.mean() - centerY) * medianAbsDev;
        }

        centerY -= yOffset;

        // Nothing sits above the configured ceiling, and nothing sits in the bedrock either - a flat
        // generator can leave far less room than a meteorite needs.
        centerY = Math.min(centerY, AEConfig.instance().getMeteoriteMaximumSpawnHeight());
        this.settings.setHeight(Math.max(yOffset, centerY));
    }

    /**
     * Whether the rim of the crater would cut into water, which is what fills it afterwards.
     *
     * @return true as soon as one such column is found
     */
    private boolean locateWaterAround(final World world, final StructureBoundingBox localBB,
            final IHeightAccessor accessor) {
        final int maxY = world.getSeaLevel() - 1;
        final BlockPos meteorCenter = this.settings.getPos();
        final MutableBlockPos pos = new MutableBlockPos();

        for (int x = localBB.minX; x <= localBB.maxX; x++) {
            for (int z = localBB.minZ; z <= localBB.maxZ; z++) {
                final double dx = x - meteorCenter.getX();
                final double dz = z - meteorCenter.getZ();
                final double h = meteorCenter.getY() - this.settings.getMeteoriteRadius() + 1;
                final double distanceFrom = dx * dx + dz * dz;

                // Only the band the crater wall actually passes through.
                if (maxY > h + distanceFrom * 0.0175 && maxY < h + distanceFrom * 0.02) {
                    pos.setPos(x, 0, z);

                    if (accessor.getHeight(pos, IHeightAccessor.HeightMapType.OCEAN_FLOOR) < world.getSeaLevel()) {
                        return true;
                    }
                }
            }
        }

        return false;
    }
}
