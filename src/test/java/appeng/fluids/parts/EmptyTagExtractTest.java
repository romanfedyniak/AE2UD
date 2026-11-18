/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.fluids.parts;


import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.me.helpers.BaseActionSource;


/**
 * An item or a fluid carrying an empty tag compound gets the same key as one with no tag; that key has to match
 * it, and a tank holding such a fluid has to give it up when the key is asked for.
 */
public final class EmptyTagExtractTest {

    @BeforeAll
    public static void bootstrap() {
        Bootstrap.register();
    }

    @Test
    public void anItemKeyMatchesTheStackItCameFrom() {
        final ItemStack stack = new ItemStack(Items.APPLE);
        stack.setTagCompound(new NBTTagCompound());
        assertTrue(AEItemKey.of(stack).matches(stack));
    }

    @Test
    public void aFluidKeyMatchesTheStackItCameFrom() {
        final FluidStack stack = new FluidStack(FluidRegistry.WATER, 1000, new NBTTagCompound());
        assertTrue(AEFluidKey.of(stack).matches(stack));
    }

    @Test
    public void aTankHoldingAnEmptyTaggedFluidGivesItUp() {
        final FluidTank tank = new FluidTank(16000);
        tank.fill(new FluidStack(FluidRegistry.WATER, 4000, new NBTTagCompound()), true);

        final FluidHandlerAdapter adapter = new FluidHandlerAdapter(tank, false, null);
        final AEFluidKey water = AEFluidKey.of(FluidRegistry.WATER);
        assertEquals(4000, adapter.getAvailableStacks().get(water));
        assertEquals(1000, adapter.extract(water, 1000, Actionable.MODULATE, new BaseActionSource()));
        assertEquals(3000, tank.getFluidAmount());
    }
}
