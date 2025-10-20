package com.ahhmino.adsync.commands;

import com.ahhmino.adsync.AdSyncPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AdSyncCommand implements CommandExecutor, TabCompleter {
    private final AdSyncPlugin plugin;

    public AdSyncCommand(AdSyncPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("adsync.use")) {
            sender.sendMessage("§cYou don’t have permission.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§eUsage: /adsync <enable|disable|reload|scan>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "enable" -> {
                plugin.setSyncEnabled(true);
                sender.sendMessage("§aAdSync enabled.");
                plugin.resyncAllPlayers();
            }
            case "disable" -> {
                plugin.setSyncEnabled(false);
                sender.sendMessage("§cAdSync disabled.");
            }
            case "reload" -> {
                plugin.reloadConfig();
                sender.sendMessage("§aAdSync config reloaded.");
            }
            case "scan" -> {
                plugin.resyncAllPlayers();
                sender.sendMessage("§aAll player advancements resynchronized.");
            }
            default -> sender.sendMessage("§eUsage: /adsync <enable|disable|reload|scan>");
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (!sender.hasPermission("adsync.use")) return Collections.emptyList();

        if (args.length == 1) {
            return Arrays.asList("enable", "disable", "reload", "scan");
        }

        return Collections.emptyList();
    }
}
