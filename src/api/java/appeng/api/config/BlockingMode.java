/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2UD contributors
 */

package appeng.api.config;


/**
 * How hard an interface refuses to push a pattern into a neighbour that is already holding something.
 * <p>
 * Written down by the name of the constant, and the first two are named as the {@link YesNo} they replaced,
 * so anything saved before this setting grew a third value reads back unchanged.
 */
public enum BlockingMode
{
	/** Push regardless of what the neighbour is holding. */
	NO,

	/** Never push into a neighbour that is holding anything. */
	YES,

	/** As {@link #YES}, except for another helping of whatever that neighbour was last given. */
	SMART
}
