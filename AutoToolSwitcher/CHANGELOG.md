# Changelog

## Auto Tool Switcher 1.4.1

Compatibility update.

### Added

- Fabric metadata now supports Minecraft `26.1` through `26.2`.
- Documented loader support expectations for Fabric, Quilt, Forge, and NeoForge.

### Changed

- Lowered Mod Menu dependency floor to `18.0.0-beta.1` for 26.1-era packs.
- Lowered Cloth Config dependency floor to `26.1.154` for 26.1-era packs.
- Updated build notes to compile against the oldest supported Minecraft version.

### Verified

- Compiles against Minecraft `26.1`.
- Compiles against Minecraft `26.1.2`.
- Previously verified on Minecraft `26.2`.

### Notes

- No local `26.1.1` jar was installed, so `26.1.1` is covered by the `>=26.1 <=26.2` Fabric metadata range but was not locally compiled.
- Forge and NeoForge are not compatible with the Fabric jar. They require separate ports/artifacts.

## Auto Tool Switcher 1.4.0

Big settings update. Core quality-of-life features are on by default, extra features are available but off by default.

### Added

- Hotbar slot filters so you can choose which slots auto switching is allowed to use.
- Durability safety with a configurable minimum durability limit.
- Restore delay to reduce slot flicker after your target disappears.
- Smarter combat scoring for Sharpness, Smite, and Bane of Arthropods.
- Optional combat axes and maces.
- Optional ranged combat switching for bow/crossbow at longer distance.
- Optional right-click block tools for axe, hoe, shovel, and shears behavior.
- Optional HUD indicator when the mod switches slots.
- Optional profiles: Default, Mining, Building, Combat, and Minimal.
- Unbound keybinds for toggling the mod, look switching, mining switching, combat switching, and hold-to-pause.
- Mod Menu settings tabs for triggers, safety, hotbar slots, optional features, and profiles.

### Changed

- Updated Fabric API build target to `0.154.0+26.2`.
- Improved `/autotoolswitcher status` to show slot filters, durability safety, restore delay, and mining preference.
- Improved `/autotoolswitcher help` with new feature descriptions and profile examples.
- Updated mod metadata to mention slot filters, durability safety, combat helpers, profiles, and slot restore.

### Defaults

- Mining switch: on.
- Hostile mob melee switch: on.
- Restore previous slot: on.
- Drop safety: on.
- Durability safety: on.
- All hotbar slots allowed.
- Restore delay: 8 ticks.
- Optional extras are off by default.

## Auto Tool Switcher 1.3.0

- Added Mod Menu integration.
- Added Cloth Config settings UI.
- Made Mod Menu and Cloth Config required dependencies.
- Cleaned up in-game help text.

## Auto Tool Switcher 1.2.0

- Changed attack mode to hostile-mob sword switching.
- Added restore-to-previous-slot behavior.
- Split block switching into look and mining triggers.

## Auto Tool Switcher 1.1.1

- Added icon.
- Updated author and metadata.

## Auto Tool Switcher 1.1.0

- Added configurable look, attack, and safety modes.

## Auto Tool Switcher 1.0.0

- First client-side Fabric build.
