/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.core.migration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import appeng.api.migration.LegacyRewrites;

/**
 * What a rewritten tile or part handed to {@link LegacyRewrites#spill}: it rides in the tile's NBT until its chunk
 * has loaded, and goes into a chest beside the tile on the world's next tick.
 */
final class Spills {

    private final Map<World, List<Spill>> pending = new WeakHashMap<>();

    @SubscribeEvent
    public void onChunkLoad(final ChunkDataEvent.Load event) {
        if (!LegacyRewrites.isLegacyWorld() || event.getWorld().isRemote) {
            return;
        }
        final NBTTagList tiles = event.getData().getCompoundTag("Level").getTagList("TileEntities",
                Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < tiles.tagCount(); i++) {
            final NBTTagCompound tile = tiles.getCompoundTagAt(i);
            if (!tile.hasKey(LegacyRewrites.SPILL_KEY)) {
                continue;
            }
            final BlockPos pos = new BlockPos(tile.getInteger("x"), tile.getInteger("y"), tile.getInteger("z"));
            final NBTTagList items = tile.getTagList(LegacyRewrites.SPILL_KEY, Constants.NBT.TAG_COMPOUND);
            tile.removeTag(LegacyRewrites.SPILL_KEY);
            this.pending.computeIfAbsent(event.getWorld(), w -> new ArrayList<>())
                    .add(new Spill(pos, tile.getString("id"), items));
        }
    }

    /**
     * Not during the chunk load: placing a block then could load the neighbours from inside the load.
     */
    @SubscribeEvent
    public void onWorldTick(final TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote) {
            return;
        }
        final List<Spill> spills = this.pending.remove(event.world);
        if (spills == null) {
            return;
        }
        for (final Spill spill : spills) {
            place(event.world, spill);
        }
    }

    private static void place(final World world, final Spill spill) {
        final List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < spill.items.tagCount(); i++) {
            final ItemStack stack = new ItemStack(spill.items.getCompoundTagAt(i));
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }

        int next = 0;
        while (next < stacks.size()) {
            final BlockPos chestPos = freeBeside(world, spill.pos);
            if (chestPos == null) {
                break;
            }
            world.setBlockState(chestPos, Blocks.CHEST.getDefaultState());
            final TileEntity tile = world.getTileEntity(chestPos);
            if (!(tile instanceof TileEntityChest)) {
                break;
            }
            final TileEntityChest chest = (TileEntityChest) tile;
            final int first = next;
            for (int slot = 0; slot < chest.getSizeInventory() && next < stacks.size(); slot++) {
                chest.setInventorySlotContents(slot, stacks.get(next++));
            }
            LegacyRewrites.log().note(spill.what + " at " + at(spill.pos) + ": " + (next - first)
                    + " stacks it held are in a chest at " + at(chestPos));
        }

        for (; next < stacks.size(); next++) {
            final ItemStack stack = stacks.get(next);
            world.spawnEntity(new EntityItem(world, spill.pos.getX() + 0.5, spill.pos.getY() + 1.5,
                    spill.pos.getZ() + 0.5, stack));
            LegacyRewrites.log().note(spill.what + " at " + at(spill.pos) + ": no room for a chest, dropped "
                    + stack.getCount() + " " + stack.getItem().getRegistryName());
        }
    }

    /**
     * Air beside the tile or above it, which a chest can take without breaking anything. In the tile's own chunk:
     * that chunk saves the chest together with the tile that no longer holds the items, and a crash between
     * two chunks' saves would hand them out a second time.
     */
    private static BlockPos freeBeside(final World world, final BlockPos pos) {
        for (int up = 0; up <= 3; up++) {
            final BlockPos centre = pos.up(up);
            for (final EnumFacing facing : EnumFacing.values()) {
                final BlockPos candidate = centre.offset(facing);
                if (candidate.getY() > 0 && candidate.getY() < world.getHeight()
                        && candidate.getX() >> 4 == pos.getX() >> 4 && candidate.getZ() >> 4 == pos.getZ() >> 4
                        && world.isBlockLoaded(candidate) && world.isAirBlock(candidate) && !nextToChest(world, candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /**
     * Two chests side by side join into one, and a third beside them would not be placed at all.
     */
    private static boolean nextToChest(final World world, final BlockPos pos) {
        for (final EnumFacing facing : EnumFacing.Plane.HORIZONTAL) {
            if (world.getBlockState(pos.offset(facing)).getBlock() == Blocks.CHEST) {
                return true;
            }
        }
        return false;
    }

    private static String at(final BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static final class Spill {
        private final BlockPos pos;
        private final String what;
        private final NBTTagList items;

        private Spill(final BlockPos pos, final String what, final NBTTagList items) {
            this.pos = pos;
            this.what = what;
            this.items = items;
        }
    }
}
