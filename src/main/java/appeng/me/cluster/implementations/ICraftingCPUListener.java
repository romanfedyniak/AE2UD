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

package appeng.me.cluster.implementations;


import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;


/**
 * Push-notification contract for {@link CraftingCPUCluster}, restoring the mechanism that used to be built on the
 * (now-deleted) generic {@code IBaseMonitor}/{@code IMEMonitorHandlerReceiver} model. See CONTRACT.md §10
 * ("Restored regressions", "Crafting CPU push notifications") — the owner explicitly rejected replacing this
 * with polling.
 * <p/>
 * Expressed against {@link AEKey} instead of {@code IAEItemStack}, matching every other storage-facing type in this
 * migration. Like the old contract, the notification only carries <em>which key changed</em>, not the delta or the
 * new amount — {@code ContainerCraftingCPU} (wave 3) never used the payload for anything beyond marking a key dirty;
 * it re-reads authoritative amounts itself via
 * {@link CraftingCPUCluster#getItemStack(AEKey, appeng.api.networking.crafting.CraftingItemList)} on its own
 * {@code detectAndSendChanges} tick.
 * <p/>
 * Usage: call {@link CraftingCPUCluster#addListener(ICraftingCPUListener, Object)} once a container starts
 * watching a CPU, and {@link CraftingCPUCluster#removeListener(ICraftingCPUListener)} when it stops (container
 * close, listener list empties, or the watched CPU changes) — exactly like the old
 * {@code IBaseMonitor#addListener}/{@code #removeListener} pair.
 */
public interface ICraftingCPUListener {

    /**
     * @param verificationToken the token passed to {@link CraftingCPUCluster#addListener}.
     * @return true if this listener should remain subscribed. Returning false causes it to be dropped the next
     *         time the CPU tries to notify it.
     */
    boolean isValid(Object verificationToken);

    /**
     * Called whenever the CPU's stored/active(waiting-for)/pending amount for {@code what} may have changed.
     *
     * @param what   the key whose amount changed.
     * @param source the action source responsible for the change.
     */
    void onCraftingCPUChange(AEKey what, IActionSource source);
}
