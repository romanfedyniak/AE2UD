/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2UD contributors
 */

package appeng.api.networking.crafting;

import net.minecraftforge.fml.common.eventhandler.Event;

/**
 * Posted on the Forge event bus, on the server thread, once recipes have changed while a world is running - a
 * GroovyScript reload. A pattern that keeps what it found out about its recipe forgets it here. The interfaces
 * decode their patterns again right after, and the networks relearn what they can craft; crafts already running
 * are left alone.
 */
public class PatternRecipesChangedEvent extends Event {
}
