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

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
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

    /**
     * The one upload each player can take back. Weak in the key so a record dies with the player who left,
     * which is also how long the offer is meant to stand - between sessions the network has moved on, and
     * "put it back" would be reaching into a machine nobody remembers choosing.
     */
    private static final Map<EntityPlayer, Undo> UNDO = new WeakHashMap<>();

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
     * Whether the terminal's button should be offering to take the last upload back. Only that a record
     * exists: whether it can still be honoured is asked when the button is pressed, not every tick.
     */
    public static boolean canReturn(final EntityPlayer player) {
        return remembered(player) != null;
    }

    /**
     * The terminal's button: files the pattern when there is one sensible answer, otherwise opens the screen
     * that asks. With nothing in the slot the same button means the opposite, and takes the last one back.
     *
     * @param pick true when the player asked to choose rather than have it decided.
     */
    public static void run(final EntityPlayerMP player, final AEBaseContainer from, final IPatternUploadHost host,
            final boolean pick) {
        final ItemStack pattern = host.getEncodedPattern();

        // Which of the two things the button does is decided here rather than by the screen: the client's
        // idea of the slot is a tick old, and only one of the two is ever possible at a time anyway.
        if (pattern.isEmpty()) {
            returnLast(player, host);
            return;
        }

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

    /** Puts the last upload back in the terminal it was sent from. */
    private static void returnLast(final EntityPlayerMP player, final IPatternUploadHost host) {
        final Undo undo = remembered(player);
        if (undo == null) {
            player.sendMessage(PlayerMessages.PatternUploadNothingToUndo.get());
            return;
        }

        final IPatternContainer target = undo.target.get();

        // On this terminal's own network, not merely still alive somewhere: without this, uploading on one
        // network and pressing the button on another would carry a pattern between the two.
        if (target == null || !PatternContainers.visible(gridOf(host)).contains(target)) {
            player.sendMessage(PlayerMessages.PatternUploadTargetGone.get());
            return;
        }

        if (!PatternContainers.extract(target, undo.pattern)) {
            player.sendMessage(PlayerMessages.PatternUploadAlreadyTaken.get());
            return;
        }

        UNDO.remove(player);
        host.setEncodedPattern(undo.pattern);
        click(player);
    }

    /** The record, with a dead one cleared away as it is read. */
    @Nullable
    private static Undo remembered(final EntityPlayer player) {
        final Undo undo = UNDO.get(player);

        if (undo != null && undo.target.get() == null) {
            UNDO.remove(player);
            return null;
        }

        return undo;
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
        UNDO.put(player, new Undo(target, one));
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

    /**
     * Where one pattern went. The container is held weakly: a strong one here would keep an interface the
     * player has since broken alive for as long as they stay logged in.
     */
    private static final class Undo {

        private final WeakReference<IPatternContainer> target;
        private final ItemStack pattern;

        private Undo(final IPatternContainer target, final ItemStack pattern) {
            this.target = new WeakReference<>(target);
            this.pattern = pattern;
        }
    }

    @Nullable
    private static IGrid gridOf(final IPatternUploadHost host) {
        final IGridNode node = host.getActionableNode();
        return node == null || !node.isActive() ? null : node.getGrid();
    }
}
