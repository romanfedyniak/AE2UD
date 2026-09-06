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

package appeng.integration.modules.jei;


import appeng.api.AEApi;
import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.implementations.items.IAEItemPowerStorage;
import appeng.core.AEClientConfig;
import appeng.items.tools.powered.ToolEntropyManipulator;
import appeng.util.InWorldToolOperationResult;
import appeng.api.config.CondenserOutput;
import appeng.api.definitions.IDefinitions;
import appeng.api.definitions.IItemDefinition;
import appeng.api.definitions.IMaterials;
import appeng.api.features.IGrinderRecipe;
import appeng.api.features.IInscriberRecipe;
import appeng.client.gui.AEGuiHandler;
import appeng.container.implementations.*;
import appeng.core.AEConfig;
import appeng.core.features.AEFeature;
import appeng.core.localization.GuiText;
import appeng.integration.Integrations;
import appeng.items.parts.ItemFacade;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import appeng.recipes.game.WirelessTerminalModeRecipe;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.IJeiRuntime;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.ISubtypeRegistry;
import mezz.jei.api.recipe.IRecipeCategoryRegistration;
import mezz.jei.api.recipe.VanillaRecipeCategoryUid;
import mezz.jei.config.Constants;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.Optional;


@mezz.jei.api.JEIPlugin
public class JEIPlugin implements IModPlugin {
    public static IJeiRuntime runtime;
    public static AEGuiHandler aeGuiHandler;

    // Kept because an entry has to know its category to draw itself: how far down its slots go
    // depends on how much that entry has to say, and only the category knows the panel.
    private ChargerCategory chargerCategory;
    private TransformCategory transformCategory;
    private CertusGrowthCategory growthCategory;
    private AttunementCategory attunementCategory;
    private EntropyCategory entropyCategory;

    @Override
    public void registerItemSubtypes(ISubtypeRegistry subtypeRegistry) {
        final Optional<Item> maybeFacade = AEApi.instance().definitions().items().facade().maybeItem();
        maybeFacade.ifPresent(subtypeRegistry::useNbtForSubtypes);
    }

    /** Display only: a category switched off is never registered, so it leaves no empty tab behind. */
    private static boolean shows(JeiCategory category) {
        return AEClientConfig.instance().shows(category);
    }

    private static ItemStack stackOf(IItemDefinition definition) {
        return definition.maybeStack(1).orElse(ItemStack.EMPTY);
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registry) {
        IGuiHelper helper = registry.getJeiHelpers().getGuiHelper();
        IDefinitions definitions = AEApi.instance().definitions();

        if (shows(JeiCategory.GRINDER)) {
            registry.addRecipeCategories(new GrinderRecipeCategory(helper));
        }
        if (shows(JeiCategory.CONDENSER)) {
            registry.addRecipeCategories(new CondenserCategory(helper));
        }
        if (shows(JeiCategory.INSCRIBER)) {
            registry.addRecipeCategories(new InscriberRecipeCategory(helper));
        }
        // Either half of the charger is reason enough for the tab; each entry is switched separately.
        if (shows(JeiCategory.CHARGER) || shows(JeiCategory.CHARGER_CHARGING)) {
            this.chargerCategory = new ChargerCategory(helper, stackOf(definitions.blocks().charger()));
            registry.addRecipeCategories(this.chargerCategory);
        }
        if (shows(JeiCategory.TRANSFORM)) {
            this.transformCategory = new TransformCategory(helper, stackOf(definitions.materials().fluixCrystal()));
            registry.addRecipeCategories(this.transformCategory);
        }
        if (shows(JeiCategory.CERTUS_GROWTH)) {
            this.growthCategory = new CertusGrowthCategory(helper, stackOf(definitions.blocks().quartzGrowthAccelerator()));
            registry.addRecipeCategories(this.growthCategory);
        }
        if (shows(JeiCategory.ATTUNEMENT)) {
            this.attunementCategory = new AttunementCategory(helper, stackOf(definitions.parts().p2PTunnelME()));
            registry.addRecipeCategories(this.attunementCategory);
        }
        if (shows(JeiCategory.ENTROPY)) {
            this.entropyCategory = new EntropyCategory(helper, stackOf(definitions.items().entropyManipulator()));
            registry.addRecipeCategories(this.entropyCategory);
        }
    }

