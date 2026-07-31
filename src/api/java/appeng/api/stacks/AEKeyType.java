/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 AlgorithmX2
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package appeng.api.stacks;

import java.io.IOException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.registries.IForgeRegistryEntry;

import appeng.api.storage.AEKeyFilter;

/**
 * Describes a kind of content an ME network is able to store, transport and craft: items, fluids,
 * and anything an addon chooses to register.
 * <p>
 * This replaces the old {@code IStorageChannel}. Unlike that interface, key types are entries in a
 * real Forge registry ({@link AEKeyTypes#REGISTRY_NAME}), which means Forge assigns and synchronizes
 * their numeric ids for us.
 * <p>
 * Addons register their own type from {@code RegistryEvent.Register<AEKeyType>}.
 */
public abstract class AEKeyType extends IForgeRegistryEntry.Impl<AEKeyType> {

    private final Class<? extends AEKey> keyClass;
    private final ITextComponent description;

    protected AEKeyType(ResourceLocation id, Class<? extends AEKey> keyClass, ITextComponent description) {
        this.keyClass = keyClass;
        this.description = description;
        this.setRegistryName(id);
    }

    /**
     * The built-in key type for {@link net.minecraft.item.Item}.
     */
    public static AEKeyType items() {
        return AEKeyTypes.items();
    }

    /**
     * The built-in key type for {@link net.minecraftforge.fluids.Fluid}.
     */
    public static AEKeyType fluids() {
        return AEKeyTypes.fluids();
    }

    /**
     * @return the type with the given network id, or null if there is none.
     */
    @Nullable
    public static AEKeyType fromRawId(int id) {
        return AEKeyTypes.fromRawId(id);
    }

    public final ResourceLocation getId() {
        return this.getRegistryName();
    }

    public final Class<? extends AEKey> getKeyClass() {
        return this.keyClass;
    }

    /**
     * The numeric id assigned by Forge. Stable for the duration of a session and synchronized to
     * clients on login, which makes it safe to use in packets.
     */
    public final int getRawId() {
        return AEKeyTypes.getRawId(this);
    }

    public ITextComponent getDescription() {
        return this.description;
    }

    /**
     * Reads a key of this type from a packet. Must mirror {@link AEKey#writeToPacket(ByteBuf)}.
     */
    public abstract AEKey readFromPacket(@Nonnull ByteBuf input) throws IOException;

    /**
     * Reads a key of this type from NBT. Must mirror {@link AEKey#toTag(NBTTagCompound)}.
     *
     * @return null if the tag does not describe a valid key (for instance, a removed mod's item).
     */
    @Nullable
    public abstract AEKey loadKeyFromTag(@Nonnull NBTTagCompound tag);

    /**
     * How much of this type is moved by a single machine operation. Items move 1 at a time, fluids
     * a bucket at a time.
     */
    public int getAmountPerOperation() {
        return 1;
    }

    /**
     * How much of this type fits into one byte of a storage cell.
     */
    public int getAmountPerByte() {
        return 8;
    }

    /**
     * The size of one displayed unit. Fluids display in buckets, so this is 1000 for them.
     */
    public int getAmountPerUnit() {
        return 1;
    }

    /**
     * Symbol appended to displayed amounts, such as "B" for buckets. Empty for items.
     */
    public String getUnitSymbol() {
        return "";
    }

    /**
     * How much of this type a craft-amount screen offers to order before the player types anything.
     * One displayed unit: a single item, a single bucket.
     * <p>
     * Not present in upstream AE2, which passes {@link #getAmountPerUnit()} at the point the screen is
     * opened. Named here so a type whose display unit and typical order differ can say so - energy is
     * counted one AE at a time but nobody orders less than a few thousand.
     */
    public long getDefaultCraftAmount() {
        return this.getAmountPerUnit();
    }

    public final String formatAmount(long amount, AmountFormat format) {
        return AEKeyFormatting.format(amount, this.getAmountPerUnit(), this.getUnitSymbol(), format);
    }

    /**
     * @return the key, if it belongs to this type, otherwise null.
     */
    @Nullable
    public final AEKey tryCast(@Nullable AEKey key) {
        return this.contains(key) ? key : null;
    }

    public final boolean contains(@Nullable AEKey key) {
        return key != null && key.getType() == this;
    }

    /**
     * A filter matching every key of this type.
     */
    public final AEKeyFilter filter() {
        return this::contains;
    }

    /**
     * Whether {@link AEKey#getFuzzySearchValue()} is meaningful for this type. True for items,
     * which can be searched by damage range.
     */
    public boolean supportsFuzzyRangeSearch() {
        return false;
    }

    /**
     * Background texture of the button used to switch a terminal or filter to this type.
     * <p>
     * Not present in upstream AE2, which handles this in its GUI layer. It is part of the type
     * contract here so that a generic multi-type GUI can build its type switcher without hardcoding
     * items and fluids. See ae-gtnh {@code IAEStackType#getButtonTexture()}.
     */
    public abstract ResourceLocation getButtonTexture();

    /**
     * Icon drawn on the type switcher button.
     */
    public abstract ItemStack getButtonIcon();

    @Override
    public String toString() {
        return String.valueOf(this.getRegistryName());
    }
}
