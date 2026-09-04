/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.client;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.stacks.AEItemKey;
import appeng.core.AEConfig;
import appeng.core.features.AEFeature;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketNetworkPickBlock;
import appeng.core.transformer.PickBlockPatch;

/**
 * The vanilla pick block key, once vanilla itself has had its turn and found nothing.
 *
 * <p>The whole decision is made here rather than on the server because only this side knows what the
 * crosshair is on. In particular the rule that matters most is decided here: if the block is already
 * somewhere in the player's own inventory, vanilla has just moved it to hand and the network is never
 * asked. Without that, every middle click on the stone underfoot would order a stack from storage.</p>
 */
@SideOnly(Side.CLIENT)
public final class NetworkPickBlock {

    private NetworkPickBlock() {
    }

    /** Called from the tail of {@code Minecraft.middleClickMouse}, on every press. */
    public static void onPickBlock() {
        final Minecraft mc = Minecraft.getMinecraft();
        final EntityPlayerSP player = mc.player;
        final RayTraceResult hit = mc.objectMouseOver;

        if (player == null || hit == null || hit.typeOfHit == RayTraceResult.Type.MISS
                || !AEConfig.instance().isFeatureEnabled(AEFeature.NETWORK_PICK_BLOCK)
                || player.capabilities.isCreativeMode) {
            return;
        }

        // The color applicator takes this same click to read a color off a block, and has already had it.
        if (PickBlockPatch.isColorApplicatorPickBlock(hit, player)) {
            return;
        }

        final ItemStack wanted = pickStack(player, hit);
        if (wanted.isEmpty()) {
            return;
        }

        final AEItemKey what = AEItemKey.of(wanted);
        if (what == null) {
            return;
        }

        final InventoryPlayer inventory = player.inventory;
        final ItemStack held = inventory.getStackInSlot(inventory.currentItem);
        final int amount = player.isSneaking() ? 1 : what.getMaxStackSize();
        final int slot;

        if (what.matches(held) && held.getCount() < Math.min(held.getMaxStackSize(), what.getMaxStackSize())) {
            // Holding a part of a stack of it: fill the hand rather than start a second stack elsewhere.
            slot = inventory.currentItem;
        } else if (inventory.getSlotFor(wanted) != -1) {
            // Vanilla found it in the player's own inventory and has already dealt with the click.
            return;
        } else if (held.isEmpty()) {
            slot = inventory.currentItem;
        } else {
            slot = firstEmptyHotbarSlot(inventory);
            if (slot == -1) {
                return;
            }

            inventory.currentItem = slot;
        }

        NetworkHandler.instance().sendToServer(new PacketNetworkPickBlock(what, amount, slot));
    }

    /** What vanilla would have handed over, for a block or for an entity - the same two questions it asks. */
    private static ItemStack pickStack(final EntityPlayerSP player, final RayTraceResult hit) {
        final World world = player.world;

        if (hit.typeOfHit == RayTraceResult.Type.BLOCK) {
            final BlockPos pos = hit.getBlockPos();
            final IBlockState state = world.getBlockState(pos);

            if (state.getBlock().isAir(state, world, pos)) {
                return ItemStack.EMPTY;
            }

            return state.getBlock().getPickBlock(state, hit, world, pos, player);
        }

        if (hit.typeOfHit == RayTraceResult.Type.ENTITY && hit.entityHit != null) {
            return hit.entityHit.getPickedResult(hit);
        }

        return ItemStack.EMPTY;
    }

    private static int firstEmptyHotbarSlot(final InventoryPlayer inventory) {
        for (int slot = 0; slot < InventoryPlayer.getHotbarSize(); slot++) {
            if (inventory.getStackInSlot(slot).isEmpty()) {
                return slot;
            }
        }

        return -1;
    }
}
