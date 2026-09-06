/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.core.worlddata.converter;


import com.google.common.base.Preconditions;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;


/**
 * Walks one of the folders AE2 used to keep its own files in, handing back the regions belonging to a
 * dimension. The regions come back lazily: a save can hold a great many of them and most conversions only
 * ever look at a few fields of each.
 *
 * @param <D> what one file of this kind reads as
 */
public abstract class OldDataReader<D extends IOldFileRegion> {

    /** {@code <dimension>_<regionX>_<regionZ>.dat}, the name every one of these folders used. */
    public static final Pattern REGION_NAME_FORMAT = Pattern.compile("([-0-9]+)_([-0-9]+)_([-0-9]+)\\.dat");

    protected final int dimId;
    protected final File folder;

    protected OldDataReader(final int dimId, final File worldSpecificFolder) {
        Preconditions.checkNotNull(worldSpecificFolder);
        Preconditions.checkArgument(worldSpecificFolder.isDirectory());

        this.dimId = dimId;
        this.folder = worldSpecificFolder;
    }

    public Stream<D> loadRegions() {
        return this.getRegionNames().stream().filter(this::belongsHere).map(this::mapNameToRegion);
    }

    /** Matched on the whole first group, not on a prefix: dimension 1 must not swallow dimension 10. */
    private boolean belongsHere(final String name) {
        final Matcher matcher = REGION_NAME_FORMAT.matcher(name);
        return matcher.matches() && matcher.group(1).equals(Integer.toString(this.dimId));
    }

    private Collection<String> getRegionNames() {
        final String[] names = this.folder.list();
        return names == null ? Collections.emptyList() : Arrays.asList(names);
    }

    public abstract D mapNameToRegion(String name);
}
