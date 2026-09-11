/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.core.sync;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

import appeng.api.AEApi;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketChannelTiers;

/**
 * Hands a joining player the server's channel tiers, so what the client says about a cable is what the network
 * does with it.
 */
public final class ChannelTierSync {

    @SubscribeEvent
    public void onPlayerLoggedIn(final PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP player) {
            NetworkHandler.instance().sendTo(new PacketChannelTiers(AEApi.instance().registries().channelTiers().getTiers()), player);
        }
    }
}
