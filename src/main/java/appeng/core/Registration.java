/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
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

package appeng.core;


import appeng.api.AEApi;
import appeng.api.upgrades.CardTrait;
import appeng.api.upgrades.CardTraits;
import appeng.api.upgrades.IUpgradeRegistry;
import appeng.api.upgrades.UpgradeCards;
import appeng.api.definitions.IBlocks;
import appeng.api.definitions.IItemDefinition;
import appeng.api.definitions.IMaterials;
import appeng.api.definitions.IItems;
import appeng.api.definitions.IParts;
import appeng.api.features.IRecipeHandlerRegistry;
import appeng.api.features.IRegistryContainer;
import appeng.api.features.IWirelessTermHandler;
import appeng.api.features.IWirelessTerminalMode;
import appeng.api.features.IWirelessTerminalModeRegistry;
import appeng.api.networking.pathing.ChannelTiers;
import appeng.api.networking.pathing.IChannelTierRegistry;
import appeng.api.features.IWorldGen.WorldGenType;
import appeng.api.implementations.items.IItemGroup;
import appeng.api.movable.IMovableRegistry;
import appeng.api.networking.IGridCacheRegistry;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.pathing.IPathingGrid;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.networking.spatial.ISpatialCache;
import appeng.api.networking.storage.IStorageService;
import appeng.api.networking.ticking.ITickManager;
import appeng.bootstrap.ICriterionTriggerRegistry;
import appeng.bootstrap.IModelRegistry;
import appeng.bootstrap.components.*;
import appeng.capabilities.Capabilities;
import appeng.core.features.AEFeature;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.StorageCells;
import appeng.core.api.AEFluidKeyType;
import appeng.core.api.AEItemKeyType;
import appeng.core.features.registries.P2PTunnelRegistry;
import appeng.core.features.registries.WirelessTerminalMode;
import appeng.core.features.registries.cell.BasicCellHandler;
import appeng.core.features.registries.cell.CreativeCellHandler;
import appeng.parts.automation.InitStackWorldBehaviors;
import appeng.parts.misc.InitExternalStorageStrategies;
import appeng.core.localization.GuiText;
import appeng.core.sync.ChannelTierSync;
import appeng.core.sync.GuiBridge;
import appeng.core.localization.PlayerMessages;
import appeng.core.stats.AdvancementTriggers;
import appeng.core.stats.PartItemPredicate;
import appeng.core.stats.Stats;
import appeng.core.worlddata.SpatialDimensionManager;
import appeng.hooks.ItemSpawnCapture;
import appeng.hooks.TickHandler;
import appeng.hooks.WrenchClickHook;
import appeng.items.materials.ItemMaterial;
import appeng.items.parts.ItemFacade;
import appeng.items.parts.ItemPart;
import appeng.items.tools.powered.PortableCellPickup;
import appeng.loot.CheckTally;
import appeng.loot.ChestLoot;
import appeng.loot.FeatureEnabled;
import appeng.loot.Tally;
import appeng.loot.ToRandomOre;
import appeng.me.cache.*;
import appeng.recipes.AEItemResolver;
import appeng.recipes.AERecipeLoader;
import appeng.recipes.game.DisassembleRecipe;
import appeng.recipes.game.WirelessTerminalModeRecipe;
import appeng.recipes.game.FacadeRecipe;
import appeng.recipes.ores.OreDictionaryHandler;
import appeng.spatial.BiomeGenStorage;
import appeng.spatial.StorageWorldProvider;
import appeng.tile.AEBaseTile;
import appeng.util.Platform;
import appeng.worldgen.MeteoriteWorldGen;
import appeng.worldgen.meteorite.MeteorConstants;
import appeng.worldgen.meteorite.heightmap.HeightMapAccessors;
import appeng.worldgen.QuartzWorldGen;
import com.google.common.base.Preconditions;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.ICriterionInstance;
import net.minecraft.advancements.ICriterionTrigger;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.ItemMeshDefinition;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.IStateMapper;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.storage.loot.LootTableList;
import net.minecraft.world.storage.loot.conditions.LootConditionManager;
import net.minecraft.world.storage.loot.functions.LootFunctionManager;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.DimensionType;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryBuilder;

import javax.annotation.Nonnull;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


final class Registration {
    DimensionType storageDimensionType;
    int storageDimensionID;
    Biome storageBiome;

