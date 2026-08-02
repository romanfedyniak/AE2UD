# Applied Energistics 2 Unofficial Deconstructed

[![GitHub issues](https://img.shields.io/github/issues/romanfedyniak/AE2UD.svg)](https://github.com/romanfedyniak/AE2UD/issues) [![GitHub pull requests](https://img.shields.io/github/issues-pr/romanfedyniak/AE2UD.svg)](https://github.com/romanfedyniak/AE2UD/pulls)

---

## About

A maintained fork of a Mod about Matter, Energy and using them to conquer the world.

**AE2UD is a heavily reworked, API-breaking fork.** It is not compatible with existing worlds that used AE2 Unofficial Extended Life and is not a drop-in replacement for vanilla AE2 or other AE2 forks. Addons made for AE2 UEL or vanilla AE2 are incompatible unless they explicitly support AE2UD. Back up your world before installing or updating the mod.

This project continues [AE2 Unofficial Extended Life](https://www.curseforge.com/minecraft/mc-mods/ae2-extended-life), which began as a Minecraft 1.12.2 fork of [Applied Energistics 2](https://www.curseforge.com/minecraft/mc-mods/applied-energistics-2) by [PrototypeTrousers](https://github.com/PrototypeTrousers) and was later maintained by the [AE2 Unofficial Extended Life Team](https://github.com/AE2-UEL). AE2UD continues that lineage as an independent, actively developed fork.

Selected fixes, features, implementation ideas, and reference code have also been adapted from [GTNewHorizons' Applied Energistics 2 Unofficial](https://github.com/GTNewHorizons/Applied-Energistics-2-Unofficial) and the actively maintained [Applied Energistics 2 upstream project](https://github.com/AppliedEnergistics/Applied-Energistics-2).

See [CHANGES.md](https://github.com/romanfedyniak/AE2UD/blob/main/CHANGES.md) for the current feature and fix list.
Addon authors can find the new extension points in the [upgrade-card API](docs/UPGRADE_API.md) and
[terminal pin API](docs/TERMINAL_PIN_API.md) guides.

## RecipeStages

You need to add the following into a CraftTweaker script:
```
mods.recipestages.Recipes.setPackageStage("appeng", allStages);
```

The second argument allows you to customize which stages exactly you want to be craftable with AE2. Not sure why you would do that, but we support that nonetheless!

## License

AE2UD retains the licenses of the project it is derived from. Unless a file states otherwise:

* The main mod code is licensed under the [GNU Lesser General Public License v3](LICENSE).
  * Original Applied Energistics 2 work: Copyright (c) 2013-2018 AlgorithmX2 and contributors.
  * Subsequent upstream modifications remain copyright their respective contributors.
  * AE2UD modifications: Copyright (c) 2025-2026 Roman Feduniak and AE2UD contributors.
* The Applied Energistics 2 API is licensed under the [MIT License](src/api/java/appeng/api/LICENSE).
* Textures and models are licensed under [CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/).
* Text and translations are released without restriction under [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/).

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for project lineage, attribution, and material adapted from other AE2 projects.

## Credits

Special thanks to:

* The AE2 Unofficial Extended Life Team for maintaining the fork this project continues from
* PrototypeTrousers for the initial fork
* The GTNewHorizons Applied Energistics 2 contributors for fixes, features, and implementation references adapted by AE2UD
* Notch et al for Minecraft
* Lex et al for MinecraftForge
* AlgorithmX2 and the Applied Energistics 2 maintainers for Applied Energistics 2
* all [Applied Energistics 2 contributors](https://github.com/AppliedEnergistics/Applied-Energistics-2/graphs/contributors)

AE2UD is an independent project and is not affiliated with or endorsed by the projects or maintainers listed above.
