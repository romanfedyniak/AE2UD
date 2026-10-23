/*
 * The MIT License (MIT)
 *
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

package appeng.api.migration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

/**
 * The stacks UEL saved, and the items it wrapped a non-item stack in, read into {@link GenericStack}.
 * <p>
 * UEL kept a stack in a cell, a filter or a crafting CPU as its own "AE stack": the key's fields plus {@code Cnt},
 * {@code Req} and {@code Craft}. Items and fluids are read here; an addon with a key type of its own adds a
 * {@link LegacyStackReader} for its entries, and names the item it wrapped its stacks in with
 * {@link #addWrapper}.
 */
public final class LegacyStacks {

    private static final List<LegacyStackReader> readers = new ArrayList<>();
    private static final Map<String, LegacyStackReader> wrappers = new HashMap<>();

    private LegacyStacks() {
    }

    /**
     * Asked before AE2UD's own item and fluid readers, in the order added.
     */
    public static void addReader(final LegacyStackReader reader) {
        readers.add(reader);
    }

    /**
     * An item UEL or an addon used to carry a fluid, gas or other non-item stack in an item slot. Every stack of it
     * becomes AE2UD's wrapped stack, and an AE stack of it in a filter or a cell becomes the key it wrapped. The
     * reader gets the item stack's NBT.
     */
    public static void addWrapper(final String itemId, final LegacyStackReader reader) {
        final String id = new ResourceLocation(itemId).toString();
        wrappers.put(id, reader);
        LegacyRewrites.addStackRewriter(id, stack -> rewriteWrapper(id, reader, stack));
    }

    /**
     * A storage cell item that kept its contents UEL's way, as numbered entries in its own tag. They become the
     * entry list AE2UD reads a cell from, and move into the world's cell storage when the cell is next used.
     * Each entry is read with {@link #read}; one that cannot be is logged and left out.
     */
    public static void addCell(final String itemId) {
        LegacyRewrites.addStackRewriter(itemId, LegacyStacks::rewriteCell);
    }

    /**
     * Reads an AE stack. Its amount is {@code Cnt}, or what a reader found where there is none.
     *
     * @return null when nothing can read it, or the item or fluid in it no longer exists.
     */
    @Nullable
    public static GenericStack read(final NBTTagCompound stack) {
        if (stack.isEmpty()) {
            return null;
        }
        for (final LegacyStackReader reader : readers) {
            final GenericStack read = reader.read(stack);
            if (read != null) {
                return read;
            }
        }

        final LegacyStackReader wrapper = wrappers.get(stack.getString("id"));
        if (wrapper != null) {
            final GenericStack wrapped = wrapper.read(stack);
            if (wrapped == null || !stack.hasKey("Cnt")) {
                return wrapped;
            }
            return new GenericStack(wrapped.what(), stack.getLong("Cnt"));
        }

        if (stack.hasKey("FluidName", Constants.NBT.TAG_STRING)) {
            final AEFluidKey fluid = fluid(stack.getString("FluidName"), stack, "Tag");
            return fluid == null ? null : new GenericStack(fluid, stack.getLong("Cnt"));
        }
        if (stack.hasKey("id", Constants.NBT.TAG_STRING)) {
            final NBTTagCompound copy = stack.copy();
            copy.setByte("Count", (byte) 1);
            copy.removeTag("stackSize");
            // Read as a stack, so that the item's own rename and rewrite apply to it.
            final AEItemKey item = AEItemKey.of(new ItemStack(copy));
            return item == null ? null : new GenericStack(item, stack.getLong("Cnt"));
        }
        return null;
    }

    /**
     * A fluid as UEL's {@code FluidStack} tags have it.
     */
    @Nullable
    public static AEFluidKey fluid(final String name, final NBTTagCompound holder, final String tagKey) {
        final Fluid fluid = FluidRegistry.getFluid(name);
        if (fluid == null) {
            return null;
        }
        return AEFluidKey.of(new FluidStack(fluid, 1,
                holder.hasKey(tagKey, Constants.NBT.TAG_COMPOUND) ? holder.getCompoundTag(tagKey) : null));
    }

