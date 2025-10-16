package com.ahhmino.adsync;

import com.ahhmino.adsync.commands.AdSyncCommand;
import com.ahhmino.adsync.commands.AdSyncTabCompleter;
import com.ahhmino.adsync.listeners.AdvancementListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class AdSync extends JavaPlugin {

    private boolean syncEnabled = false;
    private final Map<UUID, Set<UUID>> teams = new HashMap<>();

    @Override
    public void onEnable() {
        getCommand("adsync").setExecutor(new AdSyncCommand(this));
        getCommand("adsync").setTabCompleter(new AdSyncTabCompleter());
        getServer().getPluginManager().registerEvents(new AdvancementListener(this), this);
        getLogger().info("AdSync plugin enabled.");
    }

    @Override
    public void onDisable() {
        teams.clear();
    }

    public boolean isSyncEnabled() {
        return syncEnabled;
    }

    public void setSyncEnabled(boolean enabled) {
        this.syncEnabled = enabled;
    }

    public Map<UUID, Set<UUID>> getTeams() {
        return teams;
    }

    public void clearTeams() {
        teams.clear();
    }

    public void addTeam(Set<UUID> teamMembers) {
        // Remove any old team references
        for (UUID member : teamMembers) {
            teams.entrySet().removeIf(entry -> entry.getValue().contains(member));
        }
        // Add the new team
        for (UUID uuid : teamMembers) {
            teams.put(uuid, teamMembers);
        }
    }

    public Set<UUID> getTeam(UUID playerId) {
        return teams.get(playerId);
    }

    public void reloadPlugin() {
        // Placeholder for future config support
        getLogger().info("AdSync reloaded (no configuration to reload).");
    }
}
