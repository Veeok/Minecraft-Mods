# Auto Tool Switcher

Tiny Fabric client mod by Veok.

It keeps your hotbar from being annoying:

- Mines with the best tool in your hotbar.
- Points at hostile mobs and swaps to your best sword.
- Can switch back to your previous slot after the target is gone.
- Has a drop-safe toggle so throwing items does not randomly swap tools.
- Has a Mod Menu config screen, so you do not have to remember every command.

## Required Mods

- Fabric API
- Mod Menu
- Cloth Config

If Mod Menu or Cloth Config is missing, Fabric will stop loading and tell you what dependency is missing.

## Config UI

Open `Mods > Auto Tool Switcher > Configure`.

The UI has toggles for mining, look switching, hostile mob sword switching, restore, and drop safety.

## Commands

The command is mainly for quick changes while playing.

- `/autotoolswitcher status` shows your current setup.
- `/autotoolswitcher help` explains every setting in-game.
- `/autotoolswitcher on`, `off`, or `toggle` controls the whole mod.
- `/autotoolswitcher <setting> on|off|toggle` changes one setting.

Settings: `look`, `mine`, `attack`, `restore`, `drop-safe`.

Example: `/autotoolswitcher look off`

## Defaults

- `look`: off
- `mine`: on
- `attack`: on
- `restore`: on
- `drop-safe`: on

## Build Notes

This version targets Minecraft `26.2`, Fabric Loader `0.19.3`, Fabric API `0.153.0+26.2`, Mod Menu `20.0.0-beta.4`, Cloth Config `26.2.155`, and Java `25`.

Because this build uses local Minecraft/Fabric jars directly, copy `local.properties.example` to `local.properties` and fill in your local paths before running Gradle.
