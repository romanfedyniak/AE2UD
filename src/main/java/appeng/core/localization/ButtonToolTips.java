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


public enum ButtonToolTips {
    PowerUnits,
    IOMode,
    CondenserOutput,
    RedstoneMode,
    MatchingFuzzy,

    MatchingMode,
    TransferDirection,
    SortOrder,
    SortBy,
    View,

    PartitionStorage,
    Clear,
    FuzzyMode,
    OperationMode,
    TrashController,

    InterfaceBlockingMode,
    InterfaceCraftingMode,
    Trash,
    MatterBalls,

    Singularity,
    Read,
    Write,
    ReadWrite,
    AlwaysActive,

    ActiveWithoutSignal,
    ActiveWithSignal,
    ActiveOnPulse,

    EmitLevelsBelow,
    EmitLevelAbove,
    MatchingExact,
    TransferToNetwork,

    TransferToStorageCell,
    ToggleSortDirection,

    SearchMode_Auto,
    SearchMode_Standard,
    SearchMode_JEIAuto,
    SearchMode_JEIStandard,
    SearchKeep,
    SearchKeepYes,
    SearchKeepNo,
    PickBlock,
    MoreSettings,
    PickBlockYes,
    PickBlockNo,

    SearchMode,
    ItemName,
    NumberOfItems,
    PartitionStorageHint,
    MoreSettingsHint,

    ClearSettings,
    ResetIdentity,
    ResetIdentityDesc,
    StoredItems,
    StoredCraftable,
    Craftable,

    FZPercent_25,
    FZPercent_50,
    FZPercent_75,
    FZPercent_99,
    FZIgnoreAll,

    MoveWhenEmpty,
    MoveWhenWorkIsDone,
    MoveWhenFull,
    Disabled,
    HideStored,
    Enable,

    Blocking,
    NonBlocking,

    CpuSelectionMode,
    CpuSelectionModeAny,
    CpuSelectionModePlayersOnly,
    CpuSelectionModeAutomationOnly,
    CpuFilterActivity,
    CpuFilterActivityAll,
    CpuFilterActivityActive,
    CpuFilterActivityIdle,
    CpuFilterMode,
    CpuFilterModeAll,
    CpuSortBy,
    CpuSortByName,
    CpuSortByStorage,
    CpuSortByCoprocessors,
    CpuSearch,
    PatternTargetSearch,

    LockCraftingMode,
    LockCraftingModeNone,
    LockCraftingUntilRedstonePulse,
    LockCraftingWhileRedstoneHigh,
    LockCraftingWhileRedstoneLow,
    LockCraftingUntilResultReturned,

    LevelType,
    LevelType_Energy,
    LevelType_Item,
    InventoryTweaks,
    TerminalStyle,
    TerminalStyle_Full,
    TerminalStyle_Tall,
    TerminalStyle_Medium,
    TerminalStyle_Small,
    ConfigureVisibleTypes,
    ConfigureVisibleTypesHint,
    ConfigureImportedTypes,
    ConfigureImportedTypesHint,
    ConfigureStoredTypes,
    ConfigureStoredTypesHint,
    ConfigurePlacedTypes,
    ConfigurePlacedTypesHint,
    ConfigurePickedUpTypes,
    ConfigurePickedUpTypesHint,
    PlaneMode,
    PlaneModePassive,
    PlaneModeActive,

    Stash,
    StashDesc,
    Encode,
    EncodeDescription,
    PatternSlotConfig,
    PatternSlotConfigDesc32_8,
    PatternSlotConfigDesc8_32,
    Substitutions,
    SubstitutionsOn,
    SubstitutionsOff,
    SubstitutionsDescEnabled,
    SubstitutionsDescDisabled,
    FluidSubstitutions,
    FluidSubstitutionsDescEnabled,
    FluidSubstitutionsDescDisabled,
    CraftOnly,
    CraftEither,

    Craft,
    Mod,
    DoesntDespawn,
    EmitterMode,
    CraftViaRedstone,
    EmitWhenCrafting,
    ReportInaccessibleItems,
    ReportInaccessibleItemsYes,
    ReportInaccessibleItemsNo,
    ReportInaccessibleFluids,
    ReportInaccessibleFluidsYes,
    ReportInaccessibleFluidsNo,