    @Override
    public void register(IModRegistry registry) {
        IDefinitions definitions = AEApi.instance().definitions();

        this.registerFacadeRecipe(definitions, registry);

        this.registerInscriberRecipes(definitions, registry);

        this.registerCondenserRecipes(definitions, registry);

        this.registerGrinderRecipes(definitions, registry);

        this.registerChargerRecipes(definitions, registry);

        this.registerTransformRecipes(definitions, registry);

        this.registerGrowthRecipes(definitions, registry);

        this.registerAttunement(registry);

        this.registerEntropyRecipes(definitions, registry);

        this.registerDescriptions(definitions, registry);

        // Allow recipe transfer from JEI to crafting and pattern terminal
        registry.getRecipeTransferRegistry().addRecipeTransferHandler(new RecipeTransferHandler<>(ContainerCraftingTerm.class), VanillaRecipeCategoryUid.CRAFTING);
        registry.getRecipeTransferRegistry().addRecipeTransferHandler(new RecipeTransferHandler<>(ContainerWirelessCraftingTerminal.class), VanillaRecipeCategoryUid.CRAFTING);
        registry.getRecipeTransferRegistry().addRecipeTransferHandler(new RecipeTransferHandler<>(ContainerPatternTerm.class), Constants.UNIVERSAL_RECIPE_TRANSFER_UID);
        registry.getRecipeTransferRegistry().addRecipeTransferHandler(new RecipeTransferHandler<>(ContainerWirelessPatternTerminal.class), Constants.UNIVERSAL_RECIPE_TRANSFER_UID);

        // HEI has no wrapper for a recipe class it does not know, and drops it rather than drawing it.
        registry.handleRecipes(WirelessTerminalModeRecipe.class, WirelessTerminalModeRecipeWrapper::new,
                VanillaRecipeCategoryUid.CRAFTING);

        aeGuiHandler = new AEGuiHandler();
        registry.addAdvancedGuiHandlers(aeGuiHandler);
        registry.addGhostIngredientHandler(aeGuiHandler.getGuiContainerClass(), aeGuiHandler);
        registry.addSlotIngredientProvider(aeGuiHandler.getGuiContainerClass(), aeGuiHandler);
    }

    /**
     * What is left for plain text once the categories draw the rest: where a thing is <em>found</em>. A
     * category can show a recipe; it cannot say that charged quartz turns up in a cave, or that a press
     * lies at the centre of a meteorite.
     */
    private void registerDescriptions(IDefinitions definitions, IModRegistry registry) {
        IMaterials materials = definitions.materials();

        if (AEConfig.instance().isFeatureEnabled(AEFeature.CERTUS_QUARTZ_WORLD_GEN)) {
            this.addDescription(registry, materials.certusQuartzCrystalCharged(), GuiText.ChargedQuartzFind.getLocal());
        }

        if (AEConfig.instance().isFeatureEnabled(AEFeature.METEORITE_WORLD_GEN)) {
            this.addDescription(registry, materials.logicProcessorPress(), GuiText.inWorldCraftingPresses.getLocal());
            this.addDescription(registry, materials.calcProcessorPress(), GuiText.inWorldCraftingPresses.getLocal());
            this.addDescription(registry, materials.engProcessorPress(), GuiText.inWorldCraftingPresses.getLocal());
        }
    }

    private void addDescription(IModRegistry registry, IItemDefinition itemDefinition, String message) {
        itemDefinition.maybeStack(1).ifPresent(itemStack -> registry.addIngredientInfo(itemStack, ItemStack.class, message));
    }

