/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.crafting;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.NBTTagCompound;

import appeng.api.networking.IGridHost;
import appeng.api.networking.crafting.ICraftingRequester;

class CraftingLinkNexusTest {

    private static ICraftingRequester requester() {
        return (ICraftingRequester) Proxy.newProxyInstance(CraftingLinkNexusTest.class.getClassLoader(),
                new Class<?>[] { ICraftingRequester.class, IGridHost.class }, (proxy, method, args) -> {
                    if ("equals".equals(method.getName())) {
                        return proxy == args[0];
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    return null;
                });
    }

    @Test
    void theMachineThatAskedIsRecognisedWhenItLeavesTheGrid() {
        final NBTTagCompound data = new NBTTagCompound();
        data.setString("CraftID", "test");
        data.setBoolean("req", true);
        final ICraftingRequester machine = requester();
        final CraftingLinkNexus nexus = new CraftingLinkNexus("test");
        nexus.add(new CraftingLink(data, machine));

        assertTrue(nexus.isMachine((IGridHost) machine));
        assertFalse(nexus.isMachine((IGridHost) requester()));
    }
}
