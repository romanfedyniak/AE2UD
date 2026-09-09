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


import appeng.api.config.*;
import appeng.core.features.AEFeature;
import appeng.core.settings.TickRates;
import appeng.items.materials.MaterialType;
import com.google.common.collect.Sets;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.common.ModContainer;

import appeng.api.definitions.IItemDefinition;
import appeng.api.upgrades.CardTrait;

import java.io.File;
import java.util.*;
import java.util.stream.Stream;


public final class AEConfig extends Configuration {

    public static final String VERSION = appeng.Tags.VERSION;
    public static final String PACKET_CHANNEL = "AE";
    // Tunnels
    public static final double TUNNEL_POWER_LOSS = 0.05;
    // Default Grindstone ores
    private static final String[] ORES_VANILLA = {"Obsidian", "Ender", "EnderPearl", "Coal", "Iron", "Gold", "Charcoal", "NetherQuartz"};
    private static final String[] ORES_AE = {"CertusQuartz", "Wheat", "Fluix"};
    private static final String[] ORES_COMMON = {"Copper", "Tin", "Silver", "Lead", "Bronze"};
    private static final String[] ORES_MISC = {"Brass", "Platinum", "Nickel", "Invar", "Aluminium", "Electrum", "Osmium", "Zinc"};
    // Default Energy Conversion Rates
    private static final double DEFAULT_IC2_EXCHANGE = 2.0;
    private static final double DEFAULT_GTEU_EXCHANGE = 2.0;
    private static final double DEFAULT_RF_EXCHANGE = 0.5;
    // Config instance
    private static AEConfig instance;

    private final EnumSet<AEFeature> featureFlags = EnumSet.noneOf(AEFeature.class);
    private final File configFile;
    private final Set<String> grinderBlackList;
    private final int chargedChange = 4;
    private final double wirelessHighWirelessCount = 64;
    private String[] nonBlockingItems = {"[gregtech|actuallyadditions]", "gregtech:circuit.integrated", "gregtech:shape.mold.plate", "gregtech:shape.mold.gear", "gregtech:shape.mold.credit", "gregtech:shape.mold.bottle", "gregtech:shape.mold.ingot", "gregtech:shape.mold.ball", "gregtech:shape.mold.block", "gregtech:shape.mold.nugget", "gregtech:shape.mold.cylinder", "gregtech:shape.mold.anvil", "gregtech:shape.mold.name", "gregtech:shape.mold.gear.small", "gregtech:shape.mold.rotor", "gregtech:shape.extruder.plate", "gregtech:shape.extruder.rod", "gregtech:shape.extruder.bolt", "gregtech:shape.extruder.ring", "gregtech:shape.extruder.cell", "gregtech:shape.extruder.ingot", "gregtech:shape.extruder.wire", "gregtech:shape.extruder.pipe.tiny", "gregtech:shape.extruder.pipe.small", "gregtech:shape.extruder.pipe.medium", "gregtech:shape.extruder.pipe.normal", "gregtech:shape.extruder.pipe.large", "gregtech:shape.extruder.pipe.huge", "gregtech:shape.extruder.block", "gregtech:shape.extruder.sword", "gregtech:shape.extruder.pickaxe", "gregtech:shape.extruder.shovel", "gregtech:shape.extruder.axe", "gregtech:shape.extruder.hoe", "gregtech:shape.extruder.hammer", "gregtech:shape.extruder.file", "gregtech:shape.extruder.saw", "gregtech:shape.extruder.gear", "gregtech:shape.extruder.bottle", "gregtech:shape.extruder.foil", "gregtech:shape.extruder.gear_small", "gregtech:shape.extruder.rod_long", "gregtech:shape.extruder.rotor", "gregtech:glass_lens.white", "gregtech:glass_lens.orange", "gregtech:glass_lens.magenta", "gregtech:glass_lens.light_blue", "gregtech:glass_lens.yellow", "gregtech:glass_lens.lime", "gregtech:glass_lens.pink", "gregtech:glass_lens.gray", "gregtech:glass_lens.light_gray", "gregtech:glass_lens.cyan", "gregtech:glass_lens.purple", "gregtech:glass_lens.blue", "gregtech:glass_lens.brown", "gregtech:glass_lens.green", "gregtech:glass_lens.red", "gregtech:glass_lens.black", "contenttweaker:smallgearextrudershape", "contenttweaker:creativeportabletankmold", "ore:lensAlmandine", "ore:lensBlueTopaz", "ore:lensDiamond", "ore:lensEmerald", "ore:lensGreenSapphire", "ore:lensRutile", "ore:lensRuby", "ore:lensSapphire", "ore:lensTopaz", "ore:lensJasper", "ore:lensGlass", "ore:lensOlivine", "ore:lensOpal", "ore:lensAmethyst", "ore:lensLapis", "ore:lensEnderPearl", "ore:lensEnderEye", "ore:lensGarnetRed", "ore:lensGarnetYellow", "ore:lensVinteum", "ore:lensNetherStar",};
    private boolean updatable = false;
    // Misc
    private boolean removeCrashingItemsOnLoad = false;
    private boolean auditNetworkStorage = false;
    private boolean monitorThroughput = true;
    private int adHocNetworkChannels = 8;
    private int p2pTunnelChannelCost = 1;
    private int formationPlaneEntityLimit = 128;
    private int visualiserMaxNodes = 16384;
    private int visualiserUpdateInterval = 20;
    private int craftingCalculationTimePerTick = 5;
    private boolean craftingCPURequiresSingleChunk = false;
    private int craftingCPUMaxSizeX = 17;
    private int craftingCPUMaxSizeY = 17;
    private int craftingCPUMaxSizeZ = 17;
    private double crystalResonanceGeneratorRate = 20.0;

