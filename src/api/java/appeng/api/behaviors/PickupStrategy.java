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

package appeng.api.behaviors;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import appeng.api.networking.energy.IEnergySource;
import appeng.api.stacks.AEKeyType;

/**
 * Knows how to take content of one {@link AEKeyType} out of the world: breaking a block, absorbing a
 * fluid source, picking up an entity. Used by the annihilation plane.
 */
public interface PickupStrategy {

    /**
     * Forgets any progress on a partially broken block.
     */
    void reset();

    boolean canPickUpEntity(Entity entity);

    /**
     * @return true if the entity was consumed.
     */
    boolean pickUpEntity(IEnergySource energySource, PickupSink sink, Entity entity);

    Result tryPickup(IEnergySource energySource, PickupSink sink);

    enum Result {
        /**
         * Nothing here this strategy knows how to pick up.
         */
        CANT_PICKUP,
        /**
         * Picked up and stored.
         */
        PICKED_UP,
        /**
         * There was something to pick up, but the sink had no room for it.
         */
        CANT_STORE
    }

    interface Factory {
        /**
         * Upstream AE2 passes an {@code ItemEnchantments} here. Forge 1.12.2 has no equivalent data
         * type, so the two enchantments the plane actually reads are passed directly.
         */
        PickupStrategy create(World world, BlockPos fromPos, EnumFacing fromSide, TileEntity host,
                int fortuneLevel, boolean silkTouch, @Nullable UUID owningPlayerId);
    }

    static void register(AEKeyType type, Factory factory) {
        StackWorldBehaviors.registerPickupStrategy(type, factory);
    }
}
