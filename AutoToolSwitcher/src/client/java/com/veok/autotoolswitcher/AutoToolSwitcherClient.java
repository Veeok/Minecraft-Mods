package com.veok.autotoolswitcher;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Properties;
import java.util.function.BooleanSupplier;

public final class AutoToolSwitcherClient implements ClientModInitializer {
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("autotoolswitcher.properties");
    private static final float SWITCH_THRESHOLD = 0.001F;
    private static final int MANUAL_OVERRIDE_COOLDOWN_TICKS = 10;
    private static final int HOTBAR_SIZE = 9;
    private static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("autotoolswitcher", "controls"));

    static final boolean DEFAULT_ENABLED = true;
    static final boolean DEFAULT_SWITCH_FOR_BLOCKS_ON_LOOK = false;
    static final boolean DEFAULT_SWITCH_FOR_BLOCKS_WHILE_MINING = true;
    static final boolean DEFAULT_SWITCH_FOR_HOSTILE_MOBS = true;
    static final boolean DEFAULT_RESTORE_PREVIOUS_SLOT = true;
    static final boolean DEFAULT_PAUSE_WHILE_DROP_KEY_DOWN = true;
    static final boolean DEFAULT_DURABILITY_SAFETY = true;
    static final boolean DEFAULT_SWITCH_FOR_RIGHT_CLICK_BLOCKS = false;
    static final boolean DEFAULT_ALLOW_AXES_FOR_COMBAT = false;
    static final boolean DEFAULT_ALLOW_MACES_FOR_COMBAT = false;
    static final boolean DEFAULT_SWITCH_FOR_RANGED_COMBAT = false;
    static final boolean DEFAULT_SHOW_HUD_INDICATOR = false;
    static final int DEFAULT_MIN_DURABILITY_LEFT = 1;
    static final int DEFAULT_RESTORE_DELAY_TICKS = 8;
    static final int DEFAULT_RANGED_COMBAT_DISTANCE = 8;
    static final MiningPreference DEFAULT_MINING_PREFERENCE = MiningPreference.SPEED;

    static boolean enabled = DEFAULT_ENABLED;
    static boolean switchForBlocksOnLook = DEFAULT_SWITCH_FOR_BLOCKS_ON_LOOK;
    static boolean switchForBlocksWhileMining = DEFAULT_SWITCH_FOR_BLOCKS_WHILE_MINING;
    static boolean switchForHostileMobs = DEFAULT_SWITCH_FOR_HOSTILE_MOBS;
    static boolean restorePreviousSlot = DEFAULT_RESTORE_PREVIOUS_SLOT;
    static boolean pauseWhileDropKeyDown = DEFAULT_PAUSE_WHILE_DROP_KEY_DOWN;
    static boolean durabilitySafety = DEFAULT_DURABILITY_SAFETY;
    static boolean switchForRightClickBlocks = DEFAULT_SWITCH_FOR_RIGHT_CLICK_BLOCKS;
    static boolean allowAxesForCombat = DEFAULT_ALLOW_AXES_FOR_COMBAT;
    static boolean allowMacesForCombat = DEFAULT_ALLOW_MACES_FOR_COMBAT;
    static boolean switchForRangedCombat = DEFAULT_SWITCH_FOR_RANGED_COMBAT;
    static boolean showHudIndicator = DEFAULT_SHOW_HUD_INDICATOR;
    static int minDurabilityLeft = DEFAULT_MIN_DURABILITY_LEFT;
    static int restoreDelayTicks = DEFAULT_RESTORE_DELAY_TICKS;
    static int rangedCombatDistance = DEFAULT_RANGED_COMBAT_DISTANCE;
    static MiningPreference miningPreference = DEFAULT_MINING_PREFERENCE;
    static boolean[] allowedHotbarSlots = defaultAllowedSlots();

    private static KeyMapping toggleEnabledKey;
    private static KeyMapping toggleLookKey;
    private static KeyMapping toggleMineKey;
    private static KeyMapping toggleCombatKey;
    private static KeyMapping holdPauseKey;

    private static int autoSelectedSlot = -1;
    private static int restoreSlot = -1;
    private static int manualOverrideCooldown = 0;
    private static int targetMissingTicks = 0;

    @Override
    public void onInitializeClient() {
        loadConfig();
        registerKeyMappings();
        registerCommand();
        ClientTickEvents.END_CLIENT_TICK.register(AutoToolSwitcherClient::tick);
    }

    private static void registerKeyMappings() {
        toggleEnabledKey = registerUnboundKey("key.autotoolswitcher.toggle_enabled");
        toggleLookKey = registerUnboundKey("key.autotoolswitcher.toggle_look");
        toggleMineKey = registerUnboundKey("key.autotoolswitcher.toggle_mine");
        toggleCombatKey = registerUnboundKey("key.autotoolswitcher.toggle_combat");
        holdPauseKey = registerUnboundKey("key.autotoolswitcher.hold_pause");
    }

    private static KeyMapping registerUnboundKey(String translationKey) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(
            translationKey,
            InputConstants.UNKNOWN.getType(),
            InputConstants.UNKNOWN.getValue(),
            KEY_CATEGORY
        ));
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
                .then(booleanCommand("durability", () -> durabilitySafety, value -> durabilitySafety = value, "Durability safety"))
                .then(booleanCommand("right-click", () -> switchForRightClickBlocks, value -> switchForRightClickBlocks = value, "Right-click tools"))
                .then(booleanCommand("axe-combat", () -> allowAxesForCombat, value -> allowAxesForCombat = value, "Combat axes"))
                .then(booleanCommand("mace-combat", () -> allowMacesForCombat, value -> allowMacesForCombat = value, "Combat maces"))
                .then(booleanCommand("ranged", () -> switchForRangedCombat, value -> switchForRangedCombat = value, "Ranged combat"))
                .then(booleanCommand("hud", () -> showHudIndicator, value -> showHudIndicator = value, "HUD indicator"))
                .then(ClientCommands.literal("profile")
                    .then(profileCommand("default", ProfilePreset.DEFAULT))
                    .then(profileCommand("mining", ProfilePreset.MINING))
                    .then(profileCommand("building", ProfilePreset.BUILDING))
                    .then(profileCommand("combat", ProfilePreset.COMBAT))
                    .then(profileCommand("minimal", ProfilePreset.MINIMAL)))
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

    private static LiteralArgumentBuilder<FabricClientCommandSource> profileCommand(String name, ProfilePreset profile) {
        return ClientCommands.literal(name)
            .executes(context -> {
                applyProfile(profile);
                applyAndSaveConfig();
                sendSystemMessage("Applied profile: " + profile.displayName(), ChatFormatting.GREEN);
                return 1;
            });
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
        clampSettings();
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
        durabilitySafety = DEFAULT_DURABILITY_SAFETY;
        switchForRightClickBlocks = DEFAULT_SWITCH_FOR_RIGHT_CLICK_BLOCKS;
        allowAxesForCombat = DEFAULT_ALLOW_AXES_FOR_COMBAT;
        allowMacesForCombat = DEFAULT_ALLOW_MACES_FOR_COMBAT;
        switchForRangedCombat = DEFAULT_SWITCH_FOR_RANGED_COMBAT;
        showHudIndicator = DEFAULT_SHOW_HUD_INDICATOR;
        minDurabilityLeft = DEFAULT_MIN_DURABILITY_LEFT;
        restoreDelayTicks = DEFAULT_RESTORE_DELAY_TICKS;
        rangedCombatDistance = DEFAULT_RANGED_COMBAT_DISTANCE;
        miningPreference = DEFAULT_MINING_PREFERENCE;
        allowedHotbarSlots = defaultAllowedSlots();
    }

    static void applyProfile(ProfilePreset profile) {
        if (profile == ProfilePreset.CUSTOM) {
            return;
        }

        resetSettingsToDefaults();
        switch (profile) {
            case DEFAULT -> {
            }
            case MINING -> {
                switchForBlocksWhileMining = true;
                miningPreference = MiningPreference.FORTUNE;
                restoreDelayTicks = 10;
                switchForHostileMobs = false;
            }
            case BUILDING -> {
                switchForBlocksWhileMining = false;
                switchForRightClickBlocks = true;
                restoreDelayTicks = 12;
            }
            case COMBAT -> {
                switchForBlocksWhileMining = false;
                switchForHostileMobs = true;
                allowAxesForCombat = true;
                allowMacesForCombat = true;
                switchForRangedCombat = true;
                restoreDelayTicks = 12;
            }
            case MINIMAL -> {
                switchForBlocksWhileMining = true;
                switchForHostileMobs = false;
                restorePreviousSlot = true;
                restoreDelayTicks = 6;
            }
        }
    }

    private static boolean hasAnyTriggerEnabled() {
        return switchForBlocksOnLook || switchForBlocksWhileMining || switchForHostileMobs || switchForRightClickBlocks || switchForRangedCombat;
    }

    private static void sendStatus() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }

        client.player.sendSystemMessage(Component.literal("Auto Tool Switcher").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        sendStatusLine(client, "Global", enabled);
        sendStatusLine(client, "Mine block = tool", switchForBlocksWhileMining);
        sendStatusLine(client, "Look at block = tool", switchForBlocksOnLook);
        sendStatusLine(client, "Hostile mob = melee", switchForHostileMobs);
        sendStatusLine(client, "Right-click tools", switchForRightClickBlocks);
        sendStatusLine(client, "Ranged combat", switchForRangedCombat);
        sendStatusValue(client, "Allowed slots", allowedSlotsText());
        sendStatusValue(client, "Durability safety", durabilitySafety ? "ON, keep " + minDurabilityLeft + "+ durability" : "OFF");
        sendStatusValue(client, "Restore", restorePreviousSlot ? "ON, delay " + restoreDelayTicks + " ticks" : "OFF");
        sendStatusValue(client, "Mining preference", miningPreference.displayName());
        client.player.sendSystemMessage(Component.literal("Use Mod Menu > Auto Tool Switcher for full settings.").withStyle(ChatFormatting.GRAY));

        if (enabled && !hasAnyTriggerEnabled()) {
            client.player.sendSystemMessage(Component.literal("No trigger modes are enabled. Turn on mine, attack, look, right-click, or ranged.").withStyle(ChatFormatting.YELLOW));
        }
    }

    private static void sendHelp() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }

        client.player.sendSystemMessage(Component.literal("Auto Tool Switcher Help").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        client.player.sendSystemMessage(Component.literal("Best way: Mod Menu > Auto Tool Switcher. Advanced settings live there.").withStyle(ChatFormatting.GRAY));
        client.player.sendSystemMessage(Component.literal("Quick commands: /autotoolswitcher <setting> on|off|toggle").withStyle(ChatFormatting.GRAY));
        sendHelpLine(client, "on/off/toggle", "master switch");
        sendHelpLine(client, "mine", "switch tools only while breaking a block");
        sendHelpLine(client, "look", "switch tools just by looking at blocks");
        sendHelpLine(client, "attack", "swap to melee for hostile mobs");
        sendHelpLine(client, "right-click", "optional axe/hoe/shovel/shears helper for block use");
        sendHelpLine(client, "ranged", "optional bow/crossbow helper for distant hostile mobs");
        sendHelpLine(client, "restore", "switch back after the target is gone");
        sendHelpLine(client, "durability", "skip tools below your configured durability limit");
        sendHelpLine(client, "hud", "show a small overlay when the mod switches slots");
        sendHelpLine(client, "profile mining|building|combat|minimal|default", "apply a preset");
        client.player.sendSystemMessage(Component.literal("Examples: /autotoolswitcher look off, /autotoolswitcher profile combat").withStyle(ChatFormatting.YELLOW));
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

    private static void sendStatusValue(Minecraft client, String displayName, String value) {
        client.player.sendSystemMessage(Component.literal(displayName + ": " + value).withStyle(ChatFormatting.GRAY));
    }

    private static void sendSettingStatus(String displayName, boolean value) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }

        ChatFormatting color = value ? ChatFormatting.GREEN : ChatFormatting.RED;
        client.player.sendOverlayMessage(Component.literal(displayName + ": " + stateText(value)).withStyle(color));
    }

    private static void sendSystemMessage(String message, ChatFormatting color) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal(message).withStyle(color));
        }
    }

    private static String stateText(boolean value) {
        return value ? "ON" : "OFF";
    }

    private static void tick(Minecraft client) {
        processKeyMappings(client);

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

        if (holdPauseKey.isDown() || pauseWhileDropKeyDown && client.options.keyDrop.isDown()) {
            finishAutoSwitch(client);
            return;
        }

        int selectedSlot = client.player.getInventory().getSelectedSlot();
        AutoSwitchTarget target = findBestTarget(client, selectedSlot);
        if (target == null) {
            finishAutoSwitchWhenIdle(client);
            return;
        }

        targetMissingTicks = 0;
        switchToSlot(client, target.slot(), target.reason());
    }

    private static void processKeyMappings(Minecraft client) {
        if (client.player == null) {
            return;
        }

        while (toggleEnabledKey.consumeClick()) {
            enabled = !enabled;
            applyAndSaveConfig();
            sendSettingStatus("Auto Tool Switcher", enabled);
        }

        while (toggleLookKey.consumeClick()) {
            switchForBlocksOnLook = !switchForBlocksOnLook;
            applyAndSaveConfig();
            sendSettingStatus("Block look switching", switchForBlocksOnLook);
        }

        while (toggleMineKey.consumeClick()) {
            switchForBlocksWhileMining = !switchForBlocksWhileMining;
            applyAndSaveConfig();
            sendSettingStatus("Block mining switching", switchForBlocksWhileMining);
        }

        while (toggleCombatKey.consumeClick()) {
            switchForHostileMobs = !switchForHostileMobs;
            applyAndSaveConfig();
            sendSettingStatus("Hostile mob switching", switchForHostileMobs);
        }
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

    private static AutoSwitchTarget findBestTarget(Minecraft client, int selectedSlot) {
        AutoSwitchTarget combatTarget = findBestCombatTarget(client);
        if (combatTarget != null) {
            return combatTarget;
        }

        AutoSwitchTarget interactionTarget = findBestInteractionTarget(client, selectedSlot);
        if (interactionTarget != null) {
            return interactionTarget;
        }

        return findBestBlockTarget(client, selectedSlot);
    }

    private static AutoSwitchTarget findBestCombatTarget(Minecraft client) {
        if (!(client.hitResult instanceof EntityHitResult entityHit) || entityHit.getType() != HitResult.Type.ENTITY) {
            return null;
        }

        Entity entity = entityHit.getEntity();
        if (!isHostileLivingTarget(entity)) {
            return null;
        }

        LivingEntity living = (LivingEntity) entity;
        if (switchForRangedCombat && client.player.distanceTo(entity) >= rangedCombatDistance) {
            int rangedSlot = findBestRangedSlot(client);
            if (rangedSlot >= 0) {
                return new AutoSwitchTarget(rangedSlot, "Ranged weapon for " + entity.getType().getDescription().getString());
            }
        }

        if (!switchForHostileMobs) {
            return null;
        }

        int meleeSlot = findBestMeleeSlot(client, living);
        if (meleeSlot < 0) {
            return null;
        }

        return new AutoSwitchTarget(meleeSlot, "Melee weapon for " + entity.getType().getDescription().getString());
    }

    private static boolean isHostileLivingTarget(Entity entity) {
        return entity instanceof Enemy
            && entity instanceof LivingEntity living
            && entity.isAttackable()
            && living.isAlive()
            && !living.isDeadOrDying();
    }

    private static AutoSwitchTarget findBestInteractionTarget(Minecraft client, int selectedSlot) {
        if (!switchForRightClickBlocks || !client.options.keyUse.isDown()) {
            return null;
        }

        BlockState state = getTargetBlockState(client);
        if (state == null) {
            return null;
        }

        int slot = findBestSlot(client, selectedSlot, stack -> interactionScore(stack, state));
        return slot < 0 ? null : new AutoSwitchTarget(slot, "Right-click tool");
    }

    private static AutoSwitchTarget findBestBlockTarget(Minecraft client, int selectedSlot) {
        boolean canSwitchForBlock = switchForBlocksOnLook || switchForBlocksWhileMining && client.options.keyAttack.isDown();
        if (!canSwitchForBlock) {
            return null;
        }

        BlockState state = getTargetBlockState(client);
        if (state == null) {
            return null;
        }

        int slot = findBestSlot(client, selectedSlot, stack -> blockScore(client, stack, state));
        return slot < 0 ? null : new AutoSwitchTarget(slot, "Tool for block");
    }

    private static BlockState getTargetBlockState(Minecraft client) {
        if (!(client.hitResult instanceof BlockHitResult blockHit) || blockHit.getType() != HitResult.Type.BLOCK) {
            return null;
        }

        BlockState state = client.level.getBlockState(blockHit.getBlockPos());
        return state.isAir() ? null : state;
    }

    private static int findBestMeleeSlot(Minecraft client, LivingEntity target) {
        return findBestSlot(client, -1, stack -> meleeScore(client, stack, target));
    }

    private static int findBestRangedSlot(Minecraft client) {
        return findBestSlot(client, -1, stack -> rangedScore(client, stack));
    }

    private static int findBestSlot(Minecraft client, int selectedSlot, StackScorer scorer) {
        double bestScore = 0.0D;
        int bestSlot = -1;

        if (selectedSlot >= 0 && selectedSlot < HOTBAR_SIZE && isSlotAllowed(selectedSlot)) {
            ItemStack current = client.player.getInventory().getItem(selectedSlot);
            bestScore = scorer.score(current);
            if (bestScore > 0.0D) {
                bestSlot = selectedSlot;
            }
        }

        for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
            if (!isSlotAllowed(slot)) {
                continue;
            }

            ItemStack stack = client.player.getInventory().getItem(slot);
            double score = scorer.score(stack);
            if (score > bestScore + SWITCH_THRESHOLD) {
                bestScore = score;
                bestSlot = slot;
            }
        }

        return bestSlot;
    }

    private static double blockScore(Minecraft client, ItemStack stack, BlockState state) {
        if (stack.isEmpty() || isUnsafeToUse(stack)) {
            return 0.0D;
        }

        float speed = stack.getDestroySpeed(state);
        if (speed <= 1.0F) {
            return speed;
        }

        if (state.requiresCorrectToolForDrops() && !stack.isCorrectToolForDrops(state)) {
            return 0.0D;
        }

        int efficiencyLevel = getEnchantmentLevel(client, stack, Enchantments.EFFICIENCY);
        if (efficiencyLevel > 0) {
            speed += efficiencyLevel * efficiencyLevel + 1.0F;
        }

        double score = speed + (stack.isCorrectToolForDrops(state) ? 0.1D : 0.0D);
        if (miningPreference == MiningPreference.SILK_TOUCH && getEnchantmentLevel(client, stack, Enchantments.SILK_TOUCH) > 0) {
            score += 1000.0D;
        } else if (miningPreference == MiningPreference.FORTUNE) {
            int fortuneLevel = getEnchantmentLevel(client, stack, Enchantments.FORTUNE);
            if (fortuneLevel > 0) {
                score += 1000.0D + fortuneLevel;
            }
        }

        return score;
    }

    private static double interactionScore(ItemStack stack, BlockState state) {
        if (stack.isEmpty() || isUnsafeToUse(stack)) {
            return 0.0D;
        }

        if ((isBlock(state, BlockTags.LEAVES) || isBlock(state, BlockTags.WOOL) || isBlock(state, BlockTags.WOOL_CARPETS)) && stack.getItem() == Items.SHEARS) {
            return 100.0D;
        }

        if ((isBlock(state, BlockTags.LOGS) || isBlock(state, BlockTags.MINEABLE_WITH_AXE)) && isItem(stack, ItemTags.AXES)) {
            return 90.0D;
        }

        if ((isBlock(state, BlockTags.SUPPORTS_CROPS) || isBlock(state, BlockTags.GROWS_CROPS) || isBlock(state, BlockTags.MINEABLE_WITH_HOE)) && isItem(stack, ItemTags.HOES)) {
            return 80.0D;
        }

        if ((isBlock(state, BlockTags.DIRT) || isBlock(state, BlockTags.MINEABLE_WITH_SHOVEL)) && isItem(stack, ItemTags.SHOVELS)) {
            return 70.0D;
        }

        return 0.0D;
    }

    private static double meleeScore(Minecraft client, ItemStack stack, LivingEntity target) {
        if (stack.isEmpty() || isUnsafeToUse(stack) || !isMeleeCandidate(stack)) {
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

        if (target.getType().builtInRegistryHolder().is(EntityTypeTags.SENSITIVE_TO_SMITE)) {
            attackDamage[0] += getEnchantmentLevel(client, stack, Enchantments.SMITE) * 2.5D;
        }

        if (target.getType().builtInRegistryHolder().is(EntityTypeTags.SENSITIVE_TO_BANE_OF_ARTHROPODS)) {
            attackDamage[0] += getEnchantmentLevel(client, stack, Enchantments.BANE_OF_ARTHROPODS) * 2.5D;
        }

        return attackDamage[0];
    }

    private static double rangedScore(Minecraft client, ItemStack stack) {
        if (stack.isEmpty() || isUnsafeToUse(stack)) {
            return 0.0D;
        }

        if (stack.getItem() == Items.CROSSBOW) {
            return 20.0D + getEnchantmentLevel(client, stack, Enchantments.QUICK_CHARGE);
        }

        if (stack.getItem() == Items.BOW) {
            return 18.0D + getEnchantmentLevel(client, stack, Enchantments.POWER);
        }

        return 0.0D;
    }

    private static boolean isMeleeCandidate(ItemStack stack) {
        return isItem(stack, ItemTags.SWORDS)
            || allowAxesForCombat && isItem(stack, ItemTags.AXES)
            || allowMacesForCombat && stack.getItem() == Items.MACE;
    }

    private static boolean isItem(ItemStack stack, net.minecraft.tags.TagKey<net.minecraft.world.item.Item> tag) {
        return stack.getItem().builtInRegistryHolder().is(tag);
    }

    private static boolean isBlock(BlockState state, net.minecraft.tags.TagKey<Block> tag) {
        return state.getBlock().builtInRegistryHolder().is(tag);
    }

    private static int getEnchantmentLevel(Minecraft client, ItemStack stack, ResourceKey<Enchantment> enchantmentKey) {
        Holder<Enchantment> enchantment = client.level.registryAccess()
            .lookupOrThrow(Registries.ENCHANTMENT)
            .getOrThrow(enchantmentKey);
        return EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack);
    }

    private static boolean isUnsafeToUse(ItemStack stack) {
        if (!durabilitySafety || !stack.isDamageableItem()) {
            return false;
        }

        int durabilityLeft = stack.getMaxDamage() - stack.getDamageValue();
        return durabilityLeft <= minDurabilityLeft;
    }

    private static void switchToSlot(Minecraft client, int slot, String reason) {
        if (slot < 0 || slot >= HOTBAR_SIZE || client.player == null) {
            return;
        }

        int selectedSlot = client.player.getInventory().getSelectedSlot();
        if (selectedSlot == slot) {
            if (autoSelectedSlot != slot) {
                clearAutoSwitch();
            }
            return;
        }

        if (restoreSlot < 0 || selectedSlot != autoSelectedSlot) {
            restoreSlot = selectedSlot;
        }

        setSelectedSlot(client, slot);
        autoSelectedSlot = slot;
        targetMissingTicks = 0;

        if (showHudIndicator) {
            client.player.sendOverlayMessage(Component.literal("Auto Tool: " + reason).withStyle(ChatFormatting.AQUA));
        }
    }

    private static void finishAutoSwitchWhenIdle(Minecraft client) {
        if (autoSelectedSlot < 0) {
            clearAutoSwitch();
            return;
        }

        targetMissingTicks++;
        if (targetMissingTicks >= restoreDelayTicks) {
            finishAutoSwitch(client);
        }
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
        targetMissingTicks = 0;
    }

    private static void setSelectedSlot(Minecraft client, int slot) {
        client.player.getInventory().setSelectedSlot(slot);
        ClientPacketListener connection = client.getConnection();
        if (connection != null) {
            connection.send(new ServerboundSetCarriedItemPacket(slot));
        }
    }

    static boolean isSlotAllowed(int slot) {
        return slot >= 0 && slot < allowedHotbarSlots.length && allowedHotbarSlots[slot];
    }

    static void setSlotAllowed(int slot, boolean allowed) {
        if (slot >= 0 && slot < allowedHotbarSlots.length) {
            allowedHotbarSlots[slot] = allowed;
        }
    }

    static void allowAllSlots() {
        Arrays.fill(allowedHotbarSlots, true);
    }

    private static boolean[] defaultAllowedSlots() {
        boolean[] slots = new boolean[HOTBAR_SIZE];
        Arrays.fill(slots, true);
        return slots;
    }

    private static String allowedSlotsText() {
        StringBuilder builder = new StringBuilder();
        for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
            if (isSlotAllowed(slot)) {
                if (!builder.isEmpty()) {
                    builder.append(", ");
                }
                builder.append(slot + 1);
            }
        }
        return builder.isEmpty() ? "none" : builder.toString();
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
                durabilitySafety = getBoolean(properties, "durabilitySafety", true);
                switchForRightClickBlocks = getBoolean(properties, "switchForRightClickBlocks", false);
                allowAxesForCombat = getBoolean(properties, "allowAxesForCombat", false);
                allowMacesForCombat = getBoolean(properties, "allowMacesForCombat", false);
                switchForRangedCombat = getBoolean(properties, "switchForRangedCombat", false);
                showHudIndicator = getBoolean(properties, "showHudIndicator", false);
                minDurabilityLeft = getInt(properties, "minDurabilityLeft", DEFAULT_MIN_DURABILITY_LEFT, 0, 256);
                restoreDelayTicks = getInt(properties, "restoreDelayTicks", DEFAULT_RESTORE_DELAY_TICKS, 0, 60);
                rangedCombatDistance = getInt(properties, "rangedCombatDistance", DEFAULT_RANGED_COMBAT_DISTANCE, 1, 64);
                miningPreference = getEnum(properties, "miningPreference", MiningPreference.class, DEFAULT_MINING_PREFERENCE);
                allowedHotbarSlots = getAllowedSlots(properties.getProperty("allowedHotbarSlots"));
            } catch (IOException ignored) {
                loadDefaults();
            }
        }
        clampSettings();
    }

    private static void loadDefaults() {
        resetSettingsToDefaults();
    }

    private static boolean getBoolean(Properties properties, String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        return value == null ? defaultValue : Boolean.parseBoolean(value);
    }

    private static int getInt(Properties properties, String key, int defaultValue, int min, int max) {
        try {
            return clamp(Integer.parseInt(properties.getProperty(key, Integer.toString(defaultValue))), min, max);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static <T extends Enum<T>> T getEnum(Properties properties, String key, Class<T> enumType, T defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }

        try {
            return Enum.valueOf(enumType, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return defaultValue;
        }
    }

    private static boolean[] getAllowedSlots(String value) {
        boolean[] slots = defaultAllowedSlots();
        if (value == null) {
            return slots;
        }

        for (int slot = 0; slot < HOTBAR_SIZE && slot < value.length(); slot++) {
            slots[slot] = value.charAt(slot) == '1';
        }
        return slots;
    }

    static void saveConfig() {
        Properties properties = new Properties();
        properties.setProperty("enabled", Boolean.toString(enabled));
        properties.setProperty("switchForBlocksOnLook", Boolean.toString(switchForBlocksOnLook));
        properties.setProperty("switchForBlocksWhileMining", Boolean.toString(switchForBlocksWhileMining));
        properties.setProperty("switchForHostileMobs", Boolean.toString(switchForHostileMobs));
        properties.setProperty("restorePreviousSlot", Boolean.toString(restorePreviousSlot));
        properties.setProperty("pauseWhileDropKeyDown", Boolean.toString(pauseWhileDropKeyDown));
        properties.setProperty("durabilitySafety", Boolean.toString(durabilitySafety));
        properties.setProperty("switchForRightClickBlocks", Boolean.toString(switchForRightClickBlocks));
        properties.setProperty("allowAxesForCombat", Boolean.toString(allowAxesForCombat));
        properties.setProperty("allowMacesForCombat", Boolean.toString(allowMacesForCombat));
        properties.setProperty("switchForRangedCombat", Boolean.toString(switchForRangedCombat));
        properties.setProperty("showHudIndicator", Boolean.toString(showHudIndicator));
        properties.setProperty("minDurabilityLeft", Integer.toString(minDurabilityLeft));
        properties.setProperty("restoreDelayTicks", Integer.toString(restoreDelayTicks));
        properties.setProperty("rangedCombatDistance", Integer.toString(rangedCombatDistance));
        properties.setProperty("miningPreference", miningPreference.name());
        properties.setProperty("allowedHotbarSlots", allowedSlotsProperty());
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (OutputStream output = Files.newOutputStream(CONFIG_PATH)) {
                properties.store(output, "Auto Tool Switcher client config");
            }
        } catch (IOException ignored) {
            // The current session still uses the new values if saving fails.
        }
    }

    private static String allowedSlotsProperty() {
        StringBuilder builder = new StringBuilder(HOTBAR_SIZE);
        for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
            builder.append(isSlotAllowed(slot) ? '1' : '0');
        }
        return builder.toString();
    }

    private static void clampSettings() {
        minDurabilityLeft = clamp(minDurabilityLeft, 0, 256);
        restoreDelayTicks = clamp(restoreDelayTicks, 0, 60);
        rangedCombatDistance = clamp(rangedCombatDistance, 1, 64);
        if (allowedHotbarSlots == null || allowedHotbarSlots.length != HOTBAR_SIZE) {
            allowedHotbarSlots = defaultAllowedSlots();
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    enum MiningPreference {
        SPEED("Fastest tool"),
        SILK_TOUCH("Prefer Silk Touch"),
        FORTUNE("Prefer Fortune");

        private final String displayName;

        MiningPreference(String displayName) {
            this.displayName = displayName;
        }

        String displayName() {
            return displayName;
        }
    }

    enum ProfilePreset {
        CUSTOM("Custom"),
        DEFAULT("Default"),
        MINING("Mining"),
        BUILDING("Building"),
        COMBAT("Combat"),
        MINIMAL("Minimal");

        private final String displayName;

        ProfilePreset(String displayName) {
            this.displayName = displayName;
        }

        String displayName() {
            return displayName;
        }
    }

    private record AutoSwitchTarget(int slot, String reason) {
    }

    private interface StackScorer {
        double score(ItemStack stack);
    }

    private interface BooleanSettingSetter {
        void set(boolean value);
    }
}
