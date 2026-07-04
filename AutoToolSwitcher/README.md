# Auto Tool Switcher

Tiny Fabric client mod by Veok.

It keeps your hotbar from being annoying:

- Mines with the best tool in your hotbar.
- Points at hostile mobs and swaps to your best sword.
- Can switch back to your previous slot after the target is gone.
- Has a drop-safe toggle so throwing items does not randomly swap tools.
- Has a Mod Menu config screen, so you do not have to remember every command.
- Lets you choose which hotbar slots are allowed.
- Protects low-durability tools.
- Optional extras: Silk Touch/Fortune preference, right-click tools, ranged combat, HUD indicator, combat axes/maces, and profiles.

## Required Mods

- Fabric API
- Mod Menu
- Cloth Config

If Mod Menu or Cloth Config is missing, Fabric will stop loading and tell you what dependency is missing.

## Config UI

Open `Mods > Auto Tool Switcher > Configure`.

The UI has toggles for mining, look switching, hostile mob sword switching, restore, and drop safety.

It also has advanced settings for slot filters, durability limits, restore delay, mining preference, optional combat tools, optional ranged switching, and profiles.

## Changelog

See `CHANGELOG.md` for version notes.

## Commands

The command is mainly for quick changes while playing.

- `/autotoolswitcher status` shows your current setup.
- `/autotoolswitcher help` explains every setting in-game.
- `/autotoolswitcher on`, `off`, or `toggle` controls the whole mod.
- `/autotoolswitcher <setting> on|off|toggle` changes one setting.

Settings: `look`, `mine`, `attack`, `restore`, `drop-safe`.

Optional settings: `right-click`, `ranged`, `hud`, `durability`, `axe-combat`, `mace-combat`.

Profiles: `/autotoolswitcher profile mining|building|combat|minimal|default`

Example: `/autotoolswitcher look off`

## Defaults

- `look`: off
- `mine`: on
- `attack`: on
- `restore`: on
- `drop-safe`: on
- `durability safety`: on
- `restore delay`: 8 ticks
- `allowed slots`: all hotbar slots
- Optional extras: off

## Build Notes

This version targets Minecraft `26.2`, Fabric Loader `0.19.3`, Fabric API `0.154.0+26.2`, Mod Menu `20.0.0-beta.4`, Cloth Config `26.2.155`, and Java `25`.

Because this build uses local Minecraft/Fabric jars directly, copy `local.properties.example` to `local.properties` and fill in your local paths before running Gradle.
