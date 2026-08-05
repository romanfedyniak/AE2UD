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

package appeng.client.gui;


import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AmountFormat;
import appeng.container.me.GridInventoryEntry;
import appeng.client.me.SlotME;
import appeng.client.me.PinSlotME;
import appeng.container.implementations.TerminalCraftingPin;
import appeng.container.slot.AppEngSlot;
import appeng.container.slot.SlotPlayerHotBar;
import appeng.container.slot.SlotPlayerInv;
import appeng.core.AEConfig;
import appeng.core.localization.ButtonToolTips;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;


public abstract class AEBaseMEGui extends AEBaseGui {

    public AEBaseMEGui(final Container container) {
        super(container);
    }

    @Override
    protected void renderToolTip(final ItemStack stack, final int x, final int y) {
        final Slot s = this.getSlot(x, y);

        final int bigNumber = 9999;
        final List<String> currentToolTip = this.getItemToolTip(stack);

        if (s instanceof SlotME && !stack.isEmpty()) {

            GridInventoryEntry myStack = null;

            try {
                final SlotME theSlotField = (SlotME) s;
                myStack = theSlotField.getEntry();
            } catch (final Throwable ignore) {
            }

            if (myStack != null) {
                // Amounts go through the key's own formatter, not a bare number: a fluid row holds
                // millibuckets and has to read "1B", not "1,000". Items format identically to before.
                //
                // Two things depend on the key's type rather than on this screen. "Items Stored" is wrong
                // for anything that is not an item, so a non-item type says "Amount" instead; and the
                // threshold differs, because a single item is already shown by the slot's own count while
                // a single millibucket is not shown anywhere else.
                final boolean isItem = myStack.getWhat().getType() == AEKeyType.items();
                // Shift asks for the exact number in the base unit - 1,040mB where the normal reading
                // rounds to 1B.
                final AmountFormat amountFormat = isShiftKeyDown() ? AmountFormat.FULL_BASE : AmountFormat.FULL;

                if (myStack.getStoredAmount() > (isItem ? 1 : 0)) {
                    final String local = (isItem ? ButtonToolTips.ItemsStored : ButtonToolTips.AmountStored).getLocal();
                    final String formattedAmount = myStack.getWhat().formatAmount(myStack.getStoredAmount(), amountFormat);
                    final String format = String.format(local, formattedAmount);

                    currentToolTip.add(TextFormatting.GRAY + format);
                }

                if (myStack.getRequestableAmount() > 0) {
                    final String local = (isItem ? ButtonToolTips.ItemsRequestable : ButtonToolTips.AmountRequestable).getLocal();
                    final String formattedAmount = myStack.getWhat().formatAmount(myStack.getRequestableAmount(), amountFormat);
                    final String format = String.format(local, formattedAmount);

                    currentToolTip.add(format);
                }

                if (myStack.isCraftable() && AEConfig.instance().isShowCraftableTooltip()) {
                    final String local = ButtonToolTips.ItemsCraftable.getLocal();
                    currentToolTip.add(TextFormatting.GRAY + local);
                }


                if (s instanceof PinSlotME && ((PinSlotME) s).isCraftingPin()) {
                    TerminalCraftingPin pin = ((PinSlotME) s).getCraftingStatus();
                    if (pin != null) {
                        String remaining = pin.getWhat().formatAmount(pin.getRemaining(), amountFormat);
                        String requested = pin.getWhat().formatAmount(pin.getRequested(), amountFormat);
                        currentToolTip.add(TextFormatting.AQUA + I18n.translateToLocalFormatted(
                                "gui.appliedenergistics2.craftingPinProgress", remaining, requested));
                    }
                }

                this.drawHoveringText(currentToolTip, x, y, this.fontRenderer);

                return;
            } else if (stack.getCount() > bigNumber) {
                final String local = ButtonToolTips.ItemsStored.getLocal();
                final String formattedAmount = NumberFormat.getNumberInstance(Locale.US).format(stack.getCount());
                final String format = String.format(local, formattedAmount);

                currentToolTip.add(TextFormatting.GRAY + format);

                this.drawHoveringText(currentToolTip, x, y, this.fontRenderer);

                return;
            }
        } else if (s instanceof AppEngSlot) {
            if (!(s instanceof SlotPlayerInv) && !(s instanceof SlotPlayerHotBar)) {
                // The line exists because an AE slot can hold more than a stack and the overlay abbreviates
                // it. One of something needs no line: it is what a view cell or an upgrade card holds, and a
                // wrapped key is always one item whatever amount it stands for - that one states its own
                // amount already.
                if (s.getStack().getCount() > 1) {
                    final String formattedAmount = NumberFormat.getNumberInstance(Locale.US).format(s.getStack().getCount());
                    currentToolTip.add(TextFormatting.GRAY + formattedAmount);
                    this.drawHoveringText(currentToolTip, x, y, this.fontRenderer);
                    return;
                }
            }
        }

        super.renderToolTip(stack, x, y);
    }
}
