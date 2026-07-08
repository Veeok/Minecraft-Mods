package homestead.restart;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionCheck;
import net.minecraft.server.permissions.PermissionLevel;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.lang.management.ManagementFactory;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ServerRestartCommandMod implements ModInitializer {
    private static final String MOD_NAME = "Server Restart Command";
    private static final AtomicBoolean OPERATION_SCHEDULED = new AtomicBoolean(false);
    private static final Path RESTART_REQUEST = Path.of("config", "server-restart-command-restart-request.flag");

    private static RestartConfig config;

    @Override
    public void onInitialize() {
        config = RestartConfig.load();
        var permission = new PermissionCheck.Require(new Permission.HasCommandLevel(PermissionLevel.byId(config.permissionLevel)));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(operationCommand("restartserver", Operation.RESTART, permission));
            dispatcher.register(operationCommand("restart", Operation.RESTART, permission));
            dispatcher.register(operationCommand("shutdown", Operation.SHUTDOWN, permission));
        });
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> operationCommand(
        String name,
        Operation operation,
        PermissionCheck permission
    ) {
        return Commands.literal(name)
            .requires(Commands.hasPermission(permission))
            .executes(context -> scheduleOperation(context.getSource(), operation, config.defaultDelaySeconds))
            .then(Commands.argument("delay_seconds", IntegerArgumentType.integer(0, 3600))
                .executes(context -> scheduleOperation(
                    context.getSource(),
                    operation,
                    IntegerArgumentType.getInteger(context, "delay_seconds")
                )));
    }

    private static int scheduleOperation(CommandSourceStack source, Operation operation, int delaySeconds) {
        MinecraftServer server = source.getServer();

        if (!OPERATION_SCHEDULED.compareAndSet(false, true)) {
            source.sendFailure(Component.literal(MOD_NAME + ": a restart or shutdown is already scheduled."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(MOD_NAME + ": " + config.scheduledMessage(operation, delaySeconds)), true);

        Thread operationThread = new Thread(
            () -> runOperation(server, operation, delaySeconds),
            "server-restart-command-" + operation.configName
        );
        operationThread.setDaemon(false);
        operationThread.start();
        return 1;
    }

    private static void runOperation(MinecraftServer server, Operation operation, int delaySeconds) {
        try {
            announceCountdown(server, operation, delaySeconds);
            if (operation == Operation.RESTART) {
                startRestartLauncher();
            } else {
                cancelPendingRestartRequest();
            }
            server.execute(() -> {
                warnPlayers(server, config.nowMessage(operation));
                server.halt(false);
            });
        } catch (Exception exception) {
            OPERATION_SCHEDULED.set(false);
            server.execute(() -> {
                warnPlayers(server, operation.displayName + " failed before shutdown. The server is still running. Check restart-server-restart.log and the console for details: " + exception.getMessage());
                server.sendSystemMessage(Component.literal("[Server Restart Command] Restart launcher error:"));
                server.sendSystemMessage(Component.literal(stackTrace(exception)));
            });
        }
    }

    private static void announceCountdown(MinecraftServer server, Operation operation, int delaySeconds) throws InterruptedException {
        if (delaySeconds <= 0) {
            return;
        }

        for (int remaining = delaySeconds; remaining > 0; remaining--) {
            if (shouldAnnounce(remaining, delaySeconds)) {
                int seconds = remaining;
                server.execute(() -> warnPlayers(server, config.warningMessage(operation, seconds)));
            }
            Thread.sleep(1000L);
        }
    }

    private static boolean shouldAnnounce(int remaining, int initialDelay) {
        return remaining == initialDelay || remaining <= 5 || remaining == 10 || remaining == 30 || remaining % 60 == 0;
    }

    private static void startRestartLauncher() throws IOException, URISyntaxException {
        long currentPid = ProcessHandle.current().pid();
        Path serverDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path configPath = RestartConfig.CONFIG_PATH.toAbsolutePath().normalize();
        Path restartRequest = RESTART_REQUEST.toAbsolutePath().normalize();
        String restartToken = currentPid + ":" + Instant.now() + ":" + UUID.randomUUID();
        Path modJar = Path.of(ServerRestartCommandMod.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();
        Path javaExecutable = Path.of(
            System.getProperty("java.home"),
            "bin",
            RestartLauncher.isWindows() ? "java.exe" : "java"
        );

        List<String> command = List.of(
            javaExecutable.toString(),
            "-cp",
            modJar.toString(),
            RestartLauncher.class.getName(),
            Long.toString(currentPid),
            serverDir.toString(),
            configPath.toString(),
            javaExecutable.toString(),
            String.join(RestartLauncher.JVM_ARG_SEPARATOR, ManagementFactory.getRuntimeMXBean().getInputArguments()),
            restartRequest.toString(),
            restartToken
        );

        Files.createDirectories(restartRequest.getParent());
        Files.writeString(restartRequest, restartToken);

        try {
            new ProcessBuilder(command)
                .directory(serverDir.toFile())
                .start();
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(restartRequest);
            throw exception;
        }
    }

    private static void cancelPendingRestartRequest() {
        try {
            Files.deleteIfExists(RESTART_REQUEST);
        } catch (IOException ignored) {
        }
    }

    private static void warnPlayers(MinecraftServer server, String message) {
        Component warning = Component.literal(message).withStyle(config.warningColor);
        boolean sentChat = false;

        if (config.warningIndicator.showChat) {
            server.getPlayerList().broadcastSystemMessage(warning, false);
            sentChat = true;
        }

        if (config.warningIndicator.showActionBar && !sendActionBar(server, warning) && !sentChat) {
            server.getPlayerList().broadcastSystemMessage(warning, false);
        }
    }

    private static boolean sendActionBar(MinecraftServer server, Component message) {
        try {
            Object playerList = server.getPlayerList();
            Method getPlayers = playerList.getClass().getMethod("getPlayers");
            Method displayClientMessage = null;

            for (Object player : (List<?>) getPlayers.invoke(playerList)) {
                if (displayClientMessage == null) {
                    displayClientMessage = player.getClass().getMethod("displayClientMessage", Component.class, boolean.class);
                }
                displayClientMessage.invoke(player, message, true);
            }
            return true;
        } catch (ReflectiveOperationException exception) {
            server.sendSystemMessage(Component.literal("[Server Restart Command] Could not send action-bar warning: " + exception.getMessage()));
            return false;
        }
    }

    private static String stackTrace(Exception exception) {
        StringWriter stackTrace = new StringWriter();
        exception.printStackTrace(new PrintWriter(stackTrace));
        return stackTrace.toString();
    }

    private enum Operation {
        RESTART("restart"),
        SHUTDOWN("shutdown");

        private final String configName;
        private final String displayName;

        Operation(String configName) {
            this.configName = configName;
            this.displayName = configName.substring(0, 1).toUpperCase(Locale.ROOT) + configName.substring(1);
        }
    }

    private enum WarningIndicator {
        CHAT(true, false),
        ACTIONBAR(false, true),
        BOTH(true, true);

        private final boolean showChat;
        private final boolean showActionBar;

        WarningIndicator(boolean showChat, boolean showActionBar) {
            this.showChat = showChat;
            this.showActionBar = showActionBar;
        }
    }

    private record RestartConfig(
        int defaultDelaySeconds,
        int permissionLevel,
        WarningIndicator warningIndicator,
        ChatFormatting warningColor,
        String restartWarningMessage,
        String restartNowMessage,
        String restartScheduledMessage,
        String shutdownWarningMessage,
        String shutdownNowMessage,
        String shutdownScheduledMessage
    ) {
        private static final Path CONFIG_PATH = Path.of("config", "server-restart-command.properties");

        private static RestartConfig load() {
            Properties defaults = defaultProperties();
            Properties properties = new Properties();
            boolean shouldSave = false;

            try {
                if (Files.exists(CONFIG_PATH)) {
                    try (InputStream input = Files.newInputStream(CONFIG_PATH)) {
                        properties.load(input);
                    }
                } else {
                    Files.createDirectories(CONFIG_PATH.getParent());
                    shouldSave = true;
                }

                for (String key : defaults.stringPropertyNames()) {
                    if (!properties.containsKey(key)) {
                        properties.setProperty(key, defaults.getProperty(key));
                        shouldSave = true;
                    }
                }

                if (shouldSave) {
                    try (OutputStream output = Files.newOutputStream(CONFIG_PATH)) {
                        properties.store(output, "Server Restart Command configuration");
                    }
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Could not load " + CONFIG_PATH, exception);
            }

            return new RestartConfig(
                readInt(properties, "defaultDelaySeconds", 5, 0, 3600),
                readInt(properties, "permissionLevel", 4, 0, 4),
                readWarningIndicator(properties, "warningIndicator", WarningIndicator.BOTH),
                readWarningColor(properties, "warningColor", ChatFormatting.YELLOW),
                properties.getProperty("restartWarningMessage"),
                properties.getProperty("restartNowMessage"),
                properties.getProperty("restartScheduledMessage"),
                properties.getProperty("shutdownWarningMessage"),
                properties.getProperty("shutdownNowMessage"),
                properties.getProperty("shutdownScheduledMessage")
            );
        }

        private static Properties defaultProperties() {
            Properties properties = new Properties();
            properties.setProperty("javaExecutable", "");
            properties.setProperty("serverJar", "fabric-server-launch.jar");
            properties.setProperty("reuseCurrentJvmArgs", "true");
            properties.setProperty("jvmArgs", "-Xmx4G");
            properties.setProperty("serverArgs", "nogui");
            properties.setProperty("defaultDelaySeconds", "5");
            properties.setProperty("permissionLevel", "4");
            properties.setProperty("warningIndicator", "both");
            properties.setProperty("warningColor", "yellow");
            properties.setProperty("restartWarningMessage", "[SERVER] Restart in {seconds} {second_word}.");
            properties.setProperty("restartNowMessage", "[SERVER] Restarting now.");
            properties.setProperty("restartScheduledMessage", "Restart scheduled in {seconds} {second_word}.");
            properties.setProperty("shutdownWarningMessage", "[SERVER] Shutdown in {seconds} {second_word}.");
            properties.setProperty("shutdownNowMessage", "[SERVER] Shutting down now.");
            properties.setProperty("shutdownScheduledMessage", "Shutdown scheduled in {seconds} {second_word}.");
            return properties;
        }

        private String warningMessage(Operation operation, int seconds) {
            return format(operation == Operation.RESTART ? restartWarningMessage : shutdownWarningMessage, operation, seconds);
        }

        private String nowMessage(Operation operation) {
            return format(operation == Operation.RESTART ? restartNowMessage : shutdownNowMessage, operation, 0);
        }

        private String scheduledMessage(Operation operation, int seconds) {
            return format(operation == Operation.RESTART ? restartScheduledMessage : shutdownScheduledMessage, operation, seconds);
        }

        private static String format(String template, Operation operation, int seconds) {
            String secondsText = Integer.toString(seconds);
            String secondWord = seconds == 1 ? "second" : "seconds";
            return template
                .replace("{seconds}", secondsText)
                .replace("{second_word}", secondWord)
                .replace("{operation}", operation.configName);
        }

        private static int readInt(Properties properties, String key, int fallback, int min, int max) {
            try {
                int value = Integer.parseInt(properties.getProperty(key, Integer.toString(fallback)).trim());
                return Math.max(min, Math.min(max, value));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }

        private static boolean readBoolean(Properties properties, String key, boolean fallback) {
            String value = properties.getProperty(key);
            if (value == null || value.isBlank()) {
                return fallback;
            }
            return Boolean.parseBoolean(value.trim());
        }

        private static WarningIndicator readWarningIndicator(Properties properties, String key, WarningIndicator fallback) {
            String value = properties.getProperty(key);
            if (value == null || value.isBlank()) {
                return fallback;
            }

            try {
                return WarningIndicator.valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
            } catch (IllegalArgumentException ignored) {
                return fallback;
            }
        }

        private static ChatFormatting readWarningColor(Properties properties, String key, ChatFormatting fallback) {
            String value = properties.getProperty(key);
            if (value == null || value.isBlank()) {
                return fallback;
            }

            try {
                return ChatFormatting.valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
            } catch (IllegalArgumentException ignored) {
                return fallback;
            }
        }
    }
}
