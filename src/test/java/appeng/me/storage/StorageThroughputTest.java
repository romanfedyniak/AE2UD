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


import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.LongConsumer;

import javax.annotation.Nonnull;

import net.minecraft.init.Bootstrap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.RegistryBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
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
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.api.storage.cells.IBasicCellItem;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import appeng.core.api.AEItemKeyType;
import appeng.me.cache.GridStorageCache;
import appeng.me.helpers.BaseActionSource;
import appeng.util.inv.AdaptorItemHandler;


/**
 * What it costs to put an item into an ME network, to take one out, and to leave the network standing there
 * doing neither. Prints a table; asserts nothing yet.
 * <p/>
 * The shape being measured is the one the owner asked about: several servers report that item throughput
 * falls apart on large networks, and the first suspicion was that a storage cell rewrites its NBT on every
 * operation. Rather than argue about it, every candidate is driven directly and timed in one run, so the
 * numbers can be compared with each other instead of with a guess.
 */
public final class StorageThroughputTest {

    /** A full cell's worth of types, which is what {@code BasicCellInventory} caps at. */
    private static final int TYPES_PER_MOUNT = 63;

    private static final int[] MOUNT_LADDER = { 1, 10, 100, 1000 };

    private static final IActionSource SOURCE = new BaseActionSource();

    /** Budgets are written in these so that a number in the test reads as the time it is. */
    private static final long MICROSECOND = 1000;

    private static final int WARMUP_ROUNDS = 3;
    private static final int MEASURED_ROUNDS = 5;

    private static List<AEItemKey> keyPool;

    @BeforeAll
    public static void bootstrap() throws Exception {
        Bootstrap.register();
        fakeServerSide();
        registerItemKeyType();
        keyPool = buildKeyPool(MOUNT_LADDER[MOUNT_LADDER.length - 1] * TYPES_PER_MOUNT + 16);
        warmUp();
    }

    /**
     * Drives every path once at a size too small to be worth timing, so that the first row of each table is
     * not the one paying to load and compile it.
     */
    private static void warmUp() {
        final StorageThroughputTest bench = new StorageThroughputTest();

        for (int round = 0; round < 200; round++) {
            final Fixture fixture = bench.new Fixture(4, true, false);
            fixture.storage.insert(keyPool.get(0), 1, Actionable.MODULATE, SOURCE);
            fixture.storage.extract(keyPool.get(0), 1, Actionable.MODULATE, SOURCE);
            fixture.storage.extract(keyPool.get(keyPool.size() - 1), 1, Actionable.SIMULATE, SOURCE);

            bench.cache(fixture, Watch.ONE_KEY).onUpdateTick();
            bench.cell(4, null).insert(keyPool.get(0), 1, Actionable.MODULATE, SOURCE);
            bench.cell(4, () -> {}).insert(keyPool.get(0), 1, Actionable.MODULATE, SOURCE);
        }

        final ItemStackHandler handler = new ItemStackHandler(9);
        for (int slot = 0; slot < 9; slot++) {
            handler.setStackInSlot(slot, keyPool.get(slot).toStack(1));
        }
        final MEMonitorIInventory monitor = new MEMonitorIInventory(new AdaptorItemHandler(handler));
        for (int round = 0; round < 200; round++) {
            monitor.onTick();
        }
    }

    // ------------------------------------------------------------------------------------------------
    // 1. Moving one item through the network
    // ------------------------------------------------------------------------------------------------

