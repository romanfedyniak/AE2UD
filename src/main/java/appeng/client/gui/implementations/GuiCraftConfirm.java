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
import appeng.api.features.IWirelessTermHandler;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.ITerminalHost;
import appeng.container.me.GridInventoryEntry;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.container.implementations.ContainerCraftConfirm;
import appeng.core.AELog;
import appeng.core.localization.GuiText;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.parts.reporting.PartCraftingTerminal;
import appeng.parts.reporting.PartExpandedProcessingPatternTerminal;
import appeng.parts.reporting.PartPatternTerminal;
import appeng.parts.reporting.PartTerminal;
import appeng.util.Platform;
import com.google.common.base.Joiner;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;


public class GuiCraftConfirm extends AEBaseGui {

    private final ContainerCraftConfirm ccc;

    private final int rows = 5;

    private final KeyCounter storage = new KeyCounter();
    private final KeyCounter pending = new KeyCounter();
    private final KeyCounter missing = new KeyCounter();

    private final List<AEKey> visual = new ArrayList<>();

    private GuiBridge OriginalGui;
    private GuiButton cancel;
    private GuiButton start;
    private GuiButton selectCPU;
    private int tooltip = -1;

    public GuiCraftConfirm(final InventoryPlayer inventoryPlayer, final ITerminalHost te) {
        super(new ContainerCraftConfirm(inventoryPlayer, te));
        this.xSize = 238;
        this.ySize = 206;

        final GuiScrollbar scrollbar = new GuiScrollbar();
        this.setScrollBar(scrollbar);

        this.ccc = (ContainerCraftConfirm) this.inventorySlots;
        this.ccc.setGui(this);

        if (te instanceof WirelessTerminalGuiObject) {
            ItemStack itemStack = ((WirelessTerminalGuiObject) te).getItemStack();
            this.OriginalGui = (GuiBridge) AEApi.instance().registries().wireless().getWirelessTerminalHandler(itemStack).getGuiHandler(itemStack);
        }

        if (te instanceof PartTerminal) {
            this.OriginalGui = GuiBridge.GUI_ME;
        }

        if (te instanceof PartCraftingTerminal) {
            this.OriginalGui = GuiBridge.GUI_CRAFTING_TERMINAL;
        }

        if (te instanceof PartPatternTerminal) {
            this.OriginalGui = GuiBridge.GUI_PATTERN_TERMINAL;
        }

        if (te instanceof PartExpandedProcessingPatternTerminal) {
            this.OriginalGui = GuiBridge.GUI_EXPANDED_PROCESSING_PATTERN_TERMINAL;
        }
    }

    boolean isAutoStart() {
        return ((ContainerCraftConfirm) this.inventorySlots).isAutoStart();
    }