    // Energy card. What one point of it is worth is each host's own business, so each names its own.
    private double energyCardPortableCell = 8.0;
    private double energyCardMatterCannon = 8.0;
    private double energyCardColorApplicator = 8.0;
    private double energyCardWirelessTerminal = 1.0;
    private double energyCardVibrationChamber = 0.5;

    // Spatial IO/Dimension
    private int storageProviderID = -1;
    private int storageDimensionID = -1;
    private double spatialPowerExponent = 1.35;
    private double spatialPowerMultiplier = 1250.0;
    // Grindstone
    private String[] grinderOres = Stream.of(ORES_VANILLA, ORES_AE, ORES_COMMON, ORES_MISC).flatMap(Stream::of).toArray(String[]::new);
    private double oreDoublePercentage = 90.0;
    // Batteries
    private int wirelessTerminalBattery = 1600000;
    private int entropyManipulatorBattery = 200000;
    private int matterCannonBattery = 200000;
    private int portableCellBattery = 20000;
    private int colorApplicatorBattery = 20000;
    private int chargedStaffBattery = 8000;
    // Certus quartz
    private float spawnChargedChance = 0.92f;
    private int quartzOresPerCluster = 4;
    private int quartzOresClusterAmount = 15;
    // Meteors
    private int minMeteoriteDistance = 707;
    private double meteoriteClusterChance = 0.1;
    private int meteoriteMaximumSpawnHeight = 180;
    private int[] meteoriteDimensionWhitelist = {0};
    private int meteoriteGeneratorPriority = 0;
    // Wireless
    private double wirelessBaseCost = 8;
    private double wirelessCostMultiplier = 1;
    private double wirelessTerminalDrainMultiplier = 1;
    private double wirelessBaseRange = 16;
    private double wirelessBoosterRangeMultiplier = 1;
    private double wirelessBoosterExp = 1.5;
    // Autocrafting
    private boolean enableCraftingSubstitutes = false;
    // Controller sizes
    private int maxControllerSizeX = 7;
    private int maxControllerSizeY = 7;
    private int maxControllerSizeZ = 7;

