package appeng.client.gui.implementations;

import java.awt.Rectangle;
import java.util.List;

import net.minecraft.client.gui.Gui;
import net.minecraft.entity.player.InventoryPlayer;

import appeng.helpers.WirelessTerminalGuiObject;

public class GuiWirelessInterfaceTerminal extends GuiInterfaceTerminal {

    /**
     * Three pixels clear of the window. The lower part of this window is narrower than the rest - its texture
     * runs out at 189 where the top of it reaches 208 - and the plate used to start on that last column.
     */
    private static final int PLATE_X = 193;

    public GuiWirelessInterfaceTerminal(InventoryPlayer inventoryPlayer, final WirelessTerminalGuiObject te) {
        super(inventoryPlayer, te);
    }

    /**
     * This window stretches with the number of interfaces it lists, and {@code repositionSlots} moves every
     * slot with it - the upgrade slot included. The plate has to follow, or it only lines up at the shortest
     * window the terminal style allows.
     *
     * <p>Three pixels below the step in the window's right edge as well. The lower part of the window is
     * narrower than the rest, but not immediately: the corner takes two rows to come in, and the plate used to
     * start on the second of them.</p>
     */
    private int plateY() {
        return this.ySize - 90;
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.bindTexture("guis/wirelessupgrades.png");
        Gui.drawModalRectWithCustomSizedTexture(offsetX + PLATE_X, offsetY + this.plateY(), 0, 0, 32, 32, 32, 32);
        super.drawBG(offsetX, offsetY, mouseX, mouseY);
    }

    @Override
    public List<Rectangle> getJEIExclusionArea() {
        final List<Rectangle> area = super.getJEIExclusionArea();
        area.add(new Rectangle(this.guiLeft + PLATE_X, this.guiTop + this.plateY(), 32, 32));
        return area;
    }
}
