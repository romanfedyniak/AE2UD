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

import appeng.api.behaviors.StackImportStrategy;
import appeng.api.behaviors.StackTransferContext;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import appeng.util.inv.IInventoryDestination;

/**
 * Item counterpart of AE2-original's {@code StorageImportStrategy}, reshaped around
 * {@link InventoryAdaptor} instead of a raw capability handler -- see {@link HandlerStrategy}'s
 * javadoc for why. Functionally this is the pre-port {@code PartImportBus#importStuff}/
 * {@code #calculateMaximumAmountToImport} logic moved off the part and behind the frozen
 * {@link StackImportStrategy} contract, so it now also fires for any other registered
 * {@code AEKeyType} sharing the same import bus.
 */
class StorageImportStrategy implements StackImportStrategy {

    private final World world;
    private final BlockPos fromPos;
    private final EnumFacing fromSide;

    StorageImportStrategy(World world, BlockPos fromPos, EnumFacing fromSide) {
        this.world = world;
        this.fromPos = fromPos;
        this.fromSide = fromSide;
    }

    @Override
    public boolean transfer(StackTransferContext context) {
        if (!(context instanceof StackTransferContextImpl ctx)) {
            return false;
        }

        InventoryAdaptor adaptor = getAdaptor();
        if (adaptor == null) {
            return false;
        }

        var partitionList = ctx.getPartitionList();
        var fuzzyMode = ctx.getFuzzyMode();
        boolean worked = false;

        if (!partitionList.isEmpty()) {
            for (AEKey key : partitionList.getItems()) {
                if (!(key instanceof AEItemKey itemKey)) {
                    continue;
                }
                while (context.hasOperationsLeft()) {
                    if (importOne(ctx, adaptor, itemKey.toStack(), fuzzyMode)) {
                        worked = true;
                    } else {
                        break;
                    }
                }
            }
        } else {
            while (context.hasOperationsLeft()) {
                if (importOne(ctx, adaptor, ItemStack.EMPTY, fuzzyMode)) {
                    worked = true;
                } else {
                    break;
                }
            }
        }

        return worked;
    }

    /**
     * @return true if progress was made and the caller should keep looping for the same filter.
     */
    private boolean importOne(StackTransferContextImpl context, InventoryAdaptor adaptor, ItemStack filter,
            @Nullable FuzzyMode fuzzyMode) {
        int toSend = (int) Math.min(context.getOperationsRemaining(), 64);
        if (toSend <= 0) {
            return false;
        }

        ItemStack simResult = fuzzyMode != null ? adaptor.simulateSimilarRemove(toSend, filter, fuzzyMode, null)
                : adaptor.simulateRemove(toSend, filter, null);

        if (simResult.isEmpty()) {
            return false;
        }

        var internal = context.getInternalStorage();
        var source = context.getActionSource();

        AEKey candidateKey = AEItemKey.of(simResult);
        long acceptable = internal.insert(candidateKey, simResult.getCount(), Actionable.SIMULATE, source);
        if (acceptable <= 0) {
            return false;
        }

        int actualToSend = (int) Math.min(acceptable, toSend);

        IInventoryDestination destination = stack -> internal.insert(AEItemKey.of(stack), stack.getCount(),
                Actionable.SIMULATE, source) > 0;

        ItemStack removed = fuzzyMode != null ? adaptor.removeSimilarItems(actualToSend, filter, fuzzyMode, destination)
                : adaptor.removeItems(actualToSend, filter, destination);

        if (removed.isEmpty()) {
            return false;
        }

        AEKey extractedKey = AEItemKey.of(removed);
        long inserted = Platform.poweredInsert(context.getEnergySource(), internal, extractedKey, removed.getCount(),
                source);

        if (inserted < removed.getCount()) {
            long leftover = removed.getCount() - inserted;

            // Be lenient rather than void items: try an unpowered (free) insert into whatever room is
            // left, mirroring the pre-port "try unpowered insert" fallback.
            long spillInserted = internal.insert(extractedKey, leftover, Actionable.MODULATE, source);
            long stillLeftover = leftover - spillInserted;

            if (stillLeftover > 0) {
                // Last resort: hand it back to the source inventory. If that also fails, the item is
                // voided, exactly like the pre-port comment "better be a bit lenient then void items".
                ItemStack toReturn = ((AEItemKey) extractedKey).toStack((int) stillLeftover);
                adaptor.addItems(toReturn);
            }
            return false;
        }

        context.reduceOperationsRemaining(Math.max(1, inserted));
        return true;
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

    static StackImportStrategy createItem(World world, BlockPos fromPos, EnumFacing fromSide) {
        return new StorageImportStrategy(world, fromPos, fromSide);
    }
}
