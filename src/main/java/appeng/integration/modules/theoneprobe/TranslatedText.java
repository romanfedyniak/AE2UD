/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.integration.modules.theoneprobe;


import appeng.api.stacks.AEKey;
import io.netty.buffer.ByteBuf;
import mcjty.theoneprobe.api.IElement;
import mcjty.theoneprobe.api.ITheOneProbe;
import mcjty.theoneprobe.apiimpl.client.ElementTextRender;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.common.network.ByteBufUtils;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Arrays;


/**
 * A probe line that the client translates and fills in itself. The One Probe asks its providers on the server
 * and sends finished text, so text translated there is in the server's language; its own {@code {*key*}}
 * markers are translated on the client but take no arguments. A line with numbers or names in it comes as this.
 */
public final class TranslatedText implements IElement {

    private static final byte NUMBER = 0;
    private static final byte LITERAL = 1;
    private static final byte TRANSLATED = 2;
    private static final byte KEY_NAME = 3;

    private static int id = -1;

    /** Null: the arguments one after another. */
    @Nullable
    private final String format;
    private final Object[] args;
    private String text;

    /**
     * @param args numbers ({@code Long}), text as it is ({@code String}), a thing's name ({@link AEKey}) or another
     *             translation key ({@link #key}).
     */
    public TranslatedText(@Nullable final String format, final Object... args) {
        this.format = format;
        this.args = args;
    }

    private TranslatedText(final ByteBuf buf) {
        this.format = buf.readBoolean() ? ByteBufUtils.readUTF8String(buf) : null;
        this.args = new Object[buf.readUnsignedByte()];
        for (int i = 0; i < this.args.length; i++) {
            switch (buf.readByte()) {
                case NUMBER:
                    this.args[i] = buf.readLong();
                    break;
                case TRANSLATED:
                    this.args[i] = new Key(ByteBufUtils.readUTF8String(buf));
                    break;
                case KEY_NAME:
                    try {
                        this.args[i] = AEKey.readOptionalKey(buf);
                    } catch (final IOException e) {
                        this.args[i] = null;
                    }
                    break;
                default:
                    this.args[i] = ByteBufUtils.readUTF8String(buf);
            }
        }
    }

    static void register(final ITheOneProbe probe) {
        id = probe.registerElementFactory(TranslatedText::new);
    }

    /** A translation key as an argument, translated on the client too. */
    public static Object key(final String key) {
        return new Key(key);
    }

    @Override
    public void toBytes(final ByteBuf buf) {
        buf.writeBoolean(this.format != null);
        if (this.format != null) {
            ByteBufUtils.writeUTF8String(buf, this.format);
        }
        buf.writeByte(this.args.length);
        for (final Object arg : this.args) {
            if (arg instanceof Number) {
                buf.writeByte(NUMBER);
                buf.writeLong(((Number) arg).longValue());
            } else if (arg instanceof Key) {
                buf.writeByte(TRANSLATED);
                ByteBufUtils.writeUTF8String(buf, ((Key) arg).key);
            } else if (arg instanceof AEKey) {
                buf.writeByte(KEY_NAME);
                try {
                    AEKey.writeOptionalKey(buf, (AEKey) arg);
                } catch (final IOException e) {
                    throw new IllegalStateException(e);
                }
            } else {
                buf.writeByte(LITERAL);
                ByteBufUtils.writeUTF8String(buf, String.valueOf(arg));
            }
        }
    }

    @Override
    public void render(final int x, final int y) {
        ElementTextRender.render(this.text(), x, y);
    }

    @Override
    public int getWidth() {
        return ElementTextRender.getWidth(this.text());
    }

    @Override
    public int getHeight() {
        return 10;
    }

    @Override
    public int getID() {
        return id;
    }

    /** Client only: the line in the player's language. */
    private String text() {
        if (this.text == null) {
            final Object[] shown = new Object[this.args.length];
            for (int i = 0; i < shown.length; i++) {
                final Object arg = this.args[i];
                if (arg instanceof Key) {
                    shown[i] = I18n.format(((Key) arg).key);
                } else if (arg instanceof AEKey) {
                    shown[i] = ((AEKey) arg).getDisplayName().getFormattedText();
                } else {
                    shown[i] = arg == null ? "?" : arg;
                }
            }

            if (this.format == null) {
                final StringBuilder joined = new StringBuilder();
                for (final Object part : shown) {
                    joined.append(part);
                }
                this.text = joined.toString();
            } else {
                this.text = I18n.format(this.format, shown);
            }
        }
        return this.text;
    }

    @Override
    public String toString() {
        return "TranslatedText{" + this.format + " " + Arrays.toString(this.args) + "}";
    }

    private static final class Key {
        private final String key;

        private Key(final String key) {
            this.key = key;
        }

        @Override
        public String toString() {
            return "{" + this.key + "}";
        }
    }
}
