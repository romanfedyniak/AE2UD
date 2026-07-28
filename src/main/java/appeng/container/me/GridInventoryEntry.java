/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2021, TeamAppliedEnergistics, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.container.me;


import appeng.api.stacks.AEKey;
import io.netty.buffer.ByteBuf;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Objects;

/**
 * One row of a terminal's inventory listing, as synchronised from the server to an open terminal GUI.
 * <p/>
 * This is the replacement for the three pieces of information the old {@code IAEItemStack} carried at once
 * and that a {@link appeng.api.stacks.GenericStack} cannot: the stored amount, the amount that can be
 * requested from an attached external network, and whether the network can craft it. Keys carry no
 * craftable flag (see {@code CONTRACT.md} §8.3), so the flag has to travel alongside the key.
 * <p/>
 * Ported from upstream's {@code appeng.menu.me.common.GridInventoryEntry} minus its {@code serial} field.
 * Upstream needs a serial because its incremental update helper sends entries whose {@code what} is null;
 * AE2UD's terminal protocol has always identified a row by the key itself and sends the key every time, so
 * {@link #getWhat()} is never null here and no serial bookkeeping is required.
 * <p/>
 * Like the {@code IAEItemStack} it replaces, this doubles as the generic payload of
 * {@code PacketMEInventoryUpdate} and the other containers that packet serves read the two amount fields
 * with their own meaning: {@code ContainerNetworkStatus} sends a machine count in {@code storedAmount} and
 * that machine's idle power drain (x100) in {@code requestableAmount}, and {@code ContainerCraftConfirm}
 * sends the used/missing amount in {@code storedAmount} and the to-be-crafted amount in
 * {@code requestableAmount}. That reuse is inherited, not new.
 */
public class GridInventoryEntry {

    @Nonnull
    private final AEKey what;

    private final long storedAmount;

    private final long requestableAmount;

    private final boolean craftable;

    public GridInventoryEntry(@Nonnull AEKey what, long storedAmount, long requestableAmount, boolean craftable) {
        this.what = Objects.requireNonNull(what, "what");
        this.storedAmount = storedAmount;
        this.requestableAmount = requestableAmount;
        this.craftable = craftable;
    }

    /**
     * What is being stored. Never null.
     */
    @Nonnull
    public AEKey getWhat() {
        return this.what;
    }

    /**
     * How much of {@link #getWhat()} is stored in the network. Replaces the old
     * {@code IAEItemStack.getStackSize()}.
     */
    public long getStoredAmount() {
        return this.storedAmount;
    }

    /**
     * How much of {@link #getWhat()} can be requested from attached external networks. Replaces the old
     * {@code IAEItemStack.getCountRequestable()}.
     */
    public long getRequestableAmount() {
        return this.requestableAmount;
    }

    /**
     * Whether the network can automatically craft {@link #getWhat()}. Replaces the old
     * {@code IAEItemStack.isCraftable()}.
     */
    public boolean isCraftable() {
        return this.craftable;
    }

    /**
     * @return false when this entry is a removal - the row should disappear from the terminal.
     */
    public boolean isMeaningful() {
        return this.storedAmount > 0 || this.requestableAmount > 0 || this.craftable;
    }

    public GridInventoryEntry withStoredAmount(long newStoredAmount) {
        return new GridInventoryEntry(this.what, newStoredAmount, this.requestableAmount, this.craftable);
    }

    public void writeToPacket(final ByteBuf data) throws IOException {
        AEKey.writeKey(data, this.what);
        data.writeLong(this.storedAmount);
        data.writeLong(this.requestableAmount);
        data.writeBoolean(this.craftable);
    }

    public static GridInventoryEntry fromPacket(final ByteBuf data) throws IOException {
        final AEKey what = AEKey.readKey(data);
        final long storedAmount = data.readLong();
        final long requestableAmount = data.readLong();
        final boolean craftable = data.readBoolean();
        return new GridInventoryEntry(what, storedAmount, requestableAmount, craftable);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GridInventoryEntry other)) {
            return false;
        }
        return this.storedAmount == other.storedAmount && this.requestableAmount == other.requestableAmount && this.craftable == other.craftable && this.what
                .equals(other.what);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.what, this.storedAmount, this.requestableAmount, this.craftable);
    }

    @Override
    public String toString() {
        return "GridInventoryEntry{what=" + this.what + ", stored=" + this.storedAmount + ", requestable=" + this.requestableAmount + ", craftable=" + this.craftable + '}';
    }
}
