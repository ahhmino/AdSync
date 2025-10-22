package com.ahhmino.adsync.commands;

import com.ahhmino.adsync.AdSyncPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
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
                plugin.loadGlobalAdvancements();
                sender.sendMessage("§aAdSync config and advancement baseline reloaded from disk.");
            }
            case "scan" -> {
                plugin.resyncAllPlayers();
                // Save the world so advancements JSON files are flushed to disk
                if (!Bukkit.getWorlds().isEmpty()) {
                    Bukkit.getWorlds().forEach(World::save);
                }
                sender.sendMessage("§aFull collaborative resync performed and world saved.");
            }
            case "debug" -> {
                sender.sendMessage("§e=== AdSync Debug ===");
                sender.sendMessage("§eSync enabled: " + plugin.isSyncEnabled());
                sender.sendMessage("§eGlobal advancements loaded: " + plugin.globalProgress.size());

                for (Player p : Bukkit.getOnlinePlayers()) {
                    int totalCriteria = 0;
                    for (Iterator<Advancement> it = Bukkit.advancementIterator(); it.hasNext();) {
                        Advancement adv = it.next();
                        if (adv.getKey().getKey().startsWith("recipes/")) continue;
                        totalCriteria += p.getAdvancementProgress(adv).getAwardedCriteria().size();
                    }
                    sender.sendMessage("§a" + p.getName() + " has " + totalCriteria + " criteria awarded.");
                }

                sender.sendMessage("§e=== End Debug ===");
            }

            default -> sender.sendMessage("§eUsage: /adsync <enable|disable|reload|scan>");
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String alias,
                                                @NotNull String[] args) {
        if (!sender.hasPermission("adsync.use")) return Collections.emptyList();
        if (args.length == 1) return Arrays.asList("enable", "disable", "reload", "scan", "debug");
        return Collections.emptyList();
    }
}
