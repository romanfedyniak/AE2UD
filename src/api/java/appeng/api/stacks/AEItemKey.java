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
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import appeng.api.storage.AEKeyFilter;

/**
 * Identifies an item: its {@link Item}, metadata and NBT, without a count.
 */
public final class AEItemKey extends AEKey {

    private final Item item;
    private final int damage;
    @Nullable
    private final NBTTagCompound tag;
    private final int hash;

    private AEItemKey(Item item, int damage, @Nullable NBTTagCompound tag) {
        this.item = item;
        this.damage = damage;
        this.tag = tag;
        this.hash = Objects.hash(item, damage, tag);
    }

    /**
     * @return null for an empty stack.
     */
    @Nullable
    public static AEItemKey of(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        NBTTagCompound tag = stack.getTagCompound();
        return new AEItemKey(stack.getItem(), stack.getItemDamage(), tag == null ? null : tag.copy());
    }

    public static AEItemKey of(Item item) {
        return new AEItemKey(item, 0, null);
    }

    public static AEItemKey of(Item item, int damage) {
        return new AEItemKey(item, damage, null);
    }

    public static boolean matches(@Nullable AEKey what, ItemStack stack) {
        return what instanceof AEItemKey itemKey && itemKey.matches(stack);
    }

    public static boolean is(@Nullable AEKey what) {
        return what instanceof AEItemKey;
    }

    public static AEKeyFilter filter() {
        return AEItemKey::is;
    }

    @Override
    public AEKeyType getType() {
        return AEKeyTypes.items();
    }

    @Override
    public ResourceLocation getId() {
        return this.item.getRegistryName();
    }

    @Override
    public Object getPrimaryKey() {
        return this.item;
    }

    @Override
    public AEItemKey dropSecondary() {
        return this.tag == null ? this : new AEItemKey(this.item, this.damage, null);
    }

    public Item getItem() {
        return this.item;
    }

    public int getDamage() {
        return this.damage;
    }

    /**
     * @return the key's NBT, or null. Never mutate the returned tag.
     */
    @Nullable
    public NBTTagCompound getTag() {
        return this.tag;
    }

    /**
     * @return a stack of size one. Safe to mutate.
     */
    public ItemStack toStack() {
        return this.toStack(1);
    }

    public ItemStack toStack(int count) {
        if (count <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(this.item, count, this.damage);
        if (this.tag != null) {
            stack.setTagCompound(this.tag.copy());
        }
        return stack;
    }

    /**
     * A stack of size one that callers must not mutate. Avoids the copy {@link #toStack()} makes
     * when the stack is only used for rendering or comparison.
     */
    public ItemStack getReadOnlyStack() {
        ItemStack stack = new ItemStack(this.item, 1, this.damage);
        if (this.tag != null) {
            stack.setTagCompound(this.tag);
        }
        return stack;
    }

    public boolean matches(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() != this.item || stack.getItemDamage() != this.damage) {
            return false;
        }
        return Objects.equals(stack.getTagCompound(), this.tag);
    }

    public int getMaxStackSize() {
        return this.item.getItemStackLimit(this.getReadOnlyStack());
    }

    public boolean isDamaged() {
        return this.item.isDamageable() && this.damage > 0;
    }

    @Override
    public int getFuzzySearchValue() {
        return this.damage;
    }

    @Override
    public int getFuzzySearchMaxValue() {
        return this.item.getMaxDamage();
    }

    @Override
    public void toTag(NBTTagCompound out) {
        out.setString("id", String.valueOf(this.item.getRegistryName()));
        out.setShort("dmg", (short) this.damage);
        if (this.tag != null) {
            out.setTag("tag", this.tag.copy());
        }
    }

    @Nullable
    public static AEItemKey fromTag(NBTTagCompound tag) {
        Item item = Item.REGISTRY.getObject(new ResourceLocation(tag.getString("id")));
        if (item == null) {
            return null;
        }
        NBTTagCompound extra = tag.hasKey("tag") ? tag.getCompoundTag("tag") : null;
        return new AEItemKey(item, tag.getShort("dmg") & 0xFFFF, extra);
    }

    @Override
    public void writeToPacket(ByteBuf data) throws IOException {
        PacketBuffer buffer = new PacketBuffer(data);
        buffer.writeVarInt(Item.getIdFromItem(this.item));
        buffer.writeVarInt(this.damage);
        buffer.writeCompoundTag(this.tag);
    }

    public static AEItemKey fromPacket(ByteBuf data) throws IOException {
        PacketBuffer buffer = new PacketBuffer(data);
        Item item = Item.getItemById(buffer.readVarInt());
        int damage = buffer.readVarInt();
        NBTTagCompound tag;
        try {
            tag = buffer.readCompoundTag();
        } catch (DecoderException e) {
            throw new IOException("Failed to read the NBT of an item key.", e);
        }
        if (item == null) {
            throw new IOException("Received an item key with an unknown item id.");
        }
        return new AEItemKey(item, damage, tag);
    }

    @Override
    protected ITextComponent computeDisplayName() {
        ItemStack stack = this.getReadOnlyStack();
        try {
            return new TextComponentString(stack.getDisplayName());
        } catch (Throwable e) {
            // A broken item must not take down the whole terminal.
            return new TextComponentString(String.valueOf(this.item.getRegistryName()));
        }
    }

    @Override
    public ItemStack wrapForDisplayOrFilter() {
        return this.getReadOnlyStack();
    }

    @Override
    public void addDrops(long amount, List<ItemStack> drops, @Nonnull World world, @Nonnull BlockPos pos) {
        int maxSize = this.getMaxStackSize();
        long remaining = amount;
        while (remaining > 0) {
            int size = (int) Math.min(remaining, maxSize);
            drops.add(this.toStack(size));
            remaining -= size;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AEItemKey other)) {
            return false;
        }
        return this.item == other.item && this.damage == other.damage && Objects.equals(this.tag, other.tag);
    }

    @Override
    public int hashCode() {
        return this.hash;
    }

    @Override
    public String toString() {
        return this.item.getRegistryName() + "@" + this.damage + (this.tag != null ? this.tag : "");
    }
}
