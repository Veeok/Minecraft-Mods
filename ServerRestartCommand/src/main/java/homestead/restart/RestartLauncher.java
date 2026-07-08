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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

public final class RestartLauncher {
    private static final String RESTART_LOG = "restart-server-restart.log";
    static final String JVM_ARG_SEPARATOR = "\u001F";

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
        if (args.length < 5) {
            throw new IllegalArgumentException("Expected args: <oldPid> <serverDir> <configPath> <currentJavaExecutable> <currentJvmArgs>");
        }

        long oldPid = Long.parseLong(args[0]);
        Path configPath = Path.of(args[2]).toAbsolutePath().normalize();
        Path currentJavaExecutable = Path.of(args[3]).toAbsolutePath().normalize();
        List<String> currentJvmArgs = decodeCurrentJvmArgs(args[4]);

        if (!Files.isDirectory(serverDir)) {
            throw new IOException("Server directory does not exist: " + serverDir);
        }

        log(logPath, "Waiting for old server process " + oldPid + " to exit.");
        waitForOldServer(oldPid);
        Thread.sleep(2000L);

        LaunchPlan launchPlan = resolveLaunchPlan(serverDir, configPath, currentJavaExecutable, currentJvmArgs);
        log(logPath, "Launch source: " + launchPlan.source());
        log(logPath, "Launching Java server: " + commandForLog(launchPlan.command()));
        launchDetached(serverDir, launchPlan.command(), logPath);
        log(logPath, "Restart process was handed off.");
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

    private static LaunchPlan resolveLaunchPlan(
        Path serverDir,
        Path configPath,
        Path currentJavaExecutable,
        List<String> currentJvmArgs
    ) throws IOException {
        Properties properties = loadConfig(configPath);
        Path javaExecutable = resolvePath(
            serverDir,
            properties.getProperty("javaExecutable", "").trim(),
            currentJavaExecutable
        );
        if (!Files.isRegularFile(javaExecutable)) {
            throw new IOException("Java executable was not found: " + javaExecutable);
        }

        Path serverJar = resolvePath(
            serverDir,
            properties.getProperty("serverJar", "fabric-server-launch.jar").trim(),
            serverDir.resolve("fabric-server-launch.jar")
        );
        if (!Files.isRegularFile(serverJar)) {
            throw new IOException("Configured server jar was not found: " + serverJar);
        }

        boolean reuseCurrentJvmArgs = readBoolean(properties, "reuseCurrentJvmArgs", true);
        List<String> jvmArgs = reuseCurrentJvmArgs && !currentJvmArgs.isEmpty()
            ? currentJvmArgs
            : parseArgumentList(properties.getProperty("jvmArgs", "-Xmx4G"));
        List<String> serverArgs = parseArgumentList(properties.getProperty("serverArgs", "nogui"));

        List<String> command = new ArrayList<>();
        command.add(javaExecutable.toString());
        command.addAll(jvmArgs);
        command.add("-jar");
        command.add(serverJar.toString());
        command.addAll(serverArgs);

        String source = reuseCurrentJvmArgs && !currentJvmArgs.isEmpty()
            ? "current Java process arguments"
            : "configured jvmArgs";
        return new LaunchPlan(List.copyOf(command), source);
    }

    private static Path resolvePath(Path serverDir, String configuredValue, Path fallback) {
        if (configuredValue == null || configuredValue.isBlank()) {
            return fallback.toAbsolutePath().normalize();
        }

        Path configuredPath = Path.of(configuredValue);
        if (configuredPath.isAbsolute()) {
            return configuredPath.normalize();
        }
        return serverDir.resolve(configuredPath).toAbsolutePath().normalize();
    }

    private static List<String> decodeCurrentJvmArgs(String encodedArgs) {
        if (encodedArgs == null || encodedArgs.isBlank()) {
            return List.of();
        }

        List<String> decodedArgs = new ArrayList<>();
        for (String arg : encodedArgs.split(JVM_ARG_SEPARATOR, -1)) {
            if (!arg.isBlank()) {
                decodedArgs.add(arg);
            }
        }
        return List.copyOf(decodedArgs);
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

    private static void launchDetached(Path serverDir, List<String> command, Path logPath) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(serverDir.toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(logPath.toFile()));
        builder.start();
    }

    private static List<String> parseArgumentList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        List<String> args = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        boolean escaping = false;

        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (escaping) {
                current.append(character);
                escaping = false;
                continue;
            }

            if (character == '\\') {
                escaping = true;
                continue;
            }

            if (quote != 0) {
                if (character == quote) {
                    quote = 0;
                } else {
                    current.append(character);
                }
                continue;
            }

            if (character == '\'' || character == '"') {
                quote = character;
                continue;
            }

            if (Character.isWhitespace(character)) {
                if (!current.isEmpty()) {
                    args.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }

            current.append(character);
        }

        if (escaping) {
            current.append('\\');
        }

        if (!current.isEmpty()) {
            args.add(current.toString());
        }

        return List.copyOf(args);
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

    private static String commandForLog(List<String> command) {
        List<String> quoted = new ArrayList<>();
        for (String arg : command) {
            quoted.add(arg.contains(" ") ? "\"" + arg.replace("\"", "\\\"") + "\"" : arg);
        }
        return String.join(" ", quoted);
    }

    private static boolean readBoolean(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return Boolean.parseBoolean(value.trim());
    }

    private record LaunchPlan(List<String> command, String source) {
    }
}
