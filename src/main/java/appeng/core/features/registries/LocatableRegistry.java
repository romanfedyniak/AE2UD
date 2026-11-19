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

package appeng.core.features.registries;


import appeng.api.events.LocatableEventAnnounce;
import appeng.api.events.LocatableEventAnnounce.LocatableEvent;
import appeng.api.features.ILocatable;
import appeng.api.features.ILocatableRegistry;
import appeng.util.Platform;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public final class LocatableRegistry implements ILocatableRegistry {
    private final Map<Long, ILocatable> set;

    public LocatableRegistry() {
        this.set = new ConcurrentHashMap<>();
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void updateLocatable(final LocatableEventAnnounce e) {
        if (Platform.isClient()) {
            return; // IGNORE!
        }

        if (e.change == LocatableEvent.REGISTER) {
            this.set.put(e.target.getLocatableSerial(), e.target);
        } else if (e.change == LocatableEvent.UNREGISTER) {
            // A copy of a station with the same serial going away leaves the one still standing.
            this.set.remove(e.target.getLocatableSerial(), e.target);
        }
    }

    /**
     * An unloading world tells none of its tiles, and a station kept here would keep its whole world alive.
     */
    @SubscribeEvent
    public void unloadWorld(final WorldEvent.Unload e) {
        this.set.values().removeIf(l -> l instanceof TileEntity && ((TileEntity) l).getWorld() == e.getWorld());
    }

    @Override
    public ILocatable getLocatableBy(final long serial) {
        return this.set.get(serial);
    }
}
