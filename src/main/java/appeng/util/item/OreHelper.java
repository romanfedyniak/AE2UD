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

package appeng.util.item;


import appeng.api.stacks.AEItemKey;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;


public class OreHelper {

    public static final OreHelper INSTANCE = new OreHelper();

    /**
     * A local cache to speed up OreDictionary lookups.
     */
    private final LoadingCache<String, List<ItemStack>> oreDictCache = CacheBuilder.newBuilder().build(new CacheLoader<String, List<ItemStack>>() {
        @Override
        public List<ItemStack> load(final String oreName) {
            return OreDictionary.getOres(oreName);
        }
    });

    /** Stands for "no ore names" in {@link #references}, which cannot hold null. */
    private static final OreReference NONE = new OreReference();

    // Asked from the grids' threads at once on a server that ticks them in parallel, and from both sides in
    // single player. By item, then damage (none for a damageable item), so a lookup makes no key object.
    private final Map<Item, Map<Integer, OreReference>> references = new ConcurrentHashMap<>();

    /**
     * The ore names of what a key stands for, or null for none. Asked on every insert, extract and change report
     * of an ore dictionary bus, so a key already seen is answered without making a stack.
     */
    @Nullable
    public OreReference getOre(final AEItemKey key) {
        final OreReference known = this.known(key.getItem(), key.getDamage());
        if (known != null) {
            return known == NONE ? null : known;
        }
        return this.getOre(key.getReadOnlyStack()).orElse(null);
    }

    @Nullable
    private OreReference known(final Item item, final int damage) {
        final Map<Integer, OreReference> byDamage = this.references.get(item);
        return byDamage == null ? null : byDamage.get(item.isDamageable() ? 0 : damage);
    }

    /**
     * Test if the passed {@link ItemStack} is an ore.
     *
     * @param itemStack the itemstack to test
     * @return true if an ore entry exists, false otherwise
     */
    public Optional<OreReference> getOre(final ItemStack itemStack) {
        final Item item = itemStack.getItem();
        OreReference known = this.known(item, itemStack.getItemDamage());
        if (known == null) {
            final OreReference ref = new OreReference();
            final Collection<Integer> ores = ref.getOres();
            final Collection<String> set = ref.getEquivalents();

            final Set<String> toAdd = new HashSet<>();

            for (final String ore : OreDictionary.getOreNames()) {
                // skip ore if it is a match already or null.
                if (ore == null || toAdd.contains(ore)) {
                    continue;
                }

                for (final ItemStack oreItem : this.oreDictCache.getUnchecked(ore)) {
                    if (OreDictionary.itemMatches(oreItem, itemStack, false)) {
                        toAdd.add(ore);
                        break;
                    }
                }
            }

            for (final String ore : toAdd) {
                set.add(ore);
                ores.add(OreDictionary.getOreID(ore));
            }

            known = set.isEmpty() ? NONE : ref;
            this.references.computeIfAbsent(item, i -> new ConcurrentHashMap<>())
                    .put(item.isDamageable() ? 0 : itemStack.getItemDamage(), known);
        }

        return known == NONE ? Optional.empty() : Optional.of(known);
    }

    public boolean sameOre(final AEItemKey itemKey, final AEItemKey other) {
        final OreReference a = this.getOre(itemKey.getReadOnlyStack()).orElse(null);
        final OreReference b = this.getOre(other.getReadOnlyStack()).orElse(null);

        return this.sameOre(a, b);
    }

    public boolean sameOre(final OreReference a, final OreReference b) {
        if (a == null || b == null) {
            return false;
        }

        if (a == b) {
            return true;
        }

        final Collection<Integer> bOres = b.getOres();
        for (final Integer ore : a.getOres()) {
            if (bOres.contains(ore)) {
                return true;
            }
        }

        return false;
    }

    public boolean sameOre(final AEItemKey itemKey, final ItemStack o) {
        return this.getOre(itemKey.getReadOnlyStack()).map(a -> {
            for (final String oreName : a.getEquivalents()) {
                for (final ItemStack oreItem : this.oreDictCache.getUnchecked(oreName)) {
                    if (OreDictionary.itemMatches(oreItem, o, false)) {
                        return true;
                    }
                }
            }
            return false;
        }).orElse(false);
    }

    public Set<Integer> getMatchingOre(List<OreDictFilterMatcher.MatchRule> rulesList) {
        Set<Integer> matchingIds = new HashSet<>();

        for (String ore : OreDictionary.getOreNames()) {
            if (ore == null) {
                continue;
            }
            if (OreDictFilterMatcher.matches(rulesList, ore)) {
                matchingIds.add(OreDictionary.getOreID(ore));
            }
        }
        return matchingIds;
    }

    public List<ItemStack> getCachedOres(final String oreName) {
        return this.oreDictCache.getUnchecked(oreName);
    }
}
