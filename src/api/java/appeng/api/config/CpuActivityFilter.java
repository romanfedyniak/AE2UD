/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2UD contributors
 */

package appeng.api.config;

/**
 * Which crafting CPUs a CPU list shows, by whether they are working on something.
 */
public enum CpuActivityFilter {
    /**
     * Every CPU, working or not.
     */
    ALL,
    /**
     * Only CPUs with a job on them.
     */
    ACTIVE,
    /**
     * Only CPUs free to take one.
     */
    IDLE
}
