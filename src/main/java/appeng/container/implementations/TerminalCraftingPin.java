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

    /**
     * What the pin's job did. {@link #UNKNOWN} is a job that stopped being ours without ever reporting an
     * outcome - a hijacked CPU, a server restart mid-craft - and deliberately shows no status at all.
     */
    public enum Status {
        ACTIVE,
        DONE,
        CANCELLED,
        UNKNOWN;

        private static final Status[] VALUES = values();

        static Status byIndex(int index) {
            return index >= 0 && index < VALUES.length ? VALUES[index] : UNKNOWN;
        }
    }

    private final AEKey what;
    private final long remaining;
    private final long requested;
    private final Status status;

    public TerminalCraftingPin(AEKey what, long remaining, long requested, Status status) {
        this.what = Objects.requireNonNull(what, "what");
        this.remaining = remaining;
        this.requested = requested;
        this.status = Objects.requireNonNull(status, "status");
    }

    public TerminalCraftingPin(ByteBuf data) throws IOException {
        this(AEKey.readKey(data), data.readLong(), data.readLong(), Status.byIndex(data.readByte()));
    }

    public void writeToPacket(ByteBuf data) throws IOException {
        AEKey.writeKey(data, what);
        data.writeLong(remaining);
        data.writeLong(requested);
        data.writeByte(status.ordinal());
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

    public Status getStatus() {
        return status;
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof TerminalCraftingPin)) return false;
        TerminalCraftingPin other = (TerminalCraftingPin) obj;
        return remaining == other.remaining && requested == other.requested && status == other.status
                && what.equals(other.what);
    }

    @Override
    public int hashCode() {
        return Objects.hash(what, remaining, requested, status);
    }
}