    private AEConfig(final File configFile) {
        super(configFile);
        this.configFile = configFile;

        PowerUnits.EU.conversionRatio = this.get("PowerRatios", "IC2", DEFAULT_IC2_EXCHANGE).getDouble(DEFAULT_IC2_EXCHANGE);
        PowerUnits.RF.conversionRatio = this.get("PowerRatios", "ForgeEnergy", DEFAULT_RF_EXCHANGE).getDouble(DEFAULT_RF_EXCHANGE);
        PowerUnits.GTEU.conversionRatio = this.get("PowerRatios", "GTEU", DEFAULT_GTEU_EXCHANGE).getDouble(DEFAULT_GTEU_EXCHANGE);

        final double usageEffective = this.get("PowerRatios", "UsageMultiplier", 1.0).getDouble(1.0);
        PowerMultiplier.CONFIG.multiplier = Math.max(0.01, usageEffective);

        CondenserOutput.MATTER_BALLS.requiredPower = this.get("Condenser", "MatterBalls", 256).getInt(256);
        CondenserOutput.SINGULARITY.requiredPower = this.get("Condenser", "Singularity", 256000).getInt(256000);

        this.removeCrashingItemsOnLoad = this.get("general", "removeCrashingItemsOnLoad", false, "Will auto-remove items that crash when being loaded from storage. This will destroy those items instead of crashing the game!").getBoolean();
        this.auditNetworkStorage = this.get("general", "auditNetworkStorage", false, "For addon authors: once a second, count every network's contents the slow way and log anything that disagrees with the running total. Names the storage that is not reporting its changes. Costs real time on a large network; leave it off unless you are chasing a wrong count in a terminal.").getBoolean();
        this.monitorThroughput = this.get("general", "monitorThroughput", true, "Whether monitors can be switched to show how much of what they watch is moving. The network keeps the two directions of a change apart for every key a monitor is metering, which costs a lookup per change; nothing at all is done while no monitor is metering.").getBoolean();
        this.visualiserMaxNodes = this.get("general", "visualiserMaxNodes", this.visualiserMaxNodes, "How many blocks the network visualiser draws at most. The walk starts at the block it is bound to and stops here, so what is drawn is always the part of the network around that block.", 64, 1000000).getInt(this.visualiserMaxNodes);
        this.visualiserUpdateInterval = this.get("general", "visualiserUpdateInterval", this.visualiserUpdateInterval, "How many ticks apart a network is walked for the visualisers pointed at it. One walk serves everyone watching that network; a network nobody is watching is never walked.", 1, 200).getInt(this.visualiserUpdateInterval);
        this.adHocNetworkChannels = this.get("general", "adHocNetworkChannels", this.adHocNetworkChannels, "How many channels a network with no controller carries. Asking for more than this leaves it with none at all, rather than with this many.").getInt(this.adHocNetworkChannels);
        this.p2pTunnelChannelCost = Math.max(0, this.get("general", "p2pTunnelChannelCost", this.p2pTunnelChannelCost, "How many channels an ME P2P tunnel takes from the network it sits in. What it carries through is a separate matter, decided by the cables at either end.").getInt(this.p2pTunnelChannelCost));
        this.setCategoryComment("ChannelTiers", "How many channels each kind of node carries. 0 carries nothing, -1 imposes no limit of its own and lets whatever is on either side decide. Addons add their own lines here.");

        this.setCategoryComment("BlockingMode", "Map of items to not block when blockingmode is enabled.\n[modid]\nmodid:item:metadata(optional,default:0)\nSupports more than one modid, so you can block different things between, for example, gregtech or enderio");
        this.nonBlockingItems = this.get("BlockingMode", "nonBlockingItems", nonBlockingItems, "NonBlockingItems").getStringList();

        this.setCategoryComment("GrindStone", "Creates recipe of the following pattern automatically: '1 oreTYPE => 2 dustTYPE' and '(1 ingotTYPE or 1 crystalTYPE or 1 gemTYPE) => 1 dustTYPE'");
        this.grinderOres = this.get("GrindStone", "grinderOres", this.grinderOres, "The list of types to handle. Specify without a prefix like ore or dust.").getStringList();
        this.grinderBlackList = Sets.newHashSet(this.get("GrindStone", "blacklist", new String[]{}, "Blacklists the exact oredict name from being handled by any recipe.").getStringList());
        this.oreDoublePercentage = this.get("GrindStone", "oreDoublePercentage", this.oreDoublePercentage, "Chance to actually get an output with stacksize > 1.").getDouble(this.oreDoublePercentage);

        this.spawnChargedChance = (float) (1.0 - this.get("worldGen", "spawnChargedChance", 1.0 - this.spawnChargedChance).getDouble(1.0 - this.spawnChargedChance));
        this.minMeteoriteDistance = this.get("worldGen", "minMeteoriteDistance", this.minMeteoriteDistance,
                "The side of the square each meteorite is placed in, in blocks - so it sets how many there are."
                        + " Two in neighbouring squares can end up as close as a fifth of it, so it is not a"
                        + " guaranteed distance.").getInt(this.minMeteoriteDistance);
        this.meteoriteClusterChance = this.get("worldGen", "meteoriteClusterChance", this.meteoriteClusterChance,
                "The chance a meteorite has another one beside it, rolled again for each up to three."
                        + " A cluster lands closer together than the distance above, which is the point of it.")
                .getDouble(this.meteoriteClusterChance);
        this.meteoriteMaximumSpawnHeight = this.get("worldGen", "meteoriteMaximumSpawnHeight", this.meteoriteMaximumSpawnHeight).getInt(this.meteoriteMaximumSpawnHeight);
        this.meteoriteDimensionWhitelist = this.get("worldGen", "meteoriteDimensionWhitelist", this.meteoriteDimensionWhitelist).getIntList();
        this.meteoriteGeneratorPriority = this.get("worldGen", "meteoriteGeneratorPriority",
                this.meteoriteGeneratorPriority,
                "Where meteorites sit among the world generators of other mods. Lower runs earlier.")
                .getInt(this.meteoriteGeneratorPriority);

        this.quartzOresPerCluster = this.get("worldGen", "quartzOresPerCluster", this.quartzOresPerCluster).getInt(this.quartzOresPerCluster);
        this.quartzOresClusterAmount = this.get("worldGen", "quartzOresClusterAmount", this.quartzOresClusterAmount).getInt(this.quartzOresClusterAmount);


        this.addCustomCategoryComment("wireless", "Range= wirelessBaseRange + wirelessBoosterRangeMultiplier * Math.pow( boosters, wirelessBoosterExp )\nPowerDrain= wirelessBaseCost + wirelessCostMultiplier * Math.pow( boosters, 1 + boosters / wirelessHighWirelessCount )");

        this.wirelessBaseCost = this.get("wireless", "wirelessBaseCost", this.wirelessBaseCost).getDouble(this.wirelessBaseCost);
        this.wirelessCostMultiplier = this.get("wireless", "wirelessCostMultiplier", this.wirelessCostMultiplier).getDouble(this.wirelessCostMultiplier);
        this.wirelessBaseRange = this.get("wireless", "wirelessBaseRange", this.wirelessBaseRange).getDouble(this.wirelessBaseRange);
        this.wirelessBoosterRangeMultiplier = this.get("wireless", "wirelessBoosterRangeMultiplier", this.wirelessBoosterRangeMultiplier).getDouble(this.wirelessBoosterRangeMultiplier);
        this.wirelessBoosterExp = this.get("wireless", "wirelessBoosterExp", this.wirelessBoosterExp).getDouble(this.wirelessBoosterExp);
        this.wirelessTerminalDrainMultiplier = this.get("wireless", "wirelessTerminalDrainMultiplier", this.wirelessTerminalDrainMultiplier).getDouble(this.wirelessTerminalDrainMultiplier);

        this.formationPlaneEntityLimit = this.get("automation", "formationPlaneEntityLimit", this.formationPlaneEntityLimit).getInt(this.formationPlaneEntityLimit);

        this.wirelessTerminalBattery = this.get("battery", "wirelessTerminal", this.wirelessTerminalBattery).getInt(this.wirelessTerminalBattery);
        this.chargedStaffBattery = this.get("battery", "chargedStaff", this.chargedStaffBattery).getInt(this.chargedStaffBattery);
        this.entropyManipulatorBattery = this.get("battery", "entropyManipulator", this.entropyManipulatorBattery).getInt(this.entropyManipulatorBattery);
        this.portableCellBattery = this.get("battery", "portableCell", this.portableCellBattery).getInt(this.portableCellBattery);
        this.colorApplicatorBattery = this.get("battery", "colorApplicator", this.colorApplicatorBattery).getInt(this.colorApplicatorBattery);
        this.matterCannonBattery = this.get("battery", "matterCannon", this.matterCannonBattery).getInt(this.matterCannonBattery);

        this.addCustomCategoryComment("autocrafting", "Enable patterns with substitutions on to have their substitutes to be auto craftable.\nThis changes the crafting tree, and can show missing ingredients for the substitute, instead of the patterned item");
        this.enableCraftingSubstitutes = this.get("autocrafting", "EnableAutocraftinSubstitutes", this.enableCraftingSubstitutes).getBoolean(this.enableCraftingSubstitutes);

        this.addCustomCategoryComment("ControllerSize", "Set the max size of a controller in any of the 3 axis.\nEach is between [1, 64)");
        this.maxControllerSizeX = Math.min(Math.max(this.get("ControllerSize", "maxControllerSizeX", this.maxControllerSizeX).getInt(this.maxControllerSizeX), 1), 63);
        this.maxControllerSizeY = Math.min(Math.max(this.get("ControllerSize", "maxControllerSizeY", this.maxControllerSizeY).getInt(this.maxControllerSizeY), 1), 63);
        this.maxControllerSizeZ = Math.min(Math.max(this.get("ControllerSize", "maxControllerSizeZ", this.maxControllerSizeZ).getInt(this.maxControllerSizeZ), 1), 63);


        this.addCustomCategoryComment("features", "Warning: Disabling a feature may disable other features depending on it.");

        // The Interface Terminal became the Pattern Access Terminal, and its one switch was split in two -
        // it had been turning off the Interface Configuration Terminal beside it as well.
        this.inheritFormerFeatureKey("InterfaceTerminal", AEFeature.PATTERN_ACCESS_TERMINAL,
                AEFeature.INTERFACE_CONFIGURATION_TERMINAL);
        this.inheritFormerFeatureKey("WirelessInterfaceTerminal", AEFeature.WIRELESS_PATTERN_ACCESS_TERMINAL);

        for (final AEFeature feature : AEFeature.values()) {
            if (feature.isVisible()) {
                final Property option = this.get("Features." + feature.category(), feature.key(), feature.isEnabled(), feature.comment());

                if (option.getBoolean(feature.isEnabled())) {
                    this.featureFlags.add(feature);
                }
            } else {
                this.featureFlags.add(feature);
            }
        }

        final ModContainer imb = net.minecraftforge.fml.common.Loader.instance().getIndexedModList().get("ImmibisCore");
        if (imb != null) {
            final List<String> version = Arrays.asList("59.0.0", "59.0.1", "59.0.2");
            if (version.contains(imb.getVersion())) {
                this.featureFlags.remove(AEFeature.ALPHA_PASS);
            }
        }

        for (final TickRates tr : TickRates.values()) {
            tr.Load(this);
        }

        if (this.isFeatureEnabled(AEFeature.SPATIAL_IO)) {
            this.storageProviderID = this.get("spatialio", "storageProviderID", this.storageProviderID).getInt(this.storageProviderID);
            this.storageDimensionID = this.get("spatialio", "storageDimensionID", this.storageDimensionID).getInt(this.storageDimensionID);
            this.spatialPowerMultiplier = this.get("spatialio", "spatialPowerMultiplier", this.spatialPowerMultiplier).getDouble(this.spatialPowerMultiplier);
            this.spatialPowerExponent = this.get("spatialio", "spatialPowerExponent", this.spatialPowerExponent).getDouble(this.spatialPowerExponent);
        }

        if (this.isFeatureEnabled(AEFeature.CRAFTING_CPU)) {
            this.craftingCalculationTimePerTick = this.get("craftingCPU", "craftingCalculationTimePerTick", this.craftingCalculationTimePerTick).getInt(this.craftingCalculationTimePerTick);
            this.craftingCPUMaxSizeX = this.getCraftingCPUMaxSize("maxSizeX", this.craftingCPUMaxSizeX);
            this.craftingCPUMaxSizeY = this.getCraftingCPUMaxSize("maxSizeY", this.craftingCPUMaxSizeY);
            this.craftingCPUMaxSizeZ = this.getCraftingCPUMaxSize("maxSizeZ", this.craftingCPUMaxSizeZ);
            this.craftingCPURequiresSingleChunk = this.get("craftingCPU", "requireSingleChunk", this.craftingCPURequiresSingleChunk, "Refuse to form a crafting CPU that reaches into more than one chunk. A CPU already built across a chunk border falls apart the next time it is recalculated.").getBoolean(this.craftingCPURequiresSingleChunk);
        }

        if (this.isFeatureEnabled(AEFeature.CRYSTAL_RESONANCE_GENERATOR)) {
            this.crystalResonanceGeneratorRate = Math.max(0, this.get("crystalResonanceGenerator", "rate", this.crystalResonanceGeneratorRate, "How much energy a crystal resonance generator makes per tick. Only one of them runs on a network, whatever the number built. Zero turns them off.").getDouble(this.crystalResonanceGeneratorRate));
        }

        this.describeUpgradeCategories();

        this.energyCardPortableCell = this.energyCardMultiplier("portableCell", this.energyCardPortableCell);
        this.energyCardMatterCannon = this.energyCardMultiplier("matterCannon", this.energyCardMatterCannon);
        this.energyCardColorApplicator = this.energyCardMultiplier("colorApplicator", this.energyCardColorApplicator);
        this.energyCardWirelessTerminal = this.energyCardMultiplier("wirelessTerminal", this.energyCardWirelessTerminal);
        this.energyCardVibrationChamber = this.energyCardMultiplier("vibrationChamber", this.energyCardVibrationChamber);

        this.updatable = true;
    }

