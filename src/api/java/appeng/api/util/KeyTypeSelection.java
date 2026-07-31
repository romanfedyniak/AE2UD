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

package appeng.api.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ResourceLocation;

import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;

/**
 * Which {@link AEKeyType}s a machine is willing to act on, for machines whose filter cannot say it.
 * <p>
 * An import bus with an empty filter takes everything the world next to it offers, which is only useful
 * while "everything" means items. Once fluids - and whatever an addon registers - go through the same
 * strategies, a bus needs a way to say which of them it wants. That is this: a set of enabled types, with
 * the guarantee that at least one is always on.
 * <p>
 * Construct it with the types the machine could possibly handle, which is usually the set that has a
 * strategy registered - see {@code StackWorldBehaviors}. Types outside that set are not merely disabled,
 * they are absent, and asking about them is a programming error.
 */
public class KeyTypeSelection {

    private static final String NBT_KEY = "enabledKeyTypes";

    private final Listener listener;
    private final Map<AEKeyType, Boolean> keyTypes = new LinkedHashMap<>();

    public KeyTypeSelection(final Runnable listener, final Predicate<AEKeyType> allowKeyType) {
        this(selection -> listener.run(), allowKeyType);
    }

    public KeyTypeSelection(final Listener listener, final Predicate<AEKeyType> allowKeyType) {
        this.listener = listener;
        for (final AEKeyType keyType : AEKeyTypes.getAll()) {
            if (allowKeyType.test(keyType)) {
                this.keyTypes.put(keyType, true);
            }
        }
    }

    public void setEnabled(final AEKeyType type, final boolean enabled) {
        if (!this.keyTypes.containsKey(type)) {
            throw new IllegalArgumentException("Key type " + type + " is not allowed.");
        }

        // Turning the last one off would leave a machine that cannot act at all.
        if (!enabled && this.enabledSet().size() <= 1) {
            return;
        }

        this.keyTypes.put(type, enabled);
        this.listener.onKeyTypeSelectionChanged(this);
    }

    public boolean isEnabled(final AEKeyType type) {
        if (!this.keyTypes.containsKey(type)) {
            throw new IllegalArgumentException("Key type " + type + " is not allowed.");
        }

        return this.keyTypes.get(type);
    }

    /**
     * Every type this selection covers, in registration order, mapped to whether it is on.
     */
    public Map<AEKeyType, Boolean> enabled() {
        return new LinkedHashMap<>(this.keyTypes);
    }

    public List<AEKeyType> enabledSet() {
        final List<AEKeyType> out = new ArrayList<>(this.keyTypes.size());
        for (final Map.Entry<AEKeyType, Boolean> entry : this.keyTypes.entrySet()) {
            if (entry.getValue()) {
                out.add(entry.getKey());
            }
        }
        return out;
    }

    public void setEnabledSet(final List<AEKeyType> selected) {
        for (final Map.Entry<AEKeyType, Boolean> entry : this.keyTypes.entrySet()) {
            entry.setValue(selected.contains(entry.getKey()));
        }
    }

    public Predicate<AEKeyType> enabledPredicate() {
        return keyType -> this.keyTypes.getOrDefault(keyType, Boolean.FALSE);
    }

    public void writeToNBT(final NBTTagCompound tag) {
        final NBTTagList list = new NBTTagList();
        for (final Map.Entry<AEKeyType, Boolean> entry : this.keyTypes.entrySet()) {
            if (entry.getValue()) {
                list.appendTag(new NBTTagString(entry.getKey().getRegistryName().toString()));
            }
        }
        tag.setTag(NBT_KEY, list);
    }

    public void readFromNBT(final NBTTagCompound tag) {
        // Absent, not empty: a machine saved before it had a selection acted on every type, and must
        // keep doing so. Only an explicit list narrows it.
        if (!tag.hasKey(NBT_KEY, 9)) {
            return;
        }

        for (final Map.Entry<AEKeyType, Boolean> entry : this.keyTypes.entrySet()) {
            entry.setValue(false);
        }

        final NBTTagList list = tag.getTagList(NBT_KEY, 8);
        for (int i = 0; i < list.tagCount(); i++) {
            final AEKeyType keyType = AEKeyTypes.get(new ResourceLocation(list.getStringTagAt(i)));
            if (keyType != null && this.keyTypes.containsKey(keyType)) {
                this.keyTypes.put(keyType, true);
            }
        }

        // Covers a bus saved before this existed, and a type that has since gone away with the mod
        // that registered it.
        if (this.enabledSet().isEmpty()) {
            for (final Map.Entry<AEKeyType, Boolean> entry : this.keyTypes.entrySet()) {
                entry.setValue(true);
                break;
            }
        }
    }

    @FunctionalInterface
    public interface Listener {
        void onKeyTypeSelectionChanged(KeyTypeSelection keyTypeSelection);
    }
}
