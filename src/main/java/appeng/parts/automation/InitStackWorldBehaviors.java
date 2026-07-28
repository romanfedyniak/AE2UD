/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.parts.automation;

import appeng.api.behaviors.PickupStrategy;
import appeng.api.behaviors.PlacementStrategy;
import appeng.api.behaviors.StackExportStrategy;
import appeng.api.behaviors.StackImportStrategy;
import appeng.api.stacks.AEKeyType;

/**
 * Registers the item import, export, placement and pickup strategies against
 * {@code AEKeyType.items()}, through the same public {@code register(...)} API any addon would use
 * (see CONTRACT.md &sect;3, "Item and Fluid get no privileges").
 * <p>
 * Called from {@code appeng.core.Registration} (wave 3d's responsibility to wire in). Does
 * <b>not</b> register an {@code ExternalStorageStrategy} -- that belongs to the storage bus, owned by
 * {@code appeng.parts.misc.InitExternalStorageStrategies}.
 */
public final class InitStackWorldBehaviors {

    private InitStackWorldBehaviors() {
    }

    public static void register() {
        StackImportStrategy.register(AEKeyType.items(), StorageImportStrategy::createItem);
        StackExportStrategy.register(AEKeyType.items(), StorageExportStrategy::createItem);

        PlacementStrategy.register(AEKeyType.items(), ItemPlacementStrategy::new);

        PickupStrategy.register(AEKeyType.items(), ItemPickupStrategy::new);
    }
}
