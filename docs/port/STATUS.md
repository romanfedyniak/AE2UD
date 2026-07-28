# Port status — resume here

Companion to `CONTRACT.md`. The contract is the *spec*; this file is the *bookmark*. Last updated after
wave 4, 2026-07-29.

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
| `814b8a864` | — | wave 4 prerequisites: `GridInventoryEntry`, `extractItemsByRecipe` takes `AEKeyFilter` |
| `b7c364a6a` | — | wave 4 cross-agent signatures fixed in the contract |
| `f6c12d6c0` | 4 | `appeng.container`, `appeng.client`, `appeng.core.sync.packets` (51 files) |

## Where the work stands

Done: waves 0-4. No non-comment references to the old model remain in `appeng.util`, `appeng.me`,
`appeng.crafting`, `appeng.tile`, `appeng.helpers`, `appeng.core`, `appeng.parts`, `appeng.items`,
`appeng.recipes`, `appeng.container` or `appeng.client`.

Remaining broken files, by package:

| Wave | Packages | Files |
|---|---|---:|
| 5 | `fluids/*` (parts 9, util 8, container 8, client 6, helper 3, registries 1, items 1) | 36 |
| 6 | `integration/modules` 7, plus the HEI dependency swap | 7 |

The scattered single hits in `me/*`, `crafting/*`, `util/*`, `tile/*`, `parts/*`, `items/*` are comments
and one class that is merely *named* `IMEInventoryDestination`. They are not work.

## How to check where you are

Two commands. Nothing else in this repo tells you the truth during the migration.

```sh
# The only gate that works until wave 6. Must be green at every commit.
./gradlew compileApiJava

# A wave is finished when this prints nothing for its packages.
grep -rnE "\b(IAEStack|IAEItemStack|IAEFluidStack|IStorageChannel|IMEInventory|IMEInventoryHandler|IItemList|IItemContainer|IMEMonitor|IItemStorageChannel|IFluidStorageChannel|IStorageMonitorable|IStorageGrid|IStackWatcherHost|IBaseMonitor|ICellProvider|ICellContainer|ICellRegistry|ICellInventory|ICellInventoryHandler|IMEMonitorHandlerReceiver|AEItemStack|AEFluidStack|ItemList|FluidList)\b" \
  src/main/java/appeng/<package> --include=*.java | grep -vE ":[0-9]+:\s*(\*|//|/\*)"
```

**Use that pattern, not a shorter one.** Wave 4's file list was drawn up with a pattern that only listed
the deleted *interfaces*; `AEItemStack` and `AEFluidStack` are deleted *classes* and were missed, so the
wave was planned as 45 files and turned out to be 51. The five stragglers (`GuiPatternTerm`,
`GuiExpandedProcessingPatternTerm`, `GuiUpgradeable`, `PacketTargetItemStack`, `PacketTargetFluidStack`)
were only found by re-running the scan with the full list above.

## Wave 4 — done, and what it left behind

51 files across four parallel agents (packets 11 / crafting containers 7 / storage containers 8 /
client 24), plus two prerequisites done by hand first because more than one agent met at them:
`appeng.container.me.GridInventoryEntry` and `Platform.extractItemsByRecipe` taking an `AEKeyFilter`.
Both are documented in `CONTRACT.md` §9's "Wave 4 prerequisites" subsection, along with the cross-agent
signatures that were fixed up front. Per-agent detail is in §9's Wave 4a-4d entries.

### Open question for the owner — the wireless terminal's live updates

`ContainerMEMonitorable` splits on `host instanceof AbstractPartTerminal`: part-based terminals get real
push (the part holds the `IStackWatcher` and relays `onStackChange` to attached containers, because
`GridStorageCache.addNode` only ever installs a watcher on a node's *machine* and a container has no node
of its own); everything else gets the §10 case-2 per-tick diff.

That puts the **wireless terminal** in case 2, and §10 justified case 2 on the grounds that the snapshot
covers one cell rather than a whole network. For the portable cell, the ME chest and the security station
that holds. For the wireless terminal it does not: `MEStorage.getAvailableStacks()` allocates a fresh
`KeyCounter` and walks the entire network storage, once per tick per open wireless terminal. The mechanic
is intact and the diff is correct — the cost profile is what changed, and it changed outside what §10
sanctioned. Options, none of them applied yet:

