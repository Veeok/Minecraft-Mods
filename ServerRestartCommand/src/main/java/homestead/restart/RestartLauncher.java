package homestead.restart;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

public final class RestartLauncher {
    private static final String DEFAULT_LAUNCH_COMMAND = "java -Xmx4G -jar fabric-server-launch.jar nogui";

    private RestartLauncher() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            throw new IllegalArgumentException("Expected args: <oldPid> <serverDir> <configPath> <defaultRamFlag>");
        }

        long oldPid = Long.parseLong(args[0]);
        Path serverDir = Path.of(args[1]).toAbsolutePath().normalize();
        Path configPath = Path.of(args[2]).toAbsolutePath().normalize();
        Path defaultRamFlag = Path.of(args[3]).toAbsolutePath().normalize();

        waitForOldServer(oldPid);
        Thread.sleep(2000L);

        LaunchPlan launchPlan = resolveLaunchPlan(serverDir, configPath, defaultRamFlag);
        launchDetached(serverDir, launchPlan.command());
    }

    static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static void waitForOldServer(long oldPid) {
        Optional<ProcessHandle> oldProcess = ProcessHandle.of(oldPid);
        if (oldProcess.isPresent()) {
            oldProcess.get().onExit().join();
        }
    }

    private static LaunchPlan resolveLaunchPlan(Path serverDir, Path configPath, Path defaultRamFlag) throws IOException {
        Properties properties = loadConfig(configPath);
        boolean preferStartScript = readBoolean(properties, "preferStartScript", true);

        if (preferStartScript) {
            Path startScript = findStartScript(serverDir);
            if (startScript != null) {
                return new LaunchPlan(commandForStartScript(startScript), false);
            }
        }

        String configuredLaunchCommand = properties.getProperty("launchCommand", "").trim();
        if (!configuredLaunchCommand.isBlank()) {
            return new LaunchPlan(configuredLaunchCommand, false);
        }

        Files.createDirectories(defaultRamFlag.getParent());
        Files.writeString(
            defaultRamFlag,
            "Default 4G fallback launch used. Set launchCommand in server-restart-command.properties."
        );
        return new LaunchPlan(DEFAULT_LAUNCH_COMMAND, true);
    }

    private static Properties loadConfig(Path configPath) throws IOException {
        Properties properties = new Properties();
        if (Files.isRegularFile(configPath)) {
            try (InputStream input = Files.newInputStream(configPath)) {
                properties.load(input);
            }
        }
        return properties;
    }

    private static Path findStartScript(Path serverDir) {
        Path platformScript = isWindows() ? serverDir.resolve("start.bat") : serverDir.resolve("start.sh");
        if (Files.isRegularFile(platformScript)) {
            return platformScript;
        }
        return null;
    }

    private static String commandForStartScript(Path script) {
        String fileName = script.getFileName().toString().toLowerCase(Locale.ROOT);
        if (isWindows() && fileName.endsWith(".bat")) {
            return "call " + quoteWindows(script.toString());
        }
        if (!isWindows() && fileName.endsWith(".sh")) {
            return "sh " + quoteShell(script.toString());
        }
        return quoteForCurrentShell(script.toString());
    }

    private static void launchDetached(Path serverDir, String command) throws IOException {
        ProcessBuilder builder;
        if (isWindows()) {
            String startCommand = "start \"\" /D " + quoteWindows(serverDir.toString()) + " cmd.exe /c " + quoteWindows(command);
            builder = new ProcessBuilder("cmd.exe", "/c", startCommand);
        } else {
            String shellCommand = "cd " + quoteShell(serverDir.toString())
                + " && nohup sh -c " + quoteShell(command)
                + " >> restart-server-restart.log 2>&1 < /dev/null &";
            builder = new ProcessBuilder("sh", "-c", shellCommand);
        }

        builder.directory(serverDir.toFile());
        builder.start();
    }

    private static String quoteForCurrentShell(String value) {
        return isWindows() ? quoteWindows(value) : quoteShell(value);
    }

    private static String quoteWindows(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static String quoteShell(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private static boolean readBoolean(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return Boolean.parseBoolean(value.trim());
    }

    private record LaunchPlan(String command, boolean defaultRamFallback) {
    }
}
