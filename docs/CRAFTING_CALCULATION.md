# Crafting calculation

How AE2UD works out a crafting plan: what has to be made, how many times each pattern runs, what comes out
of storage, and what is missing. Written after the rewrite landed, describing what is there rather than what
was intended.

The calculation answers one question — **given a request and a snapshot of the network, what does the plan
say** — and hands the answer to a crafting CPU. Executing that plan is a separate machine and not described
here.

## The shape of it

Four layers, each of which can be understood without the one below:

| Layer | Where | Knows about |
| --- | --- | --- |
| The request | `CraftingJob` | threads, ticks, cancellation, the crafting CPU |
| The network, flattened | `NetworkCraftingSource` | patterns, slots, substitutes, fuzzy matching, the world |
| The order to decide things in | `appeng.crafting.solver.SolverGraph` | keys and edges |
| The arithmetic | `appeng.crafting.solver.CraftingSolver` | keys and longs |

The line that matters is between the second and the third. **Nothing in `appeng.crafting.solver` imports
Minecraft.** A pattern arrives there as two flat lists of keys and amounts; by then it is already settled
which substitutes count, which slot the network fills in for itself, and what a craft hands back. That is
what lets the solver be tested with no world, no registries and no items — `SolverTestNetwork` in the test
tree is a network made of nothing, and if anything below the line ever needs Minecraft it stops compiling.

## Why a graph

The planner this replaced walked a tree of *requests*, one unit at a time. Three things came out of that:

* the multi-pattern branch crafted one at a time and copied the whole network ledger on every iteration;
* a pattern that returned a container reported it could only ever run once, so buckets forced the
  one-at-a-time path;
* the walk recursed, so a deep chain of patterns was a `StackOverflowError` rather than a slow answer.

The rewrite makes the node a **thing**, not a request for a thing. One node per key, however many times it is
wanted and however much of it. Everything else follows from that: ordering ten billion of something costs
what ordering one costs, a shared ingredient is settled once instead of once per branch, and the walk is a
loop over a list rather than a recursion.

## Ordering: `SolverGraph`

The graph is over keys. An edge means **"settle this one first"**, and two kinds are drawn:

1. From a key to every ingredient option of every pattern that could make it.
2. From a pattern's **ordering output** to that pattern's *other* outputs.

The second is easy to miss and is the reason byproducts work. Without it a byproduct could be settled before
the pattern that would have given it away for nothing, and be crafted separately.

The ordering output is **the first output the pattern does not also take in** — not simply the first one
written. A pattern's output order is whatever the player typed, and a mould listed above the thing it casts
would otherwise have the two waiting on each other: a cycle that is not there, over a pattern that works.
What a pattern hands straight back is never the thing it is for.

Once built, the graph is put in order:

* **Kahn's algorithm** places every node parents-before-dependencies. If it places all of them, the graph is
  acyclic and each node becomes a component of one — the fast path, and the common one.
* If it does not, **Tarjan's algorithm** runs instead and gathers cycles into components, putting everything
  else back in order around them. Kahn stops at the first cycle and leaves *everything behind it* unplaced,
  not only the cycle, so without this a key sitting below a cycle would lose its patterns and be reported
  missing while its ingredients sat in storage.

Both are written with an explicit stack rather than by recursion, for the same reason the walk is: a chain
ten thousand patterns long is exactly the case this exists for.

A pattern that eats its own output gets an **edge to itself on purpose**. Kahn then cannot place the node,
which is precisely the answer wanted — a cycle of one is still a cycle.

## The arithmetic: `CraftingSolver`

One walk down the order. By the time a key comes up, every consumer of it has already had its say, so the
whole demand for it is known at once. That is what lets storage be handed out on the full picture instead of
to whoever asked first, and it is why the answer does not depend on which branch was walked first — a tree
cannot promise that.

For each key, in order:

1. Take from **surplus** — what earlier steps made and did not use.
2. Take from **stock**, with one exception below.
3. If a **level emitter** promises the key, the rest is promised and the key is done.
4. Otherwise run its **patterns**, in priority order, each taking as much of the remainder as it is allowed.
5. Whatever is still wanted is **missing**.

