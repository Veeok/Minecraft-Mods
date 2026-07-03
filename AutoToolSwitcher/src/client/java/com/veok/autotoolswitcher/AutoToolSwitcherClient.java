package com.veok.autotoolswitcher;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.function.BooleanSupplier;

public final class AutoToolSwitcherClient implements ClientModInitializer {
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("autotoolswitcher.properties");
    private static final float SWITCH_THRESHOLD = 0.001F;
    private static final int MANUAL_OVERRIDE_COOLDOWN_TICKS = 10;

    static final boolean DEFAULT_ENABLED = true;
    static final boolean DEFAULT_SWITCH_FOR_BLOCKS_ON_LOOK = false;
    static final boolean DEFAULT_SWITCH_FOR_BLOCKS_WHILE_MINING = true;
    static final boolean DEFAULT_SWITCH_FOR_HOSTILE_MOBS = true;
    static final boolean DEFAULT_RESTORE_PREVIOUS_SLOT = true;
    static final boolean DEFAULT_PAUSE_WHILE_DROP_KEY_DOWN = true;

    static boolean enabled = DEFAULT_ENABLED;
    static boolean switchForBlocksOnLook = DEFAULT_SWITCH_FOR_BLOCKS_ON_LOOK;
    static boolean switchForBlocksWhileMining = DEFAULT_SWITCH_FOR_BLOCKS_WHILE_MINING;
    static boolean switchForHostileMobs = DEFAULT_SWITCH_FOR_HOSTILE_MOBS;
    static boolean restorePreviousSlot = DEFAULT_RESTORE_PREVIOUS_SLOT;
    static boolean pauseWhileDropKeyDown = DEFAULT_PAUSE_WHILE_DROP_KEY_DOWN;

    private static int autoSelectedSlot = -1;
    private static int restoreSlot = -1;
    private static int manualOverrideCooldown = 0;

    @Override
    public void onInitializeClient() {
        loadConfig();
        registerCommand();
        ClientTickEvents.END_CLIENT_TICK.register(AutoToolSwitcherClient::tick);
    }

