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

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;

/**
 * Associates {@link AEKeyType#items()} with the AE2UD-specific way of reaching an adjacent
 * inventory.
 * <p>
 * AE2-original's {@code HandlerStrategy<C, S>} wraps a raw {@code Storage<ItemVariant>}/
 * {@code ResourceHandler}. Porting that literally would mean talking to the adjacent block through
 * the bare {@code IItemHandler} capability, which would silently drop the one thing the pre-port
 * {@code PartImportBus}/{@code PartExportBus} already did better: {@link appeng.util.InventoryAdaptor}
 * transparently also supports Storage Drawers' {@code IItemRepository} capability
 * ({@code appeng.util.inv.AdaptorItemRepository}). {@link StorageImportStrategy} and
 * {@link StorageExportStrategy} therefore talk to {@code InventoryAdaptor} directly instead of using
 * this class as a generic conversion layer; it exists mainly so the type-identity check
 * ("is this key one of mine") has one shared, named home, matching upstream's package shape.
 */
final class HandlerStrategy {

    static final HandlerStrategy ITEMS = new HandlerStrategy();

    private HandlerStrategy() {
    }

    AEKeyType getKeyType() {
        return AEKeyType.items();
    }

    boolean isSupported(AEKey what) {
        return AEItemKey.is(what);
    }
}