    private void registerChargerRecipes(IDefinitions definitions, IModRegistry registry) {
        ItemStack charger = stackOf(definitions.blocks().charger());
        if (charger.isEmpty()) {
            return;
        }

        List<InWorldRecipe> recipes = new ArrayList<>(2);

        if (shows(JeiCategory.CHARGER)) {
            ItemStack charged = stackOf(definitions.materials().certusQuartzCrystalCharged());
            List<ItemStack> certus = OreDictionary.getOres("crystalCertusQuartz");

            // Whatever the ore dictionary calls certus quartz, which is what the charger itself accepts.
            if (!charged.isEmpty() && !certus.isEmpty()) {
                recipes.add(this.chargerCategory.recipe(certus, ImmutableList.of(charged), null));
            }
        }

        if (shows(JeiCategory.CHARGER_CHARGING)) {
            List<ItemStack> empty = new ArrayList<>();
            List<ItemStack> full = new ArrayList<>();

            for (Item item : ForgeRegistries.ITEMS) {
                if (!(item instanceof IAEItemPowerStorage)) {
                    continue;
                }

                IAEItemPowerStorage powered = (IAEItemPowerStorage) item;
                ItemStack drained = new ItemStack(item);
                if (powered.getPowerFlow(drained) == AccessRestriction.READ) {
                    continue;
                }

                ItemStack charged = drained.copy();
                powered.injectAEPower(charged, powered.getAEMaxPower(charged), Actionable.MODULATE);
                empty.add(drained);
                full.add(charged);
            }

            // One entry rather than one per tool: they all say the same thing, and a slot that cycles is
            // still found by a search for any single one of them.
            if (!empty.isEmpty()) {
                recipes.add(this.chargerCategory.recipe(empty, full, GuiText.JeiCharging.getLocal()));
            }
        }

        if (!recipes.isEmpty()) {
            registry.addRecipes(recipes, ChargerCategory.UID);
            registry.addRecipeCatalyst(charger, ChargerCategory.UID);
        }
    }

    private void registerTransformRecipes(IDefinitions definitions, IModRegistry registry) {
        if (!shows(JeiCategory.TRANSFORM)) {
            return;
        }

        IMaterials materials = definitions.materials();
        List<InWorldRecipe> recipes = new ArrayList<>(2);

        if (AEConfig.instance().isFeatureEnabled(AEFeature.IN_WORLD_FLUIX)) {
            ItemStack fluix = stackOf(materials.fluixCrystal());
            ItemStack charged = stackOf(materials.certusQuartzCrystalCharged());

            if (!fluix.isEmpty() && !charged.isEmpty()) {
                fluix = fluix.copy();
                fluix.setCount(2);
                recipes.add(this.transformCategory.recipe(
                        ImmutableList.of(ImmutableList.of(charged), ImmutableList.of(new ItemStack(Items.QUARTZ)),
                                ImmutableList.of(new ItemStack(Items.REDSTONE))),
                        fluix, GuiText.JeiInLiquid.getLocal()));
            }
        }

        if (AEConfig.instance().isFeatureEnabled(AEFeature.IN_WORLD_SINGULARITY)) {
            ItemStack singularity = stackOf(materials.singularity());
            ItemStack entangled = stackOf(materials.qESingularity());
            List<ItemStack> enderDust = new ArrayList<>(OreDictionary.getOres("dustEnder"));
            enderDust.addAll(OreDictionary.getOres("dustEnderPearl"));

            if (!singularity.isEmpty() && !entangled.isEmpty() && !enderDust.isEmpty()) {
                entangled = entangled.copy();
                entangled.setCount(2);
                recipes.add(this.transformCategory.recipe(ImmutableList.of(ImmutableList.of(singularity), enderDust),
                        entangled, GuiText.JeiOnExplosion.getLocal()));
            }
        }

        if (!recipes.isEmpty()) {
            registry.addRecipes(recipes, TransformCategory.UID);
        }
    }

    private void registerGrowthRecipes(IDefinitions definitions, IModRegistry registry) {
        if (!shows(JeiCategory.CERTUS_GROWTH)
                || !AEConfig.instance().isFeatureEnabled(AEFeature.IN_WORLD_PURIFICATION)) {
            return;
        }

        Optional<Item> maybeSeed = definitions.items().crystalSeed().maybeItem();
        if (!maybeSeed.isPresent()) {
            return;
        }

        // A seed carries how far along it is in its damage, so its own sub-items are the stages to show.
        NonNullList<ItemStack> seeds = NonNullList.create();
        maybeSeed.get().getSubItems(CreativeTabs.SEARCH, seeds);

        Map<String, List<ItemStack>> stages = new LinkedHashMap<>();
        for (ItemStack seed : seeds) {
            stages.computeIfAbsent(seed.getItem().getTranslationKey(seed), key -> new ArrayList<>()).add(seed);
        }

        IMaterials materials = definitions.materials();
        Map<String, ItemStack> grown = new LinkedHashMap<>();
        grown.put("item.appliedenergistics2.crystal_seed.certus", stackOf(materials.purifiedCertusQuartzCrystal()));
        grown.put("item.appliedenergistics2.crystal_seed.nether", stackOf(materials.purifiedNetherQuartzCrystal()));
        grown.put("item.appliedenergistics2.crystal_seed.fluix", stackOf(materials.purifiedFluixCrystal()));

        ItemStack accelerator = stackOf(definitions.blocks().quartzGrowthAccelerator());
        List<InWorldRecipe> recipes = new ArrayList<>(grown.size());

        for (Map.Entry<String, ItemStack> entry : grown.entrySet()) {
            List<ItemStack> chain = stages.get(entry.getKey());

            if (chain != null && !chain.isEmpty() && !entry.getValue().isEmpty()) {
                recipes.add(this.growthCategory.recipe(chain, entry.getValue(), accelerator));
            }
        }

        if (!recipes.isEmpty()) {
            registry.addRecipes(recipes, CertusGrowthCategory.UID);

            if (!accelerator.isEmpty()) {
                registry.addRecipeCatalyst(accelerator, CertusGrowthCategory.UID);
            }
        }
    }

