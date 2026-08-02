# Port status — resume here

Companion to `CONTRACT.md`. The contract is the *spec*; this file is the *bookmark*. Last updated
2026-08-02, after the item-based upgrade-card API.

**The port is done and the follow-up list is empty.** All seven waves, the `appeng.fluids` decomposition
and the play-testing are finished; `feature/generic-storage` is merged, and so is everything under "After
the merge" below. Read §9 of `CONTRACT.md` before calling into any of this — it is the class-by-class
record of what each wave actually built, and the api it describes is the api that shipped.

One thing is open and **it is not code we can write today**: registering AE2UD as an HEI
`ISlotIngredientProvider`, which waits on a released HEI carrying the api the owner PR'd. It is described
where it belongs, below.

## Post-release terminal pins

Standard wired and wireless storage, crafting, pattern, and expanded processing-pattern terminals now
support two fixed-order sections above their ordinary contents: live crafting pins followed by persistent
player pins. The automatic-pin behaviour is adapted from modern AE2's `PinnedKeys`, terminal repo, and
crafting-start packet flow. The split sections, 16-row controls, pin-slot interaction, and per-terminal
per-player persistence are adapted from GTNH AE2 Unofficial's `PinList`, `PinsHolder`,
`PacketPinsUpdate`, and `GuiMEMonitorable`. AE2UD deliberately keeps crafting jobs network-derived rather
than persisting them alongside player pins.

The server performs an initial `ICraftingGrid` snapshot, subscribes to built-in `CraftingCPUCluster`
change notifications, and reconciles every 20 ticks for addon CPU implementations that cannot provide
  that internal listener. Matching jobs are aggregated with Guava saturating addition. Crafting rows are
  created only after a matching job becomes active; finished or cancelled pins become inactive and remain
  in their stable positions until the terminal closes. A newly opened terminal starts from active jobs only.
  Visible pin keys bypass search, sorting, view cells, view mode, and key-type filtering and are removed
  from the ordinary terminal list.

  The server pushes the initial pin snapshot as soon as the container listener is attached. The packet carries
  the window ID and can wait client-side for that container, so its rows are present before the GUI's first layout
  instead of appearing after a request/response round trip.

The public API consists of `ITerminalPinHost`, `ITerminalPinStorage`, `IPlayerTerminalPins`, and
`TerminalPinStorages`. It serializes generic `AEKey`s, stores player state by UUID, offers host-NBT and
item-NBT factories, and lets addon hosts using the standard terminal container opt in without depending
on internal implementation classes. See `docs/TERMINAL_PIN_API.md`.

## Post-release item-based upgrade API

The fixed `Upgrades` enum and `IUpgradeModule` marker are replaced by `IUpgradeRegistry` associations
keyed by item and metadata. Addons can register exact cards and hosts, create filtered upgrade inventories
through `UpgradeInventories`, and query installed cards by stack. Speed and capacity are independent,
combinable traits: custom cards declare positive points, standard-compatible hosts can be inherited
automatically, and duplicate conflicts fail during loading. Card NBT is deliberately ignored.

Speed points are uncapped apart from physical upgrade slots. Existing values are preserved for standard
cards, larger values extend each machine's old curve, power use follows the resulting work, and Guava
saturating arithmetic prevents wraparound. Matter Cannon compatibility is exact-only for custom speed
cards to avoid accidental unbounded shot loops. Capacity points are capped by the host's registered limit,
so a stronger card can replace several standard cards without exposing nonexistent filter slots. Forge
tooltips describe card points and compatible hosts for AE2UD and addon items alike. See `docs/UPGRADE_API.md`.

## Post-release quantum bridge startup

`QuantumCluster.isActive()` now requires a registered, intact cluster with an entangled singularity, but
does not require its local grid to be powered before creating the inter-grid connection. This ports
upstream fix `82bf06333` ("Don't require power to connect QNBs"). The connection can therefore form with
zero power; once either side receives energy, the merged grid powers both sides. Each bridge keeps its
existing 22 AE/t idle drain, so the change removes only the circular startup requirement and does not make
the link free.

## Post-release interface-terminal HEI exclusions

The wired and wireless Interface Terminal now derive one HEI exclusion rectangle from the actual
four-button side column instead of reserving one hard-coded stale position. The Interface Configuration
Terminal likewise exposes its terminal-style button. Because the rectangles use the initialized button
coordinates, terminal-style height changes and the Interface Terminal's JEI padding cannot desynchronise
the exclusion areas from what is rendered.

## Post-release import bus inverter support

The generic import bus now accepts one Inverter Card and follows modern AE2 semantics: a
non-empty configured filter becomes a blacklist, while an empty filter continues to import everything.
`StackTransferContextImpl.getFilter()` exposes the effective whitelist/blacklist predicate to every
registered import strategy, so fluids and addon key types inherit the behaviour automatically. The item
strategy handles the non-enumerable blacklist by scanning for any candidate accepted by that predicate;
item-handler and Storage Drawers adaptors continue past rejected candidates. The fluid strategy likewise
walks all advertised tanks instead of failing when the first fluid is rejected. Fuzzy matching remains
baked into the partition list, so an inverted fuzzy filter blacklists every matching variant.

## Post-release terminal type filter

Network-backed part terminals and wireless storage, crafting, and pattern terminals now implement
`KeyTypeSelectionHost`. Each terminal persists its own set of visible registered `AEKeyType`s and opens the
same extensible selection screen as the import bus; the display-only setting does not require BUILD
permission. The server filters the initial listing, watcher updates, snapshot diffs, and craftable-only rows,
so a hidden type cannot reappear when its amount or crafting availability changes. The standard portable
cell remains unchanged because it can only display its own item key type.

## Post-release terminal-style follow-up

The terminal layout setting now follows modern AE2 semantics: Small, Medium, Tall, and Full-Height use
25%, 50%, 75%, and 100% of the rows available to each screen. The shared client setting is used by ME
terminals, both interface terminal families, crafting status and confirmation, and network status while
preserving each legacy screen's safe minimum row count. The old Full horizontal expansion was removed.

The Interface Configuration Terminal stretches its list body and scrollbar trough separately. Repeating
the old fixed-height texture as one piece repeated the trough's top edge on every row and made the track
look like a column of slots.

Portable cells expose `IPortableCell.getTerminalRowLimit()`. Basic cell items derive that limit from their
type capacity, so the standard 27-type portable cell stays fixed at three rows with no meaningless style
button, while larger addon portable cells receive the same style control automatically.

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
| `820798123` | — | world unload no longer aborts when a node is mid-propagation |
| `a41e140bb` | — | terminal sort made a total order; partition tooltip names the item |
| `279b73791` | — | Identity Annihilation Plane removed, api definition included |
| `daf3523d9` | — | the fork's own HEI bookmark handler removed (revertable experiment) |
| — | — | shift-place moved onto HEI's `quickMove` hook |

## Where the work stands

**All seven waves are done. `gradlew build` is green** — `src/main` compiles, the tests pass, and the
reobfuscated jar builds. The migration phase is over; what is left is play-testing and the follow-up work
listed below, not porting.

The deleted-symbol scan over the whole of `src/main/java/appeng` returns nothing. Three classes used to
match it by name alone, their `I` prefixes naming the deleted `IMEInventory` rather than an interface; they
are `MEStorageAdaptor`, `MEStorageAdaptorIterator` and `MEStorageDestination` now.

**All seven waves are committed, the post-v1 `appeng.fluids` decomposition is finished, and every entry in
the `CONTRACT.md` §10 "at-risk features" inventory has been walked in game.** The last two - P2P tunnels
and spatial storage - were cleared at the end; P2P turned up one defect, which is pre-existing and recorded
under "Still open".

The first play-test pass found nine defects, none of which any scan could have
caught. See "In-game testing" below — the table is worth reading before writing more code, because three of
the nine were the same kind of silent mistranslation and there are certainly more.

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

### Cheapen the case-2 terminal diff (done, awaiting a play-test)

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

**What was built, and where step 3 stopped.** Steps 1 and 2 are done. The lookup that answers "does this
terminal show the network or one cell" is now `networkStorageService()`, which returns the service itself
rather than a boolean, so the three callers that each re-did the grid-then-cache dance share one. On top of
it sit two readers, and the pair is the whole guard against step 2's hazard:

- `readAvailableStacks()` - **may hand back the service's own live counter.** For reading within the tick:
  the initial full listing in `queueInventory`, and `getCachedAmount`.
- `retainAvailableStacks()` - always this container's own object. Used by the two places that keep the
  result: the constructor's seed and the case-2 diff. A cell read needs no copy, since
  `MEStorage.getAvailableStacks()` already builds a fresh counter per call; only the network read is copied.

Splitting the two was deliberate. One method plus a comment saying "copy this if you keep it" is exactly the
shape of mistake §9.1 catalogues - it compiles, it runs, and the terminal silently stops updating.

**And it uncovered a regression from `25d09eb03`, which is the more important half of this change.** The
wireless terminal had **no craftable rows at all**, and had had none since that commit narrowed
`computeCraftables()` from "attached to a grid" to `monitorsNetworkInventory()`. The identity test itself
is right; what was wrong is that `WirelessTerminalGuiObject.getInventory()` answered `this` rather than the
grid's inventory, so the comparison could never succeed and the wireless terminal was classified as
cell-only. The javadoc added with that commit asserted the opposite - that the wireless terminal "hands
back exactly that object" - which is exactly the kind of claim worth checking rather than believing.

Fixed where it was wrong: `getInventory()` now returns `networkStorage`, falling back to `this` only when
there is no link. The wrapper's `insert`/`extract`/`getAvailableStacks` are pure delegations to that same
object, so nothing about storage changes - and note that until this, `monitorsNetworkInventory()` was false
for the wireless terminal, so steps 1 and 2 above were a no-op on the one host they were written for.

Worth keeping as a pattern: **narrowing a condition is a deletion, and it needs the same "who did this used
to cover" audit that deleting a class does.** The security station was the case in mind and it was fixed;
the wireless terminal was collateral and nothing failed loudly enough to notice.

**Step 3 is done too, 2026-07-31, once the owner chose the api.** It needed one, because the saving only
lands for a caller passing `AEKeyFilter.all()` and there is no way to recognise that filter - `all()`
returns a fresh `what -> true` lambda, so an identity test against it relies on unspecified JVM lambda
caching. A no-argument `default ICraftingGrid.getCraftables()` was added instead; the reasoning, and the
`AEKeyFilter.ALL` alternative that was rejected, are in CONTRACT.md §8.6.

Two things worth carrying forward from doing it. The **real** saving turned out not to be the `HashSet`
allocation but the diff that follows it: an immutable cached set lets the container skip its craftable diff
by identity, so a tick where no player edited a pattern costs nothing at all instead of O(N). And the sweep
turned up something bigger sitting beside it - `CraftingGridCache` had never overridden
`default isCraftable(AEKey)`, so **a one-key question walked every pattern in the network**, with
`DualityInterface` asking it per slot per update whenever an extraction came up short. That needed no api
decision at all: the method was `default` precisely so an implementation could answer it directly.
**A `default` written for correctness is not a `default` anyone measured** - worth a look wherever else the
api leaves one in place.

Nothing here changes behaviour a player can observe; it is purely how the same delta is computed.

### Debt wave 4 handed to wave 5 — all discharged

Every `appeng.fluids` shape wave 4 wrote against was produced by wave 5, most of them by hand before the
wave started. One decision from that list was left open at the time - whether `PacketMEFluidInventoryUpdate`
should be merged into `PacketMEInventoryUpdate` - and **it answered itself**: stage 2 deleted the fluid
terminals, and the packet went with them (see the deletion list further down). There is one inventory-update
packet, `GridInventoryEntry`-based, serving every terminal. Nothing to merge.

Left here as a warning about this document rather than about the code: the entry sat open for a fortnight
after the class it discussed had stopped existing, and was read back as live work. **A "still open" note is
a claim about the present that nothing re-checks.** Deleting something means walking the notes that named it.

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

### HEI bookmarks — the fork's own handler was removed, deliberately

AE GUIs would not let a click on a bookmarked ingredient open its recipe, while other mods would. The cause
was `ClientHelper.MouseClickEvent`, which cancelled the entire mouse-input event whenever the cursor sat
over a bookmarked ingredient with shift or left-button down — and `AEBaseGui` refreshed that ingredient
every frame the mouse hovered the overlay, so in practice every left click there was swallowed before HEI
saw it. Removed in `daf3523d9` along with the parallel bookmark implementation it existed to serve
(`bookmarkedJEIghostItem`, `drawTargets`, the cursor-carry state machine in `handleMouseClick`).

The standard ghost-drag path is untouched: `AEGuiHandler`, `IJEIGhostIngredients` and every
`getPhantomTargets` are still registered, and dragging from the ingredient list still works.

**Two behaviours that look like bugs and are not:**

