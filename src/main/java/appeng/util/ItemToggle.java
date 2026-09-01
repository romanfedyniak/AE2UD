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

package appeng.util;

import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;

/**
 * An item that does nothing while it is switched off, kept in a slot rather than carried: the magnet card in
 * a wireless terminal, a view cell in a terminal's own row.
 *
 * <p>The flag is absent until the item is switched off for the first time, so anything that has never been
 * touched works. Reading it by hand is what makes that easy to get wrong - a plain {@code getBoolean} on a
 * fresh item answers false - which is why it is read here and nowhere else.</p>
 */
public final class ItemToggle {

    private static final String ENABLED_TAG = "enabled";

    private ItemToggle() {
    }

    public static boolean isEnabled(final ItemStack stack) {
        final NBTTagCompound tag = stack.getTagCompound();

        return tag == null || !tag.hasKey(ENABLED_TAG) || tag.getBoolean(ENABLED_TAG);
    }

    /**
     * Right-clicking one of these in its slot switches it rather than picking it up. Whether the slot holds
     * something switchable at all is the caller's to know; the slots beside it hold things that have no such
     * switch and must pick up as usual.
     *
     * @return true when the click was that, and the container should do nothing else with it
     */
    public static boolean toggle(final Slot slot, final int dragType, final ClickType clickType) {
        if (clickType != ClickType.PICKUP || dragType != 1 || slot == null || !slot.getHasStack()) {
            return false;
        }

        final ItemStack stack = slot.getStack();
        final NBTTagCompound tag = Platform.openNbtData(stack);
        tag.setBoolean(ENABLED_TAG, !isEnabled(stack));
        slot.onSlotChanged();

        return true;
    }

    /**
     * The state as a tooltip line. Coloured, because it is one grey line among several on items that carry a
     * good deal of grey text, and it is the only one that changes.
     */
    public static String describe(final ItemStack stack) {
        return isEnabled(stack)
                ? TextFormatting.GREEN + I18n.translateToLocal("gui.tooltips.appliedenergistics2.Enable")
                : TextFormatting.RED + I18n.translateToLocal("gui.tooltips.appliedenergistics2.Disabled");
    }
}
