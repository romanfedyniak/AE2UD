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


import appeng.api.AEApi;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.definitions.IDefinitions;
import appeng.api.definitions.IParts;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.ITerminalHost;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.MathExpressionParser;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.container.AEBaseContainer;
import appeng.container.implementations.ContainerCraftAmount;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.localization.GuiText;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketCraftRequest;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.helpers.Reflected;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.parts.reporting.PartCraftingTerminal;
import appeng.parts.reporting.PartExpandedProcessingPatternTerminal;
import appeng.parts.reporting.PartPatternTerminal;
import appeng.parts.reporting.PartTerminal;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.input.Keyboard;

import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.RoundingMode;


public class GuiCraftAmount extends AEBaseGui {

    /**
     * Prefix a type's base unit carries when it is measured in a larger one - millibuckets. Matches what
     * {@code AEKeyFormatting} appends, which is package-private to the API.
     */
    private static final String BASE_UNIT_PREFIX = "m";

    private static final BigDecimal MAX_AMOUNT = BigDecimal.valueOf(Long.MAX_VALUE);

    private static final int FIELD_X = 62;
    private static final int FIELD_Y = 57;
    private static final int FIELD_WIDTH = 59;

    private static final int SUFFIX_GAP = 1;
    private static final int SUFFIX_COLOR = 0xFFFFFF;

    /**
     * Where the text box starts drawing from. Private, and there is no accessor: {@code drawTextBox}
     * renders {@code text.substring(lineScrollOffset)} trimmed to the box width, so without it there is
     * no way to know how wide the amount was actually drawn - only how wide the whole string would be.
     */
    private static final Field LINE_SCROLL_OFFSET = findLineScrollOffset();

    private static Field findLineScrollOffset() {
        try {
            return ReflectionHelper.findField(GuiTextField.class, "lineScrollOffset", "field_146225_q");
        } catch (final RuntimeException e) {
            AELog.debug(e);
            return null;
        }
    }

    protected GuiTextField amountToCraft;
    protected GuiTabButton originalGuiBtn;

    protected GuiButton next;
    protected GuiButton unitToggle;

    protected GuiButton plus1;
    protected GuiButton plus10;
    protected GuiButton plus100;
    protected GuiButton plus1000;
    protected GuiButton minus1;
    protected GuiButton minus10;
    protected GuiButton minus100;
    protected GuiButton minus1000;

    protected GuiBridge originalGui;

    /** The starting amount arrives over @GuiSync, which lands a tick after the screen opens. */
    private boolean primed;

    /** The field still holds the amount the type suggested, not one the player chose. */
    private boolean pristine;

    @Reflected
    public GuiCraftAmount(final InventoryPlayer inventoryPlayer, final ITerminalHost te) {
        super(new ContainerCraftAmount(inventoryPlayer, te));
    }

    protected GuiCraftAmount(final AEBaseContainer container) {
        super(container);
    }