- *Dragging from the bookmark list into a filter does nothing.* It does nothing in other mods either, so it
  is HEI's own behaviour, not something this code ever provided.
- *Shift-clicking a bookmark both filled the pattern slot and left a ghost on the cursor.* **Fixed** — this
  one was ours after all. The shift-place was done inside `AEGuiHandler.getTargets`, which is a query, and
  which HEI calls *while starting the drag it is about to run*: the ingredient got placed and then dragged.
  HEI has a hook meant exactly for this, `IGhostIngredientHandler.quickMove`, called first and skipping the
  drag entirely when it returns true. It did not exist in JEI 4.16, which this code was written against; it
  arrived in HEI 4.30.2, so the wave 6 dependency swap is what made the proper fix available.

### Second play-test pass — what it covered

Confirmed working in game: processing patterns, Pattern Expansion Card, interface blocking mode, the fluid
interface configuration terminal, the storage bus over external inventories (items and fluids at once), the
ore-dictionary storage bus, every terminal variant, Magnet and Quantum Link cards, the item formation plane
in both block and item modes with its filters, cell partitioning and the Fuzzy Card, and the cell workbench.

Fixed from that pass: the world-unload crash (`820798123`), the unstable terminal sort and the
ore-dictionary partition tooltip (`a41e140bb`), and the two HEI bookmark defects above.

### Still open

- ~~**Missing feature: fluids cannot be put into a pattern.**~~ **Done** - encoding, planning, display and
  execution, the last of those through `ICraftingMedium.pushPattern`'s extra-ingredients overload. See
  "Fluids as a crafting *ingredient*".
- ~~**Requested: the Magnet Card should pick up into the network.**~~ **Done**, see "After the merge".
- ~~**Requested: fill and empty a fluid container against the *generic* terminal.**~~ **Done** in stage 1b
  as `ContainerItemStrategy`, which is why the legacy fluid terminal could then be deleted. The original
  brief is kept below because it records why the strategy shape was chosen over ae-gtnh's.
  <details><summary>the original brief</summary> Clicking a fluid row while
  holding a bucket (or any `FLUID_HANDLER_ITEM_CAPABILITY` container) should extract into it, and clicking
  with a full one should deposit its contents into the network.
  **This mechanic already exists — but only on the legacy fluid parts.** `ContainerFluidTerminal.doAction`
  implements `InventoryAction.FILL_ITEM`/`EMPTY_ITEM` (LMB fills the held container, RMB empties it into the
  network) and `transferStackInSlot` drains a shift-clicked container from the player inventory.
  `ContainerMEPortableFluidCell` and `ContainerFluidInterface` carry the same three blocks. The generic
  terminal has none of it: `AEBaseGui`'s `SlotME` branch never sends either action, and `EMPTY_ITEM` is
  wired only for *fake* (filter) slots.
  Owner's instruction: **look at how ae-gtnh and modern upstream do it before writing anything.** Modern
  upstream's answer is a per-key-type `ContainerItemStrategy` (fill/extract/`getContainedStack`), which is
  the right shape here too — it puts the behaviour on the key type rather than on the terminal, matching the
  rule wave 5 settled. Copying `ContainerFluidTerminal`'s body into `ContainerMEMonitorable` would work and
  would be the wrong shape.
  </details>
- **Owner decision, recorded so it stops coming back: the annihilation plane may break an adjacent cable
  bus, and that stays.** `ItemPickupStrategy.canHandleBlock` excludes only air, liquids, bedrock, the end
  portal and its frame, command blocks, unbreakable blocks and protected chunks - a cable bus is an
  ordinary breakable block and always was. Byte-for-byte the pre-port method, and upstream behaves the same.
  Adding an `IGridHost` exclusion was offered and declined; a plane placed on a cable will eat the cable
  next to it, and that is accepted behaviour rather than a bug to file again.
- **Implemented from AE2 GTNH**: each active Crafting Status CPU now has the red-yellow-green progress bar;
  its tooltip labels the craft and remaining output, shows completed/total steps with a percentage, elapsed
  time, and the requesting player (or a machine request marker). `CraftingCPUStatus` carries the elapsed time
  and requester in its existing NBT packet.
- **Implemented from AE2 GTNH, with mixed rows improved**: Crafting Confirmation shows `Steps` for every
  pattern-produced key and `Used` as the percentage of that key's stock at calculation start that the order
  consumes. Unlike GTNH, `Used` remains visible when the same row also has `To Craft` or `Missing`. The value
  uses GTNH's blue/green/orange/dark-red thresholds and travels in the container-specific metadata field of
  `GridInventoryEntry`; item and fluid amounts still use their key type's formatter.
- ~~**Untested**: P2P tunnels and spatial storage.~~ **Both play-tested and working.** P2P turned up one
  defect, immediately below; spatial storage came through clean.
- **Known limitation, owner decided to leave it: only one IC2 power P2P tunnel per cable bus works.**
  Found while play-testing P2P. Not a port regression - nothing in `appeng.parts.p2p` changed on this
  branch, and `PartP2PIC2Power` was last touched by a 2019 reformat.
  `EnergyNetLocal.registeredTiles` is a `Map<BlockPos, Tile>`: IC2 allows **one energy tile per block
  position**. AE2 registers a `SinkSource` per *part*, all at the cable bus' position, so the second IC2
  tunnel on a bus is refused (`addition is conflicting with a previous registration at the same location`
  in the log). The refused part still believes it registered, because `BasicEnergyTile.onLoad()` sets
  `addedToEnet = true` right after posting the event without checking whether IC2 took it, so it never
  retries. `TileEntityCable.updateConnectivity()` then asks the *winning* delegate about the loser's face
  and gets `false`, which is why a cable will not even connect there.
  The fix, if it is ever wanted, is one delegate per cable bus dispatching by side. It works for
  `acceptsEnergyFrom`/`emitsEnergyTo`/`injectEnergy`, which all carry the side; `getOfferedEnergy()` and
  `drawEnergy()` do not, so two *output* tunnels of different frequencies on one bus could still emit
  through each other's face. IC2's api cannot express a per-side source at all - their own transformer has
  one input and one output face per block for the same reason.
  Workaround: put the tunnels on separate cable blocks.
- **Removed by owner decision**: the Identity Annihilation Plane (`279b73791`), api definition included.
  Efficiency and Unbreaking on the annihilation plane were considered for removal and deliberately kept -
  they are the reason `PickupStrategy.Factory` carries an enchantment map at all (§8.4).

## Post-v1 phase — decomposing `appeng.fluids` (in progress, resume here)

The goal, agreed with the owner: the legacy fluid parts are duplicates of parts that are already generic and
should go, because ME became universal. `appeng.fluids` is 55 files, of which only some are duplication.

### Classification

| Keep | Why |
|---|---|
| `FluidImportStrategy`, `FluidExportStrategy`, `FluidPickupStrategy`, `FluidPlacementStrategy`, `FluidHandlerAdapter` | the fluid type's implementation of the generic hooks — this is the model working |
| `BasicFluidStorageCell`, `BasicFluidCellGuiHandler` | a cell parameterised by key type is not a duplicate; the item cell has the same shape |

| Delete | Replaced by |
|---|---|
| `PartFluidImportBus`, `PartFluidExportBus`, `PartSharedFluidBus` | `PartImportBus` / `PartExportBus` |
| `PartFluidStorageBus` | `PartStorageBus` |
| `PartFluidTerminal` | `PartTerminal` |
| `PartFluidAnnihilationPlane` | `PartAnnihilationPlane` |
| `PartFluidLevelEmitter` | `PartLevelEmitter` |
| `PartFluidFormationPlane` | `PartFormationPlane`, once its two `AEKeyType.items()` checks go |

Plus their containers, screens and the client plumbing that serves only them. The owner chose to drop the
recipes outright rather than keep them as aliases (option A), and to remove api accessors along with the
parts rather than leave disabled stubs — this port replaces the API wholesale, so "frozen" never meant
"permanent".

### Stage 0 — the config layer, done

**The planned order was wrong and had to be inverted.** Deleting the fluid parts first would have lost fluid
*filtering*: the generic parts serve fluids for **contents**, but every filter in the mod goes through
`AppEngInternalAEInventory`, which was item-only in both directions. A storage bus could show fluids and
never be partitioned to one. Deleting the fluid parts on top of that is exactly the silent capability loss
rule 6 forbids.

| Commit | What |
|---|---|
| `ed22508f5` | config inventories resolve and render any key type |
| `afba0e4e0` | left click sets a filter to a held container's contents, right click to the container |
| `2306030ca` | the legacy `FluidDummyItem` placeholder is recognised wherever configs are written |
| `386f054a1` | pattern terminals accept a dragged fluid |
| `a2045a263` | filters can be set from HEI and by hand |
| `53953f435` | a dragged container follows the same button rule as clicking |
| `c688e87ca` | a fluid cell's own partition accepts the generic placeholder |
| `fe6ade83c` | a wrapped key's name no longer recolours the rest of a tooltip line |

### It blocked stage 1 — import/export buses did not move fluids (fixed, awaiting a play-test)

`FluidImportStrategy.transfer` opened with `if (!(context instanceof FluidTransferContext ctx) || ...)`, and
the **generic** bus builds a `StackTransferContextImpl`, so the fluid strategy bailed on its first line. Only
`PartFluidImportBus` built a `FluidTransferContext`, which is why the legacy bus worked and the generic one
did not. Three changes, none of them in the api:

1. **The duplicate context is gone.** `FluidTransferContext` was a byte-for-byte copy of
   `StackTransferContextImpl`, and that copy *was* the bug: a strategy can only test a context's concrete type
   when there is more than one. `StackTransferContextImpl` (and its constructor) is now public, both legacy
   fluid buses build it, and `FluidTransferContext` is deleted. Its package-private extras stay package-private
   — nothing outside `appeng.parts.automation` uses more than the constructor.
2. **The filter goes through the frozen contract.** `context.getFilter().matches(what)` replaces the cast and
   the hand-rolled `matchesFilter`. `getFilter()` is already documented as "the bus' configured filter,
   strategies must not move anything this rejects", an empty list matches everything, and the fuzzy card is
   already baked into the `IPartitionList` behind it, so `matchesFilter` was duplicating `FuzzyPriorityList`.
3. **The budget is counted in operations, not millibuckets.** This was a second, unnoticed defect sitting
   behind the first: the strategy read `getOperationsRemaining()` as a millibucket count, so on the generic bus
   a fluid import would have drained 1–96 mB per tick. It now converts through
   `AEKeyType.fluids().getAmountPerOperation()` (125 mB), which reproduces the pre-port 125…12000 mB cascade
   exactly, and spends `max(1, inserted / 125)` operations. `PartFluidImportBus` now passes operations too.
   The export side already did this right — `PartExportBus.exportOne` multiplies by `getAmountPerOperation()`.

Also fixed while in the file: the modulate drain was untyped (`fh.drain(int, true)`), which on a multi-tank
block can return a different fluid than the one peeked and filter-checked. It drains by stack now, as the
pre-port bus did.

**Power**: neither strategy charges AE for the transfer, and that is not a regression — the pre-port
`PartFluidImportBus`/`PartFluidExportBus` called `injectItems`/`extractItems` directly, never
`Platform.poweredInsert`. The item strategies *do* charge. On the unified bus that is visibly inconsistent, but
making fluids cost power is a balance change and belongs to the owner, not to this fix. `FluidTransferContext`'s
javadoc claimed its `getEnergySource()` existed for exactly this and no caller ever used it.

Play-tested by the owner: the generic bus now moves both fluids and items. **Stage 1 is unblocked.**

### Server crash — annihilation plane facing a cable bus (fixed, awaiting a play-test)

Found by placing a bus next to a plane by accident. `Ticking GridNode` NPE at
`Platform.poweredInsert` → `Preconditions.checkNotNull(input)`, from
`ItemPickupStrategy.canStoreItemStacks`.

`BlockCableBus.getItemDropped()` returns **null**, not `Items.AIR`, so vanilla's `Block.getDrops` — which only
skips a drop when the item *is* `Items.AIR` — adds an `ItemStack` for it anyway, and that stack is empty.
`AEItemKey.of()` answers null for an empty stack, and `poweredInsert` asserts a non-null key.

A port regression, and a mirror of §9.1's family: the pre-port code passed the same null into
`injectItems()`, which answered null and did nothing. Nullability that the old API tolerated became an
assertion in the new one, and no signature changed to say so.

Fixed in two places: `Platform.getBlockDrops` now filters empty stacks out of the array it returns (the root —
every caller reads that array as "the things that dropped"), and `canStoreItemStacks` skips a null key
explicitly, because `AEItemKey.of` is `@Nullable` and a subclass may override `obtainBlockDrops`.

**Not fixed, because it is not a regression**: the plane then *breaks* the adjacent cable bus, dropping its
parts on the floor. `canHandleBlock` is byte-for-byte the pre-port version and excludes only bedrock, portals,
command blocks and liquids — a cable bus was always fair game. Raise with the owner as a design question, not
as a bug.

