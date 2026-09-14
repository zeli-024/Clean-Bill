package dev.zeli.cleanbill;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

public final class CleanupManager {
    private static final String BORN = "CleanBillBorn";
    private static final String LEGACY_BORN = "QuackyCleanBorn";
    private static final int MAX_PER_TICK = 200;
    private final Queue<ItemEntity> pending = new ArrayDeque<>();
    private final Set<UUID> queued = new HashSet<>();
    private long lastTimerSecond = Long.MIN_VALUE;
    private boolean clearing;
    private int removed;
    private int storedStacks;
    private int overflowItems;
    private int protectedItems;

    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof ItemEntity item) || !(event.getLevel() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        var persistent = item.getPersistentData();
        if (!persistent.contains(BORN)) {
            if (persistent.contains(LEGACY_BORN)) {
                persistent.putLong(BORN, persistent.getLong(LEGACY_BORN));
            } else {
                persistent.putLong(BORN, now);
                return;
            }
        }
        if (event.loadedFromDisk()) {
            CleanBillData data = CleanBillData.get(level.getServer());
            if (data.lastCleanup >= 0 && eligibleAt(item, data.lastCleanup)) queue(item);
        }
    }

    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        drain(server);
        if (server.getTickCount() % 20 != 0) return;
        CleanBillData data = CleanBillData.get(server);
        long now = server.overworld().getGameTime();
        ensureTimers(data, now);
        washIfDue(server, data, now);
        if (data.paused || clearing) return;

        long remaining = Math.max(0, (data.nextCleanup - now + 19) / 20);
        // The last visible countdown number is 2; the following second performs the cleanup.
        if (remaining <= 1) {
            beginCleanup(server);
            return;
        }
        if (remaining == lastTimerSecond) return;
        lastTimerSecond = remaining;
        CleanConfig.Values config = CleanConfig.get();
        boolean countdown = remaining > 0 && remaining <= config.countdownSeconds;
        if (countdown && config.countdownShow) {
            announce(server, config.countdownMessage, remaining, true);
        } else if (remaining > 0 && config.alertWhenSeconds.contains(remaining)) {
            announce(server, config.alertMessage, remaining, true);
        }
    }

    private void ensureTimers(CleanBillData data, long now) {
        CleanConfig.Values config = CleanConfig.get();
        boolean changed = false;
        if (data.nextCleanup < 0) { data.nextCleanup = nextCleanupTime(now, config.intervalSeconds); changed = true; }
        if (data.nextWash < 0) { data.nextWash = now + config.itemPondWashSeconds * 20; changed = true; }
        if (changed) data.setDirty();
    }

    private void washIfDue(MinecraftServer server, CleanBillData data, long now) {
        CleanConfig.Values config = CleanConfig.get();
        if (!config.itemPondWashEnabled) return;
        if (now >= data.nextWash) {
            int items = data.clearPond();
            data.nextWash = now + config.itemPondWashSeconds * 20;
            data.setDirty();
            CleanBill.LOGGER.info("Item Pond wash removed {} items", items);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.containerMenu instanceof CleanBillMenus.PondMenu) {
                    player.closeContainer();
                    player.sendSystemMessage(TextUtil.gray("The Item Pond was washed."));
                }
            }
        }
    }

    public boolean beginCleanup(MinecraftServer server) {
        return beginCleanup(server, null);
    }

    public boolean beginCleanup(MinecraftServer server, Component actor) {
        if (clearing) return false;
        clearing = true;
        removed = storedStacks = overflowItems = protectedItems = 0;
        if (actor != null) broadcastAction(server, "Cleanup started by ", actor);
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof ItemEntity item && item.isAlive()) {
                    stampIfMissing(item, level.getGameTime());
                    if (eligibleNow(item, level.getGameTime())) queue(item); else protectedItems++;
                }
            }
        }
        if (pending.isEmpty()) finishCleanup(server);
        return true;
    }

    private void drain(MinecraftServer server) {
        int budget = MAX_PER_TICK;
        CleanBillData data = CleanBillData.get(server);
        boolean pondViewersClosed = false;
        while (budget-- > 0 && !pending.isEmpty()) {
            ItemEntity item = pending.remove();
            queued.remove(item.getUUID());
            if (!item.isAlive()) continue;
            if (CleanConfig.get().itemPondEnabled) {
                if (!pondViewersClosed) {
                    CleanBillMenus.closePondViewers(server, "Item Pond closed while new cleanup items are stored.");
                    pondViewersClosed = true;
                }
                overflowItems += data.addToPond(item.getItem());
                storedStacks++;
            }
            removed += item.getItem().getCount();
            item.discard();
        }
        if (clearing && pending.isEmpty()) finishCleanup(server);
    }

    private void finishCleanup(MinecraftServer server) {
        clearing = false;
        CleanBillData data = CleanBillData.get(server);
        long now = server.overworld().getGameTime();
        data.lastCleanup = now;
        data.nextCleanup = nextCleanupTime(now, CleanConfig.get().intervalSeconds);
        data.setDirty();
        lastTimerSecond = Long.MIN_VALUE;
        announceClear(server);
        CleanBill.LOGGER.info("Cleared {} ground items; {} protected by minage; {} overflowed from Item Pond", removed, protectedItems, overflowItems);
    }

    public void resetTimer(MinecraftServer server) {
        CleanBillData data = CleanBillData.get(server);
        long now = server.overworld().getGameTime();
        data.nextCleanup = nextCleanupTime(now, CleanConfig.get().intervalSeconds);
        data.remainingWhenPaused = -1;
        data.paused = false;
        data.setDirty();
        lastTimerSecond = Long.MIN_VALUE;
    }

    public void pause(MinecraftServer server) {
        CleanBillData data = CleanBillData.get(server);
        if (!data.paused) {
            data.remainingWhenPaused = Math.max(1, data.nextCleanup - server.overworld().getGameTime());
            data.paused = true;
            data.setDirty();
        }
    }

    public void resume(MinecraftServer server) {
        CleanBillData data = CleanBillData.get(server);
        if (data.paused) {
            data.nextCleanup = server.overworld().getGameTime() + Math.max(1, data.remainingWhenPaused);
            data.remainingWhenPaused = -1;
            data.paused = false;
            data.setDirty();
            lastTimerSecond = Long.MIN_VALUE;
        }
    }

    public long remainingSeconds(MinecraftServer server) {
        CleanBillData data = CleanBillData.get(server);
        long ticks = data.paused ? data.remainingWhenPaused : data.nextCleanup - server.overworld().getGameTime();
        return Math.max(0, (ticks + 19) / 20 - 1);
    }

    private long nextCleanupTime(long now, long intervalSeconds) {
        // One internal lead second lets the visible sequence replace "1" with the cleanup itself.
        return now + (intervalSeconds + 1) * 20;
    }

    public boolean isClearing() { return clearing; }

    public void broadcastAction(MinecraftServer server, String prefix, Component actor) {
        Component message = TextUtil.gray(prefix)
                .append(actor.copy().withStyle(style -> style.withColor(TextUtil.PALE_PURPLE)));
        broadcastNotice(server, message);
    }

    public void broadcastStatus(MinecraftServer server, Component actor) {
        CleanBillData data = CleanBillData.get(server);
        String state = data.paused ? "Paused: " : clearing ? "Cleaning. Next: " : "Next cleanup: ";
        Component message = TextUtil.gray("Status requested by ")
                .append(actor.copy().withStyle(style -> style.withColor(TextUtil.PALE_PURPLE)))
                .append(TextUtil.gray(" — " + state))
                .append(TextUtil.red(CleanConfig.formatDuration(remainingSeconds(server))))
                .append(TextUtil.gray(" — Pond: " + data.pondStackCount() + "/"
                        + CleanConfig.get().itemPondCapacity() + ", filtered: "
                        + data.filteredStackCount() + "/45"));
        broadcastNotice(server, message);
    }

    /** Uses a vanilla announcement key so chat mods classify this as system output, not player chat. */
    public void broadcastNotice(MinecraftServer server, Component message) {
        Component announcement = Component.translatable("chat.type.announcement", TextUtil.purple("Clean Bill"), message);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) player.sendSystemMessage(announcement);
    }

    public void preview(ServerPlayer player, String kind) {
        CleanConfig.Values config = CleanConfig.get();
        if (config.alertShow.equals("none")) {
            player.sendSystemMessage(TextUtil.gray("Preview hidden because Alert Display is set to none."));
            return;
        }
        boolean clear = kind.equals("clear");
        boolean actionBar = config.alertShow.equals("actionbar");
        Component message;
        if (actionBar) {
            message = clear
                    ? TextUtil.gray("Cleared ").append(TextUtil.green("128")).append(TextUtil.gray(" items"))
                    : TextUtil.gray("Cleanup in ").append(TextUtil.red(kind.equals("countdown")
                    ? CleanConfig.formatDuration(config.countdownSeconds)
                    : previewAlertTime(config)));
        } else if (clear) {
            message = TextUtil.message(config.clearMessage, Map.of(
                    "count", "128", "stacks", "12", "protected", "4", "overflow", "0", "next", "20m"));
        } else {
            String template = kind.equals("countdown") ? config.countdownMessage : config.alertMessage;
            message = TextUtil.message(template, Map.of("time", kind.equals("countdown")
                    ? CleanConfig.formatDuration(config.countdownSeconds)
                    : previewAlertTime(config)));
        }
        if (actionBar) player.displayClientMessage(message, true);
        else player.sendSystemMessage(clear ? message : message.copy().append(TextUtil.gray(" — ")).append(previewButtons()));
        if (actionBar && !clear) player.sendSystemMessage(message.copy().append(TextUtil.gray(" — ")).append(previewButtons()));
        sound(player, clear);
    }

    private String previewAlertTime(CleanConfig.Values config) {
        return config.alertWhenSeconds.isEmpty() ? "10m" : CleanConfig.formatDuration(config.alertWhenSeconds.getFirst());
    }

    private boolean eligibleNow(ItemEntity item, long now) {
        long age = Math.max(0, now - item.getPersistentData().getLong(BORN));
        return !CleanConfig.get().minAgeEnabled || age >= CleanConfig.get().minAgeSeconds * 20;
    }

    private boolean eligibleAt(ItemEntity item, long cleanupTime) {
        long born = item.getPersistentData().getLong(BORN);
        long requiredAge = CleanConfig.get().minAgeEnabled ? CleanConfig.get().minAgeSeconds * 20 : 0;
        return born + requiredAge <= cleanupTime;
    }

    private void stampIfMissing(ItemEntity item, long now) {
        if (!item.getPersistentData().contains(BORN)) item.getPersistentData().putLong(BORN, now);
    }

    private void queue(ItemEntity item) {
        if (queued.add(item.getUUID())) pending.add(item);
    }

    private void announce(MinecraftServer server, String template, long seconds, boolean showButtons) {
        CleanConfig.Values config = CleanConfig.get();
        if (config.alertShow.equals("none")) return;
        boolean actionBar = config.alertShow.equals("actionbar");
        Component message = actionBar
                ? TextUtil.gray("Cleanup in ").append(TextUtil.red(CleanConfig.formatDuration(seconds)))
                : TextUtil.message(template, Map.of("time", CleanConfig.formatDuration(seconds)));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean controls = showButtons && canUseControls(server, player);
            if (actionBar) player.displayClientMessage(message, true);
            if (!actionBar || controls) {
                Component chatBase = actionBar
                        ? TextUtil.message(template, Map.of("time", CleanConfig.formatDuration(seconds)))
                        : message.copy();
                var chat = chatBase.copy();
                if (controls) chat.append(TextUtil.gray(" — ")).append(clickableControls());
                player.sendSystemMessage(chat);
            }
            sound(player, false);
        }
    }

    private void announceClear(MinecraftServer server) {
        CleanConfig.Values config = CleanConfig.get();
        if (config.alertShow.equals("none")) return;
        Component message = config.alertShow.equals("actionbar")
                ? TextUtil.gray("Cleared ").append(TextUtil.green(Integer.toString(removed))).append(TextUtil.gray(" items"))
                : TextUtil.message(config.clearMessage, Map.of(
                    "count", Integer.toString(removed), "stacks", Integer.toString(storedStacks),
                    "protected", Integer.toString(protectedItems), "overflow", Integer.toString(overflowItems),
                    "next", CleanConfig.formatDuration(config.intervalSeconds)));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.displayClientMessage(message, config.alertShow.equals("actionbar"));
            sound(player, true);
        }
    }

    private Component clickableControls() {
        return Component.literal("[clean now]").withStyle(style -> style.withColor(TextUtil.PASTEL_RED)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cb clean"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextUtil.gray("Clear eligible items now"))))
                .append(TextUtil.gray(" or "))
                .append(Component.literal("[clean later]").withStyle(style -> style.withColor(TextUtil.PASTEL_GREEN)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cb cancel"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextUtil.gray("Reset the cleanup timer")))));
    }

    private boolean canUseControls(MinecraftServer server, ServerPlayer player) {
        return CleanConfig.get().buttonAccess.equals("all")
                || server.getPlayerList().isOp(player.getGameProfile());
    }

    private Component previewButtons() {
        return TextUtil.red("[clean now]")
                .append(TextUtil.gray(" or ")).append(TextUtil.green("[clean later]"))
                .append(TextUtil.gray(" (preview)"));
    }

    private void sound(ServerPlayer player, boolean finalSound) {
        switch (CleanConfig.get().alertSound) {
            case "jukebox" -> player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.MASTER, 0.8f, finalSound ? 1.6f : 0.7f);
            case "exp" -> player.playNotifySound(finalSound ? SoundEvents.PLAYER_LEVELUP : SoundEvents.EXPERIENCE_ORB_PICKUP,
                    SoundSource.MASTER, 0.8f, finalSound ? 1.0f : 0.8f);
            default -> { }
        }
    }
}
