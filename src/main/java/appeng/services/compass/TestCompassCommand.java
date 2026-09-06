/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.services.compass;


import appeng.core.localization.PlayerMessages;
import appeng.server.ISubCommand;
import appeng.tile.storage.TileSkyChest;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;

import javax.annotation.Nullable;


/**
 * Asks the compass and the world the same question at once: the region says whether it believes this chunk
 * holds a target, the chunk says whether one is really there. Only both together tell a broken compass from
 * a chunk that never had a meteorite in it.
 */
public class TestCompassCommand implements ISubCommand {

    @Override
    public String getHelp(final MinecraftServer srv) {
        return "commands.ae2.Compass";
    }

    @Override
    public void call(final MinecraftServer srv, final String[] args, final ICommandSender sender) {
        final WorldServer world = (WorldServer) sender.getEntityWorld();
        final ChunkPos chunkPos = new ChunkPos(sender.getPosition());

        final boolean foundInRegion = CompassRegion.get(world, chunkPos).hasCompassTarget(chunkPos.x, chunkPos.z);
        final BlockPos foundInWorld = findMeteorite(world, chunkPos);

        if (foundInWorld == null) {
            sender.sendMessage(PlayerMessages.CompassTestFailure.get(foundInRegion));
        } else {
            sender.sendMessage(PlayerMessages.CompassTestSuccess.get(foundInRegion, foundInWorld.getX(),
                    foundInWorld.getY(), foundInWorld.getZ()));
        }
    }

    @Nullable
    private static BlockPos findMeteorite(final WorldServer world, final ChunkPos chunkPos) {
        final BlockPos sourcePos = new BlockPos(chunkPos.getBlock(8, 0, 8));
        double closestDistanceSq = Double.MAX_VALUE;
        BlockPos chosenPos = null;

        for (final TileEntity tileEntity : world.getChunk(chunkPos.x, chunkPos.z).getTileEntityMap().values()) {
            if (tileEntity instanceof TileSkyChest) {
                final BlockPos tePos = tileEntity.getPos();
                final double distSq = sourcePos.distanceSq(tePos.getX(), 0, tePos.getZ());

                if (distSq < closestDistanceSq) {
                    chosenPos = tePos;
                    closestDistanceSq = distSq;
                }
            }
        }

        return chosenPos;
    }
}
