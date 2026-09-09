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


import appeng.api.client.NetworkVisualiserStyles;
import appeng.api.client.VisualiserStyle;
import appeng.api.util.AEColor;
import appeng.core.AEClientConfig;
import appeng.items.tools.ToolNetworkVisualiser;
import appeng.me.visualiser.VisualiserGraph;
import appeng.me.visualiser.VisualiserMode;
import appeng.util.Platform;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;


/**
 * Draws the network a held visualiser is pointed at.
 * <p>
 * Everything except the numbers is world-space geometry - a link is a thin box, not a line of a fixed number
 * of screen pixels - so it thins away with distance the way a cable does, and, being the same shape from
 * every angle, it can sit in a buffer on the card and be redrawn without the processor touching it. The
 * buffer is rebuilt when the network changes, when the mode changes, or when the player has walked far
 * enough that the render distance would cut somewhere else.
 */
@SideOnly(Side.CLIENT)
public final class NetworkVisualiserRenderer {

    /** Vertices are floats relative to an origin, so the buffer is rebuilt after the player leaves its area. */
    private static final double REBUILD_AFTER_MOVING = 16;

    private static final int STRIDE = 16;

    private static VertexBuffer vbo;
    private static int quads;

    private static double originX;
    private static double originY;
    private static double originZ;

    private static int builtVersion = -1;
    private static VisualiserMode builtMode;
    private static double builtX;
    private static double builtY;
    private static double builtZ;

    private static final List<Label> LABELS = new ArrayList<>();

    private NetworkVisualiserRenderer() {
    }

    public static void render(final RenderWorldLastEvent event) {
        final Minecraft mc = Minecraft.getMinecraft();
        final EntityPlayerSP player = mc.player;

        if (player == null) {
            return;
        }

        final ItemStack held = player.inventory.getCurrentItem();
        if (held.isEmpty() || !(held.getItem() instanceof ToolNetworkVisualiser)
                || ToolNetworkVisualiser.getBound(held, mc.world) == null) {
            discard();
            return;
        }

        final VisualiserGraph graph = NetworkVisualiserData.graph();
        if (graph == null) {
            discard();
            return;
        }

        final float partial = event.getPartialTicks();
        final double viewX = player.lastTickPosX + (player.posX - player.lastTickPosX) * partial;
        final double viewY = player.lastTickPosY + (player.posY - player.lastTickPosY) * partial;
        final double viewZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partial;

        final VisualiserMode mode = ToolNetworkVisualiser.getMode(held);

        if (needsRebuild(mode, viewX, viewY, viewZ)) {
            rebuild(graph, mode, viewX, viewY, viewZ);
        }

        draw(viewX, viewY, viewZ);

        if (mode.drawsNumbers()) {
            VisualiserLabels.draw(LABELS, viewX, viewY, viewZ);
        }
    }

    private static boolean needsRebuild(final VisualiserMode mode, final double x, final double y, final double z) {
        if (vbo == null || builtMode != mode || builtVersion != NetworkVisualiserData.version()) {
            return true;
        }

        final double dx = x - builtX;
        final double dy = y - builtY;
        final double dz = z - builtZ;

        return dx * dx + dy * dy + dz * dz > REBUILD_AFTER_MOVING * REBUILD_AFTER_MOVING;
    }

    private static void discard() {
        if (vbo != null) {
            vbo.deleteGlBuffers();
            vbo = null;
        }

        quads = 0;
        builtVersion = -1;
        builtMode = null;
        LABELS.clear();
    }

    private static void draw(final double viewX, final double viewY, final double viewZ) {
        if (quads == 0) {
            return;
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(originX - viewX, originY - viewY, originZ - viewZ);

        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();

        // Cleared rather than switched off: with no depth test at all a link drawn later paints straight over
        // the node it runs behind, and nothing looks solid. Clearing puts the whole picture in front of the
        // world while letting it occlude itself. Vanilla clears depth again before drawing the hand.
        GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);

        vbo.bindBuffer();
        GlStateManager.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        GlStateManager.glVertexPointer(3, GL11.GL_FLOAT, STRIDE, 0);
        GlStateManager.glEnableClientState(GL11.GL_COLOR_ARRAY);
        GlStateManager.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, STRIDE, 12);

