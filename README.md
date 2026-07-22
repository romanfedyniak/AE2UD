# Applied Energistics 2 Unofficial Deconstructed

[![GitHub issues](https://img.shields.io/github/issues/romanfedyniak/AE2UD.svg)](https://github.com/romanfedyniak/AE2UD/issues) [![GitHub pull requests](https://img.shields.io/github/issues-pr/romanfedyniak/AE2UD.svg)](https://github.com/romanfedyniak/AE2UD/pulls)

---

## About

A maintained fork of a Mod about Matter, Energy and using them to conquer the world.

**AE2UD is a heavily reworked, API-breaking fork.** It is not a drop-in replacement for vanilla AE2 or other AE2 forks - its API and internals are being deliberately restructured, so addons built against vanilla AE2 are not expected to work here without changes.

This project started as a fork of [Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2) by [PrototypeTrousers](https://github.com/PrototypeTrousers), initially addressing issues related to the [Nomifactory](https://www.curseforge.com/minecraft/modpacks/nomifactory) modpack, and was later maintained by the [AE2 Unofficial Extended Life Team](https://github.com/AE2-UEL) (now unmaintained). AE2UD continues from that lineage as an independent, actively developed fork.

## RecipeStages

You need to add the following into a CraftTweaker script:
```
mods.recipestages.Recipes.setPackageStage("appeng", allStages);
```

The second argument allows you to customize which stages exactly you want to be craftable with AE2. Not sure why you would do that, but we support that nonetheless!

## License
* Applied Energistics 2 API
  - (c) 2013 - 2018 AlgorithmX2 et al
  - [![License](https://img.shields.io/badge/License-MIT-red.svg?style=flat-square)](http://opensource.org/licenses/MIT)
* Applied Energistics 2
  - (c) 2013 - 2018 AlgorithmX2 et al
  - [![License](https://img.shields.io/badge/License-LGPLv3-blue.svg?style=flat-square)](https://raw.githubusercontent.com/AppliedEnergistics/Applied-Energistics-2/rv2/LICENSE)
* Textures and Models
  - (c) 2013 - 2018 AlgorithmX2 et al
  - [![License](https://img.shields.io/badge/License-CC%20BY--NC--SA%203.0-yellow.svg?style=flat-square)](https://creativecommons.org/licenses/by-nc-sa/3.0/)
* Text and Translations
  - [![License](https://img.shields.io/badge/License-No%20Restriction-green.svg?style=flat-square)](https://creativecommons.org/publicdomain/zero/1.0/)

## Credits

Special thanks to:

* The AE2 Unofficial Extended Life Team for maintaining the fork this project continues from
* PrototypeTrousers for the initial fork
* Notch et al for Minecraft
* Lex et al for MinecraftForge
* AlgorithmX2 for AppliedEnergistics2
* all [contributors](https://github.com/AppliedEnergistics/Applied-Energistics-2/graphs/contributors)