    /**
     * Said once on each category rather than on every entry in it: these hold one line per host, and the same
     * paragraph repeated forty times buries the values it is meant to explain.
     */
    private void describeUpgradeCategories() {
        this.setCategoryComment("upgrades.cards", "How many cards of a kind fit in each machine, part, cell "
                + "and tool. Only the cards a second of which adds to the first are listed - the rest mean "
                + "the same whether one or three are in. Zero refuses the card there outright.");
        this.setCategoryComment("upgrades.points", "The most points of an upgrade a host takes, however many "
                + "its cards carry. Zero, the default, lets it take them all: AE2's own cards are worth a "
                + "point each and are already limited by how many fit, so a cap here only starts to mean "
                + "something once an addon ships a card worth several.");
        this.setCategoryComment("upgrades.cardPoints", "What one card of each kind is worth wherever it is "
                + "installed. Zero stops it conferring its upgrade at all.");
        this.setCategoryComment("energyCard", "How much of a machine's own capacity one energy card adds. "
                + "Zero makes the card do nothing there.");
    }

    private double energyCardMultiplier(final String host, final double fallback) {
        return Math.max(0, this.get("energyCard", host, fallback).getDouble(fallback));
    }

    public double getEnergyCardPortableCell() {
        return this.energyCardPortableCell;
    }

