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

The relauncher waits for the old Java process to exit, then starts the configured server jar directly with Java.

No Windows batch files, Unix shell scripts, or free-form command strings are executed by the mod.

By default, the relauncher reuses the current server JVM arguments so your RAM setting is kept. If you set `reuseCurrentJvmArgs=false`, it uses `jvmArgs` from the config instead.

Relaunch details and relaunched server output are written to `restart-server-restart.log` in the server folder.

## Config

Generated at:

```text
config/server-restart-command.properties
```

Example:

```properties
javaExecutable=
serverJar=fabric-server-launch.jar
reuseCurrentJvmArgs=true
jvmArgs=-Xmx4G
serverArgs=nogui
defaultDelaySeconds=5
permissionLevel=4
warningIndicator=both
warningColor=yellow
restartWarningMessage=[SERVER] Restart in {seconds} {second_word}.
restartNowMessage=[SERVER] Restarting now.
restartScheduledMessage=Restart scheduled in {seconds} {second_word}.
shutdownWarningMessage=[SERVER] Shutdown in {seconds} {second_word}.
shutdownNowMessage=[SERVER] Shutting down now.
shutdownScheduledMessage=Shutdown scheduled in {seconds} {second_word}.
```

`javaExecutable` can be left blank to use the same Java executable that started the current server.

`serverJar` is the jar file to start from the server folder. For Fabric servers, this is usually `fabric-server-launch.jar`.

`reuseCurrentJvmArgs=true` keeps the current JVM flags, including RAM settings. Set it to `false` to use `jvmArgs` instead.

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
