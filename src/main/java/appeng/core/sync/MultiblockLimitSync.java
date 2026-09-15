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

import appeng.core.MultiblockLimits;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketMultiblockLimits;

/**
 * Hands a joining player the sizes the server lets its multiblocks reach, so the tooltip that names a limit
 * names the one that will actually be enforced.
 */
public final class MultiblockLimitSync {

    @SubscribeEvent
    public void onPlayerLoggedIn(final PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP player) {
            NetworkHandler.instance().sendTo(new PacketMultiblockLimits(MultiblockLimits.all()), player);
        }
    }
}
