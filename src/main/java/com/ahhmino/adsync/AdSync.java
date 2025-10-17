package com.ahhmino.adsync;

import com.ahhmino.adsync.commands.AdSyncCommand;
import com.ahhmino.adsync.commands.AdSyncTabCompleter;
import com.ahhmino.adsync.listeners.AdvancementListener;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class AdSync extends JavaPlugin {

    private boolean syncEnabled = false;
    private final Map<UUID, Set<UUID>> teams = new HashMap<>();
    private final Map<UUID, Set<String>> completedAdvancements = new HashMap<>();

    private File dataFile;
    private FileConfiguration dataConfig;

    @Override
    public void onEnable() {
        // Load config and register everything
        createDataFile();
        loadData();

        getCommand("adsync").setExecutor(new AdSyncCommand(this));
        getCommand("adsync").setTabCompleter(new AdSyncTabCompleter());
        getServer().getPluginManager().registerEvents(new AdvancementListener(this), this);

        getLogger().info("AdSync enabled. Sync = " + (syncEnabled ? "ON" : "OFF"));
    }

    @Override
    public void onDisable() {
        saveData();
    }

    private void createDataFile() {
        dataFile = new File(getDataFolder(), "data.yml");
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Could not create data.yml!");
                e.printStackTrace();
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    /** Load persisted data */
    public void loadData() {
        syncEnabled = dataConfig.getBoolean("sync-enabled", false);

        // Load teams
        teams.clear();
        if (dataConfig.isList("teams")) {
            List<Map<?, ?>> teamList = (List<Map<?, ?>>) dataConfig.getList("teams");
            if (teamList != null) {
                for (Map<?, ?> entry : teamList) {
                    List<String> members = (List<String>) entry.get("members");
                    if (members == null) continue;
                    Set<UUID> uuids = new HashSet<>();
                    for (String id : members) {
                        try {
                            uuids.add(UUID.fromString(id));
                        } catch (IllegalArgumentException ignored) {}
                    }
                    for (UUID uuid : uuids) {
                        teams.put(uuid, uuids);
                    }
                }
            }
        }

        // Load completed advancements
        completedAdvancements.clear();
        if (dataConfig.isConfigurationSection("completed-advancements")) {
            for (String key : dataConfig.getConfigurationSection("completed-advancements").getKeys(false)) {
                UUID uuid = UUID.fromString(key);
                List<String> advs = dataConfig.getStringList("completed-advancements." + key);
                completedAdvancements.put(uuid, new HashSet<>(advs));
            }
        }
    }

    /** Save everything to disk */
    public void saveData() {
        dataConfig.set("sync-enabled", syncEnabled);

        // Save teams
        List<Map<String, Object>> teamList = new ArrayList<>();
        Set<Set<UUID>> uniqueTeams = new HashSet<>(teams.values());
        for (Set<UUID> team : uniqueTeams) {
            Map<String, Object> map = new HashMap<>();
            map.put("members", team.stream().map(UUID::toString).toList());
            teamList.add(map);
        }
        dataConfig.set("teams", teamList);

        // Save completed advancements
        Map<String, Object> completedMap = new HashMap<>();
        for (Map.Entry<UUID, Set<String>> entry : completedAdvancements.entrySet()) {
            completedMap.put(entry.getKey().toString(), new ArrayList<>(entry.getValue()));
        }
        dataConfig.set("completed-advancements", completedMap);

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            getLogger().severe("Failed to save data.yml!");
            e.printStackTrace();
        }
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
        // Remove any old team memberships
        for (UUID member : teamMembers) {
            teams.entrySet().removeIf(entry -> entry.getValue().contains(member));
        }
        for (UUID uuid : teamMembers) {
            teams.put(uuid, teamMembers);
        }
    }

    public Set<UUID> getTeam(UUID playerId) {
        return teams.get(playerId);
    }

    public Map<UUID, Set<String>> getCompletedAdvancements() {
        return completedAdvancements;
    }

    public void markCompleted(UUID uuid, String advKey) {
        completedAdvancements.computeIfAbsent(uuid, k -> new HashSet<>()).add(advKey);
    }

    public void reloadPlugin() {
        loadData();
        getLogger().info("AdSync data reloaded.");
    }
}