    public double getEnergyCardMatterCannon() {
        return this.energyCardMatterCannon;
    }

    public double getEnergyCardColorApplicator() {
        return this.energyCardColorApplicator;
    }

    public double getEnergyCardWirelessTerminal() {
        return this.energyCardWirelessTerminal;
    }

    public double getEnergyCardVibrationChamber() {
        return this.energyCardVibrationChamber;
    }

    /**
     * How many cards carrying a trait fit in a host. Read while the upgrade registry is being filled rather
     * than up front, because the hosts are not known until then.
     *
     * @return zero when the host should not take the card at all
     */
    public int getUpgradeCards(final CardTrait trait, final IItemDefinition host, final int fallback) {
        return this.upgradeCards(trait.getId().getPath() + '.' + host.identifier(), fallback);
    }

    /**
     * The same, for a card tied to one host by name rather than by what it confers.
     */
    public int getUpgradeCards(final IItemDefinition card, final IItemDefinition host, final int fallback) {
        return this.upgradeCards(lastPart(card.identifier()) + '.' + host.identifier(), fallback);
    }

    private int upgradeCards(final String key, final int fallback) {
        return Math.max(0, this.get("upgrades.cards", key, fallback).getInt(fallback));
    }

    /**
     * @return the most points of a trait a host may end up with, or zero for no cap at all
     */
    public int getTraitLimit(final CardTrait trait, final IItemDefinition host, final int fallback) {
        return Math.max(0, this.get("upgrades.points", trait.getId().getPath() + '.' + host.identifier(),
                fallback).getInt(fallback));
    }

