/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.integration.modules.groovyscript;

import com.cleanroommc.groovyscript.event.GroovyReloadEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import appeng.helpers.PatternRecipes;

/**
 * {@code /gs reload} swaps recipes with no registry event, so patterns would go on making what the old recipes
 * made. Registered only when GroovyScript is loaded.
 */
public final class GroovyScriptReload {

    @SubscribeEvent
    public void onReload(final GroovyReloadEvent event) {
        PatternRecipes.scheduleReload();
    }
}
