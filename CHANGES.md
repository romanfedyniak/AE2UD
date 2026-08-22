# Changelog

All notable AE2UD changes are grouped by the version in which they first appeared.

## Important compatibility notice

- AE2UD is not compatible with worlds created with AE2 Unofficial Extended Life.
- Addons made for standard AE2 or AE2 UEL are not compatible unless they explicitly support AE2UD.
- AE2UD is a heavily reworked, API-breaking fork and is not a drop-in replacement for another AE2 build.
- Back up the world before installing or updating the mod.

## Unreleased

### Pattern terminal

- The ME Pattern Terminal now encodes processing patterns on a four-by-four grid holding 32 inputs and 8 outputs, with a button that turns the grid round into 8 inputs and 32 outputs and a scrollbar that pages through it. Crafting patterns keep their three-by-three matrix. Adapted from [GTNewHorizons' Applied Energistics 2 Unofficial](https://github.com/GTNewHorizons/Applied-Energistics-2-Unofficial).
- The ME Extended Processing Pattern Terminal has been removed; the pattern terminal covers everything it did. **Terminals already placed in a world disappear when it is loaded, along with any patterns left in their slots, and copies sitting in inventories become an unnamed item.** Patterns those terminals encoded are unaffected and keep working.
- The wireless pattern terminal gained the same grid, orientation and paging.
- Removed the `IParts.expandedProcessingPatternTerminal()` API definition along with the part.
- The pattern terminal's own grid switches over when the crafting/processing tab is switched, carrying one of each item across; a fluid in the processing grid cannot survive in a crafting recipe and is cleared, as it is in GTNewHorizons' build.
- Recipes transferred from HEI turn the grid round on their own when a recipe has more outputs than the current orientation can show.
- Filling the grid - transferring a recipe from HEI, or putting an encoded pattern into the terminal - sends it back to its first page, so the result is visible instead of looking like nothing happened.

### Fixes

- An internal inventory that grew between versions loaded back at its old size, because the size stored in the save overrode the one it was built with. Any container addressing the new slots then threw on open. The constructed size now wins and surplus saved slots are dropped.
- Dragging a filled container out of HEI onto a processing pattern's grid left the container in the slot instead of the fluid inside it, while clicking the same bucket by hand set the fluid. The drag follows the same rule as the click now - left button takes what the container holds, right button takes the container itself - which is what filter slots and the interface configuration terminal already did. A fluid dragged onto the crafting matrix is refused instead, since a crafting recipe cannot hold one.
- HEI drew its item list straight over the buttons beside the Crafting CPU and Crafting Status screens. Those screens told HEI about the terminal-style button alone, so every button added beneath it since - **Hide stored items**, and now the CPU's selection mode - was left uncovered. Every button in the column is reported now.
- Holding shift over an encoded pattern showed an empty slot instead of what the pattern makes, for anything drawn by a renderer of its own rather than out of quads - a vanilla shield or chest, a GregTech machine, an IndustrialCraft cable. The preview handed the renderer the output's model while leaving the pattern in the stack, so those renderers looked their contents up in a pattern and drew nothing. The stack is swapped now, not the model, so every item previews as itself.
- The preview also works in a plain inventory rather than only in AE's own screens, and labels the slot with the amount the pattern makes, in the same digits and the same corner the ME Interface uses. The pattern's own stack count gives way to it while shift is held. It applies to a pattern in hand, on the ground and in an item frame too, as it always did.
- The confirm button in the renaming screen the Certus Quartz Cutting Knife opens - the one that renames an ME Interface or any other named block or part - was drawn without its bottom border and looked cut off. A vanilla button takes its whole height from the top of a frame drawn at 20 pixels, so anything shorter loses the bottom edge; this one is 12. It is drawn in four pieces now, each border taken from the edge it belongs to, so the frame closes at the size the window has room for.

### Autocrafting

- The Crafting Status screen has a **Hide stored items** toggle in its bottom-right corner: it drops the rows that are only waiting in the CPU, leaving what is still being worked on. Adapted from [GTNewHorizons' Applied Energistics 2 Unofficial](https://github.com/GTNewHorizons/Applied-Energistics-2-Unofficial).
- Added a **Crafting Tree** view of a crafting plan, reached by the button in the top-right corner of the Crafting Plan screen and back again. It shows what the plan needs and, under each thing, how it is obtained - taken from storage, crafted from a pattern, requested from a level emitter, or missing - and what that in turn costs. Adapted from [NovaEngineering's AE2CraftingTree](https://github.com/NovaEngineering-Source/AE2CT-Legacy) and [GTNewHorizons' Applied Energistics 2 Unofficial](https://github.com/GTNewHorizons/Applied-Energistics-2-Unofficial).
- The tree is dragged and zoomed with the mouse, branches fold away by the arrow under a node, and the search field jumps between matches with Enter and Shift+Enter. The same CPU table and Start button as the plan screen are there, so a job can be sent off without switching back.
- **Missing only** hides every branch that the network can supply, leaving what it cannot. It turns itself on when a plan came up short, which is when the tree is usually opened.
- **Save as image** writes the whole tree - not just the part on screen - to a PNG in the `screenshots` folder, and prints a link in chat that opens the file. The Crafting Plan screen has the same button, saving its list the same way, however many rows it runs to. Adapted from [GTNewHorizons' Applied Energistics 2 Unofficial](https://github.com/GTNewHorizons/Applied-Energistics-2-Unofficial).
- **Shift+click a craft node** in the tree to highlight every machine of that kind in the world and close the screen, the way the interface terminal points at an interface. The positions are asked for on the click rather than carried in the tree, which would cost far more than the rare click that wants them.
- A craft node in the tree shows **the machine the pattern would be pushed to** rather than a generic mark, and names it in the tooltip. The ME Interface Terminal and ME Interface Configuration Terminal draw the same machine beside each interface's name.
- Added `ICraftingMedium.getMachineIdentity()` and `getMachineLocation()`, and `ICraftingGrid.getMediums(pattern)`, so an addon's own crafting machine can say what it looks like and where it is. Both medium methods have defaults, so existing addons keep compiling.
- Amounts in the tree are what the job will really do: a pattern that was considered and dropped is left out, an ingredient that was substituted is shown as the item actually taken, and each branch's amount is its per-craft cost times the number of crafts.
- HEI's recipe keybinds, bookmarks and tooltips work over the tree's nodes, over the Crafting Plan and Crafting Status lists, and over the amount screen's slot, through one shared way of asking a screen what the cursor is over.
- The Crafting Plan screen picks its crafting CPU from the same table the Crafting Status screen uses, replacing the "Crafting CPU:" button that could only be cycled one CPU at a time. Its first row, **Automatic**, is the old default: the network chooses a CPU when the job is submitted. The window is shorter by the height that button occupied. Adapted from [GTNewHorizons' Applied Energistics 2 Unofficial](https://github.com/GTNewHorizons/Applied-Energistics-2-Unofficial).
- Both screens now share one table, so a CPU is named by a serial rather than by its position in the list. Previously a CPU appearing or disappearing while the list was open shifted every entry below it, and the selection silently moved to a different CPU.
- A crafting CPU can be **kept for players or for automation**, through a button on its own screen - right-click any block of the CPU to open it. A CPU set to *Only for requests by players* is passed over when a level emitter, an interface or an export bus asks for a craft; one set to *Only for requests by automation* is left out of the CPU table on the Crafting Plan screen and refuses a job sent to it by a player anyway. Set it on CPUs you keep for pre-crafting and a player's request will never queue behind them. Adapted from [GTNewHorizons' Applied Energistics 2 Unofficial](https://github.com/GTNewHorizons/Applied-Energistics-2-Unofficial), under the name modern Applied Energistics 2 gives it.
- A CPU kept for players or for automation is marked in the corner of its row in the Crafting Status screen's CPU table, and the tooltip says which it is. A CPU open to everything is the usual case and is left unmarked.
- The mode is remembered by every block of the CPU rather than by the CPU as a whole, so adding an accelerator - which takes the multiblock apart and forms it again - does not quietly reset it. Blocks that have never been given a mode do not count, so extending a CPU with fresh blocks keeps its mode; merging two CPUs that disagree leaves the result open to everything, and taking them apart again gives each its own mode back.
- Added `ICraftingCPU.getSelectionMode()` and `appeng.api.config.CpuSelectionMode`, matching modern AE2. **This is a breaking API change:** anything implementing `ICraftingCPU` has to answer it.
- **A job that cannot start now says why.** The Crafting Plan and Crafting Tree screens put a panel over their contents naming the reason - an incomplete plan, no CPUs at all, the chosen CPU being busy, offline or too small, an ingredient that has gone from the network since the plan was worked out, or no CPU being suitable, which is counted out as "2 busy, 1 too small, 1 kept for other requests". Until now a Start that failed silently threw the plan away and worked out a new one, which looked like nothing happening at all. Adapted from modern Applied Energistics 2.
- The panel's **Retry** submits the very same plan again, **Replan** works out a fresh one - the old silent behaviour, now a choice - and **Cancel** puts the plan back on screen.
- Added `appeng.api.networking.crafting.ICraftingSubmitResult`, `CraftingSubmitErrorCode` and `UnsuitableCpus`, and `ICraftingGrid.submitJob` returns the result rather than a link. **This is a breaking API change:** a caller that wants the link now asks the result for it.
- A crafting CPU that is idle now shows **how many accelerators it has** in its row of the CPU table, beside the bytes it holds, so a CPU can be picked by speed as well as by size without waiting for the tooltip. A CPU with none is left unmarked. Adapted from [GTNewHorizons' Applied Energistics 2 Unofficial](https://github.com/GTNewHorizons/Applied-Energistics-2-Unofficial).

### P2P tunnels

- Added the **ME Interface P2P Tunnel**, which lends an ME Interface's faces to somewhere else: the input stands in front of an interface and every output stands in for it beside a machine of its own, so one interface can drive as many machines as there are outputs. Adapted from [GTNewHorizons' Applied Energistics 2 Unofficial](https://github.com/GTNewHorizons/Applied-Energistics-2-Unofficial) and [NAE2](https://github.com/AE2-UEL/NAE2).
- The interface sees the tunnel as an ordinary crafting machine standing on that face, so nothing about how it looks for a machine changes. The tunnel hands each pattern to the next output in turn, and every output keeps its own queue of what its machine has not taken yet - a machine that has backed up does not stop the interface feeding the others. Blocking mode is judged per output for the same reason.
- Each output also offers its neighbour the interface's stocked items and fluids, which is how a machine there both draws what the interface stocks and hands its results back. Network access is deliberately not offered: a storage bus on an output would otherwise see the whole network without paying for a channel, which is what the ME P2P Tunnel is for.
- An output may face another tunnel's input, so tunnels chain, stock and results included; a pair pointed at each other is caught rather than recursing forever.
- A terminal lists the interface under the machine its outputs stand beside, naming that machine when every output has the same kind and the tunnel itself when they differ.
- **An ME Interface now attunes a P2P tunnel to this type rather than to the item tunnel.** A hopper, a chest, a storage/import/export bus and the other item-related triggers still give the item tunnel.
- Added `ICraftingMachine.of(tile, side)`, which finds a crafting machine on a neighbour whether it is the block itself or a part on that side of a cable bus. Patterns were offered to block entities only, so a part could never be a crafting machine.

### Storage cells

- **The Creative ME Storage Cell and the Creative ME Fluid Storage Cell have been merged into one.** The surviving Creative ME Storage Cell holds an endless amount of every kind of content at once, items and fluids together, and is configured the same way as before. Mirrors upstream [Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2), which ships a single type-agnostic creative cell. **A Creative ME Fluid Storage Cell already placed in a world disappears when it is loaded, along with what it was set to hold; the ordinary Creative ME Storage Cell replaces it in full.**
- Removed the `IItems.fluidCellCreative()` API definition along with the item.
- A cell no longer declares what it stores through `ICellWorkbenchItem`; that method is gone, and the kind of content a cell holds is now decided by the cell's own partition inventory (`CellConfig`, which takes the key types it accepts) and reported by its contents through the new `StorageCell.getSupportedKeyTypes()`. A cell is free to hold several kinds of content at once.
- The ME Chest asks the installed cell what it can hold instead of reading a type off the cell item: it offers itself to neighbours as a tank when the cell holds fluids, and its input slot accepts whatever the cell would actually take. A creative cell set to a fluid therefore works as a tank, and one set to items does not pretend to be one.
- The IO Port counts a transfer per key rather than per cell, so a cell holding items and fluids together moves an item at a time and a bucket at a time as appropriate.
- **A View Cell can now be filtered by fluids** (and by any content type an addon registers), not by items alone.
- **An addon can now ship a storage cell that holds several kinds of content in one cell.** `IBasicCellItem.getKeyType()` became `getKeyTypes()`, and a cell's bytes are counted per content type - a byte holds eight items or a quarter of a bucket, so each type pays its own way and a byte is never shared between two of them. Every cell AE2UD itself ships names one type, for which the arithmetic is unchanged, and no cell in an existing world needs migrating: what a cell holds has always been saved as the keys themselves, and the totals beside them are only a cache.

### Terminals and HEI

- Ctrl+Move Items on a HEI recipe now crafts whatever ingredients the crafting terminal is missing instead of refusing the transfer; Ctrl+Shift starts that craft immediately instead of opening the confirmation screen. Adapted from [NAE2](https://github.com/AE2-UEL/NAE2).
- Fixed the craftable "+" mark showing on real items already sitting in the crafting terminal's own crafting grid; it still shows on network item lists and the pattern terminal's placeholder ingredient slots.
- Holding Alt no longer replaces the amounts in the ME Pattern Terminal's encoding slots with the craftable "+". Alt still does so on a network row, where it previews what an Alt click would craft; a slot that only displays a key has no such click, so it keeps showing what the pattern is set to.
- Fixed the ME Interface Configuration Terminal drawing an empty frame around every row: the row background was taken from the list's top border, which repeated it every eighteen pixels. The border now belongs to the header, as it does in the ME Interface Terminal, and the rows below the last interface are plain background.
- Shift+right-click on a filled fluid container (or any other item a `ContainerItemStrategy` supports) in the player's own inventory pours it into the network instead of just shift-transferring the container; Shift+Ctrl+right-click does the whole stack. Shift+left-click is unchanged. The same Ctrl-for-the-whole-stack modifier also works when filling or emptying a held stack of containers by left/right-clicking a network row. Emptying/filling a whole stack keeps the processed result together - on the cursor (or back in the source slot) if every container was processed, otherwise the leftover unprocessed containers stay there and the processed ones move to the inventory as a single stack.

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
- A crafting pin whose job has ended says so instead of leaving the last "Crafting: 3 / 64" standing: **Done** in green, or **Cancelled** in red if the job was cancelled. A pin only speaks once every job behind it has ended, so an item split across several CPUs keeps counting until the last of them stops, and a cancellation is reported even if another CPU finished its share. A job that ends without saying how - a CPU taken over by someone else, a world reloaded mid-craft - shows no status line rather than guessing one.
- Added `ICraftingCPUListener.onCraftingJobFinished()`, which reports how a CPU's job ended. **This is a breaking change for anything implementing that interface.**

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

AE2UD continues [AE2 Unofficial Extended Life](https://www.curseforge.com/minecraft/mc-mods/ae2-extended-life), which is based on [Applied Energistics 2](https://www.curseforge.com/minecraft/mc-mods/applied-energistics-2). Selected fixes, features, implementation ideas, and reference code were also adapted from [GTNewHorizons' Applied Energistics 2 Unofficial](https://github.com/GTNewHorizons/Applied-Energistics-2-Unofficial), the actively maintained [Applied Energistics 2 upstream project](https://github.com/AppliedEnergistics/Applied-Energistics-2), [NAE2](https://github.com/AE2-UEL/NAE2), [NovaEngineering's AE2CT-Legacy](https://github.com/NovaEngineering-Source/AE2CT-Legacy) and [RandomComplement](https://github.com/Circulate233/RandomComplement).

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for complete attribution and licensing information.
