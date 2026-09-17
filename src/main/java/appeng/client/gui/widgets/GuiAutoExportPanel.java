/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.client.gui.widgets;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.implementations.IAutoExportHost;
import appeng.api.util.AutoExport;
import appeng.api.util.RelativeSide;
import appeng.client.gui.AEBaseGui;
import appeng.core.localization.ButtonToolTips;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketAutoExportSide;

/**
 * The auto-export button of a machine's window, and the panel of six faces it opens to the left of itself. The
 * screen attaches it, feeds it the container's synced state every frame, and passes it clicks, Escape and its
 * HEI exclusion areas.
 */
public final class GuiAutoExportPanel {

    private static final int FACE = 18;
    private static final int GAP = 2;
    private static final int PADDING = 6;
    private static final int TITLE = 12;
    private static final int GRID = 3 * FACE + 2 * GAP;
    private static final long OPENING_MS = 120;
    /** Where each face sits in the cross, as column and row. */
    private static final Map<RelativeSide, int[]> CELLS = new EnumMap<>(RelativeSide.class);

    static {
        CELLS.put(RelativeSide.TOP, new int[] { 1, 0 });
        CELLS.put(RelativeSide.LEFT, new int[] { 0, 1 });
        CELLS.put(RelativeSide.FRONT, new int[] { 1, 1 });
        CELLS.put(RelativeSide.RIGHT, new int[] { 2, 1 });
        CELLS.put(RelativeSide.BOTTOM, new int[] { 1, 2 });
        CELLS.put(RelativeSide.BACK, new int[] { 2, 2 });
    }

    private final TileEntity tile;
    private final IAutoExportHost host;
    private final Map<RelativeSide, FaceButton> faces = new EnumMap<>(RelativeSide.class);
    private Toggle toggle;
    private int state;
    private boolean open;
    /** How far open the panel was when it was last told to open or shut, and when that was. */
    private float movedFrom;
    private long movedAt;

    public <T extends TileEntity & IAutoExportHost> GuiAutoExportPanel(final T host) {
        this.tile = host;
        this.host = host;
    }

    /** Puts the button at a place in the column and its faces after it. Call once per layout. */
    public void attach(final List<GuiButton> buttonList, final int x, final int y) {
        this.toggle = new Toggle(x, y);
        buttonList.add(this.toggle);
        for (final RelativeSide side : RelativeSide.values()) {
            final FaceButton face = new FaceButton(side);
            this.faces.put(side, face);
            buttonList.add(face);
        }
        this.layOut();
    }

    /** The container's {@link AutoExport#getSyncState()}, every frame. */
    public void update(final int state) {
        this.state = state;
        if (this.toggle != null) {
            this.toggle.set(this.anyOn() ? YesNo.YES : YesNo.NO);
        }
    }

    /** @return true when the click was the panel's, and the screen should make nothing else of it */
    public boolean actionPerformed(final GuiButton button) {
        if (button == this.toggle) {
            this.setOpen(!this.open);
            return true;
        }
        if (button instanceof FaceButton && this.faces.containsValue(button)) {
            NetworkHandler.instance().sendToServer(new PacketAutoExportSide(((FaceButton) button).side));
            return true;
        }
        return false;
    }

    /** @return true if the panel was open, so Escape shuts it rather than the window */
    public boolean close() {
        final boolean was = this.open;
        this.setOpen(false);
        return was;
    }

    /** Turned round from wherever it has got to, so a click halfway through does not jump. */
    private void setOpen(final boolean open) {
        if (open != this.open) {
            this.movedFrom = this.opening();
            this.movedAt = System.currentTimeMillis();
            this.open = open;
        }
    }

    public void addExclusionAreas(final List<Rectangle> areas) {
        if (this.toggle != null && this.opening() > 0F) {
            areas.add(new Rectangle(this.panelLeft(), this.toggle.y, this.panelWidth(), this.panelHeight()));
        }
    }

    private boolean anyOn() {
        for (final RelativeSide side : RelativeSide.values()) {
            if (AutoExport.isOn(this.state, side) && !AutoExport.isRefused(this.state, side)) {
                return true;
            }
        }
        return false;
    }

    private String title() {
        return ButtonToolTips.AutoExport.getLocal();
    }

