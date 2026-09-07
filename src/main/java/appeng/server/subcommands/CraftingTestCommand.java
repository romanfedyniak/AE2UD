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


import appeng.core.localization.PlayerMessages;
import appeng.debug.TileCraftingTestRig;
import appeng.debug.craftingtest.TestKeys;
import appeng.debug.craftingtest.TestRunner;
import appeng.debug.craftingtest.TestScenario;
import appeng.debug.craftingtest.TestScenarios;
import appeng.server.ISubCommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;


/**
 * Runs the crafting scenarios against the rig standing next to you, and says how the answers differ from the
 * ones recorded as right.
 * <p>
 * {@code /ae2 CraftingTest} runs them all and reports differences; {@code baseline} records this run as the
 * answers to compare against from now on; naming scenarios runs only those. Recording is deliberately a
 * separate word rather than something that happens when no baseline exists: a baseline is an agreement that
 * the current answers are correct, and nothing should be able to make that agreement by accident.
 */
public class CraftingTestCommand implements ISubCommand {

    private static final String BASELINE_ARG = "baseline";
    private static final int SEARCH_RADIUS = 16;
    private static final int SEARCH_HEIGHT = 8;

    @Override
    public String getHelp(final MinecraftServer srv) {
        return "commands.ae2.CraftingTest";
    }

    @Override
    public void call(final MinecraftServer srv, final String[] args, final ICommandSender sender) {
        final TileCraftingTestRig rig = findRig(sender.getEntityWorld(), sender.getPosition());

        if (rig == null) {
            sender.sendMessage(PlayerMessages.CraftingTestNoRig.get(SEARCH_RADIUS));
            return;
        }

        if (rig.getRunner() != null) {
            sender.sendMessage(PlayerMessages.CraftingTestBusy.get());
            return;
        }

        // args[0] is the subcommand's own name, which the dispatcher passes along.
        final List<String> rest = Arrays.asList(args).subList(Math.min(1, args.length), args.length);
        final boolean recordBaseline = !rest.isEmpty() && BASELINE_ARG.equalsIgnoreCase(rest.get(0));
        final Set<String> wanted = new LinkedHashSet<>(rest.subList(recordBaseline ? 1 : 0, rest.size()));

        final TestKeys keys = new TestKeys();
        // Built in full even when only some are run, so every scenario keeps the keys it would have had.
        final List<TestScenario> all = TestScenarios.build(keys);
        final List<TestScenario> chosen = new ArrayList<>();

        for (final TestScenario scenario : all) {
            if (wanted.isEmpty() || wanted.contains(scenario.getName())) {
                chosen.add(scenario);
            }
        }

        if (chosen.isEmpty()) {
            sender.sendMessage(PlayerMessages.CraftingTestNoScenario.get(String.join(", ", wanted)));
            return;
        }

        sender.sendMessage(PlayerMessages.CraftingTestStarted.get(chosen.size(),
                recordBaseline ? BASELINE_ARG : "compare"));

        final EntityPlayer player = sender instanceof EntityPlayer entityPlayer ? entityPlayer : null;
        rig.setRunner(new TestRunner(rig, keys, chosen, sender, player, recordBaseline));
    }

    /**
     * The nearest rig, so the command needs no coordinates. Searched rather than remembered because a rig is
     * torn down and rebuilt constantly while a change is being worked on.
     */
    @Nullable
    private static TileCraftingTestRig findRig(final World world, final BlockPos origin) {
        TileCraftingTestRig closest = null;
        double closestDistance = Double.MAX_VALUE;

        for (int x = -SEARCH_RADIUS; x <= SEARCH_RADIUS; x++) {
            for (int z = -SEARCH_RADIUS; z <= SEARCH_RADIUS; z++) {
                for (int y = -SEARCH_HEIGHT; y <= SEARCH_HEIGHT; y++) {
                    final BlockPos pos = origin.add(x, y, z);

                    if (!world.isBlockLoaded(pos)) {
                        continue;
                    }

                    final TileEntity tile = world.getTileEntity(pos);

                    if (tile instanceof TileCraftingTestRig found) {
                        final double distance = origin.distanceSq(pos);

                        if (distance < closestDistance) {
                            closestDistance = distance;
                            closest = found;
                        }
                    }
                }
            }
        }

        return closest;
    }
}
