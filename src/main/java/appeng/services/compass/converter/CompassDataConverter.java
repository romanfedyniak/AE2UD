/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.services.compass.converter;


import appeng.core.AELog;
import appeng.core.worlddata.IOnWorldStartable;
import appeng.core.worlddata.IOnWorldStoppable;
import appeng.core.worlddata.converter.ConverterMetadata;
import appeng.core.worlddata.converter.Converters;
import appeng.hooks.TickHandler;
import appeng.services.compass.CompassRegion;
import appeng.services.compass.ServerCompassService;
import com.google.common.base.Stopwatch;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Reads what the compass used to keep in {@code AE2/compass} into {@link CompassRegion}, once per save.
 * <p>
 * Without this a save made before the move loses every meteorite it had already found: nothing scans a chunk
 * on its own any more, so a target that was recorded only in the old files would never be recorded again.
 */
public final class CompassDataConverter implements IOnWorldStartable, IOnWorldStoppable {

    private final File worldCompassFolder;

    public CompassDataConverter(final File worldCompassFolder) {
        this.worldCompassFolder = worldCompassFolder;
    }

    @SubscribeEvent
    public void convertOldCompassData(final WorldEvent.Load event) {
        final World world = event.getWorld();
        if (world.isRemote) {
            return;
        }

        final ConverterMetadata metadata = ConverterMetadata.get((WorldServer) world);
        if (metadata.isUpToDate(world, Converters.COMPASS)) {
            metadata.setVersion(ConverterMetadata.CURRENT_VERSION, Converters.COMPASS);
            return;
        }

        final Stopwatch watch = Stopwatch.createStarted();
        final int dimId = world.provider.getDimension();
        AELog.info("Found outdated compass metadata [version=%d] in dimension [%d] - converting old compass data...",
                metadata.getVersion(Converters.COMPASS), dimId);

        final List<ChunkPos> targets = new ArrayList<>();
        new OldCompassReader(dimId, this.worldCompassFolder).loadRegions().forEach(region -> targets.addAll(region.getBeacons()));

        AELog.info("Read %d old compass targets in %d ms", targets.size(), watch.elapsed(TimeUnit.MILLISECONDS));
        if (targets.isEmpty()) {
            metadata.setVersion(ConverterMetadata.CURRENT_VERSION, Converters.COMPASS);
            return;
        }

        // Done only once every target is scanned: a stop before that converts again on the next start.
        final AtomicInteger left = new AtomicInteger(targets.size());
        for (final ChunkPos pos : targets) {
            // Once the world is running: the chunk has to be there to be scanned.
            TickHandler.INSTANCE.addCallable(world, w -> {
                try {
                    scan((WorldServer) w, pos);
                } finally {
                    if (left.decrementAndGet() == 0) {
                        ConverterMetadata.get((WorldServer) w).setVersion(ConverterMetadata.CURRENT_VERSION, Converters.COMPASS);
                        AELog.info("Finished converting old compass data in dimension [%d]", dimId);
                    }
                }
                return null;
            });
        }
    }

    /**
     * A chunk the world no longer has (trimmed since) holds no meteorite now and is not generated again; one that
     * is not loaded is loaded for the scan and let go again.
     */
    private static void scan(final WorldServer world, final ChunkPos pos) {
        final ChunkProviderServer provider = world.getChunkProvider();
        if (!provider.isChunkGeneratedAt(pos.x, pos.z)) {
            return;
        }
        final boolean wasLoaded = provider.getLoadedChunk(pos.x, pos.z) != null;
        // The chest may predate the natural property, so take any of them.
        ServerCompassService.updateArea(world, pos, false);
        if (!wasLoaded) {
            final Chunk chunk = provider.getLoadedChunk(pos.x, pos.z);
            if (chunk != null) {
                provider.queueUnload(chunk);
            }
        }
    }

    @Override
    public void onWorldStart() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public void onWorldStop() {
        MinecraftForge.EVENT_BUS.unregister(this);
    }
}
