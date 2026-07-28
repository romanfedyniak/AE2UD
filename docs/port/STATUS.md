# Port status — resume here

Companion to `CONTRACT.md`. The contract is the *spec*; this file is the *bookmark*. Last updated after
wave 3, 2026-07-28.

Branch: `feature/generic-storage`.

## What this port is

Replacing AE2UD's old generic `IAEStack<T>` / `IStorageChannel<T>` / `IMEInventory<T>` storage model with
the type-erased `AEKey` / `AEKeyType` / `MEStorage` / `GenericStack` / `KeyCounter` model copied from
modern upstream AE2, plus the strategy layer (`StackWorldBehaviors` + five strategy interfaces) that lets
one bus class serve every registered key type.

The migration is **big-bang**: `src/main` does not compile from wave 1 until wave 6. That is deliberate.
The only gate that works during the migration is `gradlew compileApiJava` — `src/api` is a separate Gradle
source set that compiles independently and must stay green at every commit.

Because there is no compiler feedback on `src/main`, `CONTRACT.md` replaces it. §4 is the frozen api
surface; §9 is the class-by-class registry of what every wave produced. Anyone continuing this work reads
§9 to find out what the code they are calling actually looks like.

## Commits so far

| Commit | Wave | Scope |
|---|---|---|
| `45b30ba64` | 0 | `src/api` — the whole new storage API (67 files) |
| `77094bc8c` | 1 | `appeng.util`, `appeng.me` (45 files) |
| `2f9055f9a` | 2 | `appeng.crafting`, `appeng.tile`, `appeng.helpers`, `appeng.core` (43 files) |
| `caffdcffe` | — | moved the cell GUI handler registry into `StorageCells` (api) |
| `290c4df8f` | 3 | `appeng.parts`, `appeng.items`, `appeng.recipes` (44 files) |

## Where the work stands

Done: waves 0-3. No non-comment references to the old model remain in `appeng.util`, `appeng.me`,
`appeng.crafting`, `appeng.tile`, `appeng.helpers`, `appeng.core` (except `core/sync/packets`, which is
wave 4 by design), `appeng.parts`, `appeng.items` or `appeng.recipes`.

Remaining broken files, by package:

| Wave | Packages | Files |
|---|---|---:|
| 4 | `container/implementations` 12, `client/gui` 11, `core/sync` 9, `client/me` 6, `client/render` 4, `container/slot` 2, `container/AEBaseContainer` 1 | ~45 |
| 5 | `fluids/*` (parts 9, util 8, container 8, client 6, helper 3, registries 1, items 1) | 36 |
| 6 | `integration/modules` 7, plus the HEI dependency swap and the NAE2 addon | 7+ |

The scattered single hits in `me/*`, `crafting/*`, `util/*`, `tile/*`, `parts/*`, `items/*` are comments
and one class that is merely *named* `IMEInventoryDestination`. They are not work.

## How to check where you are

Two commands. Nothing else in this repo tells you the truth during the migration.

```sh
# The only gate that works until wave 6. Must be green at every commit.
./gradlew compileApiJava

# What is left. Any hit outside the current wave's packages is either a comment or a mistake.
grep -rl "IAEStack\|IAEItemStack\|IAEFluidStack\|IStorageChannel\|IMEInventory<\|IItemList\|IMEMonitor\|IItemStorageChannel\|IFluidStorageChannel" \
  src/main/java --include=*.java | sed 's|src/main/java/appeng/||' | cut -d/ -f1,2 | sort | uniq -c | sort -rn

# A wave is finished when this prints nothing for its packages (drop the comment lines).
grep -rn "IAEStack\|IAEItemStack\|IAEFluidStack\|IStorageChannel\|IMEInventory<\|IItemList\|IMEMonitor" \
  src/main/java/appeng/<package> --include=*.java | grep -vE ":\s*(\*|//|/\*)"
```

## Wave 4 — file list and a suggested split