    /** Kept so the placer can reach the areas whose drops it is throwing away. */
    MeteoriteWorldGen meteoriteGen;
    AdvancementTriggers advancementTriggers;

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void addUpgradeCardTooltip(final ItemTooltipEvent event) {
        final ItemStack card = event.getItemStack();
        final IUpgradeRegistry upgrades = Api.INSTANCE.registries().upgrades();
        if (!upgrades.isUpgradeCard(card)) {
            return;
        }

        final List<String> tooltip = event.getToolTip();
        for (final Map.Entry<CardTrait, Integer> trait : upgrades.getTraits(card).entrySet()) {
            // A trait a card either has or has not says nothing here: the hosts printed below already do.
            final String key = trait.getKey().getTooltipKey();
            if (key != null) {
                tooltip.add(TextFormatting.GRAY + I18n.format(key, trait.getValue()));
            }
        }

        final Map<ItemStack, Integer> supported = upgrades.getSupportedObjects(card);
        final List<String> names = new ArrayList<>();
        for (final Map.Entry<ItemStack, Integer> entry : supported.entrySet()) {
            final ItemStack host = entry.getKey();
            final int limit = entry.getValue();
            String name = null;
            if (host.getItem() instanceof IItemGroup) {
                final String groupName = ((IItemGroup) host.getItem())
                        .getUnlocalizedGroupName(supported.keySet(), host);
                if (groupName != null) {
                    name = Platform.gui_localize(groupName);
                }
            }
            if (name == null) {
                name = host.getDisplayName();
            }
            if (limit > 1) {
                name += " (" + limit + ')';
            }
            if (!names.contains(name)) {
                names.add(name);
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        tooltip.addAll(names);
    }

    void preInitialize(final FMLPreInitializationEvent event) {
        Capabilities.register();

        final Api api = Api.INSTANCE;
        final IRecipeHandlerRegistry recipeRegistry = api.registries().recipes();
        this.registerCraftHandlers(recipeRegistry);

        MinecraftForge.EVENT_BUS.register(OreDictionaryHandler.INSTANCE);

        ApiDefinitions definitions = api.definitions();

        this.registerChannelTiers(api.registries().channelTiers());

        this.registerWirelessTerminalModes(api.registries().wirelessTerminalModes(), definitions);

        // Register
        definitions.getRegistry().getBootstrapComponents(IPreInitComponent.class).forEachRemaining(b -> b.preInitialize(event.getSide()));
    }

    /**
     * Here rather than later because each tier reads its number out of the config as it is registered, and
     * the file is written once pre-initialisation is over.
     */
    private void registerChannelTiers(final IChannelTierRegistry registry) {
        registry.register(ChannelTiers.NONE, 0);
        registry.register(ChannelTiers.NORMAL, 8);
        registry.register(ChannelTiers.DENSE, 32);
        registry.register(ChannelTiers.CONTROLLER, -1);
        registry.register(ChannelTiers.QUANTUM_BRIDGE, 32);
        registry.register(ChannelTiers.P2P_ME_TUNNEL, -1);
    }

    /**
     * Here rather than later because the recipe that unlocks a mode and the key that opens it are both built
     * from this registry afterwards, and neither event comes round twice.
     */
    private void registerWirelessTerminalModes(final IWirelessTerminalModeRegistry registry,
            final ApiDefinitions definitions) {
        if (!definitions.items().wirelessTerminal().isEnabled()) {
            return;
        }

        final IParts parts = definitions.parts();

        // The plain terminal goes in first: whatever leads is what a terminal falls back to.
        this.registerTerminalMode(registry, AEFeature.WIRELESS_ACCESS_TERMINAL, WirelessTerminalMode.Ids.TERMINAL,
                parts.terminal(), GuiText.WirelessModeTerminal, GuiBridge.GUI_WIRELESS_TERM);
        this.registerTerminalMode(registry, AEFeature.WIRELESS_CRAFTING_TERMINAL, WirelessTerminalMode.Ids.CRAFTING,
                parts.craftingTerminal(), GuiText.WirelessModeCrafting, GuiBridge.GUI_WIRELESS_CRAFTING_TERMINAL);
        this.registerTerminalMode(registry, AEFeature.WIRELESS_PATTERN_TERMINAL, WirelessTerminalMode.Ids.PATTERN,
                parts.patternTerminal(), GuiText.WirelessModePattern, GuiBridge.GUI_WIRELESS_PATTERN_TERMINAL);
        this.registerTerminalMode(registry, AEFeature.WIRELESS_PATTERN_ACCESS_TERMINAL, WirelessTerminalMode.Ids.PATTERN_ACCESS,
                parts.patternAccessTerminal(), GuiText.WirelessModePatternAccess, GuiBridge.GUI_WIRELESS_PATTERN_ACCESS_TERMINAL);

        // No switch of its own: this one never existed as a wireless item, so there is no setting that used to
        // turn it off and none is invented here.
        this.registerTerminalMode(registry, null, WirelessTerminalMode.Ids.INTERFACE_CONFIGURATION,
                parts.interfaceConfigurationTerminal(), GuiText.WirelessModeInterfaceConfig,
                GuiBridge.GUI_WIRELESS_INTERFACE_CONFIGURATION_TERMINAL);
    }

    /**
     * A mode exists only while both halves of it do: the setting that used to remove the wireless item, and the
     * panel it is the wireless form of. Turning either off used to leave the other half reachable - the recipe
     * for the item went, but the mode could still be crafted into a terminal and still had a key of its own.
     */
    private void registerTerminalMode(final IWirelessTerminalModeRegistry registry, final AEFeature feature,
            final ResourceLocation id, final IItemDefinition part, final GuiText name, final GuiBridge gui) {
        if (feature != null && !AEConfig.instance().isFeatureEnabled(feature)) {
            return;
        }

        if (!part.isEnabled()) {
            return;
        }

        registry.register(new WirelessTerminalMode(id, part, name.getUnlocalized(), gui));
    }

    private void registerSpatialBiome(IForgeRegistry<Biome> registry) {
        if (!AEConfig.instance().isFeatureEnabled(AEFeature.SPATIAL_IO)) {
            return;
        }

        if (this.storageBiome == null) {
            this.storageBiome = new BiomeGenStorage();
        }
        registry.register(this.storageBiome.setRegistryName("appliedenergistics2:storage_biome"));
    }

    private void registerSpatialDimension() {
        final AEConfig config = AEConfig.instance();
        if (!config.isFeatureEnabled(AEFeature.SPATIAL_IO)) {
            return;
        }

        if (config.getStorageProviderID() == -1) {
            final Set<Integer> ids = new HashSet<>();
            for (DimensionType type : DimensionType.values()) {
                ids.add(type.getId());
            }

            int newId = -11;
            while (ids.contains(newId)) {
                --newId;
            }
            config.setStorageProviderID(newId);
            config.save();
        }

        this.storageDimensionType = DimensionType.register("Storage Cell", "_cell", config.getStorageProviderID(), StorageWorldProvider.class, true);

        if (config.getStorageDimensionID() == -1) {
            config.setStorageDimensionID(DimensionManager.getNextFreeDimId());
            config.save();
        }
        this.storageDimensionID = config.getStorageDimensionID();

        DimensionManager.registerDimension(this.storageDimensionID, this.storageDimensionType);
    }

    private void registerCraftHandlers(final IRecipeHandlerRegistry registry) {
        registry.addNewSubItemResolver(new AEItemResolver());
    }

    public void initialize(@Nonnull final FMLInitializationEvent event, @Nonnull final File recipeDirectory) {
        Preconditions.checkNotNull(event);
        Preconditions.checkNotNull(recipeDirectory);
        Preconditions.checkArgument(!recipeDirectory.isFile());

        final Api api = Api.INSTANCE;
        final IRegistryContainer registries = api.registries();

        ApiDefinitions definitions = api.definitions();
        definitions.getRegistry().getBootstrapComponents(IInitComponent.class).forEachRemaining(b -> b.initialize(event.getSide()));

        MinecraftForge.EVENT_BUS.register(TickHandler.INSTANCE);

        MinecraftForge.EVENT_BUS.register(new ChannelTierSync());

        MinecraftForge.EVENT_BUS.register(new WrenchClickHook());

        MinecraftForge.EVENT_BUS.register(ItemSpawnCapture.INSTANCE);

        if (AEConfig.instance().isFeatureEnabled(AEFeature.PORTABLE_CELL)) {
            MinecraftForge.EVENT_BUS.register(new PortableCellPickup());
        }

        // The conditions and functions AE2's own loot tables are written with. Registered whatever else is
        // switched on: one nothing uses costs nothing, while registering one twice throws.
        LootConditionManager.registerCondition(new CheckTally.Serializer());
        LootConditionManager.registerCondition(new FeatureEnabled.Serializer());
        LootFunctionManager.registerFunction(new Tally.Serializer());
        LootFunctionManager.registerFunction(new ToRandomOre.Serializer());

        // Certus in mineshaft chests. Gated on certus itself: without it the table has nothing to give.
        if (AEConfig.instance().isFeatureEnabled(AEFeature.CHEST_LOOT)
                && AEConfig.instance().isFeatureEnabled(AEFeature.CERTUS)) {
            LootTableList.register(new ResourceLocation(AppEng.MOD_ID, ChestLoot.MINESHAFT_INJECT_TABLE));

            MinecraftForge.EVENT_BUS.register(new ChestLoot());
        }

        // The meteorite chest. Sky stone is checked as well as the generation: a table naming a block
        // that does not exist would fail to load, and a meteorite without sky stone is nothing anyway.
        if (AEConfig.instance().isFeatureEnabled(AEFeature.METEORITE_WORLD_GEN)
                && AEConfig.instance().isFeatureEnabled(AEFeature.SKY_STONE)) {
            LootTableList.register(new ResourceLocation(AppEng.MOD_ID, MeteorConstants.METEOR_LOOT_TABLE));
        }

        final IGridCacheRegistry gcr = registries.gridCache();
        gcr.registerGridCache(ITickManager.class, TickManagerCache.class);
        gcr.registerGridCache(IEnergyGrid.class, EnergyGridCache.class);
        gcr.registerGridCache(IPathingGrid.class, PathGridCache.class);
        gcr.registerGridCache(IStorageService.class, GridStorageCache.class);
        gcr.registerGridCache(P2PCache.class, P2PCache.class);
        gcr.registerGridCache(ISpatialCache.class, SpatialPylonCache.class);
        gcr.registerGridCache(ISecurityGrid.class, SecurityCache.class);
        gcr.registerGridCache(ICraftingGrid.class, CraftingGridCache.class);

        StorageCells.addCellHandler(new BasicCellHandler());
        StorageCells.addCellHandler(new CreativeCellHandler());

        // Install the GenericStack.Wrapper implementation (CONTRACT.md §8 item 3, §8.1 item 2) before anything
        // below - or anything running later during init - can call a non-item key's wrapForDisplayOrFilter().
        definitions.items().wrappedGenericStack().maybeItem()
                .ifPresent(item -> GenericStack.setWrapper((GenericStack.Wrapper) item));

        // Wave 3: register the item strategies for the world-interaction (import/export/placement/pickup) and
        // external-storage behaviour layers (CONTRACT.md §3). Must run after the AEKeyType registry is populated,
        // which happens in newRegistry() (RegistryEvent.NewRegistry) - long before this FMLInitializationEvent.
        InitStackWorldBehaviors.register();
        InitExternalStorageStrategies.register();

        api.definitions().materials().matterBall().maybeStack(1).ifPresent(ammoStack ->
        {
            final double weight = 32;

            registries.matterCannon().registerAmmo(ammoStack, weight);
        });

        PartItemPredicate.register();
        Stats.register();
        this.advancementTriggers = new AdvancementTriggers(new CriterionTrigggerRegistry());
    }

    /**
     * Creates the {@link AEKeyType} Forge registry and registers the two built-in types under it, before any item
     * registration happens (CONTRACT.md §1.3). {@link RegistryEvent.NewRegistry} fires once, before every
     * {@code RegistryEvent.Register<T>} (including {@link Item}'s), so both the registry's existence and its
     * built-in entries are guaranteed to be in place in time.
     */
    @SubscribeEvent
    public void newRegistry(RegistryEvent.NewRegistry event) {
        final IForgeRegistry<AEKeyType> registry = new RegistryBuilder<AEKeyType>()
                .setName(AEKeyTypes.REGISTRY_NAME)
                .setIDRange(0, Integer.MAX_VALUE - 1)
                .setType(AEKeyType.class)
                .create();

        // NewRegistry fires with no mod of its own, so a registry name of ours reads as an override of
        // Minecraft's and FML warns about a broken mod. Say who we are while the two names are set.
        final Loader loader = Loader.instance();
        final ModContainer opener = loader.activeModContainer();
        loader.setActiveModContainer(loader.getIndexedModList().get(AppEng.MOD_ID));
        try {
            registry.register(new AEItemKeyType());
            registry.register(new AEFluidKeyType());
        } finally {
            loader.setActiveModContainer(opener);
        }
    }

    @SubscribeEvent
    public void registerBiomes(RegistryEvent.Register<Biome> event) {
        final IForgeRegistry<Biome> registry = event.getRegistry();
        this.registerSpatialBiome(registry);
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void modelRegistryEvent(ModelRegistryEvent event) {
        final ApiDefinitions definitions = Api.INSTANCE.definitions();
        final IModelRegistry registry = new ModelLoaderWrapper();
        final Side side = FMLCommonHandler.instance().getEffectiveSide();
        definitions.getRegistry().getBootstrapComponents(IModelRegistrationComponent.class).forEachRemaining(b -> b.modelRegistration(side, registry));
    }

    @SubscribeEvent
    public void registerBlocks(RegistryEvent.Register<Block> event) {
        final IForgeRegistry<Block> registry = event.getRegistry();
        final ApiDefinitions definitions = Api.INSTANCE.definitions();
        final Side side = FMLCommonHandler.instance().getEffectiveSide();
        definitions.getRegistry().getBootstrapComponents(IBlockRegistrationComponent.class).forEachRemaining(b -> b.blockRegistration(side, registry));
    }

    @SubscribeEvent
    public void registerItems(RegistryEvent.Register<Item> event) {
        final IForgeRegistry<Item> registry = event.getRegistry();
        final ApiDefinitions definitions = Api.INSTANCE.definitions();
        final Side side = FMLCommonHandler.instance().getEffectiveSide();
        definitions.getRegistry().getBootstrapComponents(IItemRegistrationComponent.class).forEachRemaining(b -> b.itemRegistration(side, registry));
        // register oredicts
        definitions.getRegistry().getBootstrapComponents(IOreDictComponent.class).forEachRemaining(b -> b.oreRegistration(side));
        ItemMaterial.instance.registerOredicts();
        ItemPart.instance.registerOreDicts();
    }

    @SubscribeEvent
    public void registerRecipes(RegistryEvent.Register<IRecipe> event) {
        final IForgeRegistry<IRecipe> registry = event.getRegistry();

        final Api api = Api.INSTANCE;
        final ApiDefinitions definitions = api.definitions();
        final Side side = FMLCommonHandler.instance().getEffectiveSide();

        if (AEConfig.instance().isFeatureEnabled(AEFeature.ENABLE_DISASSEMBLY_CRAFTING)) {
            DisassembleRecipe r = new DisassembleRecipe();
            registry.register(r.setRegistryName(AppEng.MOD_ID.toLowerCase(), "disassemble"));
        }

        if (AEConfig.instance().isFeatureEnabled(AEFeature.ENABLE_FACADE_CRAFTING)) {
            definitions.items().facade().maybeItem().ifPresent(facadeItem ->
            {
                FacadeRecipe f = new FacadeRecipe((ItemFacade) facadeItem);
                registry.register(f.setRegistryName(AppEng.MOD_ID.toLowerCase(), "facade"));
            });
        }

        definitions.getRegistry().getBootstrapComponents(IRecipeRegistrationComponent.class).forEachRemaining(b -> b.recipeRegistration(side, registry));

        // One per mode, and the only chance to make them: a mode registered after this event has nothing that
        // will unlock it.
        for (final IWirelessTerminalMode mode : api.registries().wirelessTerminalModes().getModes()) {
            if (mode.getUnlockIngredient().isEmpty()) {
                continue;
            }

            final WirelessTerminalModeRecipe recipe = new WirelessTerminalModeRecipe(mode);
            registry.register(recipe.setRegistryName(AppEng.MOD_ID.toLowerCase(),
                    "wireless_terminal_mode_" + mode.getId().getNamespace() + '_' + mode.getId().getPath()));
        }

        final AERecipeLoader ldr = new AERecipeLoader();
        ldr.loadProcessingRecipes();
    }

    @SubscribeEvent
    public void registerEntities(RegistryEvent.Register<EntityEntry> event) {
        final IForgeRegistry<EntityEntry> registry = event.getRegistry();
        final ApiDefinitions definitions = Api.INSTANCE.definitions();
        definitions.getRegistry().getBootstrapComponents(IEntityRegistrationComponent.class).forEachRemaining(b -> b.entityRegistration(registry));
    }

    @SubscribeEvent
    public void attachSpatialDimensionManager(AttachCapabilitiesEvent<World> event) {
        if (AEConfig.instance()
                .isFeatureEnabled(AEFeature.SPATIAL_IO) && event.getObject() == DimensionManager.getWorld(AEConfig.instance().getStorageDimensionID())) {
            event.addCapability(new ResourceLocation("appliedenergistics2:spatial_dimension_manager"), new SpatialDimensionManager(event.getObject()));
        }
    }

    private static List<IItemDefinition> portableFluidCells(final IItems items) {
        return Arrays.asList(items.portableFluidCell1k(), items.portableFluidCell4k(), items.portableFluidCell16k(),
                items.portableFluidCell64k(), items.portableFluidCell256k(), items.portableFluidCell1024k(),
                items.portableFluidCell4096k(), items.portableFluidCell16384k());
    }

    private static List<IItemDefinition> portableCells(final IItems items) {
        return Arrays.asList(items.portableCell(), items.portableCell4k(), items.portableCell16k(),
                items.portableCell64k(), items.portableCell256k(), items.portableCell1024k(),
                items.portableCell4096k(), items.portableCell16384k());
    }

    /**
     * Registers a host as taking a trait from any card carrying it, with how many of them fit read from the
     * config. Zero there means the host does not take the card at all, which the registry itself refuses to
     * express - hence the check rather than an argument.
     *
     * <p>Only a counted trait is asked about. A card that means the same whether one or three of it are in
     * has no number to tune, and there are twice as many of those as of the rest.</p>
     */
    private static void support(final IUpgradeRegistry upgrades, final CardTrait trait,
            final IItemDefinition host, final int cards) {
        final int configured = trait.isCounted() ? AEConfig.instance().getUpgradeCards(trait, host, cards)
                : cards;
        if (configured > 0) {
            upgrades.addTraitSupport(trait, host, configured);
        }
    }

    /**
     * Offers a cap on how many points of a trait a host will take, with none by default. AE2's own cards are
     * worth a point each and cannot get past the count that already limits them, so a cap says nothing until
     * an addon ships a card worth several - and whether that is too much for this pack is the pack's call.
     */
    private static void limit(final IUpgradeRegistry upgrades, final CardTrait trait,
            final IItemDefinition host) {
        final int configured = AEConfig.instance().getTraitLimit(trait, host, 0);
        if (configured > 0) {
            upgrades.setTraitLimit(trait, host, configured);
        }
    }

    private static void card(final IUpgradeRegistry upgrades, final ItemStack card, final CardTrait trait,
            final int points) {
        final int configured = AEConfig.instance().getCardPoints(trait, points);
        if (configured > 0) {
            upgrades.registerCard(card, trait, configured);
        }
    }

    /**
     * A card tied to one host by name rather than by what it confers. There is no trait to ask whether it
     * counts, so the number it was registered with answers instead: the two cards here that carry no trait
     * at all - the magnet and the quantum link - are one to a host and stay out of the config.
     */
    private static void exact(final IUpgradeRegistry upgrades, final IItemDefinition card,
            final IItemDefinition host, final int cards) {
        final int configured = cards > 1 ? AEConfig.instance().getUpgradeCards(card, host, cards) : cards;
        if (configured > 0) {
            card.maybeStack(1).ifPresent(stack -> upgrades.add(stack, host, configured));
        }
    }

    void postInit(final FMLPostInitializationEvent event) {
        final IRegistryContainer registries = Api.INSTANCE.registries();
        ApiDefinitions definitions = Api.INSTANCE.definitions();
        final IParts parts = definitions.parts();
        final IBlocks blocks = definitions.blocks();
        final IItems items = definitions.items();
        final IMaterials materials = definitions.materials();
        final IUpgradeRegistry upgrades = registries.upgrades();

        this.registerSpatialDimension();

        // default settings..
        ((P2PTunnelRegistry) registries.p2pTunnel()).configure();

        // add to localization..
        PlayerMessages.values();
        GuiText.values();

        definitions.getRegistry().getBootstrapComponents(IPostInitComponent.class).forEachRemaining(b -> b.postInitialize(event.getSide()));

        card(upgrades, UpgradeCards.speed(), CardTraits.SPEED, 1);
        card(upgrades, UpgradeCards.capacity(), CardTraits.CAPACITY, 1);

        card(upgrades, UpgradeCards.fuzzy(), CardTraits.FUZZY, 1);
        card(upgrades, UpgradeCards.inverter(), CardTraits.INVERTER, 1);
        card(upgrades, UpgradeCards.sticky(), CardTraits.STICKY, 1);
        card(upgrades, UpgradeCards.equalDistribution(), CardTraits.EQUAL_DISTRIBUTION, 1);
        card(upgrades, UpgradeCards.voidCard(), CardTraits.VOID, 1);
        card(upgrades, UpgradeCards.crafting(), CardTraits.CRAFTING, 1);
        card(upgrades, UpgradeCards.energy(), CardTraits.ENERGY, 1);
        card(upgrades, UpgradeCards.fakeCrafting(), CardTraits.FAKE_CRAFTING, 1);
        card(upgrades, UpgradeCards.redstone(), CardTraits.REDSTONE, 1);
        card(upgrades, UpgradeCards.patternExpansion(), CardTraits.PATTERN_EXPANSION, 1);

        // Interface
        support(upgrades, CardTraits.CRAFTING, parts.iface(), 1);
        support(upgrades, CardTraits.CRAFTING, blocks.iface(), 1);
        support(upgrades, CardTraits.FAKE_CRAFTING, parts.iface(), 1);
        support(upgrades, CardTraits.FAKE_CRAFTING, blocks.iface(), 1);
        support(upgrades, CardTraits.PATTERN_EXPANSION, parts.iface(), 3);
        support(upgrades, CardTraits.PATTERN_EXPANSION, blocks.iface(), 3);
        limit(upgrades, CardTraits.PATTERN_EXPANSION, parts.iface());
        limit(upgrades, CardTraits.PATTERN_EXPANSION, blocks.iface());

        // Fluid Interface

        // IO Port!
        support(upgrades, CardTraits.SPEED, blocks.iOPort(), 3);
        support(upgrades, CardTraits.REDSTONE, blocks.iOPort(), 1);

        // Level Emitter!
        support(upgrades, CardTraits.FUZZY, parts.levelEmitter(), 1);
        support(upgrades, CardTraits.CRAFTING, parts.levelEmitter(), 1);

        // Import Bus
        support(upgrades, CardTraits.FUZZY, parts.importBus(), 1);
        support(upgrades, CardTraits.INVERTER, parts.importBus(), 1);
        support(upgrades, CardTraits.REDSTONE, parts.importBus(), 1);
        support(upgrades, CardTraits.CAPACITY, parts.importBus(), 5);
        limit(upgrades, CardTraits.CAPACITY, parts.importBus());
        support(upgrades, CardTraits.SPEED, parts.importBus(), 4);

        // Fluid Import Bus

        // Export Bus
        support(upgrades, CardTraits.FUZZY, parts.exportBus(), 1);
        support(upgrades, CardTraits.REDSTONE, parts.exportBus(), 1);
        support(upgrades, CardTraits.CAPACITY, parts.exportBus(), 5);
        limit(upgrades, CardTraits.CAPACITY, parts.exportBus());
        support(upgrades, CardTraits.SPEED, parts.exportBus(), 4);
        support(upgrades, CardTraits.CRAFTING, parts.exportBus(), 1);

        // Fluid Export Bus

        // Storage Cells
        support(upgrades, CardTraits.FUZZY, items.cell1k(), 1);
        support(upgrades, CardTraits.INVERTER, items.cell1k(), 1);
        support(upgrades, CardTraits.STICKY, items.cell1k(), 1);
        support(upgrades, CardTraits.EQUAL_DISTRIBUTION, items.cell1k(), 1);
        support(upgrades, CardTraits.VOID, items.cell1k(), 1);

        support(upgrades, CardTraits.FUZZY, items.cell4k(), 1);
        support(upgrades, CardTraits.INVERTER, items.cell4k(), 1);
        support(upgrades, CardTraits.STICKY, items.cell4k(), 1);
        support(upgrades, CardTraits.EQUAL_DISTRIBUTION, items.cell4k(), 1);
        support(upgrades, CardTraits.VOID, items.cell4k(), 1);

        support(upgrades, CardTraits.FUZZY, items.cell16k(), 1);
        support(upgrades, CardTraits.INVERTER, items.cell16k(), 1);
        support(upgrades, CardTraits.STICKY, items.cell16k(), 1);
        support(upgrades, CardTraits.EQUAL_DISTRIBUTION, items.cell16k(), 1);
        support(upgrades, CardTraits.VOID, items.cell16k(), 1);

        support(upgrades, CardTraits.FUZZY, items.cell64k(), 1);
        support(upgrades, CardTraits.INVERTER, items.cell64k(), 1);
        support(upgrades, CardTraits.STICKY, items.cell64k(), 1);
        support(upgrades, CardTraits.EQUAL_DISTRIBUTION, items.cell64k(), 1);
        support(upgrades, CardTraits.VOID, items.cell64k(), 1);

        support(upgrades, CardTraits.FUZZY, items.cell256k(), 1);
        support(upgrades, CardTraits.INVERTER, items.cell256k(), 1);
        support(upgrades, CardTraits.STICKY, items.cell256k(), 1);
        support(upgrades, CardTraits.EQUAL_DISTRIBUTION, items.cell256k(), 1);
        support(upgrades, CardTraits.VOID, items.cell256k(), 1);

        support(upgrades, CardTraits.FUZZY, items.cell1024k(), 1);
        support(upgrades, CardTraits.INVERTER, items.cell1024k(), 1);
        support(upgrades, CardTraits.STICKY, items.cell1024k(), 1);
        support(upgrades, CardTraits.EQUAL_DISTRIBUTION, items.cell1024k(), 1);
        support(upgrades, CardTraits.VOID, items.cell1024k(), 1);

        support(upgrades, CardTraits.FUZZY, items.cell4096k(), 1);
        support(upgrades, CardTraits.INVERTER, items.cell4096k(), 1);
        support(upgrades, CardTraits.STICKY, items.cell4096k(), 1);
        support(upgrades, CardTraits.EQUAL_DISTRIBUTION, items.cell4096k(), 1);
        support(upgrades, CardTraits.VOID, items.cell4096k(), 1);

        support(upgrades, CardTraits.FUZZY, items.cell16384k(), 1);
        support(upgrades, CardTraits.INVERTER, items.cell16384k(), 1);
        support(upgrades, CardTraits.STICKY, items.cell16384k(), 1);
        support(upgrades, CardTraits.EQUAL_DISTRIBUTION, items.cell16384k(), 1);
        support(upgrades, CardTraits.VOID, items.cell16384k(), 1);

        for (final IItemDefinition portableCell : portableCells(items)) {
            support(upgrades, CardTraits.FUZZY, portableCell, 1);
            support(upgrades, CardTraits.INVERTER, portableCell, 1);
            support(upgrades, CardTraits.EQUAL_DISTRIBUTION, portableCell, 1);
            support(upgrades, CardTraits.ENERGY, portableCell, 2);
            limit(upgrades, CardTraits.ENERGY, portableCell);
            support(upgrades, CardTraits.VOID, portableCell, 1);
        }

        for (final IItemDefinition portableCell : portableFluidCells(items)) {
            support(upgrades, CardTraits.INVERTER, portableCell, 1);
            support(upgrades, CardTraits.EQUAL_DISTRIBUTION, portableCell, 1);
            support(upgrades, CardTraits.ENERGY, portableCell, 2);
            limit(upgrades, CardTraits.ENERGY, portableCell);
            support(upgrades, CardTraits.VOID, portableCell, 1);
        }

        support(upgrades, CardTraits.EQUAL_DISTRIBUTION, items.colorApplicator(), 1);
        support(upgrades, CardTraits.VOID, items.colorApplicator(), 1);
        support(upgrades, CardTraits.ENERGY, items.colorApplicator(), 2);
        limit(upgrades, CardTraits.ENERGY, items.colorApplicator());

        support(upgrades, CardTraits.FUZZY, items.viewCell(), 1);
        support(upgrades, CardTraits.INVERTER, items.viewCell(), 1);

        support(upgrades, CardTraits.FUZZY, materials.cardMagnet(), 1);
        support(upgrades, CardTraits.INVERTER, materials.cardMagnet(), 1);

        // Fluid Cells
        support(upgrades, CardTraits.INVERTER, items.fluidCell1k(), 1);
        support(upgrades, CardTraits.STICKY, items.fluidCell1k(), 1);
        support(upgrades, CardTraits.EQUAL_DISTRIBUTION, items.fluidCell1k(), 1);
        support(upgrades, CardTraits.VOID, items.fluidCell1k(), 1);

        support(upgrades, CardTraits.INVERTER, items.fluidCell4k(), 1);
        support(upgrades, CardTraits.STICKY, items.fluidCell4k(), 1);
        support(upgrades, CardTraits.EQUAL_DISTRIBUTION, items.fluidCell4k(), 1);
        support(upgrades, CardTraits.VOID, items.fluidCell4k(), 1);

        support(upgrades, CardTraits.INVERTER, items.fluidCell16k(), 1);
        support(upgrades, CardTraits.STICKY, items.fluidCell16k(), 1);
        support(upgrades, CardTraits.EQUAL_DISTRIBUTION, items.fluidCell16k(), 1);
        support(upgrades, CardTraits.VOID, items.fluidCell16k(), 1);

        support(upgrades, CardTraits.INVERTER, items.fluidCell64k(), 1);
        support(upgrades, CardTraits.STICKY, items.fluidCell64k(), 1);
        support(upgrades, CardTraits.EQUAL_DISTRIBUTION, items.fluidCell64k(), 1);
        support(upgrades, CardTraits.VOID, items.fluidCell64k(), 1);

        support(upgrades, CardTraits.INVERTER, items.fluidCell256k(), 1);
        support(upgrades, CardTraits.STICKY, items.fluidCell256k(), 1);
        support(upgrades, CardTraits.EQUAL_DISTRIBUTION, items.fluidCell256k(), 1);
        support(upgrades, CardTraits.VOID, items.fluidCell256k(), 1);

        support(upgrades, CardTraits.INVERTER, items.fluidCell1024k(), 1);
        support(upgrades, CardTraits.STICKY, items.fluidCell1024k(), 1);
        support(upgrades, CardTraits.EQUAL_DISTRIBUTION, items.fluidCell1024k(), 1);
        support(upgrades, CardTraits.VOID, items.fluidCell1024k(), 1);

        support(upgrades, CardTraits.INVERTER, items.fluidCell4096k(), 1);
        support(upgrades, CardTraits.STICKY, items.fluidCell4096k(), 1);
        support(upgrades, CardTraits.EQUAL_DISTRIBUTION, items.fluidCell4096k(), 1);
        support(upgrades, CardTraits.VOID, items.fluidCell4096k(), 1);

        support(upgrades, CardTraits.INVERTER, items.fluidCell16384k(), 1);
        support(upgrades, CardTraits.STICKY, items.fluidCell16384k(), 1);
        support(upgrades, CardTraits.EQUAL_DISTRIBUTION, items.fluidCell16384k(), 1);
        support(upgrades, CardTraits.VOID, items.fluidCell16384k(), 1);

        // Storage Bus
        support(upgrades, CardTraits.FUZZY, parts.storageBus(), 1);
        support(upgrades, CardTraits.INVERTER, parts.storageBus(), 1);
        support(upgrades, CardTraits.CAPACITY, parts.storageBus(), 5);
        limit(upgrades, CardTraits.CAPACITY, parts.storageBus());
        support(upgrades, CardTraits.STICKY, parts.storageBus(), 1);

        // OreDict Storage Bus
        support(upgrades, CardTraits.STICKY, parts.oreDictStorageBus(), 1);

        // Storage Bus Fluids

        // Annihilation Plane
        support(upgrades, CardTraits.FUZZY, parts.annihilationPlane(), 1);
        support(upgrades, CardTraits.INVERTER, parts.annihilationPlane(), 1);
        support(upgrades, CardTraits.CAPACITY, parts.annihilationPlane(), 5);
        limit(upgrades, CardTraits.CAPACITY, parts.annihilationPlane());

        // Formation Plane
        support(upgrades, CardTraits.FUZZY, parts.formationPlane(), 1);
        support(upgrades, CardTraits.INVERTER, parts.formationPlane(), 1);
        support(upgrades, CardTraits.CAPACITY, parts.formationPlane(), 5);
        limit(upgrades, CardTraits.CAPACITY, parts.formationPlane());
        support(upgrades, CardTraits.REDSTONE, parts.formationPlane(), 1);
        support(upgrades, CardTraits.CRAFTING, parts.formationPlane(), 1);
        support(upgrades, CardTraits.SPEED, parts.formationPlane(), 4);

        // Matter Cannon
        support(upgrades, CardTraits.FUZZY, items.massCannon(), 1);
        support(upgrades, CardTraits.INVERTER, items.massCannon(), 1);
        exact(upgrades, materials.cardSpeed(), items.massCannon(), 4);
        support(upgrades, CardTraits.ENERGY, items.massCannon(), 2);
        limit(upgrades, CardTraits.ENERGY, items.massCannon());

        // Molecular Assembler
        support(upgrades, CardTraits.SPEED, blocks.molecularAssembler(), 5);

        // Inscriber
        support(upgrades, CardTraits.SPEED, blocks.inscriber(), 3);

        // Vibration Chamber
        support(upgrades, CardTraits.ENERGY, blocks.vibrationChamber(), 3);
        limit(upgrades, CardTraits.ENERGY, blocks.vibrationChamber());

        exact(upgrades, materials.cardQuantumLink(), blocks.quantumLink(), 1);

        // Wireless Terminal Handler. The three left over from before there was one terminal are handlers too,
        // so one that has not been converted yet still opens and still holds its charge.
        ArrayList<IItemDefinition> iids = new ArrayList<>();
        iids.add(items.wirelessTerminal());
        iids.add(definitions.items().wirelessCraftingTerminal());
        iids.add(definitions.items().wirelessPatternTerminal());
        iids.add(definitions.items().wirelessInterfaceTerminal());

        for (IItemDefinition id : iids) {
            id.maybeItem().ifPresent(terminal -> registries.wireless().registerWirelessHandler((IWirelessTermHandler) terminal));
        }

        exact(upgrades, materials.cardMagnet(), items.wirelessTerminal(), 1);
        support(upgrades, CardTraits.ENERGY, items.wirelessTerminal(), 2);
        limit(upgrades, CardTraits.ENERGY, items.wirelessTerminal());

        // Charge Rates
        items.chargedStaff().maybeItem().ifPresent(chargedStaff -> registries.charger().addChargeRate(chargedStaff, 320d));
        for (final IItemDefinition portableCell : portableCells(items)) {
            portableCell.maybeItem().ifPresent(cell -> registries.charger().addChargeRate(cell, 800d));
        }
        for (final IItemDefinition portableCell : portableFluidCells(items)) {
            portableCell.maybeItem().ifPresent(cell -> registries.charger().addChargeRate(cell, 800d));
        }
        items.colorApplicator().maybeItem().ifPresent(colorApplicator -> registries.charger().addChargeRate(colorApplicator, 800d));
        items.wirelessTerminal().maybeItem().ifPresent(terminal -> registries.charger().addChargeRate(terminal, 8000d));
        items.entropyManipulator().maybeItem().ifPresent(entropyManipulator -> registries.charger().addChargeRate(entropyManipulator, 8000d));
        items.massCannon().maybeItem().ifPresent(massCannon -> registries.charger().addChargeRate(massCannon, 8000d));
        blocks.energyCell().maybeItem().ifPresent(cell -> registries.charger().addChargeRate(cell, 8000d));
        blocks.energyCellDense().maybeItem().ifPresent(cell -> registries.charger().addChargeRate(cell, 16000d));

        // add villager trading to black smiths for a few basic materials
        if (AEConfig.instance().isFeatureEnabled(AEFeature.VILLAGER_TRADING)) {
            // TODO: VILLAGER TRADING
            // VillagerRegistry.instance().getRegisteredVillagers().registerVillageTradeHandler( 3, new AETrading() );
        }

        if (AEConfig.instance().isFeatureEnabled(AEFeature.CERTUS_QUARTZ_WORLD_GEN)) {
            GameRegistry.registerWorldGenerator(new QuartzWorldGen(), 0);
        }

        if (AEConfig.instance().isFeatureEnabled(AEFeature.METEORITE_WORLD_GEN)) {
            this.meteoriteGen = new MeteoriteWorldGen();
            GameRegistry.registerWorldGenerator(this.meteoriteGen,
                    AEConfig.instance().getMeteoriteGeneratorPriority());
            this.meteoriteGen.registerStructure();

            MinecraftForge.EVENT_BUS.register(this.meteoriteGen);
            MinecraftForge.TERRAIN_GEN_BUS.register(HeightMapAccessors.class);
            MinecraftForge.EVENT_BUS.register(HeightMapAccessors.class);
        }

        final IMovableRegistry mr = registries.movable();

        /*
         * You can't move bed rock.
         */
        mr.blacklistBlock(net.minecraft.init.Blocks.BEDROCK);

        /*
         * White List Vanilla...
         */
        mr.whiteListTileEntity(net.minecraft.tileentity.TileEntityBanner.class);
        mr.whiteListTileEntity(net.minecraft.tileentity.TileEntityBeacon.class);
        mr.whiteListTileEntity(net.minecraft.tileentity.TileEntityBrewingStand.class);
        mr.whiteListTileEntity(net.minecraft.tileentity.TileEntityChest.class);
        mr.whiteListTileEntity(net.minecraft.tileentity.TileEntityCommandBlock.class);
        mr.whiteListTileEntity(net.minecraft.tileentity.TileEntityComparator.class);
        mr.whiteListTileEntity(net.minecraft.tileentity.TileEntityDaylightDetector.class);
        mr.whiteListTileEntity(net.minecraft.tileentity.TileEntityDispenser.class);
        mr.whiteListTileEntity(net.minecraft.tileentity.TileEntityDropper.class);
        mr.whiteListTileEntity(net.minecraft.tileentity.TileEntityEnchantmentTable.class);
        mr.whiteListTileEntity(net.minecraft.tileentity.TileEntityEnderChest.class);
        mr.whiteListTileEntity(net.minecraft.tileentity.TileEntityEndPortal.class);
        mr.whiteListTileEntity(net.minecraft.tileentity.TileEntityFlowerPot.class);
        mr.whiteListTileEntity(net.minecraft.tileentity.TileEntityFurnace.class);
        mr.whiteListTileEntity(net.minecraft.tileentity.TileEntityHopper.class);
        mr.whiteListTileEntity(net.minecraft.tileentity.TileEntityMobSpawner.class);
        mr.whiteListTileEntity(net.minecraft.tileentity.TileEntityNote.class);
        mr.whiteListTileEntity(net.minecraft.tileentity.TileEntityPiston.class);
        mr.whiteListTileEntity(net.minecraft.tileentity.TileEntityShulkerBox.class);
        mr.whiteListTileEntity(net.minecraft.tileentity.TileEntitySign.class);
        mr.whiteListTileEntity(net.minecraft.tileentity.TileEntitySkull.class);

        /*
         * Whitelist AE2
         */
        mr.whiteListTileEntity(AEBaseTile.class);

        /*
         * world gen
         */
        for (final WorldGenType type : WorldGenType.values()) {
            registries.worldgen().disableWorldGenForProviderID(type, StorageWorldProvider.class);

            // nether
            registries.worldgen().disableWorldGenForDimension(type, -1);

            // end
            registries.worldgen().disableWorldGenForDimension(type, 1);
        }

        // whitelist from config
        for (final int dimension : AEConfig.instance().getMeteoriteDimensionWhitelist()) {
            registries.worldgen().enableWorldGenForDimension(WorldGenType.METEORITES, dimension);
        }
    }

    private static class ModelLoaderWrapper implements IModelRegistry {

        @Override
        public void registerItemVariants(Item item, ResourceLocation... names) {
            ModelLoader.registerItemVariants(item, names);
        }

        @Override
        public void setCustomModelResourceLocation(Item item, int metadata, ModelResourceLocation model) {
            ModelLoader.setCustomModelResourceLocation(item, metadata, model);
        }

        @Override
        public void setCustomMeshDefinition(Item item, ItemMeshDefinition meshDefinition) {
            ModelLoader.setCustomMeshDefinition(item, meshDefinition);
        }

        @Override
        public void setCustomStateMapper(Block block, IStateMapper mapper) {
            ModelLoader.setCustomStateMapper(block, mapper);
        }
    }

    private static class CriterionTrigggerRegistry implements ICriterionTriggerRegistry {
        private final Method method;

        CriterionTrigggerRegistry() {
            this.method = ReflectionHelper.findMethod(CriteriaTriggers.class, "register", "func_192118_a", ICriterionTrigger.class);
            this.method.setAccessible(true);
        }

        @Override
        public void register(ICriterionTrigger<? extends ICriterionInstance> trigger) {
            try {
                this.method.invoke(null, trigger);
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                AELog.debug(e);
            }
        }

    }

}