    /**
     * Rewrites a UEL filter or tank inventory, {@code {#0: AE stack, ...}}, into AE2UD's, in place. An entry no
     * reader knows is logged with {@code where} and left out.
     */
    public static void rewriteInventory(final NBTTagCompound inventory, final String where) {
        for (final String key : new ArrayList<>(inventory.getKeySet())) {
            if (!key.startsWith("#") || !inventory.hasKey(key, Constants.NBT.TAG_COMPOUND)) {
                continue;
            }
            final NBTTagCompound entry = inventory.getCompoundTag(key);
            if (entry.isEmpty() || entry.hasKey(AEKey.TYPE_FIELD)) {
                continue;
            }
            final GenericStack read = read(entry);
            if (read == null) {
                LegacyRewrites.log().lost(where + ", slot " + key + ": " + entry);
                inventory.removeTag(key);
                continue;
            }
            final NBTTagCompound rewritten = new NBTTagCompound();
            GenericStack.writeTag(rewritten, read);
            inventory.setTag(key, rewritten);
        }
    }

    /**
     * The NBT of AE2UD's wrapped stack holding {@code stack}, or of air when it is null.
     */
    public static NBTTagCompound wrapped(@Nullable final GenericStack stack) {
        final NBTTagCompound out = new NBTTagCompound();
        if (stack == null) {
            out.setString("id", "minecraft:air");
            out.setByte("Count", (byte) 0);
            return out;
        }
        return GenericStack.wrapInItemStack(stack).writeToNBT(out);
    }

    /**
     * {@code it} entries {@code #i}, each with its amount in {@code @i}; entries past {@code it} are what an
     * emptied cell left behind.
     */
    private static void rewriteCell(final NBTTagCompound stack) {
        final NBTTagCompound tag = stack.getCompoundTag("tag");
        if (!tag.hasKey("#0") && !tag.hasKey("it") || tag.hasKey("Items") || tag.hasKey("cellIdMost")) {
            return;
        }

        final int types = tag.getInteger("it");
        final NBTTagList entries = new NBTTagList();
        long total = 0;
        for (int i = 0; i < types; i++) {
            final long amount = tag.getLong("@" + i);
            final NBTTagCompound saved = tag.getCompoundTag("#" + i);
            if (amount <= 0 || saved.isEmpty()) {
                continue;
            }
            final GenericStack read = read(saved);
            if (read == null) {
                LegacyRewrites.log().lost("cell " + stack.getString("id") + ", " + amount + " of " + saved);
                continue;
            }
            final NBTTagCompound entry = new NBTTagCompound();
            GenericStack.writeTag(entry, new GenericStack(read.what(), amount));
            entries.appendTag(entry);
            total += amount;
        }

        for (final String key : new ArrayList<>(tag.getKeySet())) {
            if (key.startsWith("#") || key.startsWith("@") || key.equals("PF") || key.equals("PF#")
                    || key.equals("PN") || key.equals("FP")) {
                tag.removeTag(key);
            }
        }
        tag.setTag("Items", entries);
        tag.setInteger("it", entries.tagCount());
        tag.setLong("ic", total);
        stack.setTag("tag", tag);
    }

    private static void rewriteWrapper(final String id, final LegacyStackReader reader, final NBTTagCompound stack) {
        final GenericStack wrapped = reader.read(stack);
        if (wrapped == null) {
            LegacyRewrites.log().lost("wrapped stack " + stack);
        }
        final NBTTagCompound replacement = wrapped(wrapped);
        for (final String key : new ArrayList<>(stack.getKeySet())) {
            stack.removeTag(key);
        }
        stack.merge(replacement);
    }
}
