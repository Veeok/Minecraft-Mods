# Release Checklist

## Current Stage

1.2.0 is Beta.

Windows `start.bat` restart has been tested on a live dedicated server. Linux and macOS relaunch paths are implemented but still need live validation before marking the mod Stable/Release.

## Stable/Release Gates

- Windows `start.bat` restart succeeds repeatedly.
- Windows `launchCommand` restart succeeds when `preferStartScript=false`.
- Windows fallback restart succeeds when no script or launch command is available.
- Linux `start.sh` restart succeeds on a dedicated server.
- Linux `launchCommand` restart succeeds on a dedicated server.
- macOS `start.sh` restart succeeds, if macOS support is advertised.
- `/restart`, `/restartserver`, `/restart 0`, `/restart 5`, and `/restart 60` work.
- `/shutdown`, `/shutdown 0`, `/shutdown 5`, and `/shutdown 60` work.
- `warningIndicator=chat`, `actionbar`, and `both` display warnings correctly.
- Custom restart and shutdown warning messages display configured text.
- Non-operators cannot run the command when `permissionLevel=4`.
- Server saves worlds cleanly before shutdown.
- Restart does not leave duplicate Java server processes.
- `restart-server-restart.log` clearly explains the selected launch source and any relaunch failure.
