# Mixins

AE2UD loads its mixins through [MixinBooter](https://github.com/CleanroomMC/MixinBooter) 10.7, declared as
a required dependency on the mod. A mixin is used only where no public API can express the integration; at
the time of writing that is five cases:

- drawing the craftable "+" over HEI recipe ingredients, because `RecipesGui` keeps its recipe layouts
  private and an ingredient's screen position exists only as a rect relative to its layout;
- drawing an encoded pattern as its output while shift is held, because the stack a screen or the world
  renders has to be swapped before a model is resolved from it, and neither `GuiContainer.drawSlot` nor
  `RenderItem` offers a hook to do that;
- letting the pick block key reach the network once vanilla has failed to find the block in the player's
  own inventory. Forge has no event for it - it replaced the body of `Minecraft.middleClickMouse` with a
  call to `ForgeHooks.onPickBlock` - and both input events that carry the key are fired *before* that
  call, so a listener on either would have to consume the click rather than follow it;
- giving the two network shortcuts a click before HEI decides what to do with it. HEI takes
  `GuiScreenEvent.MouseInputEvent.Pre` at `EventPriority.HIGHEST` **and** with `receiveCanceled = true`, so
  it acts on a click whatever a listener did with the event first - no priority is ahead of that. What it
  does with the click is not nothing, either: a screen carrying a ghost ingredient handler, which every
  terminal here does, starts a ghost drag on any button, and a screen without one starts a drag of HEI's own
  whenever control is held;
- naming those two shortcuts in the tooltip of the ingredient they act on. `ItemTooltipEvent` is fired by
  `ItemStack.getTooltip`, so a fluid in HEI's list never reaches it, and Forge's `RenderTooltipEvent`, which
  does see every ingredient, hands out its lines through `Collections.unmodifiableList` on purpose.

## Layout

Mixin classes live under `appeng.mixin`, one subpackage per target (`appeng.mixin.hei`,
`appeng.mixin.minecraft`). Configuration files are split the same way and named
`mixins.appliedenergistics2.<phase>.<target>.json`, so `mixins.appliedenergistics2.late.jei.json` holds the
mixins that target HEI and are queued in the late phase, and `mixins.appliedenergistics2.early.minecraft.json`
those that target Minecraft itself. Each config's `package` field points at the subpackage its entries live
in, which keeps the entry names short and stops one target's mixins from being visible to another's config.

Nothing but mixins may live in a package a config claims. Mixin classes are never loaded normally, so an
ordinary class sharing that package breaks in ways that are hard to read.

## Loading

`appeng.mixin.AE2UDLateMixinLoader` implements MixinBooter's `ILateMixinLoader` and maps each config file
to the condition under which it should be queued. MixinBooter finds it by scanning Forge's ASM data table
for implementations of that interface, so the class needs no registration anywhere else; it only needs a
public no-argument constructor.

Mixins that target Minecraft or Forge rather than a mod cannot wait for that phase - those classes are
loaded far earlier - and are listed by `IEarlyMixinLoader` on the coremod class, `appeng.core.AE2UDCore`,
instead. A client-only mixin needs no condition there: listing its entries under the config's `client` key
is what keeps it off a dedicated server.

Every config sets `"required": true`. Without it a mixin that fails to apply is only a warning in the log,
and the game starts as though the feature did not exist.

## Remapping

A mixin onto Minecraft is remapped by default and needs nothing said about it; the generated refmap under
`build/tmp/mixins` is where to check that every member was found, since a target it could not resolve
simply does not appear there.

Mod classes are the other way round. Their names are never obfuscated, so those mixins are annotated
`@Mixin(value = ..., remap = false)`. A method inherited from Minecraft is the exception: it is obfuscated
in a production environment even when the class declaring the override is not. Injecting into one requires
`remap = true` on the `@Inject` itself while the injection point stays `remap = false`, as
`MixinRecipesGui` does for `GuiScreen.drawScreen`. Without it the injector silently finds nothing outside
a development environment.
