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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.registries.RegistryBuilder;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.core.api.AEItemKeyType;


/**
 * How cell files reach the disk: one write per cell however many saves came while the writer was busy, and a
 * file that could not be read is never written over.
 */
public final class CellContentsWritesTest {

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
        this.store = new CellContentsStore(this.directory.toFile(), this.clock::get);
    }

    @AfterEach
    public void closeStore() throws Exception {
        this.store.awaitWrites();
        try (Stream<Path> paths = Files.walk(this.directory)) {
            paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        }
    }

    @Test
    public void savesWhileTheWriterIsBusyWriteTheCellOnce() throws Exception {
        // The store's own writer hides its queue; one that shows it, standing in for it.
        final ThreadPoolExecutor writer = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),
                task -> {
                    final Thread thread = new Thread(task);
                    thread.setDaemon(true);
                    return thread;
                });
        final Field field = CellContentsStore.class.getDeclaredField("writer");
        field.setAccessible(true);
        ((ExecutorService) field.get(this.store)).shutdown();
        field.set(this.store, writer);
        final CountDownLatch release = new CountDownLatch(1);
        writer.execute(() -> {
            try {
                release.await();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        final CellContents contents = this.store.getOrLoad(UUID.randomUUID());
        for (int i = 1; i <= 5; i++) {
            contents.add(apple, 1);
            contents.markDirty();
            this.store.save();
        }
        final int queued = writer.getQueue().size();
        release.countDown();
        this.store.awaitWrites();

        assertEquals(1, queued);
        assertEquals(5, readFile(this.store.fileFor(contents.getId())).getTagList("contents", Constants.NBT.TAG_COMPOUND)
                .getCompoundTagAt(0).getLong("#"));
    }

    /** Windows only: a lock held by another program (a backup tool, an antivirus) keeps the file from being read. */
    @Test
    public void aCellFileThatCouldNotBeReadIsNotWrittenOver() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("win"));

        final UUID id = UUID.randomUUID();
        final CellContents original = this.store.getOrLoad(id);
        original.add(apple, 64);
        original.markDirty();
        this.store.save();
        this.store.awaitWrites();
        final File file = this.store.fileFor(id);
        final long size = file.length();

        this.store = new CellContentsStore(this.directory.toFile(), this.clock::get);
        CellContents reopened;
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw"); FileChannel channel = raf.getChannel();
                FileLock lock = channel.lock()) {
            reopened = this.store.getOrLoad(id);
        }

        reopened.add(stick, 1);
        reopened.markDirty();
        this.store.save();
        this.store.awaitWrites();

        final File kept = file.isFile() && file.length() == size ? file : new File(file.getPath() + ".corrupt");
        assertTrue(kept.isFile(), "the unread file is gone");
        assertEquals(64, readFile(kept).getTagList("contents", Constants.NBT.TAG_COMPOUND).getCompoundTagAt(0).getLong("#"),
                "what the unread file held: " + readFile(kept));
    }

    /** AE logs by side; outside a game nothing says which, so this says server. */
    private static void fakeServerSide() throws Exception {
        final Class<?> handlerType = Class.forName("net.minecraftforge.fml.common.IFMLSidedHandler");
        final Object delegate = Proxy.newProxyInstance(handlerType.getClassLoader(), new Class<?>[] { handlerType },
                (proxy, method, args) -> "getSide".equals(method.getName()) ? Side.SERVER : null);

        final Field field = FMLCommonHandler.class.getDeclaredField("sidedDelegate");
        field.setAccessible(true);
        field.set(FMLCommonHandler.instance(), delegate);
    }

    private static NBTTagCompound readFile(final File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            return CompressedStreamTools.readCompressed(in);
        }
    }
}
