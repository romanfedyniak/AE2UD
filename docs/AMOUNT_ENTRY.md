# Typing an amount

Several screens exist to type one number: how much to craft, how much a fake slot stands for, what a level
emitter watches for, what priority a machine has. They all draw the same thing - a field with four buttons
above it and four below - and this document is about the parts they share.

## The step buttons

`GuiStepButtons` owns the two rows. A screen hands it the window's left edge and the two rows' heights,

```java
this.steps.addTo(this.buttonList, this.guiLeft, this.guiTop + 26, this.guiTop + 75);
```

calls `update()` once a frame, and asks it for the new amount when one of its buttons is pressed:

```java
if (this.steps.isStep(btn)) {
    this.setAmount(this.steps.apply(btn, current, min, max, scale));
}
```

The columns are always at the same four offsets and the same four widths, because every screen's background
texture is drawn with them in mind.

### Groups

A modifier key names a **group**: four steps and the one operation all four perform. The groups are
consulted in a fixed order - Ctrl, then Alt, then Shift, then none - so a modifier held on top of another
never quietly changes what a press does. The labels follow whatever is held, which is what `update()` is
for: what a button will do is written on it before it is pressed.

| Group | Operation | Steps |
| --- | --- | --- |
| none | `+` / `-` | 1, 10, 100, 1000 |
| Shift | `+` / `-` | 1, 16, 32, 64 |
| Ctrl | `×` / `÷` | 2, 3, 5, 10 |
| Alt | `×` / `÷` | 10, 100, 1000, 10000 |

The Shift set is the one modern AE2 offers, where holding Shift is the only choice there is.

### The arithmetic

`AmountSteps.apply` does the sum, and every screen goes through it so that all of them agree:

- Addition and multiplication saturate rather than wrap: a press that would run off the end of a `long`
  stops at it.
- Division truncates towards zero, so a negative amount loses as much of itself as its positive twin would.
- The result is then held between the minimum and maximum the screen itself allows.
- Multiplying zero leaves zero. Nothing pretends otherwise: a screen whose amount can be zero has a `+`
  button beside the `×` one.

A step is written in whatever unit the field is being read in - a `+10` on a screen reading buckets adds ten
buckets - so screens pass their unit scale in. A factor is a factor in any unit and is never scaled.

### Reading the field

`AmountEntry.parse` takes two roads, and which one it takes depends on what was typed:

* **A number typed as itself** - digits, and at most one decimal point - is read as itself, straight into a
  `BigDecimal`. No `double` is involved at any point.
* **Anything else** is handed to `MathExpressionParser`, which answers in a `double`.

The split exists because a `double` holds whole numbers exactly only up to 2^53, and an amount is a `long`
everywhere else in the mod. Nine quadrillion and one is the first number it cannot hold, and the field is long
enough to type it: read the old way it came back one short. An expression is a different matter - someone
typing `64*64*64` is not counting to the last unit - so that road is unchanged.

The scale is applied before rounding, not after: rounding first would turn one and a half buckets into two of
them rather than into 1500. The result is clamped to `Long.MAX_VALUE`, so a number too big for an amount is
held at the top rather than wrapping.

The field holds 24 characters. That is the largest amount there is - nineteen digits - in any unit it may be
read in, with room for a decimal point and the leading `=`.

### Labels

A step is the player's, and so is the font: `10000` is five characters of whatever width the pack in use
draws them at, in a button as narrow as 22 pixels. So `GuiStepButton` measures its own label rather than
trusting it to fit. If the step written out in full is too wide it is shortened the way a slot amount is
(`10K`), and the full reading moves to the button's tooltip; if even that is too wide - a font twice the
vanilla width, say - what is left over is taken in scale. The button therefore carries its step in a field.
Nothing reads a step back out of a label, which a label with a `×` on it or a `K` in it could not answer
anyway.

## What the amount is typed into

`GuiSetAmount` does not know what it is setting. Its container holds an `IAmountTarget`, built where the
screen was opened and asked four things: an icon for the display slot, the amount to start on, and the two
ends of what it will accept. When the player confirms, `PacketSetAmount` holds the number between those
ends and hands it to the target - and nothing else. Where the number lands, and when the player is sent
back, are the target's own business.

They have to be, because the order differs and each order has a reason:

- `SlotAmountTarget` is a fake slot of the screen the amount was opened from. That screen's container does
  not exist while the amount screen is open - the player's container was replaced by it - so the slot is
  named by its place in the container that comes back. It therefore **returns the player first** and writes
  into the new container second.
