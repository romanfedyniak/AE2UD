/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.me.storage;


import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nonnull;

import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageChangeSource;
import appeng.api.storage.MEStorage;
import appeng.me.helpers.BaseActionSource;


/**
 * Who hears about a change, and how often: networks mounted on each other through a storage bus, a bus that
 * hides some key types, and a storage that says it does not report.
 */
public final class StorageChangeReportsTest {

    private static final IActionSource SOURCE = new BaseActionSource();
    private static final AEKeyType ITEMS = new TestKeyType("items");
    private static final AEKeyType GASES = new TestKeyType("gases");
    private static final AEKey STONE = new TestKey("stone", ITEMS);
    private static final AEKey STEAM = new TestKey("steam", GASES);

    /** A cell: keeps what it is given and says so. */
    private static final class Cell implements MEStorage, IStorageChangeSource {
        final StorageChangeListeners listeners = new StorageChangeListeners();
        final KeyCounter held = new KeyCounter();
        final boolean reports;

        Cell(final boolean reports) {
            this.reports = reports;
        }

        @Override
        public long insert(final AEKey what, final long amount, final Actionable mode, final IActionSource source) {
            if (mode == Actionable.MODULATE) {
                this.held.add(what, amount);
                this.listeners.post(what, amount);
            }
            return amount;
        }

        @Override
        public void getAvailableStacks(final KeyCounter out) {
            out.addAll(this.held);
        }

        @Override
        public boolean reportsChanges() {
            return this.reports;
        }

        @Override
        public void addChangeListener(final Listener listener) {
            this.listeners.addChangeListener(listener);
        }

        @Override
        public void removeChangeListener(final Listener listener) {
            this.listeners.removeChangeListener(listener);
        }
    }

    /** A network with a cell, and a running total the way the storage service keeps one. */
    private static final class Network {
        final NetworkStorage storage = new NetworkStorage();
        final AtomicLong total = new AtomicLong();

        Network() {
            this.storage.addChangeListener((what, delta) -> this.total.addAndGet(delta));
            this.storage.mount(0, new Cell(true));
        }

        /** A storage bus on this network facing an unconfigured interface of {@code other}. */
        void busOn(final Network other) {
            this.storage.mount(1, new MEInventoryHandler(new ReportingStorageView(other.storage)));
        }
    }

    @Test
    public void aBusOnAnInterfaceOfItsOwnNetworkCountsOnce() {
        final Network a = new Network();
        a.busOn(a);

        assertEquals(5, a.storage.insert(STONE, 5, Actionable.MODULATE, SOURCE));
        assertEquals(5, a.total.get());
    }

    @Test
    public void twoNetworksMountedOnEachOtherCountOnce() {
        final Network a = new Network();
        final Network b = new Network();
        a.busOn(b);
        b.busOn(a);

        a.storage.insert(STONE, 5, Actionable.MODULATE, SOURCE);
        assertEquals(5, a.total.get());
        assertEquals(5, b.total.get());
    }

    @Test
    public void aKeyTypeTheBusHidesIsNotReported() {
        final Cell underneath = new Cell(true);
        final KeyTypeFilteredStorage filtered = new KeyTypeFilteredStorage(underneath, type -> type == ITEMS);
        final AtomicLong counted = new AtomicLong();
        filtered.addChangeListener((what, delta) -> counted.addAndGet(delta));

        underneath.insert(STEAM, 7, Actionable.MODULATE, SOURCE);
        underneath.insert(STONE, 3, Actionable.MODULATE, SOURCE);

        assertEquals(3, counted.get());
    }

    /**
     * A storage made of parts, one of which does not report: it says it does not report, so the handler reports
     * for it, and must then not also pass on what the reporting parts say.
     */
    @Test
    public void aStorageThatSaysItDoesNotReportIsCountedOnce() {
        final Cell underneath = new Cell(false);
        final MEInventoryHandler handler = new MEInventoryHandler(underneath);
        final AtomicLong counted = new AtomicLong();
        handler.addChangeListener((what, delta) -> counted.addAndGet(delta));

        handler.insert(STONE, 4, Actionable.MODULATE, SOURCE);

        assertEquals(4, counted.get());
    }

    private static final class TestKey extends AEKey {
        private final String name;
        private final AEKeyType type;

        private TestKey(final String name, final AEKeyType type) {
            this.name = name;
            this.type = type;
        }

        @Override
        public AEKeyType getType() {
            return this.type;
        }

        @Override
        public ResourceLocation getId() {
            return new ResourceLocation("test", this.name);
        }

        @Override
        public Object getPrimaryKey() {
            return this.name;
        }

        @Override
        public AEKey dropSecondary() {
            return this;
        }

        @Override
        public void toTag(final NBTTagCompound tag) {
        }

        @Override
        public void writeToPacket(final ByteBuf data) {
        }

        @Override
        protected ITextComponent computeDisplayName() {
            return new TextComponentString(this.name);
        }

        @Override
        public void addDrops(final long amount, final List<ItemStack> drops, @Nonnull final World world,
                @Nonnull final BlockPos pos) {
        }
    }

    private static final class TestKeyType extends AEKeyType {
        private TestKeyType(final String name) {
            super(new ResourceLocation("test", name), TestKey.class, new TextComponentString(name));
        }

        @Override
        public AEKey readFromPacket(@Nonnull final ByteBuf input) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AEKey loadKeyFromTag(@Nonnull final NBTTagCompound tag) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResourceLocation getButtonTexture() {
            return new ResourceLocation("test", "button");
        }

        @Override
        public ItemStack getButtonIcon() {
            return ItemStack.EMPTY;
        }
    }
}
