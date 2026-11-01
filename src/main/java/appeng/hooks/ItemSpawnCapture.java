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

package appeng.hooks;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Catches every item that enters a world between {@link #start} and {@link #stop}, instead of letting it spawn.
 * A machine breaking a block uses it to take what the block drops - a container's contents included - without
 * the items lying in the world first. One capture at a time on each thread: a server that ticks regions at once
 * breaks blocks on several, and an item spawning on one thread is not another thread's drop.
 */
public final class ItemSpawnCapture {

    public static final ItemSpawnCapture INSTANCE = new ItemSpawnCapture();

    private static final ThreadLocal<Capture> CAPTURE = new ThreadLocal<>();

    private ItemSpawnCapture() {
    }

    public static void start(final World world) {
        CAPTURE.set(new Capture(world));
    }

    public static List<ItemStack> stop() {
        final Capture capture = CAPTURE.get();
        CAPTURE.remove();
        return capture != null ? capture.items : new ArrayList<>();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityJoinWorld(final EntityJoinWorldEvent event) {
        final Capture capture = CAPTURE.get();
        if (capture != null && event.getWorld() == capture.world && event.getEntity() instanceof EntityItem item) {
            if (!item.getItem().isEmpty()) {
                capture.items.add(item.getItem());
            }
            event.setCanceled(true);
        }
    }

    private static final class Capture {
        private final World world;
        private final List<ItemStack> items = new ArrayList<>();

        private Capture(final World world) {
            this.world = world;
        }
    }
}
