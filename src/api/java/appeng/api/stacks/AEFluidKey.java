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

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.storage.AEKeyFilter;

/**
 * Identifies a fluid: its {@link Fluid} and NBT, without an amount.
 */
public final class AEFluidKey extends AEKey {

    /**
     * How many millibuckets are in one bucket, which is the unit fluids are displayed in.
     */
    public static final int AMOUNT_BUCKET = 1000;

    private final Fluid fluid;
    @Nullable
    private final NBTTagCompound tag;
    private final int hash;

    private AEFluidKey(Fluid fluid, @Nullable NBTTagCompound tag) {
        this.fluid = fluid;
        // An empty compound carries nothing, so it must not make a different key than no compound at all.
        // Forge's own FluidStack comparison treats the two as distinct, and mods routinely hand out a
        // FluidStack with an empty tag - which meant one fluid could occupy two entries in a network, show
        // as two identical rows in a terminal, and, worst of all, make a crafting job wait forever for an
        // output that had already arrived under the other key.
        this.tag = tag == null || tag.isEmpty() ? null : tag;
        this.hash = Objects.hash(fluid.getName(), this.tag);
    }

    public static AEFluidKey of(Fluid fluid) {
        return new AEFluidKey(Objects.requireNonNull(fluid, "fluid"), null);
    }

    /**
     * @return null for a null or empty stack.
     */
    @Nullable
    public static AEFluidKey of(@Nullable FluidStack stack) {
        if (stack == null || stack.getFluid() == null) {
            return null;
        }
        return new AEFluidKey(stack.getFluid(), stack.tag == null ? null : stack.tag.copy());
    }

    public static boolean matches(@Nullable AEKey what, @Nullable FluidStack fluid) {
        return what instanceof AEFluidKey fluidKey && fluidKey.matches(fluid);
    }

    public static boolean is(@Nullable AEKey what) {
        return what instanceof AEFluidKey;
    }

    public static AEKeyFilter filter() {
        return AEFluidKey::is;
    }

    @Override
    public AEKeyType getType() {
        return AEKeyTypes.fluids();
    }

    @Override
    public ResourceLocation getId() {
        return new ResourceLocation("fluid", this.fluid.getName());
    }

    @Override
    public Object getPrimaryKey() {
        return this.fluid;
    }

    @Override
    public AEFluidKey dropSecondary() {
        return this.tag == null ? this : new AEFluidKey(this.fluid, null);
    }

    public Fluid getFluid() {
        return this.fluid;
    }

    /**
     * @return the key's NBT, or null. Never mutate the returned tag.
     */
    @Nullable
    public NBTTagCompound getTag() {
        return this.tag;
    }

    public FluidStack toStack(int amount) {
        if (amount <= 0) {
            return null;
        }
        return new FluidStack(this.fluid, amount, this.tag == null ? null : this.tag.copy());
    }

    public boolean matches(@Nullable FluidStack stack) {
        if (stack == null || stack.getFluid() != this.fluid) {
            return false;
        }
        return Objects.equals(stack.tag, this.tag);
    }

    @Override
    public void toTag(NBTTagCompound out) {
        out.setString("fluid", this.fluid.getName());
        if (this.tag != null) {
            out.setTag("tag", this.tag.copy());
        }
    }

    @Nullable
    public static AEFluidKey fromTag(NBTTagCompound tag) {
        Fluid fluid = FluidRegistry.getFluid(tag.getString("fluid"));
        if (fluid == null) {
            return null;
        }
        NBTTagCompound extra = tag.hasKey("tag") ? tag.getCompoundTag("tag") : null;
        return new AEFluidKey(fluid, extra);
    }

    @Override
    public void writeToPacket(ByteBuf data) throws IOException {
        PacketBuffer buffer = new PacketBuffer(data);
        buffer.writeString(this.fluid.getName());
        buffer.writeCompoundTag(this.tag);
    }

    public static AEFluidKey fromPacket(ByteBuf data) throws IOException {
        PacketBuffer buffer = new PacketBuffer(data);
        String name = buffer.readString(Short.MAX_VALUE);
        NBTTagCompound tag;
        try {
            tag = buffer.readCompoundTag();
        } catch (DecoderException e) {
            throw new IOException("Failed to read the NBT of a fluid key.", e);
        }
        Fluid fluid = FluidRegistry.getFluid(name);
        if (fluid == null) {
            throw new IOException("Received a fluid key with an unknown fluid: " + name);
        }
        return new AEFluidKey(fluid, tag);
    }

    @Override
    protected ITextComponent computeDisplayName() {
        FluidStack stack = new FluidStack(this.fluid, AMOUNT_BUCKET, this.tag);
        try {
            return new TextComponentString(this.fluid.getLocalizedName(stack));
        } catch (Throwable e) {
            // A broken fluid must not take down the whole terminal.
            return new TextComponentString(this.fluid.getName());
        }
    }

    @Override
    public void addDrops(long amount, List<ItemStack> drops, @Nonnull World world, @Nonnull BlockPos pos) {
        // Fluids have no item form of their own. Upstream AE2 drops nothing here either; the
        // content is simply voided when the machine holding it breaks.
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AEFluidKey other)) {
            return false;
        }
        // By name, not by identity, because the hash is by name and the two have to agree. Forge lets more
        // than one Fluid instance exist for a name - a mod keeps its own object while the registry hands out
        // a default - so an identity check could call two keys different that hash the same, which is how a
        // HashMap ends up holding both.
        return this.fluid.getName().equals(other.fluid.getName()) && Objects.equals(this.tag, other.tag);
    }

    @Override
    public int hashCode() {
        return this.hash;
    }

    @Override
    public String toString() {
        return this.fluid.getName() + (this.tag != null ? this.tag.toString() : "");
    }
}
