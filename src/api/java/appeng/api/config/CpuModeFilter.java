package appeng.api.config;

/**
 * Which crafting CPUs a CPU list shows, by the requests they let themselves be picked for.
 *
 * @see CpuSelectionMode
 */
public enum CpuModeFilter {
    /**
     * Every CPU, whichever requests it takes.
     */
    ALL,
    ANY,
    PLAYER_ONLY,
    MACHINE_ONLY;

    /**
     * @return whether a CPU in the given mode belongs in a list filtered this way.
     */
    public boolean matches(final CpuSelectionMode mode) {
        switch (this) {
            case ANY:
                return mode == CpuSelectionMode.ANY;
            case PLAYER_ONLY:
                return mode == CpuSelectionMode.PLAYER_ONLY;
            case MACHINE_ONLY:
                return mode == CpuSelectionMode.MACHINE_ONLY;
            default:
                return true;
        }
    }
}
