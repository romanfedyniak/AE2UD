/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
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

package appeng.client.gui;


import appeng.core.AELog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.event.ClickEvent;
import org.apache.commons.io.FileUtils;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.IntBuffer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


/**
 * Draws part of a screen into a picture of its own size rather than the screen's, so that what is saved is
 * the whole thing and not the part that happened to be in view.
 */
public final class GuiImageExport {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss");

    private GuiImageExport() {
    }

    /**
     * @param scale how much bigger than its on-screen size to draw it, so the text stays legible
     * @param draw  drawn in the same coordinates the screen would use, with the origin at the top left
     * @return null when the graphics card offers no framebuffers to draw into
     */
    @Nullable
    public static BufferedImage render(final int width, final int height, final float scale,
            final float[] background, final Runnable draw) {
        if (!OpenGlHelper.isFramebufferEnabled() || width <= 0 || height <= 0) {
            return null;
        }

        final Minecraft mc = Minecraft.getMinecraft();
        final int limit = Math.max(1024, GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE) / 2);
        final int pixelWidth = Math.min(limit, Math.round(width * scale));
        final int pixelHeight = Math.min(limit, Math.round(height * scale));

        final Framebuffer buffer = new Framebuffer(pixelWidth, pixelHeight, true);
        try {
            buffer.setFramebufferColor(background[0], background[1], background[2], background[3]);
            buffer.framebufferClear();
            buffer.bindFramebuffer(true);

            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.pushMatrix();
            GlStateManager.loadIdentity();
            GlStateManager.ortho(0, pixelWidth / scale, pixelHeight / scale, 0, 1000, 3000);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.pushMatrix();
            GlStateManager.loadIdentity();
            GlStateManager.translate(0.0F, 0.0F, -2000.0F);
            GlStateManager.disableDepth();

            draw.run();

            GlStateManager.enableDepth();
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.popMatrix();

            return read(buffer, pixelWidth, pixelHeight);
        } finally {
            buffer.deleteFramebuffer();
            mc.getFramebuffer().bindFramebuffer(true);
            GlStateManager.viewport(0, 0, mc.displayWidth, mc.displayHeight);
        }
    }

    private static BufferedImage read(final Framebuffer buffer, final int width, final int height) {
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
    }

    /**
     * Written where Minecraft keeps its own screenshots, and announced with a link that opens the file - a
     * picture of a large plan is not something worth hunting for in a folder.
     */
    public static void save(@Nullable final BufferedImage image, final String suffix) {
        final Minecraft mc = Minecraft.getMinecraft();
        if (image == null) {
            return;
        }

        try {
            final File directory = new File(mc.gameDir, "screenshots");
            FileUtils.forceMkdir(directory);

            final String stamp = DATE_FORMAT.format(LocalDateTime.now());
            File file = new File(directory, stamp + suffix + ".png");
            for (int i = 1; file.exists() && i < 100; i++) {
                file = new File(directory, stamp + suffix + "-" + i + ".png");
            }

            ImageIO.write(image, "png", file);
            AELog.info("Saved a crafting picture to %s", file.getName());

            final ITextComponent link = new TextComponentString(file.getName());
            link.getStyle().setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, file.getAbsolutePath()))
                    .setUnderlined(Boolean.TRUE);
            mc.player.sendMessage(new TextComponentTranslation("chat.appliedenergistics2.CraftingImageSaved", link));
        } catch (final Throwable e) {
            AELog.warn(e, "Could not save the crafting picture");
            mc.player.sendMessage(
                    new TextComponentTranslation("chat.appliedenergistics2.CraftingImageFailed", String.valueOf(e)));
        }
    }
}
