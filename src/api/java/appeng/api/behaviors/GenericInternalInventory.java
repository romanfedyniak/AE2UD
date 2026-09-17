/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.api.behaviors;

import javax.annotation.Nullable;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

/**
 * A machine's own stock, as slots holding an amount of any one {@link AEKey} each - what an ME Interface
 * keeps in its nine slots, rather than what a network holds.
 * <p>
 * A machine exposes this, and the platform handlers a pipe or a hopper asks for are built from it by the
 * factories registered in {@link GenericInventoryAdapters}. That is how an addon's key type reaches
 * something that was never written with it in mind: it registers an adapter rather than the machine gaining
 * a case for it.
 */
public interface GenericInternalInventory {

    /**
     * How many slots there are. Never changes.
     */
    int size();

    @Nullable
    GenericStack getStack(int slot);

    @Nullable
    AEKey getKey(int slot);

    long getAmount(int slot);

    /**
     * How much of {@code what} one slot holds in total, whatever is in it now.
     */
    long getCapacity(AEKey what);

    void setStack(int slot, @Nullable GenericStack stack);

    long insert(int slot, AEKey what, long amount, Actionable mode);

    long extract(int slot, AEKey what, long amount, Actionable mode);

    /**
     * Insert across every slot, filling the ones that already hold this key first.
     */
    long insert(AEKey what, long amount, Actionable mode);

    long extract(AEKey what, long amount, Actionable mode);
}
