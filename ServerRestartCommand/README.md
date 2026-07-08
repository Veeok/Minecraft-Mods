# Server Restart Command

Server-side Fabric mod that adds restart commands for dedicated servers.

Author: Veok

License: MIT

Current stage: Beta. Windows restart has been tested on a live dedicated server. Linux and macOS restart paths are implemented but still need live server testing before this should be marked Stable/Release.

## Commands

- `/restart`
- `/restartserver`
- `/restart <delay_seconds>`
- `/restartserver <delay_seconds>`
- `/shutdown`
- `/shutdown <delay_seconds>`

The commands work from in-game for permitted operators and from the server console.

`/restart` stops the server and starts it again. `/shutdown` stops the server only.

## How Restarting Works

Minecraft cannot restart itself after the JVM exits unless something outside the old server process starts it again. This mod handles that by starting a tiny Java relauncher process before shutting the server down.

The relauncher waits for the old Java process to exit, then starts the server using this priority order:

1. `start.bat` on Windows, or `start.sh` on macOS/Linux, if present.
2. `launchCommand` from `config/server-restart-command.properties`.
3. Safe fallback: `java -Xmx4G -jar fabric-server-launch.jar nogui`.

If the 4G fallback is used, the next startup shows a yellow warning to online operators and logs it after startup, so the owner knows to configure their real RAM value.

On Windows, the relaunched server window stays open if the launch command fails, so the error remains visible. The relauncher also writes handoff details to `restart-server-restart.log` in the server folder, including the selected launch source.

## Config

Generated at:

```text
config/server-restart-command.properties
```

Example:

```properties
launchCommand=java -Xmx12G -jar fabric-server-launch.jar nogui
preferStartScript=true
defaultDelaySeconds=5
permissionLevel=4
defaultRamWarningDelaySeconds=60
warningIndicator=both
warningColor=yellow
restartWarningMessage=[SERVER] Restart in {seconds} {second_word}.
restartNowMessage=[SERVER] Restarting now.
restartScheduledMessage=Restart scheduled in {seconds} {second_word}.
shutdownWarningMessage=[SERVER] Shutdown in {seconds} {second_word}.
shutdownNowMessage=[SERVER] Shutting down now.
shutdownScheduledMessage=Shutdown scheduled in {seconds} {second_word}.
```

If `preferStartScript=true`, the mod uses `start.bat`/`start.sh` first. Set it to `false` to always use `launchCommand`.

`warningIndicator` controls where player warnings appear:

- `chat`
- `actionbar`
- `both`

Message placeholders:

- `{seconds}` - the number of seconds left.
- `{second_word}` - `second` or `seconds`.
- `{operation}` - `restart` or `shutdown`.

You can translate the warning messages into any language by editing the message values in the config file.

## Requirements

- Minecraft 26.1, 26.1.1, 26.1.2, and 26.2
- Fabric Loader 0.19.3+
- Fabric API
- Java 25+

Older Minecraft versions are not declared as supported because the server command and permission APIs changed. The mod may be adapted later if those versions need a separate build.

## Building

```bash
gradle build
```

This repository does not currently include a Gradle wrapper. Use a local Gradle install until a wrapper is generated for a future release.

The built jar will be in:

```text
build/libs/
```
