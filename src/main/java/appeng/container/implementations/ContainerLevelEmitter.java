/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
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

package appeng.container.implementations;


import appeng.api.config.*;
import appeng.container.guisync.GuiSync;
import appeng.container.slot.SlotFakeTypeOnly;
import appeng.core.sync.GuiBridge;
import appeng.helpers.IAmountTarget;
import appeng.helpers.LevelAmountTarget;
import appeng.container.slot.SlotRestrictedInput;
import appeng.parts.automation.PartLevelEmitter;
import appeng.util.Platform;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraftforge.items.IItemHandler;


/**
 * The emitter's threshold is a plain {@code @GuiSync} value here. The container used to hold the screen's
 * text box and write digits into it directly, which cannot be right once the field can read in a larger
 * unit: only the client knows which unit that is. {@link appeng.client.gui.implementations.GuiLevelEmitter}
 * watches the value instead.
 */
public class ContainerLevelEmitter extends ContainerUpgradeable {

    /**
     * Shorter than the window every other upgradeable machine uses: what stood between the filter slot and
     * the player inventory was the threshold field and its buttons, and the threshold is typed elsewhere.
     */
    public static final int HEIGHT = 140;

    /** Where the filter sits, which is also where the screen draws its well: centred in the window. */
    public static final int FILTER_X = 80;
    public static final int FILTER_Y = 20;

    private final PartLevelEmitter lvlEmitter;

    @GuiSync(2)
    public LevelType lvType;
    @GuiSync(3)
    public long EmitterValue = -1;
    @GuiSync(4)
    public YesNo cmType;

    /**
     * The filter slot stands for what the emitter watches; the amount typed on it is the threshold, which
     * lives on the emitter rather than in the slot.
     */
    @Override
    public IAmountTarget amountTargetFor(final GuiBridge origin, final int slot) {
        return new LevelAmountTarget(this.lvlEmitter);
    }

    @Override
    protected int getHeight() {
        return HEIGHT;
    }

    public ContainerLevelEmitter(final InventoryPlayer ip, final PartLevelEmitter te) {
        super(ip, te);
        this.lvlEmitter = te;
    }

    @Override
    protected void setupConfig() {
        final IItemHandler upgrades = this.getUpgradeable().getInventoryByName("upgrades");
        if (this.availableUpgrades() > 0) {
            this.addSlotToContainer(
                    (new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.UPGRADES, upgrades, 0, 187, 8, this.getInventoryPlayer()))
                            .setNotDraggable());
        }
        if (this.availableUpgrades() > 1) {
            this.addSlotToContainer(
                    (new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.UPGRADES, upgrades, 1, 187, 8 + 18, this.getInventoryPlayer()))
                            .setNotDraggable());
        }
        if (this.availableUpgrades() > 2) {
            this.addSlotToContainer(
                    (new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.UPGRADES, upgrades, 2, 187, 8 + 18 * 2, this.getInventoryPlayer()))
                            .setNotDraggable());
        }
        if (this.availableUpgrades() > 3) {
            this.addSlotToContainer(
                    (new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.UPGRADES, upgrades, 3, 187, 8 + 18 * 3, this.getInventoryPlayer()))
                            .setNotDraggable());
        }

        final IItemHandler inv = this.getUpgradeable().getInventoryByName("config");
        this.addSlotToContainer(new SlotFakeTypeOnly(inv, 0, FILTER_X, FILTER_Y));
    }

    @Override
    public int availableUpgrades() {

        return 1;
    }

    @Override
    public void detectAndSendChanges() {
        this.verifyPermissions(SecurityPermissions.BUILD, false);

        if (Platform.isServer()) {
            this.EmitterValue = this.lvlEmitter.getReportingValue();
            this.setCraftingMode((YesNo) this.getUpgradeable().getConfigManager().getSetting(Settings.CRAFT_VIA_REDSTONE));
            this.setLevelMode((LevelType) this.getUpgradeable().getConfigManager().getSetting(Settings.LEVEL_TYPE));
            this.setFuzzyMode((FuzzyMode) this.getUpgradeable().getConfigManager().getSetting(Settings.FUZZY_MODE));
            this.setRedStoneMode((RedstoneMode) this.getUpgradeable().getConfigManager().getSetting(Settings.REDSTONE_EMITTER));
        }

        this.standardDetectAndSendChanges();
    }

    public YesNo getCraftingMode() {
        return this.cmType;
    }

    public void setCraftingMode(final YesNo cmType) {
        this.cmType = cmType;
    }

    public LevelType getLevelMode() {
        return this.lvType;
    }

    private void setLevelMode(final LevelType lvType) {
        this.lvType = lvType;
    }
}
