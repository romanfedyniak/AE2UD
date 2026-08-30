/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2UD contributors
 */

package appeng.api.config;

import appeng.api.networking.security.IActionSource;

/**
 * Controls which crafting requests a crafting CPU may be picked for.
 */
public enum CpuSelectionMode {
    /**
     * Available to every request.
     */
    ANY,
    /**
     * Only available to requests made by a player.
     *
     * @see IActionSource#player()
     */
    PLAYER_ONLY,
    /**
     * Only available to requests made by automation.
     *
     * @see IActionSource#machine()
     */
    MACHINE_ONLY;

    /**
     * @return whether a request from the given source may run on a CPU in this mode.
     */
    public boolean accepts(final IActionSource source) {
        switch (this) {
            case PLAYER_ONLY:
                return source.player().isPresent();
            case MACHINE_ONLY:
                return !source.player().isPresent();
            default:
                return true;
        }
    }
}
