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

package appeng.client.gui.widgets;


import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.AEBaseGui;
import appeng.core.localization.GuiText;
import appeng.crafting.tree.CraftingPlanNode;
import appeng.crafting.tree.CraftingPlanSource;
import appeng.crafting.tree.CraftingPlanTree;
import appeng.util.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.text.TextFormatting;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import javax.annotation.Nullable;
import java.awt.image.BufferedImage;
import java.nio.IntBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Locale;


/**
 * The crafting plan drawn as a tree on a canvas that can be dragged and zoomed. Rows alternate: a thing the
 * plan needs, then how it is obtained, then the things that costs, and so on.
 */
public class GuiCraftingPlanTree extends Gui {

    private static final int CELL = 16;
    private static final int ARROW = 6;
    private static final int X_SPACING = 24;
    private static final int Y_SPACING = 26;

    private static final int ICON_STORAGE = 18;
    private static final int ICON_STORAGE_EMPTY = 25;
    private static final int ICON_CRAFT = 19;
    private static final int ICON_EMITTER = 1;
    private static final int ICON_MISSING = 26;
    private static final int ICON_ARROW_OPEN = 237;
    private static final int ICON_ARROW_CLOSED = 238;

    private static final int LINE_COLOR = 0xFF555555;
    private static final int TEXT_COLOR = 0xFFFFFF;
    private static final int OUTLINE_COLOR = 0xFF8B8B8B;
    private static final int OUTLINE_MISSING_COLOR = 0xFFB03030;
    private static final int OUTLINE_SEARCH_COLOR = 0xFFE0C000;
    private static final int OUTLINE_FOCUS_COLOR = 0xFF30D030;

    private static final float MIN_ZOOM = 0.25f;
    private static final float MAX_ZOOM = 2.0f;

    private final AEBaseGui parent;

    private int x, y, width, height;

    private CraftingPlanTree tree;
    private final List<Cell> cells = new ArrayList<>();
    private Cell root;
    private int treeWidth, treeHeight;

    private boolean missingOnly;
    private float scrollX, scrollY;
    private float zoom = 1.0f;
    private boolean dragging;
    private int dragX, dragY;

    private Cell hovered;
    private String search = "";
    private final List<Cell> matches = new ArrayList<>();
    private int matchIndex = -1;

    public GuiCraftingPlanTree(final AEBaseGui parent) {
        this.parent = parent;
    }