45 files. The split below keeps the packet layer with the containers that send them, and isolates the
blocked container.

**4-1 `core/sync/packets` (9)** — `PacketMEInventoryUpdate` (needs a `GenericStack` overload),
`PacketMEFluidInventoryUpdate`, `PacketInventoryAction`, `PacketPatternSlot`, `PacketJEIRecipe`,
`PacketFluidSlot`, `PacketAssemblerAnimation`, `PacketCraftingToast`, `PacketInformPlayer`.

**4-2 `container/implementations` — crafting side (7)** — `ContainerCraftAmount`,
`ContainerCraftConfirm`, `ContainerCraftingCPU`, `CraftingCPUStatus`, `ContainerPatternEncoder`,
`ContainerWirelessPatternTerminal`, `ContainerNetworkStatus`. Must hold the concrete `CraftingJob` type
to call `populatePlan(KeyCounter, KeyCounter)` (§9.2).

**4-3 `container` — storage side (8)** — `AEBaseContainer`, `ContainerMEMonitorable` (**blocked, see
below**), `ContainerStorageBus`, `ContainerOreDictStorageBus`, `ContainerCellWorkbench`,
`ContainerFluidInterfaceConfigurationTerminal`, `container/slot/SlotCraftingTerm`,
`container/slot/SlotPatternTerm`. `PATTERN_EXPANSION` lives in `ContainerInterface` /
`ContainerInterfaceTerminal` — protect it.

**4-4 `client` (21)** — `AEBaseGui`, `AEBaseMEGui`, `AEGuiHandler`, the seven `gui/implementations`
screens, `client/me/{ItemRepo,FluidRepo,SlotME,SlotFluidME,InternalSlotME,InternalFluidSlotME}`,
`client/render/{TesrRenderHelper,StackSizeRenderer,CraftingMonitorTESR,effects/AssemblerFX}`. This agent
owns the multi-type filter GUI and must replace the placeholder `AEKeyType` button textures.

## Wave 5 — file list

36 files, all under `appeng.fluids`: `parts` 9, `util` 8, `container` 8, `client` 6, `helper` 3,
`registries` 1, `items` 1. A natural split is parts+registries / util / container+client+helper.

Wave 5 is where the strategy layer pays off: registering the fluid import, export, placement, pickup and
external-storage strategies is what makes the *already migrated* generic buses and storage bus handle
fluids. Do not add fluid branches to the wave-3 parts.

## Wave 6 — file list

`integration/modules`: `jei/CraftableCallBack`, `jei/JEIMissingItem`, `bogosorter/InventoryBogoSortModule`,
`theoneprobe/part/StorageMonitorInfoProvider`, `theoneprobe/tile/CraftingMonitorInfoProvider`,
`waila/part/StorageMonitorWailaDataProvider`, `waila/tile/CraftingMonitorWailaDataProvider`.

Then: swap the JEI dependency for HEI in `build.gradle:583` (see `CONTRACT.md` §8.2 — HEI is a drop-in
CleanroomMC fork of JEI, sources at `Z:\harmony\sources\HadEnoughItems`), migrate the owner's NAE2 addon
at **`Z:\harmony\NAE2`** (~27 files, plus mixins into AE2 internals that break regardless — note there is
a second checkout at `Z:\harmony\McSkill\NAE2`; confirm with the owner which is canonical), and get
`gradlew build` green.

## Before starting wave 4 — read this

### Blocked: the portable-cell push design

**Do not start `ContainerMEMonitorable` until this is decided.** See `CONTRACT.md` §10, "Third case".

The old model pushed live inventory updates to open terminal screens. Two places have already lost that
and are waiting on the same decision:

- `appeng.helpers.WirelessTerminalGuiObject` (wave 2) — plain forwarding, no notification.
- `appeng.items.contents.PortableCellViewer` (wave 3) — same; its old `notifyListenersOfChange` on
  insert/extract has no replacement yet.

