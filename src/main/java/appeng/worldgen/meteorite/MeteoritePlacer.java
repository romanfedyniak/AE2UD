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

package appeng.worldgen.meteorite;


import appeng.api.AEApi;
import appeng.api.definitions.IBlockDefinition;
import appeng.api.definitions.IMaterials;
import appeng.block.storage.BlockSkyChest;
import appeng.core.AEConfig;
import appeng.core.AppEng;
import appeng.core.features.AEFeature;
import appeng.services.compass.ServerCompassService;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import appeng.util.StructureBoundingBoxUtils;
import appeng.util.StructureBoundingBoxUtils.BoundingBoxClamper;
import appeng.worldgen.meteorite.fallout.Fallout;
import appeng.worldgen.meteorite.fallout.FalloutCopy;
import appeng.worldgen.meteorite.fallout.FalloutMode;
import appeng.worldgen.meteorite.fallout.FalloutSand;
import appeng.worldgen.meteorite.fallout.FalloutSnow;
import appeng.worldgen.meteorite.settings.CraterType;
import appeng.worldgen.meteorite.settings.PlacedMeteoriteSettings;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.MutableBlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraftforge.oredict.OreDictionary;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static appeng.worldgen.meteorite.MeteorConstants.MAX_METEOR_RADIUS;


/**
 * Builds the part of one meteorite that falls inside the chunks currently being generated.
 * <p>
 * Everything random here comes from the meteorite's own seed, so the same meteorite is built the same way
 * however many passes it takes and in whatever order those passes happen.
 */
public final class MeteoritePlacer {

    private static final double METEOR_UPPER_CURVATURE = 1.4D;
    private static final double METEOR_LOWER_CURVATURE = 0.8D;
    private static final double CRATER_CURVATURE = 0.02D;
    private static final int CRATER_RADIUS = 5;

    /** Two streams off one seed, so what is in the chest does not shift when the shape changes. */
    private static final long SEED_OFFSET_GEN = 1;
    private static final long SEED_OFFSET_LOOT = 2;

    private static final int SKYSTONE_SPAWN_LIMIT = 12;
    private static final int MAX_PRESSES = 3;

    private static final String[] NUGGET_ORES = {
            "nuggetIron", "nuggetCopper", "nuggetTin", "nuggetSilver",
            "nuggetLead", "nuggetPlatinum", "nuggetNickel", "nuggetAluminium", "nuggetElectrum" };

    private final IBlockDefinition skyStoneDefinition;
    private final MeteoriteBlockPutter putter;
    private final BoundingBoxClamper clamper;
    private final StructureBoundingBox boundingBox;
    private final World world;
    private final Random randomForGen;
    private final Random randomForLoot;
    private final int x;
    private final int y;
    private final int z;
    private final double meteoriteSize;
    private final double squaredMeteoriteSize;
    private final double squaredCraterSize;
    private final boolean placeCrater;
    private final CraterType craterType;
    private final boolean pureCrater;
    private final boolean craterLake;
    private final boolean doDecay;
    private final Fallout type;

    public MeteoritePlacer(final World world, final PlacedMeteoriteSettings settings,
            final StructureBoundingBox structureBB, final boolean shouldUpdate) {
        this.skyStoneDefinition = AEApi.instance().definitions().blocks().skyStoneBlock();
        this.putter = new MeteoriteBlockPutter(shouldUpdate);
        this.clamper = new BoundingBoxClamper(structureBB);
        this.boundingBox = structureBB;
        this.world = world;
        this.randomForGen = new Random(settings.getSeed() + SEED_OFFSET_GEN);
        this.randomForLoot = new Random(settings.getSeed() + SEED_OFFSET_LOOT);
        this.x = settings.getPos().getX();
        this.y = settings.getPos().getY();
        this.z = settings.getPos().getZ();
        this.meteoriteSize = settings.getMeteoriteRadius();
        this.squaredMeteoriteSize = this.meteoriteSize * this.meteoriteSize;
        this.placeCrater = settings.shouldPlaceCrater();
        this.craterType = settings.getCraterType();
        this.pureCrater = settings.isPureCrater();
        this.craterLake = settings.isCraterLake();
        this.doDecay = settings.shouldDecay();

        final double craterSize = this.meteoriteSize * 2 + CRATER_RADIUS;
        this.squaredCraterSize = craterSize * craterSize;

        // The fallout copies the ground, and the ground it can see is the part being built now.
        final BlockPos localCenter = new BlockPos(
                structureBB.minX + structureBB.getXSize() / 2,
                structureBB.minY + structureBB.getYSize() / 2,
                structureBB.minZ + structureBB.getZSize() / 2);
        this.type = this.getFallout(world, localCenter, settings.getFallout());
    }

