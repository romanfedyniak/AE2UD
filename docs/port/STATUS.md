# Port status — resume here

Companion to `CONTRACT.md`. The contract is the *spec*; this file is the *bookmark*. Last updated after the
first play-test pass, 2026-07-29.

Branch: `feature/generic-storage`.

## What this port is

Replacing AE2UD's old generic `IAEStack<T>` / `IStorageChannel<T>` / `IMEInventory<T>` storage model with
the type-erased `AEKey` / `AEKeyType` / `MEStorage` / `GenericStack` / `KeyCounter` model copied from
modern upstream AE2, plus the strategy layer (`StackWorldBehaviors` + five strategy interfaces) that lets
one bus class serve every registered key type.

The migration was **big-bang**: `src/main` did not compile from wave 1 until wave 6. That was deliberate,
and it is over — `gradlew build` is green. Throughout, the only gate that worked was `gradlew compileApiJava`
(`src/api` is a separate Gradle source set that compiles independently), so `CONTRACT.md` stood in for the
compiler. §4 is the frozen api surface; §9 is the class-by-class registry of what every wave produced.
Anyone continuing this work reads §9 to find out what the code they are calling actually looks like — that
does not stop being true now that the build works.

**What the big-bang approach actually cost is measured**, once, at the end: 26 compile errors across five
waves' worth of code, listed under "What the first green build cost" below. Whether that is a good trade is
now answerable rather than a matter of opinion.

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
| `0b0c253b9` | — | wave 5 prerequisites: the six fluid types the agents meet at |
| `e85dcaf31` | — | wave 5 cross-agent surfaces pinned in the contract |
| `5d757ea55` | 5 | `appeng.fluids` (38 files), plus the fluid strategy registrations |
| `2f1818b92` | — | per-type fuzzy documented; the formation-plane filter trap documented |
| `e1ce42413` | 6 | `integration/modules` (7 files), the HEI swap, and the 26 fixes the first compile exposed |
| `6b7caf5e0` | — | first in-game launch: creative cell was being claimed by the wrong cell handler |
| `78e79e257` | — | second launch: the generic stack wrapper had no model |
| `b53e12f36` | — | fluid callback dispatch, cell partition preference, fluid amounts |
| `5c588f12a` | — | terminal counts move while shift freezes the row order |
| `ed3b47d31` | — | crafting CPU released on completion; toast identity |
| `6211b41d5` | — | craftables sent on terminal open; crafting progress counts down |

## Where the work stands

**All seven waves are done. `gradlew build` is green** — `src/main` compiles, the tests pass, and the
reobfuscated jar builds. The migration phase is over; what is left is play-testing and the follow-up work
listed below, not porting.

The deleted-symbol scan over the whole of `src/main/java/appeng` returns nothing. One class is merely
*named* `IMEInventoryDestination` and matches the scan pattern by accident; it is not work.

**The first play-test pass is done**: nine defects found and fixed, none of which any scan could have
caught. See "In-game testing" below — the table is worth reading before writing more code, because three of
the nine were the same kind of silent mistranslation and there are certainly more.

Still unplayed: patterns and interfaces, P2P, spatial storage, the ore-dictionary storage bus, and every
terminal variant except the wall terminal. The "at-risk features" inventory in `CONTRACT.md` §10 is the list
to walk.

## How to check where you are

Two commands. The first is now the real one; the second is what told the truth *during* the migration and
is still the way to check a package is clean.

