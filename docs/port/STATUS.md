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

## Before starting wave 4 — read this

### Blocked: the portable-cell push design

**Do not start `ContainerMEMonitorable` until this is decided.** See `CONTRACT.md` §10, "Third case".

The old model pushed live inventory updates to open terminal screens. Two places have already lost that
and are waiting on the same decision:

- `appeng.helpers.WirelessTerminalGuiObject` (wave 2) — plain forwarding, no notification.
- `appeng.items.contents.PortableCellViewer` (wave 3) — same; its old `notifyListenersOfChange` on
  insert/extract has no replacement yet.

Both were left as plain forwarding *deliberately*, not dropped by accident. Wave 4 must either restore a
push mechanism or agree that the terminal polls. This is an owner decision because it is a behaviour
change either way.

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
agent gets: the contract, rule 6 in full, the §9.1 hazard, its exact file list, upstream reference paths,
and a named list of the fork-specific mechanics in its files that must survive. Each appends its own
subsection to §9 when it finishes.

Two conflict rules that mattered in wave 3 and should be kept: exactly one agent may own
`appeng.core.Registration` and the item-definition classes, and when two agents need to meet at a new
class, fix its fully-qualified name and signature in *both* briefs up front rather than letting one agent
name the other's class.

Reference sources: `Z:\harmony\sources\AE2-original` (modern upstream, the reference implementation),
`Z:\harmony\sources\ae-gtnh` and `Z:\harmony\sources\AE2FluidCraft-Rework-Unofficial` (older ancestors of
this fork), `Z:\harmony\sources\HadEnoughItems` (HEI — this fork targets HEI, not JEI).

The Ukrainian-language research wiki that preceded this port is at `Z:\harmony\wiki\AE2UD` (outside git).