    private void registerEntropyRecipes(IDefinitions definitions, IModRegistry registry) {
        if (!shows(JeiCategory.ENTROPY)) {
            return;
        }

        ItemStack tool = stackOf(definitions.items().entropyManipulator());
        if (tool.isEmpty() || !(tool.getItem() instanceof ToolEntropyManipulator)) {
            return;
        }

        ToolEntropyManipulator manipulator = (ToolEntropyManipulator) tool.getItem();
        List<InWorldRecipe> recipes = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (boolean heat : new boolean[] { true, false }) {
            for (Map.Entry<IBlockState, InWorldToolOperationResult> entry : manipulator.getOperations(heat).entrySet()) {
                IBlockState from = entry.getKey();
                InWorldToolOperationResult to = entry.getValue();

                ItemStack inItem = itemFor(from);
                FluidStack inFluid = fluidFor(from);
                if (inItem.isEmpty() && inFluid == null) {
                    continue;
                }

                IBlockState result = to.getBlockState();
                List<ItemStack> outItems = new ArrayList<>();
                FluidStack outFluid = result == null ? null : fluidFor(result);

                if (result != null && outFluid == null) {
                    ItemStack out = itemFor(result);
                    if (!out.isEmpty()) {
                        outItems.add(out);
                    }
                }
                if (to.getDrops() != null) {
                    outItems.addAll(to.getDrops());
                }

                // The still and flowing forms of a liquid are separate rows of the same table, and they
                // draw as the same fluid; without this the list shows water twice.
                String key = heat + "|" + describe(inItem, inFluid) + ">" + describe(
                        outItems.isEmpty() ? ItemStack.EMPTY : outItems.get(0), outFluid);
                if (!seen.add(key)) {
                    continue;
                }

                recipes.add(this.entropyCategory.recipe(inItem.isEmpty() ? null : inItem, inFluid, outItems, outFluid,
                        heat));
            }
        }

        if (!recipes.isEmpty()) {
            registry.addRecipes(recipes, EntropyCategory.UID);
            registry.addRecipeCatalyst(tool, EntropyCategory.UID);
        }
    }

    private static String describe(ItemStack stack, @Nullable FluidStack fluid) {
        if (fluid != null) {
            return fluid.getFluid().getName();
        }
        return stack.isEmpty() ? "" : stack.getItem().getRegistryName() + "@" + stack.getItemDamage();
    }

    /** The item a block state is held as, or empty where it has none - water and lava have none. */
    private static ItemStack itemFor(IBlockState state) {
        Item item = Item.getItemFromBlock(state.getBlock());
        return item == Items.AIR ? ItemStack.EMPTY
                : new ItemStack(item, 1, state.getBlock().damageDropped(state));
    }

    @Nullable
    private static FluidStack fluidFor(IBlockState state) {
        Block block = state.getBlock();

        if (block == Blocks.WATER || block == Blocks.FLOWING_WATER) {
            return new FluidStack(FluidRegistry.WATER, Fluid.BUCKET_VOLUME);
        }
        if (block == Blocks.LAVA || block == Blocks.FLOWING_LAVA) {
            return new FluidStack(FluidRegistry.LAVA, Fluid.BUCKET_VOLUME);
        }
        return null;
    }

