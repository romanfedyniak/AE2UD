# Mixins

AE2UD loads its mixins through [MixinBooter](https://github.com/CleanroomMC/MixinBooter) 10.7, declared as
a required dependency on the mod. A mixin is used only where no public API can express the integration; at
the time of writing that is a single case, drawing the craftable "+" over HEI recipe ingredients, because
`RecipesGui` keeps its recipe layouts private and an ingredient's screen position exists only as a rect
relative to its layout.

## Layout

Mixin classes live under `appeng.mixin`, one subpackage per target mod (`appeng.mixin.hei`). Configuration
files are split the same way and named `mixins.appliedenergistics2.<phase>.<mod>.json`, so
`mixins.appliedenergistics2.late.jei.json` holds the mixins that target HEI and are queued in the late
phase. Each config's `package` field points at the subpackage its entries live in, which keeps the entry
names short and stops one target's mixins from being visible to another's config.

Nothing but mixins may live in a package a config claims. Mixin classes are never loaded normally, so an
ordinary class sharing that package breaks in ways that are hard to read.

## Loading

`appeng.mixin.AE2UDLateMixinLoader` implements MixinBooter's `ILateMixinLoader` and maps each config file
to the condition under which it should be queued. MixinBooter finds it by scanning Forge's ASM data table
for implementations of that interface, so the class needs no registration anywhere else; it only needs a
public no-argument constructor. Mixins that target Minecraft or Forge rather than a mod would instead need
`IEarlyMixinLoader` on the coremod class, `appeng.core.AE2UDCore`, and AE2UD currently has none.

Every config sets `"required": true`. Without it a mixin that fails to apply is only a warning in the log,
and the game starts as though the feature did not exist.

## Remapping

Configs and mixin classes target mod classes, whose names are never obfuscated, so classes are annotated
`@Mixin(value = ..., remap = false)`. A method inherited from Minecraft is the exception: it is obfuscated
in a production environment even when the class declaring the override is not. Injecting into one requires
`remap = true` on the `@Inject` itself while the injection point stays `remap = false`, as
`MixinRecipesGui` does for `GuiScreen.drawScreen`. Without it the injector silently finds nothing outside
a development environment; the generated refmap is the place to check, since it lists exactly the members
that will be remapped.
