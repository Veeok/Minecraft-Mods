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
        ConfigCategory triggers = builder.getOrCreateCategory(Component.literal("Triggers"));
        ConfigCategory safety = builder.getOrCreateCategory(Component.literal("Safety"));
        ConfigCategory slots = builder.getOrCreateCategory(Component.literal("Hotbar Slots"));
        ConfigCategory optional = builder.getOrCreateCategory(Component.literal("Optional"));
        ConfigCategory profiles = builder.getOrCreateCategory(Component.literal("Profiles"));

        general.addEntry(entries.startTextDescription(
            Component.literal("Veok's client-side hotbar helper. Core features are on; extra features stay off until you want them.").withStyle(ChatFormatting.GRAY)
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

        addMiningPreference(optional, entries);

        addToggle(
            triggers,
            entries,
            "Switch while mining",
            "When you hold attack on a block, use the best matching hotbar tool.",
            AutoToolSwitcherClient.switchForBlocksWhileMining,
            AutoToolSwitcherClient.DEFAULT_SWITCH_FOR_BLOCKS_WHILE_MINING,
            value -> AutoToolSwitcherClient.switchForBlocksWhileMining = value
        );

        addToggle(
            triggers,
            entries,
            "Switch on block look",
            "Switch just from aiming at a block. Powerful, but easier to accidentally trigger.",
            AutoToolSwitcherClient.switchForBlocksOnLook,
            AutoToolSwitcherClient.DEFAULT_SWITCH_FOR_BLOCKS_ON_LOOK,
            value -> AutoToolSwitcherClient.switchForBlocksOnLook = value
        );

        addToggle(
            triggers,
            entries,
            "Sword for hostile mobs",
            "When your crosshair is on a hostile mob, pull out your best melee weapon.",
            AutoToolSwitcherClient.switchForHostileMobs,
            AutoToolSwitcherClient.DEFAULT_SWITCH_FOR_HOSTILE_MOBS,
            value -> AutoToolSwitcherClient.switchForHostileMobs = value
        );

        addToggle(
            optional,
            entries,
            "Right-click block tools",
            "Optional helper for axes, hoes, shovels, and shears while holding right-click on blocks.",
            AutoToolSwitcherClient.switchForRightClickBlocks,
            AutoToolSwitcherClient.DEFAULT_SWITCH_FOR_RIGHT_CLICK_BLOCKS,
            value -> AutoToolSwitcherClient.switchForRightClickBlocks = value
        );

        addToggle(
            optional,
            entries,
            "Ranged combat",
            "Optional bow/crossbow switching for hostile mobs past the configured distance.",
            AutoToolSwitcherClient.switchForRangedCombat,
            AutoToolSwitcherClient.DEFAULT_SWITCH_FOR_RANGED_COMBAT,
            value -> AutoToolSwitcherClient.switchForRangedCombat = value
        );

        addSlider(
            optional,
            entries,
            "Ranged combat distance",
            "Minimum distance before the ranged combat option prefers bow/crossbow.",
            AutoToolSwitcherClient.rangedCombatDistance,
            AutoToolSwitcherClient.DEFAULT_RANGED_COMBAT_DISTANCE,
            1,
            64,
            value -> value + " blocks",
            value -> AutoToolSwitcherClient.rangedCombatDistance = value
        );

        addToggle(
            optional,
            entries,
            "Allow combat axes",
            "Let combat switching choose axes when they score higher than swords.",
            AutoToolSwitcherClient.allowAxesForCombat,
            AutoToolSwitcherClient.DEFAULT_ALLOW_AXES_FOR_COMBAT,
            value -> AutoToolSwitcherClient.allowAxesForCombat = value
        );

        addToggle(
            optional,
            entries,
            "Allow combat maces",
            "Let combat switching choose maces when they score higher than swords.",
            AutoToolSwitcherClient.allowMacesForCombat,
            AutoToolSwitcherClient.DEFAULT_ALLOW_MACES_FOR_COMBAT,
            value -> AutoToolSwitcherClient.allowMacesForCombat = value
        );

        addToggle(
            optional,
            entries,
            "HUD indicator",
            "Show a small overlay when Auto Tool Switcher changes slots.",
            AutoToolSwitcherClient.showHudIndicator,
            AutoToolSwitcherClient.DEFAULT_SHOW_HUD_INDICATOR,
            value -> AutoToolSwitcherClient.showHudIndicator = value
        );

        addToggle(
            safety,
            entries,
            "Durability safety",
            "Skip damageable items when they are at or below the durability limit.",
            AutoToolSwitcherClient.durabilitySafety,
            AutoToolSwitcherClient.DEFAULT_DURABILITY_SAFETY,
            value -> AutoToolSwitcherClient.durabilitySafety = value
        );

        addSlider(
            safety,
            entries,
            "Minimum durability left",
            "Damageable tools at or below this remaining durability are ignored.",
            AutoToolSwitcherClient.minDurabilityLeft,
            AutoToolSwitcherClient.DEFAULT_MIN_DURABILITY_LEFT,
            0,
            64,
            value -> value + " durability",
            value -> AutoToolSwitcherClient.minDurabilityLeft = value
        );

        addToggle(
            safety,
            entries,
            "Restore previous slot",
            "After the target is gone, switch back to what you were holding before the auto swap.",
            AutoToolSwitcherClient.restorePreviousSlot,
            AutoToolSwitcherClient.DEFAULT_RESTORE_PREVIOUS_SLOT,
            value -> AutoToolSwitcherClient.restorePreviousSlot = value
        );

        addSlider(
            safety,
            entries,
            "Restore delay",
            "How long the target must be gone before switching back. Higher values reduce flicker.",
            AutoToolSwitcherClient.restoreDelayTicks,
            AutoToolSwitcherClient.DEFAULT_RESTORE_DELAY_TICKS,
            0,
            60,
            value -> value + " ticks",
            value -> AutoToolSwitcherClient.restoreDelayTicks = value
        );

        addToggle(
            safety,
            entries,
            "Drop safety",
            "Pause auto switching while your drop key is held, so throwing items feels normal.",
            AutoToolSwitcherClient.pauseWhileDropKeyDown,
            AutoToolSwitcherClient.DEFAULT_PAUSE_WHILE_DROP_KEY_DOWN,
            value -> AutoToolSwitcherClient.pauseWhileDropKeyDown = value
        );

        slots.addEntry(entries.startTextDescription(
            Component.literal("Only these hotbar slots can be selected automatically. Restore can still return to any previous slot.").withStyle(ChatFormatting.GRAY)
        ).build());
        for (int slot = 0; slot < 9; slot++) {
            final int slotIndex = slot;
            addToggle(
                slots,
                entries,
                "Allow slot " + (slot + 1),
                "Allow Auto Tool Switcher to choose hotbar slot " + (slot + 1) + ".",
                AutoToolSwitcherClient.isSlotAllowed(slot),
                true,
                value -> AutoToolSwitcherClient.setSlotAllowed(slotIndex, value)
            );
        }

        addProfileSelector(profiles, entries);
        profiles.addEntry(entries.startTextDescription(
            Component.literal("Profiles are shortcuts. Pick one, save, then reopen this screen to see the applied toggles.").withStyle(ChatFormatting.GRAY)
        ).build());
        profiles.addEntry(entries.startTextDescription(
            Component.literal("Keybinds are in Minecraft Controls under Auto Tool Switcher. They are unbound by default.").withStyle(ChatFormatting.GRAY)
        ).build());

        return builder.build();
    }

    private static void addMiningPreference(ConfigCategory category, ConfigEntryBuilder entries) {
        category.addEntry(entries.startEnumSelector(
                Component.literal("Mining preference"),
                AutoToolSwitcherClient.MiningPreference.class,
                AutoToolSwitcherClient.miningPreference
            )
            .setDefaultValue(AutoToolSwitcherClient.DEFAULT_MINING_PREFERENCE)
            .setEnumNameProvider(value -> Component.literal(((AutoToolSwitcherClient.MiningPreference) value).displayName()))
            .setTooltip(Component.literal("Fastest tool by default, or prefer Silk Touch/Fortune tools when available."))
            .setSaveConsumer(value -> AutoToolSwitcherClient.miningPreference = value)
            .build());
    }

    private static void addProfileSelector(ConfigCategory category, ConfigEntryBuilder entries) {
        category.addEntry(entries.startEnumSelector(
                Component.literal("Apply profile on save"),
                AutoToolSwitcherClient.ProfilePreset.class,
                AutoToolSwitcherClient.ProfilePreset.CUSTOM
            )
            .setDefaultValue(AutoToolSwitcherClient.ProfilePreset.CUSTOM)
            .setEnumNameProvider(value -> Component.literal(((AutoToolSwitcherClient.ProfilePreset) value).displayName()))
            .setTooltip(Component.literal("Optional presets for quick setup. Custom means do nothing."))
            .setSaveConsumer(value -> AutoToolSwitcherClient.applyProfile(value))
            .build());
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

    private static void addSlider(
        ConfigCategory category,
        ConfigEntryBuilder entries,
        String title,
        String tooltip,
        int value,
        int defaultValue,
        int min,
        int max,
        IntTextGetter textGetter,
        Consumer<Integer> saveConsumer
    ) {
        category.addEntry(entries.startIntSlider(Component.literal(title), value, min, max)
            .setDefaultValue(defaultValue)
            .setTextGetter(savedValue -> Component.literal(textGetter.getText(savedValue)))
            .setTooltip(Component.literal(tooltip))
            .setSaveConsumer(saveConsumer)
            .build());
    }

    private interface IntTextGetter {
        String getText(int value);
    }
}
