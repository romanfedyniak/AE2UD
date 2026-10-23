/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.core.migration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import appeng.api.migration.LegacyRewrites;
import appeng.core.AELog;
import appeng.core.AppEng;

/**
 * Whether a world came from AE2 UEL. Decided once, the first time AE2UD loads the world, from the AE2 version
 * {@code level.dat} remembers, and kept in {@code AE2/migration.dat}: after that first load {@code level.dat}
 * names AE2UD.
 */
public final class LegacyWorld {

    private static final String FLAG_FILE = "migration.dat";
    private static final String MIGRATED_FROM = "MigratedFrom";
    private static final String LAST_UEL = "v0.56.7";

    @Nullable
    private static MigrationLogFile log;

    private LegacyWorld() {
    }

    public static void onServerAboutToStart(final File worldDirectory, final File ae2Directory) {
        Migration.onWorldChange();
        final String from = migratedFrom(worldDirectory, new File(ae2Directory, FLAG_FILE));
        if (from == null || from.isEmpty()) {
            LegacyRewrites.startWorld(null);
            return;
        }

        log = new MigrationLogFile(worldDirectory.getName(), from);
        if (!LAST_UEL.equals(from)) {
            log.note("Only " + LAST_UEL + " is supported; this world was saved by " + from
                    + ". What it saved differently from " + LAST_UEL + " may be lost.");
        }
        LegacyRewrites.startWorld(log);
        AELog.info("This world was saved by AE2 %s, and is migrated to AE2UD as it loads", from);
    }

    public static void onServerStarted() {
        if (log != null) {
            log.summarise();
        }
    }

    public static void onServerStopped() {
        LegacyRewrites.stopWorld();
        if (log != null) {
            log.summarise();
            log.close();
            log = null;
        }
    }

    /**
     * @return the UEL version the world was saved by, empty when it was not, null when that is unknown.
     */
    @Nullable
    private static String migratedFrom(final File worldDirectory, final File flagFile) {
        if (flagFile.isFile()) {
            try (InputStream in = new FileInputStream(flagFile)) {
                return CompressedStreamTools.readCompressed(in).getString(MIGRATED_FROM);
            } catch (final IOException e) {
                AELog.error(e, "Could not read " + flagFile + "; the world is not migrated this time");
                return null;
            }
        }

        final String saved = savedVersion(worldDirectory);
        if (saved == null) {
            return null;
        }
        final String from = isUel(saved) ? saved : "";

        final NBTTagCompound flag = new NBTTagCompound();
        flag.setString(MIGRATED_FROM, from);
        if (!flagFile.getParentFile().isDirectory() && !flagFile.getParentFile().mkdirs()) {
            AELog.error("Could not create %s; the world is not migrated this time", flagFile.getParentFile());
            return null;
        }
        try (OutputStream out = new FileOutputStream(flagFile)) {
            CompressedStreamTools.writeCompressed(flag, out);
        } catch (final IOException e) {
            AELog.error(e, "Could not write " + flagFile + "; the world is not migrated this time");
            return null;
        }
        return from;
    }

    /**
     * @return the AE2 version {@code level.dat} names, empty for a new world or one without AE2, null when
     *         {@code level.dat} cannot be read.
     */
    @Nullable
    private static String savedVersion(final File worldDirectory) {
        final File levelDat = new File(worldDirectory, "level.dat");
        if (!levelDat.isFile()) {
            return "";
        }
        final NBTTagCompound level;
        try (InputStream in = new FileInputStream(levelDat)) {
            level = CompressedStreamTools.readCompressed(in);
        } catch (final IOException e) {
            AELog.error(e, "Could not read " + levelDat + "; the world is not migrated this time");
            return null;
        }

        final NBTTagList mods = level.getCompoundTag("FML").getTagList("ModList", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < mods.tagCount(); i++) {
            final NBTTagCompound mod = mods.getCompoundTagAt(i);
            if (AppEng.MOD_ID.equals(mod.getString("ModId"))) {
                return mod.getString("ModVersion");
            }
        }
        return "";
    }

    /**
     * UEL's versions start with "v0.", rv6's with "rv"; AE2UD's are plain numbers.
     */
    static boolean isUel(final String version) {
        return version.startsWith("v0.") || version.startsWith("rv");
    }
}
