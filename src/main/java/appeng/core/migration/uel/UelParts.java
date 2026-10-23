/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.core.migration.uel;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.init.Enchantments;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants;

import appeng.api.migration.LegacyRewrites;
import appeng.api.migration.LegacyStacks;
import appeng.api.stacks.GenericStack;

/**
 * What UEL's parts kept on a cable bus, in AE2UD's shape: filters, the key types a part that handled one type
 * works with, the monitors' key and the pattern terminals' grids. Interfaces are {@link UelInterfaces}.
 */
final class UelParts {

    static final int IDENTITY_ANNIHILATION_PLANE = 301;

    private static final String ITEMS = "appliedenergistics2:item";
    private static final String FLUIDS = "appliedenergistics2:fluid";

    private UelParts() {
    }

    static void register() {
        LegacyRewrites.addPartRewriter("appliedenergistics2:part", UelParts::rewrite);
    }

    private static void rewrite(final NBTTagCompound item, final NBTTagCompound part) {
        final int meta = item.getShort("Damage");
        final String where = "part " + meta;
        switch (meta) {
            // storage, ore dictionary storage, import and formation: items only, as they were
            case 220:
            case 222:
            case 240:
            case 320:
                LegacyStacks.rewriteInventory(part.getCompoundTag("config"), where);
                onlyKeyType(part, ITEMS);
                break;
            case 221:
            case 241:
            case 321:
                LegacyStacks.rewriteInventory(part.getCompoundTag("config"), where);
                onlyKeyType(part, FLUIDS);
                break;
            case 260:
            case 261:
            case 280:
            case 281:
                LegacyStacks.rewriteInventory(part.getCompoundTag("config"), where);
                break;
            case 300:
                onlyKeyType(part, ITEMS);
                break;
            case IDENTITY_ANNIHILATION_PLANE:
                onlyKeyType(part, ITEMS);
                final NBTTagList enchantments = part.getTagList("Enchantments", Constants.NBT.TAG_COMPOUND);
                enchantments.appendTag(silkTouch());
                part.setTag("Enchantments", enchantments);
                break;
            case 302:
                onlyKeyType(part, FLUIDS);
                break;
            case 340:
                if (!part.getBoolean("craftingMode")) {
                    UelGrids.moveToProcessing(part, "crafting", "processing", 3);
                }
                break;
            case 341:
                UelGrids.moveToProcessing(part, "crafting", "processing", 4);
                part.setString("encodingMode", "appliedenergistics2:processing");
                break;
            case 400:
            case 420:
                monitor(part, where);
                break;
            case 440:
                UelInterfaces.rewriteItemInterface(part, where);
                break;
            case 441:
                UelInterfaces.rewriteFluidInterface(part, where);
                break;
            default:
                break;
        }
    }

    /**
     * A part that handled one key type on UEL handles every type on AE2UD unless told otherwise.
     */
    private static void onlyKeyType(final NBTTagCompound part, final String type) {
        final NBTTagList types = new NBTTagList();
        types.appendTag(new NBTTagString(type));
        part.setTag("enabledKeyTypes", types);
    }

    /**
     * UEL kept a monitor's item and fluid apart; AE2UD keeps one key in its config slot.
     */
    private static void monitor(final NBTTagCompound part, final String where) {
        for (final String key : new String[] { "configuredItem", "configuredFluid" }) {
            if (!part.hasKey(key, Constants.NBT.TAG_COMPOUND)) {
                continue;
            }
            final NBTTagCompound saved = part.getCompoundTag(key);
            part.removeTag(key);
            if (saved.isEmpty() || part.hasKey("config")) {
                continue;
            }
            final GenericStack read = LegacyStacks.read(saved);
            if (read == null) {
                LegacyRewrites.log().lost(where + ", the key it showed: " + saved);
                continue;
            }
            final NBTTagCompound slot = new NBTTagCompound();
            GenericStack.writeTag(slot, new GenericStack(read.what(), 0));
            final NBTTagCompound config = new NBTTagCompound();
            config.setTag("#0", slot);
            part.setTag("config", config);
        }
    }

    static NBTTagCompound silkTouch() {
        final NBTTagCompound silkTouch = new NBTTagCompound();
        silkTouch.setShort("id", (short) Enchantment.getEnchantmentID(Enchantments.SILK_TOUCH));
        silkTouch.setShort("lvl", (short) 1);
        return silkTouch;
    }
}
