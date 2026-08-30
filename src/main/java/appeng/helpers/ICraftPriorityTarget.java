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

package appeng.helpers;


/**
 * Something that holds the priority a crafting job would be ordered at: a container the craft priority
 * screen was opened over, or a machine that orders crafts of its own.
 * <p>
 * The screen is drawn over the one that was open rather than replacing it, so the container underneath is
 * the one that answers it - see {@link appeng.core.sync.packets.PacketCraftPriority}.
 */
public interface ICraftPriorityTarget {

    int getCraftPriority();

    void setCraftPriority(int priority);
}
