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

package appeng.integration.modules.waila.part;


import appeng.api.implementations.parts.IPartStorageMonitor;
import appeng.api.parts.IPart;
import appeng.api.stacks.GenericStack;
import appeng.core.localization.WailaText;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

import java.util.List;


/**
 * Storage monitor provider for WAILA
 *
 * @author thatsIch
 * @version rv2
 * @since rv2
 */
public final class StorageMonitorWailaDataProvider extends BasePartWailaDataProvider {
    /**
     * Displays the stack if present and if the monitor is locked.
     * Can handle fluids and items.
     *
     * @param part           maybe storage monitor
     * @param currentToolTip to be written to tooltip
     * @param accessor       information wrapper
     * @param config         config option
     * @return modified tooltip
     */
    @Override
    public List<String> getWailaBody(final IPart part, final List<String> currentToolTip, final IWailaDataAccessor accessor, final IWailaConfigHandler config) {
        if (part instanceof IPartStorageMonitor) {
            final IPartStorageMonitor monitor = (IPartStorageMonitor) part;

            final GenericStack displayed = monitor.getDisplayed();
            final boolean isLocked = monitor.isLocked();

            // The old "TODO: generalize" is done: the branch on item-vs-fluid is gone, because every key
            // type now answers getDisplayName() for itself. A monitor showing a key type this fork has
            // never heard of names it correctly with no change here.
            if (displayed != null) {
                currentToolTip.add(WailaText.Showing.getLocal() + ": " + displayed.what().getDisplayName().getFormattedText());
            }

            currentToolTip.add((isLocked) ? WailaText.Locked.getLocal() : WailaText.Unlocked.getLocal());
        }

        return currentToolTip;
    }
}