    /**
     * @return what one card is worth, or zero to stop it conferring the upgrade at all
     */
    public int getCardPoints(final CardTrait trait, final int fallback) {
        return Math.max(0, this.get("upgrades.cardPoints", trait.getId().getPath(), fallback)
                .getInt(fallback));
    }

    private static String lastPart(final String identifier) {
        return identifier.substring(identifier.lastIndexOf('.') + 1);
    }

    public static void init(final File configFile) {
        instance = new AEConfig(configFile);
    }

    public static AEConfig instance() {
        return instance;
    }

    /**
     * Hands renamed features whatever a pack had set under the old name, then takes that name out so the
     * file does not carry two names for one switch. Features already written under their new name are left
     * alone: an explicit setting outranks an inherited one.
     */
    private void inheritFormerFeatureKey(final String formerKey, final AEFeature... features) {
        final String category = "Features." + features[0].category();
        if (!this.hasKey(category, formerKey)) {
            return;
        }

        final boolean former = this.get(category, formerKey, features[0].isEnabled()).getBoolean();

        for (final AEFeature feature : features) {
            if (!this.hasKey(category, feature.key())) {
                this.get(category, feature.key(), former);
            }
        }

        this.getCategory(category).remove(formerKey);
    }

    public boolean isFeatureEnabled(final AEFeature f) {
        return this.featureFlags.contains(f);
    }

