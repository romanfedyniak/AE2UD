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

package appeng.client.gui.implementations;


import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.ITerminalHost;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.IKeyUnderMouse;
import appeng.client.gui.AmountEntry;
import appeng.client.gui.widgets.GuiStepButtons;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.container.AEBaseContainer;
import appeng.container.implementations.ContainerCraftAmount;
import appeng.core.localization.GuiText;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketCraftRequest;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.helpers.ISubMenuHost;
import appeng.helpers.Reflected;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;

import javax.annotation.Nullable;
import java.awt.Rectangle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class GuiCraftAmount extends AEBaseGui implements IKeyUnderMouse {

    /**
     * The order's item sits in a slot rather than a list, but HEI still asks the screen first.
     */
    @Nullable
    @Override
    public AEKey getKeyUnderMouse(final int mouseX, final int mouseY) {
        final Slot slot = this.getSlotUnderMouse();
        if (slot == null) {
            return null;
        }

        final GenericStack unwrapped = GenericStack.fromItemStack(slot.getStack());
        if (unwrapped != null) {
            return unwrapped.what();
        }
        return slot.getStack().isEmpty() ? null : AEItemKey.of(slot.getStack());
    }


    /**
     * Prefix a type's base unit carries when it is measured in a larger one - millibuckets. Matches what
     * {@code AEKeyFormatting} appends, which is package-private to the API.
     */
    private static final String BASE_UNIT_PREFIX = "m";

    private static final String EQUALS_PREFIX = "=";

    private static final int RANGE_Y = 16;
    private static final int RANGE_COLOR = 0x808080;

    /** The icon every screen that configures something wears, on the tab that opens the step settings. */
    private static final int STEP_SETTINGS_ICON = 2 + 4 * 16;

    private static final int FIELD_X = 62;
    private static final int FIELD_Y = 57;
    private static final int FIELD_WIDTH = 59;

    protected GuiTextField amountToCraft;
    protected GuiTabButton originalGuiBtn;

    protected GuiButton next;
    protected GuiButton unitToggle;
    protected GuiTabButton stepSettings;

    protected final GuiStepButtons steps = new GuiStepButtons();

    protected GuiBridge originalGui;

    /** The starting amount arrives over @GuiSync, which lands a tick after the screen opens. */
    private boolean primed;

    /** The field still holds the amount the type suggested, not one the player chose. */
    private boolean pristine;

    /**
     * What was typed before the button settings were opened. Coming back from them runs {@link #initGui()}
     * again on this same screen, and a field built fresh would otherwise throw the amount away.
     */
    private String kept;

    @Reflected
    public GuiCraftAmount(final InventoryPlayer inventoryPlayer, final ITerminalHost te) {
        super(new ContainerCraftAmount(inventoryPlayer, te));
    }

    protected GuiCraftAmount(final AEBaseContainer container) {
        super(container);
    }

    @Override
    public void initGui() {
        // Without this a held digit or backspace fires once. Turned back off in onGuiClosed, which runs
        // before the next screen's initGui, so a screen that wants it can still switch it back on.
        Keyboard.enableRepeatEvents(true);

        super.initGui();

        this.steps.addTo(this.buttonList, this.guiLeft, this.guiTop + 26, this.guiTop + 75);

        this.buttonList.add(this.next = new GuiButton(0, this.guiLeft + 128, this.guiTop + 51, 38, 20, GuiText.Next.getLocal()));

        // Sits outside the panel, where the other screens put their extra controls.
        this.buttonList.add(this.unitToggle = new GuiButton(0, this.guiLeft - 24, this.guiTop + 26, 22, 20, ""));

        // One place left of the corner, which the way back to the terminal takes when there is one. The
        // place is the same either way: a control that moves between screens is a control hunted for.
        this.buttonList.add(this.stepSettings = new GuiTabButton(this.guiLeft + 129, this.guiTop,
                STEP_SETTINGS_ICON, GuiText.AmountSteps.getLocal(), this.itemRender));

        // Whatever this screen was opened on top of already knows where back is and what to draw on the
        // way there, whether it is a terminal, a machine in the world or a terminal in the player's hand.
        final Object target = ((AEBaseContainer) this.inventorySlots).getTarget();
        ItemStack myIcon = ItemStack.EMPTY;

        if (target instanceof ISubMenuHost) {
            myIcon = ((ISubMenuHost) target).getItemStackRepresentation();
            this.originalGui = ((ISubMenuHost) target).getGuiBridge();
        }

        if (this.originalGui != null && !myIcon.isEmpty()) {
            this.buttonList.add(this.originalGuiBtn = new GuiTabButton(this.guiLeft + 154, this.guiTop, myIcon, myIcon.getDisplayName(), this.itemRender));
        }

        this.amountToCraft = new GuiTextField(0, this.fontRenderer, this.guiLeft + FIELD_X, this.guiTop + FIELD_Y, FIELD_WIDTH, this.fontRenderer.FONT_HEIGHT);
        this.amountToCraft.setEnableBackgroundDrawing(false);
        this.amountToCraft.setMaxStringLength(16);
        this.amountToCraft.setTextColor(0xFFFFFF);
        this.amountToCraft.setVisible(true);
        this.amountToCraft.setFocused(true);
        // Stands in for the one tick before the real starting amount arrives, and stays if it never does.
        this.amountToCraft.setText(this.kept == null ? "1" : this.kept);
        this.amountToCraft.setSelectionPos(0);
        this.kept = null;
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.fontRenderer.drawString(this.getTitle(), 8, 6, 4210752);

        final String range = this.getRangeLabel();
        if (range != null) {
            this.fontRenderer.drawString(range, 8, RANGE_Y, RANGE_COLOR);
        }
    }

    @Override
    public List<Rectangle> getJEIExclusionArea() {
        final List<Rectangle> area = new ArrayList<>();
        addButtonArea(area, this.unitToggle);
        return area;
    }

    /**
     * The span the field will accept, written in whatever unit the field is currently reading in, or null
     * when nothing bounds the amount from above and a range would say nothing.
     */
    @Nullable
    private String getRangeLabel() {
        final long max = this.getMaxAmount();
        if (max == Long.MAX_VALUE) {
            return null;
        }

        final int scale = this.unitScale();
        final String symbol = AmountEntry.symbol(this.getDisplayedKey(), scale);
        return AmountEntry.format(this.getMinAmount(), scale) + symbol + " - " + AmountEntry.format(max, scale) + symbol;
    }

    /**
     * The smallest amount this screen accepts.
     */
    protected long getMinAmount() {
        return 1;
    }

    /**
     * The largest, or {@link Long#MAX_VALUE} when nothing bounds it - ordering a craft has no ceiling
     * beyond what the field itself can hold.
     */
    protected long getMaxAmount() {
        return Long.MAX_VALUE;
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.prime();
        this.steps.update();

        this.next.displayString = this.getConfirmLabel();

        final AEKey what = this.getDisplayedKey();
        final int available = AmountEntry.unitOf(what);
        this.unitToggle.visible = available > 1;
        this.unitToggle.enabled = available > 1;
        if (this.unitToggle.visible) {
            final String symbol = what.getUnitSymbol();
            this.unitToggle.displayString = AmountEntry.unitsEnabled() ? symbol : BASE_UNIT_PREFIX + symbol;
        }

        this.bindTexture("guis/craft_amt.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);

        this.next.enabled = this.parseAmount(this.amountToCraft.getText()) > 0;

        // In unit entry the amount alone is ambiguous - "1" could be a bucket or a millibucket - so the
        // symbol follows the digits, and the field gives up the room it needs.
        final String symbol = AmountEntry.symbol(what, this.unitScale());
        this.amountToCraft.width = FIELD_WIDTH - AmountEntry.reservedWidth(this.fontRenderer, symbol);
        this.amountToCraft.drawTextBox();
        AmountEntry.drawSymbol(this.fontRenderer, this.amountToCraft, symbol);
    }

    @Override
    protected void keyTyped(final char character, final int key) throws IOException {
        if (!this.checkHotbarKeys(key)) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
                this.actionPerformed(this.next);
            }
            if (this.amountToCraft.textboxKeyTyped(character, key)) {
                this.pristine = false;
            } else {
                super.keyTyped(character, key);
            }
        }
    }

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        if (btn == this.originalGuiBtn) {
            NetworkHandler.instance().sendToServer(new PacketSwitchGuis(this.originalGui));
        }

        if (btn == this.unitToggle) {
            this.toggleUnits();
            return;
        }

        if (btn == this.stepSettings) {
            this.kept = this.amountToCraft.getText();
            this.mc.displayGuiScreen(new GuiAmountSteps(this, this.mc.player.inventory));
            return;
        }

        if (btn == this.next) {
            final long amount = this.parseAmount(this.amountToCraft.getText());
            this.confirm(amount > 0 ? amount : 1);
        }

        if (this.steps.isStep(btn)) {
            this.applyStep(btn);
        }
    }

    protected String getTitle() {
        return GuiText.SelectAmount.getLocal();
    }

    protected String getConfirmLabel() {
        return isShiftKeyDown() ? GuiText.Start.getLocal() : GuiText.Next.getLocal();
    }

    protected void confirm(final long amount) {
        NetworkHandler.instance().sendToServer(new PacketCraftRequest(amount, isShiftKeyDown(), this.craftsMissingAmount()));
    }

    /**
     * Whether the starting amount is an order being returned to that was written as a total.
     */
    protected boolean startsAsMissingAmount() {
        return ((ContainerCraftAmount) this.inventorySlots).initialCraftMissing;
    }

    /**
     * The amount to start on, or zero while the server has not sent one yet.
     */
    protected long getInitialAmount() {
        return ((ContainerCraftAmount) this.inventorySlots).initialAmount;
    }

    /**
     * Whether {@link #getInitialAmount()} is the type's suggestion rather than an amount that already
     * meant something. A suggestion is replaced by the first step button; a real amount is added to.
     */
    protected boolean startsFromSuggestion() {
        return true;
    }

    private void prime() {
        if (this.primed) {
            return;
        }

        final long initial = this.getInitialAmount();
        if (initial <= 0) {
            return;
        }

        this.primed = true;
        this.pristine = this.startsFromSuggestion();
        this.amountToCraft.setText((this.startsAsMissingAmount() ? EQUALS_PREFIX : "")
                + AmountEntry.format(initial, this.unitScale()));
        this.amountToCraft.setSelectionPos(0);
    }

    private void applyStep(final GuiButton btn) {
        final int scale = this.unitScale();
        long result = this.parseAmount(this.amountToCraft.getText());

        // An untouched suggestion is a starting point, not a number the player picked, so the first
        // press replaces it. Steps of a single base unit still add, which is what "+1" always did, and a
        // factor has nothing to replace - it works from the suggestion just as well.
        if (this.pristine && this.steps.isAdditive() && this.steps.stepOf(btn) * (long) scale > 1) {
            result = 0;
        }
        this.pristine = false;

        result = this.steps.apply(btn, result, this.getMinAmount(), this.getMaxAmount(), scale);

        this.amountToCraft.setText(this.formatAmount(result));
    }

    private void toggleUnits() {
        // Read in the old scale, write back in the new one, so the amount itself does not move.
        final long amount = this.parseAmount(this.amountToCraft.getText());
        AmountEntry.toggleUnits();
        this.amountToCraft.setText(this.formatAmount(amount));
    }

    /**
     * The key being ordered, read back out of the display slot: the container keeps the real one
     * server-side, and this stand-in is the only trace of it the client gets.
     */
    @Nullable
    private AEKey getDisplayedKey() {
        final Slot slot = this.inventorySlots.getSlot(0);
        final GenericStack stack = slot == null ? null : GenericStack.resolveItemStack(slot.getStack());
        return stack == null ? null : stack.what();
    }

    /** The scale the field currently reads in: the type's unit, or 1 for the base unit. */
    private int unitScale() {
        return AmountEntry.scaleOf(this.getDisplayedKey());
    }

    private String formatAmount(final long amount) {
        return (this.craftsMissingAmount() ? EQUALS_PREFIX : "") + AmountEntry.format(amount, this.unitScale());
    }

    private long parseAmount(final String text) {
        return AmountEntry.parse(text.startsWith(EQUALS_PREFIX) ? text.substring(1) : text, this.unitScale());
    }

    /**
     * Whether the field reads as a target total rather than as an amount to add - upstream's leading
     * {@code =}. Only ordering a craft can mean that; setting a slot's amount is already a total.
     */
    protected boolean craftsMissingAmount() {
        return this.amountToCraft != null && this.amountToCraft.getText().startsWith(EQUALS_PREFIX);
    }

    protected String getBackground() {
        return "guis/craftAmt.png";
    }
}