### Security station listed network craftables (fixed, awaiting a play-test)

The security station's terminal showed craftable items it does not hold, and clicking one crashed the client:
`GuiCraftConfirm.initGui` builds its Cancel button only when the host maps to a `GuiBridge`, but added it to
`buttonList` unconditionally, so a null went in and `GuiScreen.drawScreen` dereferenced it on the first frame.
That `add` is byte-for-byte the pre-port version — pre-existing, and unreachable until a host with no way back
could reach that screen at all. It is now inside the branch.

The real defect is upstream of it: `computeCraftables()` asked the **grid's** crafting cache whenever the
container had a live grid node, but "attached to a grid" is not "shows the network". A security station and an
ME chest are both grid hosts that monitor their own inventory — the station's biometric cards, the chest's one
cell. The class javadoc already said as much for the live-update path and the craftable path ignored it.

Gated on a new `monitorsNetworkInventory()`, which compares `this.monitor` by identity against
`IStorageService.getInventory()` (one stable object per grid). Deliberately not an `instanceof` chain: the
wireless terminal is network-backed without being a terminal part, and an addon terminal that returns the
grid inventory is covered with no change here. `getCachedAmount()` was reading the network cache for the same
non-network hosts and is gated on it too.

### Crafting toast overflowed its box (fixed, awaiting a play-test)

Long names ran past the toast. **Widening is not possible in 1.12.2**: `IToast` has no `width()` (that arrives
in a later version) and `GuiToast.ToastInstance.render` hardcodes 160 for both the slot and the slide-in
animation, so anything drawn wider would overlap the neighbouring toast and slide wrong. Both lines are
trimmed with `trimStringToWidth` plus an ellipsis instead — from the right, so the amount at the front of the
label survives.

### Stage 1 prerequisite — the formation plane is no longer per-type (done, awaiting a play-test)

`PartAbstractFormationPlane` carried an abstract `getKeyType()` that `insert` rejected everything else
against — per-type behaviour on the part, which is exactly what the owner ruled out in wave 5. It is gone,
along with both overrides and the `what.getType() != AEKeyType.items()` check. `PlacementStrategyFacade`
already routes each key to the strategy registered for *its* type and answers 0 when there is none, so the
guard was doing nothing the facade did not already do — except block fluids from the item plane.

`PLACE_BLOCK` is still passed to every strategy; the ones it makes no sense for ignore it, as
`FluidPlacementStrategy` already did.

The base class' javadoc claimed a plane's filter could only ever hold items, because
`AppEngInternalAEInventory`'s item-handler surface built slot contents with `GenericStack.fromItemStack`.
**Stage 0 already fixed that** and the javadoc was never updated — `toGenericStack` now unwraps a placeholder
back into its key, so the plane's `SlotFakeTypeOnly` config takes any key type from the GUI. Corrected, since
it is the reason `PartFluidFormationPlane` grew its two-inventory mirror in the first place.

### Stage 1 audit — what is actually safe to delete

Done before deleting anything, because a deletion that quietly removes a mechanic is what rule 6 forbids.

| Legacy part | Generic replacement covers it? |
|---|---|
| `PartFluidImportBus`, `PartFluidExportBus`, `PartSharedFluidBus` | **Yes** — play-tested, `59c5f29fd` |
| `PartFluidFormationPlane` | **Yes** — play-tested, `6f32d9a16` |
| `PartFluidAnnihilationPlane` | **Yes** — it only *narrows* `PartAnnihilationPlane` to the fluid strategy; the generic plane runs every registered pickup strategy, so it is a strict subset |
| `PartFluidLevelEmitter` | **Yes** — `PartLevelEmitter` reads its key through `getAEStackInSlot`, already type-erased, and its config slot is a `SlotFakeTypeOnly`, which stage 0 taught to take any key |
| `PartFluidStorageBus` | **Yes** — play-tested |
| `PartFluidTerminal` | **No. Blocked.** It is the only terminal that can fill or empty a held fluid container (see "Still open"). Deleting it before that moves to the generic terminal would remove the sole way to get a fluid in or out of the network by hand |

So stage 1 splits in two: everything above the terminal row can go now; the terminal waits on the
container-interaction port. The same blocker applies to `ContainerMEPortableFluidCell` and
`ContainerFluidInterface`, which carry copies of the same three code blocks.

### Stage 1a — the six cleared parts are deleted (done, awaiting a play-test)

Gone: the seven part classes (`PartFluidImportBus`, `PartFluidExportBus`, `PartSharedFluidBus`,
`PartFluidStorageBus`, `PartFluidLevelEmitter`, `PartFluidAnnihilationPlane`, `PartFluidFormationPlane`),
their four containers, four screens, six `PartType` entries, six `AEFeature` flags, six `ApiParts` fields and
their `IParts` accessors, four `GuiBridge` entries, eight recipes, the item and part models, the exclusive
textures, and the item-name lang entries in all six languages.

Also removed as a direct consequence:

- the `Upgrades.*.registerItem(parts.fluidImportBus()/…)` lines in `Registration`;
- `AEBasePart`'s memory-card branches that read/wrote `reportingValue` for a `PartFluidLevelEmitter`;
- `PacketValueConfig`'s `FluidLevelEmitter.Value` case and the `ContainerFluidStorageBus`
  partition/clear branches;
- `ItemPart`'s fluid import/export grouping and the `GuiText.IOBusesFluids` label it was the only user of;
- `ItemPartRendering`'s built-in `PlaneModel` registrations for the two fluid planes;
- `GuiOptionalFluidSlot`, orphaned by the three deleted screens.

**Deliberately kept:** `AEFluidTank` is unreferenced and has been since before this phase — an earlier wave
recorded in its javadoc that it is "a migration target, not a deletion target". Stage 2 replaces it; deleting
it here would silently reverse that decision.

**Found by the play-test, deliberately not fixed:** the fluid terminal's screen renders its rows as items.
The owner ruled it out of scope — that part is next to be deleted, and fixing a screen on its way out buys
nothing. It does mean the fill/empty mechanic is already unavailable in practice, so the "blocker" above is
now only about the *code*: `ContainerFluidTerminal` stays the reference implementation to port from until
the generic terminal can do it, and then both go together.

**A note on how the screens were found.** `GuiBridge` resolves a screen from its container by *name*
(`container.` → `client.gui.`, `.Container` → `.Gui`), so no compile error would ever have pointed at an
orphaned `Gui*` class. A static "who references this" sweep reports every screen in the tree as unused for
the same reason. Both facts matter for stage 3.

### Stage 1b — `ContainerItemStrategy`, so the generic terminal can fill and empty (done, awaiting a play-test)

The sixth member of the strategy family, and the last thing blocking `PartFluidTerminal`'s deletion.

**Shape chosen, after reading both references the owner asked for.** ae-gtnh puts `fillContainer` /
`drainStackFromContainer` / `clearFilledContainer` straight onto its `IAEStackType` (its `AEKeyType`) and returns
a `(container, amount)` pair from each call. Modern upstream keeps a separate `ContainerItemStrategies` registry
keyed by key type, and hands out a *context* object per interaction. **Upstream's shape won**, for two reasons:
our `AEKeyType` lives in the frozen api and has no business knowing about item containers, and we already have
`StackWorldBehaviors` — a separate registry of exactly this kind, which an addon already knows how to register
against. `ContainerItemStrategies` is its sibling, deliberately separate because this is about stacks in a hand
rather than blocks in the world.

The context object is not ceremony: Forge's `IFluidHandlerItem` works on a *copy* of the stack and returns the
result through `getContainer()`, so a fill cannot be expressed as a mutation of the stack passed in. ae-gtnh's
pair-return forces the caller to thread the container through by hand and cannot express two transfers into one
container.

New: `api/behaviors/ContainerItemStrategy` (+ `Context`), `api/behaviors/ContainerItemStrategies`,
`fluids/parts/FluidContainerItemStrategy`. Registered in `InitStackWorldBehaviors` beside the other five.
Items deliberately register none — an item *is* its own container, and pretending otherwise would make every
ordinary stack look emptiable; `ContainerItemStrategies.register` rejects `AEKeyType.items()` outright.

`AEBaseContainer` gained `handleContainerItemAction` / `fillHeldContainer` / `emptyHeldContainer` /
`replaceHeldWith`, replacing the three near-identical copies of that dance in the fluid-only containers.
Nothing in them mentions fluids. `AEBaseGui`'s `SlotME` branch sends the actions: **left click fills** the held
container from the network when the row's type has a strategy, **right click empties** it — gated on the held
item actually containing something, so right-clicking with an ordinary stack still places a single item, since
a bucket is a normal item everywhere else. Same convention the legacy fluid terminal used.

**Three upstream behaviours deliberately not ported yet**, none of them a regression:

- `FILL_ENTIRE_ITEM` / `EMPTY_ENTIRE_ITEM` (shift to transfer a whole container rather than one unit). One
  unit per click, matching `AEKey.getAmountPerUnit()`, is what the legacy fluid terminal did.
- ~~Clicking a fluid row with an **empty hand** to pull an empty bucket out of the network and fill it.~~
  **Done** after the owner asked for it. Upstream hardcodes `Items.BUCKET` into its terminal menu; here it is
  `ContainerItemStrategy.getEmptyContainerFor(AEKey)`, so the terminal still does not know fluids exist and an
  addon key type can offer its own container. The container is borrowed unpowered - it is a loan, not a
  withdrawal - and goes straight back if the key turns out not to fit in it, which is also how a fluid that
  no bucket accepts resolves itself with no special case.
- Shift-clicking a filled container **out of the player inventory** to drain it into the network.
  `ContainerFluidTerminal.transferStackInSlot` did this, and it is the one place the universal terminal
  cannot copy it: on a fluids-only terminal, storing the bucket itself was not an option, whereas here
  shift-click already means "store this item" and must keep meaning that or a bucket becomes unstorable.
  Right-click still empties it. This is a conflict created by making the terminal universal, resolved in
  favour of the unambiguous reading — not a mechanic dropped.

### Stage 1c — the fluid terminal is deleted (done, awaiting a play-test)

Gone: `PartFluidTerminal` + its container and screen, `ToolWirelessFluidTerminal` + `ContainerWirelessFluidTerminal`
/ `GuiWirelessFluidTerminal` and their base `ContainerMEPortableFluidCell` / `GuiMEPortableFluidCell`,
`SlotFluidME`, `IMEFluidSlot`, `FluidStackSizeRenderer`, `PacketMEFluidInventoryUpdate`, the `PartType`,
`AEFeature`, `GuiBridge`, `ApiParts`/`IParts`, `ApiItems`/`IItems` entries, the `Terminal` enum entry, the
`WFT` keybinding, two recipes, the models, textures and lang.

**The wireless fluid terminal was an item, and it is gone.** The ordinary wireless terminal is built on
`ContainerMEMonitorable` → `AEBaseContainer`, so it lists fluids and now fills and empties containers exactly
like the wired one. Calling this out because it is the one player-facing item removal in the phase.

`AEBaseGui`'s dedicated fluid-slot rendering branch went with `IMEFluidSlot`: the generic terminal draws a
fluid row through `SlotME` and the wrapped placeholder's own model, which is why fluids were already visible
there before any of this.

Two consequences worth knowing:

- `TileChest.getGuiBridge()` picked a screen per key type and answered **null** for anything that was neither
  items nor fluids - so an addon key type gave the chest no GUI at all. It now answers `GUI_ME` for every
  type. `BasicFluidCellGuiHandler` likewise opens `GUI_ME`; it still has to exist, because `TileChest` only
  opens a GUI when `StorageCells.getGuiHandler` answers for the cell's type. Whether that per-type registry
  still earns its keep is a stage 3 question — **answered below, under "The cell GUI registry is an
  override now".**
- `PacketTargetFluidStack` is down to two dispatch targets, both interface screens. It follows the interface
  in stage 3; `PacketTargetItemStack` already carries a bare `AEKey` of any type.

### Play-test after stage 1c — four reports (fixed, awaiting a re-test)

1. **A fluid put into an ME Interface's config is never stocked.** Not a regression, and expected: the
   universal interface is stage 3. `DualityInterface.usePlan` guards on `instanceof AEItemKey` and does
   nothing for anything else. The slot *accepts* a fluid only because stage 0 generalised every config
   inventory, which was the right order (see stage 0) but makes an unfinished feature look like a broken one.
   **What was a real defect:** `updatePlan` planned the work anyway, so `requireWork[slot]` stayed set
   forever, `hasWorkToDo()` stayed true, and **the interface never went back to sleep** - a machine ticking
   at its fastest rate indefinitely, with nothing visible to show for it. A non-item key now counts as an
   empty slot until stage 3 teaches the interface to stock one.