    public static void place(final World world, final PlacedMeteoriteSettings settings,
            final StructureBoundingBox structureBB, final boolean shouldUpdate) {
        new MeteoritePlacer(world, settings, structureBB, shouldUpdate).place();
    }

    private void place() {
        if (this.placeCrater) {
            this.placeCrater();
        }

        this.placeMeteorite();

        if (this.doDecay && this.placeCrater) {
            this.decay();
        }

        if (this.craterLake) {
            this.placeCraterLake();
        }
    }

    /**
     * Digs the crater out of this pass's part of the world.
     * <p>
     * The wall follows {@code y = (y0 - radius + 1 + falloutAdjustment) + CRATER_CURVATURE * (dx^2 + dz^2)}.
     */
    private void placeCrater() {
        final Set<StructureBoundingBox> captureDropAreas = AppEng.instance().getMeteoriteGen().captureDropAreas;
        final int seaLevel = this.world.getSeaLevel();
        final int maxY = 255;
        final IBlockState filler = this.craterType.getFiller().getDefaultState();
        final double h = this.y - this.meteoriteSize + 1 + this.type.adjustCrater();
        final MutableBlockPos pos = new MutableBlockPos();

        for (final StructureBoundingBox chunkBB : this.splitPerChunk(this.boundingBox)) {
            captureDropAreas.add(chunkBB);

            final Chunk chunk = this.world.getChunk(chunkBB.minX >> 4, chunkBB.minZ >> 4);

            for (int j = this.y - CRATER_RADIUS; j <= maxY; j++) {
                for (int i = chunkBB.minX; i <= chunkBB.maxX; i++) {
                    for (int k = chunkBB.minZ; k <= chunkBB.maxZ; k++) {
                        pos.setPos(i, j, k);

                        final double dx = i - this.x;
                        final double dz = k - this.z;

                        if (j <= h + CRATER_CURVATURE * (dx * dx + dz * dz)) {
                            continue;
                        }

                        final IBlockState currentState = chunk.getBlockState(pos);

                        if (this.boundingBox.isVecInside(pos)) {
                            if (this.craterType != CraterType.NORMAL && j < this.y
                                    && currentState.getMaterial().isSolid()) {
                                this.putter.put(this.world, pos, filler, currentState);
                            } else {
                                this.putter.put(this.world, pos, Platform.AIR_BLOCK, currentState);
                            }
                        } else if (j >= this.y && j >= seaLevel && this.isFoliage(currentState, pos)) {
                            // A second look at a chunk already decorated: take away the trees left standing
                            // over the crater. The chunks past this one may not exist, so wake nothing.
                            this.putter.putSilent(this.world, pos, Platform.AIR_BLOCK, currentState);
                        }
                    }
                }
            }

            captureDropAreas.remove(chunkBB);
        }
    }

    /**
     * One box per chunk. A chunk whose neighbours on the negative axes are all decorated is widened to the
     * whole chunk, because decoration there may have grown over the crater and can now be cleared away.
     */
    private Collection<StructureBoundingBox> splitPerChunk(final StructureBoundingBox fullBB) {
        final ArrayList<StructureBoundingBox> result = new ArrayList<>();
        final int minChunkX = fullBB.minX >> 4;
        final int maxChunkX = fullBB.maxX >> 4;
        final int minChunkZ = fullBB.minZ >> 4;
        final int maxChunkZ = fullBB.maxZ >> 4;

        final LongSet populatedChunks = new LongOpenHashSet(8);

        for (int cx = minChunkX - 1; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ - 1; cz <= maxChunkZ; cz++) {
                if (cx == maxChunkX && cz == maxChunkZ) {
                    continue;
                }

                final Chunk chunk = this.world.getChunkProvider().getLoadedChunk(cx, cz);
                if (chunk != null && chunk.isTerrainPopulated()) {
                    populatedChunks.add(ChunkPos.asLong(cx, cz));
                }
            }
        }

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                StructureBoundingBox chunkBB = new StructureBoundingBox(
                        cx << 4, cz << 4, (cx << 4) + 15, (cz << 4) + 15);

