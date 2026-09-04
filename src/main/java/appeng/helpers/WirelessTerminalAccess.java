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

import java.util.function.Predicate;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fml.common.Optional;

import appeng.api.AEApi;
import appeng.api.features.ILocatable;
import appeng.api.features.IWirelessTermHandler;
import appeng.core.localization.PlayerMessages;
import appeng.util.Platform;
import baubles.api.BaublesApi;

/**
 * Reaching a network through a wireless terminal the player is carrying, with no screen open.
 * <p>
 * Every terminal is tried, not the first one found: one of them may be the only one still charged, or the
 * only one in range. What a terminal is asked to do is the caller's, so that pick block, an ingredient
 * pulled out of the network and anything later all walk the inventory the same way and complain alike.
 */
public final class WirelessTerminalAccess {

    /**
     * Why nothing happened, worst first, so a player carrying more than one terminal is told about the one
     * that came closest to working. A spare in a backpack that has never been linked is not worth saying;
     * a terminal that is linked, charged and merely out of range is.
     */
    public static final PlayerMessages[] COMPLAINTS = {
            PlayerMessages.TerminalModeNotUnlocked,
            PlayerMessages.DeviceNotLinked,
            PlayerMessages.StationCanNotBeLocated,
            PlayerMessages.DeviceNotPowered,
            PlayerMessages.OutOfRange
    };

    private WirelessTerminalAccess() {
    }

    /** What a caller does with a terminal that is linked, charged and in range. */
    @FunctionalInterface
    public interface Action {
        /** @return true once the work is done, after which no other terminal is looked at. */
        boolean run(WirelessTerminalGuiObject terminal);
    }

    /**
     * Collects the reasons terminals could not be used and hands the player the one worth hearing.
     */
    public static final class Complaints {

        private PlayerMessages worst;
        private boolean reached;

        /** A terminal that was linked, charged and in range, whatever it then answered. */
        public void reached() {
            this.reached = true;
        }

        public void add(final PlayerMessages message) {
            if (this.worst == null || rank(message) > rank(this.worst)) {
                this.worst = message;
            }
        }

        /**
         * Says the one complaint, if there is one. Silence means either that the player carries no terminal
         * at all, or that one of them was perfectly able and simply had nothing to give.
         */
        public void tell(final EntityPlayer player) {
            if (this.worst != null && !this.reached) {
                player.sendMessage(this.worst.get());
            }
        }

        private static int rank(final PlayerMessages message) {
            for (int i = 0; i < COMPLAINTS.length; i++) {
                if (COMPLAINTS[i] == message) {
                    return i;
                }
            }

            return -1;
        }
    }

    /**
     * @param usable which of the player's terminals may answer at all - a per-terminal setting, say.
     * @return true if one of them did the work.
     */
    public static boolean run(final EntityPlayer player, final Predicate<ItemStack> usable, final Action action) {
        final Complaints complaints = new Complaints();

        final NonNullList<ItemStack> mainInventory = player.inventory.mainInventory;
        for (int i = 0; i < mainInventory.size(); i++) {
            if (tryOne(mainInventory.get(i), i, false, player, usable, action, complaints)) {
                return true;
            }
        }

        if (Platform.isModLoaded("baubles") && tryBaubles(player, usable, action, complaints)) {
            return true;
        }

        complaints.tell(player);
        return false;
    }

    @Optional.Method(modid = "baubles")
    private static boolean tryBaubles(final EntityPlayer player, final Predicate<ItemStack> usable,
            final Action action, final Complaints complaints) {
        for (int i = 0; i < BaublesApi.getBaublesHandler(player).getSlots(); i++) {
            final ItemStack stack = BaublesApi.getBaublesHandler(player).getStackInSlot(i);
            if (tryOne(stack, i, true, player, usable, action, complaints)) {
                return true;
            }
        }

        return false;
    }

    private static boolean tryOne(final ItemStack stack, final int slot, final boolean isBauble,
            final EntityPlayer player, final Predicate<ItemStack> usable, final Action action,
            final Complaints complaints) {
        if (stack.isEmpty() || !AEApi.instance().definitions().items().wirelessTerminal().isSameAs(stack)
                || !usable.test(stack)) {
            return false;
        }

        final IWirelessTermHandler handler = AEApi.instance().registries().wireless()
                .getWirelessTerminalHandler(stack);
        if (handler == null) {
            return false;
        }

        final String unparsedKey = handler.getEncryptionKey(stack);
        if (unparsedKey.isEmpty()) {
            complaints.add(PlayerMessages.DeviceNotLinked);
            return false;
        }

        ILocatable securityStation = null;
        try {
            securityStation = AEApi.instance().registries().locatable().getLocatableBy(Long.parseLong(unparsedKey));
        } catch (final NumberFormatException ignored) {
            // A key that is not a number belongs to no station, which is what the complaint below says.
        }

        if (securityStation == null) {
            complaints.add(PlayerMessages.StationCanNotBeLocated);
            return false;
        }

        if (!handler.hasPower(player, 0.5, stack)) {
            complaints.add(PlayerMessages.DeviceNotPowered);
            return false;
        }

        final WirelessTerminalGuiObject terminal = new WirelessTerminalGuiObject(handler, stack, player,
                player.world, slot, isBauble ? 1 : 0, 0);

        if (!terminal.rangeCheck()) {
            complaints.add(PlayerMessages.OutOfRange);
            return false;
        }

        complaints.reached();
        return action.run(terminal);
    }
}
