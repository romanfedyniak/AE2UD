/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.core.sync.packets;


import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.registries.RegistryBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.KeyCounter;
import appeng.core.api.AEItemKeyType;


/**
 * A cell's contents on their way to the view window: cut into parts, and whole again at the other end.
 */
public final class PacketCellViewContentsTest {

    @BeforeAll
    public static void bootstrap() {
        Bootstrap.register();
        if (GameRegistry.findRegistry(AEKeyType.class) == null) {
            new RegistryBuilder<AEKeyType>()
                    .setName(AEKeyTypes.REGISTRY_NAME)
                    .setType(AEKeyType.class)
                    .setIDRange(0, 127)
                    .create();
            AEKeyTypes.register(new AEItemKeyType());
        }
    }

    @Test
    public void anEmptyCellIsOneEmptyPart() throws IOException {
        final List<byte[]> parts = PacketCellViewContents.encodeParts(new Object2LongOpenHashMap<>());

        assertEquals(1, parts.size());
        assertTrue(PacketCellViewContents.decodePart(parts.get(0)).isEmpty());
    }

    @Test
    public void contentsTooBigForOnePacketArriveWhole() throws IOException {
        final char[] filler = new char[10_000];
        Arrays.fill(filler, 'x');

        final Object2LongMap<AEKey> sent = new Object2LongOpenHashMap<>();
        for (int n = 0; n < 1000; n++) {
            final ItemStack stack = new ItemStack(Items.PAPER);
            final NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("n", n);
            tag.setString("filler", new String(filler));
            stack.setTagCompound(tag);
            sent.put(AEItemKey.of(stack), n + 1);
        }

        final List<byte[]> parts = PacketCellViewContents.encodeParts(sent);
        assertTrue(parts.size() > 1, "a thousand ten-kilobyte entries should not fit one part");

        final KeyCounter received = new KeyCounter();
        for (final byte[] part : parts) {
            for (final Object2LongMap.Entry<AEKey> entry : PacketCellViewContents.decodePart(part)) {
                received.add(entry.getKey(), entry.getLongValue());
            }
        }

        assertEquals(sent.size(), received.size());
        for (final Object2LongMap.Entry<AEKey> entry : sent.object2LongEntrySet()) {
            assertEquals(entry.getLongValue(), received.get(entry.getKey()));
        }
    }
}
