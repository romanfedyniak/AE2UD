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

### Labels

A step is the player's, and so is the font: `10000` is five characters of whatever width the pack in use
draws them at, in a button as narrow as 22 pixels. So `GuiStepButton` measures its own label rather than
trusting it to fit. If the step written out in full is too wide it is shortened the way a slot amount is
(`10K`), and the full reading moves to the button's tooltip; if even that is too wide - a font twice the
vanilla width, say - what is left over is taken in scale. The button therefore carries its step in a field.
Nothing reads a step back out of a label, which a label with a `×` on it or a `K` in it could not answer
anyway.

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
