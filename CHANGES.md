# Changelog

All notable AE2UD changes are grouped by the version in which they first appeared.

## Important compatibility notice

- AE2UD is not compatible with worlds created with AE2 Unofficial Extended Life.
- Addons made for standard AE2 or AE2 UEL are not compatible unless they explicitly support AE2UD.
- AE2UD is a heavily reworked, API-breaking fork and is not a drop-in replacement for another AE2 build.
- Back up the world before installing or updating the mod.

## Unreleased

### Terminal type filters

- Added per-terminal visible-type selection for network and wireless terminals, using the same extensible type picker as the import bus.
- Hidden types are excluded from stored, craftable, and live-updating terminal rows; at least one type always remains visible.
- Moved type-selection buttons into the left-side button columns in terminals and import buses.

### Terminal layout

- Added the modern Small, Medium, Tall, and Full-Height terminal styles, using 25%, 50%, 75%, and 100% of the available screen height.
- Applied terminal styles to ME terminals, interface terminals, the interface configuration terminal, crafting status, crafting confirmation, and network status.
- Added forward and reverse style cycling and made Small the default for new client configurations.
- Portable cells remain compact when their type capacity fits in three rows; addon portable cells with larger capacities can opt into expanded layouts through the public API.
- HEI item panels avoid the terminal-style controls in Crafting Plan and Crafting Status.
- HEI item panels avoid the fluid-unit toggle on amount entry screens.

## 1.3.0 - 2026-08-02

This was the first AE2UD release published on CurseForge. It does not require installing any earlier AE2UD release.

### Storage cells

- Added a configurable creative fluid storage cell.
- Creative item and fluid cells report `2^52 - 1` stored units instead of `Integer.MAX_VALUE`.
- Storage amount aggregation saturates at the `long` limits instead of wrapping on overflow.

### Autocrafting and patterns

- Crafting patterns can take fluid contents directly from ME storage instead of requiring filled containers in the network.
- Dropping an encoded pattern onto the blank-pattern slot restores it to a blank pattern.
- Encoded pattern tooltips show the player who encoded the pattern.
- Crafting CPUs stop repeatedly asking the same provider after it refuses a push during the current scheduling pass.
- Crafting Confirmation displays the total pattern execution step count.
- Ingredient rows show what percentage of the available ME stock the request will consume, including mixed stored, craftable, and missing rows.
- Active Crafting Status CPUs show a red-yellow-green progress bar.
- Crafting Status tooltips show the craft name, remaining output, completed and total steps, percentage, elapsed time, and requester.
- Status information supports both item and fluid crafting jobs.

### Interaction and presentation

- Wrench-dismantled blocks, parts, facades, and contained items go to the player's inventory first; only overflow drops into the world.
- Controller light transitions are smoothly interpolated between animation states.
- Fixed the locked player inventory slot being drawn on the wrong row.

### Project metadata

- Added complete project lineage, licensing, and third-party attribution documentation.
- Updated the project documentation for the first CurseForge release.

## 1.2.0 - Not released

Version 1.2.0 was skipped. The changes developed after 1.1.0 were released together as 1.3.0.

## 1.1.0 - 2026-08-01

### Import and export buses

- Gave import and export buses dedicated containers and screens based on the modern upstream layout.
- Expanded their configurable filter to 63 slots.
- Added buttons for clearing filters and fixed stale disabled slots after upgrades or settings changed.
- Added extensible key-type selection to import buses.
- Collapsed the side button column to leave more space for the filter.
- Filter tooltips show how much of the configured item or fluid is currently stored in the ME network.
- Scrolling a configurable slot respects that slot's actual limit instead of stopping at eight buckets.

### Amount entry and autocrafting

- Crafting amount screens use the requested key type's own units and sensible default amounts.
- Amount screens show the accepted input range.
- Added `=amount` syntax to craft only enough to reach a requested total stored amount.
- Cancelling crafting confirmation returns to the amount screen without losing the original request.
- Held-key repeat works in amount screens, level emitter screens, and other supported text fields.
- Fixed remote exact-amount configuration.
- Level emitter thresholds use and display the selected key type's own units.

### Slots and controls

- Fixed hotbar keys emptying some filter slots, rejecting valid replacements, or moving a filter into a cell of another type.
- Oversized stacks moved to the hotbar are split safely instead of being handed to a vanilla slot unchanged.
- Fixed the build workflow overwriting `build.gradle`.

## 1.0.0 - 2026-07-31

The initial AE2UD release completed the API-breaking migration from AE2 Unofficial Extended Life to the modern generic storage architecture.

### Generic item and fluid storage

- Replaced the legacy `IAEStack` and storage-channel architecture with the modern type-erased `AEKey`, `AEKeyType`, `GenericStack`, `KeyCounter`, and `MEStorage` model.
- Unified item and fluid handling across storage, terminals, cells, buses, interfaces, monitors, planes, level emitters, crafting, packets, and integrations.
- Added a strategy-based world interaction layer so the same automation parts can support every registered key type.
- Reworked configuration inventories to hold items, fluids, and future key types without fluid placeholder items leaking into normal logic.
- Generic terminals can fill or empty held fluid containers.
- Generic import buses, export buses, storage buses, formation planes, annihilation planes, and level emitters can work with fluids.
- Interfaces can stock, serve, display, and push both items and fluids.
- Interface terminals can configure and display fluids directly.
- Fluid ingredients in processing patterns can be delivered to machines.
- Removed the now-redundant legacy fluid terminals, fluid buses, fluid interfaces, fluid planes, and fluid level emitters after their functionality moved into the generic implementations.
- Removed the Identity Annihilation Plane.