Every number is `long`, and every multiplication goes through `LongMath.checkedMultiply` — it throws rather
than saturating, and the throw becomes a refusal the player is shown. Saturating here would answer a question
nobody asked.

### What was asked for is never spent

Asking for a hundred when forty are on the shelf makes a hundred more, and always has. So the **root key is
the one thing a plan may not draw from stock**. The single exception is a loop, which grows what you already
have — the seed can only come from the network's own, and there is nowhere else for it to come from.

### Ingredients with a choice

A slot may accept several things: the encoded item, whatever the pattern substitutes, and — for a damageable
tool — any part-used one already in the network. The run is **shared out across the options**, as much as
each can really cover, in the order they are preferred. One worn tool and nine fresh ones make ten crafts.

Two rules sit on top of that:

* **A key nothing else can be fed with goes last.** A slot with a choice should not take the last of what a
  slot without one is going to need.
* **What is really covered is not how many there are.** A tool serves as many crafts as it has left in it,
  and a thing handed back covers the whole run on its own.

Whatever no option can cover falls to the first option something can *make*, or failing that to the encoded
one, where it is reported missing.

### Tools and things handed back

A pattern's slot is classified once, by asking `Platform.getContainerItem` what a craft leaves behind — the
same call the assembler makes, so planning and execution cannot disagree about it:

| What comes back | Read as |
| --- | --- |
| nothing | consumed; one per craft |
| a different item | consumed, and the different item joins the pattern's **outputs** |
| the same item, damage advanced by *N* | a tool: `uses = (maxDamage − damage) / N` |
| the same item, unchanged | a mould: lent, not consumed |

Two things about this are load-bearing:

* **A returned container belongs in the outputs, not beside the ingredient.** Left beside it, a surplus
  credited by a later step could be spent by an earlier one — ten buckets "returned" before they were ever
  filled, while four real ones sat unused.
* **Uses are per *option*, not per ingredient.** The encoded hammer may have sixty crafts left and the one in
  storage six. Counting the slot rather than the option asked for nine more hammers to make ten plates.

A thing that is lent is asked for **once** — enough to have in hand at a time — however the rest of the run is
shared out, and is not credited as production either, or a mould would mint copies of itself. That one rule
covers the catalyst that comes out untouched, the pattern that eats some of its own output, and everything
between.

### Cycles

A cycle is a component of more than one key, or a single key with an edge to itself.

**A pattern that feeds itself** makes some of a key and eats some of the same key per craft. Each craft nets
the difference, so the shortfall is one division: `runs = need / (made − eaten)`. No iteration, nothing that
can fail to come back.

**A cycle through several patterns** — `a` made from `b` while `b` is made from `a` — is the same division
taken round the ring (`SolverLoop`). Multiply the ratios all the way round and the ring either gives back
more than it took or it does not.

The trap is that crafts are whole numbers. Rounding each step up on its own can leave the ring *short* of
what the arithmetic promised: three `a` from one `b`, two `b` from one `a`, asked for a hundred, comes out
ninety-nine. So **a turn of the ring is sized to need no rounding anywhere** — the least common multiple of
the reduced denominators down the ring — and every turn then nets the same amount exactly. The shortfall is
one division by that.

Two rules apply to both kinds:

* **A loop grows what you have and never conjures the first of it.** Something on the ring has to be in
  storage before it can turn. That seed is handed back at the end rather than spent, which is why it shows up
  in a plan as *not* used.
* **A loop that gives back no more than it took is not a source of anything**, however many times it is run.
  Neither is one that gives back less.

Still left alone, and reported rather than half-solved:

* a **branching** cycle, where something on it is made from two other things on it — there is no single ring
  to walk;
* a key inside a cycle whose alternative pattern is *not* on the cycle.

One known non-minimality: stock of a thing part-way round a ring is not folded into the ratio, so a ring may
turn a few more times than strictly needed. Never a plan that cannot run.

### When a pattern runs out