    public boolean areFeaturesEnabled(Collection<AEFeature> features) {
        return this.featureFlags.containsAll(features);
    }

    public double wireless_getDrainRate(final double range) {
        return this.wirelessTerminalDrainMultiplier * range;
    }

    public double wireless_getMaxRange(final int boosters) {
        return this.wirelessBaseRange + this.wirelessBoosterRangeMultiplier * Math.pow(boosters, this.wirelessBoosterExp);
    }

    public double wireless_getPowerDrain(final int boosters) {
        return this.wirelessBaseCost + this.wirelessCostMultiplier * Math.pow(boosters, 1 + boosters / this.wirelessHighWirelessCount);
    }

    @Override
    public Property get(final String category, final String key, final String defaultValue, final String comment, final Property.Type type) {
        final Property prop = super.get(category, key, defaultValue, comment, type);

        if (prop != null) {
            if (!category.equals("Client")) {
                prop.setRequiresMcRestart(true);
            }
        }

        return prop;
    }

    @Override
    public void save() {
        if (this.isFeatureEnabled(AEFeature.SPATIAL_IO)) {
            this.get("spatialio", "storageProviderID", this.storageProviderID).set(this.storageProviderID);
            this.get("spatialio", "storageDimensionID", this.storageDimensionID).set(this.storageDimensionID);
        }

        if (this.hasChanged()) {
            super.save();
        }
    }

    public String getFilePath() {
        return this.configFile.toString();
    }

    public boolean useAEVersion(final MaterialType mt) {
        if (this.isFeatureEnabled(AEFeature.WEBSITE_RECIPES)) {
            return true;
        }

        this.setCategoryComment("OreCamouflage", "AE2 Automatically uses alternative ores present in your instance of MC to blend better with its surroundings, if you prefer you can disable this selectively using these flags; Its important to note, that some if these items even if enabled may not be craftable in game because other items are overriding their recipes.");
        final Property p = this.get("OreCamouflage", mt.name(), true);
        p.setComment("OreDictionary Names: " + mt.getOreName());

        return !p.getBoolean(true);
    }

    public int getFreeMaterial(final int varID) {
        return this.getFreeIDSLot(varID, "materials");
    }

    public int getFreeIDSLot(final int varID, final String category) {
        boolean alreadyUsed = false;
        int min = 0;

        for (final Property p : this.getCategory(category).getValues().values()) {
            final int thisInt = p.getInt();

            if (varID == thisInt) {
                alreadyUsed = true;
            }

            min = Math.max(min, thisInt + 1);
        }

        if (alreadyUsed) {
            if (min < 16383) {
                min = 16383;
            }

            return min;
        }

        return varID;
    }

    public int getFreePart(final int varID) {
        return this.getFreeIDSLot(varID, "parts");
    }

    public Enum getSetting(final String category, final Class<? extends Enum> class1, final Enum myDefault) {
        final String name = class1.getSimpleName();
        final Property p = this.get(category, name, myDefault.name());

        try {
            return (Enum) class1.getField(p.toString()).get(class1);
        } catch (final Throwable t) {
            // :{
        }

        return myDefault;
    }

    public void setSetting(final String category, final Enum s) {
        final String name = s.getClass().getSimpleName();
        this.get(category, name, s.name()).set(s.name());
        this.save();
    }

    // Getters
    public boolean isRemoveCrashingItemsOnLoad() {
        return this.removeCrashingItemsOnLoad;
    }

    public boolean isMonitorThroughputEnabled() {
        return this.monitorThroughput;
    }

    public boolean isNetworkStorageAudited() {
        return this.auditNetworkStorage;
    }

    public int getVisualiserMaxNodes() {
        return this.visualiserMaxNodes;
    }

    public int getVisualiserUpdateInterval() {
        return this.visualiserUpdateInterval;
    }

    public int getFormationPlaneEntityLimit() {
        return this.formationPlaneEntityLimit;
    }

    public int getCraftingCalculationTimePerTick() {
        return this.craftingCalculationTimePerTick;
    }

    public boolean craftingCPURequiresSingleChunk() {
        return this.craftingCPURequiresSingleChunk;
    }

    public int getCraftingCPUMaxSizeX() {
        return this.craftingCPUMaxSizeX;
    }

    public int getCraftingCPUMaxSizeY() {
        return this.craftingCPUMaxSizeY;
    }

    public int getCraftingCPUMaxSizeZ() {
        return this.craftingCPUMaxSizeZ;
    }

