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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.Nullable;

import com.google.common.collect.Lists;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

import appeng.api.behaviors.PickupSink;
import appeng.api.behaviors.PickupStrategy;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.stacks.AEItemKey;
import appeng.util.Platform;

/**
 * Item pickup for the annihilation plane: breaks the adjacent block or absorbs a touching item
 * entity, and stores the result through the {@link PickupSink} the plane gives it. This is the
 * pre-port {@code PartAnnihilationPlane}'s block-breaking logic (itself already correct for 1.12.2),
 * moved off the part and behind the frozen {@link PickupStrategy} contract.
 * <p>
 * {@code obtainBlockDrops}/{@code calculateEnergyUsage} stay {@code protected} (matching
 * AE2-original's own {@code ItemPickupStrategy}) because {@link IdentityItemPickupStrategy} needs to
 * override them, exactly like the pre-port {@code PartIdentityAnnihilationPlane} used to override
 * them directly on the part.
 */
class ItemPickupStrategy implements PickupStrategy {

    private final World world;
    private final BlockPos pos;
    private final EnumFacing side;
    protected final Map<Enchantment, Integer> enchantments;
    @Nullable
    private final UUID ownerUuid;

    private boolean isAccepting = true;

    ItemPickupStrategy(World world, BlockPos pos, EnumFacing side, TileEntity host,
            Map<Enchantment, Integer> enchantments, @Nullable UUID ownerUuid) {
        this.world = world;
        this.pos = pos;
        this.side = side;
        this.enchantments = enchantments;
        this.ownerUuid = ownerUuid;
    }

    @Override
    public void reset() {
        this.isAccepting = true;
    }

    @Override
    public boolean canPickUpEntity(Entity entity) {
        return entity instanceof EntityItem;
    }

    @Override
    public boolean pickUpEntity(IEnergySource energySource, PickupSink sink, Entity entity) {
        if (!this.isAccepting || !(entity instanceof EntityItem) || entity.isDead) {
            return false;
        }

        return this.storeEntityItem(sink, (EntityItem) entity);
    }

    @Override
    public Result tryPickup(IEnergySource energySource, PickupSink sink) {
        if (!this.isAccepting || !(this.world instanceof WorldServer)) {
            return Result.CANT_PICKUP;
        }

        WorldServer w = (WorldServer) this.world;
        if (!this.canHandleBlock(w, this.pos)) {
            return Result.CANT_PICKUP;
        }

        List<ItemStack> items = this.obtainBlockDrops(w, this.pos);
        float requiredPower = this.calculateEnergyUsage(w, this.pos, items);

        boolean hasPower = energySource.extractAEPower(requiredPower, Actionable.SIMULATE,
                PowerMultiplier.CONFIG) > requiredPower - 0.1;
        boolean canStore = this.canStoreItemStacks(sink, items);

        if (!hasPower || !canStore) {
            return Result.CANT_STORE;
        }

        energySource.extractAEPower(requiredPower, Actionable.MODULATE, PowerMultiplier.CONFIG);
        this.breakBlockAndStoreItems(sink, w, this.pos, items);
        return Result.PICKED_UP;
    }

    /**
     * @return true if the entity was fully consumed (see {@link PickupStrategy#pickUpEntity}'s
     *         contract). Any leftover is still stored back onto the entity's stack even when this
     *         returns false, same as the pre-port behaviour.
     */
    private boolean storeEntityItem(PickupSink sink, EntityItem entityItem) {
        ItemStack overflow = this.storeItemStack(sink, entityItem.getItem());

        if (overflow.isEmpty()) {
            entityItem.setDead();
            return true;
        }

        entityItem.getItem().setCount(overflow.getCount());
        return false;
    }

    /**
     * @return the leftover items that could not be stored inside the network.
     */
    private ItemStack storeItemStack(PickupSink sink, ItemStack item) {
        if (item.isEmpty()) {
            return ItemStack.EMPTY;
        }

        AEItemKey what = AEItemKey.of(item);
        long inserted = sink.insert(what, item.getCount(), Actionable.MODULATE);
        this.isAccepting = inserted >= item.getCount();

        long leftover = item.getCount() - inserted;
        return leftover > 0 ? what.toStack((int) leftover) : ItemStack.EMPTY;
    }

