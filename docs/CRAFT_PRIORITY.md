# Craft priority

A crafting job carries a priority: a whole number, negative allowed, 0 by default. It answers one question
and no other - **when a machine comes free, which of the jobs waiting for it gets it.**

## What it does not do

* It does not slow anything down. A job at -5 that competes with nothing runs exactly as fast as one at 0;
  a low priority means "let the others past", not "take longer".
* It does not choose the processor a new job lands on. That is still the heuristic in
  `CraftingGridCache.submitJob` - smallest processor that fits, or the largest when the caller asks for
  power.
* It does not take back work already handed out. A machine that has begun a pattern finishes it; the
  priority decides who is offered the *next* free machine.

## Where the order is applied

Two loops in `CraftingGridCache`, and both walk the same list:

* `onUpdateTick` - each processor in turn is given its slice of the tick, and the first one to reach a free
  machine claims it.
* `insert` - each processor in turn is offered an item arriving in the network, and takes it if it is
  waiting for it.

They have to agree. Serving one job first at the machine and another first with what comes back from that
machine would be two orders fighting each other.

The list is `orderedCPUs()`: the processors sorted by priority, highest first, rebuilt only when a
processor appears, disappears or moves. Rebuilding **replaces** the list rather than sorting it in place,
because a job that finishes inside the tick loop resets its priority to 0 and the `storeItems` that follows
inserts into the network - which would otherwise re-sort the list being iterated.

Equal priorities take turns: `rotateEqualPriorities` moves each run of equal numbers along by one every
tick. Without it, two background jobs on one furnace would leave the same one of them waiting forever.

## Where the number lives

On the `CraftingCPUCluster`, for as long as the job runs. It is saved with the rest of the job, and
`setCraftPriority(0)` is called from both `completeJob()` and `cancel()`, so a processor never carries a
number into the next job.

That also means priority and processor are the same thing while a job is running: a job holds a whole
processor, so "this job's priority" and "this processor's priority right now" cannot disagree.

## Where it is typed

`GuiCraftPriority` is the amount screen with `Integer.MIN_VALUE` to `Integer.MAX_VALUE` for a range. It is
**summoned over** the screen that asked for it and puts that one back on leaving, the way `GuiAmountSteps`
is - never opened in its place. Switching containers would throw away a crafting plan that cost a whole
calculation to work out, or the list of what a processor is holding. The screen never says whose priority
it is: `PacketCraftPriority` goes to `player.openContainer`, and whatever is still open there answers it as
an `ICraftPriorityTarget`.

One trap comes with being drawn over another screen. `GuiContainer.initGui` points `player.openContainer`
at the new screen's container, and a synced field is only ever sent when it moves - so every `@GuiSync`
update belonging to the screen underneath would be dropped on its window id while the overlay is up, and
never sent again. `GuiCraftPriority.initGui` therefore points `openContainer` back at the container it was
opened over. Nothing is lost by that: this screen has no slot anyone can click.

Six buttons open it, and all six are the same `GuiCraftPriorityButton` - the icon every screen that
configures something wears, with the number in its tooltip rather than on its face. Four of them are about
a job:

| screen | button | reaches |
| --- | --- | --- |
| Crafting Plan | beside Start | `ContainerCraftConfirm.craftPriority`, used by the next Start |
| Crafting Tree | beside Start | its own container's copy of that field |
| Crafting CPU | beside Suspend | the cluster, at once |
| Crafting Status | the same button, inherited | the cluster the table points at |

Switching between the plan and the tree builds a **new** container, so the number is carried the way the
plan itself is - `handOverTo`, beside the job and the request it was planned from. Cancel steps back to the
amount screen, which parks it in `ContainerCraftAmount.craftPriority` without showing it and hands it to
whatever plan is worked out next.

The way out is a tab in the top right, as on every other amount screen. It is added by `GuiCraftPriority`
rather than by the base class: the base class builds that tab from the container's host and a `GuiBridge`,
and this screen goes back to a *screen*, which is not something a `GuiBridge` can name.

Changing a running job's priority is not gated by `allowsConfiguration()`, which is what keeps the CPU
selection mode off the status screen. A priority belongs to the job rather than to the processor, and
Cancel sits beside it on both screens already doing strictly more to that job.

## Machines that order crafts

Two carry a crafting card and order jobs of their own - the ME Interface (`DualityInterface`) and the
Export Bus (`PartExportBus`). Both implement `ICraftPriorityTarget`, keep the number in their own NBT under
`craftPriority`, and hand it to `MultiCraftingTracker`, which reads it at each submit rather than holding a
copy. Their button appears only while the crafting card is in, under the button that card already adds.

The interface's container is opened on its **host** rather than on the duality (`ContainerInterface` passes
`getInterfaceDuality().getHost()` to `super`), so `IInterfaceHost` answers `ICraftPriorityTarget` with two
default methods that delegate to the duality. Implementing it on `DualityInterface` alone compiles and does
nothing: the container's `upgradeable` is the part or the tile, and neither would have answered.

The interface's older **Priority** tab is a different number: that one is its storage priority, which says
when the network asks it for space. The two never meet.
