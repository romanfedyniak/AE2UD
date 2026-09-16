/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.api.networking.energy;


/**
 * A machine that draws power from its network while it works, on top of the idle drain its grid node
 * declares, and says how much. Implemented by the grid host - a tile or a part.
 * <p>
 * The network only counts what a node declares as idle, so without this a machine that takes its power as it
 * needs it shows no drain at all in the Network Tool, however much the network as a whole is losing to it.
 */
public interface IPowerUsageReporter
{

	/**
	 * @return what the machine has drawn from its network lately, in AE per tick, averaged over about a
	 *         second - not counting its idle drain.
	 */
	double getActivePowerUsage();
}
