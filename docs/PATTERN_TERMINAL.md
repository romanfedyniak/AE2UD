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

Switching tabs copies the grid over, both ways, so a recipe typed in one mode does not have to be typed
again in the other. The rule is that a copy never overwrites what the side it came from could not hold:

* Into the matrix goes one of each item, and a fluid stays in the processing grid untouched, because the
  matrix has no way to show it.
* Back into the processing grid the matrix speaks for its nine squares alone. An empty square clears an
  item, which the player really did take out, but leaves a fluid, which was never there to take out. A
  square holding the same item on both sides is left alone, count included, so a count typed in processing
  mode survives a trip through the matrix.

Outputs are not carried across in either direction - a crafting recipe's result is computed from the
matrix, and the processing side is a grid the player fills. A pattern switched over to processing mode
therefore has ingredients and no output, and Encode does nothing until one is typed in.

GTNH keeps a single store for both modes and so needs no copy at all; the matrix here is a real inventory
of its own, because a crafting recipe's shape is part of the recipe and a processing recipe's is not.

The copy hangs off `switchCraftingMode`, never off `setCraftingMode`. The plain setter also runs when the
container is only catching up with the mode its terminal was already in - it starts in crafting mode, so
opening one left in processing mode goes through it - and a copy there would mirror an empty matrix over a
grid the player had saved.

That the matrix cannot hold a fluid is also why `getPhantomTargets` asks the mode before deciding what a
dragged HEI ingredient means. Over the processing grid a filled container stands for its contents, the
same as clicking it by hand, unless the drag ended on the right button. Over the matrix it stands for
itself, and a fluid dragged straight from the ingredient list is refused - no target is offered, so the
slot never lights up.

## Machines

A processing pattern is handed to machines as an `InventoryCrafting` four wide and eight tall
(`PatternHelper.newGrid`). Width stays at four so a machine that reads the layout by width sees what it
always did; the second page stacks underneath the first.


## Sending a pattern into the network

The button beside the encoded pattern slot files that pattern in something that holds patterns, instead of
the player carrying it there. `appeng.helpers.PatternUpload` is the whole policy; the packet and the two
screens only ask it things.

**A crafting pattern is filed without asking.** In this version the molecular assembler is an
`ICraftingMachine`, not a crafting provider - its one pattern slot locks it to a recipe and does not make
that recipe craftable. The only route from a network to an assembler is an ME Interface standing next to it,
which is `DualityInterface.canAcceptPattern`: for a craftable pattern it wants a neighbour whose
`acceptsPlans()` is true, and, when the network would fabricate containers for that pattern, one whose
`acceptsFabricatedContainers()` is true as well. Those are the same questions `pushPattern` asks a face when
the time comes, asked of every face at once and in advance.

Of the containers that pass, `PatternContainers.best` takes the one with the most free usable slots, and
breaks a tie by distance to the player. Never the first one out of the grid: `IGrid.getMachines` hands them
back in the order they joined, which changes across a world reload and means nothing to a player.

**A processing pattern always opens the list.** Any interface will run one, so there is nothing to work out;
which machine it was written for is only in the player's head. Shift-clicking opens the list for a crafting
pattern too, and so does a crafting pattern with nowhere to go - the list is where the player finds out
*why*, rather than a chat line saying nothing worked.

**A P2P interface tunnel is looked through, both times.** `acceptsPlans()` answers for the tunnel alone -
it is the question a push asks a moment before it tries, and a tunnel with nothing but furnaces behind it
still says yes, after which the push fails and nothing is lost. Filing a pattern against that answer is not
harmless: the pattern sits there being offered to the network and never runs. So `acceptingFace` asks the
tunnel `hasPlanTakingOutput()` as well, which walks its outputs, and through further tunnels.

**A row is named after the machine that would run this pattern**, not after whichever neighbour comes
first. An interface that works on every side has a choice of names, and `DualityInterface.identifyFor` picks
the accepting face, so a list of targets says what it is offering rather than what happens to be beside it.
Where that face is a tunnel the name comes from `getRemoteMachineIdentity()`, exactly as the plain naming
does - otherwise three tunnels leading to three different assembler banks all read "P2P Tunnel". An
interface the player has named keeps its own name.

**A row that `canAccept` refuses is dimmed and cannot be clicked**, and the same question is asked again on
the server when a row is clicked, because the list is a moment old by then. Filing a crafting pattern into an
interface with no assembler beside it does not fail loudly - it fails hours later, at a crafting terminal,
as a recipe the network says it cannot make - so it is not something to leave to the player's judgement.
Those rows stay on the list rather than disappearing from it, so the screen can say why.

The one thing enforced beyond that is a duplicate within a single container, since the second copy only eats
a slot. The same pattern in a second container is left alone: that is how crafting is spread over two sets of
machines.

**Where the pattern is read from.** `IPatternUploadHost` exists because the list is a container of its own,
so by the time it is open the terminal's container is closed. A terminal part answers out of its own
inventory; a wireless terminal keeps its pattern slots in the container, which writes them into the item on
every change, so the host reads them back out of the item's per-mode NBT.

The feature switch is `PatternUpload`, under `Features.CraftingFeatures`.
