/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.server.subcommands;


import appeng.api.features.IWorldGen.WorldGenType;
import appeng.core.features.registries.WorldGenRegistry;
import appeng.core.localization.PlayerMessages;
import appeng.server.ISubCommand;
import appeng.worldgen.meteorite.MapGenMeteorite;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;


/**
 * Lists where the meteorites around you are, worked out from the world seed without touching the ground.
 * <p>
 * This is the only way to see that placement really is decided by the seed: run it on two worlds made from
 * the same seed and the lists must match, however differently the two were explored. It loads no chunks and
 * generates nothing - what it prints is a prediction, and a meteorite it names may not have been built yet.
 */
public class TestMeteoritesCommand implements ISubCommand {

    private static final int DEFAULT_RANGE = 2000;
    private static final int MAX_RANGE = 20000;
    private static final int MAX_LISTED = 20;

    @Override
    public String getHelp(final MinecraftServer srv) {
        return "commands.ae2.Meteorites";
    }

    @Override
    public void call(final MinecraftServer srv, final String[] args, final ICommandSender sender) {
        final World world = sender.getEntityWorld();

        if (!WorldGenRegistry.INSTANCE.isWorldGenEnabled(WorldGenType.METEORITES, world)) {
            sender.sendMessage(PlayerMessages.MeteoritesDisabled.get());
            return;
        }

        // args[0] is the subcommand's own name, which the dispatcher passes along.
        final int range = Math.min(MAX_RANGE, args.length > 1 ? parseRange(args[1]) : DEFAULT_RANGE);
        final BlockPos origin = sender.getPosition();
        final List<BlockPos> found = predict(world, origin, range);

        found.sort(Comparator.comparingDouble(pos -> distance(origin, pos)));

        sender.sendMessage(PlayerMessages.MeteoritesFound.get(found.size(), range));

        for (int i = 0; i < Math.min(MAX_LISTED, found.size()); i++) {
            final BlockPos pos = found.get(i);
            sender.sendMessage(PlayerMessages.MeteoriteAt.get(pos.getX(), pos.getZ(),
                    (int) Math.round(distance(origin, pos))));
        }
    }

    private static int parseRange(final String arg) {
        try {
            return Math.max(1, Integer.parseInt(arg));
        } catch (final NumberFormatException e) {
            return DEFAULT_RANGE;
        }
    }

    /** One meteorite per grid cell, so the cells are what gets walked rather than the chunks. */
    private static List<BlockPos> predict(final World world, final BlockPos origin, final int range) {
        final int cellSize = MapGenMeteorite.gridCellSize();
        final int minGridX = Math.floorDiv(origin.getX() - range, cellSize);
        final int maxGridX = Math.floorDiv(origin.getX() + range, cellSize);
        final int minGridZ = Math.floorDiv(origin.getZ() - range, cellSize);
        final int maxGridZ = Math.floorDiv(origin.getZ() + range, cellSize);

        final List<BlockPos> found = new ArrayList<>();

        for (int gridX = minGridX; gridX <= maxGridX; gridX++) {
            for (int gridZ = minGridZ; gridZ <= maxGridZ; gridZ++) {
                final BlockPos pos = MapGenMeteorite.meteoriteIn(world.getSeed(), gridX, gridZ);

                if (distance(origin, pos) <= range) {
                    found.add(pos);
                }
            }
        }

        return found;
    }

    private static double distance(final BlockPos origin, final BlockPos pos) {
        final double dx = pos.getX() - origin.getX();
        final double dz = pos.getZ() - origin.getZ();

        return Math.sqrt(dx * dx + dz * dz);
    }
}