    @Override
    public void initGui() {
        super.initGui();

        this.start = new GuiButton(0, this.guiLeft + 162, this.guiTop + this.ySize - 25, 50, 20, GuiText.Start.getLocal());
        this.start.enabled = false;
        this.buttonList.add(this.start);

        this.selectCPU = new GuiButton(0, this.guiLeft + (219 - 180) / 2, this.guiTop + this.ySize - 68, 180, 20, GuiText.CraftingCPU
                .getLocal() + ": " + GuiText.Automatic);
        this.selectCPU.enabled = false;
        this.buttonList.add(this.selectCPU);

        // Only when there is a screen to go back to. The add used to sit outside the branch, so a terminal
        // host this constructor has no GuiBridge for put a null in buttonList and GuiScreen.drawScreen
        // dereferenced it on the very first frame. Pre-existing, and unreachable until a host that offers no
        // way back could reach this screen at all.
        if (this.OriginalGui != null) {
            this.cancel = new GuiButton(0, this.guiLeft + 6, this.guiTop + this.ySize - 25, 50, 20, GuiText.Cancel.getLocal());
            this.buttonList.add(this.cancel);
        }
    }

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float btn) {
        this.updateCPUButtonText();

        this.start.enabled = !(this.ccc.hasNoCPU() || this.isSimulation());
        this.selectCPU.enabled = !this.isSimulation();

        final int gx = (this.width - this.xSize) / 2;
        final int gy = (this.height - this.ySize) / 2;

        this.tooltip = -1;

        final int offY = 23;
        int y = 0;
        int x = 0;
        for (int z = 0; z <= 4 * 5; z++) {
            final int minX = gx + 9 + x * 67;
            final int minY = gy + 22 + y * offY;

            if (minX < mouseX && minX + 67 > mouseX) {
                if (minY < mouseY && minY + offY - 2 > mouseY) {
                    this.tooltip = z;
                    break;
                }
            }

            x++;

            if (x > 2) {
                y++;
                x = 0;
            }
        }

        super.drawScreen(mouseX, mouseY, btn);
    }

    private void updateCPUButtonText() {
        String btnTextText = GuiText.CraftingCPU.getLocal() + ": " + GuiText.Automatic.getLocal();
        if (this.ccc.getSelectedCpu() >= 0)// && status.selectedCpu < status.cpus.size() )
        {
            if (this.ccc.getName().length() > 0) {
                final String name = this.ccc.getName().substring(0, Math.min(20, this.ccc.getName().length()));
                btnTextText = GuiText.CraftingCPU.getLocal() + ": " + name;
            } else {
                btnTextText = GuiText.CraftingCPU.getLocal() + ": #" + this.ccc.getSelectedCpu();
            }
        }

        if (this.ccc.hasNoCPU()) {
            btnTextText = GuiText.NoCraftingCPUs.getLocal();
        }

        this.selectCPU.displayString = btnTextText;
    }

    private boolean isSimulation() {
        return ((ContainerCraftConfirm) this.inventorySlots).isSimulation();
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        final long BytesUsed = this.ccc.getUsedBytes();
        final String byteUsed = NumberFormat.getInstance().format(BytesUsed);
        final String Add = BytesUsed > 0 ? (byteUsed + ' ' + GuiText.BytesUsed.getLocal()) : GuiText.CalculatingWait.getLocal();
        this.fontRenderer.drawString(GuiText.CraftingPlan.getLocal() + " - " + Add, 8, 7, 4210752);

        String dsp = null;

        if (this.isSimulation()) {
            dsp = GuiText.Simulation.getLocal();
        } else {
            dsp = this.ccc.getCpuAvailableBytes() > 0 ? (GuiText.Bytes.getLocal() + ": " + this.ccc.getCpuAvailableBytes() + " : " + GuiText.CoProcessors
                    .getLocal() + ": " + this.ccc.getCpuCoProcessors()) : GuiText.Bytes.getLocal() + ": N/A : " + GuiText.CoProcessors.getLocal() + ": N/A";
        }

        final int offset = (219 - this.fontRenderer.getStringWidth(dsp)) / 2;
        this.fontRenderer.drawString(dsp, offset, 165, 4210752);

        final int sectionLength = 67;

        int x = 0;
        int y = 0;
        final int xo = 9;
        final int yo = 22;
        final int viewStart = this.getScrollBar().getCurrentScroll() * 3;
        final int viewEnd = viewStart + 3 * this.rows;

        String dspToolTip = "";
        final List<String> lineList = new ArrayList<>();
        int toolPosX = 0;
        int toolPosY = 0;

        final int offY = 23;

        for (int z = viewStart; z < Math.min(viewEnd, this.visual.size()); z++) {
            final AEKey refKey = this.visual.get(z);// repo.getReferenceItem( z );
            if (refKey != null) {
                GlStateManager.pushMatrix();
                GlStateManager.scale(0.5, 0.5, 0.5);

                final long stored = this.storage.get(refKey);
                final long pendingAmount = this.pending.get(refKey);
                final long missingAmount = this.missing.get(refKey);

                int lines = 0;

                if (stored > 0) {
                    lines++;
                }
                if (missingAmount > 0) {
                    lines++;
                }
                if (pendingAmount > 0) {
                    lines++;
                }

                final int negY = ((lines - 1) * 5) / 2;
                int downY = 0;

                if (stored > 0) {
                    // Through the key's formatter, so a fluid row reads "16B" rather than "16k".
                    String str = GuiText.FromStorage.getLocal() + ": " + refKey.formatAmount(stored, AmountFormat.PREVIEW_LARGE);
                    final int w = 4 + this.fontRenderer.getStringWidth(str);
                    this.fontRenderer.drawString(str, (int) ((x * (1 + sectionLength) + xo + sectionLength - 19 - (w * 0.5)) * 2),
                            (y * offY + yo + 6 - negY + downY) * 2, 4210752);

                    if (this.tooltip == z - viewStart) {
                        lineList.add(GuiText.FromStorage.getLocal() + ": " + refKey.formatAmount(stored, AmountFormat.FULL));
                    }

                    downY += 5;
                }

                boolean red = false;
                if (missingAmount > 0) {
                    String str = GuiText.Missing.getLocal() + ": " + refKey.formatAmount(missingAmount, AmountFormat.PREVIEW_LARGE);
                    final int w = 4 + this.fontRenderer.getStringWidth(str);
                    this.fontRenderer.drawString(str, (int) ((x * (1 + sectionLength) + xo + sectionLength - 19 - (w * 0.5)) * 2),
                            (y * offY + yo + 6 - negY + downY) * 2, 4210752);

                    if (this.tooltip == z - viewStart) {
                        lineList.add(GuiText.Missing.getLocal() + ": " + refKey.formatAmount(missingAmount, AmountFormat.FULL));
                    }

                    red = true;
                    downY += 5;
                }

                if (pendingAmount > 0) {
                    String str = GuiText.ToCraft.getLocal() + ": " + refKey.formatAmount(pendingAmount, AmountFormat.PREVIEW_LARGE);
                    final int w = 4 + this.fontRenderer.getStringWidth(str);
                    this.fontRenderer.drawString(str, (int) ((x * (1 + sectionLength) + xo + sectionLength - 19 - (w * 0.5)) * 2),
                            (y * offY + yo + 6 - negY + downY) * 2, 4210752);

                    if (this.tooltip == z - viewStart) {
                        lineList.add(GuiText.ToCraft.getLocal() + ": " + refKey.formatAmount(pendingAmount, AmountFormat.FULL));
                    }
                }

                GlStateManager.popMatrix();
                final int posX = x * (1 + sectionLength) + xo + sectionLength - 19;
                final int posY = y * offY + yo;

                final ItemStack is = refKey.wrapForDisplayOrFilter();

                if (this.tooltip == z - viewStart) {
                    dspToolTip = Platform.getItemDisplayName(refKey);

                    if (lineList.size() > 0) {
                        dspToolTip = dspToolTip + '\n' + Joiner.on("\n").join(lineList);
                    }

                    toolPosX = x * (1 + sectionLength) + xo + sectionLength - 8;
                    toolPosY = y * offY + yo;
                }

                this.drawItem(posX, posY, is);

                if (red) {
                    final int startX = x * (1 + sectionLength) + xo;
                    final int startY = posY - 4;
                    drawRect(startX, startY, startX + sectionLength, startY + offY, 0x1AFF0000);
                }

                x++;

                if (x > 2) {
                    y++;
                    x = 0;
                }
            }
        }

        if (this.tooltip >= 0 && !dspToolTip.isEmpty()) {
            this.drawTooltip(toolPosX, toolPosY + 10, dspToolTip);
        }
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.setScrollBar();
        this.bindTexture("guis/craftingreport.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
    }

    private void setScrollBar() {
        final int size = this.visual.size();

        this.getScrollBar().setTop(19).setLeft(218).setHeight(114);
        this.getScrollBar().setRange(0, (size + 2) / 3 - this.rows, 1);
    }

    public void postUpdate(final List<GridInventoryEntry> list, final byte ref) {
        switch (ref) {
            case 0:
                for (final GridInventoryEntry l : list) {
                    this.handleInput(this.storage, l);
                }
                break;

            case 1:
                for (final GridInventoryEntry l : list) {
                    this.handleInput(this.pending, l);
                }
                break;

            case 2:
                for (final GridInventoryEntry l : list) {
                    this.handleInput(this.missing, l);
                }
                break;
        }

        for (final GridInventoryEntry l : list) {
            final long amt = this.getTotal(l.getWhat());

            if (amt <= 0) {
                this.visual.remove(l.getWhat());
            } else if (!this.visual.contains(l.getWhat())) {
                this.visual.add(l.getWhat());
            }
        }

        this.setScrollBar();
    }

    private void handleInput(final KeyCounter s, final GridInventoryEntry l) {
        // Each of the three KeyCounters is keyed by AEKey, whose equals() is already the size-insensitive
        // identity the old findPrecise(IAEItemStack) dance existed to provide - a plain set() replaces it.
        s.set(l.getWhat(), l.getStoredAmount());
    }

    private long getTotal(final AEKey what) {
        return this.storage.get(what) + this.pending.get(what) + this.missing.get(what);
    }

    @Override
    protected void keyTyped(final char character, final int key) throws IOException {
        if (!this.checkHotbarKeys(key)) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
                this.actionPerformed(this.start);
            }
            super.keyTyped(character, key);
        }
    }

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        final boolean backwards = Mouse.isButtonDown(1);

        if (btn == this.selectCPU) {
            try {
                NetworkHandler.instance().sendToServer(new PacketValueConfig("Terminal.Cpu", backwards ? "Prev" : "Next"));
            } catch (final IOException e) {
                AELog.debug(e);
            }
        }

        if (btn == this.cancel) {
            NetworkHandler.instance().sendToServer(new PacketSwitchGuis(this.OriginalGui));
        }

        if (btn == this.start) {
            try {
                NetworkHandler.instance().sendToServer(new PacketValueConfig("Terminal.Start", "Start"));
            } catch (final Throwable e) {
                AELog.debug(e);
            }
        }
    }

    public List<AEKey> getVisual() {
        return visual;
    }

    public int getDisplayedRows() {
        return this.rows;
    }
}
