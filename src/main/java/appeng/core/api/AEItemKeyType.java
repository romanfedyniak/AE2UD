/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.core.api;


import java.io.IOException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;


/**
 * The concrete {@link AEKeyType} for {@link AEItemKey}, registered under {@link AEKeyTypes#ITEMS_ID}.
 * <p/>
 * {@code src/api} only defines the abstract {@link AEKeyType} contract (CONTRACT.md §1.2/§1.3); it cannot provide
 * the built-in implementations itself because they need a registered item ({@link #getButtonIcon()}) and other
 * {@code src/main}-only concerns. This mirrors how AE2-original's {@code AEItemKeys} singleton is implemented, but
 * lives in {@code src/main} for the same source-set-isolation reason {@code GenericStack.Wrapper} does
 * (CONTRACT.md §8 item 3/4).
 */
public final class AEItemKeyType extends AEKeyType {

    public AEItemKeyType() {
        super(AEKeyTypes.ITEMS_ID, AEItemKey.class, new TextComponentString("Items"));
    }

    @Override
    public AEKey readFromPacket(@Nonnull final ByteBuf input) throws IOException {
        return AEItemKey.fromPacket(input);
    }

    @Nullable
    @Override
    public AEKey loadKeyFromTag(@Nonnull final NBTTagCompound tag) {
        return AEItemKey.fromTag(tag);
    }

    @Override
    public boolean supportsFuzzyRangeSearch() {
        // Items are searchable by remaining durability range; see AEItemKey#getFuzzySearchValue/MaxValue.
        return true;
    }

    @Override
    public ResourceLocation getButtonTexture() {
        return new ResourceLocation("appliedenergistics2", "textures/guis/states.png");
    }

    @Override
    public ItemStack getButtonIcon() {
        return new ItemStack(Blocks.CHEST);
    }

}
