/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2UD contributors
 */

package appeng.api.storage;

/**
 * Opt-in interface for terminal hosts that support persistent player pins.
 * Hosts using the standard ME terminal container and GUI gain pin support automatically.
 */
public interface ITerminalPinHost {

    ITerminalPinStorage getTerminalPinStorage();
}
