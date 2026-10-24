/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 * Adapted from NAE2's pattern multi-tool (https://github.com/AE2-UEL/NAE2) by NotMyWing.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.items.tools;

import javax.annotation.Nullable;

import baubles.api.BaubleType;
import baubles.api.IBauble;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Optional;

import appeng.api.implementations.guiobjects.IGuiItemObject;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.parts.SelectedPart;
import appeng.api.util.AEPartLocation;
import appeng.core.sync.GuiBridge;
import appeng.helpers.IInterfaceHost;
import appeng.helpers.ISidedGuiItem;
import appeng.helpers.patternmultiplier.PatternMultiplierHost;
import appeng.items.AEBaseItem;
import appeng.util.Platform;

/**
 * Carries up to four rows of patterns, and edits them, or the patterns of an interface it is used on, all at once.
 * Worn as a trinket or carried, it also sits beside the screens that hold patterns.
 */
@Optional.Interface(iface = "baubles.api.IBauble", modid = "baubles")
public class ToolPatternMultiplier extends AEBaseItem implements ISidedGuiItem, IBauble {

    public ToolPatternMultiplier() {
        this.setMaxStackSize(1);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(final World world, final EntityPlayer player,
            final EnumHand hand) {
        if (hand != EnumHand.MAIN_HAND) {
            return new ActionResult<>(EnumActionResult.PASS, player.getHeldItem(hand));
        }
        if (Platform.isServer()) {
            Platform.openGUI(player, player.inventory.currentItem, GuiBridge.GUI_PATTERN_MULTIPLIER, false);
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
    }

    @Override
    public EnumActionResult onItemUseFirst(final EntityPlayer player, final World world, final BlockPos pos,
            final EnumFacing side, final float hitX, final float hitY, final float hitZ, final EnumHand hand) {
        if (hand != EnumHand.MAIN_HAND || player.isSneaking()) {
            return EnumActionResult.PASS;
        }
        final TileEntity tile = world.getTileEntity(pos);
        final AEPartLocation where = interfaceAt(tile, new Vec3d(hitX, hitY, hitZ));
        if (where == null) {
            return EnumActionResult.PASS;
        }
        if (Platform.isServer()) {
            Platform.openGUI(player, tile, where, GuiBridge.GUI_PATTERN_MULTIPLIER);
        }
        return EnumActionResult.SUCCESS;
    }

    /**
     * Where on this tile the interface that was clicked is: the block itself, or the interface part hit.
     */
    @Nullable
    private static AEPartLocation interfaceAt(@Nullable final TileEntity tile, final Vec3d hit) {
        if (tile instanceof IInterfaceHost) {
            return AEPartLocation.INTERNAL;
        }
        if (tile instanceof IPartHost) {
            final SelectedPart selected = ((IPartHost) tile).selectPart(hit);
            if (selected.part instanceof IInterfaceHost) {
                return selected.side;
            }
        }
        return null;
    }

    /**
     * Opened from an inventory or bauble slot: {@code pos} is that slot, not a place in the world.
     */
    @Override
    public IGuiItemObject getGuiObject(final ItemStack stack, final World world, final BlockPos pos) {
        return new PatternMultiplierHost(stack, null);
    }

    @Override
    public IGuiItemObject getGuiObject(final ItemStack stack, final World world, final BlockPos pos,
            final AEPartLocation side) {
        return new PatternMultiplierHost(stack, interfaceOf(world.getTileEntity(pos), side));
    }

    @Nullable
    private static IInterfaceHost interfaceOf(@Nullable final TileEntity tile, final AEPartLocation side) {
        if (tile instanceof IInterfaceHost) {
            return (IInterfaceHost) tile;
        }
        if (tile instanceof IPartHost) {
            final IPart part = ((IPartHost) tile).getPart(side);
            if (part instanceof IInterfaceHost) {
                return (IInterfaceHost) part;
            }
        }
        return null;
    }

    @Optional.Method(modid = "baubles")
    @Override
    public BaubleType getBaubleType(final ItemStack stack) {
        return BaubleType.TRINKET;
    }
}