    BlockPlacement,
    BlockPlacementYes,
    BlockPlacementNo,

    MultiplyByTwo,
    MultiplyByTwoDesc,
    MultiplyByThree,
    MultiplyByThreeDesc,
    IncreaseByOne,
    IncreaseByOneDesc,
    DivideByTwo,
    DivideByTwoDesc,
    DivideByThree,
    DivideByThreeDesc,
    DecreaseByOne,
    DecreaseByOneDesc,
    ToggleMolecularAssemblers,
    ToggleMolecularAssemblersOnDesc,
    ToggleMolecularAssemblersOffDesc,
    ToggleShowFullInterfaces,
    ToggleShowFullInterfacesOnDesc,
    ToggleShowFullInterfacesOffDesc,
    ToggleShowOnlyInvalidInterface,
    ToggleShowOnlyInvalidInterfaceOnDesc,
    ToggleShowOnlyInvalidInterfaceOffDesc,
    HighlightInterface,
    HighlightInterfaceDesc,
    SearchFieldInputs,
    SearchFieldConfigured,
    SearchFieldOutputs,
    SearchFieldNames,

    // Used in the tooltips of the items in the terminal, when moused over
    ItemsStored,
    AmountStored,
    AmountRequestable,
    ItemsRequestable,
    ItemsCraftable,
    ItemsFakeCraftable,

    SchedulingMode,
    SchedulingModeDefault,
    SchedulingModeRoundRobin,
    SchedulingModeRandom,

    FilterMode,
    FilterModeKeep,
    FilterModeClear,

    InscriberSideness,
    InscriberSidenessSeparate,
    InscriberSidenessCombined,
    AutoExport,
    AutoExportOn,
    AutoExportOff,
    InscriberBufferSize,
    InscriberBufferVeryLow,
    InscriberBufferLow,
    InscriberBufferHigh,

    // Which faces of a machine reach a slot, said in the slot's own tooltip while it stands empty
    CanInsertFrom,
    CanExtractFrom,
    SideTop,
    SideBottom,
    SideLeft,
    SideRight,
    SideFront,
    SideBack,
    SideAny,

    // The wireless terminal's mode switch
    TerminalModeSwitch,

    // The pattern terminal's upload button
    PatternUpload,
    PatternUploadHint,
    TerminalModeList,
    SearchModePanel,
    TerminalModeLocked,
    TerminalModeUnobtainable,

    // What a click on a slot does, for the clicks the slot cannot show by itself
    LeftClick,
    RightClick,
    MiddleClick,
    MouseButton,
    ShiftRightClick,
    CtrlLeftClick,
    CtrlRightClick,
    CtrlShiftRightClick,
    SetAction,
    StoreAction,
    ModifyAmountAction,
    ExtractAction,
    DepositAction,
    ExtractAllAction,
    DepositAllAction,

    // Which way the list beside the button is being sorted, rather than that it can be turned around
    Ascending,
    Descending,

    // What belongs in a restricted slot, said while it is empty and cannot say it by itself
    SlotStorageCell,
    SlotWorkbenchCell,
    SlotSpatialCell,
    SlotStorageComponent,
    SlotUpgrade,
    SlotViewCell,
    SlotBiometricCard,
    SlotQuantumCard,
    SlotQESingularity,
    SlotRangeBooster,
    SlotEncodableItem,
    SlotPoweredTool,
    SlotFuel,
    SlotOre,
    SlotMetalIngot,
    SlotInscriberPress,
    SlotInscriberInput,
    SlotPattern,
    SlotBlankPattern,
    SlotEncodedPattern,
    SlotEncodedCraftingPattern,
    SlotTrash;

    private final String root;

    ButtonToolTips() {
        this.root = "gui.tooltips.appliedenergistics2";
    }

    ButtonToolTips(final String r) {
        this.root = r;
    }

    public String getLocal() {
        return I18n.translateToLocal(this.getUnlocalized());
    }

    public ITextComponent getLocalizedWithArgs(final Object... args) {
        return new TextComponentTranslation(this.getUnlocalized(), args);
    }

    public String getUnlocalized() {
        return this.root + '.' + this;
    }

}