```sh
# The gate, now that main compiles. Was unusable from wave 1 to wave 6.
./gradlew build

# A package is clean when this prints nothing for it. Prints nothing anywhere now.
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

### Planned, not yet implemented — cheapen the case-2 terminal diff

**The situation.** `ContainerMEMonitorable` splits on `host instanceof AbstractPartTerminal`: part-based
terminals get real push (the part holds the `IStackWatcher` and relays `onStackChange` to attached
containers, because `GridStorageCache.addNode` only ever installs a watcher on a node's *machine* and a
container has no node of its own); everything else gets the §10 case-2 per-tick diff. That puts the
**wireless terminal** in case 2, where §10's justification for case 2 — "the snapshot covers one cell, not
a whole network" — does not hold. `MEStorage.getAvailableStacks()` allocates a fresh `KeyCounter` and walks
every mount, once per tick per open terminal.

**What upstream actually does, which settles the framing.** Upstream's `MEStorageMenu.broadcastChanges()`
calls `storage.getAvailableStacks()` plus `getCraftablesFromGrid()` **every tick for every terminal**,
including the wall-mounted network terminal, and pointedly does *not* use its own
`IStorageService.getCachedInventory()` there. So case 2 is not a regression against the reference
implementation — it *is* the reference implementation — and AE2UD's part-terminal push path is already
strictly better than upstream. The earlier framing of this as "a cost regression outside what §10
sanctioned" was wrong: nothing regressed against upstream, and the mechanic was never at risk.

**The plan.** Keep case 2. Do not add a watcher relay to `TileWireless`/`TileQuantumBridge`, and do not
touch the frozen API — both of those options are dropped. Instead take the cheap win upstream leaves on
the table:

1. In `ContainerMEMonitorable`'s case-2 branch, when the host is network-backed (`networkNode != null` and
   the grid has an `IStorageService`), source the snapshot from `IStorageService.getCachedInventory()`
   instead of `MEStorage.getAvailableStacks()`. `GridStorageCache` maintains that counter behind a
   `cachedStacksNeedUpdate` dirty flag, so N open terminals cost one recompute per invalidation instead of
   N full walks over every drive, cell and storage bus. Hosts that view a cell directly (portable cell, ME
   chest, security station) keep `getAvailableStacks()` — there is no service to ask and the walk is one
   cell deep, exactly as §10 assumed.
2. **`getCachedInventory()` returns the service's own mutable `KeyCounter`.** Never store that reference as
   `previousAvailableStacks` — copy it. Storing the reference would make the diff compare the object with
   itself and the terminal would silently stop updating.
3. Optional, same shape: `CraftingGridCache.getCraftables(AEKeyFilter)` builds a fresh `HashSet` over
   `craftableItems.keySet()` + `emitableItems` on every call, and `computeCraftables()` calls it every tick
   on **both** paths, including the push path, because no craftable-flag watcher exists. Caching an
   immutable view inside `CraftingGridCache`, invalidated where that map is mutated, removes the per-tick
   allocation for every open terminal at once. Upstream has the same cost, so this is optional polish.

Nothing here changes behaviour a player can observe; it is purely how the same delta is computed.

### Debt wave 4 handed to wave 5 — all discharged

Every `appeng.fluids` shape wave 4 wrote against was produced by wave 5, most of them by hand before the
wave started. One decision from that list is still open and was deliberately not taken:
`PacketMEFluidInventoryUpdate` remains **its own class** rather than being merged into
`PacketMEInventoryUpdate`. Merging them is a later call, and no wave has been authorised to take it
silently.

### Still not delivered from wave 4's own brief

- ~~`appeng.items.misc.WrappedGenericStack` has no client-side model~~ — **done in `78e79e257`**, after the
  second in-game launch logged a missing model for `wrapped_generic_stack#inventory`. It follows
  `FluidDummyItem`'s three pieces exactly (built-in `IModel`, dispatching baked model,
  `ItemRenderingCustomizer`), and what to draw is decided by the key type rather than by the renderer: a
  fluid key draws its own still texture, an item key its own model, anything else
  `AEKeyType.getButtonIcon()`.
- The `AEKeyType` button icons from wave 2 are still placeholder vanilla items (a chest, a water bucket),
  but they are **no longer unused**: the wrapper's model is their first consumer, so a new key type now gets
  a working placeholder icon out of the accessor it already had to implement. The button *texture* accessor
  still has no caller.
- The generic half of the multi-type filter GUI *is* done: every slot, repo and render path in
  `appeng.client` is now generic over `AEKey`/`GenericStack`, so once wave 5 registers the fluid
  strategies the same terminal shows fluid rows with no further client change.

## Wave 5 — done, and what it left behind

38 files across four parallel agents (parts+registries+items 11 / util+helper 8 / container 6 / client 5),
plus six prerequisites done by hand first because more than one agent met at them: `IAEFluidTank`,
`IFluidSyncContainer`, `IMEFluidSlot`, `FluidSyncHelper`, `FluidSorters` and `FluidStackSizeRenderer`.
Both are documented in `CONTRACT.md` §9's "Wave 5 prerequisites" subsection; per-agent detail is in §9's
Wave 5a-5d entries. The strategy layer paid off exactly as planned: registering the fluid import, export,
placement, pickup and external-storage strategies is all it took to make the *already migrated* generic
buses and storage bus serve fluids, and no fluid branch was added to any wave-3 part.

