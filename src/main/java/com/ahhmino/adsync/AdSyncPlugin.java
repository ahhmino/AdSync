package com.ahhmino.adsync;

import com.ahhmino.adsync.commands.AdSyncCommand;
import com.ahhmino.adsync.listeners.AdvancementListener;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileReader;
import java.util.*;

public class AdSyncPlugin extends JavaPlugin {
    private boolean syncEnabled;
    private final Map<UUID, Map<String, Set<String>>> lastProgress = new HashMap<>();
    private final Map<String, Set<String>> globalProgress = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        syncEnabled = getConfig().getBoolean("sync-enabled", false);

        getServer().getPluginManager().registerEvents(new AdvancementListener(this), this);
        var cmd = new AdSyncCommand(this);
        Objects.requireNonNull(getCommand("adsync")).setExecutor(cmd);
        Objects.requireNonNull(getCommand("adsync")).setTabCompleter(cmd);

        getLogger().info("AdSync initialized. Sync is " + (syncEnabled ? "enabled" : "disabled"));

        loadGlobalAdvancements();
        startProgressWatcher();
    }

    @Override
    public void onDisable() {
        saveConfig();
    }

    public boolean isSyncEnabled() {
        return syncEnabled;
    }

    public void setSyncEnabled(boolean enabled) {
        syncEnabled = enabled;
        getConfig().set("sync-enabled", enabled);
        saveConfig();
    }

    /** Loads all awarded advancement criteria from the world's advancement files */
    private void loadGlobalAdvancements() {
        globalProgress.clear();

        File advDir = new File(getServer().getWorldContainer(), "world/advancements");
        if (!advDir.exists()) {
            getLogger().warning("No world/advancements directory found.");
            return;
        }

        File[] files = advDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return;

        for (File file : files) {
            try (FileReader reader = new FileReader(file)) {
                JsonElement root = JsonParser.parseReader(reader);
                if (!root.isJsonObject()) continue;

                JsonObject json = root.getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                    // Skip DataVersion or any non-object entries
                    if (!entry.getValue().isJsonObject()) continue;

                    String advKey = entry.getKey();
                    JsonObject advObj = entry.getValue().getAsJsonObject();
                    if (!advObj.has("criteria")) continue;

                    JsonObject criteria = advObj.getAsJsonObject("criteria");
                    for (String criterion : criteria.keySet()) {
                        globalProgress.computeIfAbsent(advKey, k -> new HashSet<>()).add(criterion);
                    }
                }
            } catch (Exception ex) {
                getLogger().warning("Failed to parse advancement file " + file.getName() + ": " + ex.getMessage());
            }
        }

        getLogger().info("Loaded global advancement progress for " + globalProgress.size() + " advancements.");
    }



    /** Applies all known advancements to a joining player */
    public void applyGlobalAdvancements(Player player) {
        for (Iterator<Advancement> it = Bukkit.advancementIterator(); it.hasNext();) {
            Advancement adv = it.next();
            if (adv.getKey().getKey().startsWith("recipes/")) continue;

            boolean anyDone = Bukkit.getOnlinePlayers().stream()
                    .map(p -> p.getAdvancementProgress(adv))
                    .anyMatch(AdvancementProgress::isDone);

            if (anyDone) {
                Set<String> globalCriteria = globalProgress.get(adv.getKey().toString());
                if (globalCriteria != null) {
                    Bukkit.getScheduler().runTask(this, () -> {
                        AdvancementProgress p = player.getAdvancementProgress(adv);
                        for (String c : globalCriteria) {
                            if (!p.getAwardedCriteria().contains(c)) {
                                p.awardCriteria(c);
                            }
                        }
                    });
                }
            }
        }
    }

    /** Periodically checks and syncs incremental criteria progress */
    private void startProgressWatcher() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!syncEnabled) return;

            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID id = player.getUniqueId();
                lastProgress.putIfAbsent(id, new HashMap<>());

                for (Iterator<Advancement> it = Bukkit.advancementIterator(); it.hasNext();) {
                    Advancement adv = it.next();
                    if (adv.getKey().getKey().startsWith("recipes/")) continue;

                    AdvancementProgress progress = player.getAdvancementProgress(adv);
                    String key = adv.getKey().toString();

                    // Get last recorded criteria for this player/advancement
                    Set<String> previous = lastProgress.get(id).computeIfAbsent(key, k -> new HashSet<>());
                    Set<String> current = new HashSet<>(progress.getAwardedCriteria());

                    // Detect newly completed criteria
                    Set<String> newCriteria = new HashSet<>(current);
                    newCriteria.removeAll(previous);

                    if (!newCriteria.isEmpty()) {
                        syncCriteria(adv, newCriteria, player);
                    }

                    // Update record
                    lastProgress.get(id).put(key, current);
                }
            }
        }, 100L, 100L); // every 5 seconds
    }

    /** Syncs awarded criteria from one player to everyone else */
    private void syncCriteria(Advancement adv, Set<String> criteria, Player source) {
        String advKey = adv.getKey().toString();

        // ✅ 1. Update global progress immediately
        globalProgress.computeIfAbsent(advKey, k -> new HashSet<>()).addAll(criteria);

        // ✅ 2. Sync criteria to all online players
        for (Player other : Bukkit.getOnlinePlayers()) {
            // Skip the source player (they already have these)
            if (other.equals(source)) continue;

            AdvancementProgress progress = other.getAdvancementProgress(adv);
            for (String criterion : criteria) {
                if (!progress.getAwardedCriteria().contains(criterion)) {
                    Bukkit.getScheduler().runTask(this, () ->
                            progress.awardCriteria(criterion)
                    );
                }
            }
        }
    }

    public void resyncAllPlayers() {
        if (!syncEnabled) return;

        getLogger().info("Performing full AdSync resynchronization...");

        for (Iterator<Advancement> it = Bukkit.advancementIterator(); it.hasNext();) {
            Advancement adv = it.next();
            if (adv.getKey().getKey().startsWith("recipes/")) continue;

            String advKey = adv.getKey().toString();

            // Start with the globally known criteria (loaded from disk)
            Set<String> combinedCriteria = new HashSet<>(
                    globalProgress.getOrDefault(advKey, Collections.emptySet())
            );

            // Add in any progress from currently online players
            for (Player p : Bukkit.getOnlinePlayers()) {
                combinedCriteria.addAll(p.getAdvancementProgress(adv).getAwardedCriteria());
            }

            if (combinedCriteria.isEmpty()) continue;

            // Update the globalProgress map with the merged set
            globalProgress.put(advKey, combinedCriteria);

            // Apply the merged progress to all online players
            for (Player p : Bukkit.getOnlinePlayers()) {
                AdvancementProgress progress = p.getAdvancementProgress(adv);
                for (String criterion : combinedCriteria) {
                    if (!progress.getAwardedCriteria().contains(criterion)) {
                        Bukkit.getScheduler().runTask(this, () ->
                                progress.awardCriteria(criterion)
                        );
                    }
                }
            }
        }

        getLogger().info("AdSync resynchronization complete.");
    }

}
