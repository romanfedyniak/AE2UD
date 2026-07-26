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
import java.util.Objects;

import javax.annotation.Nullable;

import com.github.bsideup.jabel.Desugar;

import io.netty.buffer.ByteBuf;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

/**
 * An {@link AEKey} paired with an amount. This is the immutable replacement for the role
 * {@code IAEItemStack} used to play.
 */
@Desugar
public record GenericStack(AEKey what, long amount) {

    /**
     * NBT field the amount is written to.
     */
    public static final String AMOUNT_FIELD = "#";

    public GenericStack {
        Objects.requireNonNull(what, "what");
    }

    @Nullable
    public static GenericStack fromItemStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        return new GenericStack(AEItemKey.of(stack), stack.getCount());
    }

    @Nullable
    public static GenericStack fromFluidStack(@Nullable FluidStack stack) {
        if (stack == null || stack.amount <= 0) {
            return null;
        }
        return new GenericStack(AEFluidKey.of(stack), stack.amount);
    }

    public static long getStackSizeOrZero(@Nullable GenericStack stack) {
        return stack == null ? 0 : stack.amount();
    }

    /**
     * @return the sum of both stacks, or null if they do not describe the same key.
     */
    @Nullable
    public static GenericStack sum(@Nullable GenericStack left, @Nullable GenericStack right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        if (!left.what().equals(right.what())) {
            return null;
        }
        return new GenericStack(left.what(), left.amount() + right.amount());
    }

    @Nullable
    public static GenericStack readTag(NBTTagCompound tag) {
        AEKey what = AEKey.fromTagGeneric(tag);
        if (what == null) {
            return null;
        }
        return new GenericStack(what, tag.getLong(AMOUNT_FIELD));
    }

    public static void writeTag(NBTTagCompound tag, @Nullable GenericStack stack) {
        if (stack == null) {
            return;
        }
        stack.what().toTagGeneric(tag);
        tag.setLong(AMOUNT_FIELD, stack.amount());
    }

    @Nullable
    public static GenericStack readBuffer(ByteBuf buf) throws IOException {
        AEKey what = AEKey.readOptionalKey(buf);
        if (what == null) {
            return null;
        }
        return new GenericStack(what, buf.readLong());
    }

    public static void writeBuffer(@Nullable GenericStack stack, ByteBuf buf) throws IOException {
        AEKey.writeOptionalKey(buf, stack == null ? null : stack.what());
        if (stack != null) {
            buf.writeLong(stack.amount());
        }
    }

    // ------------------------------------------------------------------
    // ItemStack wrapping
    //
    // GUI slots, JEI and ghost item filters can only carry ItemStacks. Non-item keys are therefore
    // wrapped into a placeholder item. The placeholder item itself lives in src/main (it needs to be
    // registered), so the mod installs its implementation here during initialisation.
    // ------------------------------------------------------------------

    /**
     * Provided by the mod during initialisation. Addons never need to implement this.
     */
    public interface Wrapper {
        ItemStack wrap(AEKey what, long amount);

        boolean isWrapped(ItemStack stack);

        @Nullable
        GenericStack unwrap(ItemStack stack);
    }

    private static Wrapper wrapper;

    public static void setWrapper(Wrapper wrapper) {
        GenericStack.wrapper = Objects.requireNonNull(wrapper, "wrapper");
    }

    private static Wrapper wrapper() {
        Wrapper w = wrapper;
        if (w == null) {
            throw new IllegalStateException("GenericStack.Wrapper has not been installed yet.");
        }
        return w;
    }

    public static ItemStack wrapInItemStack(@Nullable GenericStack stack) {
        if (stack == null) {
            return ItemStack.EMPTY;
        }
        return wrapInItemStack(stack.what(), stack.amount());
    }

    public static ItemStack wrapInItemStack(AEKey what, long amount) {
        if (what instanceof AEItemKey itemKey) {
            return itemKey.toStack((int) Math.min(amount, Integer.MAX_VALUE));
        }
        return wrapper().wrap(what, amount);
    }

    public static boolean isWrapped(ItemStack stack) {
        return !stack.isEmpty() && wrapper().isWrapped(stack);
    }

    /**
     * @return null if the stack is not a wrapped non-item key.
     */
    @Nullable
    public static GenericStack unwrapItemStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        return wrapper().unwrap(stack);
    }
}
