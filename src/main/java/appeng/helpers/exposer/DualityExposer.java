/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 * Adapted from NAE2's storage exposer (https://github.com/AE2-UEL/NAE2) by NotMyWing.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.helpers.exposer;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;

import appeng.api.behaviors.ExposedStorage;
import appeng.api.behaviors.StackWorldBehaviors;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStackWatcher;
import appeng.api.parts.IPartHost;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.util.AEPartLocation;
import appeng.core.localization.GuiText;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.MachineSource;
import appeng.parts.misc.PartExposer;
import appeng.tile.misc.TileExposer;
import appeng.util.Platform;

/**
 * What the exposer block and part share: one {@link ExposedStorage} per key type, kept from the grid's pushes
 * rather than recounted, and one capability handler per registered capability, built the first time it is asked
 * for. Server side only.
 */
public final class DualityExposer {

    private final AENetworkProxy proxy;
    private final IActionSource source;
    private final Map<AEKeyType, Keys> keys = new IdentityHashMap<>();
    private final Map<Capability<?>, Object> handlers = new IdentityHashMap<>();
    // A pipe that reaches back into this exposer while we are pulling for it would recurse.
    private boolean extracting;

    public DualityExposer(final AENetworkProxy proxy, final IActionHost host) {
        this.proxy = proxy;
        this.source = new MachineSource(host);
    }

    public static boolean exposes(final Capability<?> capability) {
        return StackWorldBehaviors.getExposedType(capability) != null;
    }

    /**
     * A storage bus on an exposer would count its own network a second time on every rescan.
     */
    public static boolean isExposer(final TileEntity tile, final EnumFacing side) {
        if (tile instanceof TileExposer) {
            return true;
        }
        return tile instanceof IPartHost
                && ((IPartHost) tile).getPart(AEPartLocation.fromFacing(side)) instanceof PartExposer;
    }

    public static void addTooltip(final List<String> lines) {
        lines.add(GuiText.ExposerTooltip.getLocal());
        final Set<AEKeyType> types = StackWorldBehaviors.withExposerStrategy();
        if (types.isEmpty()) {
            lines.add(GuiText.ExposerNoTypes.getLocal());
            return;
        }
        lines.add(GuiText.ExposerTypes.getLocal());
        for (final AEKeyType type : types) {
            lines.add(" - " + type.getDescription().getFormattedText());
        }
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public <T> T getCapability(final Capability<T> capability) {
        final AEKeyType type = StackWorldBehaviors.getExposedType(capability);
        if (type == null) {
            return null;
        }
        return (T) this.handlers.computeIfAbsent(capability,
                c -> StackWorldBehaviors.createExposer(capability, this.keys.computeIfAbsent(type, Keys::new)));
    }

    /**
     * Called again whenever the node changes grid, so everything counted so far belongs to the old one.
     */
    public void updateWatcher(@Nullable final IStackWatcher watcher) {
        if (watcher != null) {
            watcher.setWatchAll(true);
        }
        for (final Keys list : this.keys.values()) {
            list.stale = true;
        }
    }

    public void onStackChange(final AEKey what, final long amount) {
        final Keys list = this.keys.get(what.getType());
        if (list != null && !list.stale) {
            list.set(what, amount);
        }
    }

    private final class Keys implements ExposedStorage {

        private final AEKeyType type;
        private final List<AEKey> order = new ArrayList<>();
        private final Object2LongOpenHashMap<AEKey> amounts = new Object2LongOpenHashMap<>();
        private boolean stale = true;
        private int version;

        private Keys(final AEKeyType type) {
            this.type = type;
        }

        private boolean ready() {
            if (!DualityExposer.this.proxy.isActive()) {
                return false;
            }
            if (this.stale) {
                try {
                    this.order.clear();
                    this.amounts.clear();
                    for (final Object2LongMap.Entry<AEKey> entry : DualityExposer.this.proxy.getStorage().getCachedInventory()) {
                        if (entry.getKey().getType() == this.type && entry.getLongValue() > 0) {
                            this.order.add(entry.getKey());
                            this.amounts.put(entry.getKey(), entry.getLongValue());
                        }
                    }
                    this.stale = false;
                    this.version++;
                } catch (final GridAccessException e) {
                    return false;
                }
            }
            return true;
        }

        private void set(final AEKey key, final long amount) {
            if (amount > 0) {
                if (!this.amounts.containsKey(key)) {
                    this.order.add(key);
                }
                this.amounts.put(key, amount);
            } else if (this.amounts.containsKey(key)) {
                this.amounts.removeLong(key);
                this.order.remove(key);
            }
            this.version++;
        }

        @Override
        public int size() {
            return this.ready() ? this.order.size() : 0;
        }

        @Nullable
        @Override
        public AEKey getKey(final int index) {
            return this.ready() && index >= 0 && index < this.order.size() ? this.order.get(index) : null;
        }

        @Override
        public long getAmount(final AEKey key) {
            return this.ready() ? this.amounts.getLong(key) : 0;
        }

        @Override
        public long extract(final AEKey key, final long amount, final Actionable mode) {
            if (amount <= 0 || DualityExposer.this.extracting || !this.ready()) {
                return 0;
            }

            DualityExposer.this.extracting = true;
            try {
                final long taken = Platform.poweredExtraction(DualityExposer.this.proxy.getEnergy(),
                        DualityExposer.this.proxy.getStorage().getInventory(), key, amount,
                        DualityExposer.this.source, mode);
                // The grid's own count arrives at the end of the tick; until then the next caller sees this.
                if (taken > 0 && mode == Actionable.MODULATE) {
                    this.set(key, this.amounts.getLong(key) - taken);
                    if (this.amounts.containsKey(key) && this.order.get(0) != key) {
                        this.order.remove(key);
                        this.order.add(0, key);
                    }
                }
                return taken;
            } catch (final GridAccessException e) {
                return 0;
            } finally {
                DualityExposer.this.extracting = false;
            }
        }

        @Override
        public int getVersion() {
            return this.version;
        }
    }
}