    public double getCrystalResonanceGeneratorRate() {
        return this.crystalResonanceGeneratorRate;
    }

    /**
     * Every recalculation walks the whole box, so a far larger CPU costs more each time one of its
     * blocks changes. 17 is the size AE has always allowed.
     */
    private int getCraftingCPUMaxSize(final String key, final int fallback) {
        final int size = this.get("craftingCPU", key, fallback, "How many blocks long a crafting CPU may be along this axis, from 1 to 64. AE's own limit is 17.").getInt(fallback);
        return Math.min(Math.max(size, 1), 64);
    }

    public int getStorageProviderID() {
        return this.storageProviderID;
    }

    void setStorageProviderID(int id) {
        this.storageProviderID = id;
    }

    public int getStorageDimensionID() {
        return this.storageDimensionID;
    }

    void setStorageDimensionID(int id) {
        this.storageDimensionID = id;
    }

    public double getSpatialPowerExponent() {
        return this.spatialPowerExponent;
    }

    public double getSpatialPowerMultiplier() {
        return this.spatialPowerMultiplier;
    }

    public String[] getGrinderOres() {
        return this.grinderOres;
    }

    public Set<String> getGrinderBlackList() {
        return this.grinderBlackList;
    }

    public double getOreDoublePercentage() {
        return this.oreDoublePercentage;
    }

    public int getWirelessTerminalBattery() {
        return this.wirelessTerminalBattery;
    }

    public int getEntropyManipulatorBattery() {
        return this.entropyManipulatorBattery;
    }

    public int getMatterCannonBattery() {
        return this.matterCannonBattery;
    }

    public int getPortableCellBattery() {
        return this.portableCellBattery;
    }

    public int getColorApplicatorBattery() {
        return this.colorApplicatorBattery;
    }

    public int getChargedStaffBattery() {
        return this.chargedStaffBattery;
    }

    public float getSpawnChargedChance() {
        return this.spawnChargedChance;
    }

    public int getQuartzOresPerCluster() {
        return this.quartzOresPerCluster;
    }

    public int getQuartzOresClusterAmount() {
        return this.quartzOresClusterAmount;
    }

    public String[] getNonBlockingItems() {
        return nonBlockingItems;
    }

    public int getChargedChange() {
        return this.chargedChange;
    }

    public int getMinMeteoriteDistance() {
        return this.minMeteoriteDistance;
    }

    public double getMeteoriteClusterChance() {
        return this.meteoriteClusterChance;
    }

    public int getMeteoriteMaximumSpawnHeight() {
        return this.meteoriteMaximumSpawnHeight;
    }

    public int[] getMeteoriteDimensionWhitelist() {
        return this.meteoriteDimensionWhitelist;
    }

    public int getMeteoriteGeneratorPriority() {
        return this.meteoriteGeneratorPriority;
    }

    public double getWirelessBaseCost() {
        return this.wirelessBaseCost;
    }

    public double getWirelessCostMultiplier() {
        return this.wirelessCostMultiplier;
    }

    public double getWirelessTerminalDrainMultiplier() {
        return this.wirelessTerminalDrainMultiplier;
    }

    public double getWirelessBaseRange() {
        return this.wirelessBaseRange;
    }

    public double getWirelessBoosterRangeMultiplier() {
        return this.wirelessBoosterRangeMultiplier;
    }

    public double getWirelessBoosterExp() {
        return this.wirelessBoosterExp;
    }

    // Setters keep visibility as low as possible.

    public double getWirelessHighWirelessCount() {
        return this.wirelessHighWirelessCount;
    }

    public boolean getEnableCraftingSubstitutes() {
        return this.enableCraftingSubstitutes;
    }

    public int getMaxControllerSizeX() {
        return this.maxControllerSizeX;
    }

    public int getMaxControllerSizeY() {
        return this.maxControllerSizeY;
    }

    public int getMaxControllerSizeZ() {
        return this.maxControllerSizeZ;
    }

    public int getAdHocNetworkChannels() {
        return this.adHocNetworkChannels;
    }

    public int getP2PTunnelChannelCost() {
        return this.p2pTunnelChannelCost;
    }

    /**
     * Asked as each tier is registered, which is why a tier registered after pre-initialisation never
     * reaches the file.
     */
    public int getChannelTierCapacity(final ResourceLocation id, final int defaultCapacity) {
        return this.get("ChannelTiers", id.toString(), defaultCapacity).getInt(defaultCapacity);
    }

}
