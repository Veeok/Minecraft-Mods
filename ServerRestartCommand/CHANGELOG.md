# Changelog

## 1.2.2

- Added a one-time restart request token so only `/restart` can authorize a relaunch.
- `/shutdown` now cancels any pending restart request before stopping the server.
- Prevents stale relauncher processes from making a later shutdown look like a restart.

## 1.2.1

- Removed start-script and free-form launch command execution for CurseForge moderation compliance.
- Replaced `launchCommand`, `start.bat`, and `start.sh` relaunching with structured direct Java launching.
- Added structured launch config keys: `javaExecutable`, `serverJar`, `reuseCurrentJvmArgs`, `jvmArgs`, and `serverArgs`.
- Restart now reuses the current server JVM arguments by default, keeping RAM settings without shell commands.
- Relaunched server output is appended to `restart-server-restart.log`.

## 1.2.0

- Added `/shutdown` and `/shutdown <delay_seconds>` to stop the server without relaunching.
- Added configurable player warning display with `warningIndicator=chat`, `actionbar`, or `both`.
- Added configurable warning color with `warningColor`.
- Added configurable restart and shutdown warning, scheduled, and final messages.
- Added `{seconds}`, `{second_word}`, and `{operation}` placeholders for warning messages.
- Existing config files are backfilled with new keys on startup.

## 1.1.2

- Reduced duplicate restart messages in the server log by using one broadcast path.
- Improved restart failure messages before shutdown, including a clear note that the server is still running.
- Added relauncher validation for missing server folders and missing fallback launch jar.
- Added launch source details to `restart-server-restart.log`.
- Documented the current stage as Beta and the remaining Stable/Release requirement.

## 1.1.1

- Fixed Windows restart handoff by simplifying the `cmd.exe start` command.
- Changed Windows restarts to keep the relaunched command window open, so launch errors are visible.
- Added `restart-server-restart.log` entries from the Java relauncher for easier debugging.

## 1.1.0

- Added cross-platform Java relauncher for Windows, macOS, and Linux.
- Added generated mod icon and Fabric metadata.
- Added author and MIT license metadata.
- Declared support for Minecraft 26.1, 26.1.1, 26.1.2, and 26.2.
- Added fallback launch command warning for default 4G RAM restarts.

## 1.0.0

- Initial server-side restart command implementation.
