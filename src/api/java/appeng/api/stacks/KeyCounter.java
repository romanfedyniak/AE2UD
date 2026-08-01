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

package appeng.api.stacks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.common.math.LongMath;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

import appeng.api.config.FuzzyMode;

/**
 * A multi-type "key to amount" collection. Replaces {@code IItemList}.
 * <p>
 * <strong>Note the semantic change.</strong> {@code IItemList} stored mutable {@code IAEStack}
 * elements, so callers could change an amount by mutating the element in place. Here the amount is a
 * {@code long} value in a map: it can only be changed through {@link #add}, {@link #set} or
 * {@link #remove} on the counter itself. Code that used to mutate stacks in place has to be
 * rewritten rather than adapted.
 * <p>
 * Entries are grouped by {@link AEKey#getPrimaryKey()} so that fuzzy lookups only scan the
 * variants of one item or fluid instead of the whole network.
 */
public final class KeyCounter implements Iterable<Object2LongMap.Entry<AEKey>> {

    private final Map<Object, Object2LongMap<AEKey>> byPrimary = new HashMap<>();

    public void add(AEKey key, long amount) {
        if (amount == 0) {
            return;
        }
        Object2LongMap<AEKey> sub = this.subMap(key);
        sub.put(key, LongMath.saturatedAdd(sub.getLong(key), amount));
    }

    public void remove(AEKey key, long amount) {
        this.add(key, -amount);
    }

    /**
     * Removes the key entirely.
     *
     * @return the amount that was stored under it.
     */
    public long remove(AEKey key) {
        Object2LongMap<AEKey> sub = this.byPrimary.get(key.getPrimaryKey());
        if (sub == null) {
            return 0;
        }
        long previous = sub.removeLong(key);
        if (sub.isEmpty()) {
            this.byPrimary.remove(key.getPrimaryKey());
        }
        return previous;
    }

    public void set(AEKey key, long amount) {
        if (amount == 0) {
            this.remove(key);
        } else {
            this.subMap(key).put(key, amount);
        }
    }

    public long get(AEKey key) {
        Object2LongMap<AEKey> sub = this.byPrimary.get(key.getPrimaryKey());
        return sub == null ? 0 : sub.getLong(key);
    }

    public void addAll(KeyCounter other) {
        for (Object2LongMap.Entry<AEKey> entry : other) {
            this.add(entry.getKey(), entry.getLongValue());
        }
    }

    public void removeAll(KeyCounter other) {
        for (Object2LongMap.Entry<AEKey> entry : other) {
            this.remove(entry.getKey(), entry.getLongValue());
        }
    }

    /**
     * Drops every entry whose amount is exactly zero.
     */
    public void removeZeros() {
        Iterator<Map.Entry<Object, Object2LongMap<AEKey>>> outer = this.byPrimary.entrySet().iterator();
        while (outer.hasNext()) {
            Object2LongMap<AEKey> sub = outer.next().getValue();
            Iterator<Object2LongMap.Entry<AEKey>> inner = sub.object2LongEntrySet().iterator();
            while (inner.hasNext()) {
                if (inner.next().getLongValue() == 0) {
                    inner.remove();
                }
            }
            if (sub.isEmpty()) {
                outer.remove();
            }
        }
    }

    public void removeEmptySubmaps() {
        this.byPrimary.values().removeIf(Map::isEmpty);
    }

    /**
     * Sets every amount to zero while keeping the keys, so repeated scans do not have to reallocate.
     * <p>
     * <strong>This is not {@code IItemList.resetStatus()}, however much it looks like it.</strong> The old
     * list's {@code isEmpty()} walked an iterator that skipped zero-size entries, so zeroing it made it
     * report empty; {@link #isEmpty()} here counts <em>keys</em>, and this method keeps them. Any code
     * that zeroes a counter and then asks whether it is empty wants {@link #clear()}. Getting it wrong is
     * silent and durable: it cost the crafting CPU its release, so no second job could ever be submitted.
     * <p>
     * Use this only for a counter that is about to be refilled by the same scan that reads it.
     */
    public void reset() {
        for (Object2LongMap<AEKey> sub : this.byPrimary.values()) {
            for (Object2LongMap.Entry<AEKey> entry : sub.object2LongEntrySet()) {
                entry.setValue(0L);
            }
        }
    }

    public void clear() {
        this.byPrimary.clear();
    }

    public boolean isEmpty() {
        for (Object2LongMap<AEKey> sub : this.byPrimary.values()) {
            if (!sub.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public int size() {
        int size = 0;
        for (Object2LongMap<AEKey> sub : this.byPrimary.values()) {
            size += sub.size();
        }
        return size;
    }

    public Set<AEKey> keySet() {
        Set<AEKey> keys = new HashSet<>(this.size());
        for (Object2LongMap<AEKey> sub : this.byPrimary.values()) {
            keys.addAll(sub.keySet());
        }
        return Collections.unmodifiableSet(keys);
    }

    /**
     * @return every entry sharing the key's primary identity that matches it under the given mode.
     */
    public Collection<Object2LongMap.Entry<AEKey>> findFuzzy(AEKey key, FuzzyMode fuzzy) {
        Object2LongMap<AEKey> sub = this.byPrimary.get(key.getPrimaryKey());
        if (sub == null) {
            return Collections.emptyList();
        }

        List<Object2LongMap.Entry<AEKey>> matches = new ArrayList<>();
        for (Object2LongMap.Entry<AEKey> entry : sub.object2LongEntrySet()) {
            if (key.fuzzyEquals(entry.getKey(), fuzzy)) {
                matches.add(entry);
            }
        }
        return matches;
    }

    @Nullable
    public AEKey getFirstKey() {
        Object2LongMap.Entry<AEKey> entry = this.getFirstEntry();
        return entry == null ? null : entry.getKey();
    }

    @Nullable
    public <T extends AEKey> T getFirstKey(Class<T> keyClass) {
        for (Object2LongMap.Entry<AEKey> entry : this) {
            if (keyClass.isInstance(entry.getKey())) {
                return keyClass.cast(entry.getKey());
            }
        }
        return null;
    }

    @Nullable
    public Object2LongMap.Entry<AEKey> getFirstEntry() {
        Iterator<Object2LongMap.Entry<AEKey>> it = this.iterator();
        return it.hasNext() ? it.next() : null;
    }

    @Nullable
    public <T extends AEKey> Object2LongMap.Entry<AEKey> getFirstEntry(Class<T> keyClass) {
        for (Object2LongMap.Entry<AEKey> entry : this) {
            if (keyClass.isInstance(entry.getKey())) {
                return entry;
            }
        }
        return null;
    }

    @Override
    public Iterator<Object2LongMap.Entry<AEKey>> iterator() {
        List<Object2LongMap.Entry<AEKey>> all = new ArrayList<>(this.size());
        for (Object2LongMap<AEKey> sub : this.byPrimary.values()) {
            all.addAll(sub.object2LongEntrySet());
        }
        return all.iterator();
    }

    private Object2LongMap<AEKey> subMap(AEKey key) {
        return this.byPrimary.computeIfAbsent(key.getPrimaryKey(), k -> new Object2LongOpenHashMap<>());
    }
}
