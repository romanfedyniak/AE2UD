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


import appeng.core.worlddata.converter.OldDataReader;

import javax.annotation.Nonnull;
import java.io.File;


public final class OldMeteoriteReader extends OldDataReader<OldMeteoriteRegion> {

    public OldMeteoriteReader(final int dimId, @Nonnull final File worldSpawnFolder) {
        super(dimId, worldSpawnFolder);
    }

    @Override
    public OldMeteoriteRegion mapNameToRegion(final String name) {
        return new OldMeteoriteRegion(this.folder, name);
    }
}