    private static void registerCommand() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
            ClientCommands.literal("autotoolswitcher")
                .executes(context -> {
                    sendStatus();
                    return 1;
                })
                .then(ClientCommands.literal("status")
                    .executes(context -> {
                        sendStatus();
                        return 1;
                    }))
                .then(ClientCommands.literal("help")
                    .executes(context -> {
                        sendHelp();
                        return 1;
                    }))
                .then(ClientCommands.literal("on")
                    .executes(context -> setEnabled(true)))
                .then(ClientCommands.literal("off")
                    .executes(context -> setEnabled(false)))
                .then(ClientCommands.literal("toggle")
                    .executes(context -> setEnabled(!enabled)))
                .then(booleanCommand("look", () -> switchForBlocksOnLook, value -> switchForBlocksOnLook = value, "Block look switching"))
                .then(booleanCommand("mine", () -> switchForBlocksWhileMining, value -> switchForBlocksWhileMining = value, "Block mining switching"))
                .then(booleanCommand("attack", () -> switchForHostileMobs, value -> switchForHostileMobs = value, "Hostile mob sword switching"))
                .then(booleanCommand("restore", () -> restorePreviousSlot, value -> restorePreviousSlot = value, "Restore previous slot"))
                .then(booleanCommand("drop-safe", () -> pauseWhileDropKeyDown, value -> pauseWhileDropKeyDown = value, "Drop safety"))
                .then(ClientCommands.literal("reset")
                    .executes(context -> resetDefaults()))
        ));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> booleanCommand(
        String name,
        BooleanSupplier getter,
        BooleanSettingSetter setter,
        String displayName
    ) {
        return ClientCommands.literal(name)
            .executes(context -> {
                sendSettingStatus(displayName, getter.getAsBoolean());
                return 1;
            })
            .then(ClientCommands.literal("on")
                .executes(context -> setBooleanSetting(displayName, true, setter)))
            .then(ClientCommands.literal("off")
                .executes(context -> setBooleanSetting(displayName, false, setter)))
            .then(ClientCommands.literal("toggle")
                .executes(context -> setBooleanSetting(displayName, !getter.getAsBoolean(), setter)));
    }

    private static int setEnabled(boolean value) {
        enabled = value;
        if (!enabled) {
            finishAutoSwitch(Minecraft.getInstance());
        }
        saveConfig();
        sendStatus();
        return 1;
    }

    private static int setBooleanSetting(String displayName, boolean value, BooleanSettingSetter setter) {
        setter.set(value);
        applyAndSaveConfig();
        sendSettingStatus(displayName, value);
        return 1;
    }

    private static int resetDefaults() {
        resetSettingsToDefaults();
        finishAutoSwitch(Minecraft.getInstance());
        saveConfig();
        sendStatus();
        return 1;
    }

    static void applyAndSaveConfig() {
        if (!enabled || !hasAnyTriggerEnabled()) {
            finishAutoSwitch(Minecraft.getInstance());
        }
        saveConfig();
    }

    static void resetSettingsToDefaults() {
        enabled = DEFAULT_ENABLED;
        switchForBlocksOnLook = DEFAULT_SWITCH_FOR_BLOCKS_ON_LOOK;
        switchForBlocksWhileMining = DEFAULT_SWITCH_FOR_BLOCKS_WHILE_MINING;
        switchForHostileMobs = DEFAULT_SWITCH_FOR_HOSTILE_MOBS;
        restorePreviousSlot = DEFAULT_RESTORE_PREVIOUS_SLOT;
        pauseWhileDropKeyDown = DEFAULT_PAUSE_WHILE_DROP_KEY_DOWN;
    }

    private static boolean hasAnyTriggerEnabled() {
        return switchForBlocksOnLook || switchForBlocksWhileMining || switchForHostileMobs;
    }

    private static void sendStatus() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }

        client.player.sendSystemMessage(Component.literal("Auto Tool Switcher").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        sendStatusLine(client, "Global", enabled);
        sendStatusLine(client, "Look at block = tool", switchForBlocksOnLook);
        sendStatusLine(client, "Mine block = tool", switchForBlocksWhileMining);
        sendStatusLine(client, "Hostile mob = sword", switchForHostileMobs);
        sendStatusLine(client, "Switch back after target", restorePreviousSlot);
        sendStatusLine(client, "Pause while dropping items", pauseWhileDropKeyDown);
        client.player.sendSystemMessage(Component.literal("Use Mod Menu > Auto Tool Switcher for clickable settings.").withStyle(ChatFormatting.GRAY));

        if (enabled && !hasAnyTriggerEnabled()) {
            client.player.sendSystemMessage(Component.literal("No trigger modes are enabled. Turn on look, mine, or attack.").withStyle(ChatFormatting.YELLOW));
        }
    }

    private static void sendHelp() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }

        client.player.sendSystemMessage(Component.literal("Auto Tool Switcher Help").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        client.player.sendSystemMessage(Component.literal("Best way: open Mod Menu > Auto Tool Switcher for clickable settings.").withStyle(ChatFormatting.GRAY));
        client.player.sendSystemMessage(Component.literal("Quick commands use: /autotoolswitcher <setting> on|off|toggle").withStyle(ChatFormatting.GRAY));
        sendHelpLine(client, "on/off/toggle", "master switch for the whole mod");
        sendHelpLine(client, "look", "switch tools just by looking at blocks; useful, but can get in the way");
        sendHelpLine(client, "mine", "switch tools only while breaking a block");
        sendHelpLine(client, "attack", "swap to your best sword when aiming at a hostile mob");
        sendHelpLine(client, "restore", "return to your previous slot after the block or mob is gone");
        sendHelpLine(client, "drop-safe", "pause switching while your drop key is held");
        sendHelpLine(client, "status", "show your current settings");
        sendHelpLine(client, "reset", "go back to the default setup");
        client.player.sendSystemMessage(Component.literal("Example: /autotoolswitcher look off").withStyle(ChatFormatting.YELLOW));
    }

    private static void sendHelpLine(Minecraft client, String setting, String description) {
        client.player.sendSystemMessage(
            Component.literal("/autotoolswitcher " + setting).withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" - " + description).withStyle(ChatFormatting.GRAY))
        );
    }

    private static void sendStatusLine(Minecraft client, String displayName, boolean value) {
        ChatFormatting color = value ? ChatFormatting.GREEN : ChatFormatting.RED;
        client.player.sendSystemMessage(Component.literal(displayName + ": " + stateText(value)).withStyle(color));
    }

    private static void sendSettingStatus(String displayName, boolean value) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }

        ChatFormatting color = value ? ChatFormatting.GREEN : ChatFormatting.RED;
        client.player.sendOverlayMessage(Component.literal(displayName + ": " + stateText(value)).withStyle(color));
    }

    private static String stateText(boolean value) {
        return value ? "ON" : "OFF";
    }

    private static void tick(Minecraft client) {
        if (!enabled || client.player == null || client.level == null || client.gameMode == null) {
            finishAutoSwitch(client);
            return;
        }

        if (client.player.isCreative() || client.player.isSpectator()) {
            finishAutoSwitch(client);
            return;
        }

        if (wasManuallyOverridden(client)) {
            return;
        }

        if (manualOverrideCooldown > 0) {
            manualOverrideCooldown--;
            return;
        }

        if (pauseWhileDropKeyDown && client.options.keyDrop.isDown()) {
            finishAutoSwitch(client);
            return;
        }

        int selectedSlot = client.player.getInventory().getSelectedSlot();
        int bestSlot = findBestCombatSlot(client);
        if (bestSlot < 0) {
            bestSlot = findBestBlockSlot(client, selectedSlot);
        }

        if (bestSlot < 0) {
            finishAutoSwitch(client);
            return;
        }

        switchToSlot(client, bestSlot);
    }

    private static boolean wasManuallyOverridden(Minecraft client) {
        if (autoSelectedSlot < 0 || client.player == null) {
            return false;
        }

        int selectedSlot = client.player.getInventory().getSelectedSlot();
        if (selectedSlot == autoSelectedSlot) {
            return false;
        }

        clearAutoSwitch();
        manualOverrideCooldown = MANUAL_OVERRIDE_COOLDOWN_TICKS;
        return true;
    }

    private static int findBestCombatSlot(Minecraft client) {
        if (!switchForHostileMobs || !(client.hitResult instanceof EntityHitResult entityHit) || entityHit.getType() != HitResult.Type.ENTITY) {
            return -1;
        }

        Entity entity = entityHit.getEntity();
        if (!(entity instanceof Enemy) || !(entity instanceof LivingEntity living) || !entity.isAttackable() || !living.isAlive() || living.isDeadOrDying()) {
            return -1;
        }

        return findBestSwordSlot(client);
    }

    private static int findBestBlockSlot(Minecraft client, int selectedSlot) {
        boolean canSwitchForBlock = switchForBlocksOnLook || switchForBlocksWhileMining && client.options.keyAttack.isDown();
        if (!canSwitchForBlock || !(client.hitResult instanceof BlockHitResult blockHit) || blockHit.getType() != HitResult.Type.BLOCK) {
            return -1;
        }

        BlockState state = client.level.getBlockState(blockHit.getBlockPos());
        if (state.isAir()) {
            return -1;
        }

        ItemStack current = client.player.getInventory().getItem(selectedSlot);
        float bestScore = blockScore(client, current, state);
        int bestSlot = selectedSlot;

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = client.player.getInventory().getItem(slot);
            float score = blockScore(client, stack, state);
            if (score > bestScore + SWITCH_THRESHOLD) {
                bestScore = score;
                bestSlot = slot;
            }
        }

        return bestSlot;
    }

    private static int findBestSwordSlot(Minecraft client) {
        double bestScore = 0.0D;
        int bestSlot = -1;

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = client.player.getInventory().getItem(slot);
            double score = swordScore(client, stack);
            if (score > bestScore + SWITCH_THRESHOLD) {
                bestScore = score;
                bestSlot = slot;
            }
        }

        return bestSlot;
    }

    private static float blockScore(Minecraft client, ItemStack stack, BlockState state) {
        if (stack.isEmpty() || isOneHitFromBreaking(stack)) {
            return 0.0F;
        }

        float speed = stack.getDestroySpeed(state);
        if (speed <= 1.0F) {
            return speed;
        }

        if (state.requiresCorrectToolForDrops() && !stack.isCorrectToolForDrops(state)) {
            return 0.0F;
        }

        int efficiencyLevel = getEnchantmentLevel(client, stack, Enchantments.EFFICIENCY);
        if (efficiencyLevel > 0) {
            speed += efficiencyLevel * efficiencyLevel + 1.0F;
        }

        return speed + (stack.isCorrectToolForDrops(state) ? 0.1F : 0.0F);
    }

    private static double swordScore(Minecraft client, ItemStack stack) {
        if (stack.isEmpty() || isOneHitFromBreaking(stack) || !isSword(stack)) {
            return 0.0D;
        }

        double[] attackDamage = {0.0D};
        stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.is(Attributes.ATTACK_DAMAGE)) {
                attackDamage[0] += modifier.amount();
            }
        });

        int sharpnessLevel = getEnchantmentLevel(client, stack, Enchantments.SHARPNESS);
        if (sharpnessLevel > 0) {
            attackDamage[0] += 0.5D * sharpnessLevel + 0.5D;
        }

        return attackDamage[0];
    }

    private static boolean isSword(ItemStack stack) {
        return stack.is(holder -> holder.is(ItemTags.SWORDS));
    }

    private static int getEnchantmentLevel(Minecraft client, ItemStack stack, ResourceKey<Enchantment> enchantmentKey) {
        Holder<Enchantment> enchantment = client.level.registryAccess()
            .lookupOrThrow(Registries.ENCHANTMENT)
            .getOrThrow(enchantmentKey);
        return EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack);
    }

    private static boolean isOneHitFromBreaking(ItemStack stack) {
        return stack.isDamageableItem() && stack.getDamageValue() >= stack.getMaxDamage() - 1;
    }

    private static void switchToSlot(Minecraft client, int slot) {
        if (slot < 0 || slot > 8 || client.player == null) {
            return;
        }

        int selectedSlot = client.player.getInventory().getSelectedSlot();
        if (selectedSlot == slot) {
            if (autoSelectedSlot == slot) {
                return;
            }

            clearAutoSwitch();
            return;
        }

        if (restoreSlot < 0 || selectedSlot != autoSelectedSlot) {
            restoreSlot = selectedSlot;
        }

        setSelectedSlot(client, slot);
        autoSelectedSlot = slot;
    }

    private static void finishAutoSwitch(Minecraft client) {
        if (client.player != null && restorePreviousSlot && autoSelectedSlot >= 0 && restoreSlot >= 0) {
            int selectedSlot = client.player.getInventory().getSelectedSlot();
            if (selectedSlot == autoSelectedSlot && restoreSlot != selectedSlot) {
                setSelectedSlot(client, restoreSlot);
            }
        }

        clearAutoSwitch();
    }

    private static void clearAutoSwitch() {
        autoSelectedSlot = -1;
        restoreSlot = -1;
    }

    private static void setSelectedSlot(Minecraft client, int slot) {
        client.player.getInventory().setSelectedSlot(slot);
        ClientPacketListener connection = client.getConnection();
        if (connection != null) {
            connection.send(new ServerboundSetCarriedItemPacket(slot));
        }
    }

    private static void loadConfig() {
        Properties properties = new Properties();
        if (Files.exists(CONFIG_PATH)) {
            try (InputStream input = Files.newInputStream(CONFIG_PATH)) {
                properties.load(input);
                enabled = getBoolean(properties, "enabled", true);
                switchForBlocksOnLook = getBoolean(properties, "switchForBlocksOnLook", false);
                switchForBlocksWhileMining = getBoolean(properties, "switchForBlocksWhileMining", true);
                switchForHostileMobs = getBoolean(properties, "switchForHostileMobs", true);
                restorePreviousSlot = getBoolean(properties, "restorePreviousSlot", true);
                pauseWhileDropKeyDown = getBoolean(properties, "pauseWhileDropKeyDown", true);
            } catch (IOException ignored) {
                loadDefaults();
            }
        }
    }

    private static void loadDefaults() {
        resetSettingsToDefaults();
    }

    private static boolean getBoolean(Properties properties, String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        return value == null ? defaultValue : Boolean.parseBoolean(value);
    }

    static void saveConfig() {
        Properties properties = new Properties();
        properties.setProperty("enabled", Boolean.toString(enabled));
        properties.setProperty("switchForBlocksOnLook", Boolean.toString(switchForBlocksOnLook));
        properties.setProperty("switchForBlocksWhileMining", Boolean.toString(switchForBlocksWhileMining));
        properties.setProperty("switchForHostileMobs", Boolean.toString(switchForHostileMobs));
        properties.setProperty("restorePreviousSlot", Boolean.toString(restorePreviousSlot));
        properties.setProperty("pauseWhileDropKeyDown", Boolean.toString(pauseWhileDropKeyDown));
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (OutputStream output = Files.newOutputStream(CONFIG_PATH)) {
                properties.store(output, "Auto Tool Switcher client config");
            }
        } catch (IOException ignored) {
            // The current session still uses the new values if saving fails.
        }
    }

    private interface BooleanSettingSetter {
        void set(boolean value);
    }
}
