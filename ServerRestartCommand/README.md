# Server Restart Command

Server-side Fabric mod that adds restart commands for dedicated servers.

Author: Veok

License: MIT

## Commands

- `/restart`
- `/restartserver`
- `/restart <delay_seconds>`
- `/restartserver <delay_seconds>`

The commands work from in-game for permitted operators and from the server console.

## How Restarting Works

Minecraft cannot restart itself after the JVM exits unless something outside the old server process starts it again. This mod handles that by starting a tiny Java relauncher process before shutting the server down.

The relauncher waits for the old Java process to exit, then starts the server using this priority order:

1. `start.bat` on Windows, or `start.sh` on macOS/Linux, if present.
2. `launchCommand` from `config/server-restart-command.properties`.
3. Safe fallback: `java -Xmx4G -jar fabric-server-launch.jar nogui`.

If the 4G fallback is used, the next startup shows a yellow warning to online operators and logs it after startup, so the owner knows to configure their real RAM value.

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
```

If `preferStartScript=true`, the mod uses `start.bat`/`start.sh` first. Set it to `false` to always use `launchCommand`.

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

The built jar will be in:

```text
build/libs/
```
