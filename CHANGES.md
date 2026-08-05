# Changelog

All notable AE2UD changes are grouped by the version in which they first appeared.

## Important compatibility notice

- AE2UD is not compatible with worlds created with AE2 Unofficial Extended Life.
- Addons made for standard AE2 or AE2 UEL are not compatible unless they explicitly support AE2UD.
- AE2UD is a heavily reworked, API-breaking fork and is not a drop-in replacement for another AE2 build.
- Back up the world before installing or updating the mod.

## 1.4.0 - 2026-08-05

### Universal storage components

- Removed the separate fluid storage components; fluid cells now use the same 1k, 4k, 16k, and 64k storage components as item cells, following [modern Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2).
- Added a distinct ME Fluid Cell Housing with a lapis-based recipe and a texture matching the legacy 1.12.2 art style.
- Fluid cells can be assembled directly or from their housing and component, and empty fluid cells disassemble back into the universal component and fluid housing.
- Removed the four fluid-component API definitions and added `IMaterials.fluidCellHousing()`.
- Existing completed fluid cells retain their contents, but obsolete standalone fluid components are intentionally not migrated.

### Terminal pins

- Added automatic crafting-result pins adapted from [modern Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2), with its animated Molecular Assembler lights. Rows are created only after a craft becomes active, while completed pins remain until the terminal is reopened.
- Added persistent per-player manual pins and row controls adapted from [GTNewHorizons' Applied Energistics 2 Unofficial](https://github.com/GTNewHorizons/Applied-Energistics-2-Unofficial).
- Crafting and player pins occupy separate sections above terminal contents, ignore display filters, and remove their visible keys from the ordinary sorted list.
- Shift-middle-click toggles a manual pin; carried items, filled containers, and HEI ingredients can be placed directly into player pin slots.
- Pin storage and interaction support every registered `AEKey` type and are exposed to addon terminal hosts through the public API.
- Added client settings for independently hiding crafting and player pins.
- Initial pin state is pushed with the opening container, avoiding a delayed row resize after the terminal appears.

### Extensible upgrade cards

- Replaced the fixed upgrade enum with an item-and-metadata registry that lets addons register arbitrary cards and compatible machines.
- Added public upgrade-inventory factories for addon machines and items, plus automatic compatibility tooltips for registered cards.
- Added combinable speed and capacity traits with configurable points, inherited standard-machine support, and overflow-safe arithmetic.
- High-tier speed cards extend the existing machine curves without changing standard-card speeds; custom Matter Cannon support remains explicit opt-in.
- High-tier capacity cards can replace several standard cards but remain capped by each mechanism's existing filter capacity.

### Quantum bridge startup

- Quantum Network Bridges connect their grids without requiring both sides to be powered first.
- Power supplied on either side can start the combined network, while both bridges continue to consume their normal idle power.

### Import bus inverter card

- Import buses accept one Inverter Card, changing a configured whitelist into a blacklist for items, fluids, and addon key types.
- An inverted empty filter still imports everything, matching modern AE2 behaviour.
- Whitelist and blacklist fluid filters can find an allowed fluid behind a rejected tank in a multi-tank handler.

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
- HEI item panels avoid the side controls in wired and wireless Interface Terminals and the Interface Configuration Terminal.
- Fixed the Interface Configuration Terminal repeating the scrollbar trough texture at expanded heights.

### Craftable marks

- Terminal rows that are stocked and also craftable show a small "+" in their upper-left corner, next to the amount.
- Craftable-only rows show "+" in place of the amount instead of the old "Craft" text, following [modern Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2).
- Pattern terminal grid and output slots carry the same mark for keys the network can craft.
- HEI recipe screens opened from a terminal mark every ingredient that terminal can already craft, adapted from [RandomComplement](https://github.com/Circulate233/RandomComplement).
- Removed the now unused `GuiText.SmallFontCraft` and `GuiText.LargeFontCraft` translation keys.
- Removed the `useTerminalUseLargeFont` client setting; slot amounts and the craftable "+" now render at one fixed, larger size that still fits a four-digit count.

### Dependencies

- AE2UD now requires [MixinBooter](https://github.com/CleanroomMC/MixinBooter) 10.7 or newer.

### Terminals and HEI

- Dropping an HEI ingredient onto a terminal search field searches for that ingredient's name, adapted from [GTNewHorizons' Applied Energistics 2 Unofficial](https://github.com/GTNewHorizons/Applied-Energistics-2-Unofficial).
- HEI resolves fluids and other non-item keys in AE2UD slots through its `ISlotIngredientProvider` API, replacing the previous wrapped-ingredient shortcut.
- Pattern terminal amount buttons change fluid amounts as well as item amounts.
- Removed the unreachable Max Count amount button along with its action, tooltip, and translations.
- Fixed the Level Emitter's mB/B unit toggle button not being excluded from the HEI ingredient panel, so it could be covered or miss clicks whenever it was visible.

### Autocrafting

- A finished autocraft job fires the item-crafted event for the player who requested it rather than a fake player, so quest and achievement mods credit autocrafting to the right player.
- The new `AutocraftItemCraftedEvent` feature flag disables the event for packs that do not want it.
- A crafting CPU can be suspended and resumed from the Crafting Status screen, adapted from [GTNewHorizons' Applied Energistics 2 Unofficial](https://github.com/GTNewHorizons/Applied-Energistics-2-Unofficial). A suspended CPU holds its current job without sending out new crafting work until resumed.
- The Crafting Status CPU list dims a suspended CPU's tile.
- Removed the disabled "CPUs: #" label button from Crafting Status; the CPU list on the left already highlights the selected CPU, and the label was overlapping the new Suspend button.

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
