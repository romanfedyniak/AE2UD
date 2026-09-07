/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.debug.craftingtest;


import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;


/**
 * The whole contents of the test network, held in one counter.
 * <p>
 * Unbounded on purpose - a scenario says what is in the network, and a full-storage refusal would be a
 * second thing to explain when a run comes out wrong.
 */
public final class TestStorage implements MEStorage {

    private final KeyCounter contents = new KeyCounter();

    public void reset(final KeyCounter stock) {
        this.contents.reset();
        this.contents.clear();
        this.contents.addAll(stock);
    }

    public KeyCounter getContents() {
        return this.contents;
    }

    @Override
    public long insert(final AEKey what, final long amount, final Actionable mode, final IActionSource source) {
        if (what == null || amount <= 0) {
            return 0;
        }

        if (mode == Actionable.MODULATE) {
            this.contents.add(what, amount);
        }

        return amount;
    }

    @Override
    public long extract(final AEKey what, final long amount, final Actionable mode, final IActionSource source) {
        if (what == null || amount <= 0) {
            return 0;
        }

        final long extracted = Math.min(this.contents.get(what), amount);

        if (extracted > 0 && mode == Actionable.MODULATE) {
            this.contents.remove(what, extracted);
        }

        return Math.max(0, extracted);
    }

    @Override
    public void getAvailableStacks(final KeyCounter out) {
        for (final var entry : this.contents) {
            out.add(entry.getKey(), entry.getLongValue());
        }
    }

    @Override
    public ITextComponent getDescription() {
        return new TextComponentString("Crafting Test Rig");
    }
}
