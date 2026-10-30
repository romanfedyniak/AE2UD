/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 AlgorithmX2
 * Copyright (c) 2026 AE2UD contributors
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
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

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
import net.minecraftforge.oredict.OreDictionary;

import appeng.api.storage.AEKeyFilter;

/**
 * Identifies an item: its {@link Item}, metadata, NBT and what its capabilities hold beyond a new stack's, without a
 * count.
 */
public final class AEItemKey extends AEKey {

    private final Item item;
    private final int damage;
    @Nullable
    private final NBTTagCompound tag;
    /**
     * The stack's ForgeCaps, or null when they are what a new stack of this item has. Thaumic Augmentation keeps a
     * gauntlet's augments there, not in the tag, so a key of the tag alone lost them on the way into a network.
     */
    @Nullable
    private final NBTTagCompound caps;
    private final int hash;
    /** The tag {@link #getReadOnlyStack()} hands out: a copy, so a mod that writes to it changes no key. */
    @Nullable
    private NBTTagCompound readOnlyTag;
    /** The item's stack limit for this key, worked out once: asking builds a stack, and the CPU asks per slot per tick. */
    private int maxStackSize = -1;

    /** ForgeCaps of a new stack per item and damage; an empty compound for none. */
    private static final Map<Item, Map<Integer, NBTTagCompound>> DEFAULT_CAPS = new ConcurrentHashMap<>();
    /** ItemStack.capabilities, a Forge field with the same name in development and in production. */
    private static final MethodHandle CAPABILITIES;

