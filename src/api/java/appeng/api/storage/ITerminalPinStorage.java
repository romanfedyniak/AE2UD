/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2UD contributors
 */

package appeng.api.storage;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

/** Persistent pin data belonging to one terminal host. */
public interface ITerminalPinStorage {

    IPlayerTerminalPins forPlayer(UUID playerId);

    void readFromNBT(NBTTagCompound tag, String name);

    void writeToNBT(NBTTagCompound tag, String name);
}
