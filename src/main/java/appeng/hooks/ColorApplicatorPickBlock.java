/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.hooks;


import appeng.api.AEApi;
import appeng.api.definitions.IItemDefinition;
import appeng.api.implementations.tiles.IColorableTile;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketColorApplicatorSelectColor;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;


/**
 * The color applicator taking a color with the pick block key, called from the head of
 * {@code ForgeHooks.onPickBlock}. Kept apart from the transformer, and with nothing but vanilla types in its
 * signatures, so patching ForgeHooks loads none of AE's own classes that early.
 */
public final class ColorApplicatorPickBlock {

    private ColorApplicatorPickBlock() {
    }

    /**
     * Whether a middle click is the color applicator taking a color, rather than a pick block. Asked without
     * doing anything about it, because the click reaches more than one hook and only one of them may have it.
     */
    public static boolean isColorApplicatorPickBlock(final RayTraceResult result, final EntityPlayer player) {
        if (player == null || player.world == null || result == null || result.typeOfHit != RayTraceResult.Type.BLOCK) {
            return false;
        }

        final IItemDefinition applicator = AEApi.instance().definitions().items().colorApplicator();
        if (!applicator.isSameAs(player.getHeldItemMainhand()) && !applicator.isSameAs(player.getHeldItemOffhand())) {
            return false;
        }

        return player.world.getTileEntity(result.getBlockPos()) instanceof IColorableTile;
    }

    @SuppressWarnings("unused")
    public static boolean testColorApplicatorPickBlock(final RayTraceResult result, final EntityPlayer player,
            final World world) {
        if (!isColorApplicatorPickBlock(result, player)) {
            return false;
        }

        final TileEntity tile = player.world.getTileEntity(result.getBlockPos());
        NetworkHandler.instance().sendToServer(new PacketColorApplicatorSelectColor(((IColorableTile) tile).getColor()));
        return true;
    }
}