    private void registerAttunement(IModRegistry registry) {
        if (shows(JeiCategory.ATTUNEMENT)) {
            registry.addRecipeRegistryPlugin(new AttunementRegistryPlugin(registry.getIngredientRegistry(), this.attunementCategory));
        }
    }

    private void registerGrinderRecipes(IDefinitions definitions, IModRegistry registry) {
        if (!shows(JeiCategory.GRINDER)) {
            return;
        }

        ItemStack grindstone = definitions.blocks().grindstone().maybeStack(1).orElse(ItemStack.EMPTY);

        if (grindstone.isEmpty()) {
            return;
        }

        registry.handleRecipes(IGrinderRecipe.class, new GrinderRecipeHandler(), GrinderRecipeCategory.UID);
        registry.addRecipes(Lists.newArrayList(AEApi.instance().registries().grinder().getRecipes()), GrinderRecipeCategory.UID);
        registry.addRecipeCatalyst(grindstone, GrinderRecipeCategory.UID);
    }

    private void registerCondenserRecipes(IDefinitions definitions, IModRegistry registry) {
        if (!shows(JeiCategory.CONDENSER)) {
            return;
        }

        ItemStack condenser = definitions.blocks().condenser().maybeStack(1).orElse(ItemStack.EMPTY);
        if (condenser.isEmpty()) {
            return;
        }

        ItemStack matterBall = definitions.materials().matterBall().maybeStack(1).orElse(ItemStack.EMPTY);
        if (!matterBall.isEmpty()) {
            registry.addRecipes(ImmutableList.of(CondenserOutput.MATTER_BALLS), CondenserCategory.UID);
        }

        ItemStack singularity = definitions.materials().singularity().maybeStack(1).orElse(ItemStack.EMPTY);
        if (!singularity.isEmpty()) {
            registry.addRecipes(ImmutableList.of(CondenserOutput.SINGULARITY), CondenserCategory.UID);
        }

        if (!matterBall.isEmpty() || !singularity.isEmpty()) {
            registry.addRecipeCatalyst(condenser, CondenserCategory.UID);
            registry.handleRecipes(CondenserOutput.class, new CondenserOutputHandler(registry.getJeiHelpers().getGuiHelper(), matterBall, singularity),
                    CondenserCategory.UID);
        }
    }

    private void registerInscriberRecipes(IDefinitions definitions, IModRegistry registry) {
        if (!shows(JeiCategory.INSCRIBER)) {
            return;
        }

        registry.handleRecipes(IInscriberRecipe.class, new InscriberRecipeHandler(), InscriberRecipeCategory.UID);

        // Register the inscriber as the crafting item for the inscription category
        definitions.blocks().inscriber().maybeStack(1).ifPresent(inscriber ->
        {
            registry.addRecipeCatalyst(inscriber, InscriberRecipeCategory.UID);
        });

        List<IInscriberRecipe> inscriberRecipes = new ArrayList<>(AEApi.instance().registries().inscriber().getRecipes());
        registry.addRecipes(inscriberRecipes, InscriberRecipeCategory.UID);
    }

    // Handle the generic crafting recipe for patterns in JEI
    private void registerFacadeRecipe(IDefinitions definitions, IModRegistry registry) {
        if (!shows(JeiCategory.FACADES)) {
            return;
        }

        Optional<Item> itemFacade = definitions.items().facade().maybeItem();
        Optional<ItemStack> cableAnchor = definitions.parts().cableAnchor().maybeStack(1);
        if (itemFacade.isPresent()) {
            var facade = (ItemFacade)itemFacade.get();
            if (cableAnchor.isPresent() && AEConfig.instance().isFeatureEnabled(AEFeature.ENABLE_FACADE_CRAFTING)){
                registry.addRecipeRegistryPlugin(new FacadeRegistryPlugin(facade, cableAnchor.get()));
            }

            // Hide facades from JEI/HEI except for the first found.
            var list = NonNullList.<ItemStack>create();
            facade.getSubItems(Objects.requireNonNull(facade.getCreativeTab()), list);
            list.stream().skip(1)
                    .forEach(registry.getJeiHelpers().getIngredientBlacklist()::addIngredientToBlacklist);
        }
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        JEIModule jeiModule = (JEIModule) Integrations.jei();
        jeiModule.setJei(new JeiRuntimeAdapter(jeiRuntime));
        runtime = jeiRuntime;
    }
}
