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


/**
 * An {@link MEStorage} that keeps nothing it accepts. What it takes leaves the storage system altogether: a
 * formation plane places it in the world, a crafting job waiting for it carries it off to whoever ordered it.
 * <p/>
 * <b>Such a mount has to say so.</b> A network reports on behalf of a mount that does not
 * {@linkplain IStorageChangeSource#reportsChanges() report for itself}, and what it reports is what it moved
 * in - which for a sink was never stored. Saying nothing means being counted as holding everything that ever
 * passed through, a total that grows with every insertion and comes right only when the network is made to
 * count itself again.
 * <p/>
 * A storage whose contents stand still whatever passes through it belongs here too, for the same reason: a
 * creative cell hands out and swallows any amount of what it is configured with and still holds exactly what
 * it held before.
 * <p/>
 * Either way there is no change of its own to report, so the listener methods do nothing.
 * <p/>
 * AE2UD-specific; upstream recounts unconditionally and needs no equivalent.
 */
public interface IStorageSink extends IStorageChangeSource {

    @Override
    default void addChangeListener(final Listener listener) {
    }

    @Override
    default void removeChangeListener(final Listener listener) {
    }
}
