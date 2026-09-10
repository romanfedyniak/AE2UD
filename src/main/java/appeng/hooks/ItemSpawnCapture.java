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

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Catches every item that enters a world between {@link #start} and {@link #stop}, instead of letting it spawn.
 * A machine breaking a block uses it to take what the block drops - a container's contents included - without
 * the items lying in the world first. Server thread only, and one capture at a time.
 */
public final class ItemSpawnCapture {

    public static final ItemSpawnCapture INSTANCE = new ItemSpawnCapture();

    @Nullable
    private static World capturing;
    private static List<ItemStack> captured = new ArrayList<>();

    private ItemSpawnCapture() {
    }

    public static void start(final World world) {
        capturing = world;
        captured = new ArrayList<>();
    }

    public static List<ItemStack> stop() {
        final List<ItemStack> result = captured;
        capturing = null;
        captured = new ArrayList<>();
        return result;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityJoinWorld(final EntityJoinWorldEvent event) {
        if (capturing != null && event.getWorld() == capturing && event.getEntity() instanceof EntityItem item) {
            if (!item.getItem().isEmpty()) {
                captured.add(item.getItem());
            }
            event.setCanceled(true);
        }
    }
}
