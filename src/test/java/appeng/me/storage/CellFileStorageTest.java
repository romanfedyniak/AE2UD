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


import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import javax.annotation.Nonnull;

import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.common.thread.SidedThreadGroups;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.RegistryBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.IBasicCellItem;
import appeng.api.storage.cells.StorageCell;
import appeng.core.api.AEItemKeyType;
import appeng.me.helpers.BaseActionSource;


/**
 * A cell's contents in a file of their own: what the item keeps, what the file keeps, how an older cell moves
 * across, and how two inventories open on one cell stay one cell.
 */
public final class CellFileStorageTest {

    private static final IActionSource SOURCE = new BaseActionSource();
    private static final String CELL_ID = "cellId";

    private static AEItemKey apple;
    private static AEItemKey stick;

    private final AtomicLong clock = new AtomicLong();
    private Path directory;
    private CellContentsStore store;

    @BeforeAll
    public static void bootstrap() throws Exception {
        Bootstrap.register();
        fakeServerSide();
        if (GameRegistry.findRegistry(AEKeyType.class) == null) {
            new RegistryBuilder<AEKeyType>()
                    .setName(AEKeyTypes.REGISTRY_NAME)
                    .setType(AEKeyType.class)
                    .setIDRange(0, 127)
                    .create();
            AEKeyTypes.register(new AEItemKeyType());
        }
        apple = AEItemKey.of(Items.APPLE);
        stick = AEItemKey.of(Items.STICK);
    }

    @BeforeEach
    public void openStore() throws IOException {
        this.directory = Files.createTempDirectory("ae2-cells");
        this.reopenStore();
    }

    @AfterEach
    public void closeStore() throws Exception {
        this.store.awaitWrites();
        CellContentsStore.setCurrent(null);
        try (Stream<Path> paths = Files.walk(this.directory)) {
            paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        }
    }

    @Test
    public void contentsLeaveTheItemForAFile() throws Throwable {
        final ItemStack cell = newCell();
        onServer(() -> open(cell).insert(apple, 100, Actionable.MODULATE, SOURCE));

        final NBTTagCompound tag = cell.getTagCompound();
        assertTrue(tag.hasUniqueId(CELL_ID));
        assertFalse(tag.hasKey("Items"));
        assertEquals(100, tag.getLong("ic"));

        this.store.save();
        this.store.awaitWrites();
        assertTrue(this.store.fileFor(tag.getUniqueId(CELL_ID)).isFile());

        // A store that never saw the cell has only the file to go on.
        this.reopenStore();
        onServer(() -> assertEquals(100, open(cell).getStoredItemCount()));
    }

    @Test
    public void twoInventoriesOnOneCellShareWhatItHolds() throws Throwable {
        final ItemStack cell = newCell();
        onServer(() -> {
            final BasicCellInventory first = open(cell);
            final BasicCellInventory second = open(cell);

            // Opened before anything was in the cell, so it starts out on contents of its own.
            assertTrue(second.getAvailableStacks().isEmpty());

            first.insert(apple, 10, Actionable.MODULATE, SOURCE);
            assertEquals(10, second.getAvailableStacks().get(apple));

            second.insert(apple, 5, Actionable.MODULATE, SOURCE);
            assertEquals(15, first.getAvailableStacks().get(apple));
        });

        assertEquals(15, cell.getTagCompound().getLong("ic"));
    }

    @Test
    public void anEmptiedCellLosesItsIdAndItsFile() throws Throwable {
        final ItemStack cell = newCell();
        onServer(() -> open(cell).insert(apple, 4, Actionable.MODULATE, SOURCE));
        final UUID id = cell.getTagCompound().getUniqueId(CELL_ID);

        this.store.save();
        this.store.awaitWrites();
        assertTrue(this.store.fileFor(id).isFile());

        onServer(() -> open(cell).extract(apple, 4, Actionable.MODULATE, SOURCE));
        final NBTTagCompound tag = cell.getTagCompound();
        assertFalse(tag.hasUniqueId(CELL_ID));
        assertFalse(tag.hasKey("ic"));
        assertFalse(tag.hasKey("pv"));

        this.store.save();
        this.store.awaitWrites();
        assertFalse(this.store.fileFor(id).isFile());
    }

    @Test
    public void anOldCellMovesItsListToAFile() throws Throwable {
        final ItemStack cell = newCell();

        // Off the server thread there is no store, which writes the list the way an older version did.
        final BasicCellInventory old = open(cell);
        old.insert(apple, 7, Actionable.MODULATE, SOURCE);
        old.insert(stick, 2, Actionable.MODULATE, SOURCE);
        assertTrue(cell.getTagCompound().hasKey("Items"));

        onServer(() -> assertEquals(9, open(cell).getStoredItemCount()));

        final NBTTagCompound tag = cell.getTagCompound();
        assertFalse(tag.hasKey("Items"));
        assertTrue(tag.hasUniqueId(CELL_ID));

        this.store.save();
        this.store.awaitWrites();
        assertTrue(this.store.fileFor(tag.getUniqueId(CELL_ID)).isFile());
    }

    @Test
    public void aListBesideAnIdIsAddedToTheFile() throws Throwable {
        final ItemStack cell = newCell();
        onServer(() -> open(cell).insert(apple, 10, Actionable.MODULATE, SOURCE));

        final ItemStack older = newCell();
        final BasicCellInventory old = open(older);
        old.insert(apple, 5, Actionable.MODULATE, SOURCE);
        old.insert(stick, 3, Actionable.MODULATE, SOURCE);
        cell.getTagCompound().setTag("Items", older.getTagCompound().getTag("Items").copy());

        onServer(() -> {
            final KeyCounter stored = open(cell).getAvailableStacks();
            assertEquals(15, stored.get(apple));
            assertEquals(3, stored.get(stick));
        });
        assertFalse(cell.getTagCompound().hasKey("Items"));
    }

