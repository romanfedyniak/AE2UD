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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

import appeng.api.implementations.IAutoExportHost;
import appeng.api.stacks.AEKeyType;

/**
 * Faces named from a block's point of view, and the choice of them as it is saved.
 */
class AutoExportTest {

    @Test
    void rightIsThePlayersRightWhenFacingTheFront() {
        // The front looks north, so a player facing it looks south and has west on their right
        assertEquals(EnumFacing.WEST, RelativeSide.RIGHT.toFacing(EnumFacing.NORTH, EnumFacing.UP));
        assertEquals(EnumFacing.EAST, RelativeSide.LEFT.toFacing(EnumFacing.NORTH, EnumFacing.UP));
        assertEquals(EnumFacing.SOUTH, RelativeSide.BACK.toFacing(EnumFacing.NORTH, EnumFacing.UP));
    }

    @Test
    void everyTurnPutsTheSixFacesOnSixSides() {
        for (final EnumFacing forward : EnumFacing.values()) {
            for (final EnumFacing up : EnumFacing.values()) {
                final Set<EnumFacing> seen = EnumSet.noneOf(EnumFacing.class);
                for (final RelativeSide side : RelativeSide.values()) {
                    seen.add(side.toFacing(forward, up));
                }
                if (forward.getAxis() != up.getAxis()) {
                    assertEquals(6, seen.size(), forward + " / " + up);
                }
            }
        }
    }

    @Test
    void theOldSwitchBecomesAllFacesOrNone() {
        final AutoExport on = new AutoExport(new Host(), () -> {
        });
        final NBTTagCompound yes = new NBTTagCompound();
        yes.setString("AUTO_EXPORT", "YES");
        on.readFromNBT(yes);
        for (final RelativeSide side : RelativeSide.values()) {
            assertTrue(on.isOn(side));
        }

        final NBTTagCompound no = new NBTTagCompound();
        no.setString("AUTO_EXPORT", "NO");
        on.readFromNBT(no);
        assertFalse(on.isEnabled());
    }

    @Test
    void facesSurviveASaveAndATagWithoutThemChangesNothing() {
        final AutoExport export = new AutoExport(new Host(), () -> {
        });
        export.toggle(RelativeSide.BACK);
        export.toggle(RelativeSide.LEFT);

        final NBTTagCompound saved = new NBTTagCompound();
        export.writeToNBT(saved);

        final AutoExport loaded = new AutoExport(new Host(), () -> {
        });
        loaded.readFromNBT(saved);
        assertTrue(loaded.isOn(RelativeSide.BACK));
        assertTrue(loaded.isOn(RelativeSide.LEFT));
        assertFalse(loaded.isOn(RelativeSide.FRONT));

        loaded.readFromNBT(new NBTTagCompound());
        assertTrue(loaded.isOn(RelativeSide.BACK));
    }

    @Test
    void aRefusedFaceIsNoExportWork() {
        final Host host = new Host();
        host.refuseTopAndBottom = true;
        final AutoExport export = new AutoExport(host, () -> {
        });
        export.toggle(RelativeSide.TOP);
        assertFalse(export.isEnabled());

        final int state = export.getSyncState();
        assertTrue(AutoExport.isOn(state, RelativeSide.TOP));
        assertTrue(AutoExport.isRefused(state, RelativeSide.TOP));
        assertFalse(AutoExport.isRefused(state, RelativeSide.BACK));
    }

    private static final class Host extends TileEntity implements IAutoExportHost {

        private boolean refuseTopAndBottom;

        @Override
        public AutoExport getAutoExport() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<AEKeyType> getAutoExportTypes() {
            return Collections.emptySet();
        }

        @Override
        public boolean canAutoExportTo(final RelativeSide side) {
            return !this.refuseTopAndBottom || side != RelativeSide.TOP && side != RelativeSide.BOTTOM;
        }

        @Override
        public boolean canBeRotated() {
            return true;
        }

        @Override
        public EnumFacing getForward() {
            return EnumFacing.NORTH;
        }

        @Override
        public EnumFacing getUp() {
            return EnumFacing.UP;
        }

        @Override
        public void setOrientation(final EnumFacing forward, final EnumFacing up) {
        }
    }
}
