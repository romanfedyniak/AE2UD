/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.client.render.visualiser;


import appeng.client.render.visualiser.NetworkVisualiserRenderer.Label;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import java.util.List;


/**
 * Draws the channel counts, all of them, in two draw calls.
 * <p>
 * The font renderer finishes a buffer once per string and binds its texture again each time, which on a
 * dense base means several hundred calls a frame for something that is only ever ten different characters.
 * Since the alphabet here is the digits, the glyphs can be laid out by hand out of the vanilla font sheet and
 * put in one buffer: the backgrounds in the first pass, every digit on screen in the second.
 */
@SideOnly(Side.CLIENT)
final class VisualiserLabels {

    private static final ResourceLocation FONT = new ResourceLocation("textures/font/ascii.png");

    /** Blocks per pixel of text, so a digit stands about a sixth of a block tall. */
    private static final float SCALE = 0.02f;

    /** The sheet is sixteen glyphs across, so one glyph is a sixteenth of it either way. */
    private static final float GLYPH = 1 / 16.0f;

    /** What the vanilla font advances by for a digit, wider than the glyph is drawn. */
    private static final int ADVANCE = 6;

    private VisualiserLabels() {
    }

    static void draw(final List<Label> labels, final double viewX, final double viewY, final double viewZ) {
        if (labels.isEmpty()) {
            return;
        }

        // The camera's own axes. The names of these five are not what a reader expects: what is called
        // rotationZ is sin(yaw) and belongs to the sideways axis, while the upright one is built from
        // rotationYZ, rotationXZ and rotationXY. Particles get away with taking them in the wrong order
        // because a particle is symmetric; text is not, and comes out mirrored and lying on its side.
        // Sideways is negated because that pair points to the camera's left, which would reverse a number.
        final double ux = -ActiveRenderInfo.getRotationX() * SCALE;
        final double uz = -ActiveRenderInfo.getRotationZ() * SCALE;
        final double vx = ActiveRenderInfo.getRotationYZ() * SCALE;
        final double vy = ActiveRenderInfo.getRotationXZ() * SCALE;
        final double vz = ActiveRenderInfo.getRotationXY() * SCALE;

        GlStateManager.pushMatrix();
        GlStateManager.translate(-viewX, -viewY, -viewZ);
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        GlStateManager.color(1, 1, 1, 1);

        final Tessellator tessellator = Tessellator.getInstance();
        final BufferBuilder buffer = tessellator.getBuffer();

        GlStateManager.disableTexture2D();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        for (final Label label : labels) {
            final int half = halfWidth(label.value);

            quad(buffer, label, ux, uz, vx, vy, vz, -half - 1, -1, half + 1, 8);
        }
        tessellator.draw();

        GlStateManager.enableTexture2D();
        Minecraft.getMinecraft().getTextureManager().bindTexture(FONT);
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        for (final Label label : labels) {
            digits(buffer, label, ux, uz, vx, vy, vz);
        }
        tessellator.draw();

        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    private static void digits(final BufferBuilder buffer, final Label label, final double ux, final double uz,
            final double vx, final double vy, final double vz) {
        final String text = Integer.toString(label.value);
        int left = -halfWidth(label.value);

        for (int i = 0; i < text.length(); i++) {
            final int glyph = text.charAt(i);
            final float u = (glyph % 16) * GLYPH;
            final float v = (glyph / 16) * GLYPH;

            vertex(buffer, label, ux, uz, vx, vy, vz, left, -1, u, v);
            vertex(buffer, label, ux, uz, vx, vy, vz, left, 7, u, v + GLYPH);
            vertex(buffer, label, ux, uz, vx, vy, vz, left + 8, 7, u + GLYPH, v + GLYPH);
            vertex(buffer, label, ux, uz, vx, vy, vz, left + 8, -1, u + GLYPH, v);

            left += ADVANCE;
        }
    }

    private static void vertex(final BufferBuilder buffer, final Label label, final double ux, final double uz,
            final double vx, final double vy, final double vz, final int tx, final int ty, final float u,
            final float v) {
        // Text runs downwards and the world does not, so the vertical offset is subtracted.
        buffer.pos(label.x + ux * tx - vx * ty, label.y - vy * ty, label.z + uz * tx - vz * ty)
                .tex(u, v)
                .color(255, 255, 255, 255)
                .endVertex();
    }

    private static void quad(final BufferBuilder buffer, final Label label, final double ux, final double uz,
            final double vx, final double vy, final double vz, final int x1, final int y1, final int x2,
            final int y2) {
        corner(buffer, label, ux, uz, vx, vy, vz, x1, y1);
        corner(buffer, label, ux, uz, vx, vy, vz, x1, y2);
        corner(buffer, label, ux, uz, vx, vy, vz, x2, y2);
        corner(buffer, label, ux, uz, vx, vy, vz, x2, y1);
    }

    private static void corner(final BufferBuilder buffer, final Label label, final double ux, final double uz,
            final double vx, final double vy, final double vz, final int tx, final int ty) {
        buffer.pos(label.x + ux * tx - vx * ty, label.y - vy * ty, label.z + uz * tx - vz * ty)
                .color(0, 0, 0, 128)
                .endVertex();
    }

    private static int halfWidth(final int value) {
        return Integer.toString(value).length() * ADVANCE / 2;
    }
}
