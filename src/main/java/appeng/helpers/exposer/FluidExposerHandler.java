/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 * Adapted from NAE2's storage exposer (https://github.com/AE2-UEL/NAE2) by NotMyWing.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.helpers.exposer;

import java.util.Arrays;

import javax.annotation.Nullable;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.FluidTankProperties;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import appeng.api.behaviors.ExposedStorage;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;

/**
 * Every fluid the network holds is a tank of its own, which can be drained and never filled.
 */
public final class FluidExposerHandler implements IFluidHandler {

    private static final IFluidTankProperties[] NONE = new IFluidTankProperties[0];

    private final ExposedStorage storage;
    private IFluidTankProperties[] tanks = NONE;
    private int tanksVersion = -1;

    public FluidExposerHandler(final ExposedStorage storage) {
        this.storage = storage;
    }

    /**
     * Rebuilt only when something changed. The length check catches the exposer losing or regaining its
     * channel, which empties the view without changing the version.
     */
    @Override
    public IFluidTankProperties[] getTankProperties() {
        final int size = this.storage.size();
        if (this.tanksVersion != this.storage.getVersion() || this.tanks.length != size) {
            final IFluidTankProperties[] built = new IFluidTankProperties[size];
            int count = 0;
            for (int i = 0; i < size; i++) {
                final AEKey key = this.storage.getKey(i);
                if (key instanceof AEFluidKey) {
                    final int amount = (int) Math.min(this.storage.getAmount(key), Integer.MAX_VALUE);
                    built[count++] = new FluidTankProperties(((AEFluidKey) key).toStack(amount), amount, false, true);
                }
            }
            this.tanks = count == size ? built : Arrays.copyOf(built, count);
            this.tanksVersion = this.storage.getVersion();
        }
        return this.tanks;
    }

    @Override
    public int fill(final FluidStack resource, final boolean doFill) {
        return 0;
    }

    @Nullable
    @Override
    public FluidStack drain(final FluidStack resource, final boolean doDrain) {
        if (resource == null || resource.amount <= 0) {
            return null;
        }
        return this.drain(AEFluidKey.of(resource), resource.amount, doDrain);
    }

    @Nullable
    @Override
    public FluidStack drain(final int maxDrain, final boolean doDrain) {
        return this.drain(this.storage.getKey(0), maxDrain, doDrain);
    }

    @Nullable
    private FluidStack drain(@Nullable final AEKey key, final int amount, final boolean doDrain) {
        if (!(key instanceof AEFluidKey) || amount <= 0) {
            return null;
        }
        final long taken = this.storage.extract(key, amount, doDrain ? Actionable.MODULATE : Actionable.SIMULATE);
        return taken > 0 ? ((AEFluidKey) key).toStack((int) taken) : null;
    }
}
