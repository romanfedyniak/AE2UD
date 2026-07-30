package appeng.core.api;


import java.io.IOException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;


/**
 * The concrete {@link AEKeyType} for {@link AEFluidKey}, registered under {@link AEKeyTypes#FLUIDS_ID}.
 * <p/>
 * See {@link AEItemKeyType} for why this lives in {@code src/main} instead of {@code src/api}. The unit numbers
 * mirror AE2-original's {@code AEFluidKeys} (1 bucket = 1000 mB, 8000 mB per storage-cell byte, 125 mB per
 * machine operation - the same 125 mB/op Forge fluid handlers already move per bucket-equivalent tick).
 */
public final class AEFluidKeyType extends AEKeyType {

    public AEFluidKeyType() {
        super(AEKeyTypes.FLUIDS_ID, AEFluidKey.class, new TextComponentString("Fluids"));
    }

    @Override
    public AEKey readFromPacket(@Nonnull final ByteBuf input) throws IOException {
        return AEFluidKey.fromPacket(input);
    }

    @Nullable
    @Override
    public AEKey loadKeyFromTag(@Nonnull final NBTTagCompound tag) {
        return AEFluidKey.fromTag(tag);
    }

    @Override
    public int getAmountPerOperation() {
        return AEFluidKey.AMOUNT_BUCKET * 125 / 1000;
    }

    @Override
    public int getAmountPerByte() {
        return 8 * AEFluidKey.AMOUNT_BUCKET;
    }

    @Override
    public int getAmountPerUnit() {
        return AEFluidKey.AMOUNT_BUCKET;
    }

    @Override
    public String getUnitSymbol() {
        return "B";
    }

    @Override
    public ResourceLocation getButtonTexture() {
        return new ResourceLocation("appliedenergistics2", "textures/guis/states.png");
    }

    @Override
    public ItemStack getButtonIcon() {
        return new ItemStack(Items.WATER_BUCKET);
    }

}