A single forward pass cannot see a shortfall coming. A pattern that ran out of an ingredient has not
disproved the request, only itself — so the solver **caps that pattern at what it could really do and runs
the whole pass again**, letting the next pattern for that key take the rest. That is what the old tree did
one craft at a time.

Each refill lowers a cap that was not lower already, so the loop cannot circle: there are only so many
patterns and each can only be brought down so far. `SolverLimits.maxRefills` (4096) is a backstop, not the
real bound.

### Bytes

A job is charged per thing — and for a fluid the thing is a bucket rather than a millibucket — plus eight per
craft. The same rule the tree used, applied once per key instead of at six places that each had to remember.

## Limits, and what is *not* limited

`SolverLimits.DEFAULT` is 100 000 keys, 1 000 000 edges, 4096 refills.

Every one of those is on the **shape** of the problem. **None is on how much was ordered.** Asking for ten
billion of a thing costs exactly what asking for one costs, and a ceiling on the number of crafts would give
that away for nothing. What stops an absurd order is the byte total the crafting CPU then refuses, which is
the honest place for it.

Hitting a limit — or a number that will not fit in a `long` — throws `SolverTooLargeException`, which reaches
the player as `CraftingSubmitErrorCode.REQUEST_TOO_LARGE` on the confirmation screen, beside every other
reason a job did not start. Nothing about the network is wrong when that appears; the order is simply beyond
what can be written down, and the answer is to ask for less.

## One attempt, not two

A shortfall is **a number the solver reports, not a failure it throws**. The plan that says "this cannot be
made, and here is what is missing" is the same plan as the one that would have made it.

The old tree ran the whole calculation twice to answer that — once in earnest, and again to find out why.
`CraftingCalculationFailure` is gone along with the second run.

## What the plan says

`SolverPlan` is numbers only; nothing in it knows what a pattern is for or where a machine is.

| | |
| --- | --- |
| `getCrafts()` | how many times each pattern runs |
| `getPatternInputs()` | what each pattern draws, by key — the flow along its edges |
| `getUsed()` | taken out of storage |
| `getProduced()` | made, counting a byproduct nothing asked for |
| `getMissing()` | wanted, and neither in storage nor makeable |
| `getEmitted()` | promised by a level emitter |
| `getBytes()`, `isCyclic()` | |

Whether a plan with something missing is a **simulation** or a job that waits for the missing to be brought
is the caller's decision, never the plan's — the two differ in what is done with the plan, not in the plan.
That is what `CraftingMode.IGNORE_MISSING` turns on: `CraftingJob.setJob` promises the missing to the CPU the
same way an emitter's output is promised, while still reporting it as missing.

`CraftingPlanTree` builds the confirmation screen's tree from the plan, with a visited set: a key is set out
in full once, and later mentions carry their own share, are outlined, and say so when hovered.

## The Minecraft side: `NetworkCraftingSource`

One of these belongs to one job, and caches its normalisation, because the same pattern is reached from
several keys and the answer cannot change while a job is being worked out.

What it settles:

* **Substitutes.** A slot knows whether its pattern substitutes; `AEConfig.getEnableCraftingSubstitutes` is
  the separate question of whether this pack wants substitution at all.
* **Fuzzy tool variants.** For a damageable slot the network is searched for part-used ones, which are added
  as further options — after a job, most of what a network holds *is* part-used.
* **Fabricated slots**, and containers handed back, folded into the pattern's outputs.
* **Fake crafting.** A pattern whose result never comes back is something only a player can ask for, and only
  for the job's own output. Anything below would wait for a delivery that is not coming, and a machine that
  ordered it would never be handed anything and would order it again.
* **A root pattern** handed in directly — a pattern registered nowhere that satisfies the request, replacing
  every other way of getting it, an emitter included.

### Patterns are ordered, and equal priorities are kept

`CraftingGridCache` builds the index of what makes a key in the order patterns are offered, then **stable**-
sorts by priority. It must not be a sorted set: a sorted set drops what its comparator calls equal, and
priority defaults to zero, so two ordinary ways of making the same ingot would become one — and which one
survived came out of an identity hash and differed on every launch.