    @Override
    public void initGui() {
        super.initGui();

        final int a = AEConfig.instance().craftItemsByStackAmounts(0);
        final int b = AEConfig.instance().craftItemsByStackAmounts(1);
        final int c = AEConfig.instance().craftItemsByStackAmounts(2);
        final int d = AEConfig.instance().craftItemsByStackAmounts(3);

        this.buttonList.add(this.plus1 = new GuiButton(0, this.guiLeft + 20, this.guiTop + 26, 22, 20, "+" + a));
        this.buttonList.add(this.plus10 = new GuiButton(0, this.guiLeft + 48, this.guiTop + 26, 28, 20, "+" + b));
        this.buttonList.add(this.plus100 = new GuiButton(0, this.guiLeft + 82, this.guiTop + 26, 32, 20, "+" + c));
        this.buttonList.add(this.plus1000 = new GuiButton(0, this.guiLeft + 120, this.guiTop + 26, 38, 20, "+" + d));

        this.buttonList.add(this.minus1 = new GuiButton(0, this.guiLeft + 20, this.guiTop + 75, 22, 20, "-" + a));
        this.buttonList.add(this.minus10 = new GuiButton(0, this.guiLeft + 48, this.guiTop + 75, 28, 20, "-" + b));
        this.buttonList.add(this.minus100 = new GuiButton(0, this.guiLeft + 82, this.guiTop + 75, 32, 20, "-" + c));
        this.buttonList.add(this.minus1000 = new GuiButton(0, this.guiLeft + 120, this.guiTop + 75, 38, 20, "-" + d));

        this.buttonList.add(this.next = new GuiButton(0, this.guiLeft + 128, this.guiTop + 51, 38, 20, GuiText.Next.getLocal()));

        // Sits outside the panel, where the other screens put their extra controls.
        this.buttonList.add(this.unitToggle = new GuiButton(0, this.guiLeft - 24, this.guiTop + 26, 22, 20, ""));

        ItemStack myIcon = null;
        final Object target = ((AEBaseContainer) this.inventorySlots).getTarget();
        final IDefinitions definitions = AEApi.instance().definitions();
        final IParts parts = definitions.parts();

        if (target instanceof WirelessTerminalGuiObject) {
            myIcon = ((WirelessTerminalGuiObject) target).getItemStack();
            this.originalGui = (GuiBridge) AEApi.instance().registries().wireless().getWirelessTerminalHandler(myIcon).getGuiHandler(myIcon);
        }

        if (target instanceof PartTerminal) {
            myIcon = parts.terminal().maybeStack(1).orElse(ItemStack.EMPTY);
            this.originalGui = GuiBridge.GUI_ME;
        }

        if (target instanceof PartCraftingTerminal) {
            myIcon = parts.craftingTerminal().maybeStack(1).orElse(ItemStack.EMPTY);
            this.originalGui = GuiBridge.GUI_CRAFTING_TERMINAL;
        }

        if (target instanceof PartPatternTerminal) {
            myIcon = parts.patternTerminal().maybeStack(1).orElse(ItemStack.EMPTY);
            this.originalGui = GuiBridge.GUI_PATTERN_TERMINAL;
        }

        if (target instanceof PartExpandedProcessingPatternTerminal) {
            myIcon = parts.expandedProcessingPatternTerminal().maybeStack(1).orElse(ItemStack.EMPTY);
            this.originalGui = GuiBridge.GUI_EXPANDED_PROCESSING_PATTERN_TERMINAL;
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
        this.amountToCraft.setText("1");
        this.amountToCraft.setSelectionPos(0);
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.fontRenderer.drawString(this.getTitle(), 8, 6, 4210752);
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.prime();

        this.next.displayString = this.getConfirmLabel();

        final int available = this.availableUnitScale();
        this.unitToggle.visible = available > 1;
        this.unitToggle.enabled = available > 1;
        if (this.unitToggle.visible) {
            final String symbol = this.getDisplayedKey().getUnitSymbol();
            this.unitToggle.displayString = this.unitsEnabled() ? symbol : BASE_UNIT_PREFIX + symbol;
        }

        this.bindTexture("guis/craft_amt.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);

        this.next.enabled = this.parseAmount(this.amountToCraft.getText()) > 0;

        // In unit entry the amount alone is ambiguous - "1" could be a bucket or a millibucket - so the
        // symbol follows the digits. The caret's own width is always reserved, whether or not it is
        // blinking at that moment, so the symbol neither sits under it nor jumps as it blinks.
        final String suffix = this.unitScale() > 1 ? this.getDisplayedKey().getUnitSymbol() : "";
        final int caret = this.fontRenderer.getStringWidth("_");
        final int suffixWidth = suffix.isEmpty()
                ? 0
                : this.fontRenderer.getStringWidth(suffix) + caret + SUFFIX_GAP;

        this.amountToCraft.width = FIELD_WIDTH - suffixWidth;
        this.amountToCraft.drawTextBox();

        if (suffixWidth > 0) {
            this.fontRenderer.drawString(suffix,
                    this.guiLeft + FIELD_X + this.drawnAmountWidth() + caret + SUFFIX_GAP,
                    this.guiTop + FIELD_Y, SUFFIX_COLOR);
        }
    }

    /**
     * How wide the amount is on screen. Not the width of the whole text: once it outgrows the box the
     * field scrolls, and deleting from the end shrinks what is drawn without scrolling back.
     */
    private int drawnAmountWidth() {
        final String text = this.amountToCraft.getText();
        int offset = 0;

        if (LINE_SCROLL_OFFSET != null) {
            try {
                offset = LINE_SCROLL_OFFSET.getInt(this.amountToCraft);
            } catch (final IllegalAccessException e) {
                AELog.debug(e);
            }
        }

        offset = Math.max(0, Math.min(offset, text.length()));
        final String drawn = this.fontRenderer.trimStringToWidth(text.substring(offset),
                this.amountToCraft.getWidth());
        return this.fontRenderer.getStringWidth(drawn);
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

        if (btn == this.next) {
            final long amount = this.parseAmount(this.amountToCraft.getText());
            this.confirm(amount > 0 ? amount : 1);
        }

        final boolean isPlus = btn == this.plus1 || btn == this.plus10 || btn == this.plus100 || btn == this.plus1000;
        final boolean isMinus = btn == this.minus1 || btn == this.minus10 || btn == this.minus100 || btn == this.minus1000;

        if (isPlus || isMinus) {
            this.addQty((long) this.getQty(btn) * this.unitScale());
        }
    }

    protected String getTitle() {
        return GuiText.SelectAmount.getLocal();
    }

    protected String getConfirmLabel() {
        return isShiftKeyDown() ? GuiText.Start.getLocal() : GuiText.Next.getLocal();
    }

    protected void confirm(final long amount) {
        NetworkHandler.instance().sendToServer(new PacketCraftRequest(amount, isShiftKeyDown()));
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
        this.amountToCraft.setText(this.formatAmount(initial));
        this.amountToCraft.setSelectionPos(0);
    }

    private void addQty(final long i) {
        long result = this.parseAmount(this.amountToCraft.getText());

        // An untouched suggestion is a starting point, not a number the player picked, so the first
        // press replaces it. Steps of a single base unit still add, which is what "+1" always did.
        if (this.pristine && i > 1) {
            result = 0;
        }
        this.pristine = false;

        result += i;
        if (result < 1) {
            result = 1;
        }

        this.amountToCraft.setText(this.formatAmount(result));
    }

    private void toggleUnits() {
        // Read in the old scale, write back in the new one, so the amount itself does not move.
        final long amount = this.parseAmount(this.amountToCraft.getText());

        AEConfig.instance().getConfigManager()
                .putSetting(Settings.AMOUNT_ENTRY_UNITS, this.unitsEnabled() ? YesNo.NO : YesNo.YES);

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

    /**
     * How much of the ordered type makes one displayed unit, or 1 for a type that has no larger one.
     * <p>
     * Only powers of ten qualify. The field has to survive a round trip through decimal text, and a
     * unit of, say, 144 does not divide into one - the amount would drift every time it was re-read.
     */
    private int availableUnitScale() {
        final AEKey what = this.getDisplayedKey();
        if (what == null) {
            return 1;
        }

        final int perUnit = what.getAmountPerUnit();
        for (long power = 10; power <= perUnit; power *= 10) {
            if (power == perUnit) {
                return perUnit;
            }
        }
        return 1;
    }

    private boolean unitsEnabled() {
        return AEConfig.instance().getConfigManager().getSetting(Settings.AMOUNT_ENTRY_UNITS) == YesNo.YES;
    }

    /** The scale the field currently reads in: the type's unit, or 1 for the base unit. */
    private int unitScale() {
        return this.unitsEnabled() ? this.availableUnitScale() : 1;
    }

    /**
     * Renders a raw amount for the entry field. In unit mode the fractional part is written out in
     * full, so reading the field back gives the same number: 1001 mB is "1.001", never "1".
     */
    private String formatAmount(final long amount) {
        final int scale = this.unitScale();
        if (scale <= 1) {
            return Long.toString(amount);
        }

        final long remainder = amount % scale;
        if (remainder == 0) {
            return Long.toString(amount / scale);
        }

        String fraction = String.format("%0" + (Long.toString(scale).length() - 1) + "d", remainder);
        while (fraction.endsWith("0")) {
            fraction = fraction.substring(0, fraction.length() - 1);
        }
        return amount / scale + "." + fraction;
    }

    /**
     * Reads the entry field back into a raw amount.
     * <p>
     * Rounding to a whole amount happens last, after the unit scale is applied: rounding first would
     * turn one and a half buckets into two of them instead of 1500 mB.
     *
     * @return zero when the field does not hold a usable positive number.
     */
    private long parseAmount(final String text) {
        final double value = MathExpressionParser.parse(text);
        if (Double.isNaN(value) || Double.isInfinite(value) || value <= 0) {
            return 0;
        }

        final BigDecimal scaled = BigDecimal.valueOf(value).multiply(BigDecimal.valueOf(this.unitScale()));
        return scaled.min(MAX_AMOUNT).setScale(0, RoundingMode.HALF_UP).longValue();
    }

    protected String getBackground() {
        return "guis/craftAmt.png";
    }
}
