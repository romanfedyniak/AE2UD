/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.api.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.init.Bootstrap;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.registries.RegistryBuilder;

import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.core.api.AEFluidKeyType;
import appeng.core.api.AEItemKeyType;

/**
 * A selection keeps at least one type on, however it is set, and tells its owner when it changes.
 */
class KeyTypeSelectionTest {

    @BeforeAll
    static void registerKeyTypes() {
        Bootstrap.register();
        if (GameRegistry.findRegistry(AEKeyType.class) == null) {
            new RegistryBuilder<AEKeyType>()
                    .setName(AEKeyTypes.REGISTRY_NAME)
                    .setType(AEKeyType.class)
                    .setIDRange(0, 127)
                    .create();
        }
        if (AEKeyTypes.get(new ResourceLocation("appliedenergistics2", "item")) == null) {
            AEKeyTypes.register(new AEItemKeyType());
        }
        if (AEKeyTypes.get(new ResourceLocation("appliedenergistics2", "fluid")) == null) {
            AEKeyTypes.register(new AEFluidKeyType());
        }
    }

    @Test
    void anEmptySetLeavesOneTypeOn() {
        final KeyTypeSelection selection = new KeyTypeSelection(() -> {
        }, type -> true);

        selection.setEnabledSet(Collections.emptyList());

        assertFalse(selection.enabledSet().isEmpty());
    }

    @Test
    void settingTheSetTellsTheOwner() {
        final AtomicInteger told = new AtomicInteger();
        final KeyTypeSelection selection = new KeyTypeSelection(told::incrementAndGet, type -> true);

        selection.setEnabledSet(Collections.singletonList(AEKeyTypes.fluids()));

        assertTrue(selection.isEnabled(AEKeyTypes.fluids()));
        assertFalse(selection.isEnabled(AEKeyTypes.items()));
        assertEquals(1, told.get());
    }
}
