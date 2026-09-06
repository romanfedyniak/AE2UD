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

package appeng.worldgen.meteorite.settings;


import appeng.worldgen.meteorite.MeteorConstants;
import appeng.worldgen.meteorite.fallout.FalloutMode;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;


/**
 * Everything decided about one meteorite, which is everything needed to build it again the same way.
 * <p>
 * Two of these are not known when the meteorite is decided on, because 1.12 cannot ask the terrain about a
 * chunk it has not generated: the height, and whether there is water to fill the crater. Both are worked out
 * the first time a chunk of the meteorite is actually built, and written down here so the rest of it agrees.
 */
public final class PlacedMeteoriteSettings {

    private static final String TAG_SEED = "seed";
    private static final String TAG_POS = "pos";
    private static final String TAG_RADIUS = "radius";
    private static final String TAG_CRATER = "type";
    private static final String TAG_FALLOUT = "fallout";
    private static final String TAG_PURE = "pure";
    private static final String TAG_LAKE = "lake";
    private static final String TAG_DECAY = "decay";

    private final long seed;
    private final float meteoriteRadius;
    private final CraterType craterType;
    private final boolean pureCrater;
    private final FalloutMode fallout;
    private final boolean doDecay;

    private BlockPos pos;
    private CraterLakeState craterLake;

    public PlacedMeteoriteSettings(final long seed, final BlockPos pos, final float meteoriteRadius,
            final CraterType craterType, final boolean pureCrater, final CraterLakeState craterLake,
            final FalloutMode fallout) {
        this(seed, pos, meteoriteRadius, craterType, pureCrater, craterLake, fallout, true);
    }

    public PlacedMeteoriteSettings(final long seed, final BlockPos pos, final float meteoriteRadius,
            final CraterType craterType, final boolean pureCrater, final CraterLakeState craterLake,
            final FalloutMode fallout, final boolean doDecay) {
        this.seed = seed;
        this.pos = pos;
        this.meteoriteRadius = meteoriteRadius;
        this.craterType = craterType;
        this.pureCrater = pureCrater;
        this.craterLake = craterLake;
        this.fallout = fallout;
        this.doDecay = doDecay;
    }

    public long getSeed() {
        return this.seed;
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public CraterType getCraterType() {
        return this.craterType;
    }

    public float getMeteoriteRadius() {
        return this.meteoriteRadius;
    }

    public FalloutMode getFallout() {
        return this.fallout;
    }

    public boolean shouldPlaceCrater() {
        return this.craterType != CraterType.NONE;
    }

    public boolean isPureCrater() {
        return this.pureCrater;
    }

    public boolean isCraterLakeSet() {
        return this.craterLake != CraterLakeState.UNSET;
    }

    public boolean isCraterLake() {
        return this.craterLake == CraterLakeState.TRUE;
    }

    /** Only ever false for a meteorite converted from an older save, which was left half built. */
    public boolean shouldDecay() {
        return this.doDecay;
    }

    public void setHeight(final int y) {
        this.pos = new BlockPos(this.pos.getX(), y, this.pos.getZ());
    }

    public void setCraterLake(final CraterLakeState state) {
        this.craterLake = state;
    }

    public static PlacedMeteoriteSettings read(final NBTTagCompound nbt) {
        final BlockPos stored = BlockPos.fromLong(nbt.getLong(TAG_POS));
        // A height that was never set packs as zero, which is not a height a meteorite can have.
        final BlockPos pos = stored.getY() == 0
                ? new BlockPos(stored.getX(), MeteorConstants.UNSET_HEIGHT, stored.getZ())
                : stored;

        return new PlacedMeteoriteSettings(
                nbt.getLong(TAG_SEED),
                pos,
                nbt.getFloat(TAG_RADIUS),
                CraterType.values()[nbt.getByte(TAG_CRATER)],
                nbt.getBoolean(TAG_PURE),
                CraterLakeState.values()[nbt.getByte(TAG_LAKE)],
                FalloutMode.values()[nbt.getByte(TAG_FALLOUT)],
                !nbt.hasKey(TAG_DECAY) || nbt.getBoolean(TAG_DECAY));
    }

    public NBTTagCompound write(final NBTTagCompound nbt) {
        nbt.setLong(TAG_SEED, this.seed);
        nbt.setLong(TAG_POS, this.pos.toLong());
        nbt.setFloat(TAG_RADIUS, this.meteoriteRadius);
        nbt.setByte(TAG_CRATER, (byte) this.craterType.ordinal());
        nbt.setByte(TAG_FALLOUT, (byte) this.fallout.ordinal());
        nbt.setBoolean(TAG_PURE, this.pureCrater);
        nbt.setByte(TAG_LAKE, (byte) this.craterLake.ordinal());
        nbt.setBoolean(TAG_DECAY, this.doDecay);
        return nbt;
    }

    @Override
    public String toString() {
        return "PlacedMeteoriteSettings [seed=" + this.seed + ", pos=" + this.pos + ", radius=" + this.meteoriteRadius
                + ", crater=" + this.craterType + ", fallout=" + this.fallout + ", pure=" + this.pureCrater
                + ", lake=" + this.craterLake + ", decay=" + this.doDecay + "]";
    }
}
