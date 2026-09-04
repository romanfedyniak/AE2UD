# Reaching the network without opening a terminal

A wireless terminal in a pocket is a network in a pocket, and a few things are worth asking of it without
stopping to open its screen. This file covers those, the rules they share, and the decisions behind them
that the code cannot state for itself.

At the time of writing there is one: the vanilla pick block key.

## The shared half

Every one of these walks the player's main inventory and then their bauble slots, looking for the wireless
terminal item, and asks each one it finds until one answers. `appeng.helpers.WirelessTerminalAccess` is that
walk. It hands the caller a `WirelessTerminalGuiObject` — the same object a terminal screen is opened on, so
the encryption key, the power draw and the range check are the ones the screen already uses and there is no
second set of rules to keep in step.

Every terminal is tried rather than the first one found. One of several may be the only one still charged,
or the only one in range.

**A terminal is not asked twice.** `WirelessTerminalAccess.Action` returns true once the work is done, and
nothing after it is looked at. A stack is never gathered out of two networks: a player who asks for iron and
gets half of it from the base behind them and half from one two thousand blocks away has no way to see that
happened, and no terminal screen behaves that way either.

### What the player is told

Reasons pile up and exactly one is said, the one that came closest to working — a terminal that is linked,
charged and merely out of range outranks a spare in a backpack that was never linked at all. The order lives
in `WirelessTerminalAccess.COMPLAINTS`, and `PacketTerminalUse` ranks the reasons a terminal would not open
through the same list.

Two silences matter more than the message:

- **No terminal at all is silent.** Otherwise every middle click by a player who has never crafted one would
  put a line in the chat.
- **A terminal that worked and simply had nothing is silent.** Middle-clicking grass is not an error, and it
  is the common case. Once any terminal is reached the complaints are dropped, whatever it then answered.

### What is not checked

`SecurityPermissions.EXTRACT` is not asked here, because it is not asked anywhere in the storage path — see
the note in `docs/port/STATUS.md`. Nor is there any rate limit, on either side: no click in a terminal has
one either, including the shift click that moves thousands of items through `PacketInventoryAction`, and one
guarded door in a wall of open ones is an exception rather than a defence. If this class of action ever needs
a limit, it belongs beside the other limits in `AEConfig` and covers all of them at once.

## Pick block

`AEFeature.NetworkPickBlock`, on by default. Each terminal also carries `Settings.PICK_BLOCK`, a button in
the terminal's own left-hand column, so a working terminal on the belt can answer the key while a spare in a
chest does not. The setting rides on the item because the server reads it off the very stack it is about to
use.

The client half is `appeng.client.NetworkPickBlock`, reached from the tail of `Minecraft.middleClickMouse`
through `appeng.mixin.minecraft.MixinMinecraft`. Why a mixin and not an event is in `docs/MIXINS.md`.

**The whole decision is made on the client**, because that is the side that knows what the crosshair is on
and which slot the hand is in. The server is sent a key, an amount and a hotbar slot, and re-checks that the
slot can still hold the request before it takes anything out — a hotbar that filled up in between costs the
network nothing.

The rules, in the order they are asked:

1. **Creative is skipped.** Vanilla already hands over whatever is looked at.
2. **A color applicator has first claim.** The same middle click reads a color off a block through the
   `ForgeHooks.onPickBlock` patch in `appeng.core.transformer`, which has already run by the time the tail
   is reached. `PickBlockPatch.isColorApplicatorPickBlock` asks whether that happened without doing it a
   second time — the method that acts sends a packet, so it cannot be the one that answers the question.
3. **Holding a part of a stack of it fills the hand.** The alternative is starting a second stack somewhere
   else while the hand holds a partial one.
4. **Otherwise, if it is anywhere in the player's inventory, nothing happens.** Vanilla has just moved it to
   hand, which is the whole of what the key does in survival. Without this rule every middle click on the
   stone underfoot would order a stack out of storage.
5. **An empty hand takes it; a full one looks for an empty hotbar slot.** If the hotbar is full, nothing
   happens.

Blocks and entities both, because vanilla answers the key for both: a block through `getPickBlock`, an item
frame or a minecart through `getPickedResult`. Stopping at blocks would leave the same click silent on a
frame for no reason a player could see.

Sneak asks for one, otherwise a full stack — a full stack is what the key hands over out of the player's own
inventory, so it is what it should hand over out of the network.