    static {
        try {
            Field field = ItemStack.class.getDeclaredField("capabilities");
            field.setAccessible(true);
            CAPABILITIES = MethodHandles.lookup().unreflectGetter(field)
                    .asType(MethodType.methodType(Object.class, ItemStack.class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private AEItemKey(Item item, int damage, @Nullable NBTTagCompound tag, @Nullable NBTTagCompound caps) {
        this.item = item;
        this.damage = damage;
        // Same normalisation as AEFluidKey: an empty compound is no compound. Item identity is safe here -
        // Item is a true Forge registry singleton, unlike Fluid - so only the tag needed fixing.
        this.tag = tag == null || tag.isEmpty() ? null : tag;
        this.caps = caps == null || caps.isEmpty() ? null : caps;
        this.hash = Objects.hash(item, damage, this.tag, this.caps);
    }

    /**
     * @return the stack's ForgeCaps, or null when it has none or they match a new stack's, so the many items other
     *         mods attach default capabilities to keep one key, and keys saved before caps were kept still match.
     */
    @Nullable
    private static NBTTagCompound capsOf(ItemStack stack) {
        NBTTagCompound caps = forgeCaps(stack);
        if (caps == null || caps.isEmpty() || caps.equals(defaultCaps(stack.getItem(), stack.getItemDamage()))) {
            return null;
        }
        return caps;
    }

    private static NBTTagCompound defaultCaps(Item item, int damage) {
        return DEFAULT_CAPS.computeIfAbsent(item, i -> new ConcurrentHashMap<>()).computeIfAbsent(damage, d -> {
            NBTTagCompound caps = forgeCaps(new ItemStack(item, 1, damage));
            return caps == null ? new NBTTagCompound() : caps;
        });
    }

    /** The stack writes its tag by reference and adds ForgeCaps only when it has capabilities. */
    @Nullable
    private static NBTTagCompound forgeCaps(ItemStack stack) {
        try {
            if ((Object) CAPABILITIES.invokeExact(stack) == null) {
                return null;
            }
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
        NBTTagCompound written = stack.serializeNBT();
        return written.hasKey("ForgeCaps", 10) ? written.getCompoundTag("ForgeCaps") : null;
    }

    private ItemStack newStack(int count, @Nullable NBTTagCompound tag) {
        ItemStack stack = new ItemStack(this.item, count, this.damage, this.caps == null ? null : this.caps.copy());
        if (tag != null) {
            stack.setTagCompound(tag);
        }
        return stack;
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
        return new AEItemKey(stack.getItem(), stack.getItemDamage(), tag == null ? null : tag.copy(), capsOf(stack));
    }

    public static AEItemKey of(Item item) {
        return new AEItemKey(item, 0, null, null);
    }

    public static AEItemKey of(Item item, int damage) {
        return new AEItemKey(item, damage, null, null);
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
        return this.tag == null && this.caps == null ? this : new AEItemKey(this.item, this.damage, null, null);
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
        return this.newStack(count, this.tag == null ? null : this.tag.copy());
    }

    /**
     * A stack of size one that callers must not mutate. Avoids the copy {@link #toStack()} makes
     * when the stack is only used for rendering or comparison. Its tag is the key's own copied once, not the
     * key's: some items write to their tag while drawn (Thaumic Augmentation's gauntlet sets its default colour),
     * and a key changed that way names an item the network does not hold, so it cannot be taken out.
     */
    public ItemStack getReadOnlyStack() {
        if (this.tag != null && this.readOnlyTag == null) {
            this.readOnlyTag = this.tag.copy();
        }
        return this.newStack(1, this.readOnlyTag);
    }

    @Override
    public List<String> getOreDictNames() {
        final int[] ids = OreDictionary.getOreIDs(this.getReadOnlyStack());
        if (ids.length == 0) {
            return Collections.emptyList();
        }

        final List<String> names = new ArrayList<>(ids.length);
        for (final int id : ids) {
            names.add(OreDictionary.getOreName(id));
        }
        return names;
    }

    public boolean matches(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() != this.item || stack.getItemDamage() != this.damage) {
            return false;
        }
        return Objects.equals(stack.getTagCompound(), this.tag) && Objects.equals(capsOf(stack), this.caps);
    }

    public int getMaxStackSize() {
        if (this.maxStackSize < 0) {
            this.maxStackSize = this.item.getItemStackLimit(this.getReadOnlyStack());
        }
        return this.maxStackSize;
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
        if (this.caps != null) {
            out.setTag("caps", this.caps.copy());
        }
    }

    @Nullable
    public static AEItemKey fromTag(NBTTagCompound tag) {
        Item item = Item.REGISTRY.getObject(new ResourceLocation(tag.getString("id")));
        if (item == null) {
            return null;
        }
        NBTTagCompound extra = tag.hasKey("tag") ? tag.getCompoundTag("tag") : null;
        NBTTagCompound caps = tag.hasKey("caps") ? tag.getCompoundTag("caps") : null;
        return new AEItemKey(item, tag.getShort("dmg") & 0xFFFF, extra, caps);
    }

    @Override
    public void writeToPacket(ByteBuf data) throws IOException {
        PacketBuffer buffer = new PacketBuffer(data);
        buffer.writeVarInt(Item.getIdFromItem(this.item));
        buffer.writeVarInt(this.damage);
        buffer.writeCompoundTag(this.tag);
        buffer.writeCompoundTag(this.caps);
    }

    public static AEItemKey fromPacket(ByteBuf data) throws IOException {
        PacketBuffer buffer = new PacketBuffer(data);
        Item item = Item.getItemById(buffer.readVarInt());
        int damage = buffer.readVarInt();
        NBTTagCompound tag;
        NBTTagCompound caps;
        try {
            tag = buffer.readCompoundTag();
            caps = buffer.readCompoundTag();
        } catch (DecoderException e) {
            throw new IOException("Failed to read the NBT of an item key.", e);
        }
        if (item == null) {
            throw new IOException("Received an item key with an unknown item id.");
        }
        return new AEItemKey(item, damage, tag, caps);
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
        return this.item == other.item && this.damage == other.damage && Objects.equals(this.tag, other.tag)
                && Objects.equals(this.caps, other.caps);
    }

    @Override
    public int hashCode() {
        return this.hash;
    }

    @Override
    public String toString() {
        return this.item.getRegistryName() + "@" + this.damage + (this.tag != null ? this.tag : "")
                + (this.caps != null ? " caps " + this.caps : "");
    }
}
