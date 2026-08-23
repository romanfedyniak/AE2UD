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

package appeng.api.config;


/**
 * How a crafting job treats an ingredient the network can neither supply nor make.
 */
public enum CraftingMode {
    /**
     * The job gives up and reports what it lacks. Requesting it again changes nothing until the missing
     * ingredients are in the network.
     */
    STANDARD,

    /**
     * The job is planned as though the missing ingredients had been promised by a level emitter: it is a
     * real job rather than a simulation, and the CPU that takes it waits for them to be put into the
     * network before the steps that need them can run. What is lacking is still reported, so the plan says
     * what has to be brought.
     */
    IGNORE_MISSING
}