    @Test
    public void networkInsertAndExtract() {
        heading("1. NetworkStorage: one operation, in nanoseconds");
        row("mounts", "keys", "spread", "insert (accepted)", "insert (all full)", "extract (absent)");

        for (final boolean unique : new boolean[] { true, false }) {
            for (final int mounts : MOUNT_LADDER) {
                final Fixture open = new Fixture(mounts, unique, false);
                final Fixture full = new Fixture(mounts, unique, true);

                final AEKey wanted = keyPool.get(0);
                final AEKey absent = keyPool.get(keyPool.size() - 1);

                final long accepted = time(ops(mounts),
                        i -> open.storage.insert(wanted, 1, Actionable.MODULATE, SOURCE));
                final long refused = time(ops(mounts),
                        i -> full.storage.insert(wanted, 1, Actionable.MODULATE, SOURCE));
                final long missing = time(ops(mounts),
                        i -> open.storage.extract(absent, 1, Actionable.SIMULATE, SOURCE));

                row(mounts, open.distinctKeys(), unique ? "unique" : "shared", accepted, refused, missing);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------
    // 2. The tick a network pays for standing still
    // ------------------------------------------------------------------------------------------------

    @Test
    public void idleTick() {
        heading("2. GridStorageCache.onUpdateTick: one tick, in nanoseconds");
        row("mounts", "keys", "spread", "idle", "one watched key", "watch-all", "after 100 moves");

        for (final boolean unique : new boolean[] { true, false }) {
            for (final int mounts : MOUNT_LADDER) {
                final long unwatched = timeTick(mounts, unique, Watch.NONE, 0);
                final long oneKey = timeTick(mounts, unique, Watch.ONE_KEY, 0);
                final long all = timeTick(mounts, unique, Watch.EVERYTHING, 0);
                final long busy = timeTick(mounts, unique, Watch.ONE_KEY, 100);

                row(mounts, unique ? mounts * TYPES_PER_MOUNT : TYPES_PER_MOUNT,
                        unique ? "unique" : "shared", unwatched, oneKey, all, busy);
            }
        }
    }

    /**
     * The two claims worth a failing build, both of them about the same thing: what a tick costs must follow
     * what moved, not what is stored.
     * <p/>
     * Stated as loose absolute ceilings rather than as a ratio, which is what a first draft of this reached
     * for. An idle tick now costs tens of nanoseconds, and at that size the timer's own overhead is most of
     * the reading, so any ratio built from it swings by a factor of two between runs and says nothing. The
     * shape being guarded against - counting the whole network every tick - was twenty-five milliseconds on
     * the network below, so a ceiling three orders of magnitude above the real cost still catches it on any
     * machine, and will not turn red because the build server is busy.
     */
    @Test
    public void aTickCostsWhatMovedAndNotWhatIsStored() {
        final int mounts = MOUNT_LADDER[MOUNT_LADDER.length - 1];

        final long idle = timeTick(mounts, true, Watch.ONE_KEY, 0);
        final long busy = timeTick(mounts, true, Watch.ONE_KEY, 100);

        System.out.println("gate: idle " + idle + "ns, after 100 moves " + busy + "ns");

        assertThat("an idle tick on " + mounts + " mounts", idle, lessThan(MICROSECOND * 100));
        assertThat("a tick after 100 moves", busy, lessThan(MICROSECOND * 2000));
    }

    /**
     * The claim the whole change rests on: a running total kept from what moved says the same as counting
     * everything. Both ways a mount changes are exercised - through the network, and by whoever owns the mount
     * writing to it directly, which is what a hopper filling an ME Chest does.
     */
    @Test
    public void theRunningTotalMatchesARecount() {
        final Fixture fixture = new Fixture(20, true, false);
        final GridStorageCache cache = cache(fixture, Watch.ONE_KEY);
        final Random random = new Random(1234);

        for (int step = 0; step < 20000; step++) {
            final AEKey what = keyPool.get(random.nextInt(TYPES_PER_MOUNT * 25));

            switch (step % 4) {
                case 0 -> cache.getInventory().insert(what, 1 + random.nextInt(64), Actionable.MODULATE, SOURCE);
                case 1 -> cache.getInventory().extract(what, 1 + random.nextInt(64), Actionable.MODULATE, SOURCE);
                // Straight at one mount, the way the owner of a machine writes to its own storage.
                case 2 -> fixture.mounts.get(random.nextInt(fixture.mounts.size()))
                        .insert(what, 1 + random.nextInt(64), Actionable.MODULATE, SOURCE);
                default -> fixture.mounts.get(random.nextInt(fixture.mounts.size()))
                        .extract(what, 1 + random.nextInt(64), Actionable.MODULATE, SOURCE);
            }

            if (step % 37 == 0) {
                cache.onUpdateTick();
            }
        }

        cache.onUpdateTick();

        final KeyCounter counted = new KeyCounter();
        for (final MEStorage mount : fixture.mounts) {
            mount.getAvailableStacks(counted);
        }
        counted.removeEmptySubmaps();

        final KeyCounter running = cache.getCachedInventory();

        assertThat("keys tracked", running.keySet(), is(counted.keySet()));
        for (final AEKey what : counted.keySet()) {
            assertThat(what.toString(), running.get(what), is(counted.get(what)));
        }
    }

    private enum Watch {
        NONE, ONE_KEY, EVERYTHING
    }

    private long timeTick(final int mounts, final boolean unique, final Watch watch, final int moves) {
        final Fixture fixture = new Fixture(mounts, unique, false);
        final GridStorageCache cache = cache(fixture, watch);
        final MEStorage network = cache.getInventory();

        // The moves happen off the clock: a hundred insertions cost far more than the tick that accounts for
        // them, and subtracting one noisy number from another said less than timing the tick alone.
        return time(Math.max(20, 20000 / (mounts * TYPES_PER_MOUNT)),
                i -> {
                    for (int move = 0; move < moves; move++) {
                        network.insert(keyPool.get(move), 1, Actionable.MODULATE, SOURCE);
                    }
                },
                i -> cache.onUpdateTick());
    }

    /** A storage service with that network mounted on it, and whoever is looking at it attached. */
    private GridStorageCache cache(final Fixture fixture, final Watch watch) {
        final GridStorageCache cache = new GridStorageCache(null);
        cache.addGlobalStorageProvider(mount -> {
            for (final MEInventoryHandler inv : fixture.mounts) {
                mount.mount(inv, 0);
            }
        });

        if (watch == Watch.ONE_KEY || watch == Watch.EVERYTHING) {
            cache.addNode(stubNode(), new StubWatcher(watch, keyPool.get(0)));
        }

        return cache;
    }

    // ------------------------------------------------------------------------------------------------
    // 3. The cell itself, which is where the NBT lives
    // ------------------------------------------------------------------------------------------------

    @Test
    public void cellInsertAndPersist() {
        heading("3. BasicCellInventory: one operation, in nanoseconds");
        row("types held", "insert (a drive)", "insert+persist (a chest)", "the NBT alone");

        for (final int held : new int[] { 1, 8, 32, 63 }) {
            // A drive only marks its chunk dirty; a chest rewrites the cell's NBT there and then.
            final StorageCell drive = cell(held, () -> {});
            final StorageCell chest = cell(held, null);
            final AEKey what = keyPool.get(0);

            final long inserted = time(20000, i -> drive.insert(what, 1, Actionable.MODULATE, SOURCE));
            final long withNbt = time(20000, i -> chest.insert(what, 1, Actionable.MODULATE, SOURCE));

            row(held, inserted, withNbt, withNbt - inserted);
        }
    }

    // ------------------------------------------------------------------------------------------------
    // 4. A storage bus rescanning the box next door
    // ------------------------------------------------------------------------------------------------

    @Test
    public void storageBusRescan() {
        heading("4. MEMonitorIInventory.onTick: one tick, in nanoseconds");
        row("slots", "occupied", "rescan (unchanged)");

        for (final int slots : new int[] { 27, 54, 108, 1000 }) {
            final ItemStackHandler handler = new ItemStackHandler(slots);
            for (int slot = 0; slot < slots; slot++) {
                handler.setStackInSlot(slot, keyPool.get(slot % keyPool.size()).toStack(1));
            }

            final MEMonitorIInventory monitor = new MEMonitorIInventory(new AdaptorItemHandler(handler));
            monitor.onTick();

            row(slots, slots, time(Math.max(50, 50000 / slots), i -> monitor.onTick()));
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------------------------------------

    /** A network of {@code mounts} storages, each holding {@link #TYPES_PER_MOUNT} keys. */
    private final class Fixture {
        private final NetworkStorage storage = new NetworkStorage();
        private final List<MEInventoryHandler> mounts = new ArrayList<>();
        private final boolean unique;

        Fixture(final int mounts, final boolean unique, final boolean full) {
            this.unique = unique;

            for (int m = 0; m < mounts; m++) {
                final StubStorage inv = new StubStorage(full);
                for (int t = 0; t < TYPES_PER_MOUNT; t++) {
                    inv.contents.add(keyPool.get(unique ? m * TYPES_PER_MOUNT + t : t), 64);
                }

                // Wrapped exactly as a drive, a chest and a storage bus all wrap what they mount.
                final MEInventoryHandler handler = new MEInventoryHandler(inv);
                this.mounts.add(handler);
                this.storage.mount(0, handler);
            }
        }

        int distinctKeys() {
            return this.unique ? this.mounts.size() * TYPES_PER_MOUNT : TYPES_PER_MOUNT;
        }
    }

    /**
     * Stands in for a mounted cell. Deliberately trivial: scenario 1 is measuring what the network spends
     * walking its mounts, and a real cell's own cost is measured on its own in scenario 3.
     */
    private static final class StubStorage implements MEStorage {
        private final KeyCounter contents = new KeyCounter();
        private final boolean full;

        StubStorage(final boolean full) {
            this.full = full;
        }

        @Override
        public long insert(final AEKey what, final long amount, final Actionable mode, final IActionSource src) {
            if (this.full) {
                return 0;
            }
            if (mode == Actionable.MODULATE) {
                this.contents.add(what, amount);
            }
            return amount;
        }

        @Override
        public long extract(final AEKey what, final long amount, final Actionable mode, final IActionSource src) {
            final long have = this.contents.get(what);
            final long taken = Math.min(have, amount);
            if (taken > 0 && mode == Actionable.MODULATE) {
                this.contents.remove(what, taken);
            }
            return taken;
        }

        @Override
        public void getAvailableStacks(final KeyCounter out) {
            out.addAll(this.contents);
        }

        @Override
        public ITextComponent getDescription() {
            return new TextComponentString("stub");
        }
    }

    /**
     * A real cell, holding {@code types} keys, backed by a real item stack and real NBT. A null
     * {@code saver} is how a cell with no holder behaves: it writes its NBT on every single change.
     */
    private StorageCell cell(final int types, final ISaveProvider saver) {
        final ItemStack stack = new ItemStack(TestCellItem.INSTANCE);
        final StorageCell inventory = BasicCellInventory.createInventory(stack, saver);

        for (int t = 0; t < types; t++) {
            inventory.insert(keyPool.get(t), 64, Actionable.MODULATE, SOURCE);
        }
        inventory.persist();

        return inventory;
    }

    /** The smallest thing that is a storage cell: one key type, plenty of bytes, no upgrades, no config. */
    private static final class TestCellItem extends Item implements IBasicCellItem {
        private static final TestCellItem INSTANCE = new TestCellItem();

        @Override
        public Set<AEKeyType> getKeyTypes() {
            return Collections.singleton(AEKeyType.items());
        }

        @Override
        public int getBytes(@Nonnull final ItemStack cellItem) {
            return 1 << 20;
        }

        @Override
        public int getBytesPerType(@Nonnull final ItemStack cellItem) {
            return 8;
        }

        @Override
        public int getTotalTypes(@Nonnull final ItemStack cellItem) {
            return TYPES_PER_MOUNT;
        }

        @Override
        public double getIdleDrain() {
            return 1.0;
        }

        @Override
        public boolean isEditable(final ItemStack is) {
            return false;
        }

        @Override
        public IItemHandler getUpgradesInventory(final ItemStack is) {
            return null;
        }

        @Override
        public IItemHandler getConfigInventory(final ItemStack is) {
            return null;
        }

        @Override
        public FuzzyMode getFuzzyMode(final ItemStack is) {
            return FuzzyMode.IGNORE_ALL;
        }

        @Override
        public void setFuzzyMode(final ItemStack is, final FuzzyMode fzMode) {
        }
    }

    /** Stands in for a level emitter (one key) or an open terminal (everything). */
    private static final class StubWatcher implements IGridHost, IStorageWatcherNode {
        private final Watch watch;
        private final AEKey key;

        StubWatcher(final Watch watch, final AEKey key) {
            this.watch = watch;
            this.key = key;
        }

        @Override
        public void updateWatcher(final IStackWatcher newWatcher) {
            if (this.watch == Watch.ONE_KEY) {
                newWatcher.add(this.key);
            } else if (this.watch == Watch.EVERYTHING) {
                newWatcher.setWatchAll(true);
            }
        }

        @Override
        public void onStackChange(final AEKey what, final long amount) {
        }

        @Override
        public IGridNode getGridNode(@Nonnull final appeng.api.util.AEPartLocation dir) {
            return null;
        }

        @Override
        public appeng.api.util.AECableType getCableConnectionType(@Nonnull final appeng.api.util.AEPartLocation dir) {
            return appeng.api.util.AECableType.NONE;
        }

        @Override
        public void securityBreak() {
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Plumbing
    // ------------------------------------------------------------------------------------------------

    /**
     * Nothing in {@code GridStorageCache} asks a node anything - it is only ever an identity key in a map -
     * so sixteen empty overrides would say less than this does.
     */
    private static IGridNode stubNode() {
        return (IGridNode) Proxy.newProxyInstance(IGridNode.class.getClassLoader(),
                new Class<?>[] { IGridNode.class }, (proxy, method, args) -> null);
    }

    /** {@code Platform} reads the side in a static initialiser, and there is no FML around to answer. */
    private static void fakeServerSide() throws Exception {
        final Class<?> handlerType = Class.forName("net.minecraftforge.fml.common.IFMLSidedHandler");
        final Object delegate = Proxy.newProxyInstance(handlerType.getClassLoader(),
                new Class<?>[] { handlerType },
                (proxy, method, args) -> "getSide".equals(method.getName()) ? Side.SERVER : null);

        final Field field = FMLCommonHandler.class.getDeclaredField("sidedDelegate");
        field.setAccessible(true);
        field.set(FMLCommonHandler.instance(), delegate);
    }

    /** The registry is normally created during mod construction, which does not happen here. */
    private static void registerItemKeyType() {
        // Another test class in the same run may have made it already.
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

    /**
     * Distinct keys, made by walking the item registry and then the damage values, so that comparing two of
     * them costs what comparing two ordinary items costs. Building them out of differing NBT instead would
     * measure tag comparison as much as anything else.
     */
    private static List<AEItemKey> buildKeyPool(final int wanted) {
        final List<Item> items = new ArrayList<>();
        for (final Item item : Item.REGISTRY) {
            items.add(item);
        }

        final List<AEItemKey> pool = new ArrayList<>(wanted);
        for (int i = 0; pool.size() < wanted; i++) {
            final Item item = items.get(i % items.size());
            final AEItemKey key = AEItemKey.of(item, i / items.size());
            if (key != null) {
                pool.add(key);
            }
        }
        return pool;
    }

    /**
     * Nanoseconds per operation: the median of {@link #MEASURED_ROUNDS} timed runs, each preceded by
     * {@link #WARMUP_ROUNDS} untimed ones.
     * <p/>
     * A single timed run is not a measurement here. The first one through any of these paths pays for
     * loading and compiling everything under it, and one late garbage collection is enough to triple a
     * number that is otherwise in nanoseconds. The median throws away both ends, so a row says what the
     * work costs when nothing unusual is happening - which is the number worth comparing between rows.
     */
    private static long time(final int iterations, final LongConsumer body) {
        return time(iterations, i -> {}, body);
    }

    /** As above, with {@code setup} run before each measured operation but kept off the clock. */
    private static long time(final int iterations, final LongConsumer setup, final LongConsumer body) {
        for (int round = 0; round < WARMUP_ROUNDS; round++) {
            for (int i = 0; i < iterations; i++) {
                setup.accept(i);
                body.accept(i);
            }
        }

        final long[] rounds = new long[MEASURED_ROUNDS];
        for (int round = 0; round < MEASURED_ROUNDS; round++) {
            long spent = 0;
            for (int i = 0; i < iterations; i++) {
                setup.accept(i);
                final long started = System.nanoTime();
                body.accept(i);
                spent += System.nanoTime() - started;
            }
            rounds[round] = spent / iterations;
        }

        Arrays.sort(rounds);
        return rounds[rounds.length / 2];
    }

    private static int ops(final int mounts) {
        return Math.max(200, 200000 / mounts);
    }

    private static void heading(final String title) {
        System.out.println();
        System.out.println(title);
    }

    private static void row(final Object... cells) {
        final StringBuilder line = new StringBuilder();
        for (final Object cell : cells) {
            line.append(String.format("%20s", cell));
        }
        System.out.println(line);
    }
}