Both were left as plain forwarding *deliberately*, not dropped by accident.

`CONTRACT.md` §10 splits this into two cases and only one is solved:

1. **Network-backed terminals** — solved. Register an `IStorageWatcherNode`, call
   `IStackWatcher.setWatchAll(true)`, handle `onStackChange(AEKey, long)`. No decision needed.
2. **Portable cell / view-only cell terminals** — they view a `StorageCell` directly, with no grid node
   and therefore no watcher. **No replacement exists.** Rule 6 rules out polling (the owner already
   rejected it once, for crafting CPUs).

The standing recommendation is a small push interface on this path, in the same spirit as the
`ICraftingCPUListener` that was added when the crafting-CPU regression was repaired. It needs owner
sign-off before wave 4 touches `ContainerMEMonitorable`, because that class is the base container of
*every* ME terminal — regular, crafting, pattern, wireless and portable.

### Debt handed to wave 4 by earlier waves

- `appeng.client.render.TesrRenderHelper.renderItem2dWithAmount` / `renderFluid2dWithAmount` still have
  the old `(IAEItemStack)` / `(IAEFluidStack)` signatures. `AbstractPartMonitor.renderDynamic` already
  calls them as `(AEItemKey/AEFluidKey, long amount, float, float)`.
- `ContainerStorageBus` / `ContainerOreDictStorageBus` / `ContainerFluidStorageBus` still treat
  `getInternalHandler()`'s result as the old generic type; it now returns a plain `MEInventoryHandler`.
- `appeng.util.Platform.extractItemsByRecipe` still takes an `IPartitionList`, but
  `ItemViewCell.createFilter` now returns an `AEKeyFilter`. Three wave-4 call sites feed one into the
  other (`SlotCraftingTerm`, `ContainerPatternEncoder`, `PacketJEIRecipe`). Recommended fix: add an
  additive `AEKeyFilter` overload rather than make the two filter types interoperate.
- `PacketMEInventoryUpdate` needs a `GenericStack` overload.
- The `AEKeyType` button textures/icons in the terminal are placeholders and must be replaced.
- Wave 4 must hold the concrete `CraftingJob` type to call `populatePlan(KeyCounter, KeyCounter)` —
  see `CONTRACT.md` §9.2.

### Wave 5 notes already written down

- `appeng.fluids.items.BasicFluidStorageCell` must become `extends AbstractStorageCell` (non-generic) with
  `getKeyType() { return AEKeyType.fluids(); }`.