A pattern is dropped only when the **same encoded pattern** has already been offered for that key, so one
recipe sitting in ten interfaces is still one way of making it.

A pattern is registered under **every** one of its outputs, so any of them can be ordered and the craft count
is worked from whichever was asked for. The first output decides only two things: the order the others are
settled in, and what an interface waits for to unlock. When a recipe is sent from JEI, the output the player
had focused is moved to the front, so looking up a byproduct encodes that byproduct first.

## Testing

Two levels, and both are meant to be run.

### Unit tests

`src/test/java/appeng/crafting/solver` — no Minecraft. `CraftingSolverTest` covers the behaviour;
`CraftingSolverScaleTest` writes down the claims that are the whole reason for the rewrite, so a change that
quietly gives one up fails the build:

* the amount ordered does not change the cost (ten billion, same plan, same time);
* a chain 10 000 patterns deep does not run out of stack;
* twenty-four levels of shared branches — 2²⁴ nodes to a tree — cost their number of *nodes*;
* a loop and a ring at ten billion each divide out at once.

The time budgets there are deliberately loose. They are not there to catch something ten per cent slower;
they are there to catch a return to the old shape, which is thousands of times slower.

### The in-game rig

A **Crafting Test Rig** block, and `/ae2 CraftingTest` (spelled exactly so — the dispatcher matches the
name case-sensitively). Sixteen scenarios build their own patterns and
storage against the rig standing next to you, plan each one, and diff the answers against a recorded
baseline (`run/config/AppliedEnergistics2/CraftingTestBaseline.json`).

```
/ae2 CraftingTest                  run them all, report differences
/ae2 CraftingTest baseline         record this run as the answers to compare against
/ae2 CraftingTest spill recursive  run only those
```

Recording is deliberately a separate word rather than something that happens when no baseline exists: **a
baseline is an agreement that an answer is right**, and a new scenario reporting "no baseline recorded" is
the rig staying silent, not the rig approving. Read the recorded JSON before believing it.

Everything in an outcome is read through the job's public plan, so a baseline stays comparable when what
produced it is replaced — which is the one comparison the rig exists to make. One scenario hands its plan to
a real crafting CPU and watches until it finishes, so execution is covered too.

**Timings are reported and never diffed.** A machine that is a little slower today has not changed any
answer, and a baseline that failed on that would be red every time somebody opened a browser. Each scenario
is planned up to five times, the first thrown away and the median of the rest printed — fewer when a slow
scenario runs out of its budget, which is why the report says how many it is the middle of. Even so, do not
compare a number against one from another session: different client launches have different JIT state.

## Measured

Old tree → new solver, same scenarios, same session:

| Scenario | Before | After | |
| --- | --- | --- | --- |
| `batch_large` (20 000 of a thing) | 34 255 µs | 225 µs | ×152 |
| `spill_short` | | | ×7.6 |
| `spill` | | | ×4.9 |
| `deep` (40 patterns) | 412 µs | 1311 µs | **×0.3** |
| `wide` (201 patterns) | 1328 µs | 2568 µs | **×0.5** |

Small acyclic plans got two to three times **slower**, consistently. Building the graph costs allocations the
tree never paid, and forty nodes is not enough work to earn them back. It is one to three milliseconds on a
background thread and invisible in play, so it has been left: the fix, if small plans ever start to matter,
is a structural-graph cache on `CraftingGridCache` keyed by a pattern revision.

What the tree could not do at all: a 10 000-deep chain (stack overflow → 64 ms), twenty-four levels of shared
branches (2²⁴ nodes → 0 ms), ten billion of anything (→ 0 ms).

## Provenance

Written from scratch. Three existing projects were read to understand what already works and what the shape
of the problem is — AE2 Quick Calculation, PatternTest and AE2CraftingPlanImprove — and **no code was taken
from any of them**; two are the same author's and one is GPLv3, which this fork's LGPL cannot carry. The
approaches were collected, combined, and rebuilt.

One inherited defect was fixed on the way and is not part of the rewrite: the priority comparator and sorted
set that dropped equal-priority patterns, which GTNewHorizons' fork still has.
