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


/**
 * The kinds of old data a save can still be carrying, each remembering its own version.
 */
public enum Converters {
    COMPASS("compass_version");

    private final String versionKey;

    Converters(final String versionKey) {
        this.versionKey = versionKey;
    }

    public String getKey() {
        return this.versionKey;
    }
}
