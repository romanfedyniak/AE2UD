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
import java.util.EnumSet;


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

    private void readValues() {
        this.enableEffects = this.get(CATEGORY, "enableEffects", true).getBoolean(true);
        this.useColoredCraftingStatus = this.get(CATEGORY, "useColoredCraftingStatus", true).getBoolean(true);
        this.disableColoredCableRecipesInJEI = this.get(CATEGORY, "disableColoredCableRecipesInJEI", true).getBoolean(true);
        this.showCraftableTooltip = this.get(CATEGORY, "showCraftableTooltip", true, "Whether to add \"Craftable\" to item tooltips when they can be crafted automatically.").getBoolean(true);
        this.showPlacementPreview = this.get(CATEGORY, "showPlacementPreview", true, "Whether to show a preview of part and facade placement.").getBoolean(true);
        this.turnToHighlightedBlock = this.get(CATEGORY, "turnToHighlightedBlock", true,
                "Whether highlighting a block also turns the player to face it.").getBoolean(true);
        this.showCraftingPins = this.get(CATEGORY, "showCraftingPins", true,
                "Whether terminals show active crafting jobs pinned above their contents.").getBoolean(true);
        this.showPlayerPins = this.get(CATEGORY, "showPlayerPins", true,
                "Whether terminals show persistent player pins.").getBoolean(true);

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
