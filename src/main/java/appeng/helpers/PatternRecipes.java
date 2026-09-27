/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.helpers;

import net.minecraftforge.common.MinecraftForge;

import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.PatternRecipesChangedEvent;
import appeng.hooks.TickHandler;
import appeng.me.Grid;

/**
 * What happens to encoded patterns when recipes change under a running world: everything remembered about their
 * recipes is dropped, addons are told to drop theirs, and every interface decodes its patterns again.
 */
public final class PatternRecipes {

    private PatternRecipes() {
    }

    /**
     * Waits for the end of the server tick, so a reload that clears the recipes and then runs its scripts again
     * has finished before any pattern is read.
     */
    public static void scheduleReload() {
        TickHandler.INSTANCE.addCallable(null, world -> {
            reload();
            return null;
        });
    }

    private static void reload() {
        PatternHelper.clearRecipeCache();
        MinecraftForge.EVENT_BUS.post(new PatternRecipesChangedEvent());

        for (final Grid grid : TickHandler.INSTANCE.getGridList()) {
            for (final IGridNode node : grid.getNodes()) {
                final IGridHost machine = node.getMachine();
                if (machine instanceof IInterfaceHost host) {
                    host.getInterfaceDuality().rereadPatterns();
                }
            }
        }
    }
}
