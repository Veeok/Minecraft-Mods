package com.veok.autotoolswitcher;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public final class AutoToolSwitcherConfigScreen {
    private AutoToolSwitcherConfigScreen() {
    }

    public static Screen create(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Component.literal("Auto Tool Switcher"))
            .setSavingRunnable(AutoToolSwitcherClient::applyAndSaveConfig);

        ConfigEntryBuilder entries = builder.entryBuilder();
        ConfigCategory general = builder.getOrCreateCategory(Component.literal("General"));
        ConfigCategory behavior = builder.getOrCreateCategory(Component.literal("Behavior"));

        general.addEntry(entries.startTextDescription(
            Component.literal("Quick toggles for Veok's client-side hotbar helper.").withStyle(ChatFormatting.GRAY)
        ).build());

        addToggle(
            general,
            entries,
            "Enabled",
            "Master switch for the whole mod.",
            AutoToolSwitcherClient.enabled,
            AutoToolSwitcherClient.DEFAULT_ENABLED,
            value -> AutoToolSwitcherClient.enabled = value
        );

        addToggle(
            behavior,
            entries,
            "Switch while mining",
            "When you hold attack on a block, use the best matching hotbar tool.",
            AutoToolSwitcherClient.switchForBlocksWhileMining,
            AutoToolSwitcherClient.DEFAULT_SWITCH_FOR_BLOCKS_WHILE_MINING,
            value -> AutoToolSwitcherClient.switchForBlocksWhileMining = value
        );

        addToggle(
            behavior,
            entries,
            "Switch on block look",
            "Switch just from aiming at a block. Handy, but easier to accidentally trigger.",
            AutoToolSwitcherClient.switchForBlocksOnLook,
            AutoToolSwitcherClient.DEFAULT_SWITCH_FOR_BLOCKS_ON_LOOK,
            value -> AutoToolSwitcherClient.switchForBlocksOnLook = value
        );

        addToggle(
            behavior,
            entries,
            "Sword for hostile mobs",
            "When your crosshair is on a hostile mob, pull out the best sword in your hotbar.",
            AutoToolSwitcherClient.switchForHostileMobs,
            AutoToolSwitcherClient.DEFAULT_SWITCH_FOR_HOSTILE_MOBS,
            value -> AutoToolSwitcherClient.switchForHostileMobs = value
        );

        addToggle(
            behavior,
            entries,
            "Restore previous slot",
            "After the target is gone, switch back to what you were holding before the auto swap.",
            AutoToolSwitcherClient.restorePreviousSlot,
            AutoToolSwitcherClient.DEFAULT_RESTORE_PREVIOUS_SLOT,
            value -> AutoToolSwitcherClient.restorePreviousSlot = value
        );

        addToggle(
            behavior,
            entries,
            "Drop safety",
            "Pause auto switching while your drop key is held, so throwing items feels normal.",
            AutoToolSwitcherClient.pauseWhileDropKeyDown,
            AutoToolSwitcherClient.DEFAULT_PAUSE_WHILE_DROP_KEY_DOWN,
            value -> AutoToolSwitcherClient.pauseWhileDropKeyDown = value
        );

        return builder.build();
    }

    private static void addToggle(
        ConfigCategory category,
        ConfigEntryBuilder entries,
        String title,
        String tooltip,
        boolean value,
        boolean defaultValue,
        Consumer<Boolean> saveConsumer
    ) {
        category.addEntry(entries.startBooleanToggle(Component.literal(title), value)
            .setDefaultValue(defaultValue)
            .setTooltip(Component.literal(tooltip))
            .setSaveConsumer(saveConsumer)
            .build());
    }
}
