/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.core;


import appeng.api.config.CpuActivityFilter;
import appeng.api.config.CpuModeFilter;
import appeng.api.config.CpuSortOrder;
import appeng.api.config.PowerUnits;
import appeng.api.config.SearchBoxMode;
import appeng.api.config.Settings;
import appeng.api.config.SortDir;
import appeng.api.config.TerminalStyle;
import appeng.api.config.YesNo;
import appeng.api.util.IConfigManager;
import appeng.integration.modules.jei.JeiCategory;
import appeng.api.util.IConfigurableObject;
import appeng.util.ConfigManager;
import appeng.util.IConfigManagerHost;
import appeng.util.Platform;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.io.File;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;


/**
 * What a player sets for themselves: what the terminals look like, what the amount buttons step by, which
 * unit power is read in, and which of the mod's flourishes are drawn at all.
 * <p>
 * It is a file of its own so that replacing the pack's settings does not replace the player's. Everything
 * here is written by the game as buttons are pressed, and none of it decides how anything works - so a pack
 * that ships {@code AppliedEnergistics2.cfg}, and a launcher that keeps that file up to date, can leave
 * this one alone.
 */
public final class AEClientConfig extends Configuration implements IConfigurableObject, IConfigManagerHost {

    private static final String CATEGORY = "Client";
    /** Which recipe categories the mod offers JEI. Display only - nothing here changes a recipe. */
    private static final String CATEGORY_JEI = "JEI";

    private static final String CATEGORY_VISUALISER = "NetworkVisualiser";

    private static AEClientConfig instance;

    private final IConfigManager settings = new ConfigManager(this);

    private boolean enableEffects = true;
    private boolean useColoredCraftingStatus = true;
    private boolean disableColoredCableRecipesInJEI = true;
    private boolean showCraftableTooltip = true;
    private boolean showPlacementPreview = true;
    private boolean turnToHighlightedBlock = true;
    private boolean showCraftingPins = true;
    private boolean showPlayerPins = true;
    private int visualiserRenderDistance = 128;
    private int visualiserLabelDistance = 24;
    private double visualiserLineWidth = 0.05;
    private double visualiserNodeSize = 0.18;
    private int visualiserNodeColor = 0xFF8CD6FF;
    private int visualiserNodeMissingColor = 0xFFFF4040;
    private int visualiserLinkIdleColor = 0xFF3CD63C;
    private int visualiserLinkFullColor = 0xFFFF4040;
    private int visualiserLinkOtherColor = 0xFFCCCCCC;
    private final Map<JeiCategory, Boolean> shownCategories = new EnumMap<>(JeiCategory.class);
    private PowerUnits selectedPowerUnit = PowerUnits.AE;

    /** False until the file has been read once, so reading it does not write it back a line at a time. */
    private boolean updatable = false;

    private AEClientConfig(final File configFile) {
        super(configFile);

        MinecraftForge.EVENT_BUS.register(this);

        this.settings.registerSetting(Settings.SEARCH_TOOLTIPS, YesNo.YES);
        this.settings.registerSetting(Settings.TERMINAL_STYLE, TerminalStyle.SMALL);
        this.settings.registerSetting(Settings.HIDE_STORED, YesNo.NO);
        this.settings.registerSetting(Settings.SEARCH_MODE, SearchBoxMode.AUTOSEARCH);
        this.settings.registerSetting(Settings.SEARCH_KEEP, YesNo.NO);
        this.settings.registerSetting(Settings.AMOUNT_ENTRY_UNITS, YesNo.NO);
        this.settings.registerSetting(Settings.CPU_FILTER_ACTIVITY, CpuActivityFilter.ALL);
        this.settings.registerSetting(Settings.CPU_FILTER_MODE, CpuModeFilter.ALL);
        this.settings.registerSetting(Settings.CPU_SORT_BY, CpuSortOrder.NAME);
        this.settings.registerSetting(Settings.CPU_SORT_DIRECTION, SortDir.ASCENDING);

        this.readValues();

        this.updatable = true;
    }

    public static void init(final File configFile) {
        instance = new AEClientConfig(configFile);
    }

    public static AEClientConfig instance() {
        return instance;
    }

    /** Whether a search box keeps what was typed in it when its screen closes. Every screen with one. */
    public boolean keepsSearch() {
        return this.settings.getSetting(Settings.SEARCH_KEEP) == YesNo.YES;
    }

    /**
     * Whether a screen's first search box is focused the moment it opens, so that typing goes into it
     * without a click. The {@code AUTOSEARCH} half of {@link Settings#SEARCH_MODE} says so.
     */
    public boolean focusesSearchOnOpen() {
        final Enum<?> mode = this.settings.getSetting(Settings.SEARCH_MODE);
        return mode == SearchBoxMode.AUTOSEARCH || mode == SearchBoxMode.JEI_AUTOSEARCH;
    }

