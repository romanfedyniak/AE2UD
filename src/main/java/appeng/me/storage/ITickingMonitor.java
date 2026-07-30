/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
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


import appeng.api.networking.ticking.TickRateModulation;


/**
 * Implemented by {@link appeng.api.storage.MEStorage} that need to periodically re-scan whatever they wrap (an
 * adjacent inventory, a fluid tank) to detect changes made outside of {@code insert}/{@code extract}.
 * <p/>
 * The old {@code setActionSource}/{@code setMode} members are gone: per-object listener bookkeeping was removed
 * along with {@code IMEMonitor} - the network's storage service now diffs its cached amounts itself and notifies
 * {@code IStorageWatcherNode}s directly.
 */
public interface ITickingMonitor {

    TickRateModulation onTick();
}
