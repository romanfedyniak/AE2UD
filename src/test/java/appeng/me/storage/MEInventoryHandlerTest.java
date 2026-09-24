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


import java.util.Collections;
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import appeng.api.config.IncludeExclude;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageChangeSource;
import appeng.api.storage.MEStorage;
import appeng.util.prioritylist.IPartitionList;


public final class MEInventoryHandlerTest {

    private static final AEKeyType TYPE = new TestKeyType();
    private static final AEKey FILTERED = new TestKey("filtered");
    private static final AEKey OTHER = new TestKey("other");

    /** A storage that reports its own changes and nothing else. */
    private static final class ReportingStorage implements MEStorage, IStorageChangeSource {
        final StorageChangeListeners listeners = new StorageChangeListeners();
        final KeyCounter held = new KeyCounter();

        @Override
        public void getAvailableStacks(final KeyCounter out) {
            out.addAll(this.held);
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

    /**
     * A partitioned storage bus that reports only what it can hand out lists only the partitioned key, and a
     * change to anything else underneath - a tube filling a jar with the wrong aspect - used to be passed on to
     * the network all the same.
     */
    @Test
    public void aHiddenKeyChangingUnderneathIsNotCounted() {
        final ReportingStorage underneath = new ReportingStorage();
        final MEInventoryHandler handler = new MEInventoryHandler(underneath);
        handler.setWhitelist(IncludeExclude.WHITELIST);
        handler.setPartitionList(new SingleKeyList(FILTERED));
        handler.setExtractFiltering(true, true);

        final AtomicLong counted = new AtomicLong();
        handler.addChangeListener((what, delta) -> counted.addAndGet(delta));

        underneath.listeners.post(OTHER, 5);
        underneath.listeners.post(FILTERED, 3);

        assertThat(counted.get(), is(3L));
    }

    /** With inaccessible keys reported, everything underneath is shown, so every change counts. */
    @Test
    public void aReportedInaccessibleKeyIsCounted() {
        final ReportingStorage underneath = new ReportingStorage();
        final MEInventoryHandler handler = new MEInventoryHandler(underneath);
        handler.setWhitelist(IncludeExclude.WHITELIST);
        handler.setPartitionList(new SingleKeyList(FILTERED));
        handler.setExtractFiltering(true, false);

        final AtomicLong counted = new AtomicLong();
        handler.addChangeListener((what, delta) -> counted.addAndGet(delta));

        underneath.listeners.post(OTHER, 5);

        assertThat(counted.get(), is(5L));
    }

    /**
     * Setting a filter hides what it leaves out, and the network has to hear about it: the storage underneath
     * did not change, so nothing else would tell it.
     */
    @Test
    public void aNewFilterTakesWhatItHidesOffTheNetwork() {
        final ReportingStorage underneath = new ReportingStorage();
        underneath.held.add(OTHER, 5);
        final MEInventoryHandler handler = new MEInventoryHandler(underneath);

        final AtomicLong counted = new AtomicLong(5);
        handler.addChangeListener((what, delta) -> counted.addAndGet(delta));

        handler.reconfigure(() -> {
            handler.setWhitelist(IncludeExclude.WHITELIST);
            handler.setPartitionList(new SingleKeyList(FILTERED));
            handler.setExtractFiltering(true, true);
        });

        assertThat(counted.get(), is(0L));
    }

    private static final class SingleKeyList implements IPartitionList {
        private final AEKey key;

        private SingleKeyList(final AEKey key) {
            this.key = key;
        }

        @Override
        public boolean isListed(final AEKey input) {
            return this.key.equals(input);
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public Iterable<AEKey> getItems() {
            return Collections.singletonList(this.key);
        }
    }

    private static final class TestKey extends AEKey {
        private final String name;

        private TestKey(final String name) {
            this.name = name;
        }

        @Override
        public AEKeyType getType() {
            return TYPE;
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
        private TestKeyType() {
            super(new ResourceLocation("test", "keys"), TestKey.class, new TextComponentString("keys"));
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
