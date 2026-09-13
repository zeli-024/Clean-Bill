package dev.zeli.cleanbill;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.neoforged.neoforge.event.ServerChatEvent;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CleanBillMenus {
    private static final int STORAGE_PER_PAGE = 45;
    private static final Map<UUID, MessageKind> MESSAGE_EDITS = new java.util.HashMap<>();
    private CleanBillMenus() {}

    public static void openConfig(ServerPlayer player) {
        MESSAGE_EDITS.remove(player.getUUID());
        SimpleContainer box = decorated(27);
        box.setItem(10, category(Items.CLOCK, "Cleanup", value("Timer, countdown, and item-age protection."),
                gray("Controls when cleanups happen and which recent drops are protected.")));
        box.setItem(11, category(Items.BELL, "Alerts", value("Display, sounds, schedule, and clickable controls."),
                gray("Also contains private previews that do not change the timer.")));
        box.setItem(13, category(Items.WRITABLE_BOOK, "Messages", value("Edit and preview public cleanup text."),
                gray("Supports & codes and <#RRGGBB> hex colors.")));
        box.setItem(15, category(Items.CHEST, "Item Pond", value("Open the shared, take-only cleanup archive."),
                gray("Its collection, access, filtering, and wash controls are inside.")));
        box.setItem(16, category(Items.COMPARATOR, "Maintenance", value("Reload, restore, reset, help, and status."),
                gray("Destructive actions require a confirmation screen.")));
        player.openMenu(provider(box, 3, "Clean Bill", (slot, p, button, type) -> {
            switch (slot) {
                case 10 -> later(p, () -> openCleanupCategory(p));
                case 11 -> later(p, () -> openAlertsCategory(p));
                case 13 -> later(p, () -> openMessagesCategory(p));
                case 15 -> openPondLater(p, 0, false);
                case 16 -> later(p, () -> openMaintenance(p));
                default -> { }
            }
        }));
    }

    private static void openCleanupCategory(ServerPlayer player) {
        CleanConfig.Values c = CleanConfig.get();
        SimpleContainer box = decorated(27);
        box.setItem(10, detailed(Items.CLOCK, "Cleanup Interval",
                value("Current: " + CleanConfig.formatDuration(c.intervalSeconds)),
                gray("How long the server waits between successful cleanups."),
                gray("Uses server game time, so it pauses while the server is offline."),
                green("Click to edit.")));
        box.setItem(12, detailed(Items.REPEATER, "Countdown",
                value("Current: " + CleanConfig.formatDuration(c.countdownSeconds)),
                gray("Starts per-second warnings before the cleanup."),
                gray("Countdown visibility is controlled in the Alerts category."),
                green("Click to edit.")));
        box.setItem(14, detailed(Items.CLOCK, "Minimum Item Age",
                value("Current: " + (c.minAgeEnabled ? CleanConfig.formatDuration(c.minAgeSeconds) : "disabled")),
                gray("Protects recently dropped items from automatic and manual cleanup."),
                gray("Disabled by default. Timestamp checks also work after chunks reload."),
                green("Click to edit or toggle.")));
        CleanBillData data = CleanBillData.get(player.getServer());
        box.setItem(16, detailed(data.paused ? Items.LIME_DYE : Items.RED_DYE,
                data.paused ? "Resume Timer" : "Pause Timer",
                value("Remaining: " + CleanConfig.formatDuration(CleanBill.CLEANUP.remainingSeconds(player.getServer()))),
                gray("Pauses or resumes the cleanup timer without discarding its remaining time."),
                green("Click to " + (data.paused ? "resume." : "pause."))));
        back(box, 22);
        player.openMenu(provider(box, 3, "Cleanup Settings", (slot, p, button, type) -> {
            switch (slot) {
                case 10 -> openDurationLater(p, DurationKind.INTERVAL, -1);
                case 12 -> openDurationLater(p, DurationKind.COUNTDOWN, -1);
                case 14 -> openDurationLater(p, DurationKind.MIN_AGE, -1);
                case 16 -> {
                    if (CleanBillData.get(p.getServer()).paused) {
                        CleanBill.CLEANUP.resume(p.getServer());
                        CleanBill.CLEANUP.broadcastAction(p.getServer(), "Cleanup resumed by ", p.getDisplayName());
                    } else {
                        CleanBill.CLEANUP.pause(p.getServer());
                        CleanBill.CLEANUP.broadcastAction(p.getServer(), "Cleanup paused by ", p.getDisplayName());
                    }
                    later(p, () -> openCleanupCategory(p));
                }
                case 22 -> openConfigLater(p);
                default -> { }
            }
        }));
    }

    private static void openAlertsCategory(ServerPlayer player) {
        CleanConfig.Values c = CleanConfig.get();
        SimpleContainer box = decorated(54);
        box.setItem(10, detailed(Items.BELL, "Scheduled Alerts",
                value("Current: " + formatAlerts(c.alertWhenSeconds)),
                gray("Warnings sent at selected times before cleanup."),
                gray("Duplicates are merged and countdown warnings take priority."),
                green("Click to add, edit, remove, or reset alerts.")));
        box.setItem(12, choiceItem(Items.OAK_SIGN, "Alert Display", c.alertShow,
                "Select chat, action bar, or none. Action-bar text is automatically shortened."));
        box.setItem(14, choiceItem(Items.NOTE_BLOCK, "Alert Sound", c.alertSound,
                "XP or note-block cues play for all online players. Default: jukebox."));
        box.setItem(16, choiceItem(Items.REDSTONE_TORCH, "Countdown Display", enabled(c.countdownShow),
                "Controls per-second countdown messages and their clickable controls."));
        box.setItem(20, choiceItem(Items.OAK_BUTTON, "Clickable Controls", c.buttonAccess,
                "Select whether everyone or only operators may use clean now and clean later."));
        box.setItem(29, previewItem(Items.BELL, "Preview Scheduled Alert", "Shows a 10m alert only to you."));
        box.setItem(31, previewItem(Items.CLOCK, "Preview Countdown", "Shows a 5s countdown only to you."));
        box.setItem(33, previewItem(Items.FIREWORK_STAR, "Preview Cleanup Result", "Shows a sample result only to you."));
        back(box, 49);
        player.openMenu(provider(box, 6, "Alert Settings", (slot, p, button, type) -> {
            switch (slot) {
                case 10 -> openAlertsLater(p);
                case 12 -> openChoiceLater(p, ChoiceKind.ALERT_SHOW);
                case 14 -> openChoiceLater(p, ChoiceKind.ALERT_SOUND);
                case 16 -> openChoiceLater(p, ChoiceKind.COUNTDOWN_SHOW);
                case 20 -> openChoiceLater(p, ChoiceKind.BUTTON_ACCESS);
                case 29 -> CleanBill.CLEANUP.preview(p, "alert");
                case 31 -> CleanBill.CLEANUP.preview(p, "countdown");
                case 33 -> CleanBill.CLEANUP.preview(p, "clear");
                case 49 -> openConfigLater(p);
                default -> { }
            }
        }));
    }

    private static void openMessagesCategory(ServerPlayer player) {
        SimpleContainer box = decorated(27);
        box.setItem(10, messageItem(Items.PAPER, "Scheduled Alert Message", CleanConfig.get().alertMessage,
                "Used for non-countdown alert times."));
        box.setItem(13, messageItem(Items.MAP, "Countdown Message", CleanConfig.get().countdownMessage,
                "Used for each visible countdown second."));
        box.setItem(16, messageItem(Items.WRITABLE_BOOK, "Cleanup Result Message", CleanConfig.get().clearMessage,
                "Used after the cleanup queue finishes."));
        back(box, 22);
        player.openMenu(provider(box, 3, "Message Settings", (slot, p, button, type) -> {
            if (slot == 10) openMessageLater(p, MessageKind.ALERT);
            else if (slot == 13) openMessageLater(p, MessageKind.COUNTDOWN);
            else if (slot == 16) openMessageLater(p, MessageKind.CLEAR);
            else if (slot == 22) openConfigLater(p);
        }));
    }

    private static void openMessage(ServerPlayer player, MessageKind kind) {
        SimpleContainer box = decorated(27);
        box.setItem(11, detailed(Items.WRITABLE_BOOK, "Edit in Chat",
                gray("Closes this menu and captures your next chat message."),
                gray("Type cancel to leave the current message unchanged."),
                green("Click to begin editing.")));
        box.setItem(13, detailed(kind.item, kind.label,
                value("Current: " + kind.get()),
                gray("Click this center icon to reset only this message."),
                gray("Default: " + kind.defaultValue)));
        box.setItem(15, previewItem(Items.SPYGLASS, "Preview", "Displays this message and sound only to you."));
        back(box, 22);
        player.openMenu(provider(box, 3, kind.label, (slot, p, button, type) -> {
            if (slot == 11) {
                MESSAGE_EDITS.put(p.getUUID(), kind);
                p.closeContainer();
                p.sendSystemMessage(TextUtil.gray("Type the new message in chat, or type ")
                        .append(TextUtil.red("cancel")).append(TextUtil.gray(". Supports & codes and <#RRGGBB>.")));
            } else if (slot == 13) {
                kind.set(kind.defaultValue);
                saveQuietly();
                openMessageLater(p, kind);
            } else if (slot == 15) {
                CleanBill.CLEANUP.preview(p, kind.preview);
            } else if (slot == 22) openMessagesLater(p);
        }));
    }

    public static void onServerChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        MessageKind kind = MESSAGE_EDITS.remove(player.getUUID());
        if (kind == null) return;
        event.setCanceled(true);
        String message = event.getRawText();
        if (message.equalsIgnoreCase("cancel")) {
            player.sendSystemMessage(TextUtil.gray("Message edit canceled."));
        } else {
            kind.set(message);
            saveQuietly();
            player.sendSystemMessage(TextUtil.green(kind.label + " saved."));
        }
        openMessageLater(player, kind);
    }

    private static void openMaintenance(ServerPlayer player) {
        SimpleContainer box = decorated(27);
        box.setItem(10, detailed(Items.LIME_DYE, "Reload Configuration",
                gray("Reloads only Clean Bill's JSON file."),
                gray("Does not reload the world, server, datapacks, or other mods.")));
        box.setItem(12, detailed(Items.YELLOW_DYE, "Restore Latest Backup",
                gray("Restores the newest automatic configuration backup."),
                gray("The current configuration is backed up first.")));
        box.setItem(14, detailed(Items.RED_DYE, "Reset All Settings",
                red("Resets every Clean Bill setting to its default."),
                gray("A backup is created first. Confirmation required.")));
        box.setItem(16, detailed(Items.WRITABLE_BOOK, "Help",
                gray("Prints the short command list privately to you.")));
        box.setItem(20, detailed(Items.COMPASS, "Broadcast Status",
                gray("Shows the timer and Pond usage to every online player."),
                gray("The report identifies you as the requester.")));
        back(box, 22);
        player.openMenu(provider(box, 3, "Maintenance", (slot, p, button, type) -> {
            if (slot == 10) reload(p);
            else if (slot == 12) restore(p);
            else if (slot == 14) later(p, () -> openConfirmReset(p));
            else if (slot == 16) run(p, "qc help");
            else if (slot == 20) run(p, "qc status");
            else if (slot == 22) openConfigLater(p);
        }));
    }

    private static void openConfirmReset(ServerPlayer player) {
        SimpleContainer box = decorated(27);
        box.setItem(11, detailed(Items.RED_DYE, "Confirm Full Reset",
                red("Resets every setting and restarts the cleanup timer."),
                gray("A restorable backup is saved first.")));
        box.setItem(15, detailed(Items.ARROW, "Go Back", gray("Makes no changes.")));
        player.openMenu(provider(box, 3, "Reset All Settings?", (slot, p, button, type) -> {
            if (slot == 11) {
                try {
                    Path backup = CleanConfig.resetWithBackup();
                    CleanBill.CLEANUP.resetTimer(p.getServer());
                    p.sendSystemMessage(TextUtil.green("Configuration reset. Backup: " + backup.getFileName()));
                    openConfigLater(p);
                } catch (IOException exception) { error(p, "Reset failed: " + exception.getMessage()); }
            } else if (slot == 15) later(p, () -> openMaintenance(p));
        }));
    }

    private static void reload(ServerPlayer player) {
        try {
            CleanConfig.load();
            player.sendSystemMessage(TextUtil.green("Clean Bill configuration reloaded."));
        } catch (IOException exception) { error(player, "Reload failed: " + exception.getMessage()); }
        later(player, () -> openMaintenance(player));
    }

    private static void restore(ServerPlayer player) {
        try {
            Path restored = CleanConfig.restoreLatest();
            if (restored == null) error(player, "No configuration backup exists.");
            else player.sendSystemMessage(TextUtil.green("Restored " + restored.getFileName() + "."));
        } catch (IOException exception) { error(player, "Restore failed: " + exception.getMessage()); }
        later(player, () -> openMaintenance(player));
    }

    private static void openChoice(ServerPlayer player, ChoiceKind kind) {
        SimpleContainer box = decorated(27);
        List<String> choices = kind.choices;
        int current = Math.max(0, choices.indexOf(kind.get()));
        box.setItem(11, detailed(Items.ARROW, "Previous",
                value(choices.get((current - 1 + choices.size()) % choices.size())),
                gray("Click to select the previous option.")));
        box.setItem(13, detailed(kind.item, kind.label,
                value("Current: " + kind.get()),
                gray("Click this center icon to reset only this setting."),
                gray("Default: " + kind.defaultValue)));
        box.setItem(15, detailed(Items.ARROW, "Next",
                value(choices.get((current + 1) % choices.size())),
                gray("Click to select the next option.")));
        back(box, 22);
        player.openMenu(provider(box, 3, kind.label, (slot, p, button, type) -> {
            int index = Math.max(0, kind.choices.indexOf(kind.get()));
            if (slot == 11) kind.set(kind.choices.get((index - 1 + kind.choices.size()) % kind.choices.size()));
            else if (slot == 15) kind.set(kind.choices.get((index + 1) % kind.choices.size()));
            else if (slot == 13) kind.set(kind.defaultValue);
            else if (slot == 22) { openAlertsLater(p); return; }
            else return;
            saveQuietly();
            openChoiceLater(p, kind);
        }));
    }

    private static void openDuration(ServerPlayer player, DurationKind kind, int alertIndex) {
        long current = kind.get(alertIndex);
        SimpleContainer box = decorated(27);
        long[] deltas = kind == DurationKind.WASH
                ? new long[]{-3600, -1800, -300, 0, 300, 1800, 3600}
                : new long[]{-60, -30, -5, -1, 0, 1, 5, 30, 60};
        int start = kind == DurationKind.WASH ? 10 : 9;
        for (int i = 0; i < deltas.length; i++) {
            long delta = deltas[i];
            int slot = start + i;
            if (delta == 0) {
                box.setItem(slot, detailed(kind.item, kind.label,
                        value("Current: " + CleanConfig.formatDuration(current)),
                        gray("Click this center icon to reset only this setting."),
                        gray("Default: " + CleanConfig.formatDuration(kind.defaultValue(alertIndex)))));
            } else {
                String label = (delta > 0 ? "+" : "-") + CleanConfig.formatDuration(Math.abs(delta));
                box.setItem(slot, detailed(delta < 0 ? Items.RED_DYE : Items.LIME_DYE, label,
                        gray("Adjusts the current value by " + CleanConfig.formatDuration(Math.abs(delta)) + "."),
                        gray("Values cannot go below one second.")));
            }
        }
        if (kind.optional) box.setItem(21, detailed(Items.LEVER, kind.enabled() ? "Disable" : "Enable",
                value("Current: " + enabled(kind.enabled())),
                gray(kind.optionalDescription)));
        back(box, 22);
        if (kind == DurationKind.ALERT) box.setItem(23, detailed(Items.BARRIER, "Remove This Alert",
                red("Removes this alert time from the schedule.")));
        player.openMenu(provider(box, 3, kind.label, (slot, p, button, type) -> {
            int deltaIndex = slot - start;
            if (deltaIndex >= 0 && deltaIndex < deltas.length) {
                long delta = deltas[deltaIndex];
                long adjusted = delta == 0 ? kind.defaultValue(alertIndex) : Math.max(1, kind.get(alertIndex) + delta);
                kind.set(alertIndex, adjusted);
                if (delta == 0) kind.resetEnabledState();
                saveQuietly();
                kind.afterChange(p);
                int nextIndex = kind == DurationKind.ALERT ? CleanConfig.get().alertWhenSeconds.indexOf(adjusted) : alertIndex;
                if (kind == DurationKind.ALERT && nextIndex < 0) openAlertsLater(p);
                else openDurationLater(p, kind, nextIndex);
            } else if (slot == 21 && kind.optional) {
                kind.toggle();
                saveQuietly();
                kind.afterChange(p);
                openDurationLater(p, kind, alertIndex);
            } else if (slot == 22) {
                if (kind == DurationKind.ALERT) openAlertsLater(p);
                else if (kind == DurationKind.WASH) openPondLater(p, 0, false);
                else later(p, () -> openCleanupCategory(p));
            } else if (slot == 23 && kind == DurationKind.ALERT) {
                CleanConfig.get().alertWhenSeconds.remove(alertIndex);
                saveQuietly();
                openAlertsLater(p);
            }
        }));
    }

    private static void openAlerts(ServerPlayer player) {
        SimpleContainer box = decorated(54);
        List<Long> alerts = CleanConfig.get().alertWhenSeconds;
        for (int i = 0; i < Math.min(45, alerts.size()); i++) {
            box.setItem(i, detailed(Items.CLOCK, "Alert " + (i + 1),
                    value("Current: " + CleanConfig.formatDuration(alerts.get(i))),
                    gray("Sends an alert when this much time remains."),
                    green("Click to edit or remove.")));
        }
        back(box, 45);
        box.setItem(49, detailed(Items.BELL, "Reset Alert Schedule",
                value("Default: " + formatAlerts(CleanConfig.DEFAULT_ALERTS)),
                gray("Click this center icon to replace the entire schedule with its defaults.")));
        if (alerts.size() < 45) box.setItem(53, detailed(Items.LIME_DYE, "Add Alert",
                gray("Adds a new 10-second alert and opens its duration editor.")));
        player.openMenu(provider(box, 6, "Scheduled Alerts", (slot, p, button, type) -> {
            if (slot >= 0 && slot < alerts.size() && slot < 45) openDurationLater(p, DurationKind.ALERT, slot);
            else if (slot == 45) later(p, () -> openAlertsCategory(p));
            else if (slot == 49) {
                CleanConfig.get().alertWhenSeconds = new ArrayList<>(CleanConfig.DEFAULT_ALERTS);
                saveQuietly();
                openAlertsLater(p);
            } else if (slot == 53 && alerts.size() < 45) {
                alerts.add(10L);
                saveQuietly();
                int index = CleanConfig.get().alertWhenSeconds.indexOf(10L);
                openDurationLater(p, DurationKind.ALERT, index);
            }
        }));
    }

    public static void openPond(ServerPlayer player, int page, boolean filterMode) {
        int safePage = Math.max(0, Math.min(2, page));
        CleanBillData data = CleanBillData.get(player.getServer());
        SimpleContainer box = takeOnlyContainer(54);
        for (int i = 0; i < STORAGE_PER_PAGE; i++) box.setItem(i, data.pond.get(safePage * STORAGE_PER_PAGE + i));
        fillToolbar(box);
        boolean op = isOp(player);
        box.setItem(45, detailed(CleanConfig.get().itemPondEnabled ? Items.LIME_DYE : Items.RED_DYE,
                "Collection: " + enabled(CleanConfig.get().itemPondEnabled),
                gray("Controls whether future cleanup drops are stored or destroyed."),
                gray("Disabling collection keeps all existing and filtered items."),
                op ? green("Operator: left-click toggles; right-click resets to enabled.") : red("Operators only.")));
        box.setItem(46, detailed(Items.PLAYER_HEAD, "Access: " + CleanConfig.get().itemPondAccess,
                gray("Controls who may open the Pond and retrieve items."),
                gray("Default: operators. Pond controls remain operator-only."),
                op ? green("Operator: left-click toggles; right-click resets to operators.") : red("Operators only.")));
        box.setItem(47, detailed(filterMode ? Items.HOPPER : Items.CHEST, filterMode ? "Filter Mode: enabled" : "Filtered Storage",
                gray("Filtered stacks are protected from every Pond wash."),
                gray("Left-click toggles filter mode; then click a Pond stack to protect it."),
                gray("Right-click opens the 45-slot filtered inventory."),
                filterMode ? green("Click a Pond stack to move it safely.") : value("Filtered: " + data.filteredStackCount() + "/45")));
        if (safePage > 0) box.setItem(48, detailed(Items.ARROW, "Previous Page", gray("Opens page " + safePage + " of 3.")));
        box.setItem(49, detailed(op ? Items.WATER_BUCKET : Items.BARRIER, "Wash Item Pond",
                gray("Deletes all normal Pond contents after confirmation."),
                gray("Filtered storage is never washed."),
                op ? red("Operator: click to continue.") : red("Operators only.")));
        if (safePage < 2) box.setItem(50, detailed(Items.ARROW, "Next Page", gray("Opens page " + (safePage + 2) + " of 3.")));
        box.setItem(51, detailed(Items.CLOCK, "Wash Interval",
                value("Current: " + (CleanConfig.get().itemPondWashEnabled
                        ? CleanConfig.formatDuration(CleanConfig.get().itemPondWashSeconds) : "disabled")),
                gray("Automatically deletes normal Pond contents at this interval."),
                gray("Filtered storage is excluded."),
                op ? green("Operator: click to edit.") : red("Operators only.")));
        box.setItem(52, detailed(Items.PAPER, "Pond Status",
                value("Page " + (safePage + 1) + "/3"),
                gray("Normal storage: " + data.pondStackCount() + "/135 stacks"),
                gray("Filtered storage: " + data.filteredStackCount() + "/45 stacks"),
                gray("Players may take items but cannot deposit them.")));
        box.setItem(53, detailed(Items.BARRIER, "Close", gray("Closes the Item Pond.")));
        player.openMenu(new SimpleMenuProvider((id, inventory, ignored) ->
                new PondMenu(id, inventory, box, safePage, filterMode, player),
                title("Item Pond — " + (safePage + 1) + "/3")));
    }

    private static void openFiltered(ServerPlayer player) {
        CleanBillData data = CleanBillData.get(player.getServer());
        SimpleContainer box = takeOnlyContainer(54);
        for (int i = 0; i < CleanBillData.FILTERED_SIZE; i++) box.setItem(i, data.filtered.get(i));
        fillToolbar(box);
        box.setItem(49, detailed(Items.ARROW, "Back to Item Pond",
                gray("Filtered items are never removed by automatic or manual washing."),
                gray("Items can only enter here through Pond filter mode.")));
        player.openMenu(new SimpleMenuProvider((id, inventory, ignored) ->
                new FilteredMenu(id, inventory, box, player), title("Filtered Storage — " + data.filteredStackCount() + "/45")));
    }

    private static void openConfirmWash(ServerPlayer player, int returnPage) {
        SimpleContainer box = decorated(27);
        box.setItem(11, detailed(Items.WATER_BUCKET, "Confirm Wash",
                red("Permanently deletes every normal Item Pond stack."),
                gray("Filtered storage will not be touched.")));
        box.setItem(15, detailed(Items.ARROW, "Go Back", gray("Keeps all Pond items.")));
        player.openMenu(provider(box, 3, "Wash the Item Pond?", (slot, p, button, type) -> {
            if (slot == 11 && isOp(p)) {
                int count = CleanBillData.get(p.getServer()).clearPond();
                closePondViewers(p.getServer(), "");
                Component message = TextUtil.gray("Item Pond washed by ")
                        .append(p.getDisplayName().copy().withStyle(style -> style.withColor(TextUtil.PALE_PURPLE)))
                        .append(TextUtil.gray(". Removed ")).append(TextUtil.green(Integer.toString(count)))
                        .append(TextUtil.gray(" items."));
                for (ServerPlayer online : p.getServer().getPlayerList().getPlayers()) online.sendSystemMessage(message);
                openPondLater(p, returnPage, false);
            } else if (slot == 15) openPondLater(p, returnPage, false);
        }));
    }

    public static final class PondMenu extends ChestMenu {
        private final SimpleContainer top;
        private final int page;
        private final boolean filterMode;
        private final ServerPlayer owner;

        PondMenu(int id, Inventory inventory, SimpleContainer top, int page, boolean filterMode, ServerPlayer owner) {
            super(MenuType.GENERIC_9x6, id, inventory, top, 6);
            this.top = top;
            this.page = page;
            this.filterMode = filterMode;
            this.owner = owner;
        }

        @Override public void clicked(int slot, int button, ClickType type, Player player) {
            if (slot >= 0 && slot < STORAGE_PER_PAGE) {
                if (filterMode) {
                    int pondSlot = page * STORAGE_PER_PAGE + slot;
                    if (top.getItem(slot).isEmpty()) return;
                    if (!CleanBillData.get(owner.getServer()).moveToFiltered(pondSlot)) {
                        owner.sendSystemMessage(TextUtil.red("Filtered storage is full."));
                    }
                    openPondLater(owner, page, true);
                } else if (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE) {
                    take(top, slot, button == 1 && type == ClickType.PICKUP, owner,
                            CleanBillData.get(owner.getServer()).pond, page * STORAGE_PER_PAGE);
                }
                return;
            }
            if (slot >= 45 && slot < 54) {
                if (slot == 45 && isOp(owner)) {
                    CleanConfig.get().itemPondEnabled = button == 1 || !CleanConfig.get().itemPondEnabled;
                    saveQuietly();
                    openPondLater(owner, page, filterMode);
                } else if (slot == 46 && isOp(owner)) {
                    CleanConfig.get().itemPondAccess = button == 1 ? "ops"
                            : CleanConfig.get().itemPondAccess.equals("ops") ? "all" : "ops";
                    saveQuietly();
                    openPondLater(owner, page, filterMode);
                } else if (slot == 47) {
                    if (button == 1) later(owner, () -> openFiltered(owner));
                    else openPondLater(owner, page, !filterMode);
                } else if (slot == 48 && page > 0) openPondLater(owner, page - 1, filterMode);
                else if (slot == 49 && isOp(owner)) later(owner, () -> openConfirmWash(owner, page));
                else if (slot == 50 && page < 2) openPondLater(owner, page + 1, filterMode);
                else if (slot == 51 && isOp(owner)) openDurationLater(owner, DurationKind.WASH, -1);
                else if (slot == 53) owner.closeContainer();
                return;
            }
            super.clicked(slot, button, type, player);
        }

        @Override public ItemStack quickMoveStack(Player player, int slot) {
            if (slot >= 0 && slot < STORAGE_PER_PAGE) {
                take(top, slot, false, owner, CleanBillData.get(owner.getServer()).pond, page * STORAGE_PER_PAGE);
            }
            return ItemStack.EMPTY;
        }
    }

    private static final class FilteredMenu extends ChestMenu {
        private final SimpleContainer top;
        private final ServerPlayer owner;

        FilteredMenu(int id, Inventory inventory, SimpleContainer top, ServerPlayer owner) {
            super(MenuType.GENERIC_9x6, id, inventory, top, 6);
            this.top = top;
            this.owner = owner;
        }

        @Override public void clicked(int slot, int button, ClickType type, Player player) {
            if (slot >= 0 && slot < CleanBillData.FILTERED_SIZE) {
                if (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE) {
                    take(top, slot, button == 1 && type == ClickType.PICKUP, owner,
                            CleanBillData.get(owner.getServer()).filtered, 0);
                }
                return;
            }
            if (slot >= 45 && slot < 54) {
                if (slot == 49) openPondLater(owner, 0, false);
                return;
            }
            super.clicked(slot, button, type, player);
        }

        @Override public ItemStack quickMoveStack(Player player, int slot) {
            if (slot >= 0 && slot < CleanBillData.FILTERED_SIZE) {
                take(top, slot, false, owner, CleanBillData.get(owner.getServer()).filtered, 0);
            }
            return ItemStack.EMPTY;
        }
    }

    private static void take(SimpleContainer top, int visibleSlot, boolean half, ServerPlayer player,
                             List<ItemStack> backing, int offset) {
        ItemStack source = top.getItem(visibleSlot);
        if (source.isEmpty()) return;
        int amount = half ? (source.getCount() + 1) / 2 : source.getCount();
        ItemStack moving = source.split(amount);
        player.getInventory().add(moving);
        if (!moving.isEmpty()) source.grow(moving.getCount());
        top.setItem(visibleSlot, source.isEmpty() ? ItemStack.EMPTY : source);
        backing.set(offset + visibleSlot, top.getItem(visibleSlot));
        CleanBillData.get(player.getServer()).setDirty();
    }

    public static void closePondViewers(net.minecraft.server.MinecraftServer server, String reason) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof PondMenu) {
                player.closeContainer();
                if (!reason.isBlank()) player.sendSystemMessage(TextUtil.gray(reason));
            }
        }
    }

    private static final class ActionMenu extends ChestMenu {
        private final int topSlots;
        private final MenuAction action;

        ActionMenu(int id, Inventory inventory, SimpleContainer container, int rows, MenuAction action) {
            super(type(rows), id, inventory, container, rows);
            this.topSlots = rows * 9;
            this.action = action;
        }

        @Override public void clicked(int slot, int button, ClickType type, Player player) {
            if (slot >= 0 && slot < topSlots) {
                action.run(slot, (ServerPlayer) player, button, type);
                return;
            }
            super.clicked(slot, button, type, player);
        }

        @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
    }

    private interface MenuAction { void run(int slot, ServerPlayer player, int button, ClickType type); }

    private enum DurationKind {
        INTERVAL("Cleanup Interval", Items.CLOCK, false, ""),
        COUNTDOWN("Countdown", Items.REPEATER, false, ""),
        MIN_AGE("Minimum Item Age", Items.CLOCK, true, "Disabled means every timestamped item is eligible."),
        WASH("Item Pond Wash", Items.WATER_BUCKET, true, "Disabled means the Pond is never washed automatically."),
        ALERT("Scheduled Alert", Items.BELL, false, "");

        final String label;
        final Item item;
        final boolean optional;
        final String optionalDescription;

        DurationKind(String label, Item item, boolean optional, String optionalDescription) {
            this.label = label;
            this.item = item;
            this.optional = optional;
            this.optionalDescription = optionalDescription;
        }

        long get(int index) { return switch (this) {
            case INTERVAL -> CleanConfig.get().intervalSeconds;
            case COUNTDOWN -> CleanConfig.get().countdownSeconds;
            case MIN_AGE -> CleanConfig.get().minAgeSeconds;
            case WASH -> CleanConfig.get().itemPondWashSeconds;
            case ALERT -> CleanConfig.get().alertWhenSeconds.get(index);
        }; }

        void set(int index, long value) { switch (this) {
            case INTERVAL -> CleanConfig.get().intervalSeconds = value;
            case COUNTDOWN -> CleanConfig.get().countdownSeconds = value;
            case MIN_AGE -> CleanConfig.get().minAgeSeconds = value;
            case WASH -> CleanConfig.get().itemPondWashSeconds = value;
            case ALERT -> CleanConfig.get().alertWhenSeconds.set(index, value);
        } }

        long defaultValue(int index) { return switch (this) {
            case INTERVAL -> CleanConfig.DEFAULT_INTERVAL;
            case COUNTDOWN -> CleanConfig.DEFAULT_COUNTDOWN;
            case MIN_AGE -> CleanConfig.DEFAULT_MIN_AGE;
            case WASH -> CleanConfig.DEFAULT_WASH;
            case ALERT -> 10L;
        }; }

        boolean enabled() { return this == MIN_AGE ? CleanConfig.get().minAgeEnabled : CleanConfig.get().itemPondWashEnabled; }
        void toggle() {
            if (this == MIN_AGE) CleanConfig.get().minAgeEnabled = !CleanConfig.get().minAgeEnabled;
            else CleanConfig.get().itemPondWashEnabled = !CleanConfig.get().itemPondWashEnabled;
        }
        void resetEnabledState() {
            if (this == MIN_AGE) CleanConfig.get().minAgeEnabled = false;
            else if (this == WASH) CleanConfig.get().itemPondWashEnabled = true;
        }
        void afterChange(ServerPlayer player) {
            if (this == INTERVAL) CleanBill.CLEANUP.resetTimer(player.getServer());
            if (this == WASH) {
                CleanBillData data = CleanBillData.get(player.getServer());
                data.nextWash = player.serverLevel().getGameTime() + CleanConfig.get().itemPondWashSeconds * 20;
                data.setDirty();
            }
        }
    }

    private enum ChoiceKind {
        ALERT_SHOW("Alert Display", Items.OAK_SIGN, List.of("chat", "actionbar", "none"), "chat"),
        ALERT_SOUND("Alert Sound", Items.NOTE_BLOCK, List.of("jukebox", "exp", "none"), "jukebox"),
        COUNTDOWN_SHOW("Countdown Display", Items.REDSTONE_TORCH, List.of("enabled", "disabled"), "enabled"),
        BUTTON_ACCESS("Clickable Controls", Items.OAK_BUTTON, List.of("all", "ops"), "all");

        final String label;
        final Item item;
        final List<String> choices;
        final String defaultValue;

        ChoiceKind(String label, Item item, List<String> choices, String defaultValue) {
            this.label = label;
            this.item = item;
            this.choices = choices;
            this.defaultValue = defaultValue;
        }

        String get() { return switch (this) {
            case ALERT_SHOW -> CleanConfig.get().alertShow;
            case ALERT_SOUND -> CleanConfig.get().alertSound;
            case COUNTDOWN_SHOW -> enabled(CleanConfig.get().countdownShow);
            case BUTTON_ACCESS -> CleanConfig.get().buttonAccess;
        }; }

        void set(String value) { switch (this) {
            case ALERT_SHOW -> CleanConfig.get().alertShow = value;
            case ALERT_SOUND -> CleanConfig.get().alertSound = value;
            case COUNTDOWN_SHOW -> CleanConfig.get().countdownShow = value.equals("enabled");
            case BUTTON_ACCESS -> CleanConfig.get().buttonAccess = value;
        } }
    }

    private enum MessageKind {
        ALERT("Scheduled Alert Message", Items.PAPER, CleanConfig.DEFAULT_ALERT_MESSAGE, "alert"),
        COUNTDOWN("Countdown Message", Items.MAP, CleanConfig.DEFAULT_COUNTDOWN_MESSAGE, "countdown"),
        CLEAR("Cleanup Result Message", Items.WRITABLE_BOOK, CleanConfig.DEFAULT_CLEAR_MESSAGE, "clear");

        final String label;
        final Item item;
        final String defaultValue;
        final String preview;

        MessageKind(String label, Item item, String defaultValue, String preview) {
            this.label = label;
            this.item = item;
            this.defaultValue = defaultValue;
            this.preview = preview;
        }

        String get() { return switch (this) {
            case ALERT -> CleanConfig.get().alertMessage;
            case COUNTDOWN -> CleanConfig.get().countdownMessage;
            case CLEAR -> CleanConfig.get().clearMessage;
        }; }

        void set(String value) { switch (this) {
            case ALERT -> CleanConfig.get().alertMessage = value;
            case COUNTDOWN -> CleanConfig.get().countdownMessage = value;
            case CLEAR -> CleanConfig.get().clearMessage = value;
        } }
    }

    private static SimpleMenuProvider provider(SimpleContainer box, int rows, String title, MenuAction action) {
        return new SimpleMenuProvider((id, inventory, ignored) -> new ActionMenu(id, inventory, box, rows, action), title(title));
    }

    private static SimpleContainer decorated(int size) {
        SimpleContainer box = takeOnlyContainer(size);
        ItemStack pane = named(Items.BLACK_STAINED_GLASS_PANE, " ", TextUtil.LIGHT_GRAY);
        for (int i = 0; i < size; i++) box.setItem(i, pane.copy());
        return box;
    }

    private static SimpleContainer takeOnlyContainer(int size) {
        return new SimpleContainer(size) {
            @Override public boolean canPlaceItem(int slot, ItemStack stack) { return false; }
            @Override public boolean stillValid(Player ignored) { return true; }
        };
    }

    private static void fillToolbar(SimpleContainer box) {
        ItemStack pane = named(Items.BLACK_STAINED_GLASS_PANE, " ", TextUtil.LIGHT_GRAY);
        for (int i = 45; i < 54; i++) box.setItem(i, pane.copy());
    }

    private static ItemStack named(Item item, String name, int color) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name)
                .withStyle(style -> style.withColor(color).withItalic(false)));
        return stack;
    }

    private static ItemStack detailed(Item item, String name, Component... lines) {
        int nameColor = name.startsWith("+") || name.startsWith("Previous") || name.startsWith("Next")
                || name.startsWith("Back") || name.startsWith("Go Back") ? TextUtil.PASTEL_GREEN
                : name.startsWith("-") || name.startsWith("Remove") || name.startsWith("Confirm")
                ? TextUtil.PASTEL_RED : TextUtil.LIGHT_GRAY;
        ItemStack stack = named(item, name, nameColor);
        stack.set(DataComponents.LORE, new ItemLore(List.of(lines)));
        return stack;
    }

    private static ItemStack category(Item item, String name, Component... lines) {
        ItemStack stack = named(item, name, TextUtil.PALE_PURPLE);
        stack.set(DataComponents.LORE, new ItemLore(List.of(lines)));
        return stack;
    }

    private static ItemStack choiceItem(Item item, String name, String current, String explanation) {
        return detailed(item, name, value("Current: " + current), gray(explanation),
                gray("Click to select an option. The center icon resets this setting."));
    }

    private static ItemStack messageItem(Item item, String name, String current, String explanation) {
        return detailed(item, name, value("Current: " + current), gray(explanation),
                gray("Click to edit, preview, or reset this message."));
    }

    private static ItemStack previewItem(Item item, String name, String explanation) {
        return detailed(item, name, gray(explanation), gray("No timer, cleanup, or public message is triggered."));
    }

    private static Component gray(String text) {
        return TextUtil.gray(text).withStyle(style -> style.withItalic(false));
    }

    private static Component green(String text) {
        return TextUtil.green(text).withStyle(style -> style.withItalic(false));
    }

    private static Component red(String text) {
        return TextUtil.red(text).withStyle(style -> style.withItalic(false));
    }

    private static Component value(String text) {
        return TextUtil.purple(text).withStyle(style -> style.withItalic(false));
    }

    private static Component title(String text) { return TextUtil.purple(text); }
    private static void back(SimpleContainer box, int slot) { box.setItem(slot, detailed(Items.ARROW, "Back", gray("Returns to the previous menu."))); }
    private static MenuType<?> type(int rows) { return rows == 3 ? MenuType.GENERIC_9x3 : MenuType.GENERIC_9x6; }
    private static boolean isOp(ServerPlayer player) { return player.getServer().getPlayerList().isOp(player.getGameProfile()); }
    private static String enabled(boolean value) { return value ? "enabled" : "disabled"; }
    private static String formatAlerts(List<Long> alerts) {
        return alerts.isEmpty() ? "none" : String.join(", ", alerts.stream().map(CleanConfig::formatDuration).toList());
    }

    private static void saveQuietly() {
        try { CleanConfig.save(); }
        catch (IOException exception) { CleanBill.LOGGER.error("Could not save configuration", exception); }
    }

    private static void error(ServerPlayer player, String message) { player.sendSystemMessage(TextUtil.red(message)); }
    private static void later(ServerPlayer player, Runnable action) { player.getServer().execute(action); }
    private static void openConfigLater(ServerPlayer player) { later(player, () -> openConfig(player)); }
    private static void openPondLater(ServerPlayer player, int page, boolean filterMode) { later(player, () -> openPond(player, page, filterMode)); }
    private static void openDurationLater(ServerPlayer player, DurationKind kind, int index) { later(player, () -> openDuration(player, kind, index)); }
    private static void openChoiceLater(ServerPlayer player, ChoiceKind kind) { later(player, () -> openChoice(player, kind)); }
    private static void openAlertsLater(ServerPlayer player) { later(player, () -> openAlerts(player)); }
    private static void openMessagesLater(ServerPlayer player) { later(player, () -> openMessagesCategory(player)); }
    private static void openMessageLater(ServerPlayer player, MessageKind kind) { later(player, () -> openMessage(player, kind)); }
    private static void run(ServerPlayer player, String command) {
        player.closeContainer();
        player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
    }
}
