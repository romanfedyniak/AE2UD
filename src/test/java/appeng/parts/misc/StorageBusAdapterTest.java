/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.parts.misc;


import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import javax.annotation.Nonnull;

import com.jaquadro.minecraft.storagedrawers.api.capabilities.IItemRepository;

import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.items.ItemStackHandler;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.me.helpers.BaseActionSource;
import appeng.me.storage.MEInventoryHandler;


/**
 * What a storage bus's adapters cost and report: the network's total after the drawers gave more than it knew,
 * the price of asking a big inventory whether it holds something, and the rescans its own moves set off.
 */
public final class StorageBusAdapterTest {

    private static final IActionSource SOURCE = new BaseActionSource();

    private static AEItemKey apple;
    private static AEItemKey stick;

    @BeforeAll
    public static void bootstrap() {
        Bootstrap.register();
        apple = AEItemKey.of(Items.APPLE);
        stick = AEItemKey.of(Items.STICK);
    }

    /** One drawer of apples, its count set from outside the way a hopper fills it. */
    private static final class Drawer implements IItemRepository {
        int count;

        @Nonnull
        @Override
        public NonNullList<ItemRecord> getAllItems() {
            final NonNullList<ItemRecord> all = NonNullList.create();
            if (this.count > 0) {
                all.add(new ItemRecord(new ItemStack(Items.APPLE), this.count));
            }
            return all;
        }

        @Nonnull
        @Override
        public ItemStack insertItem(@Nonnull final ItemStack stack, final boolean simulate, final Predicate<ItemStack> predicate) {
            return stack;
        }

        @Nonnull
        @Override
        public ItemStack extractItem(@Nonnull final ItemStack stack, final int amount, final boolean simulate,
                final Predicate<ItemStack> predicate) {
            if (stack.getItem() != Items.APPLE) {
                return ItemStack.EMPTY;
            }
            final int taken = Math.min(amount, this.count);
            if (!simulate) {
                this.count -= taken;
            }
            return taken == 0 ? ItemStack.EMPTY : new ItemStack(Items.APPLE, taken);
        }
    }

    @Test
    public void takingMoreThanTheDrawersWereKnownToHoldKeepsTheTotalRight() {
        final Drawer drawer = new Drawer();
        drawer.count = 5;
        final ItemRepositoryAdapter adapter = new ItemRepositoryAdapter(drawer);
        final AtomicLong total = new AtomicLong(adapter.getAvailableStacks().get(apple));
        adapter.addChangeListener((what, delta) -> total.addAndGet(delta));

        drawer.count = 15;
        assertEquals(15, adapter.extract(apple, 15, Actionable.MODULATE, SOURCE));
        adapter.onTick();

        assertEquals(0, total.get());
    }

    /** A chest of many slots that counts how often a slot is looked at. */
    private static final class CountingChest extends ItemStackHandler {
        final AtomicInteger looks = new AtomicInteger();
        final AtomicInteger simulatedTakes = new AtomicInteger();

        CountingChest(final int slots) {
            super(slots);
        }

        @Nonnull
        @Override
        public ItemStack getStackInSlot(final int slot) {
            this.looks.incrementAndGet();
            return super.getStackInSlot(slot);
        }

        @Nonnull
        @Override
        public ItemStack extractItem(final int slot, final int amount, final boolean simulate) {
            if (simulate) {
                this.simulatedTakes.incrementAndGet();
            }
            return super.extractItem(slot, amount, simulate);
        }
    }

    @Test
    public void askingWhetherItHoldsSomethingDoesNotWalkTheSlots() {
        final CountingChest chest = new CountingChest(1000);
        chest.setStackInSlot(500, new ItemStack(Items.APPLE, 3));
        final MEInventoryHandler bus = new MEInventoryHandler(new ItemHandlerAdapter(chest, false, null));

        chest.looks.set(0);
        assertFalse(bus.isPreferredStorageFor(stick, SOURCE));
        assertTrue(bus.isPreferredStorageFor(apple, SOURCE));
        assertEquals(0, chest.looks.get());
    }

    @Test
    public void itsOwnMovesDoNotWakeTheBus() {
        final CountingChest chest = new CountingChest(27);
        final AtomicInteger wakes = new AtomicInteger();
        final ItemHandlerAdapter adapter = new ItemHandlerAdapter(chest, false, wakes::incrementAndGet);

        adapter.insert(apple, 10, Actionable.MODULATE, SOURCE);
        adapter.extract(apple, 4, Actionable.MODULATE, SOURCE);
        assertEquals(0, wakes.get());
        assertEquals(6, adapter.getAvailableStacks().get(apple));
    }

    @Test
    public void aRescanOfEverythingTakesNothingOutToSee() {
        final CountingChest chest = new CountingChest(27);
        chest.setStackInSlot(3, new ItemStack(Items.APPLE, 3));
        final ItemHandlerAdapter adapter = new ItemHandlerAdapter(chest, false, null);

        chest.simulatedTakes.set(0);
        adapter.onTick();
        assertEquals(0, chest.simulatedTakes.get());
        assertEquals(3, adapter.getAvailableStacks().get(apple));
    }
}
