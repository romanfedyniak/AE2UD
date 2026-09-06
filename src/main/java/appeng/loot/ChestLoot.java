/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
 * Copyright (c) 2026 AE2UD contributors
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

package appeng.loot;


import appeng.core.AppEng;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.loot.LootEntry;
import net.minecraft.world.storage.loot.LootEntryTable;
import net.minecraft.world.storage.loot.LootPool;
import net.minecraft.world.storage.loot.LootTable;
import net.minecraft.world.storage.loot.LootTableList;
import net.minecraft.world.storage.loot.RandomValueRange;
import net.minecraft.world.storage.loot.conditions.LootCondition;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.List;
import java.util.Random;

import static appeng.worldgen.meteorite.MeteorConstants.METEOR_LOOT_TABLE;


public class ChestLoot {

    /** Rolled into the vanilla mineshaft table, and overridable because it is a table of its own. */
    public static final String MINESHAFT_INJECT_TABLE = "inject/mineshaft";

    /**
     * What goes in the chest at the centre of a meteorite. Rolled through a context that counts what it
     * has already handed out, so the table can say that a press turns up at most once per chest.
     */
    public static List<ItemStack> generateMeteorLoot(final World world, final Random rand) {
        final LootTable table = world.getLootTableManager()
                .getLootTableFromLocation(new ResourceLocation(AppEng.MOD_ID, METEOR_LOOT_TABLE));

        return table.generateLootForPools(rand,
                new TallyingLootContext.Builder((WorldServer) world).build());
    }

    /**
     * Adds one pool to the mineshaft table, holding nothing but a reference to AE2's own table.
     * <p>
     * The alternative is building the pools here out of {@code LootPool} and {@code LootEntryItem}, which is
     * how this worked for a decade - and which no resource pack could touch, so a pack that wanted less
     * certus in its chests had to turn the whole thing off or reach for another mod. Everything that decides
     * what actually comes out now lives in the JSON.
     */
    @SubscribeEvent
    public void loadLootTable(final LootTableLoadEvent event) {
        if (event.getName() != LootTableList.CHESTS_ABANDONED_MINESHAFT) {
            return;
        }

        final LootEntry entry = new LootEntryTable(
                new ResourceLocation(AppEng.MOD_ID, MINESHAFT_INJECT_TABLE),
                1, 0, new LootCondition[0], "AE2 Mineshaft Loot");

        event.getTable().addPool(new LootPool(new LootEntry[] { entry }, new LootCondition[0],
                new RandomValueRange(1), new RandomValueRange(0), "AE2 Mineshaft"));
    }
}
