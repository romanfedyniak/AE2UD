# Loot tables

AE2UD fills two kinds of container from loot tables. One of them is a real table you can override; the
other is added programmatically and cannot be.

## The meteorite chest

Table: `appliedenergistics2:meteor_loot`, shipped at
`assets/appliedenergistics2/loot_tables/meteor_loot.json`.

This is what goes in the Sky Stone Chest at the centre of a meteorite. It is a normal loot table, so a
resource pack or another mod may replace it outright. Two pools are in the file as shipped:

* `presses` — one to four of the four processor presses, at most one of each.
* `junk` — one to three lots of sky stone or of a nugget, two thirds of the time.

The table is rolled once per meteorite, with the meteorite's own seed, so the same world seed always
gives the same chest. `/ae2 meteorites` lists where they are without generating anything, which is a
quicker way to check a change than exploring for one.

It is registered only when both `MeteoriteWorldGen` and `SkyStone` are enabled. With either switched off
the table is never loaded, because it names blocks that would not exist.

### Custom conditions

**`appliedenergistics2:check_tally`** — on an entry. Lets that entry be rolled at most `max` times per
container, however many times its pool is rolled. Vanilla rolls each entry independently, so without this
a pool of four presses rolled four times can hand out the same press four times. Use it with the `tally`
function, which is what does the counting.

```json
{
  "condition": "appliedenergistics2:check_tally",
  "id": "appliedenergistics2:material:13",
  "max": 1,
  "context_id": 0
}
```

`id` is any string, and must match the `id` of the matching `tally` function. `context_id` is optional
and defaults to `0`; it only matters if you want a second entry with the same `id` counted separately.

**`appliedenergistics2:feature_enabled`** — on a pool or an entry. Passes only when every named feature
is enabled in the config. The names are the constants of `appeng.core.features.AEFeature`.

```json
{
  "condition": "appliedenergistics2:feature_enabled",
  "features": ["PRESSES", "SPAWN_PRESSES_IN_METEORITES"]
}
```

`features` may be a single string instead of a list.

### Custom functions

**`appliedenergistics2:tally`** — records that this entry has been rolled, which is what `check_tally`
counts. Leave `id` out and it uses the item's registry name and metadata.

```json
{
  "function": "appliedenergistics2:tally",
  "id": "appliedenergistics2:material:13",
  "context_id": 0
}
```

**`appliedenergistics2:to_random_ore`** — replaces the entry's item with one drawn at random from the
ore dictionary entries named. The item the entry names is only a placeholder and is thrown away. This is
the only way to name loot that depends on which mods are installed. If none of the names is registered
in this pack, the entry produces nothing.

```json
{
  "function": "appliedenergistics2:to_random_ore",
  "ores": ["nuggetIron", "nuggetGold"]
}
```

Both conditions and both functions need the loot to be rolled through AE2UD's own loot context. Rolled
through anything else they are skipped, with a line in the log; nothing breaks, but `check_tally` stops
holding anything back.

## Mineshaft chests

Two pools, `AE2 Crystals` and `AE2 DUSTS`, are appended to
`minecraft:chests/abandoned_mineshaft` in code, so **they cannot be overridden by JSON**. Turn them off
entirely with the `ChestLoot` feature in the config, or retune them with something like LootTweaker.
