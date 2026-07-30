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
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;

import appeng.api.config.FuzzyMode;

/**
 * Identifies <em>what</em> is stored in an ME network, without saying how much.
 * <p>
 * This replaces the old {@code IAEStack} hierarchy. The critical difference: an {@code IAEItemStack}
 * carried both the identity and the amount and was mutable, while an {@link AEKey} is identity only
 * and immutable. Amounts live next to the key, in {@link GenericStack} or {@link KeyCounter}.
 * <p>
 * Implementations must be immutable and must implement {@code equals}/{@code hashCode}.
 */
public abstract class AEKey {

    /**
     * NBT field the key type id is written to by {@link #toTagGeneric(NBTTagCompound)}.
     */
    public static final String TYPE_FIELD = "#t";

    @Nullable
    private ITextComponent cachedDisplayName;

    /**
     * @return the type this key belongs to. Never null.
     */
    public abstract AEKeyType getType();

    /**
     * @return the identifier of the thing being stored, such as {@code minecraft:stone}.
     */
    public abstract ResourceLocation getId();

    /**
     * The object identity used to group keys that only differ in secondary data (NBT, damage).
     * Returns the {@code Item} for item keys and the {@code Fluid} for fluid keys.
     */
    public abstract Object getPrimaryKey();

    /**
     * @return this key with all secondary data (NBT and similar) removed.
     */
    public abstract AEKey dropSecondary();

    /**
     * Writes this key <em>without</em> its type. Use {@link #toTagGeneric(NBTTagCompound)} when the
     * reader does not know the type up front.
     */
    public abstract void toTag(NBTTagCompound tag);

    public abstract void writeToPacket(ByteBuf data) throws IOException;

    /**
     * Writes this key together with its type id, so it can be read back by
     * {@link #fromTagGeneric(NBTTagCompound)} without external context.
     */
    public final void toTagGeneric(NBTTagCompound tag) {
        this.toTag(tag);
        tag.setString(TYPE_FIELD, this.getType().getId().toString());
    }

    /**
     * @return null if the tag has no valid type, or the type could not read the key (for instance
     *         because the mod that provided it was removed).
     */
    @Nullable
    public static AEKey fromTagGeneric(NBTTagCompound tag) {
        if (!tag.hasKey(TYPE_FIELD)) {
            return null;
        }

        AEKeyType type = AEKeyTypes.get(new ResourceLocation(tag.getString(TYPE_FIELD)));
        if (type == null) {
            return null;
        }

        return type.loadKeyFromTag(tag);
    }

    public static void writeKey(ByteBuf buf, AEKey key) throws IOException {
        buf.writeInt(key.getType().getRawId());
        key.writeToPacket(buf);
    }

    public static AEKey readKey(ByteBuf buf) throws IOException {
        AEKeyType type = AEKeyTypes.fromRawId(buf.readInt());
        if (type == null) {
            throw new IOException("Received a key of an unknown type.");
        }
        return type.readFromPacket(buf);
    }

    public static void writeOptionalKey(ByteBuf buf, @Nullable AEKey key) throws IOException {
        buf.writeBoolean(key != null);
        if (key != null) {
            writeKey(buf, key);
        }
    }

    @Nullable
    public static AEKey readOptionalKey(ByteBuf buf) throws IOException {
        return buf.readBoolean() ? readKey(buf) : null;
    }

    public final int getAmountPerUnit() {
        return this.getType().getAmountPerUnit();
    }

    public final int getAmountPerOperation() {
        return this.getType().getAmountPerOperation();
    }

    public final int getAmountPerByte() {
        return this.getType().getAmountPerByte();
    }

    public final String getUnitSymbol() {
        return this.getType().getUnitSymbol();
    }

    public final boolean supportsFuzzyRangeSearch() {
        return this.getType().supportsFuzzyRangeSearch();
    }

    public String formatAmount(long amount, AmountFormat format) {
        return this.getType().formatAmount(amount, format);
    }

    /**
     * Value used for fuzzy range comparisons, such as an item's remaining durability percentage.
     * Only meaningful when {@link #supportsFuzzyRangeSearch()} is true.
     */
    public int getFuzzySearchValue() {
        return 0;
    }

    public int getFuzzySearchMaxValue() {
        return 0;
    }

    public final boolean fuzzyEquals(@Nullable AEKey other, FuzzyMode fuzzyMode) {
        if (other == null || this.getType() != other.getType()) {
            return false;
        }

        if (fuzzyMode == FuzzyMode.IGNORE_ALL) {
            return this.getPrimaryKey() == other.getPrimaryKey();
        }

        if (!this.supportsFuzzyRangeSearch()) {
            return this.equals(other);
        }

        if (this.getPrimaryKey() != other.getPrimaryKey()) {
            return false;
        }

        int maxValue = this.getFuzzySearchMaxValue();
        if (maxValue <= 0) {
            return this.equals(other);
        }

        // Compare both values against the mode's breakpoint: they match when they fall on the same
        // side of it. IGNORE_ALL is handled above, so only the percentage modes remain.
        int breakpoint = fuzzyMode.calculateBreakPoint(maxValue);
        boolean mine = this.getFuzzySearchValue() > breakpoint;
        boolean theirs = other.getFuzzySearchValue() > breakpoint;
        return mine == theirs;
    }

    public final boolean matches(@Nullable GenericStack stack) {
        return stack != null && stack.what().equals(this);
    }

    public String getModId() {
        ResourceLocation id = this.getId();
        return id == null ? "minecraft" : id.getNamespace();
    }

    /**
     * Localized name shown in terminals and tooltips. Cached, since it is queried per rendered row.
     */
    public final ITextComponent getDisplayName() {
        ITextComponent name = this.cachedDisplayName;
        if (name == null) {
            name = this.computeDisplayName();
            this.cachedDisplayName = name;
        }
        return name;
    }

    protected abstract ITextComponent computeDisplayName();

    /**
     * An {@link ItemStack} that stands in for this key wherever only item stacks can be shown:
     * GUI slots, JEI, ghost items in filters.
     * <p>
     * For item keys this is the stack itself. For every other type it is a wrapper item, which is
     * the generalisation of what {@code FluidDummyItem} does for fluids today. This is what allows
     * a single filter GUI to accept any registered type.
     */
    public ItemStack wrapForDisplayOrFilter() {
        // Amount zero, deliberately: this stack stands for an identity, never for a quantity. The real
        // amount is drawn beside the slot and belongs to whatever row or filter the caller is showing.
        // A placeholder amount of 1 leaked into tooltips as a wrong quantity - a terminal row for water
        // read "Water: 0B", because one millibucket rounds to zero buckets.
        return GenericStack.wrapInItemStack(this, 0);
    }

    /**
     * Drops the given amount of this key into the world, used when a machine holding content is
     * broken.
     */
    public abstract void addDrops(long amount, List<ItemStack> drops, @Nonnull World world, @Nonnull BlockPos pos);
}