        vbo.drawArrays(GL11.GL_QUADS);

        GlStateManager.glDisableClientState(GL11.GL_COLOR_ARRAY);
        GlStateManager.glDisableClientState(GL11.GL_VERTEX_ARRAY);
        vbo.unbindBuffer();

        GlStateManager.disableBlend();
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1, 1, 1, 1);

        GlStateManager.popMatrix();
    }

    private static void rebuild(final VisualiserGraph graph, final VisualiserMode mode, final double viewX,
            final double viewY, final double viewZ) {
        final AEClientConfig config = AEClientConfig.instance();
        final double range = config.getVisualiserRenderDistance();
        final double rangeSq = range * range;
        final double labelRange = config.getVisualiserLabelDistance();
        final double labelRangeSq = labelRange * labelRange;

        originX = MathHelper.floor(viewX);
        originY = MathHelper.floor(viewY);
        originZ = MathHelper.floor(viewZ);

        LABELS.clear();

        final BufferBuilder buffer = new BufferBuilder(0x40000);
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);

        final int nodes = graph.nodeCount();
        final double[] x = new double[nodes];
        final double[] y = new double[nodes];
        final double[] z = new double[nodes];
        final boolean[] near = new boolean[nodes];

        for (int i = 0; i < nodes; i++) {
            final BlockPos pos = BlockPos.fromLong(graph.positions[i]);
            x[i] = pos.getX() + 0.5 - originX;
            y[i] = pos.getY() + 0.5 - originY;
            z[i] = pos.getZ() + 0.5 - originZ;

            final double dx = pos.getX() + 0.5 - viewX;
            final double dy = pos.getY() + 0.5 - viewY;
            final double dz = pos.getZ() + 0.5 - viewZ;
            near[i] = dx * dx + dy * dy + dz * dz < rangeSq;
        }

        if (mode.drawsNodes()) {
            final float size = (float) config.getVisualiserNodeSize();

            for (int i = 0; i < nodes; i++) {
                if (!near[i]) {
                    continue;
                }

                final VisualiserStyle style = NetworkVisualiserStyles.get(graph.styleOfNode(i));

                final int colour;
                if ((graph.nodeFlags[i] & VisualiserGraph.FLAG_MISSING_CHANNEL) != 0) {
                    colour = config.getVisualiserNodeMissingColor();
                } else if (style != null) {
                    colour = style.getColor();
                } else {
                    colour = config.getVisualiserNodeColor();
                }

                cube(buffer, x[i], y[i], z[i], style == null ? size : size * style.getThickness(), colour);
            }
        }

        if (mode.drawsLinks()) {
            links(buffer, graph, config, mode, x, y, z, near, viewX, viewY, viewZ, labelRangeSq);
        }

        buffer.finishDrawing();
        quads = buffer.getVertexCount() / 4;
        buffer.reset();

        if (vbo == null) {
            vbo = new VertexBuffer(DefaultVertexFormats.POSITION_COLOR);
        }

        vbo.bufferData(buffer.getByteBuffer());

        builtVersion = NetworkVisualiserData.version();
        builtMode = mode;
        builtX = viewX;
        builtY = viewY;
        builtZ = viewZ;
    }

    private static void links(final BufferBuilder buffer, final VisualiserGraph graph, final AEClientConfig config,
            final VisualiserMode mode, final double[] x, final double[] y, final double[] z, final boolean[] near,
            final double viewX, final double viewY, final double viewZ, final double labelRangeSq) {
        final float base = (float) config.getVisualiserLineWidth();
        final int count = graph.linkCount();

        // Several links between one pair of blocks is what a cable with more than one P2P tunnel looks like.
        // Without this they would land on exactly the same box and fight over which colour wins.
        final Long2IntOpenHashMap sharing = new Long2IntOpenHashMap();
        final int[] rank = new int[count];
        for (int i = 0; i < count; i++) {
            final long key = pairKey(graph.linkA[i], graph.linkB[i]);
            rank[i] = sharing.addTo(key, 1);
        }

        for (int i = 0; i < count; i++) {
            final int a = graph.linkA[i];
            final int b = graph.linkB[i];

            if (!near[a] && (b == VisualiserGraph.NO_NODE || !near[b])) {
                continue;
            }

            final VisualiserStyle style = NetworkVisualiserStyles.get(graph.styleOfLink(i));
            final float half = halfWidth(graph.linkCapacity[i], base) * (style == null ? 1 : style.getThickness());
            final int shared = sharing.get(pairKey(a, b));
            final double offset = (rank[i] - (shared - 1) / 2.0) * half * 2.5;

            final double x1 = x[a];
            final double y1 = y[a];
            final double z1 = z[a];

            final double x2;
            final double y2;
            final double z2;

            if (b == VisualiserGraph.NO_NODE) {
                // The far end is in another world. All that can honestly be drawn is that something leaves here.
                x2 = x1;
                y2 = y1 + 0.75;
                z2 = z1;
            } else {
                x2 = x[b];
                y2 = y[b];
                z2 = z[b];
            }

            final short frequency = graph.linkFrequency[i];
            if (frequency != 0) {
                p2pLink(buffer, x1, y1, z1, x2, y2, z2, half, offset, frequency);
            } else {
                // A link an addon claimed keeps its own colour rather than being coloured by how full it is,
                // and one claimed by an addon this client has never heard of is drawn plainly.
                final int colour;
                if (style != null) {
                    colour = style.getColor();
                } else if (b == VisualiserGraph.NO_NODE || graph.styleOfLink(i) != null) {
                    colour = config.getVisualiserLinkOtherColor();
                } else {
                    colour = loadColour(graph.linkUsed[i], graph.linkCapacity[i], config);
                }

                box(buffer, x1, y1, z1, x2, y2, z2, half, offset, colour);
            }

            if (mode.drawsNumbers() && b != VisualiserGraph.NO_NODE && graph.linkUsed[i] > 0) {
                final double mx = (x1 + x2) / 2 + originX;
                final double my = (y1 + y2) / 2 + originY;
                final double mz = (z1 + z2) / 2 + originZ;

                final double dx = mx - viewX;
                final double dy = my - viewY;
                final double dz = mz - viewZ;

                if (dx * dx + dy * dy + dz * dz < labelRangeSq) {
                    LABELS.add(new Label(mx, my, mz, graph.linkUsed[i]));
                }
            }
        }
    }

    /**
     * A P2P link wears the four colours of its frequency, the same ones the tunnel itself shows, repeated
     * along its length so that the frequency can be read from anywhere near it rather than only in the middle.
     */
    private static void p2pLink(final BufferBuilder buffer, final double x1, final double y1, final double z1,
            final double x2, final double y2, final double z2, final float half, final double offset,
            final short frequency) {
        final AEColor[] colours = Platform.p2p().toColors(frequency);
        final double length = Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1) + (z2 - z1) * (z2 - z1));
        final int segments = MathHelper.clamp((int) Math.round(length * 2), 4, 128);

        for (int i = 0; i < segments; i++) {
            final double from = (double) i / segments;
            final double to = (double) (i + 1) / segments;

            box(buffer, x1 + (x2 - x1) * from, y1 + (y2 - y1) * from, z1 + (z2 - z1) * from,
                    x1 + (x2 - x1) * to, y1 + (y2 - y1) * to, z1 + (z2 - z1) * to, half, offset,
                    0xFF000000 | colours[i % colours.length].mediumVariant);
        }
    }

    private static long pairKey(final int a, final int b) {
        return b == VisualiserGraph.NO_NODE ? (long) a << 32 : ((long) Math.min(a, b) << 32) | Math.max(a, b);
    }

    /** Wider for a cable that carries more, so a tier registered by an addon is drawn without asking us. */
    private static float halfWidth(final int capacity, final float base) {
        final int carried = capacity < 0 ? 64 : Math.max(1, capacity);
        final double scale = 1 + 0.35 * (Math.log(carried / 8.0) / Math.log(2));

        return (float) (base * MathHelper.clamp(scale, 0.5, 3.0));
    }

    private static int loadColour(final int used, final int capacity, final AEClientConfig config) {
        final int idle = config.getVisualiserLinkIdleColor();
        final int full = config.getVisualiserLinkFullColor();

        if (capacity <= 0) {
            return idle;
        }

        final float load = MathHelper.clamp((float) used / capacity, 0, 1);

        return mix(idle, full, load);
    }

    /**
     * Mixed by hue rather than by channel. Halfway from green to red down the channels is olive, which reads
     * as a third colour rather than as half a load; around the wheel it is yellow, which is what anyone
     * expects a half-full thing to be.
     */
    private static int mix(final int from, final int to, final float t) {
        final float[] a = toHsv(from);
        final float[] b = toHsv(to);

        float hue = b[0] - a[0];
        if (hue > 0.5f) {
            hue -= 1;
        } else if (hue < -0.5f) {
            hue += 1;
        }

        final int alphaFrom = (from >>> 24) & 0xFF;
        final int alphaTo = (to >>> 24) & 0xFF;

        return toRgb(a[0] + hue * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t,
                (int) (alphaFrom + (alphaTo - alphaFrom) * t));
    }

    private static float[] toHsv(final int colour) {
        final float red = ((colour >> 16) & 0xFF) / 255.0f;
        final float green = ((colour >> 8) & 0xFF) / 255.0f;
        final float blue = (colour & 0xFF) / 255.0f;

        final float max = Math.max(red, Math.max(green, blue));
        final float span = max - Math.min(red, Math.min(green, blue));

        float hue = 0;
        if (span > 0) {
            if (max == red) {
                hue = (green - blue) / span / 6;
            } else if (max == green) {
                hue = (2 + (blue - red) / span) / 6;
            } else {
                hue = (4 + (red - green) / span) / 6;
            }
        }

        return new float[] {hue - (float) Math.floor(hue), max == 0 ? 0 : span / max, max};
    }

    private static int toRgb(final float hue, final float saturation, final float value, final int alpha) {
        final float wrapped = (hue - (float) Math.floor(hue)) * 6;
        final int sector = (int) wrapped;
        final float fraction = wrapped - sector;

        final float p = value * (1 - saturation);
        final float q = value * (1 - saturation * fraction);
        final float u = value * (1 - saturation * (1 - fraction));

        switch (sector) {
            case 0:
                return pack(alpha, value, u, p);
            case 1:
                return pack(alpha, q, value, p);
            case 2:
                return pack(alpha, p, value, u);
            case 3:
                return pack(alpha, p, q, value);
            case 4:
                return pack(alpha, u, p, value);
            default:
                return pack(alpha, value, p, q);
        }
    }

    private static int pack(final int alpha, final float red, final float green, final float blue) {
        return alpha << 24 | (int) (red * 255) << 16 | (int) (green * 255) << 8 | (int) (blue * 255);
    }

    private static void cube(final BufferBuilder buffer, final double x, final double y, final double z,
            final float size, final int colour) {
        box(buffer, x, y - size, z, x, y + size, z, size, 0, colour);
    }

    /**
     * A box from one point to the other, of the given half width, pushed sideways by an offset. Closed at both
     * ends, because the same method draws the cube at a node, and a cube with no top is a hole.
     */
    private static void box(final BufferBuilder buffer, final double x1, final double y1, final double z1,
            final double x2, final double y2, final double z2, final float half, final double offset,
            final int colour) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;

        final double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-6) {
            return;
        }

        dx /= length;
        dy /= length;
        dz /= length;

        // Any vector not parallel to the link does; the up axis is only unusable for a link that is vertical.
        final double upX = Math.abs(dy) > 0.9 ? 1 : 0;
        final double upY = Math.abs(dy) > 0.9 ? 0 : 1;

        double sx = dy * 0 - dz * upY;
        double sy = dz * upX - dx * 0;
        double sz = dx * upY - dy * upX;
        final double sLen = Math.sqrt(sx * sx + sy * sy + sz * sz);
        sx /= sLen;
        sy /= sLen;
        sz /= sLen;

        final double ux = dy * sz - dz * sy;
        final double uy = dz * sx - dx * sz;
        final double uz = dx * sy - dy * sx;

        final double ox = sx * offset;
        final double oy = sy * offset;
        final double oz = sz * offset;

        final double ax = x1 + ox;
        final double ay = y1 + oy;
        final double az = z1 + oz;
        final double bx = x2 + ox;
        final double by = y2 + oy;
        final double bz = z2 + oz;

        // Every face carries the same colour: the picture is a diagram, and a face shaded darker for being
        // turned away reads as a different state rather than as the same link seen from another side.
        side(buffer, ax, ay, az, bx, by, bz, sx * half, sy * half, sz * half, ux * half, uy * half, uz * half,
                colour);
        side(buffer, ax, ay, az, bx, by, bz, -sx * half, -sy * half, -sz * half, ux * half, uy * half, uz * half,
                colour);
        side(buffer, ax, ay, az, bx, by, bz, ux * half, uy * half, uz * half, sx * half, sy * half, sz * half,
                colour);
        side(buffer, ax, ay, az, bx, by, bz, -ux * half, -uy * half, -uz * half, sx * half, sy * half, sz * half,
                colour);

        cap(buffer, ax, ay, az, sx * half, sy * half, sz * half, ux * half, uy * half, uz * half, colour);
        cap(buffer, bx, by, bz, sx * half, sy * half, sz * half, ux * half, uy * half, uz * half, colour);
    }

    /** One end of a box, in the plane across it. */
    private static void cap(final BufferBuilder buffer, final double x, final double y, final double z,
            final double sx, final double sy, final double sz, final double ux, final double uy, final double uz,
            final int colour) {
        final int alpha = (colour >>> 24) & 0xFF;
        final int red = (colour >> 16) & 0xFF;
        final int green = (colour >> 8) & 0xFF;
        final int blue = colour & 0xFF;

        buffer.pos(x + sx + ux, y + sy + uy, z + sz + uz).color(red, green, blue, alpha).endVertex();
        buffer.pos(x + sx - ux, y + sy - uy, z + sz - uz).color(red, green, blue, alpha).endVertex();
        buffer.pos(x - sx - ux, y - sy - uy, z - sz - uz).color(red, green, blue, alpha).endVertex();
        buffer.pos(x - sx + ux, y - sy + uy, z - sz + uz).color(red, green, blue, alpha).endVertex();
    }

    private static void side(final BufferBuilder buffer, final double ax, final double ay, final double az,
            final double bx, final double by, final double bz, final double nx, final double ny, final double nz,
            final double tx, final double ty, final double tz, final int colour) {
        final int alpha = (colour >>> 24) & 0xFF;
        final int red = (colour >> 16) & 0xFF;
        final int green = (colour >> 8) & 0xFF;
        final int blue = colour & 0xFF;

        buffer.pos(ax + nx - tx, ay + ny - ty, az + nz - tz).color(red, green, blue, alpha).endVertex();
        buffer.pos(ax + nx + tx, ay + ny + ty, az + nz + tz).color(red, green, blue, alpha).endVertex();
        buffer.pos(bx + nx + tx, by + ny + ty, bz + nz + tz).color(red, green, blue, alpha).endVertex();
        buffer.pos(bx + nx - tx, by + ny - ty, bz + nz - tz).color(red, green, blue, alpha).endVertex();
    }

    /** One number waiting to be drawn, in world coordinates because it faces the camera. */
    static final class Label {

        final double x;
        final double y;
        final double z;
        final int value;

        Label(final double x, final double y, final double z, final int value) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.value = value;
        }
    }
}
