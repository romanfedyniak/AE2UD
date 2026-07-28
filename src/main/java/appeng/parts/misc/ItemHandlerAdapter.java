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

package appeng.parts.misc;


import appeng.api.behaviors.ExternalStorageStrategy;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.core.AELog;
import appeng.me.storage.ITickingMonitor;
import appeng.util.inv.ItemHandlerIterator;
import appeng.util.inv.ItemSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nullable;


/**
 * Wraps a plain {@link IItemHandler} (any inventory that only exposes the vanilla Forge capability) so it can be
 * used as an {@link MEStorage} - the role {@code IMEInventory<IAEItemStack>} used to play for a storage bus.
 * <p/>
 * This is also the reference {@link ExternalStorageStrategy} for {@link appeng.api.stacks.AEKeyType#items()},
 * registered by {@link InitExternalStorageStrategies}. A fluid (wave 5) or addon strategy is expected to follow
 * the exact same shape: a small {@code Factory} that looks the capability up fresh in {@link #createWrapper} so
 * {@link PartStorageBus} can hold onto the {@code Factory} for the part's whole lifetime and only re-resolve the
 * capability when it actually needs to.
 */
class ItemHandlerAdapter implements MEStorage, ITickingMonitor {
    private final IItemHandler itemHandler;
    private final boolean extractableOnly;
    @Nullable
    private final Runnable changeListener;
    private KeyCounter currentlyCached = new KeyCounter();

    ItemHandlerAdapter(final IItemHandler itemHandler, final boolean extractableOnly, @Nullable final Runnable changeListener) {
        this.itemHandler = itemHandler;
        this.extractableOnly = extractableOnly;
        this.changeListener = changeListener;
        this.updateCache();
    }

    @Override
    public long insert(final AEKey what, final long amount, final Actionable mode, final IActionSource source) {
        if (!(what instanceof AEItemKey itemKey) || amount <= 0) {
            return 0;
        }

        final int toInsertCount = (int) Math.min(amount, Integer.MAX_VALUE);
        ItemStack remaining = itemKey.toStack(toInsertCount);

        final int slotCount = this.itemHandler.getSlots();
        for (int i = 0; i < slotCount && !remaining.isEmpty(); i++) {
            remaining = this.itemHandler.insertItem(i, remaining, mode == Actionable.SIMULATE);
        }

        final long inserted = toInsertCount - remaining.getCount();

        if (inserted > 0 && mode == Actionable.MODULATE) {
            this.updateCache();
            this.notifyChange();
        }

        return inserted;
    }

    @Override
    public long extract(final AEKey what, final long amount, final Actionable mode, final IActionSource source) {
        if (!(what instanceof AEItemKey itemKey) || amount <= 0) {
            return 0;
        }

        final boolean simulate = mode == Actionable.SIMULATE;
        long remainingToExtract = amount;
        long extracted = 0;

        for (int i = 0; i < this.itemHandler.getSlots() && remainingToExtract > 0; i++) {
            final ItemStack stackInSlot = this.itemHandler.getStackInSlot(i);
            if (!itemKey.matches(stackInSlot)) {
                continue;
            }

            final int wanted = (int) Math.min(remainingToExtract, stackInSlot.getCount());
            int gatheredThisSlot = 0;
            ItemStack pulled;

            // We have to loop here because according to the docs, the handler shouldn't return a stack with size >
            // maxSize, even if we request more. So even if it returns a valid stack, it might have more stuff.
            do {
                final int remainingWanted = wanted - gatheredThisSlot;
                pulled = this.itemHandler.extractItem(i, remainingWanted, simulate);
                if (!pulled.isEmpty()) {
                    if (pulled == stackInSlot) {
                        // Guard against broken IItemHandler implementations that return the live stack instead of a
                        // copy (see https://github.com/MinecraftForge/MinecraftForge/pull/6580).
                        pulled = pulled.copy();
                    }

                    if (pulled.getCount() > remainingWanted) {
                        // Something broke. It should never return more than we requested. Silently eat the remainder.
                        AELog.warn("Mod that provided item handler %s is broken. Returned %s items while only requesting %d.",
                                this.itemHandler.getClass().getName(), pulled, remainingWanted);
                        pulled.setCount(remainingWanted);
                    }

                    // Heuristic for simulation: looping is pointless since a simulated extraction doesn't change the
                    // underlying inventory. To still support handlers that report stacks larger than maxStackSize,
                    // assume the full amount is available if a simulated pull already maxed out a single stack.
                    if (simulate && pulled.getCount() == pulled.getMaxStackSize() && remainingWanted > pulled.getMaxStackSize()) {
                        pulled.setCount(remainingWanted);
                    }

                    gatheredThisSlot += pulled.getCount();
                }
            } while (!simulate && !pulled.isEmpty() && gatheredThisSlot < wanted);

            extracted += gatheredThisSlot;
            remainingToExtract -= gatheredThisSlot;
        }

        if (extracted > 0 && mode == Actionable.MODULATE) {
            this.updateCache();
            this.notifyChange();
        }

        return extracted;
    }

    @Override
    public void getAvailableStacks(final KeyCounter out) {
        for (final var entry : this.currentlyCached) {
            out.add(entry.getKey(), entry.getLongValue());
        }
    }

    @Override
    public TickRateModulation onTick() {
        final KeyCounter before = this.currentlyCached;
        this.updateCache();
        return keyCountersEqual(before, this.currentlyCached) ? TickRateModulation.SLOWER : TickRateModulation.URGENT;
    }

    private void notifyChange() {
        if (this.changeListener != null) {
            this.changeListener.run();
        }
    }

    private void updateCache() {
        final KeyCounter fresh = new KeyCounter();
        final var it = new ItemHandlerIterator(this.itemHandler);
        while (it.hasNext()) {
            final ItemSlot slot = it.next();
            if (this.extractableOnly && !slot.isExtractable()) {
                continue;
            }
            final GenericStack stack = slot.getGenericStack();
            if (stack != null) {
                fresh.add(stack.what(), stack.amount());
            }
        }
        this.currentlyCached = fresh;
    }

    private static boolean keyCountersEqual(final KeyCounter a, final KeyCounter b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (final var entry : a) {
            if (b.get(entry.getKey()) != entry.getLongValue()) {
                return false;
            }
        }
        return true;
    }

    /**
     * The {@link ExternalStorageStrategy} for {@link appeng.api.stacks.AEKeyType#items()}. Registered by
     * {@link InitExternalStorageStrategies#register()}; {@link PartStorageBus} obtains it (and, from wave 5
     * onward, the fluid equivalent) through {@link appeng.api.behaviors.StackWorldBehaviors#createExternalStorageStrategies}
     * rather than talking to this class directly.
     */
    static final class Strategy implements ExternalStorageStrategy {
        private final World world;
        private final BlockPos fromPos;
        private final EnumFacing fromSide;

        Strategy(final World world, final BlockPos fromPos, final EnumFacing fromSide) {
            this.world = world;
            this.fromPos = fromPos;
            this.fromSide = fromSide;
        }

        @Override
        @Nullable
        public MEStorage createWrapper(final boolean extractableOnly, final Runnable injectOrExtractCallback) {
            final TileEntity target = this.world.getTileEntity(this.fromPos);
            if (target == null) {
                return null;
            }

            final IItemHandler itemHandler = target.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, this.fromSide);
            if (itemHandler == null) {
                return null;
            }

            return new ItemHandlerAdapter(itemHandler, extractableOnly, injectOrExtractCallback);
        }
    }
}
