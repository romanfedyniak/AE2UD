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

import appeng.api.behaviors.StackExportStrategy;
import appeng.api.behaviors.StackTransferContext;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;

/**
 * Iterates over every registered {@link StackExportStrategy} and exposes them as a single strategy.
 * First come, first serve -- ported verbatim from AE2-original.
 */
class StackExportFacade implements StackExportStrategy {

    private final List<StackExportStrategy> strategies;

    StackExportFacade(List<StackExportStrategy> strategies) {
        this.strategies = strategies;
    }

    @Override
    public long transfer(StackTransferContext context, AEKey what, long maxAmount) {
        for (StackExportStrategy strategy : this.strategies) {
            long result = strategy.transfer(context, what, maxAmount);
            if (result > 0) {
                return result;
            }
        }
        return 0;
    }

    @Override
    public long push(AEKey what, long amount, Actionable mode) {
        for (StackExportStrategy strategy : this.strategies) {
            long result = strategy.push(what, amount, mode);
            if (result > 0) {
                return result;
            }
        }
        return 0;
    }
}