2. **Middle-click on an item to type an amount does not work.** Verified *not* a port regression: the
   `case CLONE` branches in `AEBaseGui` are structurally identical to `main`, both for `SlotFake` (no
   middle-click handling at all, before or after) and for `SlotME` (craftable → `AUTO_CRAFT`, else creative
   duplicate). Whatever opens a value dialog on a filter slot is not in this repo. Left alone pending the
   owner naming the screen.
3. **A fluid is not transferred into the pattern terminal by HEI's "Move Items".**
   `RecipeTransferHandler` read only `recipeLayout.getItemStacks()`; fluid ingredients live in a separate
   `IGuiIngredientGroup` that nothing ever looked at. It now also walks `getFluidStacks()`, placing inputs
   into whichever matrix slots the item pass left empty and appending outputs to the output list, each as
   the same wrapped placeholder the pattern slots already store - so the server side needed no new case.
   Only for `ContainerPatternEncoder`: a real crafting matrix cannot hold a placeholder.
4. **A fluid in a pattern slot or interface config drew "1" instead of "1000".**
   `AEBaseGui.drawSlot` sized the overlay with `GenericStack.fromItemStack(stackInSlot)`, which reads a
   placeholder as the ordinary item it is - one `WrappedGenericStack` - so every wrapped key rendered as a
   count of 1. It unwraps first now. Same family as §9.1: a call that translates word for word and changes
   meaning.

### Second fluid play-test — six reports (fixed, awaiting a re-test)

The last three share one root, found by chasing "two identical fluids" (see CONTRACT.md §9.1c):
`AEFluidKey.equals` compared `Fluid` by identity while `hashCode` hashed its name, and an empty NBT tag was
kept rather than normalised to null. Either alone makes two keys for one fluid. That is why a fluid could
appear twice, and why **a crafting job never finished even though the fluid reached the network** - the
output arrived under a key that did not equal the one the job waited for. Both key classes normalise now.

1. **HEI recipe keybinds did nothing over a fluid.** Every non-item key travels through the GUI as a
   `WrappedGenericStack` placeholder, so HEI saw an ordinary item with no recipes. `AEGuiHandler` (already
   registered as an `IAdvancedGuiHandler`) now unwraps the slot under the mouse and answers with a
   `FluidStack`. Registered for `AEBaseGui`, so it covers every screen at once. Its craft-plan and
   crafting-CPU branches were handing HEI the placeholder too, and were fixed with it.
2. **Crafting a fluid never completed** - see above.
3. **The same fluid listed twice in a plan** - see above.
4. **Shift+wheel on a fluid in a pattern did nothing useful.** Every amount-changing fake-slot action worked
   on `ItemStack.getCount()`, but a wrapped key's amount is in its NBT and the placeholder is always exactly
   one item that cannot stack - so the count grew where nothing read it, the configured amount never moved,
   and the slot claimed a stack size the wrapper is not allowed to have (the odd trailing tooltip line).
   Handled before the item switch now, stepping by the key type's own unit: a bucket per notch, with
   Ctrl-halve/double reaching the amounts in between, which is how 40mB gets configured.
5. **"Fish Oil: 0B"** and 6. **"Items Stored: 0B"** were the same formatting bug: `AEKeyFormatting` divided
   40 mB by 1000 and printed `0.04` through a one-fractional-digit format, giving "0". Below one unit the
   base unit is now the only reading, so 40 mB says "40mB". Also from that report: a non-item type says
   **"Amount"** rather than "Items Stored", and **shift** in a terminal tooltip switches to the new
   `AmountFormat.FULL_BASE` - the exact number in the base unit, "1,040mB" where the normal reading rounds
   to "1B".

### The fluids-in-patterns root cause, and what it left behind

Three reports - a duplicated terminal row, a crafting job that never completed, and "craft 3" producing three
runs of 40mB - turned out to be **one** defect, written up as CONTRACT.md §9.1d: `PatternHelper` read a
pattern's slots with `GenericStack.fromItemStack`, which answers an *item* key for a wrapped placeholder. A
processing pattern producing 40mB therefore declared its output as one placeholder item. Fixed by a new
canonical resolver, `GenericStack.resolveItemStack`, threaded through the pattern path.

Worth recording how it was found, because two confident diagnoses came first and both were wrong (an
`equals`/`hashCode` disagreement in `AEFluidKey`, real but unrelated; and a duplicate transfer context). What
settled it was asking the owner to read out **both** tooltips: the real row said "Amount: 80mB" and the
phantom said "Fish Oil: 40mB" - the second is a placeholder item's own tooltip, which no fluid row can
produce. One piece of evidence beat two rounds of inference.

**Data loss found next, and fixed:** `CraftingCPUCluster` extracted a processing pattern's ingredient from
the network with `MODULATE` and *then* tested whether it was an `AEItemKey`. A fluid ingredient was pulled
out, rejected, and never placed in the `InventoryCrafting` - which is the only thing the put-back loop
restores from, so it was destroyed. Only the first one, because the loop breaks on the first failure, which
is exactly what the owner saw ("one of the two gets voided"). The type test now happens before the
extraction.

**Still not delivered, and it is stage 3:** a fluid *ingredient* cannot reach the machine at all. The CPU
hands the crafting medium an `InventoryCrafting`, which carries `ItemStack`s only, and the interface pushes
items. Fluids in patterns therefore encode, plan and display correctly but cannot yet run. That is the
universal interface's job.

### Stages 2 and 3 turned out to be one stage

The plan had stage 2 as "replace the fluid inventory types with a generic one, ~30 mechanical files" and
stage 3 as "the universal interface". A dependency sweep before starting showed **stage 2 has no independent
existence**: every remaining user of `AEFluidInventory` / `IAEFluidTank` / `IAEFluidInventory` /
`AENetworkFluidInventory` is either the fluid interface itself, its configuration terminal, or the two
memory-card branches in `AEBasePart`/`AEBaseTile` that serve them. Generalising those types would mean
rewriting exactly the classes stage 3 deletes.

Same mistake as the original stage order (see stage 0): the plan was drawn from package names rather than
from the dependency graph. Merged into one stage, sliced so the game stays playable between slices:

