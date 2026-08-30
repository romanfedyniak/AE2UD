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

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants;

import appeng.api.AEApi;
import appeng.api.features.IWirelessTerminalMode;
import appeng.api.features.IWirelessTerminalModeRegistry;
import appeng.util.Platform;

/**
 * Where a wireless terminal keeps the modes it owns, which one it is showing, and what each mode has stored
 * on it.
 *
 * <p>Modes are named by string, never by index, so a terminal keeps a mode whose addon has been taken out and
 * gets it back untouched when the addon returns.</p>
 */
public final class WirelessTerminalModes {

    private static final String CURRENT = "mode";
    private static final String UNLOCKED = "modes";
    private static final String PER_MODE = "modeData";

    private WirelessTerminalModes() {
    }

    @Nullable
    public static ResourceLocation getModeId(final ItemStack terminal) {
        final NBTTagCompound tag = terminal.getTagCompound();
        if (tag == null || !tag.hasKey(CURRENT, Constants.NBT.TAG_STRING)) {
            return null;
        }

        final String id = tag.getString(CURRENT);
        return id.isEmpty() ? null : new ResourceLocation(id);
    }

    public static void setModeId(final ItemStack terminal, final ResourceLocation id) {
        Platform.openNbtData(terminal).setString(CURRENT, id.toString());
    }

    /**
     * The modes this terminal owns. A terminal that says nothing owns the default mode alone - that is the
     * plain wireless terminal from before there were modes, and it must not come back with more than it had.
     */
    public static List<ResourceLocation> getUnlocked(final ItemStack terminal) {
        final List<ResourceLocation> unlocked = new ArrayList<>();
        final NBTTagCompound tag = terminal.getTagCompound();

        if (tag != null && tag.hasKey(UNLOCKED, Constants.NBT.TAG_LIST)) {
            final NBTTagList list = tag.getTagList(UNLOCKED, Constants.NBT.TAG_STRING);
            for (int i = 0; i < list.tagCount(); i++) {
                final ResourceLocation id = new ResourceLocation(list.getStringTagAt(i));
                if (!unlocked.contains(id)) {
                    unlocked.add(id);
                }
            }
        }

        if (unlocked.isEmpty()) {
            final IWirelessTerminalMode fallback = registry().getDefaultMode();
            if (fallback != null) {
                unlocked.add(fallback.getId());
            }
        }

        return unlocked;
    }

    public static boolean isUnlocked(final ItemStack terminal, final ResourceLocation id) {
        return getUnlocked(terminal).contains(id);
    }

    /**
     * @return false when the terminal already owned that mode, so a recipe can refuse rather than hand back
     *         the same terminal for an ingredient it would swallow.
     */
    public static boolean unlock(final ItemStack terminal, final ResourceLocation id) {
        final List<ResourceLocation> unlocked = getUnlocked(terminal);
        if (unlocked.contains(id)) {
            return false;
        }

        unlocked.add(id);
        setUnlocked(terminal, unlocked);
        return true;
    }

    public static void setUnlocked(final ItemStack terminal, final List<ResourceLocation> unlocked) {
        final NBTTagList list = new NBTTagList();
        for (final ResourceLocation id : unlocked) {
            list.appendTag(new NBTTagString(id.toString()));
        }

        Platform.openNbtData(terminal).setTag(UNLOCKED, list);
    }

    /**
     * The mode this terminal is showing, or null while nothing it owns is registered.
     *
     * <p>A mode that is written down but unknown - its addon is gone - falls back to the first owned mode
     * that is known, and the written one is left alone so it is still there if the addon comes back.</p>
     */
    @Nullable
    public static IWirelessTerminalMode getActiveMode(final ItemStack terminal) {
        final ResourceLocation current = getModeId(terminal);
        if (current != null && isUnlocked(terminal, current)) {
            final IWirelessTerminalMode mode = registry().getMode(current);
            if (mode != null) {
                return mode;
            }
        }

        for (final ResourceLocation id : getUnlocked(terminal)) {
            final IWirelessTerminalMode mode = registry().getMode(id);
            if (mode != null) {
                return mode;
            }
        }

        return null;
    }

    /**
     * The modes this terminal owns that are registered, in the order the registry holds them, so the buttons
     * do not move about between one terminal and the next.
     */
    public static List<IWirelessTerminalMode> getUnlockedModes(final ItemStack terminal) {
        final List<ResourceLocation> unlocked = getUnlocked(terminal);
        final List<IWirelessTerminalMode> modes = new ArrayList<>();

        for (final IWirelessTerminalMode mode : registry().getModes()) {
            if (unlocked.contains(mode.getId())) {
                modes.add(mode);
            }
        }

        return modes;
    }

    /**
     * Modes written on the terminal that nothing registers, so the tooltip can say they are still there
     * rather than pretending they were never bought.
     */
    public static List<ResourceLocation> getUnknown(final ItemStack terminal) {
        final List<ResourceLocation> unknown = new ArrayList<>();

        for (final ResourceLocation id : getUnlocked(terminal)) {
            if (registry().getMode(id) == null) {
                unknown.add(id);
            }
        }

        return unknown;
    }

    /**
     * One mode's own corner of the terminal's NBT. The crafting terminal and the pattern terminal both keep a
     * three by three grid, one of real items and one of ghosts, and on a single item they would otherwise be
     * the same nine slots.
     */
    public static NBTTagCompound getModeData(final ItemStack terminal, final ResourceLocation id) {
        final NBTTagCompound tag = terminal.getTagCompound();
        if (tag == null) {
            return new NBTTagCompound();
        }

        return tag.getCompoundTag(PER_MODE).getCompoundTag(id.toString());
    }

    public static void setModeData(final ItemStack terminal, final ResourceLocation id,
            final NBTTagCompound data) {
        final NBTTagCompound tag = Platform.openNbtData(terminal);

        final NBTTagCompound perMode = tag.getCompoundTag(PER_MODE);
        perMode.setTag(id.toString(), data);
        tag.setTag(PER_MODE, perMode);
    }

    private static IWirelessTerminalModeRegistry registry() {
        return AEApi.instance().registries().wirelessTerminalModes();
    }
}
