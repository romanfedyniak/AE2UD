/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2018, AlgorithmX2, All rights reserved.
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

package appeng.fluids.parts;

import javax.annotation.Nullable;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import appeng.api.behaviors.ExternalStorageStrategy;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.me.storage.ITickingMonitor;

/**
 * Wraps a plain {@link IFluidHandler} (any tank that only exposes the vanilla Forge capability) so it can be used
 * as an {@link MEStorage} -- the role {@code IMEInventory<IAEFluidStack>} used to play for the fluid storage bus.
 * <p/>
 * This is the fluid counterpart of {@code appeng.parts.misc.ItemHandlerAdapter}, mirroring its shape exactly (see
 * that class's javadoc): a small cache of {@link AEFluidKey}s rebuilt on construction, after every real
 * (non-simulated) insert/extract, and once per {@link #onTick()}. It is also the reference
 * {@link ExternalStorageStrategy} for {@link appeng.api.stacks.AEKeyType#fluids()}, registered by
 * {@code appeng.parts.misc.InitExternalStorageStrategies}. Public (unlike the item version) because the
 * registration call in that class lives in a different package.
 */
public class FluidHandlerAdapter implements MEStorage, ITickingMonitor {
    private final IFluidHandler fluidHandler;
    private final boolean extractableOnly;
    @Nullable
    private final Runnable changeListener;
    private KeyCounter currentlyCached = new KeyCounter();

    FluidHandlerAdapter(final IFluidHandler fluidHandler, final boolean extractableOnly, @Nullable final Runnable changeListener) {
        this.fluidHandler = fluidHandler;
        this.extractableOnly = extractableOnly;
        this.changeListener = changeListener;
        this.updateCache();
    }

    @Override
    public long insert(final AEKey what, final long amount, final Actionable mode, final IActionSource source) {
        if (!(what instanceof AEFluidKey fluidKey) || amount <= 0) {
            return 0;
        }

        final int toInsert = (int) Math.min(amount, Integer.MAX_VALUE);
        final FluidStack stack = fluidKey.toStack(toInsert);
        final int filled = this.fluidHandler.fill(stack, mode == Actionable.MODULATE);

        if (filled > 0 && mode == Actionable.MODULATE) {
            this.updateCache();
            this.notifyChange();
        }

        return filled;
    }

    @Override
    public long extract(final AEKey what, final long amount, final Actionable mode, final IActionSource source) {
        if (!(what instanceof AEFluidKey fluidKey) || amount <= 0) {
            return 0;
        }

        final int toDrain = (int) Math.min(amount, Integer.MAX_VALUE);
        final FluidStack drained = this.fluidHandler.drain(fluidKey.toStack(toDrain), mode == Actionable.MODULATE);
        final long extracted = drained != null ? drained.amount : 0;

        if (extracted > 0 && mode == Actionable.MODULATE) {
            this.updateCache();
            this.notifyChange();
        }

        return extracted;
    }

    @Override
    public void getAvailableStacks(final KeyCounter out) {
        for (final var entry : this.currentlyCached) {
            out.add(entry.getKey(), entry.getLongValue());
        }
    }

    @Override
    public TickRateModulation onTick() {
        final KeyCounter before = this.currentlyCached;
        this.updateCache();
        return keyCountersEqual(before, this.currentlyCached) ? TickRateModulation.SLOWER : TickRateModulation.URGENT;
    }

    private void notifyChange() {
        if (this.changeListener != null) {
            this.changeListener.run();
        }
    }

    private void updateCache() {
        final KeyCounter fresh = new KeyCounter();
        for (final IFluidTankProperties tankProperty : this.fluidHandler.getTankProperties()) {
            final FluidStack contents = tankProperty.getContents();
            if (contents == null || contents.amount <= 0) {
                continue;
            }
            if (this.extractableOnly && this.fluidHandler.drain(contents, false) == null) {
                continue;
            }
            final AEFluidKey key = AEFluidKey.of(contents);
            if (key != null) {
                fresh.add(key, contents.amount);
            }
        }
        this.currentlyCached = fresh;
    }

    private static boolean keyCountersEqual(final KeyCounter a, final KeyCounter b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (final var entry : a) {
            if (b.get(entry.getKey()) != entry.getLongValue()) {
                return false;
            }
        }
        return true;
    }

    /**
     * The {@link ExternalStorageStrategy} for {@link appeng.api.stacks.AEKeyType#fluids()}. Registered by
     * {@code appeng.parts.misc.InitExternalStorageStrategies#register()}; {@code appeng.parts.misc.PartStorageBus}
     * obtains it through {@link appeng.api.behaviors.StackWorldBehaviors#createExternalStorageStrategies} rather
     * than talking to this class directly.
     */
    public static final class Strategy implements ExternalStorageStrategy {
        private final World world;
        private final BlockPos fromPos;
        private final EnumFacing fromSide;

        public Strategy(final World world, final BlockPos fromPos, final EnumFacing fromSide) {
            this.world = world;
            this.fromPos = fromPos;
            this.fromSide = fromSide;
        }

        @Override
        @Nullable
        public MEStorage createWrapper(final boolean extractableOnly, final Runnable injectOrExtractCallback) {
            final TileEntity target = this.world.getTileEntity(this.fromPos);
            if (target == null) {
                return null;
            }

            final IFluidHandler fluidHandler = target.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, this.fromSide);
            if (fluidHandler == null) {
                return null;
            }

            return new FluidHandlerAdapter(fluidHandler, extractableOnly, injectOrExtractCallback);
        }
    }
}