    /**
     * Whether the search box keeps its text used to be half of {@link Settings#SEARCH_MODE}, doubling that
     * setting's values into a {@code _KEEP} twin of each. A file written before the split says
     * {@code AUTOSEARCH_KEEP}, which no longer names a mode - left alone it would fall back to the default
     * and quietly turn the option off, so it is rewritten as the mode it meant plus the new setting.
     */
    private void migrateSearchKeep() {
        final Property mode = this.get(CATEGORY, Settings.SEARCH_MODE.name(), SearchBoxMode.AUTOSEARCH.name(),
                this.getListComment(SearchBoxMode.AUTOSEARCH));
        final String stored = mode.getString();

        if (!stored.endsWith("_KEEP")) {
            return;
        }

        mode.set(stored.substring(0, stored.length() - "_KEEP".length()));
        this.get(CATEGORY, Settings.SEARCH_KEEP.name(), YesNo.NO.name(), this.getListComment(YesNo.NO))
                .set(YesNo.YES.name());
    }

    private void readValues() {
        this.migrateSearchKeep();

        this.enableEffects = this.get(CATEGORY, "enableEffects", true).getBoolean(true);
        this.useColoredCraftingStatus = this.get(CATEGORY, "useColoredCraftingStatus", true).getBoolean(true);
        this.disableColoredCableRecipesInJEI = this.get(CATEGORY, "disableColoredCableRecipesInJEI", true).getBoolean(true);
        this.showCraftableTooltip = this.get(CATEGORY, "showCraftableTooltip", true, "Whether a terminal tooltip says that what it names can be crafted, and which click orders it.").getBoolean(true);
        this.showPlacementPreview = this.get(CATEGORY, "showPlacementPreview", true, "Whether to show a preview of part and facade placement.").getBoolean(true);
        this.turnToHighlightedBlock = this.get(CATEGORY, "turnToHighlightedBlock", true,
                "Whether highlighting a block also turns the player to face it.").getBoolean(true);
        this.showCraftingPins = this.get(CATEGORY, "showCraftingPins", true,
                "Whether terminals show active crafting jobs pinned above their contents.").getBoolean(true);
        this.showPlayerPins = this.get(CATEGORY, "showPlayerPins", true,
                "Whether terminals show persistent player pins.").getBoolean(true);

        this.visualiserRenderDistance = this.get(CATEGORY_VISUALISER, "renderDistance", 128,
                "How far from the player the network visualiser draws, in blocks.", 16, 512).getInt(128);
        this.visualiserLabelDistance = this.get(CATEGORY_VISUALISER, "labelDistance", 24,
                "How far from the player the network visualiser writes channel counts, in blocks.", 0, 128).getInt(24);
        this.visualiserLineWidth = this.get(CATEGORY_VISUALISER, "lineWidth", 0.05,
                "Half the thickness, in blocks, of a link carrying eight channels. Thicker tiers scale up from here.",
                0.005, 0.5).getDouble(0.05);
        this.visualiserNodeSize = this.get(CATEGORY_VISUALISER, "nodeSize", 0.18,
                "Half the size, in blocks, of the cube drawn at a network node.", 0.02, 0.5).getDouble(0.18);

        this.visualiserNodeColor = this.getColor(CATEGORY_VISUALISER, "nodeColor", 0xFF8CD6FF,
                "Colour of a node, as AARRGGBB.");
        this.visualiserNodeMissingColor = this.getColor(CATEGORY_VISUALISER, "nodeMissingChannelColor", 0xFFFF4040,
                "Colour of a node that is asking for a channel it did not get.");
        this.visualiserLinkIdleColor = this.getColor(CATEGORY_VISUALISER, "linkIdleColor", 0xFF3CD63C,
                "Colour of a link carrying nothing. A link is coloured between this and linkFullColor by how full it is; the pair defaults to green and red, so change both if that pair is hard for you to tell apart.");
        this.visualiserLinkFullColor = this.getColor(CATEGORY_VISUALISER, "linkFullColor", 0xFFFF4040,
                "Colour of a link with no channels left.");
        this.visualiserLinkOtherColor = this.getColor(CATEGORY_VISUALISER, "linkOtherColor", 0xFFCCCCCC,
                "Colour of a link whose far end is not in this world, and of anything an addon draws without saying what colour it wants.");

        for (final JeiCategory category : JeiCategory.values()) {
            this.shownCategories.put(category,
                    this.get(CATEGORY_JEI, category.key(), true, category.comment()).getBoolean(true));
        }

        try {
            this.selectedPowerUnit = PowerUnits.valueOf(this.get(CATEGORY, "PowerUnit", this.selectedPowerUnit.name(),
                    this.getListComment(this.selectedPowerUnit)).getString());
        } catch (final Throwable t) {
            this.selectedPowerUnit = PowerUnits.AE;
        }

        AmountSteps.load(this);

        for (final Settings e : this.settings.getSettings()) {
            Enum<?> value = this.settings.getSetting(e);

            final Property p = this.get(CATEGORY, e.name(), value.name(), this.getListComment(value));

            try {
                value = Enum.valueOf(value.getClass(), p.getString());
            } catch (final IllegalArgumentException er) {
                AELog.info("Invalid value '" + p.getString() + "' for " + e.name() + " using '" + value.name() + "' instead");
            }

            this.settings.putSetting(e, value);
        }
    }

