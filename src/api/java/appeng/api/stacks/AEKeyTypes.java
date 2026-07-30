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

import java.util.Collection;
import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.IForgeRegistry;

/**
 * Access to the {@link AEKeyType} registry.
 * <p>
 * The registry itself is a plain Forge registry, created during {@code RegistryEvent.NewRegistry}
 * before items are registered. Addons add their own type by subscribing to
 * {@code RegistryEvent.Register<AEKeyType>}, exactly as they would for blocks or items.
 */
public final class AEKeyTypes {

    /**
     * Registry name of the key type registry.
     */
    public static final ResourceLocation REGISTRY_NAME = new ResourceLocation("appliedenergistics2", "keytypes");

    /**
     * Registry name of the built-in item key type.
     */
    public static final ResourceLocation ITEMS_ID = new ResourceLocation("appliedenergistics2", "item");

    /**
     * Registry name of the built-in fluid key type.
     */
    public static final ResourceLocation FLUIDS_ID = new ResourceLocation("appliedenergistics2", "fluid");

    private static IForgeRegistry<AEKeyType> registry;
    private static AEKeyType itemsCache;
    private static AEKeyType fluidsCache;

    private AEKeyTypes() {
    }

    public static IForgeRegistry<AEKeyType> getRegistry() {
        IForgeRegistry<AEKeyType> reg = registry;
        if (reg == null) {
            reg = GameRegistry.findRegistry(AEKeyType.class);
            Objects.requireNonNull(reg, "The AEKeyType registry has not been created yet.");
            registry = reg;
        }
        return reg;
    }

    /**
     * Registers a key type outside of the registry event. Prefer subscribing to
     * {@code RegistryEvent.Register<AEKeyType>} where possible.
     */
    public static void register(AEKeyType keyType) {
        Objects.requireNonNull(keyType, "keyType");
        Objects.requireNonNull(keyType.getRegistryName(), "keyType has no registry name");
        getRegistry().register(keyType);
    }

    @Nullable
    public static AEKeyType get(ResourceLocation id) {
        return getRegistry().getValue(id);
    }

    public static Collection<AEKeyType> getAll() {
        return getRegistry().getValuesCollection();
    }

    /**
     * @return the numeric id Forge assigned to this type, usable in packets.
     */
    public static int getRawId(AEKeyType type) {
        return ((ForgeRegistry<AEKeyType>) getRegistry()).getID(type);
    }

    @Nullable
    public static AEKeyType fromRawId(int id) {
        return ((ForgeRegistry<AEKeyType>) getRegistry()).getValue(id);
    }

    public static AEKeyType items() {
        AEKeyType type = itemsCache;
        if (type == null) {
            type = get(ITEMS_ID);
            Objects.requireNonNull(type, "The built-in item key type is not registered yet.");
            itemsCache = type;
        }
        return type;
    }

    public static AEKeyType fluids() {
        AEKeyType type = fluidsCache;
        if (type == null) {
            type = get(FLUIDS_ID);
            Objects.requireNonNull(type, "The built-in fluid key type is not registered yet.");
            fluidsCache = type;
        }
        return type;
    }
}
