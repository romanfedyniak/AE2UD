package appeng.client.gui.implementations;

import java.awt.Rectangle;
import java.io.IOException;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;

import appeng.client.gui.widgets.GuiTerminalModeSwitch;
import appeng.client.gui.widgets.GuiWirelessUpgradePlate;
import appeng.container.interfaces.IWirelessTerminalContainer;
import appeng.helpers.WirelessTerminalGuiObject;

public class GuiWirelessInterfaceTerminal extends GuiInterfaceTerminal {

    /**
     * Three pixels clear of the window. The lower part of this window is narrower than the rest - its texture
     * runs out at 189 where the top of it reaches 208 - and the plate used to start on that last column.
     */
    private static final int PLATE_X = 193;

    private final GuiTerminalModeSwitch modeSwitch = new GuiTerminalModeSwitch(this);

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

    /**
     * This screen empties its button list on every frame and puts its own back, so the switch has to go back
     * with them rather than being added once when the screen opens.
     */
    @Override
    protected void addExtraButtons() {
        if (this.inventorySlots instanceof IWirelessTerminalContainer) {
            this.modeSwitch.attach(this.buttonList,
                    ((IWirelessTerminalContainer) this.inventorySlots).getTerminal(), this.guiLeft, this.guiTop);
        }
    }

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        if (this.modeSwitch.actionPerformed(btn)) {
            return;
        }

        super.actionPerformed(btn);
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        GuiWirelessUpgradePlate.draw(this, offsetX + PLATE_X, offsetY + this.plateY(),
                IWirelessTerminalContainer.UPGRADE_SLOTS);
        super.drawBG(offsetX, offsetY, mouseX, mouseY);
    }

    @Override
    public List<Rectangle> getJEIExclusionArea() {
        final List<Rectangle> area = super.getJEIExclusionArea();
        GuiWirelessUpgradePlate.addExclusionArea(area, this.guiLeft + PLATE_X, this.guiTop + this.plateY(),
                IWirelessTerminalContainer.UPGRADE_SLOTS);
        this.modeSwitch.addExclusionAreas(area);
        return area;
    }
}