    @Test
    public void twoInventoriesRefillingAnEmptiedCellEndUpInOne() throws Throwable {
        final ItemStack cell = newCell();
        onServer(() -> {
            final BasicCellInventory first = open(cell);
            first.insert(apple, 10, Actionable.MODULATE, SOURCE);
            first.extract(apple, 10, Actionable.MODULATE, SOURCE);
            assertFalse(cell.getTagCompound().hasUniqueId(CELL_ID));

            final BasicCellInventory second = open(cell);
            second.insert(apple, 4, Actionable.MODULATE, SOURCE);
            first.insert(apple, 6, Actionable.MODULATE, SOURCE);

            assertEquals(10, first.getAvailableStacks().get(apple));
            assertEquals(10, second.getAvailableStacks().get(apple));
        });

        assertEquals(10, cell.getTagCompound().getLong("ic"));
    }

    @Test
    public void theClientReadsTheSummary() throws Throwable {
        final ItemStack cell = newCell();
        final long[] serverBytes = new long[1];
        onServer(() -> {
            final BasicCellInventory inventory = open(cell);
            for (int n = 0; n < 8; n++) {
                inventory.insert(AEItemKey.of(Items.PAPER, n), 10 + n, Actionable.MODULATE, SOURCE);
            }
            serverBytes[0] = inventory.getUsedBytes();
        });

        // The copy a client is sent, opened where there is no store.
        final BasicCellInventory client = open(cell.copy());
        assertEquals(8, client.getStoredItemTypes());
        assertEquals(serverBytes[0], client.getUsedBytes());
        assertEquals(cell.getTagCompound().getUniqueId(CELL_ID), client.getUnloadedContentsId());

        final KeyCounter shown = client.getAvailableStacks();
        assertEquals(BasicCellInventory.PREVIEW_SIZE, shown.size());
        assertEquals(17, shown.get(AEItemKey.of(Items.PAPER, 7)));
        assertEquals(0, shown.get(AEItemKey.of(Items.PAPER, 0)));
    }

    @Test
    public void aKeyWithALargeTagIsPreviewedWithoutIt() throws Throwable {
        final ItemStack heavy = new ItemStack(Items.PAPER);
        final char[] filler = new char[4000];
        Arrays.fill(filler, 'x');
        final NBTTagCompound tag = new NBTTagCompound();
        tag.setString("contents", new String(filler));
        heavy.setTagCompound(tag);

        final ItemStack cell = newCell();
        onServer(() -> open(cell).insert(AEItemKey.of(heavy), 1, Actionable.MODULATE, SOURCE));

        final NBTTagList preview = cell.getTagCompound().getTagList("pv", Constants.NBT.TAG_COMPOUND);
        assertEquals(1, preview.tagCount());
        assertFalse(preview.getCompoundTagAt(0).hasKey("tag"));
    }

    @Test
    public void aCopyIsAskedWhetherItFitsWithoutOpeningIt() throws Throwable {
        final ItemStack cell = newCell();
        onServer(() -> open(cell).insert(apple, 1, Actionable.MODULATE, SOURCE));

        this.reopenStore();
        onServer(() -> assertFalse(open(cell.copy()).canFitInsideCell()));
        assertEquals(0, this.store.countActive());
    }

    @Test
    public void idleContentsAreHandedBackWhileSomethingHoldsThem() {
        final UUID id = UUID.randomUUID();
        final CellContents held = this.store.getOrLoad(id);
        held.add(apple, 1);
        held.markDirty();

        this.store.save();
        this.clock.addAndGet(CellContentsStore.IDLE_MILLIS);
        this.store.save();
        assertEquals(0, this.store.countActive());

        assertSame(held, this.store.getOrLoad(id));
        assertEquals(1, this.store.countActive());
    }

    @Test
    public void aFileThatCannotBeReadIsSetAside() throws IOException {
        final UUID id = UUID.randomUUID();
        final File file = this.store.fileFor(id);
        assertTrue(file.getParentFile().mkdirs());
        Files.write(file.toPath(), new byte[] { 1, 2, 3 });

        assertTrue(this.store.getOrLoad(id).isEmpty());
        assertTrue(new File(file.getPath() + ".corrupt").isFile());
    }

    // ------------------------------------------------------------------------------------------------
    // Plumbing
    // ------------------------------------------------------------------------------------------------

    private void reopenStore() {
        this.store = new CellContentsStore(this.directory.toFile(), this.clock::get);
        CellContentsStore.setCurrent(this.store);
    }

    private static ItemStack newCell() {
        return new ItemStack(TestCellItem.INSTANCE);
    }

    private static BasicCellInventory open(final ItemStack cell) {
        return (BasicCellInventory) BasicCellInventory.createInventory(cell, null);
    }

    /** The store answers only on a server thread, the way it does in game. */
    private static void onServer(final Body body) throws Throwable {
        final Throwable[] failure = new Throwable[1];
        final Thread thread = new Thread(SidedThreadGroups.SERVER, () -> {
            try {
                body.run();
            } catch (final Throwable t) {
                failure[0] = t;
            }
        }, "Server thread");
        thread.start();
        thread.join();

        if (failure[0] != null) {
            throw failure[0];
        }
    }

    @FunctionalInterface
    private interface Body {
        void run() throws Exception;
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

    /** One key type, plenty of bytes, no upgrades, no config. */
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
            return 63;
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
}
