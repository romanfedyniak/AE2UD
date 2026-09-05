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

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.SPacketSoundEffect;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.World;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.IPatternContainer;
import appeng.api.stacks.AEItemKey;
import appeng.container.AEBaseContainer;
import appeng.core.localization.PlayerMessages;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.packets.PacketSwitchGuis;

/**
 * Sending an encoded pattern from a pattern terminal into the network. A crafting pattern goes on its own,
 * since only an interface beside a plan-taking machine will run one; a processing pattern is the player's
 * choice, since any interface will. Shift asks either way. What may go where is
 * {@link IPatternContainer#canAccept}, not here.
 */
public final class PatternUpload {

    /** What a button press is worth: {@code GuiButton.playPressSound} plays its click at a quarter. */
    private static final float PRESS_VOLUME = 0.25F;

    private PatternUpload() {
    }

    /** Null when the stack is not an encoded pattern, or is one nothing can decode. */
    @Nullable
    public static ICraftingPatternDetails detailsOf(final ItemStack pattern, final World world) {
        if (pattern.isEmpty() || !(pattern.getItem() instanceof ICraftingPatternItem)) {
            return null;
        }

        return ((ICraftingPatternItem) pattern.getItem()).getPatternForItem(pattern, world);
    }

    /**
     * The terminal's button: files the pattern when there is one sensible answer, otherwise opens the screen
     * that asks.
     *
     * @param pick true when the player asked to choose rather than have it decided.
     */
    public static void run(final EntityPlayerMP player, final AEBaseContainer from, final IPatternUploadHost host,
            final boolean pick) {
        final ItemStack pattern = host.getEncodedPattern();
        final ICraftingPatternDetails details = detailsOf(pattern, player.world);

        if (details == null) {
            player.sendMessage(PlayerMessages.PatternUploadNoPattern.get());
            return;
        }

        final List<IPatternContainer> containers = PatternContainers.visible(gridOf(host));
        if (containers.isEmpty()) {
            player.sendMessage(PlayerMessages.PatternUploadNoTarget.get());
            return;
        }

        if (!pick && details.isCraftable()) {
            final IPatternContainer best = PatternContainers.best(containers, player, pattern, details);
            if (best != null) {
                fileInto(player, host, best, pattern, details);
                return;
            }
        }

        PacketSwitchGuis.reopen(player, from, GuiBridge.GUI_PATTERN_UPLOAD);
    }

    /** A row was clicked. Success goes back to the terminal; a refusal leaves the list up to read against. */
    public static void runTo(final EntityPlayerMP player, final AEBaseContainer from, final IPatternUploadHost host,
            @Nullable final IPatternContainer target) {
        if (target == null) {
            player.sendMessage(PlayerMessages.PatternUploadNoTarget.get());
            return;
        }

        final ItemStack pattern = host.getEncodedPattern();
        final ICraftingPatternDetails details = detailsOf(pattern, player.world);

        if (details == null) {
            player.sendMessage(PlayerMessages.PatternUploadNoPattern.get());
            return;
        }

        if (fileInto(player, host, target, pattern, details)) {
            PacketSwitchGuis.reopen(player, from, host.getGuiBridge());
        }
    }

    /**
     * @return true when the pattern is now in that container and gone from the terminal.
     */
    private static boolean fileInto(final EntityPlayerMP player, final IPatternUploadHost host,
            final IPatternContainer target, final ItemStack pattern, final ICraftingPatternDetails details) {
        final AEItemKey key = AEItemKey.of(pattern);
        if (key == null) {
            player.sendMessage(PlayerMessages.PatternUploadNoPattern.get());
            return false;
        }

        // Asked again because the list the player clicked is half a second old.
        if (!target.canAccept(pattern, details)) {
            player.sendMessage(PlayerMessages.PatternUploadUnsuitable.get());
            return false;
        }

        // Per container, not per network: a copy elsewhere is how crafting runs on two sets of machines.
        if (target.containsPattern(key)) {
            player.sendMessage(PlayerMessages.PatternUploadDuplicate.get());
            return false;
        }

        final ItemStack one = pattern.copy();
        one.setCount(1);

        if (!PatternContainers.insert(target, one)) {
            player.sendMessage(PlayerMessages.PatternUploadNoRoom.get());
            return false;
        }

        host.setEncodedPattern(ItemStack.EMPTY);
        click(player);
        return true;
    }

    /**
     * The click a button makes, to the one player who asked for it. Not {@code World.playSound}: on a server
     * that plays to everyone *but* the player named, assuming they have already heard it locally.
     */
    private static void click(final EntityPlayerMP player) {
        player.connection.sendPacket(new SPacketSoundEffect(SoundEvents.UI_BUTTON_CLICK, SoundCategory.MASTER,
                player.posX, player.posY, player.posZ, PRESS_VOLUME, 1.0F));
    }

    @Nullable
    private static IGrid gridOf(final IPatternUploadHost host) {
        final IGridNode node = host.getActionableNode();
        return node == null || !node.isActive() ? null : node.getGrid();
    }
}
