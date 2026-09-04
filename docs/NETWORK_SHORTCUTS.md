# Reaching the network without opening a terminal

A wireless terminal in a pocket is a network in a pocket, and a few things are worth asking of it without
stopping to open its screen. This file covers those, the rules they share, and the decisions behind them
that the code cannot state for itself.

There are three: the vanilla pick block key, and two keys over an ingredient in JEI's list.

## The shared half

With no terminal screen open, every one of these walks the player's main inventory and then their bauble
slots, looking for the wireless terminal item, and asks each one it finds until one answers.
`appeng.helpers.WirelessTerminalAccess` is that walk. It hands the caller a `WirelessTerminalGuiObject` — the same object a terminal screen is opened on, so
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

**No terminal at all is silent**, always. Otherwise every middle click by a player who has never crafted one
would put a line in the chat.

**A terminal that worked and simply had nothing** is the case where the actions differ, and it is the caller
that decides, by handing `run` a message or not:

- **Pick block says nothing.** Middle-clicking grass is not an error, it is the common case, and the key
  fires wherever the crosshair happens to be.
- **Taking an ingredient says so.** That gesture named one thing on purpose; silence there reads as a broken
  feature rather than an empty network. Being unable to hold the result is worth saying for the same reason.

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

## Taking and ordering an ingredient in JEI

`AEFeature.JeiRetrieve` and `AEFeature.JeiCraftRequest`, both on by default, and both dead weight without
JEI — which is why the keys are not even registered when it is absent, rather than sitting in the controls
screen doing nothing. `appeng.integration.modules.jei.JeiIngredientActions` is the client half, registered
from `JEIModule.init()`, which only runs on a client with JEI present.

- **Ctrl + middle click takes it.** A full stack, or one while shift is also held.
- **Alt + middle click orders it**, opening the amount to craft.

Both are ordinary `ActionKey` bindings and can be rebound to anything, keyboard included; the defaults match
what a player arriving from either of the two 1.12 mods that already do this will have in their fingers.
`ActionKey` grew a modifier and a "is this worth offering" question for them.

**Which ingredient** is public API - `getIngredientUnderMouse()`, on the ingredient list and on the bookmark
overlay alike - so nothing has to be prised out of JEI to know what the cursor is over. A bookmark answers
with its own wrapper around the ingredient rather than the ingredient, since it carries an amount and the
group it sits in; unwrap it or the shortcuts silently do nothing over the bookmark list, which is where they
are arguably most wanted. A collapsed group is deliberately left unanswered: it is not one ingredient.

**Getting the click, however, needs a mixin.** JEI takes `GuiScreenEvent.MouseInputEvent.Pre` at
`EventPriority.HIGHEST` *and* with `receiveCanceled = true`, which means no listener can be ahead of it and
cancelling the event does not stop it either. It is not idle with the click: a screen that registered a ghost
ingredient handler - every terminal in this mod - starts a ghost drag on any button, and a screen without one
starts a drag of JEI's own whenever control is held, which is what made the control chord appear to do
nothing at first. `appeng.mixin.hei.MixinInputHandler` therefore takes the head of `InputHandler`'s own
`handleMouseClick`, which is the one method every click in an open screen passes through, and answers true
the way JEI's own handlers say a click was theirs. A binding moved onto the keyboard needs none of this: JEI
reads the keyboard on `KeyboardInputEvent.Post`, so the ordinary listener is already ahead of it.

**JEI's cheat mode is not involved** and could not have been: a server turns it off for anyone who is not in
creative, which is precisely the player this is for.

### Which network answers

If a `ContainerMEMonitorable` is open — any ME terminal, wired or wireless — that terminal's network does
the work, through the container's own power source, storage and action source. Otherwise the carried
terminals are walked as above. The rules are the same either way; only where the network came from differs.

### Retrieval

Items only. A fluid has nowhere to go: there is no bucket to put it in, and the wrapper that lets a fluid sit
in an item slot exists for pattern slots and must never reach a player's inventory.

The amount asked for is capped by what the player's own inventory can actually take, counted before anything
is extracted, so nothing is dropped at their feet. Anything that still fails to fit — the count is a guess
about a live inventory — is put back into the network rather than spilled.

### Ordering

Any key type, fluids included: asking for a fluid to be made is an ordinary request, and
`ContainerCraftAmount` has always taken a bare `AEKey`.

The network is asked whether it can make the thing **before** any screen opens. Walking a player through the
amount screen and the confirmation only to say "no pattern" is the common outcome of trying this on a JEI
list, where most entries are things no network can make.

Opening goes through the path `InventoryAction.AUTO_CRAFT` already uses, so the way back is whatever
`ISubMenuHost` says it is — the terminal the job was ordered from. There is deliberately no attempt to
remember and restore an unrelated screen the player happened to have open: the return tab would then look
the same and lead somewhere different every time.

### The hint in the tooltip

Both keys are named in the tooltip of the ingredient they would act on, because a binding nothing mentions is
one nobody finds. Only where it means something, though: the lines are drawn only when the player has a
terminal on them or an ME terminal open, and the "take" line only over something that can actually be taken,
so a fluid is offered the order and nothing else.

This is a second mixin, `appeng.mixin.hei.MixinIngredientRenderer`, and the reason is the fluid.
`ItemTooltipEvent` is fired by `ItemStack.getTooltip`, so a fluid in the list never reaches it — the lines
went missing on exactly the ingredients the order shortcut is most interesting for. Forge's
`RenderTooltipEvent` sees every ingredient but hands out its lines through `Collections.unmodifiableList`,
deliberately. What is left is the one call that paints a list ingredient's tooltip, whatever type it is; and
going through it rather than through a general tooltip event also means the lines cannot appear over the
slots of the screen behind the list.
