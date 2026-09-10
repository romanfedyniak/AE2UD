# Monitors

An ME Storage Monitor shows how much of one thing the network holds. An ME Conversion Monitor does the same
and also lets a player put that thing in or take it out by hand. Both are configured the same way, and both
can be asked to show how fast that number is moving.

## The window

Sneak and right-click a monitor with an empty hand and it opens a window:

- **the slot** in the middle is what the monitor watches. Put an item in it from the inventory below, or drag
  one out of HEI. A fluid works there too, and so does any other kind of thing a network can hold - the slot
  holds a key rather than an item stack, so nothing is item-only about it.
- **the padlock** beside the slot is the same lock as before. A locked monitor keeps the key it has: clicking
  it in the world no longer changes it, and neither does the slot in this window. On a Conversion Monitor,
  locking is also what turns the clicks that move items on.
- **the throughput buttons** are described below.

The old gestures in the world still work. Right-click a monitor with something in hand and it watches that;
right-click a monitor holding a bucket that is already what it watches and it watches the fluid inside;
right-click with an empty hand and it forgets; right-click with a wrench and the display turns.

Sneak-clicking used to toggle the lock, and that is the one thing that changed. There was one gesture per
setting and no gesture left over, which is why the window exists.

## Throughput

The two buttons under the slot say how much of the watched key is moving.

The first is the span, and it is also the switch: `Off`, `/t`, `/s`, `/m`, `/h`. The second is which number
to show: `Net`, `In`, `Out`. While the first says `Off` the monitor is an ordinary monitor and the second is
greyed out.

With the span set, a line appears under the amount on the monitor's face:

```
   [ iron ingot ]
       12.4K
      +1.2K/s
```

- **Net** is what the network gained or lost - `+1.2K/s`, `-340/s`, `0/s`. This is the number to trust: it
  cannot be inflated by anything that takes a stack out and puts it straight back.
- **In** (`▲`) and **Out** (`▼`) are what actually passed through, in each direction. They are the only way to
  see a line that produces and consumes at the same rate: net calls that nothing at all, while in and out both
  read the real figure.

Green means the network is gaining, red that it is losing, grey that nothing is moving - grey rather than the
black the stock above is drawn in, so a still line is told apart at a glance.

The line runs across the lower corners of the screen, where a locked monitor draws two of its four lock marks.
While it is shown those two are left off - a separate model, `*_locked_metering_on`, picked only by a locked
monitor with a channel and a line to draw - and the upper two still say the monitor is locked.

Look at a monitor with Waila installed and all three numbers are there at once, which is the only place they
can be read against each other without clicking through the modes.

### What the span really means

The span is both the unit the rate is quoted in and how much history it is averaged over, because the two go
together: an hourly figure is read by someone watching a slow process and should be steady, while a per-tick
figure is read by someone watching a machine start and stop and is allowed to jump.

| span | averaged over |
|---|---|
| `/t`, `/s` | the last 5 seconds |
| `/m`, `/h` | the last minute |

Nothing waits for its own span to elapse. `/h` measures a minute and multiplies by sixty; otherwise a monitor
placed a moment ago would read zero for an hour, and "nothing is moving" would be indistinguishable from "I
have not measured yet".

Measurements are kept in one-second buckets, sixty of them. The bucket being filled right now is left out of
the average - it holds part of a second, and counting a partial second against whole ones drags every reading
down - except before any bucket has finished, so that a meter just switched on says something.

## What it costs the server

Nothing at all while no monitor is metering.

A network already sees every change to its contents one at a time and adds them up (see
`docs/NETWORK_STORAGE.md`); a total is all a watcher is ever told, and a key that arrives and leaves inside
one tick changes no total. Metering keeps the two directions apart, but only for the keys some monitor asked
about: one lookup in a small map per change, guarded by an `isEmpty` test that costs nothing when nobody is
metering. It is deliberately not part of watching - a terminal watches every key in the network, and a meter
inherited from that would meter the lot.

A metering monitor ticks once every 10-20 ticks to roll its buckets, and sends its two rates to nearby
players only when the number a player would read actually changes. A rate moves every tick by an amount
nobody can see, and every send is a block update to everyone in range.

`monitorThroughput` in the config's `general` section turns the whole thing off for a server. Off, the span
button has no states but `Off`.

## Where this came from

The idea is GregTech: New Horizons' Throughput Monitor, and Advanced AE has one too. Both measure the net
change of the network's total and both are separate parts you craft. This is a mode on the monitors that
already exist, off until switched on, and it measures what passes through as well as what is left over -
which the delta-reporting the storage rewrite put in makes nearly free, and which neither of them can do.
