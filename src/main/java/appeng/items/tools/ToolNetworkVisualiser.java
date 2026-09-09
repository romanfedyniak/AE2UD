/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.items.tools;


import appeng.api.networking.IGridHost;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.items.AEBaseItem;
import appeng.me.visualiser.NetworkVisualiserService;
import appeng.me.visualiser.VisualiserMode;
import appeng.util.Platform;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;

import javax.annotation.Nullable;

import java.util.List;


/**
 * Draws a network in the world, so that a cable that is not carrying what it should can be found by looking
 * at it rather than by taking it apart.
 * <p>
 * The idea, and the shape of the tool, come from AE2Stuff by bdew and from the port of it in GregTech: New
 * Horizons; none of the code does.
 */
public class ToolNetworkVisualiser extends AEBaseItem {

    private static final String NBT_POS = "visPos";
    private static final String NBT_DIM = "visDim";
    private static final String NBT_SIDE = "visSide";
    private static final String NBT_MODE = "visMode";

    public ToolNetworkVisualiser() {
        this.setMaxStackSize(1);
    }

    /**
     * Binds to the side that was clicked rather than to the block, so that clicking the face of a P2P tunnel
     * shows the sub-network behind it - that face has a node of its own.
     * <p>
     * Taken before the block gets the click, the way the network tool takes its own, or a machine with a
     * screen would open it instead of being bound to.
     */
    @Override
    public EnumActionResult onItemUseFirst(final EntityPlayer player, final World world, final BlockPos pos,
            final EnumFacing side, final float hitX, final float hitY, final float hitZ, final EnumHand hand) {
        final TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof IGridHost)) {
            return EnumActionResult.PASS;
        }

        if (Platform.isServer()) {
            final AEPartLocation location = ((IGridHost) te).getGridNode(AEPartLocation.fromFacing(side)) != null
                    ? AEPartLocation.fromFacing(side)
                    : AEPartLocation.INTERNAL;

            final ItemStack held = player.getHeldItem(hand);
            final NBTTagCompound tag = tagOf(held);
            tag.setLong(NBT_POS, pos.toLong());
            tag.setInteger(NBT_DIM, world.provider.getDimension());
            tag.setByte(NBT_SIDE, (byte) location.ordinal());

            player.sendStatusMessage(new TextComponentTranslation("chat.appliedenergistics2.VisualiserBound",
                    pos.getX(), pos.getY(), pos.getZ()), true);
        }

        return EnumActionResult.SUCCESS;
    }

    /** In the air: the next mode, or sneaking, forget the network and stop drawing. */
    @Override
    public ActionResult<ItemStack> onItemRightClick(final World world, final EntityPlayer player,
            final EnumHand hand) {
        final ItemStack held = player.getHeldItem(hand);

        if (Platform.isServer()) {
            if (player.isSneaking()) {
                this.unbind(held, player);
            } else {
                final VisualiserMode mode = getMode(held).next();
                tagOf(held).setByte(NBT_MODE, (byte) mode.ordinal());

                player.sendStatusMessage(new TextComponentTranslation("chat.appliedenergistics2.VisualiserMode",
                        new TextComponentTranslation(mode.getUnlocalizedName())), true);
            }
        }

        return new ActionResult<>(EnumActionResult.SUCCESS, held);
    }

    /**
     * A held visualiser asks for a picture every tick; the service decides how often one is actually built.
     */
    @Override
    public void onUpdate(final ItemStack stack, final World world, final Entity entity, final int slot,
            final boolean isSelected) {
        if (!isSelected || Platform.isClient() || !(entity instanceof EntityPlayerMP)) {
            return;
        }

        final NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(NBT_POS) || tag.getInteger(NBT_DIM) != world.provider.getDimension()) {
            return;
        }

        NetworkVisualiserService.INSTANCE.watch((EntityPlayerMP) entity, world, BlockPos.fromLong(tag.getLong(NBT_POS)),
                AEPartLocation.fromOrdinal(tag.getByte(NBT_SIDE)));
    }

    @Override
    protected void addCheckedInformation(final ItemStack stack, final World world, final List<String> lines,
            final ITooltipFlag advancedTooltips) {
        lines.add(I18n.translateToLocalFormatted("item.appliedenergistics2.network_visualiser.mode",
                I18n.translateToLocal(getMode(stack).getUnlocalizedName())));

        final DimensionalCoord bound = getBound(stack, world);
        if (bound != null) {
            lines.add(I18n.translateToLocalFormatted("item.appliedenergistics2.network_visualiser.bound", bound.x,
                    bound.y, bound.z));
        }
    }

    private void unbind(final ItemStack stack, final EntityPlayer player) {
        final NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(NBT_POS)) {
            return;
        }

        tag.removeTag(NBT_POS);
        tag.removeTag(NBT_DIM);
        tag.removeTag(NBT_SIDE);

        if (player instanceof EntityPlayerMP) {
            NetworkVisualiserService.INSTANCE.stopWatching((EntityPlayerMP) player);
        }

        player.sendStatusMessage(new TextComponentTranslation("chat.appliedenergistics2.VisualiserUnbound"), true);
    }

    public static VisualiserMode getMode(final ItemStack stack) {
        final NBTTagCompound tag = stack.getTagCompound();

        return tag == null ? VisualiserMode.FULL : VisualiserMode.byOrdinal(tag.getByte(NBT_MODE));
    }

    /** Where this is pointed, or null if it is pointed nowhere or into another dimension. */
    @Nullable
    public static DimensionalCoord getBound(final ItemStack stack, @Nullable final World world) {
        final NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(NBT_POS)) {
            return null;
        }

        if (world == null || tag.getInteger(NBT_DIM) != world.provider.getDimension()) {
            return null;
        }

        return new DimensionalCoord(world, BlockPos.fromLong(tag.getLong(NBT_POS)));
    }

    private static NBTTagCompound tagOf(final ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();

        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }

        return tag;
    }
}
