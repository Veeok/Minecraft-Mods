package homestead.restart;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

public final class RestartLauncher {
    private static final String DEFAULT_LAUNCH_COMMAND = "java -Xmx4G -jar fabric-server-launch.jar nogui";
    private static final String RESTART_LOG = "restart-server-restart.log";

    private RestartLauncher() {
    }

    public static void main(String[] args) throws Exception {
        Path serverDir = args.length >= 2 ? Path.of(args[1]).toAbsolutePath().normalize() : Path.of(".").toAbsolutePath().normalize();
        Path logPath = serverDir.resolve(RESTART_LOG);

        try {
            run(args, serverDir, logPath);
        } catch (Throwable throwable) {
            log(logPath, "Restart launcher failed.", throwable);
            if (throwable instanceof Exception exception) {
                throw exception;
            }
            if (throwable instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(throwable);
        }
    }

    private static void run(String[] args, Path serverDir, Path logPath) throws Exception {
        if (args.length < 4) {
            throw new IllegalArgumentException("Expected args: <oldPid> <serverDir> <configPath> <defaultRamFlag>");
        }

        long oldPid = Long.parseLong(args[0]);
        Path configPath = Path.of(args[2]).toAbsolutePath().normalize();
        Path defaultRamFlag = Path.of(args[3]).toAbsolutePath().normalize();

        if (!Files.isDirectory(serverDir)) {
            throw new IOException("Server directory does not exist: " + serverDir);
        }

        log(logPath, "Waiting for old server process " + oldPid + " to exit.");
        waitForOldServer(oldPid);
        Thread.sleep(2000L);

        LaunchPlan launchPlan = resolveLaunchPlan(serverDir, configPath, defaultRamFlag);
        log(logPath, "Launch source: " + launchPlan.source());
        log(logPath, "Launching restart command: " + launchPlan.command());
        launchDetached(serverDir, launchPlan.command(), logPath);
        log(logPath, "Restart command was handed off.");
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
                return new LaunchPlan(commandForStartScript(startScript), "start script: " + startScript.getFileName(), false);
            }
        }

        String configuredLaunchCommand = properties.getProperty("launchCommand", "").trim();
        if (!configuredLaunchCommand.isBlank()) {
            return new LaunchPlan(configuredLaunchCommand, "configured launchCommand", false);
        }

        Path fallbackJar = serverDir.resolve("fabric-server-launch.jar");
        if (!Files.isRegularFile(fallbackJar)) {
            throw new IOException("No start.bat/start.sh or launchCommand is configured, and fabric-server-launch.jar was not found for the fallback command.");
        }

        Files.createDirectories(defaultRamFlag.getParent());
        Files.writeString(
            defaultRamFlag,
            "Default 4G fallback launch used. Set launchCommand in server-restart-command.properties."
        );
        return new LaunchPlan(DEFAULT_LAUNCH_COMMAND, "default 4G fallback", true);
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
            return "call " + quoteWindows(script.getFileName().toString());
        }
        if (!isWindows() && fileName.endsWith(".sh")) {
            return "sh " + quoteShell(script.toString());
        }
        return quoteForCurrentShell(script.toString());
    }

    private static void launchDetached(Path serverDir, String command, Path logPath) throws IOException {
        ProcessBuilder builder;
        if (isWindows()) {
            builder = new ProcessBuilder(
                "cmd.exe",
                "/c",
                "start",
                "Server Restart Command",
                "/D",
                serverDir.toString(),
                "cmd.exe",
                "/k",
                command
            );
        } else {
            String shellCommand = "cd " + quoteShell(serverDir.toString())
                + " && nohup sh -c " + quoteShell(command)
                + " >> " + quoteShell(RESTART_LOG) + " 2>&1 < /dev/null &";
            builder = new ProcessBuilder("sh", "-c", shellCommand);
        }

        builder.directory(serverDir.toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(logPath.toFile()));
        builder.start();
    }

    private static void log(Path logPath, String message) {
        try {
            Path parent = logPath.getParent();
            if (parent != null && !Files.isDirectory(parent)) {
                return;
            }
            Files.writeString(
                logPath,
                "[" + Instant.now() + "] " + message + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
        } catch (IOException ignored) {
        }
    }

    private static void log(Path logPath, String message, Throwable throwable) {
        StringWriter stackTrace = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stackTrace));
        log(logPath, message + System.lineSeparator() + stackTrace);
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

    private record LaunchPlan(String command, String source, boolean defaultRamFallback) {
    }
}
