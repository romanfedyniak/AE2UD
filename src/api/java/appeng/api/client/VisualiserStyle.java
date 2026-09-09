/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2UD contributors
 */

package appeng.api.client;


import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * How the network visualiser draws something an addon claimed. Colour is decided here, on the client, rather
 * than sent from the server, so that a player can change it and a server-only addon breaks nothing.
 */
@SideOnly(Side.CLIENT)
public final class VisualiserStyle {

    private final int color;
    private final float thickness;

    /**
     * @param color     as AARRGGBB.
     * @param thickness a multiple of the thickness the block would be drawn at otherwise. 1 leaves it alone.
     */
    public VisualiserStyle(final int color, final float thickness) {
        this.color = color;
        this.thickness = thickness;
    }

    public VisualiserStyle(final int color) {
        this(color, 1);
    }

    public int getColor() {
        return this.color;
    }

    public float getThickness() {
        return this.thickness;
    }
}
