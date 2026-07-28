/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.parts.automation;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import appeng.api.behaviors.StackExportStrategy;
import appeng.api.behaviors.StackTransferContext;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;

/**
 * Item counterpart of AE2-original's {@code StorageExportStrategy}. Reshaped around
 * {@link InventoryAdaptor} for the same reason as {@link StorageImportStrategy} -- see
 * {@link HandlerStrategy}. Ports the pre-port {@code PartExportBus#pushItemIntoTarget}/
 * {@code #injectCraftedItems} logic.
 */
class StorageExportStrategy implements StackExportStrategy {

    private final World world;
    private final BlockPos fromPos;
    private final EnumFacing fromSide;

    StorageExportStrategy(World world, BlockPos fromPos, EnumFacing fromSide) {
        this.world = world;
        this.fromPos = fromPos;
        this.fromSide = fromSide;
    }

    @Override
    public long transfer(StackTransferContext context, AEKey what, long maxAmount) {
        if (!(what instanceof AEItemKey itemKey) || maxAmount <= 0 || !(context instanceof StackTransferContextImpl ctx)) {
            return 0;
        }

        InventoryAdaptor adaptor = getAdaptor();
        if (adaptor == null) {
            return 0;
        }

        int amount = (int) Math.min(maxAmount, Integer.MAX_VALUE);
        ItemStack template = itemKey.toStack(amount);

        ItemStack notAccepted = adaptor.simulateAdd(template);
        int canFit = amount - (notAccepted.isEmpty() ? 0 : notAccepted.getCount());
        if (canFit <= 0) {
            return 0;
        }

        var internal = context.getInternalStorage();
        var source = context.getActionSource();

        long extracted = Platform.poweredExtraction(ctx.getEnergySource(), internal, itemKey, canFit, source);
        if (extracted <= 0) {
            return 0;
        }

        ItemStack toInsert = itemKey.toStack((int) extracted);
        ItemStack failed = adaptor.addItems(toInsert);
        long insertedIntoTarget = extracted - (failed.isEmpty() ? 0 : failed.getCount());

        if (!failed.isEmpty()) {
            // Spill whatever couldn't be pushed back into the network rather than voiding it.
            internal.insert(itemKey, failed.getCount(), Actionable.MODULATE, source);
        }

        return insertedIntoTarget;
    }

    @Override
    public long push(AEKey what, long maxAmount, Actionable mode) {
        if (!(what instanceof AEItemKey itemKey) || maxAmount <= 0) {
            return 0;
        }

        InventoryAdaptor adaptor = getAdaptor();
        if (adaptor == null) {
            return 0;
        }

        int amount = (int) Math.min(maxAmount, Integer.MAX_VALUE);
        ItemStack stack = itemKey.toStack(amount);
        ItemStack remaining = mode == Actionable.SIMULATE ? adaptor.simulateAdd(stack) : adaptor.addItems(stack);
        int notAccepted = remaining.isEmpty() ? 0 : remaining.getCount();
        return amount - notAccepted;
    }

    @Nullable
    private InventoryAdaptor getAdaptor() {
        TileEntity target = getTileEntity();
        return target == null ? null : InventoryAdaptor.getAdaptor(target, this.fromSide);
    }

    @Nullable
    private TileEntity getTileEntity() {
        if (this.world.getChunkProvider().getLoadedChunk(this.fromPos.getX() >> 4, this.fromPos.getZ() >> 4) == null) {
            return null;
        }
        return this.world.getTileEntity(this.fromPos);
    }

    static StackExportStrategy createItem(World world, BlockPos fromPos, EnumFacing fromSide) {
        return new StorageExportStrategy(world, fromPos, fromSide);
    }
}