### The one design trap wave 5 uncovered — read this before writing another key type

The frozen `PartAbstractFormationPlane.getConfigInventory()` returns a concrete
`AppEngInternalAEInventory`, and its wave-3a javadoc claimed any key type could live there via
`GenericStack.wrapInItemStack`. **That was wrong.** `AppEngInternalAEInventory.setStackInSlot`/`insertItem`
build slot contents with `GenericStack.fromItemStack`, which never unwraps a placeholder stack and always
yields an `AEItemKey`. A fluid filter populated through the `IItemHandler` surface would therefore have
silently become a permanent no-op — the plane would place everything, and nothing would have reported an
error. Agent 5-A caught it and worked around it by keeping the real `AEFluidInventory` GUI-facing and
mirroring it into a private `AppEngInternalAEInventory` through `GenericStack.writeTag`/`readFromNBT`,
which are type-erased. The misleading javadoc is now corrected in place with the full explanation.

The general lesson: **the slots can hold any key; the item-handler mutation surface cannot put one there.**

### Also settled in wave 5

- **The Fuzzy Card is decided per key type, not per part** — `AEKeyType.supportsFuzzyRangeSearch()` plus
  `AEKey.getFuzzySearchValue()`/`getFuzzySearchMaxValue()`, and nothing else. Full table and the two
  counter-intuitive consequences are in `CONTRACT.md` §10. Wave 5 made the legacy `PartFluidImportBus` and
  `PartFluidStorageBus` read the setting they had registered since before the port but never consulted, so
  they now match the generic buses.
- **`Platform.poweredInsert` returns the amount inserted, not the remainder** — the opposite of the old
  `IAEStack`-era signature, which returned what would not fit. Any wave-2-or-later code carrying over that
  subtraction dance is wrong. Verified correct in `DualityFluidInterface.usePlan`.
- **`AEFluidStack`, `FluidList` and `MeaningfulFluidIterator` are deleted**, matching the item-side trio
  wave 0 removed.

### Pre-existing defect found, deliberately not fixed

`PacketMEFluidInventoryUpdate.clientPacketData` dispatches only to `GuiFluidTerminal` and
`GuiWirelessFluidTerminal`, never to `GuiMEPortableFluidCell` directly — so the portable fluid cell's own
screen gets no live updates. `git show 1e855f729` confirms the dispatch list is **unchanged** since before
the migration, so this is an inherited fork defect, not a port regression. Rule 6 forbids removing
mechanics; it does not oblige this port to repair old ones. Fixing it is a one-line addition whenever
someone wants it.

## Wave 6 — done, and what the green build cost

Seven files, done by hand rather than by agents, plus the HEI swap. The prediction written here before the
wave held exactly: **the seven files were the smallest part of it.**

Five of the seven got *simpler*. The two storage-monitor and two crafting-monitor providers each carried a
`// TODO: generalize` over an item-vs-fluid `instanceof` pair; both branches collapse into
`displayed.what().getDisplayName()`, because every key type names itself. `InventoryBogoSortModule` became
`Comparator<Object2LongMap.Entry<AEKey>>` like everything in `ItemSorters`.

The two JEI helpers were the real ones. Per-agent detail is in `CONTRACT.md` §9's Wave 6 entry; the part
worth knowing here is why `appeng.integration.modules.jei.AvailableItems` had to be written instead of
reusing `KeyCounter`: **a `KeyCounter` drops any key whose amount is zero, and a zero-amount craftable entry
is exactly what paints a JEI ingredient slot blue instead of red.** It also carries no craftable flag. Where
the old code merely counted amounts (`used`) a `KeyCounter` was the right answer and was used.

`build.gradle` now pulls `mezz:jei:4.32.0` — HadEnoughItems, from `https://maven.cleanroommc.com`, which was
already a declared repository. Not one import changed, including the internal `mezz.jei.gui.*` classes
`JEIMissingItem` reaches into.

### What the first green build cost — 26 errors, none in wave 6's own files

This is the part worth reading, because it is the evidence for what a scan-driven migration cannot catch.
Every one of these compiled cleanly in the mind of the agent that wrote it and survived the deleted-symbol
scan, the seam checks and the report verification.

