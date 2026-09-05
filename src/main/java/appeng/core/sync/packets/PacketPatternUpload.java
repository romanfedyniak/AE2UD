/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.core.sync.packets;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;

import appeng.api.config.SecurityPermissions;
import appeng.container.AEBaseContainer;
import appeng.container.implementations.ContainerPatternUpload;
import appeng.core.AEConfig;
import appeng.core.features.AEFeature;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import appeng.helpers.IPatternUploadHost;
import appeng.helpers.PatternUpload;

/**
 * Either half of sending a pattern away: the terminal's button, and a row of the screen that button opens.
 *
 * <p>One packet for both because the two are one action asked twice - the first time without saying where,
 * the second time saying it.</p>
 */
public class PacketPatternUpload extends AppEngPacket {

    private static final long NO_TARGET = Long.MIN_VALUE;

    private final long target;
    private final boolean pick;

    public PacketPatternUpload(final ByteBuf stream) {
        this.target = stream.readLong();
        this.pick = stream.readBoolean();
    }

    private PacketPatternUpload(final long target, final boolean pick) {
        this.target = target;
        this.pick = pick;

        final ByteBuf data = Unpooled.buffer();
        data.writeInt(this.getPacketID());
        data.writeLong(target);
        data.writeBoolean(pick);

        this.configureWrite(data);
    }

    /**
     * The button was pressed on a pattern terminal.
     *
     * @param pick whether the player asked to choose the target rather than have it decided.
     */
    public static PacketPatternUpload button(final boolean pick) {
        return new PacketPatternUpload(NO_TARGET, pick);
    }

    /**
     * A row of the target screen was clicked.
     */
    public static PacketPatternUpload to(final long target) {
        return new PacketPatternUpload(target, false);
    }

    @Override
    public void serverPacketData(final INetworkInfo manager, final AppEngPacket packet, final EntityPlayer player) {
        if (!AEConfig.instance().isFeatureEnabled(AEFeature.PATTERN_UPLOAD)) {
            return;
        }

        final Container open = player.openContainer;
        if (!(open instanceof AEBaseContainer)) {
            return;
        }

        final AEBaseContainer from = (AEBaseContainer) open;
        if (!(from.getTarget() instanceof IPatternUploadHost)) {
            return;
        }

        // Filing a pattern into a machine is building. The screen that asks where is opened behind the same
        // permission, but the automatic half never opens a screen at all and would otherwise go unasked.
        if (!from.isPermitted(SecurityPermissions.BUILD)) {
            return;
        }

        final IPatternUploadHost host = (IPatternUploadHost) from.getTarget();

        if (this.target == NO_TARGET) {
            PatternUpload.run((EntityPlayerMP) player, from, host, this.pick);
            return;
        }

        if (from instanceof ContainerPatternUpload) {
            PatternUpload.runTo((EntityPlayerMP) player, from, host,
                    ((ContainerPatternUpload) from).resolve(this.target));
        }
    }
}