    public void setBounds(final int x, final int y, final int width, final int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void setTree(final CraftingPlanTree tree) {
        this.tree = tree;
        this.rebuild();
        this.centreOnRoot();
    }

    /**
     * The root sits over the middle of its own subtree, not at zero, so the view is put on it rather than on
     * the corner of the canvas.
     */
    private void centreOnRoot() {
        if (this.root == null) {
            return;
        }
        this.scrollX = this.root.x - this.width / this.zoom / 2f + CELL / 2f;
        this.scrollY = -8;
        this.clampScroll();
    }

    public boolean hasTree() {
        return this.root != null;
    }

    public boolean hasMissing() {
        return this.tree != null && this.tree.getRoot().hasMissing();
    }

    public boolean isMissingOnly() {
        return this.missingOnly;
    }

    public void setMissingOnly(final boolean missingOnly) {
        if (this.missingOnly != missingOnly) {
            this.missingOnly = missingOnly;
            this.rebuild();
            this.centreOnRoot();
        }
    }

    // ------------------------------------------------------------------ building

    private void rebuild() {
        this.cells.clear();
        this.matches.clear();
        this.matchIndex = -1;
        this.root = null;

        if (this.tree == null) {
            return;
        }

        final CraftingPlanNode rootNode = this.tree.getRoot();
        if (this.missingOnly && !rootNode.hasMissing()) {
            return;
        }

        this.root = new Cell(rootNode, null, null);
        this.cells.add(this.root);

        final Deque<Cell> pending = new ArrayDeque<>();
        pending.push(this.root);

        while (!pending.isEmpty()) {
            final Cell cell = pending.pop();

            if (cell.node != null) {
                for (final CraftingPlanSource source : cell.node.getSources()) {
                    if (this.missingOnly && !keepsMissing(source)) {
                        continue;
                    }
                    final Cell child = new Cell(null, source, cell);
                    cell.children.add(child);
                    this.cells.add(child);
                    pending.push(child);
                }
            } else {
                for (final CraftingPlanNode input : cell.source.getInputs()) {
                    if (this.missingOnly && !input.hasMissing()) {
                        continue;
                    }
                    final Cell child = new Cell(input, null, cell);
                    cell.children.add(child);
                    this.cells.add(child);
                    pending.push(child);
                }
            }
        }

        this.layout();
        this.updateSearch(this.search);
    }

    private static boolean keepsMissing(final CraftingPlanSource source) {
        if (source.getKind() == CraftingPlanSource.Kind.MISSING) {
            return true;
        }
        for (final CraftingPlanNode input : source.getInputs()) {
            if (input.hasMissing()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Settled for every cell, collapsed branches included: a branch folded away is never walked by the
     * layout again, so its descendants would otherwise keep the flag they had when it was open.
     */
    private void updateVisibility() {
        this.root.visible = true;

        final Deque<Cell> stack = new ArrayDeque<>();
        stack.push(this.root);

        while (!stack.isEmpty()) {
            final Cell cell = stack.pop();
            final boolean childrenVisible = cell.visible && !cell.collapsed;
            for (final Cell child : cell.children) {
                child.visible = childrenVisible;
                stack.push(child);
            }
        }
    }

    /**
     * A leaf takes the next free column and a parent centres over its children. Done with an explicit stack:
     * a plan deep enough to be worth drawing is deep enough to overflow a recursive walk.
     */
    private void layout() {
        if (this.root == null) {
            return;
        }

        this.updateVisibility();

        int cursor = 0;
        this.treeWidth = 0;
        this.treeHeight = 0;

        final Deque<LayoutFrame> stack = new ArrayDeque<>();
        stack.push(new LayoutFrame(this.root, 0));

        while (!stack.isEmpty()) {
            final LayoutFrame frame = stack.peek();
            final Cell cell = frame.cell;
            final List<Cell> children = cell.collapsed ? Collections.<Cell>emptyList() : cell.children;

            if (frame.next < children.size()) {
                final Cell child = children.get(frame.next++);
                stack.push(new LayoutFrame(child, frame.depth + 1));
                continue;
            }

            stack.pop();

            cell.y = frame.depth * Y_SPACING;
            if (children.isEmpty()) {
                cell.x = cursor;
                cursor += X_SPACING;
            } else {
                cell.x = (children.get(0).x + children.get(children.size() - 1).x) / 2;
            }

            this.treeWidth = Math.max(this.treeWidth, cell.x + CELL);
            this.treeHeight = Math.max(this.treeHeight, cell.y + CELL);
        }
    }

    // ------------------------------------------------------------------ drawing

    public void draw(final int mouseX, final int mouseY) {
        if (this.root == null) {
            return;
        }

        this.hovered = this.hit(mouseX, mouseY);
        final Cell hovered = this.hovered;

        this.beginClip();
        GlStateManager.pushMatrix();
        GlStateManager.translate(this.x - this.scrollX * this.zoom, this.y - this.scrollY * this.zoom, 0);
        GlStateManager.scale(this.zoom, this.zoom, 1.0f);

        final float viewLeft = this.scrollX - CELL;
        final float viewTop = this.scrollY - Y_SPACING;
        final float viewRight = this.scrollX + this.width / this.zoom + CELL;
        final float viewBottom = this.scrollY + this.height / this.zoom + Y_SPACING;

        for (final Cell cell : this.cells) {
            if (!cell.visible || !inView(cell, viewLeft, viewTop, viewRight, viewBottom)) {
                continue;
            }
            if (cell.parent != null) {
                drawLine(cell.parent.x + CELL / 2, cell.parent.y + CELL, cell.x + CELL / 2, cell.y);
            }
        }

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        final FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        for (final Cell cell : this.cells) {
            if (!cell.visible || !inView(cell, viewLeft, viewTop, viewRight, viewBottom)) {
                continue;
            }
            this.drawCell(font, cell, cell == hovered);
        }

        GlStateManager.popMatrix();
        endClip();
    }

    /**
     * Draws the whole tree, ignoring the canvas and its scrolling, for the image export.
     */
    private void drawWhole(final int padding, final float scale) {
        final FontRenderer font = Minecraft.getMinecraft().fontRenderer;

        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, 1.0f);
        GlStateManager.translate(padding, padding, 0);

        for (final Cell cell : this.cells) {
            if (cell.visible && cell.parent != null) {
                drawLine(cell.parent.x + CELL / 2, cell.parent.y + CELL, cell.x + CELL / 2, cell.y);
            }
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        for (final Cell cell : this.cells) {
            if (cell.visible) {
                this.drawCell(font, cell, false);
            }
        }

        GlStateManager.popMatrix();
    }

    public int getTreeWidth() {
        return this.treeWidth;
    }

    public int getTreeHeight() {
        return this.treeHeight;
    }

    /**
     * Renders the tree into an off-screen buffer at its full size, so the picture holds the whole plan
     * rather than the part that happened to be on screen.
     */
    @Nullable
    public BufferedImage createImage(final int padding, final float scale) {
        if (this.root == null || !OpenGlHelper.isFramebufferEnabled()) {
            return null;
        }

        final Minecraft mc = Minecraft.getMinecraft();
        final int maxSize = Math.max(1024, GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE) / 2);
        final int width = Math.min(maxSize, Math.round((this.treeWidth + 2 * padding) * scale));
        final int height = Math.min(maxSize, Math.round((this.treeHeight + 2 * padding) * scale));

        final Framebuffer buffer = new Framebuffer(width, height, true);
        try {
            buffer.setFramebufferColor(0.23F, 0.23F, 0.23F, 1.0F);
            buffer.framebufferClear();
            buffer.bindFramebuffer(true);

            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.pushMatrix();
            GlStateManager.loadIdentity();
            GlStateManager.ortho(0, width / scale, height / scale, 0, 1000, 3000);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.pushMatrix();
            GlStateManager.loadIdentity();
            GlStateManager.translate(0.0F, 0.0F, -2000.0F);
            GlStateManager.disableDepth();

            this.drawWhole(padding, 1.0f);

            GlStateManager.enableDepth();
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.popMatrix();

            final IntBuffer pixels = BufferUtils.createIntBuffer(width * height);
            GlStateManager.bindTexture(buffer.framebufferTexture);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixels);

            final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    // The buffer starts at the bottom row, an image at the top.
                    image.setRGB(x, height - 1 - y, pixels.get(y * width + x));
                }
            }
            return image;
        } finally {
            buffer.deleteFramebuffer();
            mc.getFramebuffer().bindFramebuffer(true);
            GlStateManager.viewport(0, 0, mc.displayWidth, mc.displayHeight);
        }
    }

