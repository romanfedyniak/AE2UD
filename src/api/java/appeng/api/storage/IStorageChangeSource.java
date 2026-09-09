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

package appeng.api.storage;


import appeng.api.stacks.AEKey;


/**
 * An {@link MEStorage} that says when its contents change, instead of waiting to be counted again.
 * <p/>
 * A network keeps a running total of everything mounted on it. Recounting the lot to find out what moved costs
 * time proportional to how much is stored, every tick, on a network that may not have moved anything at all - so
 * a storage that can say "two more of this" is asked to, and only what cannot is counted the slow way.
 * <p/>
 * <b>Implement this on anything mounted onto a network whose contents can change.</b> A mount that neither
 * implements this nor calls {@link appeng.api.networking.storage.IStorageService#invalidateCache()} after
 * changing behind the network's back will be shown with stale contents in terminals - it will not correct itself.
 * The one case needing no work is a storage that only ever changes because the network itself inserted into or
 * extracted from it: the network sees those and counts them without being told.
 * <p/>
 * AE2UD-specific; upstream recounts unconditionally and has no equivalent.
 */
public interface IStorageChangeSource {

    /**
     * Whether this actually has anything to say. A wrapper that only passes changes along from what it wraps
     * answers for whatever is underneath it: a chain of wrappers over a storage that says nothing says nothing,
     * and whoever is above the chain must go on counting for itself rather than waiting to be told.
     */
    default boolean reportsChanges() {
        return true;
    }

    void addChangeListener(Listener listener);

    void removeChangeListener(Listener listener);

    @FunctionalInterface
    interface Listener {

        /**
         * @param what  what changed.
         * @param delta how much of it appeared, negative if it went away. Never zero.
         */
        void onStorageChange(AEKey what, long delta);
    }
}
