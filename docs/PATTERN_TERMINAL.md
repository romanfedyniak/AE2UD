# Pattern terminal

AE2UD has one pattern terminal. It encodes crafting patterns on a three-by-three matrix and processing
patterns on a four-by-four grid that pages and inverts, following
[GTNewHorizons' AE2 Unofficial](https://github.com/GTNewHorizons/Applied-Energistics-2-Unofficial). The
separate Extended Processing Pattern Terminal it replaces was removed.

## Geometry

A processing pattern has two sides, and the invert button decides which of them is the wide one:

| | not inverted | inverted |
| --- | --- | --- |
| inputs | 32 | 8 |
| outputs | 8 | 32 |

Both are one grid of the same shape read two ways. The **expanded** side is the whole four-by-four grid on
each of two pages, so `4 * 4 * 2 = 32` slots. The **compact** side shows a single column of four at a time,
also on two pages, so `4 * 2 = 8`. The scrollbar on the left moves both sides to the same page at once.

Three invariants follow, and none of them is checkable by a test:

* **Both inventories are 32 slots**, not 32 and 8. Inverting swaps their roles, so either one has to be
  able to hold the wide side. `PROCESSING_INPUT_LIMIT` and `PROCESSING_OUTPUT_LIMIT` are both 32.
* **Eight is not a constant.** It is `PROCESSING_COMPACT_LIMIT`, derived as grid dimension times pages.
  Changing the number of pages changes both numbers, and hardcoding either breaks the other.
* **The slots a given orientation cannot reach must be empty.** `PatternHelper.clearUnreachable` empties
  them whenever the orientation changes. Without it a recipe could be entered one way round, inverted,
  extended on the other side, and encoded into a pattern the terminal can no longer display.

## Order of operations when a pattern is decoded

`setInverted` empties the unreachable side. Decoding a pattern into the terminal therefore has to set the
orientation **first** and lay the pattern out **second**; the other order makes the pattern erase itself
the moment it is put in the slot. Both paths that decode - `AbstractPartEncoder.onChangeInventory` for the
part and `ContainerPatternEncoder.loadIntoGrid` for the wireless terminal - do it in that order.

Which way round to turn is one rule, `PatternHelper.shouldInvert`, shared by every path that fills the
grid: putting an encoded pattern in the slot, and transferring a recipe from HEI. A recipe with more
outputs than the compact side holds has to be shown inverted, or its outputs would have nowhere to go.

Both paths also send the grid back to its first page, because a pattern fills it from the start and
arriving on the second page shows empty slots. The page is client-side, so the server cannot simply set
it: `AbstractPartEncoder` counts how many times the grid has been filled in one go, the container mirrors
that count into a `@GuiSync` field, and the screen resets the page whenever the count it last saw
changes. The count itself means nothing - only that it moved.

The reset is driven from the server for the HEI path too, out of `PacketJEIRecipe`, rather than from the
transfer handler. The handler runs while JEI's own recipe screen is the open one, so it cannot reach the
terminal's screen to touch the scrollbar.

## The two grid inventories

The terminal keeps the crafting matrix and the processing grid apart, because the processing grid is four
wide and a crafting recipe's shape is three wide - one inventory would scramble the recipe on every tab
switch. They are addressed by name, and the names mean exactly what they say:

* `"crafting"` is **always** the three-by-three matrix. `SlotPatternTerm`, `fixCraftingRecipes` and the
  recipe lookup all build an `InventoryCrafting(3, 3)` straight out of it.
* `"processing"` is the 32-slot grid.

`PacketJEIRecipe` picks between them by the terminal's mode. Anything that asks for `"crafting"` and gets
32 slots will silently take the first nine and lay the recipe out wrong.

Switching to crafting mode copies the processing grid into the matrix, one of each, dropping anything a
crafting recipe cannot hold - a fluid in the processing grid is cleared from both. This mirrors GTNH.

## Machines

A processing pattern is handed to machines as an `InventoryCrafting` four wide and eight tall
(`PatternHelper.newGrid`). Width stays at four so a machine that reads the layout by width sees what it
always did; the second page stacks underneath the first.
