package com.ahhmino.adsync.commands;

import com.ahhmino.adsync.AdSync;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class AdSyncCommand implements CommandExecutor {

    private final AdSync plugin;

    public AdSyncCommand(AdSync plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "enable":
                if (!sender.hasPermission("adsync.admin")) {
                    sender.sendMessage("§cYou do not have permission to enable/disable sync.");
                    return true;
                }
                plugin.setSyncEnabled(!plugin.isSyncEnabled());
                sender.sendMessage("§aAdvancement sync is now " +
                        (plugin.isSyncEnabled() ? "§2ENABLED" : "§cDISABLED"));
                break;

            case "status":
                sender.sendMessage("§6=== AdSync Status ===");
                sender.sendMessage("§eSync: " + (plugin.isSyncEnabled() ? "§aEnabled" : "§cDisabled"));
                if (plugin.getTeams().isEmpty()) {
                    sender.sendMessage("§7No teams defined (global sync mode).");
                } else {
                    sender.sendMessage("§eTeams:");
                    Set<Set<UUID>> uniqueTeams = new HashSet<>(plugin.getTeams().values());
                    for (Set<UUID> team : uniqueTeams) {
                        String names = team.stream()
                                .map(Bukkit::getPlayer)
                                .filter(Objects::nonNull)
                                .map(Player::getName)
                                .collect(Collectors.joining(", "));
                        sender.sendMessage("§7- " + names);
                    }
                }
                break;

            case "team":
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /adsync team <username> <username> ...");
                    return true;
                }

                Set<UUID> teamMembers = Arrays.stream(args)
                        .skip(1)
                        .map(Bukkit::getPlayerExact)
                        .filter(Objects::nonNull)
                        .map(Player::getUniqueId)
                        .collect(Collectors.toSet());

                if (teamMembers.isEmpty()) {
                    sender.sendMessage("§cNo valid players found.");
                    return true;
                }

                plugin.addTeam(teamMembers);
                sender.sendMessage("§aTeam created with " + teamMembers.size() + " members.");
                break;

            case "reset-teams":
                plugin.clearTeams();
                sender.sendMessage("§aAll teams reset.");
                break;

            case "help":
                sendHelp(sender);
                break;

            case "reload":
                if (!sender.hasPermission("adsync.admin")) {
                    sender.sendMessage("§cYou do not have permission to reload AdSync.");
                    return true;
                }
                plugin.reloadPlugin();
                sender.sendMessage("§aAdSync reloaded.");
                break;

            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6=== AdSync Commands ===");
        sender.sendMessage("§e/adsync enable §7- Toggle advancement sync (admin only)");
        sender.sendMessage("§e/adsync status §7- View current sync status and teams");
        sender.sendMessage("§e/adsync team <players...> §7- Create a team of players");
        sender.sendMessage("§e/adsync reset-teams §7- Clear all existing teams");
        sender.sendMessage("§e/adsync reload §7- Reload plugin (admin only)");
        sender.sendMessage("§e/adsync help §7- Show this help message");
    }
}
