/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2UD contributors
 */

package appeng.api.config;

/**
 * How much an inscriber's three input slots may hold.
 */
public enum InscriberInputCapacity {
    ONE(1),
    FOUR(4),
    SIXTY_FOUR(64);

    public final int capacity;

    InscriberInputCapacity(final int capacity) {
        this.capacity = capacity;
    }
}
