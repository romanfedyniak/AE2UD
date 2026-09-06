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


import java.io.File;


/**
 * One file of the data a converter is reading out of.
 */
public interface IOldFileRegion {

    void openFile(String fileName);

    default boolean isFileExistent(final File file) {
        return file.exists() && file.isFile();
    }
}