1. Leave it. Invisible on small networks, a full storage walk per tick on large ones.
2. Apply the same relay trick to `TileWireless` / `TileQuantumBridge` — they are the machines behind the
   node `WirelessTerminalGuiObject.getActionableNode()` returns, so no API change is needed. Semantically
   odd (an access point watching storage for someone else's GUI) but it follows an established pattern.
3. Give `IStorageService` a way to register a node-less watcher. Cleanest conceptually; a frozen-API
   change, so owner approval required.

Related and unresolved: there is no craftable-flag watcher, so `computeCraftables()` runs every tick on
**both** paths, including the push path.

### Debt wave 4 handed to wave 5

Wave 4 wrote against these `appeng.fluids` shapes; wave 5 must produce them:
- `IAEFluidTank.getFluidInSlot(int)` / `setFluidInSlot(int, GenericStack)` become `GenericStack`-typed
  (mirrors the `AppEngInternalAEInventory.getAEStackInSlot(int): GenericStack` rename from wave 2).
  `ContainerFluidInterfaceConfigurationTerminal` and `GuiFluidInterfaceConfigurationTerminal` already
  call it that way, and both halves round-trip NBT through `GenericStack.writeTag`/`readTag`.
- `IFluidSyncContainer.receiveFluidSlots(Map<Integer, GenericStack>)`, and `FluidSyncHelper` to match.
- `IMEFluidSlot.getAEFluidStack()` becomes `getGenericStack()` (mirrors `ItemSlot`'s wave 1a rename).
- `FluidSorters`' comparators become `Comparator<Object2LongMap.Entry<AEKey>>` (mirrors `ItemSorters`).
- `FluidStackSizeRenderer` needs a `(FontRenderer, GenericStack, int, int)` overload.
- `ContainerFluidTerminal` / `ContainerWirelessFluidTerminal` / `ContainerFluidInterface` /
  `ContainerMEPortableFluidCell`: `setTargetStack(AEKey)`, and `postUpdate(List<GridInventoryEntry>)` on
  the fluid terminals. `PacketMEFluidInventoryUpdate` was deliberately **kept as its own class** rather
  than merged into `PacketMEInventoryUpdate`; merging them is a later decision, not wave 5's to take
  silently.

### Still not delivered from wave 4's own brief

- `appeng.items.misc.WrappedGenericStack` still has **no client-side model or texture**, so a wrapped
  non-item key in a slot renders as a missing texture. The precedent to follow is
  `FluidDummyItem`/`FluidDummyItemRendering`: GUI code draws the wrapped content's own icon rather than
  giving the placeholder item a texture of its own.
- The `AEKeyType` button icons from wave 2 are still placeholders, and nothing in `appeng.client` calls
  `AEKeyType`'s button-texture accessors yet — there is currently no consumer at all.
- The generic half of the multi-type filter GUI *is* done: every slot, repo and render path in
  `appeng.client` is now generic over `AEKey`/`GenericStack`, so once wave 5 registers the fluid
  strategies the same terminal shows fluid rows with no further client change.

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
CleanroomMC fork of JEI), and get `gradlew build` green.

## Before starting wave 5 — read this

### How terminal live updates ended up working

`CONTRACT.md` §10's two cases were both implemented in wave 4. Case 1 (real push) required one addition
outside the wave's file list: `appeng.parts.reporting.AbstractPartTerminal` now implements
`IStorageWatcherNode` and relays `onStackChange` to attached containers, because `GridStorageCache.addNode`
only ever installs a watcher on a node's *machine* — a container is not a grid machine and has no node.
Case 2 (server-side per-tick diff) covers the portable cell, the ME chest, the security station and — see
the open question above — the wireless terminal. Neither case needed an addition to the frozen API.

Wave 5's fluid terminals inherit this: `ContainerFluidTerminal` and `ContainerMEPortableFluidCell` face
the same split, and a fluid terminal part that extends `AbstractPartTerminal` gets case 1 for free.

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
Real instances found so far: `CraftingTreeProcess.addProcess()/getTimes()` (wave 2),
`ToolColorApplicator.findNextColor()` (wave 3d), several in `PartConversionMonitor`/`insertItem`
(wave 3b), and `GuiFluidInterfaceConfigurationTerminal.matchedStacks` (wave 4d, where a tank's amount can
drift between the search pass and the redraw and would spuriously un-match a still-matching slot).
Every remaining wave must check this. Note the inverse also exists and is correct:
`ContainerFluidInterfaceConfigurationTerminal`'s server-vs-client change detection *should* compare whole
`GenericStack`s, because there an amount change is exactly what it is looking for.

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

**Wave 4 added a stronger version of that second rule, and it is worth keeping.** Where several agents met
at one type, the type was *written by hand before the wave started* and the meeting signatures were pinned
in `CONTRACT.md` §9 as "prerequisites", not merely described in the briefs. Four agents then implemented
and called them with zero drift and zero reported api gaps. The rule of thumb: if a shared type is small
enough to write in one sitting, write it rather than specifying it.

**Verify the file list with the full deleted-symbol pattern before splitting it.** Wave 4 was planned from
a pattern matching only deleted *interfaces* and came out six files short — see the pattern under "How to
check where you are".

**Expect agents to step outside their file list occasionally, and check whether they were right to.** In
wave 3 one agent adapted `PartIdentityAnnihilationPlane`; in wave 4 another added `IStorageWatcherNode` to
`AbstractPartTerminal`. Both were correct and both would have silently broken a mechanic if the agent had
obeyed its list literally. Review each report against rule 6 rather than against the file list.

**Check the seams between agents yourself afterwards.** Wave 4's two halves of the fluid interface
configuration terminal disagreed on the NBT wire format — the container still wrote through the old
`AEFluidStack.writeToNBT` while the screen already read through `GenericStack.readTag`. Neither agent was
wrong within its own file list, and with no compiler there is nothing but a manual read to catch it. Run
the deleted-symbol scan over the finished wave's packages before committing.

Reference sources to keep checked out locally: modern upstream Applied Energistics 2 (the reference
implementation for every ported class), the AE2 GTNH fork and AE2FluidCraft-Rework-Unofficial (older
ancestors of this fork, for 1.12.2-era API shapes), and HadEnoughItems (this fork targets HEI, not JEI).