    /**
     * The canvas is a window onto a tree far larger than it, so what falls outside has to be cut off rather
     * than drawn over the frame. GlStateManager has no scissor of its own, so this is raw GL by necessity.
     */
    private void beginClip() {
        final Minecraft mc = Minecraft.getMinecraft();
        final int scale = new ScaledResolution(mc).getScaleFactor();
        final int left = this.parent.getGuiLeft() + this.x;
        final int bottom = this.parent.getGuiTop() + this.y + this.height;

        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(left * scale, mc.displayHeight - bottom * scale, this.width * scale, this.height * scale);
    }

    private static void endClip() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private void drawCell(final FontRenderer font, final Cell cell, final boolean hovered) {
        int outline = OUTLINE_COLOR;
        if (cell.searchMatch) {
            outline = this.matches.get(this.matchIndex) == cell ? OUTLINE_FOCUS_COLOR : OUTLINE_SEARCH_COLOR;
        } else if (cell.isMissing()) {
            outline = OUTLINE_MISSING_COLOR;
        } else if (hovered) {
            outline = 0xFFFFFFFF;
        }

        drawRect(cell.x - 1, cell.y - 1, cell.x + CELL + 1, cell.y + CELL + 1, outline);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        if (cell.node != null) {
            this.parent.drawItem(cell.x, cell.y,
                    GenericStack.wrapInItemStack(new GenericStack(cell.node.getWhat(), 1)));
            this.drawAmount(font, cell, format(cell.node.getWhat(), cell.node.getAmount()));
        } else {
            this.drawIcon(cell.x, cell.y, iconFor(cell.source));
            final CraftingPlanSource source = cell.source;
            final long shown = source.getKind() == CraftingPlanSource.Kind.CRAFT
                    ? source.getCrafts()
                    : source.getAmount();
            this.drawAmount(font, cell, format(source.getWhat(), shown));
        }

        if (!cell.children.isEmpty()) {
            this.drawIcon(cell.x + CELL - ARROW, cell.y + CELL + 1,
                    cell.collapsed ? ICON_ARROW_CLOSED : ICON_ARROW_OPEN, ARROW / 16.0f);
        }
    }

