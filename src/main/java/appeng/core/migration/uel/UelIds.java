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

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import appeng.api.migration.LegacyRewrites;
import appeng.api.migration.LegacyStacks;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;

/**
 * UEL's ids that AE2UD renamed or merged: the fluid interface, the fluid terminal, the fluid placeholder item,
 * the fluid cell parts and the parts that had a fluid twin.
 */
final class UelIds {

    private static final String AE = "appliedenergistics2:";

    private UelIds() {
    }

    static void register() {
        LegacyRewrites.remapBlock(AE + "fluid_interface", AE + "interface");
        LegacyRewrites.remapItem(AE + "wireless_fluid_terminal", AE + "wireless_terminal");
        LegacyStacks.addWrapper(AE + "dummy_fluid_item", UelIds::fluidPlaceholder);
        LegacyRewrites.addStackRewriter(AE + "material", UelIds::material);
        LegacyRewrites.addStackRewriter(AE + "part", UelIds::part);
    }

    private static GenericStack fluidPlaceholder(final NBTTagCompound stack) {
        final NBTTagCompound fluid = stack.getCompoundTag("tag");
        final AEFluidKey key = LegacyStacks.fluid(fluid.getString("FluidName"), fluid, "Tag");
        if (key == null) {
            return null;
        }
        return new GenericStack(key, (long) fluid.getInteger("Amount") * Math.max(1, stack.getInteger("Count")));
    }

    /**
     * The fluid cell components 54-57 are the item cell components 35-38 now.
     */
    private static void material(final NBTTagCompound stack) {
        final int meta = stack.getShort("Damage");
        if (meta >= 54 && meta <= 57) {
            stack.setShort("Damage", (short) (meta - 19));
        }
    }

    private static void part(final NBTTagCompound stack) {
        final int meta = stack.getShort("Damage");
        final int merged = mergedPart(meta);
        if (merged == meta) {
            return;
        }
        stack.setShort("Damage", (short) merged);
        if (meta == UelParts.IDENTITY_ANNIHILATION_PLANE) {
            addSilkTouch(stack);
        }
    }

    /**
     * @return the part a fluid or otherwise retired part was merged into, or the same meta.
     */
    static int mergedPart(final int meta) {
        switch (meta) {
            case 221:
                return 220;
            case 241:
                return 240;
            case 261:
                return 260;
            case 281:
                return 280;
            case 301:
            case 302:
                return 300;
            case 321:
                return 320;
            case 341:
                return 340;
            case 441:
                return 440;
            case 520:
                return 380;
            case 522:
                return 521;
            default:
                return meta;
        }
    }

    /**
     * What UEL itself did to an identity plane taken in hand: a plain plane with Silk Touch.
     */
    private static void addSilkTouch(final NBTTagCompound stack) {
        final NBTTagCompound tag = stack.getCompoundTag("tag");
        final NBTTagList enchantments = tag.getTagList("ench", Constants.NBT.TAG_COMPOUND);
        enchantments.appendTag(UelParts.silkTouch());
        tag.setTag("ench", enchantments);
        stack.setTag("tag", tag);
    }
}
