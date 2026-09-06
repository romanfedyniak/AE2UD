/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.loot;


import appeng.core.AELog;
import appeng.core.AppEng;
import com.github.bsideup.jabel.Desugar;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.LootTableManager;

import javax.annotation.Nullable;


/**
 * A loot context that remembers what has already come out of the pool it is filling.
 * <p>
 * Vanilla rolls each entry of a pool independently, so a pool of four presses rolled four times can hand out
 * the same press four times. This is what lets an entry say it may only come up once per container.
 */
public class TallyingLootContext extends LootContext implements ILootTallyer {

    private final Object2IntMap<ItemEntry> talliedItems;

    public TallyingLootContext(final float luckIn, final WorldServer worldIn,
            final LootTableManager lootTableManagerIn, @Nullable final Entity lootedEntityIn,
            @Nullable final EntityPlayer playerIn, @Nullable final DamageSource damageSourceIn) {
        super(luckIn, worldIn, lootTableManagerIn, lootedEntityIn, playerIn, damageSourceIn);

        this.talliedItems = new Object2IntArrayMap<>();
        this.talliedItems.defaultReturnValue(0);
    }

    /** A table using these may still be rolled by something else; say so rather than failing. */
    @Nullable
    public static ILootTallyer functionHasContext(final LootContext context, final String lootFunctionName) {
        if (context instanceof ILootTallyer) {
            return (ILootTallyer) context;
        }

        AELog.warn("Using LootFunction %s:%s without a TallyingLootContext! Skipping function.",
                AppEng.MOD_ID, lootFunctionName);
        return null;
    }

    @Nullable
    public static ILootTallyer conditionHasContext(final LootContext context, final String lootConditionName) {
        if (context instanceof ILootTallyer) {
            return (ILootTallyer) context;
        }

        AELog.warn("Using LootCondition %s:%s without a TallyingLootContext! Skipping condition.",
                AppEng.MOD_ID, lootConditionName);
        return null;
    }

    @Override
    public boolean canRoll(final int max, final String itemName, final int contextId) {
        return this.talliedItems.getInt(new ItemEntry(itemName, contextId)) < max;
    }

    @Override
    public void tally(final String itemName, final int contextId) {
        final ItemEntry key = new ItemEntry(itemName, contextId);
        this.talliedItems.put(key, this.talliedItems.getInt(key) + 1);
    }

    public static class Builder {

        private final WorldServer world;

        private float luck;
        private Entity lootedEntity;
        private EntityPlayer player;
        private DamageSource damageSource;

        public Builder(final WorldServer worldIn) {
            this.world = worldIn;
        }

        public Builder withLuck(final float luckIn) {
            this.luck = luckIn;
            return this;
        }

        public Builder withLootedEntity(final Entity entityIn) {
            this.lootedEntity = entityIn;
            return this;
        }

        public Builder withPlayer(final EntityPlayer playerIn) {
            this.player = playerIn;
            return this;
        }

        public Builder withDamageSource(final DamageSource dmgSource) {
            this.damageSource = dmgSource;
            return this;
        }

        public TallyingLootContext build() {
            return new TallyingLootContext(this.luck, this.world, this.world.getLootTableManager(),
                    this.lootedEntity, this.player, this.damageSource);
        }
    }

    @Desugar
    private record ItemEntry(String itemName, int contextId) {
    }
}
