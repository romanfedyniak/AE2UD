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

package appeng.client.gui.widgets;


import appeng.core.localization.GuiText;
import net.minecraft.client.resources.I18n;


/**
 * Opens the craft priority screen. It wears the icon every screen that configures something wears, and
 * carries the number in its tooltip, which is how every other button of the mod says what it is set to.
 */
public class GuiCraftPriorityButton extends GuiIconButton {

    private static final int ICON = 2 + 4 * 16;

    public GuiCraftPriorityButton(final int x, final int y) {
        super(x, y, ICON, GuiText.CraftPriority.getLocal());
    }

    public void setPriority(final int priority) {
        this.setMessage(I18n.format(GuiText.CraftPriorityOf.getUnlocalized(), priority));
    }
}