    /**
     * Checks if this plane can handle the block at the specific coordinates.
     */
    protected boolean canHandleBlock(WorldServer w, BlockPos pos) {
        IBlockState state = w.getBlockState(pos);
        Material material = state.getMaterial();
        float hardness = state.getBlockHardness(w, pos);
        boolean ignoreMaterials = material == Material.AIR || material == Material.LAVA
                || material == Material.WATER || material.isLiquid();
        boolean ignoreBlocks = state.getBlock() == Blocks.BEDROCK || state.getBlock() == Blocks.END_PORTAL
                || state.getBlock() == Blocks.END_PORTAL_FRAME || state.getBlock() == Blocks.COMMAND_BLOCK;

        return !ignoreMaterials && !ignoreBlocks && hardness >= 0f && !w.isAirBlock(pos) && w.isBlockLoaded(pos)
                && w.canMineBlockBody(Platform.getPlayer(w), pos);
    }

    protected List<ItemStack> obtainBlockDrops(WorldServer w, BlockPos pos) {
        FakePlayer fakePlayer = FakePlayerFactory.getMinecraft(w);
        IBlockState state = w.getBlockState(pos);

        if (state.getBlock().canSilkHarvest(w, pos, state, fakePlayer) && this.enchantments.containsKey(Enchantments.SILK_TOUCH)) {
            List<ItemStack> out = new ArrayList<>(1);
            Item item = Item.getItemFromBlock(state.getBlock());

            if (item != Items.AIR) {
                int meta = 0;
                if (item.getHasSubtypes()) {
                    meta = state.getBlock().getMetaFromState(state);
                }
                out.add(new ItemStack(item, 1, meta));
            }
            return out;
        } else {
            ItemStack[] out = Platform.getBlockDrops(w, pos, this.enchantments.getOrDefault(Enchantments.FORTUNE, 0));
            return Lists.newArrayList(out);
        }
    }

    /**
     * Checks how much power this pickup will cost.
     */
    protected float calculateEnergyUsage(WorldServer w, BlockPos pos, List<ItemStack> items) {
        boolean useEnergy = true;
        IBlockState state = w.getBlockState(pos);
        float hardness = state.getBlockHardness(w, pos);

        float requiredEnergy = 1 + hardness;
        for (ItemStack is : items) {
            requiredEnergy += is.getCount();
        }

        if (!this.enchantments.isEmpty()) {
            float efficiencyFactor = 1f;
            int efficiencyLevel = 0;
            if (this.enchantments.containsKey(Enchantments.EFFICIENCY)) {
                // Reduce total energy usage incurred by other enchantments by 15% per Efficiency level.
                efficiencyLevel = this.enchantments.get(Enchantments.EFFICIENCY);
                efficiencyFactor *= Math.pow(0.85, efficiencyLevel);
            }
            if (this.enchantments.containsKey(Enchantments.UNBREAKING)) {
                // Give the plane only a (100 / (level + 1))% chance to use energy at all.
                int unbreakingLevel = this.enchantments.get(Enchantments.UNBREAKING);
                int randomNumber = ThreadLocalRandom.current().nextInt(unbreakingLevel + 1);
                useEnergy = randomNumber == 0;
            }
            // Preserved verbatim from the pre-port formula, including its rounding behaviour.
            int levelSum = this.enchantments.values().stream().reduce(0, Integer::sum) - efficiencyLevel;
            requiredEnergy *= 8 * levelSum * efficiencyFactor;
        }

        return useEnergy ? requiredEnergy : 0;
    }

    /**
     * Checks if the network can store the possible drops, and sets isAccepting to false if not.
     */
    private boolean canStoreItemStacks(PickupSink sink, List<ItemStack> itemStacks) {
        boolean canStore = true;

        for (ItemStack itemStack : itemStacks) {
            AEItemKey what = AEItemKey.of(itemStack);
            long inserted = sink.insert(what, itemStack.getCount(), Actionable.SIMULATE);
            if (inserted < itemStack.getCount()) {
                canStore = false;
            }
        }

        this.isAccepting = canStore;
        return canStore;
    }

    private void breakBlockAndStoreItems(PickupSink sink, WorldServer w, BlockPos pos, List<ItemStack> items) {
        for (ItemStack item : items) {
            Block.spawnAsEntity(w, pos, item);
        }

        AxisAlignedBB box = new AxisAlignedBB(pos).grow(0.2);
        for (EntityItem entityItem : w.getEntitiesWithinAABB(EntityItem.class, box)) {
            this.storeEntityItem(sink, entityItem);
        }

        w.destroyBlock(pos, false);
    }
}
