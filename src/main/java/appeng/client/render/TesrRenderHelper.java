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

package appeng.client.render;


import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fluids.FluidStack;
import org.lwjgl.opengl.GL11;


/**
 * Helper methods for rendering TESRs.
 */
public class TesrRenderHelper {

    /**
     * Move the current coordinate system to the center of the given block face, assuming that the origin is currently
     * at the center of a block.
     */
    public static void moveToFace(EnumFacing face) {
        GlStateManager.translate(face.getXOffset() * 0.50, face.getYOffset() * 0.50, face.getZOffset() * 0.50);
    }

    /**
     * Rotate the current coordinate system so it is on the face of the given block side. This can be used to render on
     * the given face as if it was
     * a 2D canvas.
     */
    public static void rotateToFace(EnumFacing face, byte spin) {
        switch (face) {
            case UP:
                GlStateManager.scale(1.0f, -1.0f, 1.0f);
                GlStateManager.rotate(90.0f, 1.0f, 0.0f, 0.0f);
                GlStateManager.rotate(spin * 90.0F, 0, 0, 1);
                break;

            case DOWN:
                GlStateManager.scale(1.0f, -1.0f, 1.0f);
                GlStateManager.rotate(-90.0f, 1.0f, 0.0f, 0.0f);
                GlStateManager.rotate(spin * -90.0F, 0, 0, 1);
                break;

            case EAST:
                GlStateManager.scale(-1.0f, -1.0f, -1.0f);
                GlStateManager.rotate(-90.0f, 0.0f, 1.0f, 0.0f);
                break;

            case WEST:
                GlStateManager.scale(-1.0f, -1.0f, -1.0f);
                GlStateManager.rotate(90.0f, 0.0f, 1.0f, 0.0f);
                break;

            case NORTH:
                GlStateManager.scale(-1.0f, -1.0f, -1.0f);
                break;

            case SOUTH:
                GlStateManager.scale(-1.0f, -1.0f, -1.0f);
                GlStateManager.rotate(180.0f, 0.0f, 1.0f, 0.0f);
                break;

            default:
                break;
        }
    }

    /**
     * How deep a flat item model is left, in blocks. Its layers stand thousandths of a texel apart - the fluid
     * in a Forge bucket, the cover over it - and squashing that gap away makes them fight for the pixel.
     */
    private static final float LAYERED_DEPTH = 0.05f;

    /**
     * What to take off z to centre a flat item on the panel: the distance the GUI item renderer pushes a model
     * away, which is 100 plus the 50 it raises zLevel by. The model's own 7.5 to 8.5 and the half unit
     * {@code RenderItem.renderItem} takes off after scaling cancel to half a unit either side of that.
     * <p>
     * Centred rather than in front on purpose: {@link #rotateToFace} mirrors z on a wall but not on a floor, so
     * which way "out" runs depends on the face, and a straddling model shows its near half either way.
     */
    private static final float GUI_Z_OFFSET = 150.0f;

    /**
     * A model that is drawn as a block is turned to face the player and is sixteen units deep, so it has to be
     * squashed to nearly nothing to read as a picture. Its own faces are real geometry and do not fight.
     */
    private static final float BLOCK_DEPTH = 0.0001f;

    /**
     * Render an item in 2D.
     */
    public static void renderItem2d(ItemStack itemStack, float scale) {
        if (!itemStack.isEmpty()) {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.f, 240.0f);

            RenderItem renderItem = Minecraft.getMinecraft().getRenderItem();
            final boolean layered = !renderItem.getItemModelWithOverrides(itemStack, null, null).isGui3d();

            GlStateManager.pushMatrix();

            GlStateManager.scale(scale / 32.0f, scale / 32.0f, layered ? LAYERED_DEPTH : BLOCK_DEPTH);
            // Position the item icon at the top middle of the panel. A flat model is also carried back the
            // distance the GUI renderer pushes it, so the depth left to it is spent on the model itself.
            GlStateManager.translate(-8, -11, layered ? -GUI_Z_OFFSET : 0);

            renderItem.renderItemAndEffectIntoGUI(itemStack, 0, 0);

            GlStateManager.popMatrix();
        }
    }

    public static void renderFluid2d(FluidStack fluidStack, float scale) {
        if (fluidStack != null) {
            // This format carries no lightmap coordinate, so whatever the last draw left is what the quad
            // is multiplied by - in a TESR that is usually dark enough to render the fluid black.
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.f, 240.0f);

            GlStateManager.pushMatrix();
            int color = fluidStack.getFluid().getColor(fluidStack);
            float r = (color >> 16 & 255) / 255.0f;
            float g = (color >> 8 & 255) / 255.0f;
            float b = (color & 255) / 255.0f;
            TextureAtlasSprite sprite = Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(fluidStack.getFluid().getStill(fluidStack).toString());
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            GlStateManager.disableAlpha();
            GlStateManager.disableLighting();
            Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            Tessellator tess = Tessellator.getInstance();
            BufferBuilder buf = tess.getBuffer();

            // The same size and place the item renderer puts an item of this scale, which is where the
            // figures below come from: they were written for the 0.8 the storage monitor asks for.
            float width = scale * 0.5f;
            float height = width;
            float alpha = 1.0f;
            float z = 0.0001f;
            float x = -scale * 0.25f;
            float y = -scale * 0.3125f;

            buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
            double uMin = sprite.getMinU(), uMax = sprite.getMaxU();
            double vMin = sprite.getMinV(), vMax = sprite.getMaxV();
            buf.pos(x, y, z).tex(uMin, vMin).color(r, g, b, alpha).endVertex();
            buf.pos(x, y + height, z).tex(uMin, vMax).color(r, g, b, alpha).endVertex();
            buf.pos(x + width, y + height, z).tex(uMax, vMax).color(r, g, b, alpha).endVertex();
            buf.pos(x + width, y, z).tex(uMax, vMin).color(r, g, b, alpha).endVertex();

            tess.draw();
            GlStateManager.enableLighting();
            GlStateManager.enableAlpha();
            GlStateManager.disableBlend();
            GlStateManager.color(1F, 1F, 1F, 1F);
            GlStateManager.popMatrix();

        }
    }

    /**
     * Render a key in 2D with its amount below it - a monitor face.
     * <p>
     * The amount goes through the key's own formatter, so a partial bucket reads "500mB" instead of the
     * "0B" that dividing by 1000 here used to produce.
     *
     * @param spacing Specifies how far apart the icon and the amount are rendered.
     */
    public static void renderKey2dWithAmount(AEKey what, long amount, float scale, float spacing) {
        if (what instanceof AEItemKey itemKey) {
            // count = 1, identity only - matches the old IAEItemStack.asItemStackRepresentation()
            TesrRenderHelper.renderItem2d(itemKey.toStack(), scale);
        } else if (what instanceof AEFluidKey fluidKey) {
            // Identity only, like the item above: toStack(0) is null, and a monitor whose network has run
            // out should still show what it is watching.
            TesrRenderHelper.renderFluid2d(fluidKey.toStack(1), scale);
        } else {
            return;
        }

        renderAmount2d(what.formatAmount(amount, AmountFormat.PREVIEW_LARGE), spacing);
    }

    private static void renderAmount2d(String text, float spacing) {
        final FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        final int width = fr.getStringWidth(text);
        GlStateManager.translate(0.0f, spacing, 0);
        GlStateManager.scale(1.0f / 62.0f, 1.0f / 62.0f, 1.0f / 62.0f);
        GlStateManager.translate(-0.5f * width, 0.0f, 0.5f);
        fr.drawString(text, 0, 0, 0);
    }

}
