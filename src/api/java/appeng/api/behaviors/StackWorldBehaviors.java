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

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import appeng.api.stacks.AEKeyType;
import appeng.api.storage.AEKeyFilter;

/**
 * Registry of "how do I talk to the world about this key type", one entry per {@link AEKeyType} per
 * kind of interaction.
 * <p>
 * Registering a key type only declares that a type exists. It is these strategies that let the type
 * actually connect to anything: without an {@link ExternalStorageStrategy} a storage bus has nothing
 * to read, without a {@link PlacementStrategy} a formation plane has nothing to place.
 * <p>
 * Lives in the API source set rather than next to the parts, because {@code src/api} is compiled
 * independently of {@code src/main} and the strategy interfaces' static {@code register} methods
 * have to reach it.
 */
public final class StackWorldBehaviors {

    private static final Map<AEKeyType, StackImportStrategy.Factory> importStrategies = new LinkedHashMap<>();
    private static final Map<AEKeyType, StackExportStrategy.Factory> exportStrategies = new LinkedHashMap<>();
    private static final Map<AEKeyType, ExternalStorageStrategy.Factory> externalStorageStrategies = new LinkedHashMap<>();
    private static final Map<AEKeyType, PlacementStrategy.Factory> placementStrategies = new LinkedHashMap<>();
    private static final Map<AEKeyType, PickupStrategy.Factory> pickupStrategies = new LinkedHashMap<>();

    private StackWorldBehaviors() {
    }

    public static void registerImportStrategy(AEKeyType type, StackImportStrategy.Factory factory) {
        importStrategies.putIfAbsent(type, factory);
    }

    public static void registerExportStrategy(AEKeyType type, StackExportStrategy.Factory factory) {
        exportStrategies.putIfAbsent(type, factory);
    }

    public static void registerExternalStorageStrategy(AEKeyType type, ExternalStorageStrategy.Factory factory) {
        externalStorageStrategies.putIfAbsent(type, factory);
    }

    public static void registerPlacementStrategy(AEKeyType type, PlacementStrategy.Factory factory) {
        placementStrategies.putIfAbsent(type, factory);
    }

    public static void registerPickupStrategy(AEKeyType type, PickupStrategy.Factory factory) {
        pickupStrategies.putIfAbsent(type, factory);
    }

    public static AEKeyFilter hasImportStrategyFilter() {
        return what -> importStrategies.containsKey(what.getType());
    }

    public static Predicate<AEKeyType> hasImportStrategyTypeFilter() {
        return importStrategies::containsKey;
    }

    public static AEKeyFilter hasExportStrategyFilter() {
        return what -> exportStrategies.containsKey(what.getType());
    }

    public static AEKeyFilter hasPlacementStrategy() {
        return what -> placementStrategies.containsKey(what.getType());
    }

    public static Set<AEKeyType> withImportStrategy() {
        return Collections.unmodifiableSet(importStrategies.keySet());
    }

    public static Set<AEKeyType> withExportStrategy() {
        return Collections.unmodifiableSet(exportStrategies.keySet());
    }

    public static Set<AEKeyType> withPlacementStrategy() {
        return Collections.unmodifiableSet(placementStrategies.keySet());
    }

    /**
     * @return one strategy per registered type that passes {@code forTypes}. Composing them into a
     *         single facade is left to the caller, which lives in {@code src/main}.
     */
    public static List<StackImportStrategy> createImportStrategies(World world, BlockPos fromPos, EnumFacing fromSide,
            Predicate<AEKeyType> forTypes) {
        List<StackImportStrategy> strategies = new ArrayList<>(importStrategies.size());
        for (Map.Entry<AEKeyType, StackImportStrategy.Factory> entry : importStrategies.entrySet()) {
            if (forTypes.test(entry.getKey())) {
                strategies.add(entry.getValue().create(world, fromPos, fromSide));
            }
        }
        return strategies;
    }

    public static List<StackExportStrategy> createExportStrategies(World world, BlockPos fromPos, EnumFacing fromSide) {
        List<StackExportStrategy> strategies = new ArrayList<>(exportStrategies.size());
        for (StackExportStrategy.Factory factory : exportStrategies.values()) {
            strategies.add(factory.create(world, fromPos, fromSide));
        }
        return strategies;
    }

    /**
     * @return one wrapper per registered type. The storage bus mounts all of them at once, which is
     *         what makes a single bus serve every type simultaneously.
     */
    public static Map<AEKeyType, ExternalStorageStrategy> createExternalStorageStrategies(World world, BlockPos fromPos,
            EnumFacing fromSide) {
        Map<AEKeyType, ExternalStorageStrategy> strategies = new IdentityHashMap<>(externalStorageStrategies.size());
        for (Map.Entry<AEKeyType, ExternalStorageStrategy.Factory> entry : externalStorageStrategies.entrySet()) {
            strategies.put(entry.getKey(), entry.getValue().create(world, fromPos, fromSide));
        }
        return strategies;
    }

    public static Map<AEKeyType, PlacementStrategy> createPlacementStrategies(World world, BlockPos fromPos,
            EnumFacing fromSide, TileEntity host, @Nullable UUID owningPlayerId) {
        Map<AEKeyType, PlacementStrategy> strategies = new IdentityHashMap<>(placementStrategies.size());
        for (Map.Entry<AEKeyType, PlacementStrategy.Factory> entry : placementStrategies.entrySet()) {
            strategies.put(entry.getKey(), entry.getValue().create(world, fromPos, fromSide, host, owningPlayerId));
        }
        return strategies;
    }

    public static List<PickupStrategy> createPickupStrategies(World world, BlockPos fromPos, EnumFacing fromSide,
            TileEntity host, int fortuneLevel, boolean silkTouch, @Nullable UUID owningPlayerId) {
        List<PickupStrategy> strategies = new ArrayList<>(pickupStrategies.size());
        for (PickupStrategy.Factory factory : pickupStrategies.values()) {
            strategies.add(factory.create(world, fromPos, fromSide, host, fortuneLevel, silkTouch, owningPlayerId));
        }
        return strategies;
    }
}
