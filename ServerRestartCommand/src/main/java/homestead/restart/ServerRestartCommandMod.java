package homestead.restart;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
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
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ServerRestartCommandMod implements ModInitializer {
    private static final String MOD_NAME = "Server Restart Command";
    private static final AtomicBoolean RESTART_SCHEDULED = new AtomicBoolean(false);
    private static final Path DEFAULT_RAM_FLAG = Path.of("config", "server-restart-command-default-ram.flag");

    private static RestartConfig config;

    @Override
    public void onInitialize() {
        config = RestartConfig.load();
        var permission = new PermissionCheck.Require(new Permission.HasCommandLevel(PermissionLevel.byId(config.permissionLevel)));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(restartCommand("restartserver", permission));
            dispatcher.register(restartCommand("restart", permission));
        });

        ServerLifecycleEvents.SERVER_STARTED.register(ServerRestartCommandMod::warnIfDefaultRamFallbackWasUsed);
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> restartCommand(
        String name,
        PermissionCheck permission
    ) {
        return Commands.literal(name)
            .requires(Commands.hasPermission(permission))
            .executes(context -> scheduleRestart(context.getSource(), config.defaultDelaySeconds))
            .then(Commands.argument("delay_seconds", IntegerArgumentType.integer(0, 3600))
                .executes(context -> scheduleRestart(
                    context.getSource(),
                    IntegerArgumentType.getInteger(context, "delay_seconds")
                )));
    }

    private static int scheduleRestart(CommandSourceStack source, int delaySeconds) {
        MinecraftServer server = source.getServer();

        if (!RESTART_SCHEDULED.compareAndSet(false, true)) {
            source.sendFailure(Component.literal(MOD_NAME + ": a restart is already scheduled."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(MOD_NAME + ": restart scheduled in " + delaySeconds + " seconds."), true);

        Thread restartThread = new Thread(
            () -> runRestart(server, delaySeconds),
            "server-restart-command"
        );
        restartThread.setDaemon(false);
        restartThread.start();
        return 1;
    }

    private static void runRestart(MinecraftServer server, int delaySeconds) {
        try {
            announceCountdown(server, delaySeconds);
            startRestartLauncher();
            server.execute(() -> {
                broadcast(server, "Restarting server now.");
                server.halt(false);
            });
        } catch (Exception exception) {
            RESTART_SCHEDULED.set(false);
            server.execute(() -> {
                broadcast(server, "Restart failed before shutdown. The server is still running. Check restart-server-restart.log and the console for details: " + exception.getMessage());
                server.sendSystemMessage(Component.literal("[Server Restart Command] Restart launcher error:"));
                server.sendSystemMessage(Component.literal(stackTrace(exception)));
            });
        }
    }

    private static void announceCountdown(MinecraftServer server, int delaySeconds) throws InterruptedException {
        if (delaySeconds <= 0) {
            return;
        }

        for (int remaining = delaySeconds; remaining > 0; remaining--) {
            if (shouldAnnounce(remaining, delaySeconds)) {
                int seconds = remaining;
                server.execute(() -> broadcast(server, "Server restarting in " + seconds + " seconds."));
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
        Path defaultRamFlag = DEFAULT_RAM_FLAG.toAbsolutePath().normalize();
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
            defaultRamFlag.toString()
        );

        new ProcessBuilder(command)
            .directory(serverDir.toFile())
            .start();
    }

    private static void broadcast(MinecraftServer server, String message) {
        server.getPlayerList().broadcastSystemMessage(Component.literal("[Server] " + message), false);
    }

    private static String stackTrace(Exception exception) {
        StringWriter stackTrace = new StringWriter();
        exception.printStackTrace(new PrintWriter(stackTrace));
        return stackTrace.toString();
    }

    private static void warnIfDefaultRamFallbackWasUsed(MinecraftServer server) {
        if (!Files.isRegularFile(DEFAULT_RAM_FLAG)) {
            return;
        }

        Thread warningThread = new Thread(() -> {
            try {
                Thread.sleep(config.defaultRamWarningDelaySeconds * 1000L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }

            server.execute(() -> {
                Component warning = Component.literal(
                    "[Server Restart Command] WARNING: no custom restart launchCommand/start.bat/start.sh was available, so the server restarted with the default 4G RAM command. Edit config/server-restart-command.properties and set launchCommand to your real RAM setting."
                ).withStyle(ChatFormatting.YELLOW);

                server.sendSystemMessage(warning);
                sendWarningToOnlineOps(server, warning);

                try {
                    Files.deleteIfExists(DEFAULT_RAM_FLAG);
                } catch (IOException exception) {
                    server.sendSystemMessage(Component.literal("[Server Restart Command] Could not clear default-RAM warning flag: " + exception.getMessage()));
                }
            });
        }, "server-restart-command-default-ram-warning");

        warningThread.setDaemon(true);
        warningThread.start();
    }

    private static void sendWarningToOnlineOps(MinecraftServer server, Component warning) {
        try {
            Object playerList = server.getPlayerList();
            Method getPlayers = playerList.getClass().getMethod("getPlayers");
            Method isOp = null;

            for (Object player : (List<?>) getPlayers.invoke(playerList)) {
                Object nameAndId = player.getClass().getMethod("nameAndId").invoke(player);
                if (isOp == null) {
                    isOp = playerList.getClass().getMethod("isOp", nameAndId.getClass());
                }

                boolean operator = (Boolean) isOp.invoke(playerList, nameAndId);
                if (operator) {
                    player.getClass().getMethod("sendSystemMessage", Component.class).invoke(player, warning);
                }
            }
        } catch (ReflectiveOperationException exception) {
            server.sendSystemMessage(Component.literal("[Server Restart Command] Could not send default-RAM warning to online ops: " + exception.getMessage()));
        }
    }

    private record RestartConfig(String launchCommand, boolean preferStartScript, int defaultDelaySeconds, int permissionLevel, int defaultRamWarningDelaySeconds) {
        private static final Path CONFIG_PATH = Path.of("config", "server-restart-command.properties");

        private static RestartConfig load() {
            Properties properties = new Properties();
            properties.setProperty("launchCommand", "");
            properties.setProperty("preferStartScript", "true");
            properties.setProperty("defaultDelaySeconds", "5");
            properties.setProperty("permissionLevel", "4");
            properties.setProperty("defaultRamWarningDelaySeconds", "60");

            try {
                if (Files.exists(CONFIG_PATH)) {
                    try (InputStream input = Files.newInputStream(CONFIG_PATH)) {
                        properties.load(input);
                    }
                } else {
                    Files.createDirectories(CONFIG_PATH.getParent());
                    try (OutputStream output = Files.newOutputStream(CONFIG_PATH)) {
                        properties.store(output, "Server Restart Command configuration");
                    }
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Could not load " + CONFIG_PATH, exception);
            }

            return new RestartConfig(
                properties.getProperty("launchCommand", ""),
                readBoolean(properties, "preferStartScript", true),
                readInt(properties, "defaultDelaySeconds", 5, 0, 3600),
                readInt(properties, "permissionLevel", 4, 0, 4),
                readInt(properties, "defaultRamWarningDelaySeconds", 60, 0, 300)
            );
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
    }
}