- **2a — the generic inventory itself (done, nothing wired to it yet).** `GenericStackInv` in
  `appeng.util.inv`: fixed slots, each holding an amount of one `AEKey` of any type, with per-slot capacity
  from the new `appeng.api.behaviors.GenericSlotCapacities` (a stack of items, four buckets of fluid, and an
  addon's own type registers its own). Two adapters over it, deliberately separate: `GenericStackItemHandler`
  shows only the item slots as items, `GenericStackFluidHandler` only the fluid slots as tanks. Each reports
  the other's slots as **empty and unusable**, which is the point - an adjacent machine pulling items out of
  an interface must not be handed a `WrappedGenericStack` placeholder, and a pipe must not drain an item
  slot. That is also why this is a new class rather than a reuse of `AppEngInternalAEInventory`: that one
  exists to be edited in a GUI and hands out placeholders on purpose.
- **2b — the interface's stock is generic (done, awaiting a play-test).** `DualityInterface.storage` is a
  `GenericStackInv` rather than an `AppEngInternalInventory`. What that let us delete is the point:
  - `updatePlan` no longer asks what type anything is. It compared a `GenericStack` request against an
    `ItemStack` in a slot, so every branch had to unwrap one side; both sides are keys now and the whole
    method is four cases with no `instanceof` in them. The stopgap added last week - "a non-item request
    counts as an empty slot, or the interface never sleeps" - is gone with it.
  - `usePlan` moves stock with `storage.insert`/`storage.extract` on the slot instead of an
    `InventoryAdaptor`, which could only ever carry items. It still holds rather than pushes, same as
    upstream's `InterfaceLogic`.
  - `InterfaceInventory`, the face the network sees, extends the new `GenericStackInvStorage` instead of
    `MEMonitorIInventory`. The two priority guards on top of it are unchanged.
  - `AppEngNetworkInventory` is deleted. Its one behaviour - something pushed into an interface goes to the
    **network** first and only overflows into the nine slots - is now `NetworkFirstItemHandler`, wrapped
    around the item view. That behaviour is fork-specific and had to survive verbatim.
  - Breaking an interface drops the item slots only; a fluid has nowhere to land and stays in the network,
    which is what breaking a fluid interface already did.

  The crafting card still delivers through an `InventoryAdaptor` over a one-slot item view, so a *crafted*
  non-item restock is still item-only. That is 2c's business, along with the fluid capability.
- **2c — the interface stocks and serves fluids (done, awaiting a play-test).** With 2b's plan and execution
  already type-agnostic, this is mostly capability wiring: `hasCapability`/`getCapability` now answer
  `FLUID_HANDLER_CAPABILITY` with a `GenericStackFluidHandler` over the same stock, wrapped in the new
  `NetworkFirstFluidHandler` - the fluid mirror of the item behaviour, and the deleted
  `AENetworkFluidInventory`'s: what a machine pumps in goes to the network first, while **draining stays
  local**, which is what makes an interface a buffer rather than a pipe into storage.
  Slot capacity is four buckets, the same number `DualityFluidInterface.TANK_CAPACITY` used, so nothing
  changes size.
  <br/>
  One thing this needed that the plan did not foresee: the interface's own GUI drew its nine stored slots
  through the *item* view, so a stocked fluid was simply invisible. `GenericStackDisplayHandler` is the third
  view of a `GenericStackInv` and exists for exactly that - a machine must not see a fluid slot at all, a
  player must. It shows a non-item key as a placeholder and refuses every mutation of that slot, which is
  also what stops the placeholder being picked up: a vanilla slot decides whether it can be taken by asking
  `extractItem` for one.
  Two defects the play-test found in 2c, both about **capacity**, and the first of them a regression I would
  not have caught by reading:
  - *"It always stocks one bucket."* The config can be set to any amount, but a slot holds four buckets.
    `usePlan` checks that the whole amount fits before moving anything, so an over-large request failed on
    every single tick - and the slot kept whatever had been stocked while the number was still small, which
    from the outside reads as "the amount setting does nothing". `updatePlan` clamps the request to the slot
    capacity now, so the interface stocks as much as it can hold and then rests.
  - **The item slot silently shrank from 512 to 64.** The old storage was an
    `AppEngInternalOversizedInventory` built with `maxStack = 512`; `GenericSlotCapacities` is a *standard*
    slot size and says 64. `GenericStackInv` therefore takes an optional per-inventory `SlotCapacity`, and
    the interface passes 512 for items while leaving every other type at the standard. Nothing about the
    symptom pointed here - it was found only by asking what the deleted class's constructor arguments meant.

  A second round on the same slice found the reason the amount looked inert, and it is worth writing down
  because it is the §9.1 family again: **`AppEngInternalAEInventory.setStackInSlot` reported "nothing
  changed" whenever a wrapped key's amount changed.** It works out what happened by differencing the old and
  new `ItemStack` *counts* - and a placeholder is always exactly one item whatever amount it stands for, so
  the difference was zero, `removed` and `added` both came out empty, and `DualityInterface` skipped
  `readConfig()`. The amount was stored correctly and nothing was ever told to act on it, which is why it
  only took effect after a reload. Placeholders now compare by their `GenericStack`.

  Capacity is one rule instead of two special cases: **an interface slot holds eight standard slots' worth**.
  That is where the fork's 512 items came from (`maxStack = 512`, eight stacks), and the same multiple gives
  32 buckets of fluid. The same arithmetic bounds what can be *typed*: a fake slot's stack limit is in items,
  so scaling it by the key's standard slot size makes "512" mean 512 items and 32 buckets, and scrolling
  stops there rather than running to 9.2E18.

  A third round found that a stocked fluid could still be picked up out of the interface's GUI - "as an
  item, but only visually", which was the whole diagnosis in one phrase. **§9.1d again, in the opposite
  direction.** Vanilla syncs a container slot by sending its `ItemStack` and calling `putStack` on the
  client, which reaches `GenericStackDisplayHandler.setStackInSlot`; that read the placeholder as the item
  it looks like and wrote `AEItemKey(WrappedGenericStack)` into the *client's* copy of the inventory. From
  then on the client's slot held a genuine item: it rendered as one, `canTakeStack` allowed it, and a bucket
  could be swapped into it - while the server, whose inventory held a fluid, refused. Hence "only visually".
  It resolves the stack now, like every other reader of a slot.

  Filling a bucket **from** that slot needed adding rather than fixing - the slot rightly will not hand a
  fluid over as an item, so there was no way to reach it by hand. `SlotGenericStorage` marks a slot as backed
  by a generic inventory, and a click with a container in hand becomes a fill or an empty **against that
  slot** rather than against the network. Same left-fills/right-empties convention as a terminal row, and
  upstream does the same for any slot backed by a generic inventory.

  Also from the same report: scrolling a wrapped amount now snaps to whole units. Up from a hand-tuned 1mB
  reads 1B rather than 1001mB, because a notch means "one more bucket"; Ctrl is what reaches the amounts in
  between.

- **2d — the ordinary configuration terminal handles fluid configs (done, awaiting a play-test).** Smaller
  than planned, because it was already most of the way there: the terminal syncs `dual.getConfig()`, whose
  item-handler face hands out placeholders, so a fluid config was already travelling to the client and
  rendering. Two gaps, both in the drag-in path:
  - `getPhantomTargets` rejected anything that was not an `ItemStack`, so a fluid dragged out of HEI offered
    no target at all - the same defect stage 0 fixed for filter slots, in the one screen that was not
    covered because it does not use `SlotFake`.
  - the accepted stack went through `GenericStack.fromItemStack`, §9.1d once more, so even a placeholder
    dropped from a bookmark would have been recorded as the placeholder item.

  A second round found three more, all one root: **`SlotDisconnected` is a config slot too, and received
  none of what stage 0 gave `SlotFake`.** It is the slot type this terminal uses to reach a *remote*
  interface's config, and it has its own parallel copy of the fake-slot actions in
  `ContainerInterfaceConfigurationTerminal.doAction`, working on `ItemStack` counts. So:
  - clicking with a lava bucket configured *the bucket*, because the left-click-takes-the-contents rule
    lives in the `SlotFake` branch;
  - Ctrl/Shift scrolling did nothing, because a placeholder is always one item and every case there steps a
    count. `AEBaseContainer.adjustAmount` is now shared between the two copies rather than duplicated;
  - dragging from HEI always produced the fluid, ignoring the button. Reading the live mouse state was not
    enough on its own: **it has to be read at the right moment.** HEI asks for targets when the drag
    *starts* and calls `accept` when it ends, so resolving the ingredient in `getPhantomTargets` pinned the
    answer before the player had given it. `GuiUpgradeable` reads the button inside `accept`, which is why
    the same code worked there and not here; the resolution moved into `accept` too.

  The lesson is worth keeping for stage 3: **"every filter goes through `AppEngInternalAEInventory`" was
  true, but "every filter is a `SlotFake`" was not.** Stage 0 fixed the inventory and the common slot; this
  terminal reaches the same inventory through a different slot and a different action switch, so it looked
  finished and was not.

  What that leaves for 2e is the whole fluid interface orbit at once - the part, tile, block, duality,
  container, screen, *and* its configuration terminal - since they only exist to serve each other.
- **2e — the fluid interface orbit is deleted (done, awaiting a play-test).** The part, the tile, the block,
  `DualityFluidInterface`, its container and screen, its configuration terminal (part, container, screen,
  `ClientDCInternalFluidInv`), `FluidSyncHelper`, `IFluidSyncContainer`, `IConfigurableFluidInventory`,
  `ContainerFluidConfigurable`, the `GuiFluidSlot`/`GuiFluidTank` widgets, `PacketFluidSlot`,
  `PacketTargetFluidStack`, and the five fluid inventory types stage 2 was originally about -
  `AEFluidInventory`, `AEFluidTank`, `AENetworkFluidInventory`, `IAEFluidInventory`, `IAEFluidTank`.
  Plus the `PartType`, `AEFeature`, `GuiBridge`, `ApiParts`/`IParts`, `ApiBlocks`/`IBlocks` and `GuiText`
  entries, the recipes, models, textures and lang.

  Two removals worth calling out because they were not on the list:

  - **`FluidDummyItem` and its renderer.** The fluid-only placeholder that predated `GenericStack.Wrapper`.
    Nothing produced one any more - the legacy fluid GUIs were its only source - so it was a registered item
    that could not exist. Deleting it collapses `AppEngInternalAEInventory.toGenericStack` to exactly
    `GenericStack.resolveItemStack`: **one placeholder in the mod instead of two.**
  - The memory-card branches in `AEBasePart`/`AEBaseTile` that read and wrote a fluid config, which had no
    remaining implementor.

  What is left of `appeng.fluids` is ten files, and none of them is a duplicate of anything: the six
  strategies that make fluids a first-class key type, the fluid storage cell and its GUI handler, the cell's
  config helper, and the sorters.

  **Startup crash the play-test caught, and the sweep it prompted.** `_constants.json` - the recipe
  constants file, not a recipe - still declared an `appliedenergistics2:fluid_interface` ingredient gated on
  the `fluid_interface` feature, so `AEFeature.valueOf` threw during `loadConstants` and the game did not
  start. Deleting a feature flag is not a Java-visible change: recipe JSON names it as a *string*, so the
  compiler cannot see the reference and neither can a search for the enum constant, which is spelled
  differently there (`fluid_interface`, uppercased at read time).

  Swept for the whole phase afterwards, not just this one: every `"features"` condition in every recipe file
  now names a flag that exists, and none of the ten removed part/item names appears anywhere in the assets.
  Worth repeating before any future feature removal - **the assets are a second, untyped reference to the
  enum, and `_constants.json` is the easiest one to miss because it is not a recipe.**

## Fluids as a crafting *ingredient* (done, awaiting a play-test)

The last thing fluids could not do. A pattern with a fluid input encoded, planned and displayed correctly but
never ran, because the crafting CPU hands a medium an `InventoryCrafting` and that carries `ItemStack`s only.

`ICraftingMedium` gained a **default** overload taking the ingredients an `InventoryCrafting` cannot express:

```java
default boolean pushPattern(details, table, GenericStack[] extraInputs) {
    return extraInputs.length == 0 && pushPattern(details, table);
}
```

Default rather than a new abstract method for two reasons: an addon's medium keeps compiling, and - more
importantly - one that has not been updated **refuses** a pattern it cannot fully place instead of silently
pushing it short an ingredient. The two halves are all-or-nothing, and `CraftingCPUCluster` puts *both* back
when the push fails.

Where the ingredients actually go, per destination:

- **A neighbouring ME network** needs no translation at all - it speaks `MEStorage`, so a fluid inserts
  exactly like an item.
- **An adjacent machine** goes through `StackExportStrategy.push`, the same layer an export bus uses. That is
  the whole point of the strategy layer paying off: `DualityInterface` never learns what a tank is, and a key
  type an addon registers works here the moment it registers an export strategy.
- **A third-party `ICraftingMachine`** is not offered the pattern at all when there are non-item ingredients,
  since its API takes an `InventoryCrafting`.

Deliberately not done: **queueing**. Items that do not fit go into `waitingToSendFacing`, which holds
`ItemStack`s; a non-item ingredient is instead pushed only when the whole amount fits right now, and the
pattern is otherwise refused. That matches what `acceptsItems` already required of the item half and avoids a
second, parallel queue - but it does mean a machine whose tank is momentarily full defers the craft rather
than buffering it.

## HEI recipe keybinds over a fluid row (done, awaiting a play-test)

Pressing HEI's show-recipes key over a fluid in a terminal did nothing, while an item row worked.

The first attempt - answering `IAdvancedGuiHandler.getIngredientUnderMouse` with a `FluidStack` - was the
wrong lever, and the reason is worth keeping: **HEI asks the hovered slot for its ingredient before it asks
any plugin.** A wrapped key is a genuine `ItemStack` in a genuine `Slot`, so HEI finds it, is satisfied, and
looks up recipes for a display shim. Telling HEI what the slot really holds cannot help when it never asks.
(The handler is still worth having - it covers the screens whose rows are *not* vanilla slots, like the
craft-plan list.)

So the answer has to arrive before the question: `WrappedKeyRecipeShortcut` listens to the keyboard event at
`EventPriority.HIGHEST`, ahead of HEI's own handler, and takes over **only** when the slot under the mouse
holds a wrapped key. Every item slot in the mod, and every other screen, is left untouched. The bindings are
read from HEI's own `KeyBindings`, so a rebind is followed automatically.

Registered from `onRuntimeAvailable`, so it needs the runtime to exist and does not exist at all without HEI.

**Deferred here, pending an HEI API request.** This covers the two recipe keybinds and nothing else -
bookmarks, HEI's own tooltips, recipe transfer and cheat-mode clicks all still see the placeholder, because
each of them reaches the slot through the same path and would each need its own interception.

`AE2FluidCraft-Rework-Unofficial` solves the whole class at once with a **Mixin into HEI's internal
`GuiContainerWrapper.getIngredientUnderMouse`**, wrapping the `ClickedIngredient.create(...)` call so their
fluid-packet item is substituted for the real `FluidStack` at the single point where a slot's contents become
an ingredient. That is the right place - it is *one* point, and everything downstream follows. The cost is a
mixin into a private class of a JEI fork, plus turning on mixins here at all (`usesMixins = false`; the fork
has an ASM coremod but no Mixin).

What would remove the need for either hack: **a way to tell HEI what a slot's stack really represents.**
Something like an `ISlotIngredientProvider` consulted inside `GuiContainerWrapper.getIngredientUnderMouse`,
or simply having HEI ask the registered `IAdvancedGuiHandler`s *before* falling back to the slot's own
`ItemStack` rather than after. Either one turns this whole problem into a handler we already have. The owner
is raising it with HEI, who have extended their API before; until then the two keybinds are worth having and
the rest waits.

## After the merge — the follow-up list, worked through

`feature/generic-storage` landed on `main` as `f7218a674` (460 files, +19,729 / -25,225). Everything below
was done on top of it, each on its own branch, each play-tested by the owner before being committed - the
working rule at the end of this file.

| Commit | What |
|---|---|
| `a777c9d34` | the Magnet Card stores into the network instead of the player |
| `df319d0d3` | the terminal diff reads the grid's cached inventory; the wireless terminal gets its craftables back |
| `693e91c73` | the cell GUI handler registry became an override rather than a gate |
| `a2a880f90` | middle click types an exact amount into a pattern or interface slot |
| `74c19d6ab` | a count of one is no longer drawn on a slot |
| `bab1bb6d5` | shift reads a wrapped key's exact amount in any slot, not only a terminal row |
| `9f1339903` | a capacity-card filter slot no longer stores an amount |
| `03ac6da63` | the tooltip no longer states a count of one |

Two of these were **regressions of my own**, both found by playing and neither by any scan:

- `df319d0d3` repaired `25d09eb03`. Narrowing `computeCraftables()` from "attached to a grid" to
  `monitorsNetworkInventory()` fixed the security station and silently took every craftable row away from
  the **wireless terminal**, because `WirelessTerminalGuiObject.getInventory()` answered `this` rather than
  the grid's inventory and could never satisfy the identity test. The javadoc written with that commit
  asserted the opposite. **Narrowing a condition is a deletion**, and it needs the same "who did this used
  to cover" audit that deleting a class does.
- Steps 1 and 2 of the terminal-diff optimisation were a **no-op on the one host they were written for**
  until that same fix landed, for the same reason.

### The shape four of these bugs shared

`bab1bb6d5`, `9f1339903`, `03ac6da63` and the config-slot round in stage 2d are all one thing: **a rule
written in two places, and only one copy taught the new behaviour.**

| The rule | Copy that learned | Copy that did not |
|---|---|---|
| a fake config slot resolves a wrapped key | `SlotFake` | `SlotDisconnected` (stage 2d) |
| a filter stores an identity, not a quantity | `SlotFakeTypeOnly` | `OptionalSlotFakeTypeOnly` |
| shift reads the exact amount | `AEBaseMEGui.renderToolTip` | `WrappedGenericStack.addCheckedInformation` |
| a count of one is not worth stating | `StackSizeRenderer` | `AEBaseMEGui.renderToolTip` |

None of the pairs is related by inheritance, which is exactly why each looked finished on its own. The
repair in every case was to delete one copy rather than to patch it - `SlotFake.typeOnly()` is now shared
by both slot classes, `AEBaseContainer.adjustAmount` by both action switches. **When a fix has to be made
twice, that is the bug.**

### The audit those four bugs prompted — one axis done, two open

Started 2026-07-31, after the fourth duplicated-rule bug. This is a deliberate sweep rather than a response
to a report, so it is worth recording where it got to.

**Axis 1 - §9.1d, raw `fromItemStack` where a placeholder can arrive. Done.** Fourteen call sites, twelve
safe: they read real items out of vanilla inventories, which a wrapper never enters. Two were not, both
fixed in `a4aad9a59`:

- `AEBaseGui.drawSlot`'s encoded-pattern preview. `ItemEncodedPattern.getOutput()` returns
  `wrapInItemStack(details.getOutputs()[0])`, so a fluid-output pattern arrives as a placeholder and the
  shift preview sized the *placeholder*. Note how it hid: a placeholder's raw count is always 1, and
  `74c19d6ab` had just stopped drawing counts of one - so a wrong number became no number, which reads as
  "the feature does not work" rather than as a bug. **When something "just stops working", check whether a
  display rule changed under it.**
- `GuiUpgradeable`'s ghost-ingredient drop, changed to the canonical reader with no known symptom. There is
  no path today that hands HEI a placeholder to drag; the ingredient list cannot contain one.

Also settled while looking: `ItemSlot.getGenericStack()` reads raw and is **safe by design** - its only
callers wrap a neighbour's `IItemHandler`, and the interface deliberately exposes `GenericStackItemHandler`,
which hides non-item slots rather than handing out placeholders. That is what the three-views split in
stage 2a bought.

**Axis 2 - pairs of classes carrying one rule, not related by inheritance. Done for on-screen amounts,
open elsewhere.** Following the third lead - two code paths producing one piece of on-screen text - turned
up not a pair but **six** copies of "how to display an amount of a key", none of which asked the key.
Fixed in `46f4c2cdd`:

- `TesrRenderHelper` had `renderItem2dWithAmount`/`renderFluid2dWithAmount`, identical but for the icon and
  the number. The fluid copy did `amount / 1000` and appended "B" by hand, so a partial bucket read **"0B"**
  - the exact bug `AEKeyFormatting` already carries a comment about fixing. The fix had landed in the
  formatter; this copy never saw it. Collapsed into one `renderKey2dWithAmount(AEKey, ...)`.
- `AbstractPartMonitor` and `CraftingMonitorTESR` each carried their own `instanceof` item/fluid dispatch to
  choose between those two. Both now make one call.
- `GuiCraftConfirm` (3 blocks), `GuiCraftingCPU` (3), `GuiCraftingStatus` (2) each hand-rolled a `k`/`m`
  abbreviation off the raw number, so a fluid job read "16k" instead of "16B". All go through
  `formatAmount` now - `PREVIEW_LARGE` on screen, `FULL` in the tooltip.

Worth noting how it stayed invisible: every one of these paths was *correct for items*, which is what anyone
looks at while testing. **A rule that is right for the common type is not a rule that was ported.**

`GuiNetworkStatus` looks like a seventh copy and is not - its rows are machines, so its numbers really are
item counts. Left alone.

Both remaining leads on this axis are now closed. The parallel `doAction` switches produced the
configuration terminal's missing middle click, above. **Sibling slot classes** produced one finding:
`OptionalSlotFake.getSlotStackLimit()` answers `Integer.MAX_VALUE` where `SlotFake` answers the backing
inventory's real limit. That is deliberate - a pattern's output is a quantity the recipe chooses - but
`maxAmountIn` was scaling it into the key's units, so a pattern output slot would have advertised a ceiling
of `1 - 134217727B`. It reads both `1` and `Integer.MAX_VALUE` as "unbounded" now, and the override says why
it differs. **Two siblings may disagree legitimately; what they must not do is disagree silently.**

**Axis 3 - `instanceof AEItemKey` where every key type belongs. Swept 2026-08-01, no new defects.** This
shape had already produced two: `CraftingCPUCluster` destroying a fluid ingredient because the type test
came *after* the extraction, and `DualityInterface.usePlan` silently doing nothing for a non-item key while
`updatePlan` kept planning the work, so the interface never slept.

About seventy sites, and the ones that look wrong are not:

- `Platform.poweredExtract`/`poweredInsert` test the type before a *statistic* (`ItemsExtracted`). Counting
  millibuckets as items would be the bug.
- `CraftableCallBack` and `JEIMissingItem` carry bare casts, `((AEItemKey) entry.what()).getDamage()`, which
  read as a crash waiting for a fluid. Unreachable: `AvailableItems.findFuzzy` buckets by `getPrimaryKey()`,
  which is an `Item` for item keys and a `Fluid` for fluid keys, so a fluid never enters an item bucket.
- `PatternHelper`'s slot test is item-only correctly - the frame is a vanilla crafting grid.
- `MultiCraftingTracker` is the craft-on-demand exception CONTRACT already documents.

**A type test is not a smell by itself.** The two real defects shared something the sweep's false positives
did not: a *generic* caller that carried on as though the work had been done.

Both open axes are blind sweeps rather than bug reports: expect findings, expect a lower hit rate, and
expect each to need a play-test.

## Stage 2 is done — what the phase actually removed

`appeng.fluids` went from 55 files to 10. Everything deleted was a duplicate of something already generic;
nothing that was deleted took a mechanic with it, and the two capabilities that only the fluid parts had -
stocking a fluid in an interface, and filling a container by hand - are now on the generic ones and reach
every registered key type rather than fluids alone.

Note for 2b: our interface **holds** its stock rather than pushing it, same as upstream's `InterfaceLogic` -
`getAdaptor(slot)` wraps a one-slot view of its own inventory, not the neighbour's. So the change is about
what the slots contain, not about how anything is moved.

### Working rule adopted during this phase

**Do not commit a change that needs in-game testing until the owner has verified it.** Build, report what to
test, leave the tree dirty. Committing first produced a run of corrections-of-corrections that had to be
squashed away.

## The cell GUI registry is an override now (done, awaiting a play-test)

`BasicItemCellGuiHandler` and `BasicFluidCellGuiHandler` had identical bodies - both opened `GUI_ME` - and
differed only in the key type they claimed. They existed because `TileChest.openGui` refused to open
anything at all unless `StorageCells.getGuiHandler` answered, which made a per-type registry a **gate that
defaults to closed**: a key type an addon registers had a cell that could not be opened in an ME Chest, and
the addon's only way out was to register a handler doing exactly what the two built-ins did.

So the duplication was a symptom, not the problem. `TileChest.openGui` now opens `GUI_ME` when no handler
answers, and both built-ins are deleted. The registry survives, unchanged in shape, for what §7 approved it
for - an addon shipping a cell with a screen of its own, still preferred over the generic handler through
`isSpecializedFor`. It just no longer has to be asked permission to open the ordinary terminal.

Note what was *not* done: deleting `ICellGuiHandler` outright. It looks unused now that nothing in this
repo implements it, and that is exactly the reading rule 6 forbids acting on - the capability was added
post-freeze on purpose (see "Amendments made to the frozen API", item 2), and its only consumers live
outside this tree.

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

## Craft-amount defaults and unit entry (done, awaiting a play-test)

Ordering a fluid craft started at `1` — one millibucket, never what anyone wants. The number was a
literal in `GuiCraftAmount.initGui`, which is why it could not know what it was counting.

`AEKeyType.getDefaultCraftAmount()` (CONTRACT §8.7) supplies it now, and `ContainerCraftAmount` carries it
to the client in `@GuiSync(10) long initialAmount`. `GuiSetAmount` had already built that mechanism for
itself; it now inherits it and its `drawBG`/`primed` override is gone.

Three things were found on the way and are worth remembering:

- **The same parse rule stood in three copies** in `GuiCraftAmount` — `drawBG`, `actionPerformed` and
  `addQty` — each answering an unusable field differently (`0`, `1`, `0`), and the third narrowing to
  `int`. They are one `parseAmount`/`formatAmount` pair now. Same shape as the six copies of the display
  rule that the second audit axis found; the fix is deletion, again.
- **Rounding order is load-bearing.** All three copies rounded to a whole number *before* anything could
  scale it. In unit entry that turns one and a half buckets into two of them. `parseAmount` scales first
  and rounds last, and returns a finished `long` so no caller can get the order wrong.
- **`AEKeyFormatting` must not be used for an editable field.** Its `DecimalFormat("0.#")` is right for a
  label and wrong here: 1001 mB renders "1B", and reading that back loses the millibucket. Unit entry
  formats integrally instead — quotient, remainder, no `double` — and is only offered when the unit is a
  power of ten, because that is exactly when the round trip is exact.

`Settings.AMOUNT_ENTRY_UNITS` toggles it, client-side only: packets always carry the base unit, so two
players on one server can disagree about the display and nothing diverges. The step buttons scale with the
field, so a screen is entirely in buckets or entirely in millibuckets, never a mix. In the base unit —
the default — the buttons behave exactly as before, including the "first press replaces an untouched
suggestion" rule, which used to be spelled `result == 1` and now reads a flag.

The shared half lives in `appeng.client.gui.AmountEntry`: the scale a key reads in, the two conversions,
and the symbol drawing. It is static and stateless — a screen asks what scale it is in and converts.

**Held keys now repeat** in both amount screens and in the level emitter. 1.12 gates that on
`Keyboard.enableRepeatEvents(true)`; without it a held digit or backspace fires once. It is turned on in
`initGui` and back off in `onGuiClosed`, following `GuiMEMonitorable`, which was the only screen in the
mod that already did it. The order is safe in both directions — `Minecraft.displayGuiScreen` calls the
old screen's `onGuiClosed` *before* the new screen's `initGui`, so leaving the terminal for the amount
screen and coming back both end with the right setting.

Five screens with text fields still lack it, and they are not all the same case: `GuiPriority` and
`GuiQuartzKnife` are plain fields with the same defect, while `GuiRenamer`,
`GuiInterfaceConfigurationTerminal` and `GuiOreDictStorageBus` use `MEGuiTextField`, whose tooltip variant
already toggles repeat per focus and restores the previous value rather than forcing it off. Worth a
single pass that decides which of the two patterns the mod should standardise on.

### Two defects the same play-test turned up

**A hotbar key emptied filter slots, and could not move anything into a slot whose limit was not 64.**
One cause: `AEBaseGui.checkHotbarKeys` found the player's hotbar slot by `s.inventory == getPlayerInv()`,
and every `AppEngSlot` passes a **shared dummy `IInventory`** to its super constructor, so that comparison
was never true. Both loops it guarded were dead — including the one that sent `PacketSwapSlots`. So the
`getSlotStackLimit() == 64` branch was the only live path: slots that took it got vanilla's swap, which
empties a fake slot, and slots that did not got nothing at all, which is why an upgrade card could never
be inserted with a number key and why the optional filter slots looked safe.

Worth remembering as a shape: **a condition that is always false reads exactly like a feature that works,
because "nothing happened" is indistinguishable from "correctly refused"**. It cost a wrong fix first —
routing everything through the surviving branch, which was the dead one, and broke every screen at once.

The live version guards fake slots explicitly, drops the limit branch, and identifies the hotbar slot by
`SlotPlayerHotBar`/`SlotDisabled`. `canTakeStack` alone was not enough there either: an empty slot answers
"cannot take", and taking *into* an empty hotbar slot is half of what the key is for.

**Swapping cells in the workbench carried a filter across key types.** `TileCellWorkbench` copies what is
on screen into a newly inserted cell whenever that cell is blank — a deliberate convenience for cloning
settings between cells. It never checked that the two cells store the same thing, so a fluid filter
followed a swap into an item cell. It now compares against `getCellKeyType()` and clears the screen
instead when they differ; nothing is lost, because the config is written into the cell on every edit, so
the cell that was removed already carries its own copy.

### The level emitter, second pass

Same entry rules, but the screen is built differently and each difference had to be answered:

- **Two guards blocked a decimal point.** `Character.isDigit` in `keyTyped`, and `GuiNumberBox` built with
  `Long.class`, which reverts any text that will not parse. The box is `Double.class` now and the
  character gate admits one `.`, only while the field reads in units.
- **The threshold is sent on every keystroke, as the field's text.** It now sends a converted base-unit
  number instead, so what the emitter stores never depends on what the screen is displaying. This also
  retired the hand-rolled leading-zero stripping, which existed only because the raw string was going
  into `Long.parseLong` — and which would have eaten the `0` in `0.5`.
- **The server echoes that value straight back**, and the container used to write it into the text box
  directly. Re-rendering on the echo wipes a half-typed `1.` before its decimals arrive, so the screen now
  remembers what it sent and only writes back a value it did not cause. `ContainerLevelEmitter` lost its
  `@SideOnly(Side.CLIENT)` text box, `setTextField` and `onUpdate` along with it: a container cannot format
  an amount correctly, because only the client knows which unit is being displayed.
- **Two states have no unit at all** — `LevelType.ENERGY_LEVEL`, and an empty filter slot. Both answer
  null, which collapses to the base unit and hides the toggle.

`AEConfig.levelByMillibuckets` was left alone. It is dead — never read from the config, no caller — and it
describes the approach this work rejected, but `levelByMillyBuckets(int)` is public, and breaking a public
method to delete four unused numbers is a bad trade.

## The IO busses, reworked to match upstream (in progress)

Upstream gives the import and export busses a 63-slot filter — two rows always visible, five more unlocked
one per capacity card — the same shape the storage bus and formation plane already have here. Ours still
has the nine-slot cross. Four steps, one commit each, in this order: split the classes, widen the filter,
add a clear button, add the import type selection.

### Step 1 — the busses got their own container and screen (done, verified in game)

`ContainerUpgradeable` and `GuiUpgradeable` *were* the bus screen. Nine other containers and eight other
screens extend them, and every one overrode `setupConfig`, `addButtons` and `getBackground` — so the bodies
those bases carried were reachable from exactly one place, the busses, while the bus-specific state sat
where a dozen unrelated screens could see it. `ContainerIOBus`/`GuiIOBus` now hold the cross layout, the
craft-only and scheduling modes, `guis/bus.png` and the ImportBus/ExportBus title; both bases are abstract.

`GUI_BUS` also stopped accepting `IUpgradeableHost` and takes `PartSharedItemBus` — only the two busses
ever opened it.

Two things the code decided rather than the plan:

- `supportCapacity()` was read only by the `setupConfig` that moved out. Deleting it took seven overrides
  with it. **A protected method whose only caller moves is not "still an extension point".**
- `getName()` did not become abstract. `GuiInterface` draws no title at all, so an abstract method would
  have forced it to invent one; the base returns null and `drawFG` skips the title when it is absent.

Also worth recording: `ContainerLevelEmitter` had `@Override` on `getCraftingMode`/`setCraftingMode` while
carrying its own `cmType` field. It was not extending the base behaviour, it was borrowing a signature —
the base's `cMode` was write-only from its point of view. The annotations are gone.

### Step 2 — the filter is 63 slots (done, verified in game)

Config inventory 9 → 63, `availableSlots()` = `min(18 + capacity * 9, 63)`, upgrade slots 4 → 5, capacity
card limit 2 → 5, `MultiCraftingTracker` 9 → 63, `bus.png` replaced with the storage bus panel, screen 251
tall. No NBT migration: `readFromNBT` walks `#0..#62`, a missing key yields an empty tag and
`GenericStack.readTag` answers null for it, so the old nine settings land in the first row and a half —
both of which are now free.

The layout was about to be written a **third** time. `ContainerStorageBus` and `ContainerFormationPlane`
each carried the same "2 fixed rows, 5 optional" double loop, and the busses needed it too. It is
`ContainerUpgradeable.setupExpandableConfig(rows, cols, optionalRows)` now — upstream's
`addExpandableConfigSlots` — and all three call `setupExpandableConfig(2, 9, 5)`. `setupUpgrades()` became
a loop over `availableUpgrades()` in the same pass, which retired two more inlined copies of the five
upgrade slots.

**One regression, found in play-test, and worth the entry.** The scheduling button was gated on
`getInstalledUpgrades(CAPACITY) > 0`. Nothing about scheduling needs a capacity card — the condition was
standing in for "this bus has more than one slot", which was true only while an uncarded bus had exactly
one. With two rows free by default it hid the button precisely when round-robin first becomes useful.
Upstream has no such condition; ours no longer does either. **A condition that encodes a fact about a
different number will not fail when that number changes — it will just start lying.**

### Step 3 — a clear button, and the desync it uncovered (done, verified in game)

The button itself is `Settings.ACTIONS`/`ActionItems.CLOSE` on both busses and on the formation plane.
`ContainerStorageBus.clear()` and `ContainerCellWorkbench.clear()` turned out to be byte-identical, so the
method moved to `ContainerUpgradeable` and the three packet routes (`StorageBus.Action/Clear`,
`CellWorkbench.Action/Clear`, and the new one) collapsed into a single `Filter.Clear` that accepts any
`ContainerUpgradeable`.

**The desync.** Pull a capacity card without closing the screen, put it back, and the hidden rows returned
with their old contents — until the window was reopened, at which point they were empty. The server was
right the whole time; the client was never told. It took two independent defects, and fixing either one
alone changed nothing visible:

1. `AppEngSlot.putStack` refused to write to a disabled slot. That is also the client's sync entry point
   (`Container.putStackInSlot`), so the client could not accept a correction for a slot that was switched
   off. The guard is gone; refusing a *click* is now the caller's job, which for real slots `isItemValid`
   and `canTakeStack` already did, and for filter slots is an explicit check in `AEBaseContainer.doAction`.
2. `EntityPlayerMP.sendSlotContents` drops every slot packet while `isChangingQuantityOnly` is set — which
   is precisely the tick a click is being processed, and pulling the card *is* a click. Vanilla's broadcast
   still updated the container's record of what the client knows, so the next tick saw no difference and
   never retried. The emptied rows are now pushed with an explicit `SPacketSetSlot`.

Where that sweep lives matters and got it wrong once. Putting it in `detectAndSendChanges` fixed the busses
and not the storage bus or the formation plane, because both replace that method wholesale — they were
clearing their disabled slots *by accident*, inside `OptionalSlotFake.getStack()` as vanilla's diff loop
queried them. It is in `standardDetectAndSendChanges()` now, the one method every subclass routes through.

**Two lessons.** A bug can need two fixes, and testing each alone reads as "the fix did nothing" — the first
fix here was correct and looked worthless. And a base-class hook is only shared by the subclasses that
actually call it: `detectAndSendChanges` looked like the common path and was not.

### Step 4 — choosing which key types an import bus takes (done, verified in game)

The API half is `CONTRACT.md` §8.8. The server half was nearly free: `PartImportBus` already passed a
`Predicate<AEKeyType>` to `createImportStrategies`, it just always passed "every type with a strategy".
It passes the player's selection now and drops the cached strategy when that changes.

The screen is `ContainerKeyTypeSelection`/`GuiKeyTypeSelection`, reached by a tab on the import bus and
left by a tab back, the same way `GuiPriority` works. Its panel grows a row per type instead of reserving
a fixed number, because how many types exist is up to whoever registered them.

**The whole selection is synced, not just the enabled part.** One string,
`namespace:type=1,namespace:other=0`. A bitmask over registry ids was the obvious cheap encoding and is
wrong twice: an id is a position in a registry, and — the part that is easy to miss — the client cannot
know *which* types a given host allows, only which exist in the world. Both the membership and the state
have to come from the server.

**Follow-up: the button column collapses now.** With clear always present and scheduling always present on
an export bus, a hidden button left a visible hole in the middle of the column. `GuiIOBus.layoutColumn()`
packs the visible ones top-down every frame, from a list whose order *is* the layout - the same thing
upstream's `VerticalButtonBar.updateBeforeRender` does. Adding or removing a button no longer means
recomputing everyone's `y`.

### Step 5 — filter slots say what the network holds (done, verified in game)

Hovering a configured filter slot on either bus adds a `Stored: N` line, formatted by the key itself, so a
fluid reads `3B` and shift reads `3,000mB` - the same rule `AEBaseMEGui` uses for terminal rows.

`IStorageService.getCachedInventory()` is already a maintained snapshot, so a lookup is one map read. The
container rebuilds a `slot=amount` string every ten ticks and syncs it as one `@GuiSync` field. Half a
second stale is not wrong for a tooltip; a packet every tick would have been.

Two things the play-test corrected:

- **A disconnected bus reported `Stored: 0`.** No exception is thrown - a bus cut off from its cable forms
  a one-node grid of its own, and *that* grid genuinely holds nothing. Zero was a true answer to the wrong
  question. Gated on `proxy.isActive()` now: with no channel there is no number to report, so no line.
  **An error path that cannot fire is not the same as a correct answer.**
- Shift did nothing, because the formatter was pinned to `AmountFormat.FULL` instead of asking the keyboard.

**Textures were generated, not drawn.** `keytypes.png` is three horizontal bands copied pixel-for-pixel out
of `priority.png`'s frame (header 0–17, a clean fill row 20–37, footer 96–106), which is why it matches the
other panels exactly. The check and cross went into two unused placeholder cells of `states.png` at
(6,11) and (6,12), scripted, with the cell borders verified clear so nothing bleeds into a neighbour. Worth
knowing for the next screen that needs a panel: `priority.png` rows 4–54 and 67–103 are pure fill and can
be sliced again.

## Key repeat, and the middle click the configuration terminal never learned (done, verified in game)

**Repeat is on everywhere now.** The five screens that lacked it - `GuiPriority`, `GuiQuartzKnife`,
`GuiRenamer`, `GuiInterfaceConfigurationTerminal`, `GuiOreDictStorageBus` - use the screen-level pattern
(`initGui` on, `onGuiClosed` off) that three screens already had. `GuiInterfaceTerminal` was left alone:
its `MEGuiTooltipTextField` enables repeat on focus and **restores** the previous value rather than forcing
it off, so the two compose. That restore is the whole reason a per-widget and a per-screen mechanism can
coexist; a widget that just turned it off would have fought the screen.

**Middle click to set an amount did not work in the interface configuration terminal.** Three layers, each
found only after the one above it was fixed:

1. `AEBaseGui` offers the middle click on a `SlotFake`. The terminal uses `SlotDisconnected` - a different
   slot class over *the same interface config inventory*, reached remotely.
2. The whole `SET_AMOUNT` path addresses a slot by its index in `inventorySlots`. The terminal's slots are
   not in any container: they are `(which interface, which slot of it)`. `PacketInventoryAction` grew a
   branch that reads through `getSlotByID` instead.
3. **Writing back still silently did nothing.** The interface id is `ConfigTracker.which = autoBase++`, a
   per-container counter, and `byId` is rebuilt per container instance - so returning to the terminal
   builds a fresh container that hands the same interfaces *different* ids, and the id the click carried
   named nothing. `ContainerSetAmount` holds the interface's `IItemHandler` instead, which is owned by the
   interface and outlives both screens, and the write happens before the screen switch rather than after.

**An identifier that is minted by the thing you are about to replace is not an identifier.** The open GUI
was the only reason step 2 appeared to work: the container that issued the id was still the live one.

This is the divergence the audit's axis 2 predicted - "parallel `doAction` switches: `PacketInventoryAction`,
`AEBaseContainer`, the two interface terminals". One of three copies had not learned an action the others had.

### Scrolling a config slot up stopped at 8 on a bucket (fixed, verified in game)

`PLACE_SINGLE` - wheel up, no modifier - capped the slot at `getMaxStackSize() * 8`. That is not a limit the
slot has; it is a number that *coincides* with the real ceiling of 512 for an item that stacks to 64, and
gives 8 for a bucket. Ctrl+wheel worked because `DOUBLE` asks the slot instead. Both copies now use the
ceiling `SPLIT_OR_PLACE_SINGLE` sits next to and already used: `maxAmountIn(slot, key)` in
`AEBaseContainer`, `getMaxAmount(key)` in the configuration terminal.

Third instance in two days of the same shape: **adjacent branches of one switch, one taught the slot's real
capacity and the next not.** The earlier two were `SPLIT_OR_PLACE_SINGLE` stopping at 64 in a slot of 512,
and the hotbar swap. Worth treating "a magic multiplier where a capacity belongs" as its own audit lead.

## Amount screens: the accepted range, and "craft up to" (done, verified in game)

**The range.** Both amount screens now print the span they accept under the title, in whatever unit the
field is reading in - `1 - 512` for an interface config slot, `1 - 32B` for the same slot on a fluid. The
step buttons stop at the ceiling instead of stepping past it. Where nothing bounds the amount the line is
absent rather than printing a number: ordering a craft has no ceiling but `Long.MAX_VALUE`, and neither does
a pattern's output slot.

That second case was found by the axis-2 sweep and not by testing - `OptionalSlotFake` reports
`Integer.MAX_VALUE`, which `maxAmountIn` was happily scaling into "1 - 134217727B". **A sentinel that is
also a number will be used as one by whatever arithmetic it reaches.**

**`=` means "up to".** `=100` orders the difference between what the network holds and 100, rather than 100
more; already-satisfied orders never open the confirmation screen. Server-side, read from
`IStorageService.getCachedInventory()` - the snapshot the terminal already shows - and matching upstream's
`CraftAmountMenu.confirm(amount, craftMissingAmount, autoStart)` including the "already enough" case. The
prefix survives the step buttons and the unit toggle because both read the field before rewriting it.
`GuiSetAmount` inherits the field and pins the flag off: a slot's amount is already a total.

**Cancelling a plan goes back to the order.** `GuiCraftConfirm`'s cancel used to return to the terminal, so
adjusting an order meant finding the item again and retyping. It opens the amount screen now, seeded with
what was typed; the way out to the terminal is that screen's own tab, one step further rather than gone.

I had argued against this as "a navigation change, not a fix", and the owner asked for it anyway - correctly:
the two-clicks-away estimate ignored that one of the clicks is *finding the item in the terminal again*.

What is restored is **what the player typed, not what the job planned**. `ContainerCraftConfirm` keeps the
key, the number and the `=` flag separately from `result`, because an "up to" order plans the difference -
restoring from the job would answer `60` to someone who asked for `=100`, with no way to tell why. It is
captured when the plan opens rather than when it finishes, so cancelling mid-calculation restores too.

## Crafting patterns that take fluids straight from the network (done, awaiting a play-test)

A recipe calling for a bucket of water can draw the water and nothing else. Second toggle in the pattern
terminal, independent of ore-dict substitution, stored as `substitutefluids`; upstream's
`AECraftingPattern.canSubstituteFluids`.

**Eligibility is per slot, decided once, and hangs on the recipe's own remainder.** In `PatternHelper`'s
constructor: the slot holds something a registered `ContainerItemStrategy` recognises, **and** the emptied
container equals what `IRecipe.getRemainingItems` leaves in that slot. Both halves are needed. Without the
second, a mod's part-filled tank - which the recipe hands back untouched - would start costing three buckets
a craft, and a recipe that swallows the vessel would stop costing one. Nothing here knows what a bucket is;
`ContainerItemStrategies` was already type-erased, so an addon's key type gets this for free.

**The container is assembled and destroyed, and that is only sound because the two sources never mix.** An
eligible slot is supplied *only* as a key - never a real filled container out of storage, never an ore-dict
alternative. That is what makes "slot x is eligible" equal to "the container in slot x was fabricated",
derivable from the pattern alone. It has to be derivable: `TileMolecularAssembler` keeps a half-finished
craft in `gridInv` across a chunk reload and rebuilds `myPlan` from the pattern item, so a side channel from
the CPU would not survive to the moment the container is destroyed - and a fabricated bucket and a real one
are the same `ItemStack`, byte for byte. The rule itself lives in one place, `Platform.getRemainingItem`,
which `CraftingCPUCluster` and `TileMolecularAssembler` both ask instead of `getContainerItem`.

**Provenance is `forcePlan`, and it must be read before it is spent.** A pattern dropped straight into an
assembler and fed by an import bus gets *real* buckets, and destroying those would be eating the player's
items. `forcePlan` is set exactly when a CPU pushed the plan and is already saved to NBT, so it answers
after a reload too. Consequence, accepted: the same pattern in a standalone assembler ignores the toggle and
still wants filled containers. It has no network to draw from - it is fed.

The first version read it one line too late. `pushOut()` clears `forcePlan` as soon as the result is away
(and `recalculatePlan()` may drop `myPlan` with it), and the grid was emptied *after* that call - so every
CPU-driven craft looked hand-fed and handed back a bucket that had never been taken out of the network.
Both are captured into locals before `pushOut`. **A flag that means "how this got here" is spent by the
thing that finishes the job; read it before, never after.**

**Three landmines found by reading rather than by testing**, any one of which would have made the feature
look broken or silently duplicate items:

- `CraftingTreeNode` counted **a byte per millibucket** (`bytes += available`, six sites). 64 crafts with a
  water bucket would have cost 64,000 bytes against a 64k accumulator's 65,536. Scaled once in `dive()`
  rather than six times: the node holds a single key for its whole life, so dividing the sum is exact and
  cannot half-learn. This also makes existing **processing** patterns with fluids a thousand times cheaper,
  which is the correct number.

  The divisor is `getAmountPerUnit()` (1 for items, 1000 for fluids) and **not** `getAmountPerByte()`, which
  reads like the right method and is not: it is how densely a *storage cell* packs the type - 8 items,
  8000 mB - so dividing by it would have made every existing job eight times cheaper. A job is charged per
  thing, and for a fluid the thing is a bucket. Caught by reading the constant, not by testing; nothing
  in-game would have looked wrong, autocrafting would just have quietly become cheap.
- `AEConfig.enableCraftingSubstitutes` defaults to **false** and gates all of `CraftingTreeProcess:92-158`,
  while `CraftingTreeNode` and `CraftingCPUCluster` never check it. Hanging fluids inside that `if` would
  have had the plan reserve buckets and the CPU wait on water it never asked for - a job that never
  finishes, with no error anywhere. The fabricated branch sits outside the gate.
- `DualityInterface.pushPattern` has **three** destinations, not one, and a fabricated container may go to
  none of them: a third-party `ICraftingMachine` hands the emptied container straight back, a neighbouring
  network takes the whole table into its storage, and a plain `InventoryAdaptor` drops it in a chest. Each
  would mint a bucket out of water on every craft. All three are gated on one local now.

  The first attempt at that gate was `!canSubstituteFluids()` on the machine branch alone - which broke the
  feature outright, because **`TileMolecularAssembler` is itself an `ICraftingMachine`** and that branch is
  the only route from an interface to one. The job planned, the CPU filled, and nothing was ever pushed:
  a silent hang. The gate is an opt-in on the machine instead, `ICraftingMachine.acceptsFabricatedContainers()`,
  defaulting to false - so an addon's machine is passed over rather than quietly duplicating, and can join in
  by consulting `isContainerFabricated` and leaving the slot empty.

**`getRemainingItems` is asked on a throwaway copy.** It is free to empty the stacks it is handed, and mod
recipes do; the grid it was first called with is `PatternHelper.crafting`, the reference frame every
`isValidItemForSlot` test is rebuilt from. Draining it left an assembler accepting the water bucket - which
the constructor had already put in the pass cache - and rejecting every other ingredient, because the frame
those were tested against no longer contained any water. **A method that reads a thing is not thereby a
method that leaves it alone.**

**Atlas cells are not free just because they are blank.** The two button icons first went into
`states.png` cells (11,1) and (12,1), the only unregistered fully-transparent ones - and the droplet then
appeared along the top of every terminal. `GuiTabButton` draws its background as **25x22** from `(11*16, 0)`
and `(13*16, 0)`, so it reads six pixels down into row 1. The icons live in the bottom row now, which
nothing oversized reaches. Only two draws on this sheet are not 16x16 cells: that one and `GuiScrollbar`.

**The button previews its own effect.** Hovering it tints the qualifying slots green, answered by the same
`PatternHelper.findFabricatedSlots` the pattern will use once encoded - static and shared precisely so the
preview cannot drift from the rule. A container the recipe does not simply empty stays dark rather than
promising something. The recipe lookup is a scan of every registered recipe, so it is redone only when the
grid contents change.

**Two pre-existing defects fixed in passing**, both inside the blast radius. `CraftingCPUCluster`'s tail
put-back (`if (ic != null)`) restored the table but never the ingredients carried beside it, so a processing
pattern with a fluid lost it whenever no medium took the job; both put-backs are one method now. And
`getSubstituteInputs` amounts are normalised to "per one of the encoded ingredient", because callers
multiply by the slot's own count and a substitute arriving with a stack size would square it.

**Batching.** `getTimes()` returns 1 - one craft planned at a time - when any input carries a container,
because `request()` puts containers back only at the end of a batch. An eligible slot returns nothing, so it
is no longer a reason to give it up; the same item sitting in another, non-eligible slot still is.

**Known landmine, deliberately not touched.** For *item* substitution the same split is still there: the
plan is gated by `enableCraftingSubstitutes`, the CPU is not. With the config off and a substituting pattern,
the two can disagree about which item a craft will use. Left alone because fixing it changes ore-dict
behaviour for every existing pattern, which is a separate change with separate testing.

**Considered and rejected: batching by how many containers are in stock.** Worth recording, because the
starting observation is real and will occur to the next person too.

A recipe whose inputs carry a container plans **one craft at a time** - `getTimes` returns 1 - while every
ordinary recipe collapses to a single pass (`ceil(remaining / stackSize)` times, one call). So ordering 8192
of a bucket recipe is 8192 rounds against 1. The obvious fix is to bound the batch by the containers on hand
and credit them back properly, since `addContainers` returns exactly one container however many were
consumed. Note that credit is **not** a standalone bug: it is correct precisely because `getTimes` pins the
batch at 1, so the node only ever draws one. The two are a single package.

Rejected for two reasons.

- **Nesting breaks the bound.** The pool is shared by the whole subtree, but the bound is computed per
  process, and the outer process is served first even though the inner crafts happen first. Recipe A uses a
  bucket and contains B, which also uses one: A's batch takes every bucket, B finds none and cannot craft
  one, and a job that plans fine today comes back as "missing buckets". (Related and pre-existing, worth
  knowing: nested container recipes already need one container *per nesting level* at once, because a
  process holds its containers until the end of its own `request` - `CraftingTreeProcess.request` inserts
  the pending list only after every child has been asked.)
- **Both the payoff and the failure are invisible.** "Same plan, fewer rounds" cannot be eyeballed, and
  neither can a subtly different one. The nesting interaction above was found by talking it through, not by
  testing - which is exactly the problem with the change.

A retry-without-batching fallback would make the *outcome* safe (`CraftingJob:144-157` already resets the
tree with `setSimulate()` and re-runs on failure, so the machinery exists; `setSimulate` would additionally
have to clear each process's pending `containers`, which today is only ever cleared on a successful
`request`). It would not make the plan *shape* safe. Revisit only if a real pack turns out to order
container recipes in the thousands.

## The crafting CPU stops re-asking things that just said no (done, awaiting a play-test)

Three changes to `CraftingCPUCluster.executeCrafting`, taken from a server-side fixer the owner runs and
from ae-gtnh's fork, which solve two different halves of the same waste.

**Within a tick** (ae-gtnh's shape, `CraftingCPUCluster.java:776-789, 822, 852` there). `updateCraftingLogic`
re-runs `executeCrafting` for as long as anything moved, so one tick can ask the same question many times.
Two answers cannot change in between: a task that failed `canCraft` drops out of `workableTasks`, a per-tick
copy, and a medium that answered `isBusy()` goes into `busyMediums`. Both are cleared every tick, so nothing
stays skipped longer than that. `tasks` itself is untouched except where an exhausted task was already being
removed.

**Between ticks** (the fixer's shape). `isBusy()` only means "has something queued to send" - an interface
pointed at a *full* chest is not busy, so finding out it will refuse costs a whole `pushPattern`: build an
adaptor, simulate inserting every stack on the table. That ran every tick, per pattern, forever. A refusal
now doubles the wait before that (pattern, medium) pair is asked again, capped at **20 ticks**, and any
success clears it outright.

The cap is the one real trade-off: CPU time against latency. The fixer uses 60 ticks; a destination that
frees up mid-backoff would then wait up to three seconds, which is a throughput loss on a setup that is
merely intermittent rather than dead. One second keeps the win against genuinely stuck destinations and
misses a freed-up one at most once. The two within-tick measures cost nothing and need no such judgement,
which is why they carry most of the weight.

**Also fixed, unrelated and found while reading the same source:**
`ContainerPatternEncoder.transferStackInSlot` asked `getPart().getInventoryByName("pattern")` with no null
check, while `getInventoryByName` three methods below already knew a part may be absent. Shift-clicking a
blank pattern crashed for anyone using the **wireless** pattern terminal, which has no part.

**From that same fixer, deliberately not ported** - each one is a bug this port had already removed:

- Hash collisions in `AEItemStackRegistry`, which cached by raw `int` in a `Map<Integer, …>` with no
  equality check, so two items sharing a hash replaced each other. The class does not exist here;
  `AEItemKey` with a real `equals`/`hashCode` replaced the whole idea.
- `PacketInventoryAction` calling `sendToServer` where it meant `sendTo` - already correct here.
- An NPE in `FluidHandlerAdapter$InventoryCache` when `drain` was handed null - the null is checked one line
  earlier here.
- A recipe cache on `PatternHelper`; the owner rejected it as unnecessary.

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

Post-freeze edits to §1-§4 are the owner's call (§7). Eight have been approved - §8.5
(`wrapForDisplayOrFilter()` wraps with amount 0) was the last, on 2026-08-01, and was the only one that had
gone in ahead of its review:

1. **§8.3** — `ICraftingGrid.getCraftables(AEKeyFilter)` + `default isCraftable(AEKey)`. Keys carry no
   craftable flag, so the crafting grid answers instead. Mirrors upstream verbatim; additive.
2. **`StorageCells` GUI half** (commit `caffdcffe`) — `addCellGuiHandler` / `getGuiHandler(AEKeyType)` /
   `getGuiHandler(AEKeyType, ItemStack)` moved from an internal `src/main` class into api, so an addon
   shipping a cell with its own screen does not have to import an internal package. Upstream has no
   GUI-handler concept at all; this is a deliberate divergence.
3. **§8.4** — `PickupStrategy.Factory` now carries `Map<Enchantment, Integer>` instead of
   `int fortuneLevel, boolean silkTouch`. AE2UD's annihilation-plane energy formula also reads Efficiency
   and Unbreaking, which the upstream-shaped signature could not carry.
4. **§8.6** — no-argument `default ICraftingGrid.getCraftables()`, so the per-tick terminal diff can
   recognise "nothing changed" by identity instead of rebuilding the set.
5. **§8.7** — `default AEKeyType.getDefaultCraftAmount()`. Same behaviour as upstream, which passes
   `getAmountPerUnit()` at the call site; named here so a type can separate its display unit from its
   typical order.
6. **§8.8** — `appeng.api.util.KeyTypeSelection` and `KeyTypeSelectionHost`, so an import bus can say which
   key types it takes at all. Same package and method names as upstream; the "return to the previous
   screen" half stays in `appeng.helpers.ISubMenuHost` because it needs `GuiBridge`, and `src/api` imports
   nothing from `src/main`.
7. **§8.9** — `default ICraftingPatternDetails.canSubstituteFluids()` and `isContainerFabricated(int)`, so a
   crafting pattern can take a bucket's contents from the network instead of the bucket. Upstream carries
   the same two facts on `IInput`, a type this version does not have. The empty-container rule stays in
   `Platform` for the `src/api` reason again.

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
