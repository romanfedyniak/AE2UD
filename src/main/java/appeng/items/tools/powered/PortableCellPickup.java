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

package appeng.items.tools.powered;


import appeng.api.implementations.guiobjects.IPortableCell;
import appeng.api.stacks.AEItemKey;
import appeng.items.contents.PortableCellViewer;
import appeng.me.helpers.PlayerSource;
import appeng.util.Platform;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.StatList;
import net.minecraft.util.NonNullList;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;


/**
 * Puts what a player picks up, and what the blocks they break drop, into the item portable cells they carry
 * with auto pickup on. The idea is NAE2's.
 */
public final class PortableCellPickup {

    /** Each player's cells as found this tick: a mining tool breaks a whole area in one tick, an event per block. */
    private final Map<EntityPlayer, FoundCells> found = new WeakHashMap<>();

    @SubscribeEvent
    public void onItemPickup(final EntityItemPickupEvent event) {
        final EntityPlayer player = event.getEntityPlayer();
        final EntityItem entity = event.getItem();
        final ItemStack stack = entity.getItem();
        if (player.world.isRemote || player instanceof FakePlayer || stack.isEmpty()) {
            return;
        }

        // Anyone may take someone else's item once it is about to despawn, but only the client knows its age,
        // so such an item is left to vanilla.
        final String owner = entity.getOwner();
        if (owner != null && !owner.equals(player.getName())) {
            return;
        }

        final int count = stack.getCount();
        final int stored = this.store(player, stack);
        if (stored <= 0) {
            return;
        }

        if (stored < count) {
            // Vanilla picks up the rest as usual.
            stack.shrink(stored);
            return;
        }

        // All of it went in, so do what vanilla does with an item it picked up whole.
        event.setCanceled(true);
        FMLCommonHandler.instance().firePlayerItemPickupEvent(player, entity, stack.copy());
        player.onItemPickup(entity, count);
        player.addStat(StatList.getObjectsPickedUpStats(stack.getItem()), count);
        entity.setDead();
    }

    // Last, so what is taken is the drop other mods have finished changing.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onHarvestDrops(final BlockEvent.HarvestDropsEvent event) {
        final EntityPlayer player = event.getHarvester();
        if (player == null || player instanceof FakePlayer || event.getWorld().isRemote) {
            return;
        }

        // The chance is rolled per drop after this event, so taking a drop here would hand out one the ground
        // might not have got.
        if (event.getDropChance() < 1.0F) {
            return;
        }

        boolean tookAny = false;
        for (final ItemStack drop : event.getDrops()) {
            if (!drop.isEmpty()) {
                final int stored = this.store(player, drop);
                // An emptied stack stays in the list; the block skips empty stacks when it spawns the drops.
                drop.shrink(stored);
                tookAny |= stored > 0;
            }
        }

        if (tookAny) {
            player.world.playSound(null, player.posX, player.posY, player.posZ, SoundEvents.ENTITY_ITEM_PICKUP,
                    SoundCategory.PLAYERS, 0.2F,
                    ((player.getRNG().nextFloat() - player.getRNG().nextFloat()) * 0.7F + 1.0F) * 2.0F);
        }
    }

    /**
     * Offers a stack to the player's cells: first those partitioned to it, then the rest, each group in
     * inventory order.
     *
     * @return how many of it went in
     */
    private int store(final EntityPlayer player, final ItemStack stack) {
        final AEItemKey key = AEItemKey.of(stack);
        if (key == null) {
            return 0;
        }

        final List<IPortableCell> cells = this.findCells(player);
        if (cells.isEmpty()) {
            return 0;
        }

        final PlayerSource source = new PlayerSource(player, null);
        long remaining = stack.getCount();
        for (final boolean preferred : new boolean[]{true, false}) {
            for (final IPortableCell cell : cells) {
                if (remaining > 0 && cell.isPreferredStorageFor(key, source) == preferred) {
                    remaining -= Platform.poweredInsert(cell, cell, key, remaining, source);
                }
            }
        }

        return (int) (stack.getCount() - remaining);
    }

    /**
     * The hotbar, then the rest of the inventory, then the offhand. Reading a cell's cards and partition builds
     * an item for every entry, so this is done once a tick, not once a block. Every inventory on a cell shares
     * its contents, so a window open on one of them sees what goes in.
     */
    private List<IPortableCell> findCells(final EntityPlayer player) {
        final long tick = player.world.getTotalWorldTime();
        final FoundCells cached = this.found.get(player);
        if (cached != null && cached.tick == tick && cached.isStillInPlace()) {
            return cached.cells;
        }

        final InventoryPlayer inventory = player.inventory;
        final FoundCells fresh = new FoundCells(tick);
        for (int slot = 0; slot < inventory.mainInventory.size(); slot++) {
            fresh.add(inventory.mainInventory, slot, slot);
        }
        for (int slot = 0; slot < inventory.offHandInventory.size(); slot++) {
            fresh.add(inventory.offHandInventory, slot, -1);
        }

        this.found.put(player, fresh);
        return fresh.cells;
    }

    private static final class FoundCells {
        private final long tick;
        private final List<IPortableCell> cells = new ArrayList<>();
        private final List<NonNullList<ItemStack>> lists = new ArrayList<>();
        private final List<ItemStack> stacks = new ArrayList<>();
        private final List<Integer> indices = new ArrayList<>();

        private FoundCells(final long tick) {
            this.tick = tick;
        }

        private void add(final NonNullList<ItemStack> list, final int index, final int viewerSlot) {
            final ItemStack stack = list.get(index);
            if (ToolPortableCell.isAutoPickupEnabled(stack)) {
                this.cells.add(new PortableCellViewer(stack, viewerSlot));
                this.lists.add(list);
                this.stacks.add(stack);
                this.indices.add(index);
            }
        }

        /** A cell moved within the tick would otherwise go on taking items from wherever it went. */
        private boolean isStillInPlace() {
            for (int i = 0; i < this.stacks.size(); i++) {
                if (this.lists.get(i).get(this.indices.get(i)) != this.stacks.get(i)) {
                    return false;
                }
            }
            return true;
        }
    }
}