    /** Whether this recipe category is offered to JEI at all. A category switched off is never registered. */
    /** Colours are written as AARRGGBB so that a person editing the file can read them. */
    private int getColor(final String category, final String name, final int fallback, final String comment) {
        final String written = this.get(category, name, String.format("%08X", fallback), comment).getString();

        try {
            return (int) Long.parseLong(written.trim(), 16);
        } catch (final NumberFormatException e) {
            AELog.warn("%s/%s is not a colour: %s", category, name, written);
            return fallback;
        }
    }

    public int getVisualiserRenderDistance() {
        return this.visualiserRenderDistance;
    }

    public int getVisualiserLabelDistance() {
        return this.visualiserLabelDistance;
    }

    public double getVisualiserLineWidth() {
        return this.visualiserLineWidth;
    }

    public double getVisualiserNodeSize() {
        return this.visualiserNodeSize;
    }

    public int getVisualiserNodeColor() {
        return this.visualiserNodeColor;
    }

    public int getVisualiserNodeMissingColor() {
        return this.visualiserNodeMissingColor;
    }

    public int getVisualiserLinkIdleColor() {
        return this.visualiserLinkIdleColor;
    }

    public int getVisualiserLinkFullColor() {
        return this.visualiserLinkFullColor;
    }

    public int getVisualiserLinkOtherColor() {
        return this.visualiserLinkOtherColor;
    }

    public boolean shows(final JeiCategory category) {
        return this.shownCategories.getOrDefault(category, true);
    }

    private String getListComment(final Enum value) {
        String comment = null;

        if (value != null) {
            final EnumSet set = EnumSet.allOf(value.getClass());

            for (final Object Oeg : set) {
                final Enum eg = (Enum) Oeg;
                if (comment == null) {
                    comment = "Possible Values: " + eg.name();
                } else {
                    comment += ", " + eg.name();
                }
            }
        }

        return comment;
    }

    @Override
    public void save() {
        this.get(CATEGORY, "PowerUnit", this.selectedPowerUnit.name(), this.getListComment(this.selectedPowerUnit))
                .set(this.selectedPowerUnit.name());

        if (this.hasChanged()) {
            super.save();
        }
    }

    @SubscribeEvent
    public void onConfigChanged(final ConfigChangedEvent.OnConfigChangedEvent eventArgs) {
        if (eventArgs.getModID().equals(AppEng.MOD_ID)) {
            this.readValues();
        }
    }

    @Override
    public IConfigManager getConfigManager() {
        return this.settings;
    }

    @Override
    public void updateSetting(final IConfigManager manager, final Enum setting, final Enum newValue) {
        for (final Settings e : this.settings.getSettings()) {
            if (e == setting) {
                final Property p = this.get(CATEGORY, e.name(), this.settings.getSetting(e).name(), this.getListComment(newValue));
                p.set(newValue.name());
            }
        }

        if (this.updatable) {
            this.save();
        }
    }

    public PowerUnits selectedPowerUnit() {
        return this.selectedPowerUnit;
    }

    public void nextPowerUnit(final boolean backwards) {
        this.selectedPowerUnit = Platform.rotateEnum(this.selectedPowerUnit, backwards, Settings.POWER_UNITS.getPossibleValues());
        this.save();
    }

    public boolean isEnableEffects() {
        return this.enableEffects;
    }

    public boolean isUseColoredCraftingStatus() {
        return this.useColoredCraftingStatus;
    }

    public boolean isDisableColoredCableRecipesInJEI() {
        return this.disableColoredCableRecipesInJEI;
    }

    public boolean isShowCraftableTooltip() {
        return this.showCraftableTooltip;
    }

    public boolean showPlacementPreview() {
        return this.showPlacementPreview;
    }

    public boolean turnToHighlightedBlock() {
        return this.turnToHighlightedBlock;
    }

    public boolean showCraftingPins() {
        return this.showCraftingPins;
    }

    public boolean showPlayerPins() {
        return this.showPlayerPins;
    }
}
