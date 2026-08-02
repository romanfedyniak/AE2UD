/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.container.implementations;

import java.io.IOException;
import java.util.Objects;

import io.netty.buffer.ByteBuf;

import appeng.api.stacks.AEKey;

/** Aggregated progress for one output shown in the crafting pin section. */
public final class TerminalCraftingPin {
    private final AEKey what;
    private final long remaining;
    private final long requested;
    private final boolean active;

    public TerminalCraftingPin(AEKey what, long remaining, long requested, boolean active) {
        this.what = Objects.requireNonNull(what, "what");
        this.remaining = remaining;
        this.requested = requested;
        this.active = active;
    }

    public TerminalCraftingPin(ByteBuf data) throws IOException {
        this(AEKey.readKey(data), data.readLong(), data.readLong(), data.readBoolean());
    }

    public void writeToPacket(ByteBuf data) throws IOException {
        AEKey.writeKey(data, what);
        data.writeLong(remaining);
        data.writeLong(requested);
        data.writeBoolean(active);
    }

    public AEKey getWhat() {
        return what;
    }

    public long getRemaining() {
        return remaining;
    }

    public long getRequested() {
        return requested;
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof TerminalCraftingPin)) return false;
        TerminalCraftingPin other = (TerminalCraftingPin) obj;
        return remaining == other.remaining && requested == other.requested && active == other.active
                && what.equals(other.what);
    }

    @Override
    public int hashCode() {
        return Objects.hash(what, remaining, requested, active);
    }
}
