/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.integration.modules.jei;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import mezz.jei.bookmarks.BookmarkItem;
import org.lwjgl.input.Keyboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.client.ActionKey;
import appeng.client.gui.AEGuiHandler;
import appeng.container.implementations.ContainerMEMonitorable;
import appeng.core.AppEng;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketNetworkIngredient;
import appeng.helpers.NetworkIngredientAction;
import appeng.helpers.WirelessTerminalAccess;

/**
 * Taking an ingredient out of the network, or ordering one made, from JEI's own list.
 *
 * <p>Which ingredient is public API - {@code getIngredientUnderMouse()}, on the ingredient list and on the
 * bookmark overlay alike. Getting the click is not: see {@code appeng.mixin.hei.MixinInputHandler} for why a
 * listener cannot be ahead of JEI, and {@code MixinIngredientRenderer} for why the hint below cannot go
 * through {@code ItemTooltipEvent}. JEI's cheat mode is not involved and cannot be - a server turns it off
 * for anyone who is not in creative.</p>
 */
@SideOnly(Side.CLIENT)
public final class JeiIngredientActions {

    private static boolean registered;

    private JeiIngredientActions() {
    }

    public static void register() {
        if (!registered) {
            registered = true;
            MinecraftForge.EVENT_BUS.register(new JeiIngredientActions());
        }
    }

    /**
     * The keyboard half, for a binding moved off the mouse. HEI reads the keyboard on
     * {@code KeyboardInputEvent.Post}, so this is ahead of it without any help. The mouse half cannot be
     * done this way at all - see {@code appeng.mixin.hei.MixinInputHandler}.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onKeyboardInput(final GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (Keyboard.getEventKeyState() && act(Keyboard.getEventKey())) {
            event.setCanceled(true);
        }
    }

    /** @return true when the key was one of ours and nothing else should see it. */
    public static boolean act(final int code) {
        final NetworkIngredientAction action = actionFor(code);
        if (action == null) {
            return false;
        }

        final AEKey what = hoveredKey();
        if (what == null) {
            return false;
        }

        // Ordering a fluid is an ordinary request; taking one is not, since there is nowhere to put it.
        if (action == NetworkIngredientAction.RETRIEVE && !(what instanceof AEItemKey)) {
            return false;
        }

        NetworkHandler.instance().sendToServer(
                new PacketNetworkIngredient(action, what, GuiScreen.isShiftKeyDown()));
        return true;
    }

    @Nullable
    private static NetworkIngredientAction actionFor(final int code) {
        if (AppEng.proxy.isActionKey(ActionKey.JEI_RETRIEVE, code)) {
            return NetworkIngredientAction.RETRIEVE;
        }

        if (AppEng.proxy.isActionKey(ActionKey.JEI_CRAFT, code)) {
            return NetworkIngredientAction.CRAFT;
        }

        return null;
    }

    /** What the cursor is over in JEI's own list, or failing that in its bookmarks. */
    @Nullable
    private static AEKey hoveredKey() {
        if (JEIPlugin.runtime == null) {
            return null;
        }

        Object ingredient = JEIPlugin.runtime.getIngredientListOverlay().getIngredientUnderMouse();
        if (ingredient == null) {
            ingredient = JEIPlugin.runtime.getBookmarkOverlay().getIngredientUnderMouse();
        }

        // A bookmark hands back its own wrapper rather than what was bookmarked: it carries the amount and
        // the group it belongs to. A collapsed group is not one ingredient at all, and stays unanswered.
        if (ingredient instanceof BookmarkItem) {
            ingredient = ((BookmarkItem<?>) ingredient).getIngredient();
        }

        return ingredient == null ? null : AEGuiHandler.keyOf(ingredient);
    }

    /**
     * Says what the two keys do, under the ingredient they would act on - a binding nothing mentions is one
     * nobody finds. Only where it means something: a player with no way to reach a network is not told
     * about a shortcut into one, and a fluid is not offered a way to take it.
     *
     * @return the tooltip to draw, which is the one handed in when there is nothing to add.
     */
    public static List<String> withHints(final List<String> tooltip) {
        final AEKey hovered = hoveredKey();
        if (hovered == null || !canReachNetwork()) {
            return tooltip;
        }

        final List<String> hinted = new ArrayList<>(tooltip);

        if (hovered instanceof AEItemKey) {
            addHint(hinted, GuiText.JeiRetrieveHint, ActionKey.JEI_RETRIEVE);
        }

        addHint(hinted, GuiText.JeiCraftHint, ActionKey.JEI_CRAFT);
        return hinted;
    }

    private static void addHint(final List<String> tooltip, final GuiText line, final ActionKey key) {
        final String name = AppEng.proxy.getActionKeyName(key);

        if (name != null) {
            tooltip.add(line.getLocal(name));
        }
    }

    private static boolean canReachNetwork() {
        final EntityPlayer player = Minecraft.getMinecraft().player;

        return player != null && (player.openContainer instanceof ContainerMEMonitorable
                || WirelessTerminalAccess.carriesTerminal(player));
    }

}
