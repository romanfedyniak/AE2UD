/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.api.features;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

/**
 * A switch an addon puts on every wireless terminal, kept in the terminal item - so a terminal on the belt can be
 * on while a spare in the chest is off - and shown as a button in the settings drawer of every wireless screen.
 * It means whatever the addon that registered it reads it as; the mod only stores and shows it.
 * <p>
 * Built with {@link #builder} and registered with {@link WirelessTerminalToggles#register}.
 */
public final class WirelessTerminalToggle {

    /** The compound in a terminal's NBT that holds every toggle, by id. */
    public static final String NBT_KEY = "toggles";

    private final ResourceLocation id;
    private final boolean defaultValue;
    private final ResourceLocation texture;
    private final int onU;
    private final int onV;
    private final int offU;
    private final int offV;
    private final String titleKey;
    private final String onKey;
    private final String offKey;

    private WirelessTerminalToggle(final Builder builder) {
        this.id = builder.id;
        this.defaultValue = builder.defaultValue;
        this.texture = Objects.requireNonNull(builder.texture, "icons");
        this.onU = builder.onU;
        this.onV = builder.onV;
        this.offU = builder.offU;
        this.offV = builder.offV;
        this.titleKey = Objects.requireNonNull(builder.titleKey, "tooltip");
        this.onKey = builder.onKey;
        this.offKey = builder.offKey;
    }

    public static Builder builder(@Nonnull final ResourceLocation id) {
        return new Builder(id);
    }

    public ResourceLocation getId() {
        return this.id;
    }

    public boolean getDefaultValue() {
        return this.defaultValue;
    }

    /**
     * @return whether it is on in that terminal; the default until someone has pressed the button.
     */
    public boolean isOn(final ItemStack terminal) {
        final NBTTagCompound tag = terminal.getTagCompound();
        if (tag == null || !tag.hasKey(NBT_KEY)) {
            return this.defaultValue;
        }
        final NBTTagCompound toggles = tag.getCompoundTag(NBT_KEY);
        final String key = this.id.toString();
        return toggles.hasKey(key) ? toggles.getBoolean(key) : this.defaultValue;
    }

    public void set(final ItemStack terminal, final boolean on) {
        if (!terminal.hasTagCompound()) {
            terminal.setTagCompound(new NBTTagCompound());
        }
        final NBTTagCompound tag = terminal.getTagCompound();
        final NBTTagCompound toggles = tag.getCompoundTag(NBT_KEY);
        toggles.setBoolean(this.id.toString(), on);
        tag.setTag(NBT_KEY, toggles);
    }

    /** A 256×256 texture holding both icons, 16×16 each. */
    public ResourceLocation getTexture() {
        return this.texture;
    }

    public int getU(final boolean on) {
        return on ? this.onU : this.offU;
    }

    public int getV(final boolean on) {
        return on ? this.onV : this.offV;
    }

    /** Translation keys: the button's name, and the line below it saying what the current state does. */
    public String getTitleKey() {
        return this.titleKey;
    }

    public String getStateKey(final boolean on) {
        return on ? this.onKey : this.offKey;
    }

    public static final class Builder {

        private final ResourceLocation id;
        private boolean defaultValue;
        private ResourceLocation texture;
        private int onU;
        private int onV;
        private int offU;
        private int offV;
        private String titleKey;
        private String onKey;
        private String offKey;

        private Builder(final ResourceLocation id) {
            this.id = Objects.requireNonNull(id);
        }

        public Builder defaultValue(final boolean on) {
            this.defaultValue = on;
            return this;
        }

        /**
         * @param texture a 256×256 texture, such as {@code modid:textures/guis/states.png}.
         */
        public Builder icons(final ResourceLocation texture, final int onU, final int onV, final int offU,
                final int offV) {
            this.texture = texture;
            this.onU = onU;
            this.onV = onV;
            this.offU = offU;
            this.offV = offV;
            return this;
        }

        public Builder tooltip(final String titleKey, final String onKey, final String offKey) {
            this.titleKey = titleKey;
            this.onKey = onKey;
            this.offKey = offKey;
            return this;
        }

        public WirelessTerminalToggle build() {
            return new WirelessTerminalToggle(this);
        }
    }
}