- `InventoryAmountTarget` is a slot of an inventory that belongs to something else: the interface
  configuration terminal edits interfaces elsewhere on the network. That inventory is the interface's own
  and outlives both screens, so it **writes first**. Going back to that terminal builds a fresh container,
  which hands the same interfaces different ids, and by then there would be nothing left to look the slot
  up by.

### The three targets

| Target | Sets | Opened by |
| --- | --- | --- |
| `SlotAmountTarget` | one fake slot of the screen it came from | a middle click on that slot |
| `InventoryAmountTarget` | one slot of an interface elsewhere on the network | a middle click in the interface configuration terminal |
| `PriorityAmountTarget` | a machine's priority | the priority tab on that machine's screen |
| `LevelAmountTarget` | a level emitter's threshold | a middle click on the emitter's filter slot |

A middle click asks the container what that slot's amount means, through `AEBaseContainer.amountTargetFor`.
The answer is normally the slot itself; a level emitter's filter slot answers with the threshold instead,
which is why the emitter needs no field, no buttons and no button of its own to open one - its filter wears
the threshold the way any slot wears an amount, and is middle-clicked the way any configured amount is.

A priority has no slot to click, so it is reached the way a screen is reached: `GUI_PRIORITY` names
`ContainerSetPriority`, whose only job is to build the target for the machine it was opened on, and the
amount screen is what comes up. There is no priority screen any more: a number is typed in one place in
this mod.

Both accept what a fake slot does not. A priority is negative as readily as positive, so the field takes a
leading `-` and the range line - which reads "1 - 512" over a slot - says nothing at all where the low end
is `Integer.MIN_VALUE`. A threshold starts at zero, and zero is a real answer rather than an empty field,
which is why the container syncs a `ready` flag: the screen cannot tell an answer of zero from a value
still on its way by looking at the value.

The way back is a separate question from where the amount goes, and the screen answers it for itself: the
host it was opened on top of is asked, through `ISubMenuHost`, for the screen to return to and the item to
draw on the tab. Every machine with a screen of its own answers - so does a terminal, and so does a
terminal held in the hand - which is why the tab is there whether the amount was opened from a pattern
terminal, a storage bus or an interface.

**The tab is drawn either way.** A host that is not an `ISubMenuHost`, or that hands back no item to draw -
an ME chest, or an addon's own terminal - used to leave the corner empty, and a screen with no visible way
out reads as a dead end even though escape closes it. It now gets a plain arrow instead of the host's item,
labelled simply "Back", and pressing it closes the screen: that is the honest answer when nothing will say
where the player came from, and it is never the wrong screen to open.

## Changing them in game

`GuiAmountSteps` is the screen behind the tab in the top right of the amount window, one modifier to a row:
a label, a button that swaps the row between `+/-` and multiplying, and its four steps. `Defaults` puts the
table back to what is written above.

It is a client screen and nothing more. It hangs on `ContainerAmountSteps`, which holds no slots and syncs
nothing, and it is summoned over whichever amount screen sent for it with `displayGuiScreen`, the way
`GuiPatternView` is summoned over a terminal. So Escape and the inventory key have to be caught: this screen
never opened a window on the server, and letting them through would close the one underneath.

Writing happens in `onGuiClosed`, which every way out of the screen passes through - the tab, Escape, the
inventory key - so there is no such thing as a change made but not kept, and nothing to warn about on the
way out. A field that does not read as a whole number of one or more is shown in red and left out of the
write: that button keeps the step it had.

Coming back re-runs the amount screen's `initGui`, which builds a new text field. `GuiCraftAmount` therefore
remembers what stood in the old one and puts it back, or the amount would be lost to a visit to the
settings.

## Where the steps are kept

One set serves every screen, in the `Client` category of the mod's config:

```
amountStep1..4          amountStepMode
amountStepShift1..4     amountStepShiftMode
amountStepCtrl1..4      amountStepCtrlMode
amountStepAlt1..4       amountStepAltMode
```

A step is any whole number of 1 or upwards; the mode is `ADD` or `MULTIPLY`. There is no cap on a step,
because the only thing a cap ever protected was the width of the button, and the button now measures
itself.

Before this there were three separate sets - `craftAmtButton*`, `priorityAmtButton*` and `levelAmtButton*` -
one per screen, each capped at `10^n - 1` so that the first button could never exceed 9. They are not read
any more, and are taken out of the config file when it is loaded.