### Storage cells and filters

- Cell partitions accept generic fluid keys and no longer list the same key more than once.
- Cell GUI handlers can be overridden per key type through the public API.
- Filter slots can be configured with fluids by clicking a held container, dragging from HEI, or entering an exact amount.
- Left and right mouse buttons can select the container itself or its contents when configuring a filter.
- Legacy fluid placeholders in existing configuration data are recognised and converted for display and use.
- Filter and card slots no longer display or retain meaningless stack counts.
- Shift tooltips show the exact configured amount using the key type's own unit and formatting.

### Autocrafting and patterns

- Crafting CPUs are released correctly when a job finishes.
- Fixed crafting progress counts and made craftable entries appear as soon as a terminal opens.
- Restored craftable entries in wireless terminals.
- Fixed pattern previews and CPU execution for fluid and other non-item ingredients.
- Fixed patterns reading generic fluid slots as placeholder items.
- Fixed interfaces sleeping while fluid work was still pending.

### Terminals, HEI, and interaction

- Terminal listings read the grid's cached inventory instead of walking every mounted storage device for every open terminal.
- Holding Shift can freeze terminal row order while amounts continue updating live.
- Terminal sorting is a total order, preventing unstable rows when entries compare equally under the selected sort mode.
- HEI recipe and usage keybinds work over fluid rows.
- HEI ingredient dragging and quick-move interaction can configure fluid-aware slots.
- Middle-clicking a configurable slot opens an exact amount entry screen.
- Counts of one are no longer drawn redundantly on configured slots or repeated in their tooltips.
- Wrapped fluid and generic key names no longer recolour the remainder of a tooltip line.
- Partition tooltips show the configured resource name plainly.

### Automation and quality of life

- The magnet card can insert collected items directly into ME storage.
- Magnet card processing is paced per player instead of once per matching item stack.
- A chest terminal can open without requiring a storage-cell GUI handler.

### Performance

- Added cached network snapshots for terminals that do not receive direct watcher updates.
- Cached the network craftables set and made single-key craftability checks use direct lookups instead of scanning every pattern.
- Terminal craftable diffs can be skipped entirely when the cached set has not changed.
- Migrated item and fluid accounting to `KeyCounter`, avoiding legacy stack-copy and channel-conversion paths.
- Preserved earlier AE2 UEL performance work for large and channelless networks, storage drawers, interfaces, import buses, level emitters, and incremental network-content tracking.

### Fixes and stability

- Fixed the basic cell handler incorrectly claiming creative storage cells.
- Added the missing model for generic stack wrapper items.
- Fixed fluid filter callback dispatch, cell partition preference, and incorrect fluid amounts.
- Fixed generic buses initially moving items but not fluids.
- Fixed formation planes crashing when facing blocks without an item representation.
- Fixed formation planes losing generic filters and made them place every supported key type.
- Fixed crafting CPUs destroying non-item ingredients.
- Fixed equality and hash-code inconsistencies for keys with empty tags.
- Fixed sub-bucket amounts being read in the wrong unit.
- Fixed security stations incorrectly listing network craftables in their cell-only terminal.
- Fixed world unload aborting when a grid node was in the middle of propagation.
- Fixed crafting notification identity and text overflowing the toast.
- Fixed fluid interfaces and pattern terminals disagreeing about the generic NBT format.
- Fixed the wireless terminal being misclassified as cell-only storage.
- Preserved correct live updates for terminal counts, crafting flags, and fluid quantities.

### API, integrations, and distribution

- Migrated the complete public storage API to the `AEKey` model; addons must explicitly port to AE2UD.
- Added extensible key-type registration, storage strategies, generic filters, and per-key-type formatting and fuzzy behaviour.
- Exposed the storage-cell GUI handler registry through the API.
- Migrated mod integrations to the generic storage model and Had Enough Items integration.
- Removed the obsolete built-in version checker and legacy `rv6`, stability, and build-number fields.
- Versions are derived from `v`-prefixed Git release tags.
- Added GitHub release automation and reproducible Java 17 builds targeting Java 8-compatible mod bytecode.

## Credits

AE2UD continues [AE2 Unofficial Extended Life](https://www.curseforge.com/minecraft/mc-mods/ae2-extended-life), which is based on [Applied Energistics 2](https://www.curseforge.com/minecraft/mc-mods/applied-energistics-2). Selected fixes, features, implementation ideas, and reference code were also adapted from [GTNewHorizons' Applied Energistics 2 Unofficial](https://github.com/GTNewHorizons/Applied-Energistics-2-Unofficial) and the actively maintained [Applied Energistics 2 upstream project](https://github.com/AppliedEnergistics/Applied-Energistics-2).

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for complete attribution and licensing information.
