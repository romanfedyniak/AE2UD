/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.worldgen.meteorite.converter;


import appeng.core.AELog;
import appeng.core.AppEng;
import appeng.core.worlddata.IOnWorldStartable;
import appeng.core.worlddata.IOnWorldStoppable;
import appeng.core.worlddata.converter.ConverterMetadata;
import appeng.core.worlddata.converter.Converters;
import appeng.worldgen.MeteoriteWorldGen;
import appeng.worldgen.meteorite.MapGenMeteorite;
import appeng.worldgen.meteorite.settings.PlacedMeteoriteSettings;
import com.google.common.base.Stopwatch;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.io.File;
import java.util.concurrent.TimeUnit;


/**
 * Reads what the old generator kept in {@code AE2/spawndata} into the structures the new one works from,
 * once per save.
 * <p>
 * The meteorites themselves are already in the ground; what this recovers is the record of them, so the parts
 * that were never built - the chunks that happened not to be loaded when the meteorite landed - are finished
 * off if anyone ever walks there, instead of the meteorite staying cut off at a chunk border for good.
 */
public final class MeteoriteDataConverter implements IOnWorldStartable, IOnWorldStoppable {

    private final File meteoriteWorldSpawnFolder;

    public MeteoriteDataConverter(final File meteoriteWorldSpawnFolder) {
        this.meteoriteWorldSpawnFolder = meteoriteWorldSpawnFolder;
    }

    @SubscribeEvent
    public void convertOldMeteorData(final WorldEvent.Load event) {
        final World world = event.getWorld();
        if (world.isRemote) {
            return;
        }

        // With meteorite generation switched off there is nothing to convert them into. Leave the old
        // files alone and the metadata untouched, so turning it back on still converts them.
        final MeteoriteWorldGen worldGen = AppEng.instance().getMeteoriteGen();
        if (worldGen == null) {
            return;
        }

        final ConverterMetadata metadata = ConverterMetadata.get((WorldServer) world);
        if (metadata.isUpToDate(world, Converters.METEOR_SPAWN)) {
            metadata.setVersion(ConverterMetadata.CURRENT_VERSION, Converters.METEOR_SPAWN);
            return;
        }

        final Stopwatch watch = Stopwatch.createStarted();
        final int dimId = world.provider.getDimension();
        AELog.info("Found outdated meteor spawn metadata [version=%d] in dimension [%d] "
                + "- converting old meteor spawn data...", metadata.getVersion(Converters.METEOR_SPAWN), dimId);

        final MapGenMeteorite meteoriteGen = worldGen.getGenerator(world);

        new OldMeteoriteReader(dimId, this.meteoriteWorldSpawnFolder).loadRegions().forEach(region -> {
            for (final NBTTagCompound meteor : region.getSettings()) {
                final PlacedMeteoriteSettings settings = MeteoriteSettingsConverter.convertOld(meteor, world);
                final ChunkPos centerChunkPos = new ChunkPos(settings.getPos());

                meteoriteGen.addOldMeteor(world, centerChunkPos.x, centerChunkPos.z, settings);
            }
        });

        AELog.info("Finished converting old meteor spawn data in %d ms", watch.elapsed(TimeUnit.MILLISECONDS));
        metadata.setVersion(ConverterMetadata.CURRENT_VERSION, Converters.METEOR_SPAWN);
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