- `PartFluidFormationPlane` must extend the new non-generic `PartAbstractFormationPlane` and implement
  `getKeyType()` / `insert()` / `getConfigInventory()`. Formation planes stay **split** into item and fluid
  parts (AE2UD's existing shape), not merged as upstream did.
- Wave 5 registers the fluid import/export/placement/pickup and external-storage strategies. Once it does,
  the existing generic bus and storage bus serve fluids with no further change — that plumbing is already
  in place from wave 3.
- `BasicFluidCellGuiHandler` registers through `StorageCells.addCellGuiHandler` now, not the deleted
  `CellRegistry`.

## Standing rules that have already been violated once each

**Rule 6 — do not cut any mechanic** (`CONTRACT.md` rule 6). This is a new API and new capabilities, not
the removal of old ones. "Mirror upstream" governs the shape of types and API and is never a licence to
drop a feature. Violated four times so far, every one caught and repaired: Sticky Card silently became a
no-op, crafting-CPU push notifications were replaced with a polling suggestion,
`ICellGuiHandler.isSpecializedFor` was dropped, and `PartIdentityAnnihilationPlane` would have broken
silently had the wave 3a agent obeyed its file list literally. The one sanctioned exception is
save-file compatibility, which the owner separately agreed to break.

**§9.1 — `GenericStack.equals()` is not `IAEItemStack.equals()`.** The old one ignored stack size; the
record compares the amount too. A literal translation compiles cleanly and silently changes behaviour.
Two real instances have been found so far: `CraftingTreeProcess.addProcess()/getTimes()` (wave 2) and
`ToolColorApplicator.findNextColor()` (wave 3). Every remaining wave must check this.

## Amendments made to the frozen API

Post-freeze edits to §1-§4 are the owner's call (§7). Three have been made and approved:

1. **§8.3** — `ICraftingGrid.getCraftables(AEKeyFilter)` + `default isCraftable(AEKey)`. Keys carry no
   craftable flag, so the crafting grid answers instead. Mirrors upstream verbatim; additive.
2. **`StorageCells` GUI half** (commit `caffdcffe`) — `addCellGuiHandler` / `getGuiHandler(AEKeyType)` /
   `getGuiHandler(AEKeyType, ItemStack)` moved from an internal `src/main` class into api, so an addon
   shipping a cell with its own screen does not have to import an internal package. Upstream has no
   GUI-handler concept at all; this is a deliberate divergence.
3. **§8.4** — `PickupStrategy.Factory` now carries `Map<Enchantment, Integer>` instead of
   `int fortuneLevel, boolean silkTouch`. AE2UD's annihilation-plane energy formula also reads Efficiency
   and Unbreaking, which the upstream-shaped signature could not carry.

## How the waves are executed

Parallel Sonnet subagents on disjoint packages, with `CONTRACT.md` as the only inter-agent channel. Each
appends its own subsection to §9 when it finishes.

**Every brief must contain all of the following.** Waves 1-3 showed that dropping any one of them
produces a specific, repeatable failure:

1. *Read `CONTRACT.md` in full first.* §4 is the api surface, §9 is what earlier waves actually built.
2. *`src/main` does not compile; that is expected.* Without this an agent burns its budget trying to make
   the build pass and then "fixes" unrelated packages.
3. *Reference sources*, with paths — `AE2-original` for the reference implementation, `ae-gtnh` and
   `AE2FluidCraft-Rework-Unofficial` for 1.12.2-era API shapes.
4. *Rule 6 in full, quoted, with the count of past violations.* Summarising it does not work; agents read
   "mirror upstream" as permission to delete.
5. *The §9.1 `equals` hazard*, spelled out. Two real instances have been caught by agents that were told;
   it is invisible to one that is not.
6. *`src/api` is frozen — report, do not edit.* Plus: run `compileApiJava` if you touch it by accident.
7. *An explicit list of the fork-specific mechanics in that agent's files*, by name (`Upgrades.STICKY`,
   `Settings.SCHEDULING_MODE`, the ore-dict bus, …). "Preserve everything" is not actionable; a named
   list is.
8. *The names of the other agents' packages*, so it knows what not to touch.
9. *Instruction to enumerate each file's current behaviour before rewriting it*, and to report anything it
   could not express rather than silently dropping it.

Two conflict rules that mattered in wave 3 and should be kept: exactly one agent may own
`appeng.core.Registration` and the item-definition classes, and when two agents need to meet at a new
class, fix its fully-qualified name and signature in *both* briefs up front rather than letting one agent
name the other's class.

**Expect agents to step outside their file list occasionally, and check whether they were right to.** In
wave 3 one agent adapted `PartIdentityAnnihilationPlane`, which was not in its list; had it obeyed
literally, the Identity Annihilation Plane would have broken silently. Review each report against rule 6
rather than against the file list.

Reference sources: `Z:\harmony\sources\AE2-original` (modern upstream, the reference implementation),
`Z:\harmony\sources\ae-gtnh` and `Z:\harmony\sources\AE2FluidCraft-Rework-Unofficial` (older ancestors of
this fork), `Z:\harmony\sources\HadEnoughItems` (HEI — this fork targets HEI, not JEI).

The Ukrainian-language research wiki that preceded this port is at `Z:\harmony\wiki\AE2UD` (outside git).