| What | Where | Why no scan found it |
|---|---|---|
| `ICellWorkbenchItem` moved to `appeng.api.storage.cells` in wave 0 | `SlotRestrictedInput`, `ItemMaterial` | the *name* is unchanged, only the package |
| `AEApi.instance().registries().cell()` removed (§8, item 9) — now static `StorageCells` | `SlotRestrictedInput` ×2 | the scan lists deleted *types*, not deleted *methods* |
| `List.of(…)` | `PartFluidAnnihilationPlane`, `PartIdentityAnnihilationPlane` | Jabel gives Java 17 **syntax**; the classpath is still Java 8, so Java 9 **APIs** are absent |
| `Object2LongMaps.fastIterable(…)` | `BasicCellInventory` ×3 | copied from modern upstream, which ships a newer fastutil |
| `Object2LongMap.Entry` needs the boxed `getValue`/`setValue` too | `ItemRepo`, `FluidRepo` | this fastutil still extends `Map.Entry<K, Long>` without defaults |
| `grid.getCache(IStorageService.class).getInventory()` | `ContainerCraftConfirm` | `getCache` infers its type variable from the **assignment target**; chained, it infers `IGridCache` |
| `setStackSize` called on an `AEKey` | `PacketCraftRequest` | an amount was pushed into a container field that no longer holds one |
| `setTargetStack(GenericStack)` where an `AEKey` is wanted | `GuiFluidInterface` | third instance of the same slip; wave 5 fixed two others by hand |
| `getDescription()` returning `String` | `PartAbstractFormationPlane` | `ITextComponent` is the new return type across `MEStorage` |
| missing `IPartModel` / `AEKey` imports | `PartFluidLevelEmitter`, `ContainerPatternEncoder` | plain omissions, invisible without a compiler |

The shape of the list is the lesson: **almost none of these are storage-model mistakes.** They are
platform-version mismatches (Java 8 classpath, older fastutil), API surface moves that kept their names, and
type-inference corners. A migration executed against a frozen contract instead of a compiler will produce
exactly this residue, and it is cheap to fix once — but only once something actually compiles.

## In-game testing — first pass done, nine defects found and fixed

Everything below was found by playing, after `gradlew build` was already green. **None of it would have been
caught by the deleted-symbol scan, the seam checks or the report verification** — that is the point of this
section. The build proves the types line up; nothing else does.

| # | Symptom | Root cause | Commit |
|---|---|---|---|
| 1 | Client crashed on startup indexing creative tooltips | `ItemCreativeStorageCell` was widened to `IBasicCellItem`, so `BasicCellHandler` claimed it before `CreativeCellHandler` | `6b7caf5e0` |
| 2 | Every non-item key rendered as a missing texture | `WrappedGenericStack` had no model at all | `78e79e257` |
| 3 | Fluid formation plane placed everything regardless of its filter | `AEFluidInventory` calls the 5-arg `onFluidInventoryChanged`; the interface declared it a no-op default and three parts override the 2-arg one | `b53e12f36` |
| 4 | Fluid storage bus and fluid level emitter ignored config changes until reload | same dispatch mismatch | `b53e12f36` |
| 5 | A partitioned Sticky cell accepted nothing at all | `BasicCellInventory` never answered `isPreferredStorageFor`, so the sticky pass did not claim, and the ordinary pass skips sticky mounts | `b53e12f36` |
| 6 | Terminal tooltip read `Water: 0B` and `Items Stored: 1,000` | display wrapper carried amount 1; the stored line went through a bare `NumberFormat` | `b53e12f36` |
| 7 | Shift-extraction did not visibly decrement | the view rebuild was suppressed wholesale while shift was held | `5c588f12a` |
| 8 | A finished job never released its CPU | `KeyCounter.reset()` keeps the keys and `isEmpty()` counts keys — see §9.1a | `ed3b47d31` |
| 9 | Craftables invisible in a freshly opened terminal; `10 / 10` never counted down; toast said "Air" | initial sync sent only stocked keys; `remainingItemCount` was never decremented; the toast wrapped a stack already counted down to zero | `6211b41d5`, `ed3b47d31` |

### What the pattern says

Three of the nine (3, 4, 8) are **the same kind of mistake**: a call that translated word for word and
changed meaning. §9.1 predicted this shape for `equals`; §9.1a now records the `reset()`/`isEmpty()`
instance. Expect more of them, and expect them to be silent — none of these three produced a log line.

