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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.Loader;

import appeng.api.migration.MigrationLog;
import appeng.core.AELog;

/**
 * {@code logs/ae2ud-migration.log}, appended to. The file is opened by the first line written, so a world with
 * nothing to report leaves none.
 */
final class MigrationLogFile implements MigrationLog {

    private final String world;
    private final String from;
    private final File file;
    private final SimpleDateFormat time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Nullable
    private PrintWriter out;
    private boolean broken;
    private int lost;
    private int failed;

    MigrationLogFile(final String world, final String from) {
        this.world = world;
        this.from = from;
        this.file = new File(new File(Loader.instance().getConfigDir().getParentFile(), "logs"),
                "ae2ud-migration.log");
    }

    @Override
    public synchronized void note(final String message) {
        this.write("NOTE", message);
    }

    @Override
    public synchronized void lost(final String message) {
        this.lost++;
        this.write("LOST", message);
    }

    @Override
    public synchronized void failed(final String what, final NBTTagCompound original, final Throwable error) {
        this.failed++;
        this.write("FAILED", what + ": " + error + "\n    original: " + original);
        if (this.out != null) {
            error.printStackTrace(this.out);
            this.out.flush();
        }
    }

    synchronized void summarise() {
        if (this.lost == 0 && this.failed == 0) {
            AELog.info("Migration from AE2 %s: nothing lost so far", this.from);
        } else {
            AELog.warn("Migration from AE2 %s: %d things had no equivalent and %d could not be rewritten so far; see %s",
                    this.from, this.lost, this.failed, this.file);
        }
    }

    synchronized void close() {
        if (this.out != null) {
            this.out.close();
            this.out = null;
        }
    }

    private void write(final String kind, final String message) {
        if (this.out == null && !this.open()) {
            return;
        }
        this.out.println("[" + this.time.format(new Date()) + "] " + kind + " " + message);
        this.out.flush();
    }

    private boolean open() {
        if (this.broken) {
            return false;
        }
        try {
            if (!this.file.getParentFile().isDirectory() && !this.file.getParentFile().mkdirs()) {
                throw new IOException("Could not create " + this.file.getParentFile());
            }
            this.out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(this.file, true),
                    StandardCharsets.UTF_8));
        } catch (final IOException e) {
            this.broken = true;
            AELog.error(e, "Could not open " + this.file + "; the migration is logged nowhere");
            return false;
        }
        this.out.println("[" + this.time.format(new Date()) + "] WORLD " + this.world + ", saved by AE2 " + this.from);
        return true;
    }
}
