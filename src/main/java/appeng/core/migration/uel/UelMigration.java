/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.core.migration.uel;

/**
 * What AE2UD makes of AE2 UEL's own blocks, items and data. The rules are written up in the AE2UD wiki, under
 * migration.
 */
public final class UelMigration {

    private UelMigration() {
    }

    public static void register() {
        UelIds.register();
        UelParts.register();
        UelInterfaces.register();
        UelSettings.register();
        UelStorage.register();
    }
}