    private int panelWidth() {
        return Math.max(GRID, Minecraft.getMinecraft().fontRenderer.getStringWidth(this.title())) + 2 * PADDING;
    }

    private int panelHeight() {
        return PADDING + TITLE + GRID + PADDING;
    }

    private int panelLeft() {
        return this.toggle.x - GAP - this.panelWidth();
    }

    private void layOut() {
        final int left = this.panelLeft() + (this.panelWidth() - GRID) / 2;
        final int top = this.toggle.y + PADDING + TITLE;
        for (final FaceButton face : this.faces.values()) {
            final int[] cell = CELLS.get(face.side);
            face.x = left + cell[0] * (FACE + GAP);
            face.y = top + cell[1] * (FACE + GAP);
        }
    }

    /** 0 shut, 1 open, and in between while it unfolds or folds away. */
    private float opening() {
        final float moved = (System.currentTimeMillis() - this.movedAt) / (float) OPENING_MS;
        return this.open ? Math.min(1F, this.movedFrom + moved) : Math.max(0F, this.movedFrom - moved);
    }

    private static String faceName(final RelativeSide side) {
        switch (side) {
            case TOP:
                return ButtonToolTips.FaceTop.getLocal();
            case BOTTOM:
                return ButtonToolTips.FaceBottom.getLocal();
            case LEFT:
                return ButtonToolTips.FaceLeft.getLocal();
            case RIGHT:
                return ButtonToolTips.FaceRight.getLocal();
            case FRONT:
                return ButtonToolTips.FaceFront.getLocal();
            default:
                return ButtonToolTips.FaceBack.getLocal();
        }
    }

    /** The column's button, which also draws the panel behind the faces while it is open. */
    private final class Toggle extends GuiImgButton {

        Toggle(final int x, final int y) {
            super(x, y, Settings.AUTO_EXPORT, YesNo.NO);
        }

        @Override
        public void drawButton(final Minecraft mc, final int mouseX, final int mouseY, final float partial) {
            final GuiAutoExportPanel panel = GuiAutoExportPanel.this;
            final float t = panel.opening();
            final boolean shown = panel.open && t >= 1F;
            if (t > 0F && this.visible) {
                final int width = Math.round(FACE + (panel.panelWidth() - FACE) * t);
                final int height = Math.round(FACE + (panel.panelHeight() - FACE) * t);
                // Unfolds from the corner beside the button, and folds back into it
                AEBaseGui.drawPanel(this.x - GAP - width, this.y, width, height);
                if (shown) {
                    mc.fontRenderer.drawString(panel.title(), panel.panelLeft() + PADDING, this.y + PADDING,
                            4210752);
                }
            }

            panel.layOut();
            for (final FaceButton face : panel.faces.values()) {
                face.visible = shown && this.visible;
                face.enabled = face.visible && AutoExport.isAvailable(panel.state, face.side);
            }

            super.drawButton(mc, mouseX, mouseY, partial);
        }

        @Override
        public String getMessage() {
            final GuiAutoExportPanel panel = GuiAutoExportPanel.this;
            final List<String> on = new ArrayList<>();
            for (final RelativeSide side : RelativeSide.values()) {
                if (AutoExport.isOn(panel.state, side) && !AutoExport.isRefused(panel.state, side)) {
                    on.add(faceName(side));
                }
            }

            final String value = on.isEmpty() ? ButtonToolTips.AutoExportOff.getLocal()
                    : String.format(ButtonToolTips.AutoExportTo.getLocal(), String.join(", ", on));
            return panel.title() + '\n' + value + '\n' + ButtonToolTips.AutoExportConfigure.getLocal();
        }
    }

    /** One face: green when it pushes out, red when it does not, plain and dead when nothing there takes it. */
    private final class FaceButton extends GuiButton implements ITooltip {

        private final RelativeSide side;
        private IBlockState shownState;
        private ItemStack shownBlock = ItemStack.EMPTY;

        FaceButton(final RelativeSide side) {
            super(0, 0, 0, FACE, FACE, "");
            this.side = side;
            this.visible = false;
        }

