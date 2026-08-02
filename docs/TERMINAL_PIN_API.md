# Terminal pin API

Addon terminals can opt into AE2UD's standard crafting and player pin UI by implementing
`ITerminalPinHost` in addition to `ITerminalHost`.

For a block, tile, or part, create one storage object with
`TerminalPinStorages.forHost(saveCallback)`, return it from `getTerminalPinStorage()`, and call its
`readFromNBT` and `writeToNBT` methods from the host's own persistence methods. The callback must mark the
host for saving whenever a player's rows or pins change.

For a terminal item, use `TerminalPinStorages.forItem(stack, saveCallback)`. This factory reads and writes
the terminal's item NBT automatically; the callback only needs to notify the containing inventory when the
host requires it.

`ITerminalPinStorage.forPlayer(UUID)` returns the player-specific view. It exposes the crafting-row and
player-row settings plus 144 persistent manual pin slots. Pins are `AEKey`s, so registered addon key types
work without item placeholders. Crafting pins are intentionally not part of persistent storage: the
standard container derives them from the active CPUs on the terminal's current grid.

Hosts using `ContainerMEMonitorable` and `GuiMEMonitorable` need no additional packets or screen code.
Specialized addon containers may still use the storage interfaces directly, but are responsible for their
own synchronization and interaction.
