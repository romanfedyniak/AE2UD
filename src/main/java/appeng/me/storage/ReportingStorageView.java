/*
 * This file is part of Applied Energistics 2.
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

package appeng.me.storage;

import net.minecraft.util.text.ITextComponent;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageChangeSource;
import appeng.api.storage.MEStorage;

/**
 * What a machine shows to a storage bus of another network, saying when it changes. That network counts the bus
 * once when it mounts it and then only adds up what it is told; a storage that changes without telling - another
 * network filling up behind an interface - was shown as it stood at mounting and never again.
 * <p>
 * A storage that reports for itself has its reports passed on. One that does not, such as an interface's own few
 * slots, is counted again on every tick of the bus and the difference told.
 */
public final class ReportingStorageView implements MEStorage, IStorageChangeSource, ITickingMonitor {

    private final MEStorage source;
    private final boolean forwards;
    private final StorageChangeListeners listeners = new StorageChangeListeners();
    private KeyCounter counted = new KeyCounter();

    public ReportingStorageView(final MEStorage source) {
        this.source = source;
        this.forwards = source instanceof IStorageChangeSource changes && changes.reportsChanges();
    }

    public MEStorage getSource() {
        return this.source;
    }

    @Override
    public void addChangeListener(final Listener listener) {
        if (this.forwards) {
            ((IStorageChangeSource) this.source).addChangeListener(listener);
            return;
        }
        // From here on only changes are told: whoever listens has just counted what there is.
        if (this.listeners.isEmpty()) {
            this.counted = this.source.getAvailableStacks();
        }
        this.listeners.addChangeListener(listener);
    }

    @Override
    public void removeChangeListener(final Listener listener) {
        if (this.forwards) {
            ((IStorageChangeSource) this.source).removeChangeListener(listener);
        } else {
            this.listeners.removeChangeListener(listener);
        }
    }

    @Override
    public TickRateModulation onTick() {
        if (this.forwards || this.listeners.isEmpty()) {
            return TickRateModulation.IDLE;
        }

        final KeyCounter next = this.source.getAvailableStacks();
        final KeyCounter before = this.counted;
        this.counted = next;
        boolean changed = false;
        for (final var entry : next) {
            if (before.get(entry.getKey()) != entry.getLongValue()) {
                changed = true;
                break;
            }
        }
        if (!changed) {
            for (final var entry : before) {
                if (next.get(entry.getKey()) == 0) {
                    changed = true;
                    break;
                }
            }
        }
        if (!changed) {
            return TickRateModulation.SLOWER;
        }
        this.listeners.postDiff(before, next);
        return TickRateModulation.URGENT;
    }

    @Override
    public boolean isPreferredStorageFor(final AEKey what, final IActionSource source) {
        return this.source.isPreferredStorageFor(what, source);
    }

    @Override
    public long insert(final AEKey what, final long amount, final Actionable mode, final IActionSource source) {
        final long inserted = this.source.insert(what, amount, mode, source);
        if (mode == Actionable.MODULATE && inserted > 0 && !this.forwards && !this.listeners.isEmpty()) {
            this.counted.add(what, inserted);
            this.listeners.post(what, inserted);
        }
        return inserted;
    }

    @Override
    public long extract(final AEKey what, final long amount, final Actionable mode, final IActionSource source) {
        final long extracted = this.source.extract(what, amount, mode, source);
        if (mode == Actionable.MODULATE && extracted > 0 && !this.forwards && !this.listeners.isEmpty()) {
            this.counted.remove(what, extracted);
            this.listeners.post(what, -extracted);
        }
        return extracted;
    }

    @Override
    public void getAvailableStacks(final KeyCounter out) {
        this.source.getAvailableStacks(out);
    }

    @Override
    public ITextComponent getDescription() {
        return this.source.getDescription();
    }
}
