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


import appeng.api.AEApi;
import appeng.api.config.InscriberInputCapacity;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.definitions.IItemDefinition;
import appeng.api.features.IInscriberRecipe;
import appeng.api.util.IConfigManager;
import appeng.container.guisync.GuiSync;
import appeng.container.interfaces.IProgressProvider;
import appeng.container.slot.SlotOutput;
import appeng.container.slot.SlotRestrictedInput;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.GuiText;
import appeng.tile.misc.TileInscriber;
import appeng.util.Platform;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;


/**
 * @author AlgorithmX2
 * @author thatsIch
 * @version rv2
 * @since rv0
 */
public class ContainerInscriber extends ContainerUpgradeable implements IProgressProvider {

    /**
     * The four faces that are neither the top nor the bottom, named as the player sees them standing in
     * front of the machine.
     */
    private static final ButtonToolTips[] FLANKS = {
            ButtonToolTips.SideLeft, ButtonToolTips.SideRight, ButtonToolTips.SideBack, ButtonToolTips.SideFront
    };

    private final TileInscriber ti;

    private final Slot top;
    private final Slot middle;
    private final Slot bottom;

    @GuiSync(2)
    public int maxProcessingTime = -1;

    @GuiSync(3)
    public int processingTime = -1;

    @GuiSync(4)
    public YesNo separateSides = YesNo.NO;

    @GuiSync(5)
    public YesNo autoExport = YesNo.NO;

    @GuiSync(6)
    public InscriberInputCapacity bufferSize = InscriberInputCapacity.SIXTY_FOUR;

    public ContainerInscriber(final InventoryPlayer ip, final TileInscriber te) {
        super(ip, te);
        this.ti = te;

        IItemHandler inv = te.getInternalInventory();

        final SlotRestrictedInput topSlot = new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.INSCRIBER_PLATE, inv, 0, 45, 16, this.getInventoryPlayer());
        topSlot.setEmptyTooltip(() -> this.insertFrom(ButtonToolTips.SideTop));
        topSlot.setStackLimitCap(this::inputCapacity);
        this.addSlotToContainer(this.top = topSlot);