        @Override
        public void drawButton(final Minecraft mc, final int mouseX, final int mouseY, final float partial) {
            if (!this.visible) {
                return;
            }

            this.hovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width
                    && mouseY < this.y + this.height;

            final int state = GuiAutoExportPanel.this.state;
            final int v;
            if (!this.enabled) {
                v = 46;
                GlStateManager.color(1F, 1F, 1F, 1F);
            } else {
                v = this.hovered ? 86 : 66;
                if (AutoExport.isOn(state, this.side)) {
                    GlStateManager.color(0.45F, 1F, 0.45F, 1F);
                } else {
                    GlStateManager.color(1F, 0.45F, 0.45F, 1F);
                }
            }

            // The vanilla button, its four corners cut down to this size
            AEBaseGui.enableSpriteBlending();
            mc.getTextureManager().bindTexture(BUTTON_TEXTURES);
            final int half = FACE / 2;
            this.drawTexturedModalRect(this.x, this.y, 0, v, half, half);
            this.drawTexturedModalRect(this.x + half, this.y, 200 - half, v, half, half);
            this.drawTexturedModalRect(this.x, this.y + half, 0, v + 20 - half, half, half);
            this.drawTexturedModalRect(this.x + half, this.y + half, 200 - half, v + 20 - half, half, half);
            GlStateManager.color(1F, 1F, 1F, 1F);

            if (this.enabled) {
                final ItemStack block = this.block(mc);
                if (!block.isEmpty()) {
                    GlStateManager.enableDepth();
                    RenderHelper.enableGUIStandardItemLighting();
                    mc.getRenderItem().renderItemAndEffectIntoGUI(block, this.x + 1, this.y + 1);
                    RenderHelper.disableStandardItemLighting();
                    GlStateManager.disableDepth();
                }
            }
        }

        /** What the block on this face is, asked again only when the block there changes. */
        private ItemStack block(final Minecraft mc) {
            final World world = GuiAutoExportPanel.this.tile.getWorld();
            if (world == null || mc.player == null) {
                return ItemStack.EMPTY;
            }

            final IAutoExportHost host = GuiAutoExportPanel.this.host;
            final EnumFacing facing = this.side.toFacing(host.getForward(), host.getUp());
            final BlockPos pos = GuiAutoExportPanel.this.tile.getPos().offset(facing);
            final IBlockState blockState = world.getBlockState(pos);
            if (blockState != this.shownState) {
                this.shownState = blockState;
                try {
                    final RayTraceResult hit = new RayTraceResult(new Vec3d(pos).add(0.5, 0.5, 0.5),
                            facing.getOpposite(), pos);
                    this.shownBlock = blockState.getBlock().getPickBlock(blockState, hit, world, pos, mc.player);
                } catch (final RuntimeException e) {
                    // A block that cannot say what it is picked as is still a face to push into
                    this.shownBlock = ItemStack.EMPTY;
                }
            }
            return this.shownBlock;
        }

        @Override
        public String getMessage() {
            final int state = GuiAutoExportPanel.this.state;
            final StringBuilder message = new StringBuilder(faceName(this.side));

            if (AutoExport.isRefused(state, this.side)) {
                final String refusal = GuiAutoExportPanel.this.host.getAutoExportRefusal(this.side);
                if (refusal != null) {
                    message.append('\n').append(I18n.format(refusal));
                }
                return message.toString();
            }

            if (!AutoExport.isAvailable(state, this.side)) {
                return message.append('\n').append(ButtonToolTips.AutoExportFaceNothing.getLocal()).toString();
            }

            final ItemStack block = this.block(Minecraft.getMinecraft());
            if (!block.isEmpty()) {
                message.append('\n').append(block.getDisplayName());
            }
            message.append('\n').append(AutoExport.isOn(state, this.side)
                    ? ButtonToolTips.AutoExportFaceOn.getLocal()
                    : ButtonToolTips.AutoExportFaceOff.getLocal());
            return message.toString();
        }

        @Override
        public int xPos() {
            return this.x;
        }

        @Override
        public int yPos() {
            return this.y;
        }

        @Override
        public int getWidth() {
            return this.width;
        }

        @Override
        public int getHeight() {
            return this.height;
        }

        @Override
        public boolean isVisible() {
            return this.visible;
        }
    }
}
