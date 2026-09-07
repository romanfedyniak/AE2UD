/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.debug.craftingtest;


import appeng.core.AELog;
import appeng.core.AppEng;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import javax.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;


/**
 * The answers the planner is expected to give, as it gave them last time somebody looked and agreed.
 * <p>
 * Kept in the config folder rather than in the mod, because it is a record of one machine's run against one
 * build, not a fact about the mod - and because recording a new one is a deliberate act, done when a
 * difference has been read and accepted.
 */
public final class TestBaseline {

    private static final String FILE_NAME = "CraftingTestBaseline.json";
    private static final Type TYPE = new TypeToken<LinkedHashMap<String, TestOutcome>>() {
    }.getType();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private TestBaseline() {
    }

    public static File file() {
        return new File(AppEng.instance().getConfigDirectory(), FILE_NAME);
    }

    /**
     * @return an empty map when there is no baseline yet, which the report then calls out per scenario.
     */
    public static Map<String, TestOutcome> load() {
        final File file = file();

        if (!file.isFile()) {
            return new LinkedHashMap<>();
        }

        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            final Map<String, TestOutcome> loaded = GSON.fromJson(reader, TYPE);
            return loaded == null ? new LinkedHashMap<>() : loaded;
        } catch (final IOException | RuntimeException e) {
            AELog.warn("Could not read the crafting test baseline: %s", e);
            return new LinkedHashMap<>();
        }
    }

    /**
     * @return null when it was written, or why it was not.
     */
    @Nullable
    public static String save(final Map<String, TestOutcome> outcomes) {
        final File file = file();

        try {
            final File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                return "could not create " + parent;
            }

            try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
                GSON.toJson(outcomes, TYPE, writer);
            }

            return null;
        } catch (final IOException | RuntimeException e) {
            AELog.warn("Could not write the crafting test baseline: %s", e);
            return String.valueOf(e);
        }
    }
}