        final SlotRestrictedInput bottomSlot = new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.INSCRIBER_PLATE, inv, 1, 45, 62, this.getInventoryPlayer());
        bottomSlot.setEmptyTooltip(() -> this.insertFrom(ButtonToolTips.SideBottom));
        bottomSlot.setStackLimitCap(this::inputCapacity);
        this.addSlotToContainer(this.bottom = bottomSlot);

        final SlotRestrictedInput middleSlot = new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.INSCRIBER_INPUT, inv, 2, 63, 39, this.getInventoryPlayer());
        middleSlot.setEmptyTooltip(() -> this.insertFrom(FLANKS));
        middleSlot.setStackLimitCap(this::inputCapacity);
        this.addSlotToContainer(this.middle = middleSlot);

        final SlotOutput outputSlot = new SlotOutput(inv, 3, 113, 40, -1);
        outputSlot.setEmptyTooltip(() -> this.extractFrom(FLANKS));
        this.addSlotToContainer(outputSlot);
    }

    private int inputCapacity() {
        return this.bufferSize.capacity;
    }

    private String insertFrom(final ButtonToolTips... whenSeparate) {
        return String.format(ButtonToolTips.CanInsertFrom.getLocal(), this.sideList(whenSeparate));
    }

    private String extractFrom(final ButtonToolTips... whenSeparate) {
        return String.format(ButtonToolTips.CanExtractFrom.getLocal(), this.sideList(whenSeparate));
    }

    /**
     * Read afresh on every hover, because the automation access mode decides whether a slot belongs to
     * particular faces or to all of them.
     */
    private String sideList(final ButtonToolTips[] whenSeparate) {
        if (this.separateSides != YesNo.YES) {
            return ButtonToolTips.SideAny.getLocal();
        }

        final StringBuilder sides = new StringBuilder(whenSeparate[0].getLocal());
        for (int i = 1; i < whenSeparate.length; i++) {
            sides.append(i == whenSeparate.length - 1 ? " " + GuiText.And.getLocal() + " " : ", ");
            sides.append(whenSeparate[i].getLocal());
        }

        return sides.toString();
    }

    @Override
    protected int getHeight() {
        return 176;
    }

    @Override
    /**
     * Overridden super.setupConfig to prevent setting up the fake slots
     */
    protected void setupConfig() {
        this.setupUpgrades();
    }

    @Override
    public int availableUpgrades() {
        return 3;
    }

    @Override
    public void detectAndSendChanges() {
        if (Platform.isServer()) {
            this.loadSettingsFromHost(this.ti.getConfigManager());
            this.maxProcessingTime = this.ti.getMaxProcessingTime();
            this.processingTime = this.ti.getProcessingTime();
        }

        this.standardDetectAndSendChanges();
    }

    @Override
    protected void loadSettingsFromHost(final IConfigManager cm) {
        // Not the base implementation: the inscriber takes neither a redstone nor a fuzzy card, so those
        // two settings were never registered and asking for them would throw.
        this.separateSides = (YesNo) cm.getSetting(Settings.INSCRIBER_SEPARATE_SIDES);
        this.autoExport = (YesNo) cm.getSetting(Settings.AUTO_EXPORT);
        this.bufferSize = (InscriberInputCapacity) cm.getSetting(Settings.INSCRIBER_INPUT_CAPACITY);
    }

    public YesNo getSeparateSides() {
        return this.separateSides;
    }

    public YesNo getAutoExport() {
        return this.autoExport;
    }

    public InscriberInputCapacity getBufferSize() {
        return this.bufferSize;
    }

    @Override
    public boolean isValidForSlot(final Slot s, final ItemStack is) {
        final ItemStack top = this.ti.getInternalInventory().getStackInSlot(0);
        final ItemStack bot = this.ti.getInternalInventory().getStackInSlot(1);

        if (s == this.middle) {
            IItemDefinition press = AEApi.instance().definitions().materials().namePress();
            if (press.isSameAs(top) || press.isSameAs(bot)) {
                return !press.isSameAs(is);
            }

            boolean matches = false;
            for (final IInscriberRecipe recipe : AEApi.instance().registries().inscriber().getRecipes()) {
                // Check if plateA matches any item in the list of top components of the recipe
                final boolean matchA = top.isEmpty() && recipe.getTopInputs().isEmpty() ||
                        recipe.getTopInputs().stream().anyMatch(topItem -> Platform.itemComparisons().isSameItem(top, topItem)) &&
                                (bot.isEmpty() && recipe.getBottomInputs().isEmpty() ||
                                        recipe.getBottomInputs().stream().anyMatch(bottomItem -> Platform.itemComparisons().isSameItem(bot, bottomItem)));

                // Check if plateB matches any item in the list of top components of the recipe
                final boolean matchB = bot.isEmpty() && recipe.getTopInputs().isEmpty() ||
                        recipe.getTopInputs().stream().anyMatch(topItem -> Platform.itemComparisons().isSameItem(bot, topItem)) &&
                                (top.isEmpty() && recipe.getBottomInputs().isEmpty() ||
                                        recipe.getBottomInputs().stream().anyMatch(bottomItem -> Platform.itemComparisons().isSameItem(top, bottomItem)));

                // If either matchA or matchB is true, iterate through the recipe's inputs
                if (matchA || matchB) {
                    matches = true;
                    for (final ItemStack option : recipe.getInputs()) {
                        if (Platform.itemComparisons().isSameItem(is, option)) {
                            return true;
                        }
                    }
                }
            }
            return !matches;
        } else if ((s == this.top && !bot.isEmpty()) || (s == this.bottom && !top.isEmpty())) {
            ItemStack otherSlot;
            if (s == this.top) {
                otherSlot = this.bottom.getStack();
            } else {
                otherSlot = this.top.getStack();
            }

            // name presses
            final IItemDefinition namePress = AEApi.instance().definitions().materials().namePress();
            if (namePress.isSameAs(otherSlot)) {
                return namePress.isSameAs(is);
            }

            // everything else
            for (final IInscriberRecipe recipe : AEApi.instance().registries().inscriber().getRecipes()) {
                boolean isValid = false;
                // Check if otherSlot matches any item in the top component list
                boolean matchTop = recipe.getTopInputs().stream()
                        .anyMatch(topItem -> Platform.itemComparisons().isSameItem(otherSlot, topItem));

                // Check if otherSlot matches any item in the bottom component list
                boolean matchBottom = recipe.getBottomInputs().stream()
                        .anyMatch(bottomItem -> Platform.itemComparisons().isSameItem(otherSlot, bottomItem));

                if (matchTop) {
                    // If otherSlot matches a top component, check if 'is' matches any item in the bottom component list
                    isValid = recipe.getBottomInputs().stream()
                            .anyMatch(bottomItem -> Platform.itemComparisons().isSameItem(is, bottomItem));
                } else if (matchBottom) {
                    // If otherSlot matches a bottom component, check if 'is' matches any item in the top component list
                    isValid = recipe.getTopInputs().stream()
                            .anyMatch(topItem -> Platform.itemComparisons().isSameItem(is, topItem));
                }

                if (isValid) {
                    return true;
                }
            }
            return false;
        }

        return true;
    }

    @Override
    public int getCurrentProgress() {
        return this.processingTime;
    }

    @Override
    public int getMaxProgress() {
        return this.maxProcessingTime;
    }
}
