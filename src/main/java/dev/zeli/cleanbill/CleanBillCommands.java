package dev.zeli.cleanbill;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class CleanBillCommands {
    private CleanBillCommands() {}

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(root("cb"));
        dispatcher.register(root("cleanbill"));
        // Legacy aliases remain available so existing buttons and habits keep working.
        dispatcher.register(root("qc"));
        dispatcher.register(root("quackyclean"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> root(String name) {
        return Commands.literal(name)
                .executes(context -> help(context.getSource()))
                .then(Commands.literal("help").executes(context -> help(context.getSource())))
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("clean").requires(CleanBillCommands::canUseButtons)
                        .executes(context -> clean(context.getSource())))
                .then(Commands.literal("cancel").requires(CleanBillCommands::canUseButtons)
                        .executes(context -> cancel(context.getSource())))
                .then(Commands.literal("pause").requires(source -> source.hasPermission(2))
                        .executes(context -> pause(context.getSource())))
                .then(Commands.literal("resume").requires(source -> source.hasPermission(2))
                        .executes(context -> resume(context.getSource())))
                .then(Commands.literal("config").requires(source -> source.hasPermission(2))
                        .executes(context -> openConfig(context.getSource())))
                .then(Commands.literal("itempond").requires(CleanBillCommands::canOpenPond)
                        .executes(context -> openPond(context.getSource())));
    }

    private static int help(CommandSourceStack source) {
        source.sendSystemMessage(TextUtil.purple("Clean Bill"));
        source.sendSystemMessage(TextUtil.gray("General: /cb help, /cb status"));
        source.sendSystemMessage(TextUtil.gray("Cleanup: /cb clean, /cb cancel, /cb pause, /cb resume"));
        source.sendSystemMessage(TextUtil.gray("Interfaces: /cb config, /cb itempond"));
        source.sendSystemMessage(TextUtil.gray("All settings are controlled through /cb config."));
        return 1;
    }

    private static int status(CommandSourceStack source) {
        CleanBill.CLEANUP.broadcastStatus(source.getServer(), source.getDisplayName());
        return 1;
    }

    private static int clean(CommandSourceStack source) {
        if (!CleanBill.CLEANUP.beginCleanup(source.getServer(), source.getDisplayName())) {
            source.sendFailure(TextUtil.red("A cleanup is already running."));
            return 0;
        }
        return 1;
    }

    private static int cancel(CommandSourceStack source) {
        CleanBill.CLEANUP.resetTimer(source.getServer());
        CleanBill.CLEANUP.broadcastAction(source.getServer(), "Cleanup delayed by ", source.getDisplayName());
        return 1;
    }

    private static int pause(CommandSourceStack source) {
        CleanBill.CLEANUP.pause(source.getServer());
        CleanBill.CLEANUP.broadcastAction(source.getServer(), "Cleanup paused by ", source.getDisplayName());
        return 1;
    }

    private static int resume(CommandSourceStack source) {
        CleanBill.CLEANUP.resume(source.getServer());
        CleanBill.CLEANUP.broadcastAction(source.getServer(), "Cleanup resumed by ", source.getDisplayName());
        return 1;
    }

    private static int openConfig(CommandSourceStack source) {
        try {
            CleanBillMenus.openConfig(source.getPlayerOrException());
            return 1;
        } catch (Exception exception) {
            source.sendFailure(TextUtil.red("The configuration GUI can only be opened in game."));
            return 0;
        }
    }

    private static int openPond(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            CleanBillMenus.openPond(player, 0, false);
            return 1;
        } catch (Exception exception) {
            source.sendFailure(TextUtil.red("The Item Pond can only be opened in game."));
            return 0;
        }
    }

    private static boolean canUseButtons(CommandSourceStack source) {
        return CleanConfig.get().buttonAccess.equals("all") || source.hasPermission(2);
    }

    private static boolean canOpenPond(CommandSourceStack source) {
        return CleanConfig.get().itemPondAccess.equals("all") || source.hasPermission(2);
    }
}
