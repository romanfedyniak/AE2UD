/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.api.behaviors;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraftforge.common.capabilities.Capability;

import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;

/**
 * Turns a machine's {@link GenericInternalInventory} into the handlers other mods ask a block for: an item
 * handler, a fluid handler, or whatever an addon registers for its own key type.
 * <p>
 * Registration replaces writing another case into every machine that holds stock. It happens once during
 * mod initialisation, before a world is loaded, and a capability that is registered twice keeps its first
 * factory.
 * <p>
 * A factory's handler is built once per inventory and then handed to everyone (see {@link Cache}), because
 * pipes ask for a neighbour's handler every tick and on all six sides. So a handler must be a live view on
 * the inventory it was given and must hold no state of its own between calls.
 */
public final class GenericInventoryAdapters {

    /**
     * Builds one handler over a machine's stock.
     *
     * @param <T> the handler the capability hands out.
     */
    @FunctionalInterface
    public interface Factory<T> {
        /**
         * @param inventory the machine's own slots.
         * @param network   the network behind it, for a handler that puts what it is given into storage
         *                  rather than into a slot. Answers null when the machine is offline or has no
         *                  network.
         * @param source    who the machine acts as when it reaches the network.
         */
        T create(GenericInternalInventory inventory, Supplier<IStorageService> network, IActionSource source);
    }

    private static final Map<Capability<?>, Integer> INDICES = new IdentityHashMap<>();
    private static volatile Factory<?>[] factories = new Factory<?>[0];

    private GenericInventoryAdapters() {
    }

    /**
     * @return false if that capability already has a factory, which is then left in place.
     */
    public static synchronized <T> boolean register(final Capability<T> capability, final Factory<T> factory) {
        if (capability == null || INDICES.containsKey(capability)) {
            return false;
        }

        final Factory<?>[] grown = new Factory<?>[factories.length + 1];
        System.arraycopy(factories, 0, grown, 0, factories.length);
        grown[factories.length] = factory;

        INDICES.put(capability, factories.length);
        factories = grown;
        return true;
    }

    /**
     * Whether anything can build that capability out of a machine's stock. This is the whole of what a
     * machine's {@code hasCapability} needs to ask.
     */
    public static boolean isRegistered(@Nullable final Capability<?> capability) {
        return capability != null && INDICES.containsKey(capability);
    }

    /**
     * One machine's handlers, built as they are first asked for and kept afterwards.
     * <p>
     * A machine holds one of these beside its inventory and answers {@code getCapability} out of it.
     */
    public static final class Cache {

        private final GenericInternalInventory inventory;
        private final Supplier<IStorageService> network;
        private final IActionSource source;

        private Object[] adapters = new Object[0];

        public Cache(final GenericInternalInventory inventory, final Supplier<IStorageService> network,
                final IActionSource source) {
            this.inventory = inventory;
            this.network = network;
            this.source = source;
        }

        /**
         * @return null if nothing is registered for that capability.
         */
        @SuppressWarnings("unchecked")
        @Nullable
        public <T> T get(@Nullable final Capability<T> capability) {
            final Integer index = capability == null ? null : INDICES.get(capability);
            if (index == null) {
                return null;
            }

            if (index >= this.adapters.length) {
                final Object[] grown = new Object[factories.length];
                System.arraycopy(this.adapters, 0, grown, 0, this.adapters.length);
                this.adapters = grown;
            }

            Object adapter = this.adapters[index];
            if (adapter == null) {
                adapter = ((Factory<T>) factories[index]).create(this.inventory, this.network, this.source);
                this.adapters[index] = adapter;
            }
            return (T) adapter;
        }
    }
}
