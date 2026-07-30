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

import java.util.List;

import appeng.api.behaviors.StackImportStrategy;
import appeng.api.behaviors.StackTransferContext;

/**
 * Iterates over every registered {@link StackImportStrategy} and exposes them as a single strategy.
 * First come, first serve -- ported verbatim from AE2-original.
 */
class StackImportFacade implements StackImportStrategy {

    private final List<StackImportStrategy> strategies;

    StackImportFacade(List<StackImportStrategy> strategies) {
        this.strategies = strategies;
    }

    @Override
    public boolean transfer(StackTransferContext context) {
        boolean worked = false;
        for (StackImportStrategy strategy : this.strategies) {
            if (strategy.transfer(context)) {
                worked = true;
            }
        }
        return worked;
    }
}
