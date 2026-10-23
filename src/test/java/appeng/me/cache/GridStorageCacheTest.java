/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.me.cache;


import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.registries.RegistryBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import appeng.api.config.Actionable;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStackWatcher;
import appeng.api.networking.storage.IStorageWatcherNode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.core.api.AEItemKeyType;
import appeng.me.helpers.BaseActionSource;
import appeng.me.storage.MEInventoryHandler;


public final class GridStorageCacheTest {

    private static final IActionSource SOURCE = new BaseActionSource();

    private static final List<AEItemKey> keys = new ArrayList<>();

    @BeforeAll
    public static void bootstrap() throws Exception {
        Bootstrap.register();
        fakeServerSide();
        registerItemKeyType();
        for (int meta = 0; meta < 16; meta++) {
            keys.add(AEItemKey.of(new ItemStack(Items.DYE, 1, meta)));
        }
    }

    /**
     * A watcher that moves things, or reads the network, while it is being told about a change - a level
     * emitter driving an export bus, an interface restocking. That crashed the server tick: the batch was
     * walked again from inside itself and cleared under the outer walk.
     */
    @Test
    public void watcherThatMovesThingsDuringTheBatch() {
        final KeyCounter held = new KeyCounter();
        final GridStorageCache cache = cache(held);
        final MEStorage network = cache.getInventory();
        final AEItemKey moved = keys.get(15);

        final boolean[] acted = { false };
        cache.addNode(stubNode(), new Watcher(() -> {
            if (!acted[0]) {
                acted[0] = true;
                network.insert(moved, 1, Actionable.MODULATE, SOURCE);
                cache.getCachedInventory();
            }
        }));

        for (int i = 0; i < 12; i++) {
            network.insert(keys.get(i), 10, Actionable.MODULATE, SOURCE);
        }

        cache.onUpdateTick();
        cache.onUpdateTick();

        final KeyCounter cached = cache.getCachedInventory();
        for (final var entry : held) {
            assertThat(entry.getKey().toString(), cached.get(entry.getKey()), is(entry.getLongValue()));
        }
        assertThat(cached.size(), is(held.size()));
    }

    private static GridStorageCache cache(final KeyCounter held) {
        final GridStorageCache cache = new GridStorageCache(null);
        final MEStorage storage = new MEStorage() {
            @Override
            public long insert(final AEKey what, final long amount, final Actionable mode, final IActionSource src) {
                if (mode == Actionable.MODULATE) {
                    held.add(what, amount);
                }
                return amount;
            }

            @Override
            public void getAvailableStacks(final KeyCounter out) {
                out.addAll(held);
            }
        };
        cache.addGlobalStorageProvider(mount -> mount.mount(new MEInventoryHandler(storage), 0));
        cache.onUpdateTick();
        return cache;
    }

    private static final class Watcher implements IGridHost, IStorageWatcherNode {
        private final Runnable onChange;

        Watcher(final Runnable onChange) {
            this.onChange = onChange;
        }

        @Override
        public void updateWatcher(final IStackWatcher newWatcher) {
            newWatcher.setWatchAll(true);
        }

        @Override
        public void onStackChange(final AEKey what, final long amount) {
            this.onChange.run();
        }

        @Override
        public IGridNode getGridNode(@Nonnull final AEPartLocation dir) {
            return null;
        }

        @Override
        public AECableType getCableConnectionType(@Nonnull final AEPartLocation dir) {
            return AECableType.NONE;
        }

        @Override
        public void securityBreak() {
        }
    }

    private static IGridNode stubNode() {
        return (IGridNode) Proxy.newProxyInstance(IGridNode.class.getClassLoader(),
                new Class<?>[] { IGridNode.class }, (proxy, method, args) -> null);
    }

    private static void fakeServerSide() throws Exception {
        final Class<?> handlerType = Class.forName("net.minecraftforge.fml.common.IFMLSidedHandler");
        final Object delegate = Proxy.newProxyInstance(handlerType.getClassLoader(),
                new Class<?>[] { handlerType },
                (proxy, method, args) -> "getSide".equals(method.getName()) ? Side.SERVER : null);

        final Field field = FMLCommonHandler.class.getDeclaredField("sidedDelegate");
        field.setAccessible(true);
        field.set(FMLCommonHandler.instance(), delegate);
    }

    private static void registerItemKeyType() {
        if (GameRegistry.findRegistry(AEKeyType.class) != null) {
            return;
        }
        new RegistryBuilder<AEKeyType>()
                .setName(AEKeyTypes.REGISTRY_NAME)
                .setType(AEKeyType.class)
                .setIDRange(0, 127)
                .create();
        AEKeyTypes.register(new AEItemKeyType());
    }
}
