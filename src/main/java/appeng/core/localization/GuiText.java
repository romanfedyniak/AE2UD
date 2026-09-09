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

package appeng.core.localization;


import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.translation.I18n;


public enum GuiText {
    inventory("container"), // mc's default Inventory localization.

    ChannelCapacity,
    Chest,
    StoredEnergy,
    Of,
    Condenser,
    Drive,
    GrindStone,
    SkyChest,

    VibrationChamber,
    SpatialIOPort,
    LevelEmitter,
    FluidLevelEmitter,

    MonitorOff,
    MonitorNet,
    MonitorIn,
    MonitorOut,
    MonitorPerTick,
    MonitorPerSecond,
    MonitorPerMinute,
    MonitorPerHour,
    MonitorRateNet,
    MonitorRateIn,
    MonitorRateOut,
    MonitorLock,
    MonitorLockHint,
    MonitorThroughput,
    MonitorThroughputHint,
    MonitorShowing,
    MonitorShowingHint,
    Terminal,

    Interface,
    Config,
    StoredItems,
    StoredFluids,
    Patterns,
    ImportBus,
    ImportBusFluids,
    ExportBus,
    ExportBusFluids,
    ConfigureImportedTypes,
    ConfigureImportedTypesHint,
    ConfigureVisibleTypes,
    ConfigureVisibleTypesHint,
    ConfigureStoredTypes,
    ConfigureStoredTypesHint,
    ConfigurePlacedTypes,
    ConfigurePlacedTypesHint,
    ConfigurePickedUpTypes,
    ConfigurePickedUpTypesHint,
    KeyTypeIgnored,

    // What each wireless terminal mode is called, in its button, in the terminal's name and in its tooltip
    WirelessTerminalModeName,
    WirelessModeTerminal,
    WirelessModeCrafting,
    WirelessModePattern,
    WirelessModePatternAccess,
    WirelessModeInterfaceConfig,
    UnknownWirelessMode,

    CellWorkbench,
    NetworkDetails,
    StorageCells,
    IOBuses,

    IOPort,
    BytesUsed,
    Types,
    QuantumLinkChamber,
    PortableCell,

    NetworkTool,
    PowerUsageRate,
    PowerInputRate,
    Installed,
    EnergyDrain,

    StorageBus,
    OreDictStorageBus,
    StorageBusFluids,
    Priority,
    CraftPriority,
    CraftPriorityOf,
    CraftPriorityBack,
    Security,
    Encoded,
    Blank,
    Unlinked,
    Linked,

    SecurityCardEditor,
    NoPermissions,
    WirelessTerminal,
    Wireless,

    CraftingTerminal,
    AnnihilationPlane,
    FormationPlane,
    FluidFormationPlane,
    Inscriber,
    QuartzCuttingKnife,

    Renamer,

    // tunnel names
    METunnel,
    InterfaceTunnel,
    ItemTunnel,
    RedstoneTunnel,
    EUTunnel,
    FluidTunnel,
    FluidTerminal,
    OCTunnel,
    LightTunnel,
    FETunnel,
    GTEUTunnel,
    PressureTunnel,

    // spatial
    StoredSize,
    CellId,

    CopyMode,
    CopyModeDesc,
    PatternTerminal,

    // Pattern tooltips
    CraftingPattern,
    ProcessingPattern,
    Crafts,
    PlanOfTotal,
    PlanShownAbove,
    Creates,
    And,
    With,
    EncodedBy,
    Substitute,
    UsesFluidsDirectly,
    ViewPatternHint,
    Yes,
    No,

    MolecularAssembler,

    StoredPower,
    MaxPower,
    RequiredPower,
    Efficiency,
    SCSSize,
    SCSInvalid,
    InWorldCrafting,



    NoSecondOutput,
    OfSecondOutput,
    MultipleOutputs,

    Stores,
    Next,
    SelectAmount,
    SetAmount,
    Set,
    AmountSteps,
    AmountStepsNormal,
    AmountStepsDefaults,
    Lumen,
    Empty,

    ConfirmCrafting,
    Stored,
    Crafting,
    Scheduled,
    Waiting,
    CraftingStatus,
    Cancel,
    Suspend,
    Resume,
    ETA,
    ETAFormat,
    CraftName,
    Remains,
    Progress,
    TimeUsed,
    CPUSourcePlayer,
    CPUSourceMachineRequested,

