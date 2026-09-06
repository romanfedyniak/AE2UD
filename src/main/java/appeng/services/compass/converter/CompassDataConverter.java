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
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.io.File;
import java.util.concurrent.TimeUnit;


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

        new OldCompassReader(dimId, this.worldCompassFolder).loadRegions().forEach(region -> {
            for (final ChunkPos pos : region.getBeacons()) {
                // Once the world is running: the chunk has to be there to be scanned.
                TickHandler.INSTANCE.addCallable(world, w -> {
                    // The chest may predate the natural property, so take any of them.
                    ServerCompassService.updateArea((WorldServer) w, pos, false);
                    return null;
                });
            }
        });

        AELog.info("Finished converting old compass data in %d ms", watch.elapsed(TimeUnit.MILLISECONDS));
        metadata.setVersion(ConverterMetadata.CURRENT_VERSION, Converters.COMPASS);
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
