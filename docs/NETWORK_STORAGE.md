# What a network knows it is holding

A terminal, a level emitter and a storage monitor all ask the same question: how much of this is on the
network? Answering it by adding up every mounted cell is too slow to do often, so the answer is kept as a
running total and brought up to date once a tick.

How that total is kept is the whole subject of this file.

## The shape it replaced, and why

`GridStorageCache.onUpdateTick` used to recount everything, every tick, whenever anything on the network was
watching - which an open terminal is. Measured on the ladder in `StorageThroughputTest`, on a network with a
thousand mounted cells holding sixty-three thousand different things:

| | per tick |
|---|---|
| nobody watching | 5 ns |
| one level emitter watching one key | **25 400 000 ns** |

Half of a fifty-millisecond tick, spent by a network that had moved nothing. Splitting that figure by how the
contents were spread showed roughly two fifths going on walking the mounts and three fifths on comparing the
result with the previous tick, key by key - so making the comparison cleverer could only ever have removed
part of it. The list had to stop being rebuilt at all.

Modern AE2 recounts the same way. This fork's own ancestor did not: it pushed changes to listeners as they
happened. What follows is a return to that, and it is recorded as a deliberate departure in
`docs/port/STATUS.md`, api amendment 50.

## Who says what

`IStorageChangeSource` is how a storage says its contents moved. `GridStorageCache` listens to
`NetworkStorage`, and everything else feeds into that:

- **`NetworkStorage`** subscribes to every mount as it is mounted, and passes on what it hears. For a mount
  that says nothing for itself it reports what it moved into or out of it, so a plain `MEStorage` a machine
  only ever reaches through the network still counts correctly with no work at all.
- **`MEInventoryHandler`** - which is what a drive, an ME chest and a storage bus all mount - reports its own
  insertions and extractions. This is the hook that catches a hopper filling an ME Chest: the chest's own
  accessor writes through the same wrapper the network mounted, not past it.
- **The adapters** (`MEMonitorIInventory`, `ItemHandlerAdapter`, `ItemRepositoryAdapter`, and the fluid pair)
  report the difference their periodic rescan finds. That rescan is the only way the box next door can be
  known to have changed, and it was already being computed; now it is handed on instead of thrown away.
- **A nested network** - a storage bus pointed at another network through
  `STORAGE_MONITORABLE_ACCESSOR` - chains, because `NetworkStorage` is itself a change source and the
  wrappers between forward what they are told.

`reportsChanges()` is the part that is easy to get wrong. `DelegatingMEInventory` is a change source
whether or not it has anything to say, so asking `instanceof` alone would let a wrapper over a silent storage
pass for one that speaks, and whoever was above it would fall quiet too. A plain delegate therefore answers
for what is underneath it; `MEInventoryHandler` answers yes, because it reports for whatever it wraps.

## What a tick does now

`refreshCachedStacks` takes one of three paths:

1. **A full recount**, when something has happened that a delta cannot describe: a mount leaving, or a machine
   calling `IStorageService.invalidateCache()`. Unmounting is deliberately not accounted for by subtraction -
   what a storage holds on the way out need not be what it contributed, and a total that has drifted is worse
   than a recount nobody notices.
2. **Applying what was reported**, which is the ordinary case.
3. **Nothing**, when nothing moved.

Watchers hear about changes **once per tick, in one batch**, exactly as they did when this was a recount. A
machine that moves the same stack a hundred times in a tick must not wake a level emitter a hundred times, and
keeping the batching is what makes this change invisible from outside.

## The contract, and how a breach is found

A mount that changes behind the network's back, implements nothing and calls nothing will be shown with a
stale count that never corrects itself. That is a worse failure than slowness, so there is a way to find it:
`auditNetworkStorage` in the config's `general` section, **off by default**. Once a second it counts the
network the slow way, logs every key that disagrees with the running total, names every mount on the network,
and forces a recount so that one silent mount does not leave every terminal wrong until a reload.

It costs exactly what the old per-tick recount cost, which is why it is off. Turn it on while chasing a wrong
count in a terminal - including one caused by a mistake in this code, which looks identical from the outside.

## Measuring it

`src/test/java/appeng/me/storage/StorageThroughputTest` drives all of this directly, with no world: a
`GridStorageCache` takes a null grid and never asks it for anything, `addGlobalStorageProvider` mounts storage
without any nodes, and a JUnit run can build the `AEKeyType` registry by hand and fake FML's side so
`Platform` will load. It prints a table of what an insert, an extract, a tick and a cell's NBT write cost
across a ladder of network sizes, and asserts two things: an idle tick, and a tick after a hundred changes,
both on the largest network on the ladder.

Those two budgets are loose absolute ceilings rather than ratios. An idle tick now costs tens of nanoseconds,
where the timer's own overhead is most of the reading and any ratio built from it swings by a factor of two
between runs; the shape being guarded against was three orders of magnitude larger, so a generous ceiling
still catches it and will not turn red because the machine is busy.
