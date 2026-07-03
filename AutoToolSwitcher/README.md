# Auto Tool Switcher

Tiny Fabric client mod by Veok.

It keeps your hotbar from being annoying:

- Mines with the best tool in your hotbar.
- Points at hostile mobs and swaps to your best sword.
- Can switch back to your previous slot after the target is gone.
- Has a drop-safe toggle so throwing items does not randomly swap tools.

## Commands

`/autotoolswitcher status`

`/autotoolswitcher on|off|toggle`

`/autotoolswitcher look on|off|toggle`

`/autotoolswitcher mine on|off|toggle`

`/autotoolswitcher attack on|off|toggle`

`/autotoolswitcher restore on|off|toggle`

`/autotoolswitcher drop-safe on|off|toggle`

`/autotoolswitcher reset`

## Defaults

- `look`: off
- `mine`: on
- `attack`: on
- `restore`: on
- `drop-safe`: on

## Build Notes

This version targets Minecraft `26.2`, Fabric Loader `0.19.3`, Fabric API `0.153.0+26.2`, and Java `25`.

Because this build uses local Minecraft/Fabric jars directly, copy `local.properties.example` to `local.properties` and fill in your local paths before running Gradle.
