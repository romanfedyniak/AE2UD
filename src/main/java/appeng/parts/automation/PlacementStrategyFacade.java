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

import java.util.Map;

import appeng.api.behaviors.PlacementStrategy;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;

/**
 * Dispatches to the {@link PlacementStrategy} registered for the key's own type. Ported verbatim
 * from AE2-original.
 */
class PlacementStrategyFacade implements PlacementStrategy {

    private final Map<AEKeyType, PlacementStrategy> strategies;

    PlacementStrategyFacade(Map<AEKeyType, PlacementStrategy> strategies) {
        this.strategies = strategies;
    }

    @Override
    public void clearBlocked() {
        for (PlacementStrategy strategy : this.strategies.values()) {
            strategy.clearBlocked();
        }
    }

    @Override
    public long placeInWorld(AEKey what, long amount, Actionable type, boolean placeAsEntity) {
        PlacementStrategy strategy = this.strategies.get(what.getType());
        return strategy != null ? strategy.placeInWorld(what, amount, type, placeAsEntity) : 0;
    }
}
