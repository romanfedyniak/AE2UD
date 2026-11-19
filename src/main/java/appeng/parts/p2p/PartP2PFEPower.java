/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2017, AlgorithmX2, All rights reserved.
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

package appeng.parts.p2p;


import appeng.api.config.PowerUnits;
import appeng.api.parts.IPartModel;
import appeng.api.parts.P2PTunnelModels;
import appeng.capabilities.Capabilities;
import appeng.core.AppEng;
import appeng.items.parts.PartModels;
import appeng.me.GridAccessException;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.IEnergyStorage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;


public class PartP2PFEPower extends PartP2PTunnel<PartP2PFEPower> {
    private static final P2PTunnelModels MODELS = new P2PTunnelModels(new ResourceLocation(AppEng.MOD_ID, "part/p2p/p2p_tunnel_fe"));
    private static final IEnergyStorage NULL_ENERGY_STORAGE = new NullEnergyStorage();
    private final IEnergyStorage inputHandler = new InputEnergyStorage();
    private final IEnergyStorage outputHandler = new OutputEnergyStorage();
    // The inputs being asked on this thread: two pairs whose outputs face each other's inputs pass energy round in
    // a circle, and the stack overflowed.
    private static final ThreadLocal<Set<PartP2PFEPower>> ASKING = ThreadLocal.withInitial(
            () -> Collections.newSetFromMap(new IdentityHashMap<>()));

    public PartP2PFEPower(ItemStack is) {
        super(is);
    }

    @PartModels
    public static List<IPartModel> getModels() {
        return MODELS.getModels();
    }

    @Override
    public IPartModel getStaticModels() {
        return MODELS.getModel(this.isPowered(), this.isActive());
    }

    @Override
    public void onTunnelNetworkChange() {
        this.getHost().notifyNeighbors();
    }

    private IEnergyStorage getAttachedEnergyStorage() {
        if (this.isActive()) {
            final TileEntity te = this.getLoadedFacingTile();

            if (te != null && te.hasCapability(Capabilities.FORGE_ENERGY, this.getSide().getOpposite().getFacing())) {
                return te.getCapability(Capabilities.FORGE_ENERGY, this.getSide().getOpposite().getFacing());
            }
        }
        return NULL_ENERGY_STORAGE;
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability) {
        if (capability == Capabilities.FORGE_ENERGY) {
            return true;
        }
        return super.hasCapability(capability);
    }

    @Nullable
    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability) {
        if (capability == Capabilities.FORGE_ENERGY) {
            if (this.isOutput()) {
                return (T) this.outputHandler;
            }
            return (T) this.inputHandler;
        }
        return super.getCapability(capability);
    }

    private List<IEnergyStorage> getOutputStorages() throws GridAccessException {
        final List<IEnergyStorage> storages = new ArrayList<>();
        for (final PartP2PFEPower output : this.getOutputs()) {
            storages.add(output.getAttachedEnergyStorage());
        }
        return storages;
    }

    /**
     * Shares what is offered out evenly, then offers what some could not take to the rest, never handing on more
     * than was offered. A simulation asks each storage for its total in one go, since a simulated storage does not
     * remember what it was offered a moment before.
     */
    static int receive(final List<IEnergyStorage> targets, final int maxReceive, final boolean simulate) {
        if (targets.isEmpty() || maxReceive <= 0) {
            return 0;
        }

        final int share = Math.max(1, maxReceive / targets.size());
        final int[] taken = new int[targets.size()];
        int total = 0;

        for (int i = 0; i < targets.size() && total < maxReceive; i++) {
            taken[i] = targets.get(i).receiveEnergy(Math.min(share, maxReceive - total), simulate);
            total += taken[i];
        }

        for (int i = 0; i < targets.size() && total < maxReceive; i++) {
            final int more = maxReceive - total;
            final int got = simulate
                    ? Math.max(0, targets.get(i).receiveEnergy(taken[i] + more, true) - taken[i])
                    : targets.get(i).receiveEnergy(more, false);
            total += Math.min(got, more);
        }

        return total;
    }

    /** Sums without wrapping round: batteries near the int limit read as full, not as negative. */
    static int sum(final List<IEnergyStorage> targets, final boolean max) {
        long total = 0;
        for (final IEnergyStorage target : targets) {
            total += max ? target.getMaxEnergyStored() : target.getEnergyStored();
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    private class InputEnergyStorage implements IEnergyStorage {
        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (!ASKING.get().add(PartP2PFEPower.this)) {
                return 0;
            }
            try {
                final int total = receive(PartP2PFEPower.this.getOutputStorages(), maxReceive, simulate);
                if (!simulate) {
                    PartP2PFEPower.this.queueTunnelDrain(PowerUnits.RF, total);
                }
                return total;
            } catch (GridAccessException ignored) {
                return 0;
            } finally {
                ASKING.get().remove(PartP2PFEPower.this);
            }
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }

        @Override
        public int getMaxEnergyStored() {
            return this.stored(true);
        }

        @Override
        public int getEnergyStored() {
            return this.stored(false);
        }

        private int stored(final boolean max) {
            if (!ASKING.get().add(PartP2PFEPower.this)) {
                return 0;
            }
            try {
                return sum(PartP2PFEPower.this.getOutputStorages(), max);
            } catch (GridAccessException e) {
                return 0;
            } finally {
                ASKING.get().remove(PartP2PFEPower.this);
            }
        }
    }

    private class OutputEnergyStorage implements IEnergyStorage {
        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            final int total = PartP2PFEPower.this.getAttachedEnergyStorage().extractEnergy(maxExtract, simulate);

            if (!simulate) {
                PartP2PFEPower.this.queueTunnelDrain(PowerUnits.RF, total);
            }

            return total;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return 0;
        }

        @Override
        public boolean canExtract() {
            return PartP2PFEPower.this.getAttachedEnergyStorage().canExtract();
        }

        @Override
        public boolean canReceive() {
            return false;
        }

        @Override
        public int getMaxEnergyStored() {
            return PartP2PFEPower.this.getAttachedEnergyStorage().getMaxEnergyStored();
        }

        @Override
        public int getEnergyStored() {
            return PartP2PFEPower.this.getAttachedEnergyStorage().getEnergyStored();
        }
    }

    private static class NullEnergyStorage implements IEnergyStorage {

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return 0;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return 0;
        }

        @Override
        public int getMaxEnergyStored() {
            return 0;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return false;
        }

    }
}
