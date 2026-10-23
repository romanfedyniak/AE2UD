/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.core.migration.uel;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import appeng.api.behaviors.GenericSlotCapacities;
import appeng.api.migration.LegacyRewrites;
import appeng.api.migration.LegacyStacks;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;

/**
 * UEL's item interface and fluid interface, as a block or a part, into AE2UD's one interface.
 */
final class UelInterfaces {

    private static final int CAPACITY_CARD = 27;

    private UelInterfaces() {
    }

    static void register() {
        LegacyRewrites.addTileRewriter("appliedenergistics2:interface", tile -> {
            rewriteItemInterface(tile, "interface" + at(tile));
            return tile;
        });
        LegacyRewrites.addTileRewriter("appliedenergistics2:fluid_interface", tile -> {
            rewriteFluidInterface(tile, "fluid interface" + at(tile));
            tile.setString("id", "appliedenergistics2:interface");
            return tile;
        });
    }

    static void rewriteItemInterface(final NBTTagCompound data, final String where) {
        LegacyStacks.rewriteInventory(data.getCompoundTag("config"), where + ", config");

        final NBTTagCompound storage = data.getCompoundTag("storage");
        if (storage.hasKey("Items", Constants.NBT.TAG_LIST)) {
            data.setTag("storage", itemStorage(storage, where));
        }
        // A copy of the storage the block kept for its neighbours; read too, it would be counted twice.
        data.removeTag("inv");

        if (data.hasKey("unlockStack", Constants.NBT.TAG_COMPOUND)) {
            final NBTTagCompound unlock = data.getCompoundTag("unlockStack");
            if (!unlock.hasKey(AEKey.TYPE_FIELD)) {
                final GenericStack read = LegacyStacks.read(unlock);
                final NBTTagCompound rewritten = new NBTTagCompound();
                GenericStack.writeTag(rewritten, read);
                data.setTag("unlockStack", rewritten);
            }
        }
    }

    /**
     * A tank kept as much as its capacity cards allowed, whatever its config said, so the config is set to that.
     */
    static void rewriteFluidInterface(final NBTTagCompound data, final String where) {
        final int cards = UelGrids.count(data.getCompoundTag("upgrades"), "appliedenergistics2:material", CAPACITY_CARD);
        // What one storage slot of an AE2UD interface holds of a fluid.
        final long slotCapacity = 8 * GenericSlotCapacities.get(AEKeyType.fluids());
        final long tank = Math.min((long) Math.pow(4, cards + 1) * 1000, slotCapacity);

        final NBTTagCompound config = data.getCompoundTag("config");
        LegacyStacks.rewriteInventory(config, where + ", config");
        for (final String key : config.getKeySet()) {
            final NBTTagCompound slot = config.getCompoundTag(key);
            if (slot.hasKey(GenericStack.AMOUNT_FIELD)) {
                slot.setLong(GenericStack.AMOUNT_FIELD, tank);
            }
        }
        data.setTag("config", config);

        final NBTTagCompound storage = data.getCompoundTag("storage");
        LegacyStacks.rewriteInventory(storage, where + ", tanks");
        for (final String key : storage.getKeySet()) {
            final long amount = storage.getCompoundTag(key).getLong(GenericStack.AMOUNT_FIELD);
            if (amount > slotCapacity) {
                LegacyRewrites.log().note(where + ", tank " + key + " holds " + amount + " mB, more than the "
                        + slotCapacity + " mB a slot takes; the interface returns the rest to the network");
            }
        }
        data.setTag("storage", storage);
    }

    private static NBTTagCompound itemStorage(final NBTTagCompound storage, final String where) {
        final NBTTagCompound rewritten = new NBTTagCompound();
        final NBTTagList items = storage.getTagList("Items", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < items.tagCount(); i++) {
            final NBTTagCompound item = items.getCompoundTagAt(i);
            final long amount = item.hasKey("stackSize") ? item.getInteger("stackSize") : item.getInteger("Count");
            final NBTTagCompound one = item.copy();
            one.setByte("Count", (byte) 1);
            one.removeTag("stackSize");
            one.removeTag("Slot");
            final AEItemKey key = AEItemKey.of(new ItemStack(one));
            if (key == null) {
                LegacyRewrites.log().lost(where + ", storage slot " + item.getInteger("Slot") + ": " + item);
                continue;
            }
            final NBTTagCompound slot = new NBTTagCompound();
            GenericStack.writeTag(slot, new GenericStack(key, amount));
            rewritten.setTag("#" + item.getInteger("Slot"), slot);
        }
        return rewritten;
    }

    static String at(final NBTTagCompound tile) {
        return " at " + tile.getInteger("x") + " " + tile.getInteger("y") + " " + tile.getInteger("z");
    }
}
