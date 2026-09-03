# Search

What a search field in this mod understands, and where each answer comes from. Renamed from
`TERMINAL_SEARCH.md`, which is the name older changelog entries know it by, when the grammar stopped
belonging to the terminal alone.

Seven fields read it:

| Screen | Field | Asked of |
| --- | --- | --- |
| ME Terminal | the search box | one key per row |
| Cell view | the search box | one key per row |
| Crafting Plan tree | the search box | one key per cell |
| Crafting Status, and a CPU's own screen | the search box | one key per row |
| Crafting Plan | the search box | one key per row |
| Interface Terminal | Recipe Inputs, Recipe Outputs | one pattern's ingredients, as a set |
| Interface Configuration Terminal | Configured Items | one interface's configured items, as a set |

The two boxes that name interfaces - the Interface Terminal's third field and the Configuration
Terminal's second - are plain case-insensitive substrings, and always have been: an interface's name is
not a key, and none of the channels below have anything to ask of it.

The grammar is HadEnoughItems', on purpose. Under the `JEI_AUTOSEARCH` and `JEI_MANUAL_SEARCH` modes of the
search-box setting the terminal hands its string to HEI verbatim, so the two panes have to read it the same
way. Where modern upstream AE2 disagrees with HEI - it reads `$` as the tooltip channel and `#` as tags -
HEI wins here, because the player types into HEI's field every day and the meaning of `#` and `$` is the
part they would trip over.

## Grammar

| Written | Means |
| --- | --- |
| `iron ingot` | every term has to match |
| `iron \| gold` | either side may match |
| `"iron ingot"` | one term with a space in it |
| `-stone`, `!stone` | leave out anything that matches |
| `\x` | the character `x`, whatever it usually means |
| `/^dense.*plate$/` | a regular expression |

`|` binds loosest: `a b | c` is `(a AND b) OR c`. A term that says nothing yet - a prefix with nothing after
it, an expression that does not compile - filters nothing out, so a half-typed query does not blank the
screen.

## Channels

| Prefix | Searches | Answered by |
| --- | --- | --- |
| *(none)* | the name on the row, and the tooltip while `SEARCH_TOOLTIPS` is on | `AEKey.getDisplayName()` |
| `@` | the mod's id, and the mod's name | `AEKey.getModId()`, `Platform.getModName` |
| `#` | the tooltip alone | `Platform.getTooltip` |
| `$` | the Ore Dictionary names | `AEKey.getOreDictNames()` |
| `&`, `*` | the registry name, such as `minecraft:stone` | `AEKey.getId()` |
| `/.../` | the name, and the tooltip while `SEARCH_TOOLTIPS` is on | as above, as a regex |

Everything is matched case-insensitively, in `Locale.ROOT` rather than the player's locale - a Turkish
client would otherwise fail to fold `I` onto `i` and find nothing.

### Ore Dictionary

`AEKey.getOreDictNames()` is an amendment to the upstream API, registered as item 31 in
`docs/port/STATUS.md`. Upstream's equivalent channel searches item tags, which do not exist on this version;
the Ore Dictionary is what stands in their place. It returns nothing by default, and `AEItemKey` overrides
it - so a fluid never answers `$`, and an addon's own key type answers it as soon as the addon says how.

### Tooltips

Whether a **bare** term also looks inside tooltips is the `SEARCH_TOOLTIPS` setting, the button in the
terminal - not a symbol. `#` searches tooltips whatever that setting says.

The last tooltip line is dropped when it is the mod's name. Nothing in this mod puts it there, but packs
commonly do through `ItemTooltipEvent`, and left in it would make a bare `thermal` match that mod's whole
catalogue through a line that says nothing about the item. `@` is the way to ask that question.

### Regular expressions

The old search had no grammar: the whole string was compiled as a regex and run over tooltip lines, with a
silent fall back to a literal match when it did not compile. `/.../` is what is left of that, said out loud.
It is ours alone - HEI does not understand slashes, so a regex query mirrored into HEI's field finds nothing
there.

## Putting a key into a box

A screen lists its boxes as `KeySearchTarget`s - a rectangle in screen coordinates, and what to write in
it - through `AEBaseGui.getKeySearchTargets()`. Two callers read that list: `AEBaseGui.mouseClicked`, for
an item held on the cursor, and `AEGuiHandler.getTargets`, for an ingredient dragged out of HEI. Neither
knows anything about text fields, which is the point - the two field classes here share no supertype, and
the Crafting Plan's field is built in the window's coordinates while every other is built in the screen's.

What goes in is `RepoSearch.termFor(key)`: the display name, stripped of formatting codes and quoted, so
the whole name is one term. The two boxes that search interface *names* are deliberately not targets.

## What the tooltip says

`RepoSearch.syntaxTooltip(fieldName)` writes the whole grammar out, one line to a rule, and every field
running the grammar uses it - the two that search interface names do not, having no grammar to explain.
It collapses to the field's name plus *hold Shift* until Shift is held: eleven lines is a reference, not
a hint, and a box is small.

It sits here rather than in `Tooltips` for two reasons. A rule added to the parser and a line describing
it are then one file apart. And `appeng.core.localization` is loaded on a dedicated server - items reach
`Tooltips` - so it keeps no client classes, and reading the keyboard needs one.

A field whose tooltip answers to Shift cannot hand it over as a string once, the way a tooltip that never
changes can: `MEGuiTooltipTextField` takes a `Supplier<String>`, read on each frame the box is hovered.

## Keeping what was typed

`Settings.SEARCH_KEEP` says whether a box keeps its text when the screen closes, and answers for all seven
fields plus the two that search names. Each screen keeps its own text - the setting is shared, the text is
not. It used to be half of `SEARCH_MODE`, which is why a config written before the split names a mode like
`AUTOSEARCH_KEEP`; `AEClientConfig.migrateSearchKeep` rewrites those. See item 32 in `docs/port/STATUS.md`.

## Asked of a set

A pattern is not one key, and neither is an interface's shelf of configured items. `RepoSearch.matchesAny`
lifts the same compiled terms onto a whole set:

- a **positive** term needs one key in the set to satisfy it;
- an **excluded** term needs none of them to.

That second line is the whole reason for the method. `-iron` said of a pattern means *no ingredient is
iron*, and asking it of one ingredient at a time can only mean *this one is not iron*, which every other
ingredient in the pattern answers yes to. Both readings are compiled in one pass over the terms, so a query
costs the same as it did.

`RepoSearch.hasPositiveTerms` goes with it. A screen that marks what it found asks this before marking
anything: under `-iron` everything left qualifies, and lighting up all of it points at nothing.

## Where it lives

`appeng.client.me.search`. `RepoSearch` holds the compiled query and every cache; `SearchTokenizer` cuts the
string into groups and terms; one predicate class per channel. A screen with a field of its own keeps a
`RepoSearch` per field, feeds it on every change and asks `matches` or `matchesAny` per row.

The string is parsed **once**, when it or the tooltip setting changes. What a key answers - its name, its
tooltip, its Ore Dictionary names - is worked out once per key and kept, and so is whether it matched. This
is not a micro-optimisation: `Repo.updateView` re-filters on every update the network sends, and the code
before this recompiled a `Pattern` and rebuilt a full `ItemStack` tooltip for every row of the terminal
every time.
