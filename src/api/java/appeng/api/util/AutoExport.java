/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.api.util;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import appeng.api.behaviors.ExternalStorageStrategy;
import appeng.api.behaviors.StackExportStrategy;
import appeng.api.behaviors.StackWorldBehaviors;
import appeng.api.config.Actionable;
import appeng.api.implementations.IAutoExportHost;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;

/**
 * Which faces a machine pushes its output through, and the pushing itself. A machine keeps one, saves it with
 * its own data and calls {@link #push} when it has something to hand on; taking that much out of its own slots
 * is up to the machine.
 */
public final class AutoExport {

    private static final String TAG = "autoExportSides";
    /** The on/off setting machines had before faces could be chosen, still in old saves and memory cards. */
    private static final String LEGACY_TAG = "AUTO_EXPORT";
    private static final RelativeSide[] FACES = RelativeSide.values();
    private static final int ALL = (1 << FACES.length) - 1;
    private static final int AVAILABLE_SHIFT = 6;
    private static final int REFUSED_SHIFT = 12;

    private final TileEntity tile;
    private final IAutoExportHost host;
    private final Runnable onChange;

    private final Map<EnumFacing, List<StackExportStrategy>> exporters = new EnumMap<>(EnumFacing.class);
    private final Map<EnumFacing, Map<AEKeyType, ExternalStorageStrategy>> storages = new EnumMap<>(EnumFacing.class);

    private int sides;
    /** The face tried first: the one after the last face that took something. */
    private int next;

    /**
     * @param onChange run when a player changes the faces, to save and to wake a sleeping machine
     */
    public <T extends TileEntity & IAutoExportHost> AutoExport(final T host, final Runnable onChange) {
        this.tile = host;
        this.host = host;
        this.onChange = onChange;
    }

    public boolean isOn(final RelativeSide side) {
        return (this.sides & side.mask()) != 0;
    }

    /** Whether any chosen face is one the machine allows, which is whether it has export work at all. */
    public boolean isEnabled() {
        for (final RelativeSide side : FACES) {
            if (this.isOn(side) && this.host.canAutoExportTo(side)) {
                return true;
            }
        }
        return false;
    }

    public void toggle(final RelativeSide side) {
        this.sides ^= side.mask();
        this.onChange.run();
    }

    /**
     * Pushes up to {@code amount} of a key out through the chosen faces, one after another, starting after the
     * face that last took something.
     *
     * @return how much went out, which the machine then takes out of its own slots
     */
    public long push(final AEKey what, final long amount) {
        final World world = this.tile.getWorld();
        if (world == null || world.isRemote || amount <= 0
                || !this.host.getAutoExportTypes().contains(what.getType())) {
            return 0;
        }

        long moved = 0;
        for (int i = 0; i < FACES.length && moved < amount; i++) {
            final int index = (this.next + i) % FACES.length;
            final RelativeSide side = FACES[index];
            if (!this.isOn(side) || !this.host.canAutoExportTo(side)) {
                continue;
            }

            final EnumFacing facing = side.toFacing(this.host.getForward(), this.host.getUp());
            if (!world.isBlockLoaded(this.tile.getPos().offset(facing))) {
                continue;
            }

            long pushed = 0;
            for (final StackExportStrategy exporter : this.exporters(world, facing)) {
                pushed += exporter.push(what, amount - moved - pushed, Actionable.MODULATE);
                if (moved + pushed >= amount) {
                    break;
                }
            }

            if (pushed > 0) {
                moved += pushed;
                this.next = (index + 1) % FACES.length;
            }
        }
        return moved;
    }

    /**
     * The faces as a machine's window shows them: which are chosen, which have a block that takes what the
     * machine makes, and which the machine refuses. Asks the world, so the server calls it.
     */
    public int getSyncState() {
        int state = this.sides;
        final World world = this.tile.getWorld();
        for (final RelativeSide side : FACES) {
            if (!this.host.canAutoExportTo(side)) {
                state |= side.mask() << REFUSED_SHIFT;
            } else if (world != null
                    && this.accepts(world, side.toFacing(this.host.getForward(), this.host.getUp()))) {
                state |= side.mask() << AVAILABLE_SHIFT;
            }
        }
        return state;
    }

    public static boolean isOn(final int state, final RelativeSide side) {
        return (state & side.mask()) != 0;
    }

    public static boolean isAvailable(final int state, final RelativeSide side) {
        return (state & side.mask() << AVAILABLE_SHIFT) != 0;
    }

    public static boolean isRefused(final int state, final RelativeSide side) {
        return (state & side.mask() << REFUSED_SHIFT) != 0;
    }

    private boolean accepts(final World world, final EnumFacing facing) {
        final BlockPos target = this.tile.getPos().offset(facing);
        if (!world.isBlockLoaded(target) || world.getTileEntity(target) == null) {
            return false;
        }

        final Map<AEKeyType, ExternalStorageStrategy> strategies = this.storages.computeIfAbsent(facing,
                f -> StackWorldBehaviors.createExternalStorageStrategies(world, target, f.getOpposite()));
        for (final AEKeyType type : this.host.getAutoExportTypes()) {
            final ExternalStorageStrategy strategy = strategies.get(type);
            if (strategy != null && strategy.createWrapper(false, AutoExport::nothing) != null) {
                return true;
            }
        }
        return false;
    }

    private static void nothing() {
    }

    private List<StackExportStrategy> exporters(final World world, final EnumFacing facing) {
        return this.exporters.computeIfAbsent(facing, f -> StackWorldBehaviors.createExportStrategies(world,
                this.tile.getPos().offset(f), f.getOpposite()));
    }

    public void writeToNBT(final NBTTagCompound data) {
        data.setByte(TAG, (byte) this.sides);
    }

    /**
     * Reads the faces, or turns the old on/off setting into all faces or none. A tag with neither, such as a
     * memory card copied from another kind of machine, leaves them as they are.
     */
    public void readFromNBT(final NBTTagCompound data) {
        if (data.hasKey(TAG)) {
            this.sides = data.getByte(TAG) & ALL;
        } else if (data.hasKey(LEGACY_TAG)) {
            this.setAll("YES".equals(data.getString(LEGACY_TAG)));
        }
    }

    /** For a machine that kept its old on/off switch somewhere of its own. */
    public void setAll(final boolean on) {
        this.sides = on ? ALL : 0;
    }
}
