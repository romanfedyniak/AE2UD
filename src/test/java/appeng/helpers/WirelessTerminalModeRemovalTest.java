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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;

import appeng.core.features.registries.WirelessTerminalMode;

/**
 * What taking a mode out of a wireless terminal leaves on it, and what it hands over.
 */
class WirelessTerminalModeRemovalTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        Bootstrap.register();
        fakeServerSide();
    }

    @Test
    void theModeGoesAndTheRestStay() {
        final ItemStack terminal = terminal(WirelessTerminalMode.Ids.TERMINAL, WirelessTerminalMode.Ids.CRAFTING,
                WirelessTerminalMode.Ids.PATTERN);
        WirelessTerminalModes.setModeId(terminal, WirelessTerminalMode.Ids.CRAFTING);
        terminal.getTagCompound().setDouble("internalCurrentPower", 1000);

        final ItemStack result = WirelessTerminalModeRemoval.takeOut(terminal, WirelessTerminalMode.Ids.CRAFTING,
                new ArrayList<>());

        assertEquals(Arrays.asList(WirelessTerminalMode.Ids.TERMINAL, WirelessTerminalMode.Ids.PATTERN),
                WirelessTerminalModes.getUnlocked(result));
        assertNull(WirelessTerminalModes.getModeId(result));
        assertEquals(1000, result.getTagCompound().getDouble("internalCurrentPower"));
        assertEquals(3, WirelessTerminalModes.getUnlocked(terminal).size(), "the grid's own stack is left alone");
    }

    @Test
    void theLastModeNeverComesOut() {
        final ItemStack terminal = terminal(WirelessTerminalMode.Ids.PATTERN);
        assertFalse(WirelessTerminalModeRemoval.canTakeOut(terminal, WirelessTerminalMode.Ids.PATTERN));

        final ItemStack two = terminal(WirelessTerminalMode.Ids.PATTERN, WirelessTerminalMode.Ids.CRAFTING);
        assertTrue(WirelessTerminalModeRemoval.canTakeOut(two, WirelessTerminalMode.Ids.PATTERN));
        assertFalse(WirelessTerminalModeRemoval.canTakeOut(two, WirelessTerminalMode.Ids.PATTERN_ACCESS));
    }

    @Test
    void aPlayerIsHandedTheRealItemsAndTheModeForgetsItsData() {
        final ItemStack terminal = terminal(WirelessTerminalMode.Ids.TERMINAL, WirelessTerminalMode.Ids.PATTERN);
        final NBTTagCompound data = new NBTTagCompound();
        data.setTag("patterns", inventory(new ItemStack(Items.PAPER, 40), new ItemStack(Items.BOOK)));
        data.setTag("craftingGrid", inventory(new ItemStack(Items.IRON_INGOT)));
        WirelessTerminalModes.setModeData(terminal, WirelessTerminalMode.Ids.PATTERN, data);

        final List<ItemStack> handedOut = new ArrayList<>();
        final ItemStack result = WirelessTerminalModeRemoval.takeOut(terminal, WirelessTerminalMode.Ids.PATTERN,
                handedOut);

        assertEquals(2, handedOut.size(), "the pattern mode's crafting grid holds ghosts, not items");
        assertEquals(Items.PAPER, handedOut.get(0).getItem());
        assertEquals(40, handedOut.get(0).getCount());
        assertEquals(Items.BOOK, handedOut.get(1).getItem());
        assertTrue(WirelessTerminalModes.getModeData(result, WirelessTerminalMode.Ids.PATTERN).isEmpty());
    }

    @Test
    void withNobodyToHandThemToTheItemsStayForTheModesReturn() {
        final ItemStack terminal = terminal(WirelessTerminalMode.Ids.TERMINAL, WirelessTerminalMode.Ids.CRAFTING);
        final NBTTagCompound data = new NBTTagCompound();
        data.setTag("craftingGrid", inventory(new ItemStack(Items.IRON_INGOT, 5)));
        WirelessTerminalModes.setModeData(terminal, WirelessTerminalMode.Ids.CRAFTING, data);

        final ItemStack result = WirelessTerminalModeRemoval.takeOut(terminal, WirelessTerminalMode.Ids.CRAFTING,
                null);

        assertEquals(Collections.singletonList(WirelessTerminalMode.Ids.TERMINAL),
                WirelessTerminalModes.getUnlocked(result));
        assertTrue(WirelessTerminalModes.getModeData(result, WirelessTerminalMode.Ids.CRAFTING)
                .hasKey("craftingGrid"));

        WirelessTerminalModes.unlock(result, WirelessTerminalMode.Ids.CRAFTING);
        assertEquals(1, WirelessTerminalModes.getModeData(result, WirelessTerminalMode.Ids.CRAFTING)
                .getCompoundTag("craftingGrid").getTagList("Items", 10).tagCount());
    }

    private static ItemStack terminal(final ResourceLocation... modes) {
        final ItemStack terminal = new ItemStack(Items.COMPASS);
        WirelessTerminalModes.setUnlocked(terminal, Arrays.asList(modes));
        return terminal;
    }

    /** {@code Platform}, which opens an item's tag, asks FML for the side as it loads. */
    private static void fakeServerSide() throws Exception {
        final Class<?> handlerType = Class.forName("net.minecraftforge.fml.common.IFMLSidedHandler");
        final Object delegate = Proxy.newProxyInstance(handlerType.getClassLoader(),
                new Class<?>[] { handlerType },
                (proxy, method, args) -> "getSide".equals(method.getName()) ? Side.SERVER : null);

        final Field field = FMLCommonHandler.class.getDeclaredField("sidedDelegate");
        field.setAccessible(true);
        field.set(FMLCommonHandler.instance(), delegate);
    }

    private static NBTTagCompound inventory(final ItemStack... stacks) {
        final NBTTagList items = new NBTTagList();
        for (int slot = 0; slot < stacks.length; slot++) {
            final NBTTagCompound item = ItemStackHelper.stackToNBT(stacks[slot]);
            item.setInteger("Slot", slot);
            items.appendTag(item);
        }
        final NBTTagCompound inventory = new NBTTagCompound();
        inventory.setTag("Items", items);
        inventory.setInteger("Size", stacks.length);
        return inventory;
    }
}