                if (!(populatedChunks.contains(ChunkPos.asLong(cx - 1, cz))
                        && populatedChunks.contains(ChunkPos.asLong(cx, cz - 1))
                        && populatedChunks.contains(ChunkPos.asLong(cx - 1, cz - 1)))) {
                    chunkBB = StructureBoundingBoxUtils.intersection(chunkBB, fullBB);
                }

                result.add(chunkBB);
            }
        }

        return result;
    }

    private boolean isFoliage(final IBlockState state, final BlockPos pos) {
        final Block block = state.getBlock();
        final Material material = state.getMaterial();

        return block.isFoliage(this.world, pos) || block.isWood(this.world, pos)
                || block.isLeaves(state, this.world, pos)
                || material == Material.LEAVES || material == Material.PLANTS || material == Material.VINE;
    }

    private void placeMeteorite() {
        this.skyStoneDefinition.maybeBlock().ifPresent(this::placeMeteoriteSkyStone);

        final BlockPos chestPos = new BlockPos(this.x, this.y, this.z);
        if (this.boundingBox.isVecInside(chestPos)
                && AEConfig.instance().isFeatureEnabled(AEFeature.SPAWN_PRESSES_IN_METEORITES)) {
            this.placeChest(chestPos);

            // The one chunk that gained something the compass can point at.
            ServerCompassService.updateArea((WorldServer) this.world, new ChunkPos(chestPos));
        }
    }

    private void placeChest(final BlockPos pos) {
        AEApi.instance().definitions().blocks().skyStoneChest().maybeBlock().ifPresent(block -> this.putter
                .put(this.world, pos, block.getDefaultState().withProperty(BlockSkyChest.NATURAL, true)));

        final TileEntity te = this.world.getTileEntity(pos);
        final InventoryAdaptor ap = InventoryAdaptor.getAdaptor(te, EnumFacing.UP);

        if (ap != null) {
            this.addPresses(ap);
            this.addJunk(ap);
        }
    }

    /** One to three of the four presses, never the same one twice in a chest. */
    private void addPresses(final InventoryAdaptor ap) {
        final IMaterials materials = AEApi.instance().definitions().materials();
        final List<ItemStack> presses = new ArrayList<>(4);

        materials.calcProcessorPress().maybeStack(1).ifPresent(presses::add);
        materials.engProcessorPress().maybeStack(1).ifPresent(presses::add);
        materials.logicProcessorPress().maybeStack(1).ifPresent(presses::add);
        materials.siliconPress().maybeStack(1).ifPresent(presses::add);

        Collections.shuffle(presses, this.randomForLoot);

        final int count = Math.min(presses.size(), 1 + this.randomForLoot.nextInt(MAX_PRESSES));
        for (int i = 0; i < count; i++) {
            ap.addItems(presses.get(i));
        }
    }

    private void addJunk(final InventoryAdaptor ap) {
        final int rolls = 1 + this.randomForLoot.nextInt(2);

        for (int i = 0; i < rolls; i++) {
            switch (this.randomForLoot.nextInt(3)) {
                case 0:
                    this.skyStoneDefinition.maybeStack(1 + this.randomForLoot.nextInt(SKYSTONE_SPAWN_LIMIT))
                            .ifPresent(ap::addItems);
                    break;
                case 1:
                    final ItemStack nugget = this.randomNugget();
                    if (!nugget.isEmpty()) {
                        ap.addItems(nugget);
                    }
                    break;
                default:
            }
        }
    }

    private ItemStack randomNugget() {
        final List<ItemStack> possibles = new ArrayList<>();

        for (final String ore : NUGGET_ORES) {
            possibles.addAll(OreDictionary.getOres(ore));
        }
        possibles.add(new ItemStack(Items.GOLD_NUGGET));

        final ItemStack nugget = possibles.get(this.randomForLoot.nextInt(possibles.size())).copy();
        nugget.setCount(1 + this.randomForLoot.nextInt(SKYSTONE_SPAWN_LIMIT));
        return nugget;
    }

    /**
     * The ball of sky stone: {@code 0.7*dx^2 + curvature*dy^2 + 0.7*dz^2 < radius^2}, flattened on top.
     */
    private void placeMeteoriteSkyStone(final Block block) {
        final int meteorXLength = this.clamper.minX(this.x - MAX_METEOR_RADIUS);
        final int meteorXHeight = this.clamper.maxX(this.x + MAX_METEOR_RADIUS);
        final int meteorZLength = this.clamper.minZ(this.z - MAX_METEOR_RADIUS);
        final int meteorZHeight = this.clamper.maxZ(this.z + MAX_METEOR_RADIUS);

        final MutableBlockPos pos = new MutableBlockPos();

        for (int i = meteorXLength; i <= meteorXHeight; i++) {
            for (int j = this.y - MAX_METEOR_RADIUS; j < this.y + MAX_METEOR_RADIUS; j++) {
                for (int k = meteorZLength; k <= meteorZHeight; k++) {
                    final double dx = i - this.x;
                    final double dy = j - this.y;
                    final double dz = k - this.z;

                    if (dx * dx * 0.7
                            + dy * dy * (j > this.y ? METEOR_UPPER_CURVATURE : METEOR_LOWER_CURVATURE)
                            + dz * dz * 0.7 < this.squaredMeteoriteSize) {
                        pos.setPos(i, j, k);
                        this.putter.put(this.world, pos, block);
                    }
                }
            }
        }
    }

    /** Scatters loose ground about the crater and lets what is hanging over it fall in. */
    private void decay() {
        double randomShit = 0;

        final int meteorXLength = this.clamper.minX(this.x - 30);
        final int meteorXHeight = this.clamper.maxX(this.x + 30);
        final int meteorZLength = this.clamper.minZ(this.z - 30);
        final int meteorZHeight = this.clamper.maxZ(this.z + 30);

        final MutableBlockPos pos = new MutableBlockPos();
        final MutableBlockPos posUp = new MutableBlockPos();
        final MutableBlockPos posDown = new MutableBlockPos();

        for (int i = meteorXLength; i <= meteorXHeight; i++) {
            for (int k = meteorZLength; k <= meteorZHeight; k++) {
                pos.setPos(i, 0, k);
                final Chunk chunk = this.world.getChunk(pos);

                for (int j = this.y - MAX_METEOR_RADIUS + 1; j < this.y + 30; j++) {
                    pos.setPos(i, j, k);
                    posUp.setPos(i, j + 1, k);
                    posDown.setPos(i, j - 1, k);

                    final IBlockState currentState = chunk.getBlockState(pos);

                    if (this.pureCrater && currentState.getBlock() == this.craterType.getFiller()) {
                        continue;
                    }

                    final IBlockState upperBlockState = chunk.getBlockState(posUp);

                    if (currentState.getMaterial().isReplaceable()) {
                        if (upperBlockState.getMaterial() != Material.AIR) {
                            this.putter.put(this.world, pos, upperBlockState, currentState);
                        } else if (randomShit < 100 * this.squaredCraterSize) {
                            final IBlockState lowerBlockState = chunk.getBlockState(posDown);

                            if (!lowerBlockState.getMaterial().isReplaceable()) {
                                final double dx = i - this.x;
                                final double dy = j - this.y;
                                final double dz = k - this.z;
                                final double dist = dx * dx + dy * dy + dz * dz;

                                final double extraRange = this.randomForGen.nextDouble() * 0.6;
                                final double height = this.squaredCraterSize * (extraRange + 0.2)
                                        - Math.abs(dist - this.squaredCraterSize * 1.7);

                                if (lowerBlockState.getMaterial() != Material.AIR && height > 0
                                        && this.randomForGen.nextDouble() > 0.6) {
                                    randomShit++;
                                    this.type.getRandomFall(this.world, pos);
                                }
                            }
                        }
                    } else if (upperBlockState.getMaterial() == Material.AIR
                            && this.randomForGen.nextDouble() > 0.4) {
                        final double dx = i - this.x;
                        final double dy = j - this.y;
                        final double dz = k - this.z;

                        if (dx * dx + dy * dy + dz * dz < this.squaredCraterSize * 1.6) {
                            this.type.getRandomInset(this.world, pos);
                        }
                    }
                }
            }
        }
    }

    /** A crater that cut into water fills with it, up to the sea. */
    private void placeCraterLake() {
        final int maxY = this.world.getSeaLevel() - 1;
        final MutableBlockPos pos = new MutableBlockPos();

        for (int currentX = this.boundingBox.minX; currentX <= this.boundingBox.maxX; currentX++) {
            for (int currentZ = this.boundingBox.minZ; currentZ <= this.boundingBox.maxZ; currentZ++) {
                pos.setPos(currentX, 0, currentZ);
                final Chunk currentChunk = this.world.getChunk(pos);

                for (int currentY = this.y - CRATER_RADIUS; currentY <= maxY; currentY++) {
                    pos.setPos(currentX, currentY, currentZ);

                    final double dx = currentX - this.x;
                    final double dz = currentZ - this.z;
                    final double h = this.y - this.meteoriteSize + 1 + this.type.adjustCrater();
                    final double distanceFrom = dx * dx + dz * dz;

                    if (currentY > h + distanceFrom * CRATER_CURVATURE) {
                        final IBlockState currentState = currentChunk.getBlockState(pos);

                        if (currentState.getMaterial() == Material.AIR) {
                            this.putter.put(this.world, pos, Blocks.WATER, currentState);

                            if (currentY == maxY) {
                                this.world.scheduleUpdate(pos, Blocks.WATER, 0);
                            }
                        }
                    } else if (maxY + (maxY - currentY) * 2 + 2 > h + distanceFrom * CRATER_CURVATURE) {
                        this.pillarDownSlopeBlocks(currentChunk, pos);
                    }
                }
            }
        }
    }

    /** Walls the lake in, so the water it is about to be given does not run out of the side of the hill. */
    private void pillarDownSlopeBlocks(final Chunk currentChunk, final BlockPos blockPos) {
        final MutableBlockPos enclosingBlockPos = new MutableBlockPos(blockPos);

        for (int i = 0; i < 20; i++) {
            if (this.placeEnclosingBlock(currentChunk, enclosingBlockPos)) {
                break;
            }

            enclosingBlockPos.move(EnumFacing.DOWN);
        }
    }

    private boolean placeEnclosingBlock(final Chunk currentChunk, final MutableBlockPos enclosingBlockPos) {
        final IBlockState currentState = currentChunk.getBlockState(enclosingBlockPos);
        final Material material = currentState.getMaterial();

        if (material != Material.AIR && (material.isLiquid() || !material.isReplaceable())) {
            return true;
        }

        if (this.craterType == CraterType.LAVA && this.randomForGen.nextFloat() < 0.075f) {
            this.putter.put(this.world, enclosingBlockPos, Blocks.MAGMA, currentState);
        } else {
            this.type.getRandomFall(this.world, enclosingBlockPos);
        }

        return false;
    }

    private Fallout getFallout(final World world, final BlockPos pos, final FalloutMode mode) {
        switch (mode) {
            case SAND:
                return new FalloutSand(world, pos, this.putter, this.skyStoneDefinition, this.randomForGen);
            case TERRACOTTA:
                return new FalloutCopy(world, pos, this.putter, this.skyStoneDefinition, this.randomForGen);
            case ICE_SNOW:
                return new FalloutSnow(world, pos, this.putter, this.skyStoneDefinition, this.randomForGen);
            default:
                return new Fallout(this.putter, this.skyStoneDefinition, this.randomForGen);
        }
    }
}
