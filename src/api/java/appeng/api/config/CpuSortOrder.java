package appeng.api.config;

/**
 * What a CPU list is ordered by.
 */
public enum CpuSortOrder {
    /**
     * The CPU's own name, with unnamed CPUs last.
     */
    NAME,
    /**
     * How many bytes of crafting storage it holds.
     */
    STORAGE,
    /**
     * How many co-processors it has.
     */
    COPROCESSORS
}