Two more (1, 5) are **routing**: an `instanceof` somewhere else keys off an interface, and widening or
narrowing a type quietly changes who handles it. Before adding an interface to a class, grep for who tests
it. The compiler cannot see a handler-selection race.

One (9, first part) is worth remembering on its own: **an initial-sync path and an incremental-diff path can
each be individually correct and still leave a hole between them.** The constructor seeded
`previousCraftables`, so the diff had nothing to report; the initial sync only walked stocked keys. Neither
looked wrong in isolation.

Two changes here were **frozen-API edits** and need owner review under §7: §8.5
(`wrapForDisplayOrFilter()` wraps with amount 0) and the `KeyCounter.reset()` javadoc, which is
documentation only.

### Still open

- **Enhancements asked for during testing and delivered**: the completion toast names the crafted amount,
  and the crafting status header shows elapsed time instead of a moving estimate.
- **Not reproduced**: a green progress line in the crafting status screen. It does not exist in this tree
  and did not exist pre-port either; it is believed to come from an addon. The data for one exists now that
  `remainingItemCount` moves, if it is ever wanted.
- **Untested so far**: patterns and interfaces (item, processing and fluid), P2P tunnels, spatial storage,
  the ore-dictionary storage bus, and every terminal variant other than the wall terminal.

## How terminal live updates ended up working

`CONTRACT.md` §10's two cases were both implemented in wave 4. Case 1 (real push) required one addition
outside the wave's file list: `appeng.parts.reporting.AbstractPartTerminal` now implements
`IStorageWatcherNode` and relays `onStackChange` to attached containers, because `GridStorageCache.addNode`
only ever installs a watcher on a node's *machine* — a container is not a grid machine and has no node.
Case 2 (server-side per-tick diff) covers the portable cell, the ME chest, the security station and — see
the open question above — the wireless terminal. Neither case needed an addition to the frozen API.

Wave 5 confirmed the split holds for fluids: `ContainerFluidTerminal` got case 1 for free because
`PartFluidTerminal extends AbstractPartTerminal`, and `ContainerMEPortableFluidCell` is case 2
unconditionally, since an `IPortableCell` is never an `AbstractPartTerminal`.

## Standing rules that have already been broken in practice

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
   the build pass and then "fixes" unrelated packages. (No longer true after wave 6 — but if this method is
   ever reused for another big-bang migration, it is the first thing to put back.)
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

**Verify the claims in a report, do not accept them.** This is what wave 5 actually cost, and it is where
the value was. Every agent reported honestly, and the reports were still worth checking one by one:

- A claim that a mechanic *never existed* is the one most worth testing, because it is indistinguishable
  from having destroyed it. Wave 5's agent said `ContainerFluidStorageBus` never had `Settings.STICKY_MODE`
  — true, confirmed against `1e855f729`, and confirmed *meaningful* by running the same grep against the
  item-side container, which does have it. A grep that finds nothing proves nothing until you have seen it
  find something.
- A claim that a dropped call was *pure optimisation* needs the control flow traced, not skimmed. Wave 5
  removed a `findPrecise` pre-check in `DualityFluidInterface.usePlan`; the old `else if` meant a miss left
  `changed == false`, and so does a `poweredExtraction` returning 0. Equivalent — but only by reading both.
- A sign flip hides in any signature that changed meaning. `Platform.poweredInsert` used to return the
  **remainder**; it now returns the **inserted** amount. Code that carried over the old subtraction would
  compile and silently misbehave.
- When an agent works around a frozen shape, verify the workaround's own seam. Wave 5's formation-plane
  fix round-trips through NBT; it only works because both sides agree on `"#" + slot` and on
  `GenericStack.writeTag`/`readTag`. Had the tag naming differed, the filter would have gone silently
  empty — the exact regression the workaround existed to prevent.

**When a wave finds that a frozen javadoc is wrong, fix the javadoc in place.** Wave 5 found that
`PartAbstractFormationPlane.getConfigInventory()` documented a capability the class does not have. Leaving
that for a later wave to rediscover is how the same trap gets sprung twice.

Reference sources to keep checked out locally: modern upstream Applied Energistics 2 (the reference
implementation for every ported class), the AE2 GTNH fork and AE2FluidCraft-Rework-Unofficial (older
ancestors of this fork, for 1.12.2-era API shapes), and HadEnoughItems (this fork targets HEI, not JEI).
