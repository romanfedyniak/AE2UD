# Terminal search

What the ME terminal's search field understands, and where each answer comes from.

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

## Where it lives

`appeng.client.me.search`. `RepoSearch` holds the compiled query and every cache; `SearchTokenizer` cuts the
string into groups and terms; one predicate class per channel.

The string is parsed **once**, when it or the tooltip setting changes. What a key answers - its name, its
tooltip, its Ore Dictionary names - is worked out once per key and kept, and so is whether it matched. This
is not a micro-optimisation: `Repo.updateView` re-filters on every update the network sends, and the code
before this recompiled a `Pattern` and rebuilt a full `ItemStack` tooltip for every row of the terminal
every time.