    FromStorage,
    FromStoragePercent,
    ToCraft,
    ToCraftRequests,
    CraftingPlan,

    CraftingTree,

    ShowMissingOnly,
    SaveAsImage,
    ShiftClickToLocate,
    NothingMissing,
    CalculatingWait,
    Start,
    ForceStart,
    Bytes,

    CraftingCPU,
    Automatic,
    CoProcessors,
    Simulation,
    Missing,

    CraftErrorTitle,
    CraftErrorIncompletePlan,
    CraftErrorNoCpuFound,
    CraftErrorNoSuitableCpu,
    CraftErrorNoSuitableCpuOffline,
    CraftErrorNoSuitableCpuBusy,
    CraftErrorNoSuitableCpuTooSmall,
    CraftErrorNoSuitableCpuExcluded,
    CraftErrorCpuBusy,
    CraftErrorCpuOffline,
    CraftErrorCpuTooSmall,
    CraftErrorRequestTooLarge,
    CraftErrorMissingIngredient,
    CraftErrorMissingIngredientNamed,
    CraftErrorRetry,
    CraftErrorReplan,

    PatternAccessTerminal,
    SendPatternTo,
    PatternTargetUnsuitable,
    PatternTargetFull,
    PatternTargetFreeSlots,
    InterfaceConfigurationTerminal,
    NoCraftingCPUs,
    Clean,
    InvalidPattern,

    PatternAccessTerminalHint,
    Range,
    TransparentFacades,
    TransparentFacadesHint,

    NoCraftingJobs,
    CPUs,
    FacadeCrafting,
    inWorldCraftingPresses,
    ChargedQuartzFind,
    JeiCharging,
    JeiInLiquid,
    JeiOnExplosion,
    JeiGrowth,
    JeiAttune,
    JeiHeat,
    JeiCool,

    Included,
    Excluded,
    Partitioned,
    Precise,
    Fuzzy,
    Sticky,
    EqualDistributionOf,
    OverflowDestruction,

    // Used in a ME Interface when no appropriate TileEntity was detected near it
    Nothing,

    // Used in Crafting Toasts
    CraftingToastDone,
    CraftingToastCancelled,

    //Used in Lock Crafting,
    CraftingLock,
    NoneLock,
    LowRedstoneLock,
    HighRedstoneLock,
    ResultLock,
    UntilPulseUnlock,
  
    // Used on the pattern terminal blank slot, when the network supplies it
    BlankPatternInNetwork,

    // Used on the memory card, to say what a copy carries
    MemoryCardCards,
    MemoryCardPatterns,

    // Used in Annihilation Planes
    CanBeEnchanted,
    IncreasedEnergyUseFromEnchants,
    Deprecated,

    // How the two priorities actually decide anything, which neither number says by itself
    PriorityHintInsert,
    PriorityHintExtract,
    PriorityHintPreferred,
    CraftPriorityHint,
    CraftPriorityHintNotFaster,
    CraftPriorityHintNotProcessor,

    // What the terminal's search field understands
    // The two lists one cell view can show, and the tail of a tooltip that shows only the top of one
    AndMoreTypes,
    CellContents,
    CellFilter,

    SearchHintTitle,
    SearchHintName,
    SearchHintTerms,
    SearchHintOr,
    SearchHintPhrase,
    SearchHintExclude,
    SearchHintMod,
    SearchHintTooltip,
    SearchHintOreDict,
    SearchHintId,
    SearchHintRegex,
    Back,
    SearchHintMore,
    JeiRetrieveHint,
    JeiCraftHint;

    private final String root;

    GuiText() {
        this.root = "gui.appliedenergistics2";
    }

    GuiText(final String r) {
        this.root = r;
    }

    public String getLocal() {
        return I18n.translateToLocal(this.getUnlocalized());
    }

    /** The same, for a line that names something the caller knows - a key combination, say. */
    public String getLocal(final Object... args) {
        return I18n.translateToLocalFormatted(this.getUnlocalized(), args);
    }

    public ITextComponent getLocalizedWithArgs(Object... args) {
        return new TextComponentTranslation(this.getUnlocalized(), args);
    }

    public String getUnlocalized() {
        return this.root + '.' + this;
    }

}