    private void drawAmount(final FontRenderer font, final Cell cell, final String text) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(cell.x + CELL - font.getStringWidth(text) * 0.5f, cell.y + CELL - 4f, 0);
        GlStateManager.scale(0.5f, 0.5f, 1.0f);
        font.drawStringWithShadow(text, 0, 0, TEXT_COLOR);
        GlStateManager.popMatrix();
    }

    private void drawIcon(final int cellX, final int cellY, final int iconIndex) {
        this.drawIcon(cellX, cellY, iconIndex, 1.0f);
    }

    private void drawIcon(final int cellX, final int cellY, final int iconIndex, final float scale) {
        this.parent.bindTexture("guis/states.png");
        final int v = iconIndex / 16;
        final int u = iconIndex - v * 16;

        GlStateManager.pushMatrix();
        GlStateManager.translate(cellX, cellY, 0);
        GlStateManager.scale(scale, scale, 1.0f);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        this.parent.drawTexturedModalRect(0, 0, u * 16, v * 16, 16, 16);
        GlStateManager.popMatrix();
    }

    private static boolean inView(final Cell cell, final float left, final float top, final float right,
            final float bottom) {
        return cell.x + CELL >= left && cell.x <= right && cell.y + CELL >= top && cell.y <= bottom;
    }

    /**
     * Down out of the parent, across, then down into the child - the two ends belong to different cells, so
     * each leg has to take its x and its y from the same one.
     */
    private static void drawLine(final int parentX, final int parentBottom, final int childX, final int childTop) {
        final int midY = (parentBottom + childTop) / 2;
        drawRect(parentX, parentBottom, parentX + 1, midY + 1, LINE_COLOR);
        drawRect(Math.min(parentX, childX), midY, Math.max(parentX, childX) + 1, midY + 1, LINE_COLOR);
        drawRect(childX, midY, childX + 1, childTop, LINE_COLOR);
    }

    private static int iconFor(final CraftingPlanSource source) {
        switch (source.getKind()) {
            case STORAGE:
                return source.getAmount() > 0 ? ICON_STORAGE : ICON_STORAGE_EMPTY;
            case CRAFT:
                return ICON_CRAFT;
            case EMITTER:
                return ICON_EMITTER;
            default:
                return ICON_MISSING;
        }
    }

    private static String format(final AEKey what, final long amount) {
        return what.formatAmount(amount, AmountFormat.PREVIEW_LARGE);
    }

    // ------------------------------------------------------------------ interaction

    @Nullable
    public String getTooltip(final int mouseX, final int mouseY) {
        final Cell cell = this.hovered;
        if (cell == null) {
            return null;
        }
        if (cell.tooltip != null) {
            return cell.tooltip;
        }

        final StringBuilder text = new StringBuilder();
        if (cell.node != null) {
            text.append(Platform.getItemDisplayName(cell.node.getWhat()));
            text.append('\n');
            text.append(TextFormatting.GRAY);
            text.append(cell.node.getWhat().formatAmount(cell.node.getAmount(), AmountFormat.FULL));
            return cell.tooltip = text.toString();
        }

        final CraftingPlanSource source = cell.source;
        text.append(labelFor(source));
        text.append('\n');
        text.append(TextFormatting.GRAY);
        text.append(Platform.getItemDisplayName(source.getWhat()));
        text.append('\n');
        text.append(TextFormatting.GRAY);
        text.append(source.getWhat().formatAmount(source.getAmount(), AmountFormat.FULL));
        if (source.getKind() == CraftingPlanSource.Kind.CRAFT) {
            text.append('\n');
            text.append(TextFormatting.GRAY);
            text.append(GuiText.Crafts.getLocal());
            text.append(": ");
            text.append(source.getCrafts());
        }
        return cell.tooltip = text.toString();
    }

    private static String labelFor(final CraftingPlanSource source) {
        switch (source.getKind()) {
            case STORAGE:
                return GuiText.FromStorage.getLocal();
            case CRAFT:
                return GuiText.Crafting.getLocal();
            case EMITTER:
                return GuiText.LevelEmitter.getLocal();
            default:
                return GuiText.Missing.getLocal();
        }
    }

    /**
     * @return the key under the cursor, so the screen can hand it to HEI.
     */
    @Nullable
    public AEKey getKeyUnderMouse(final int mouseX, final int mouseY) {
        final Cell cell = this.hit(mouseX, mouseY);
        if (cell == null) {
            return null;
        }
        return cell.node != null ? cell.node.getWhat() : cell.source.getWhat();
    }

    public boolean mouseClicked(final int mouseX, final int mouseY) {
        if (!this.contains(mouseX, mouseY)) {
            return false;
        }

        final Cell arrow = this.hitArrow(mouseX, mouseY);
        if (arrow != null) {
            arrow.collapsed = !arrow.collapsed;
            this.layout();
            return true;
        }

        this.dragging = true;
        this.dragX = mouseX;
        this.dragY = mouseY;
        return true;
    }

    /**
     * Driven from the screen's own draw, once a frame. Minecraft dispatches GUI mouse input on game ticks,
     * about twenty times a second, so a canvas dragged from {@code mouseClickMove} stutters however high the
     * frame rate is.
     */
    public void mouseMoved(final int mouseX, final int mouseY) {
        if (!this.dragging) {
            return;
        }

        if (!Mouse.isButtonDown(0)) {
            this.dragging = false;
            return;
        }

        this.scrollX -= (mouseX - this.dragX) / this.zoom;
        this.scrollY -= (mouseY - this.dragY) / this.zoom;
        this.dragX = mouseX;
        this.dragY = mouseY;
        this.clampScroll();
    }

    public void mouseReleased() {
        this.dragging = false;
    }

    public boolean mouseWheel(final int mouseX, final int mouseY, final int wheel) {
        if (wheel == 0 || !this.contains(mouseX, mouseY)) {
            return false;
        }

        final float before = this.zoom;
        this.zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, this.zoom * (wheel > 0 ? 1.25f : 0.8f)));

        // Keep whatever is under the cursor under the cursor.
        final int localX = mouseX - this.parent.getGuiLeft() - this.x;
        final int localY = mouseY - this.parent.getGuiTop() - this.y;
        final float pointX = localX / before + this.scrollX;
        final float pointY = localY / before + this.scrollY;
        this.scrollX = pointX - localX / this.zoom;
        this.scrollY = pointY - localY / this.zoom;
        this.clampScroll();
        return true;
    }

    private void clampScroll() {
        final float viewW = this.width / this.zoom;
        final float viewH = this.height / this.zoom;
        this.scrollX = Math.max(-viewW + CELL, Math.min(this.treeWidth - CELL, this.scrollX));
        this.scrollY = Math.max(-viewH + CELL, Math.min(this.treeHeight - CELL, this.scrollY));
    }

    public boolean contains(final int mouseX, final int mouseY) {
        final int localX = mouseX - this.parent.getGuiLeft();
        final int localY = mouseY - this.parent.getGuiTop();
        return localX >= this.x && localX < this.x + this.width
                && localY >= this.y && localY < this.y + this.height;
    }

    /**
     * The little arrow under a cell, which is what folds a branch away - clicking the cell itself is for
     * dragging the canvas, so looking at a node never rearranges the tree.
     */
    @Nullable
    private Cell hitArrow(final int mouseX, final int mouseY) {
        if (this.root == null || !this.contains(mouseX, mouseY)) {
            return null;
        }

        final float treeX = (mouseX - this.parent.getGuiLeft() - this.x) / this.zoom + this.scrollX;
        final float treeY = (mouseY - this.parent.getGuiTop() - this.y) / this.zoom + this.scrollY;

        for (final Cell cell : this.cells) {
            if (cell.children.isEmpty() || !cell.visible) {
                continue;
            }
            final int left = cell.x + CELL - 2 * ARROW;
            final int top = cell.y + CELL;
            if (treeX >= left && treeX < left + 2 * ARROW && treeY >= top && treeY < top + ARROW + 2) {
                return cell;
            }
        }
        return null;
    }

    @Nullable
    private Cell hit(final int mouseX, final int mouseY) {
        if (this.root == null || !this.contains(mouseX, mouseY)) {
            return null;
        }

        final float treeX = (mouseX - this.parent.getGuiLeft() - this.x) / this.zoom + this.scrollX;
        final float treeY = (mouseY - this.parent.getGuiTop() - this.y) / this.zoom + this.scrollY;

        for (final Cell cell : this.cells) {
            if (!cell.visible) {
                continue;
            }
            if (treeX >= cell.x && treeX < cell.x + CELL && treeY >= cell.y && treeY < cell.y + CELL) {
                return cell;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ search

    public void updateSearch(final String text) {
        this.search = text == null ? "" : text.toLowerCase(Locale.ROOT);
        for (final Cell cell : this.matches) {
            cell.searchMatch = false;
        }
        this.matches.clear();
        this.matchIndex = -1;

        if (this.search.isEmpty()) {
            return;
        }

        for (final Cell cell : this.cells) {
            final AEKey what = cell.node != null ? cell.node.getWhat() : cell.source.getWhat();
            cell.searchMatch = Platform.getItemDisplayName(what).toLowerCase(Locale.ROOT).contains(this.search);
            if (cell.searchMatch) {
                this.matches.add(cell);
            }
        }

        this.goToMatch(true);
    }

    public void goToMatch(final boolean forward) {
        if (this.matches.isEmpty()) {
            return;
        }

        this.matchIndex += forward ? 1 : -1;
        if (this.matchIndex >= this.matches.size()) {
            this.matchIndex = 0;
        } else if (this.matchIndex < 0) {
            this.matchIndex = this.matches.size() - 1;
        }

        final Cell cell = this.matches.get(this.matchIndex);
        for (Cell up = cell.parent; up != null; up = up.parent) {
            up.collapsed = false;
        }
        this.layout();

        this.scrollX = cell.x - this.width / this.zoom / 2 + CELL / 2f;
        this.scrollY = cell.y - this.height / this.zoom / 2 + CELL / 2f;
        this.clampScroll();
    }

    public int getMatchCount() {
        return this.matches.size();
    }

    // ------------------------------------------------------------------ cells

    private static final class Cell {
        private final CraftingPlanNode node;
        private final CraftingPlanSource source;
        private final Cell parent;
        private final List<Cell> children = new ArrayList<>();
        private int x, y;
        private boolean collapsed;
        private boolean visible = true;
        private boolean searchMatch;
        private String tooltip;

        private Cell(final CraftingPlanNode node, final CraftingPlanSource source, final Cell parent) {
            this.node = node;
            this.source = source;
            this.parent = parent;
        }

        private boolean isMissing() {
            if (this.source != null) {
                return this.source.getKind() == CraftingPlanSource.Kind.MISSING;
            }
            return this.node.hasMissing();
        }
    }

    private static final class LayoutFrame {
        private final Cell cell;
        private final int depth;
        private int next;

        private LayoutFrame(final Cell cell, final int depth) {
            this.cell = cell;
            this.depth = depth;
        }
    }
}
