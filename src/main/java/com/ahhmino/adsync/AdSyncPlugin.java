package com.ahhmino.adsync;

import com.ahhmino.adsync.commands.AdSyncCommand;
import com.ahhmino.adsync.listeners.AdvancementListener;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.World;
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
    public Map<String, Set<String>> globalProgress = new HashMap<>();

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

        if (syncEnabled) {
            Bukkit.getScheduler().runTaskLater(this, this::resyncAllPlayers, 40L);
        }
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

    /** Load all awarded advancement criteria from disk (offline players included) */
    public void loadGlobalAdvancements() {
        globalProgress.clear();
        File advDir = new File(getServer().getWorldContainer(), "world/advancements");
        if (!advDir.exists() || !advDir.isDirectory()) {
            getLogger().warning("No world/advancements directory found.");
            return;
        }

        File[] files = advDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return;

        int count = 0;
        for (File file : files) {
            try (FileReader reader = new FileReader(file)) {
                JsonElement root = JsonParser.parseReader(reader);
                if (!root.isJsonObject()) continue;

                JsonObject json = root.getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                    if (!entry.getValue().isJsonObject()) continue;

                    String advKey = entry.getKey();
                    JsonObject advObj = entry.getValue().getAsJsonObject();
                    if (!advObj.has("criteria") || !advObj.get("criteria").isJsonObject()) continue;

                    JsonObject criteria = advObj.getAsJsonObject("criteria");
                    for (String criterion : criteria.keySet()) {
                        globalProgress.computeIfAbsent(advKey, k -> new HashSet<>()).add(criterion);
                    }
                }
                count++;
            } catch (Exception ex) {
                getLogger().warning("Failed to parse advancement file " + file.getName() + ": " + ex.getMessage());
            }
        }
        getLogger().info("Loaded global advancement progress from " + count + " files (" + globalProgress.size() + " advs).");
    }

    /** Apply union of globalProgress + online player progress to a joining player */
    public void applyGlobalAdvancements(Player player) {
        for (Iterator<Advancement> it = Bukkit.advancementIterator(); it.hasNext();) {
            Advancement adv = it.next();
            if (adv.getKey().getKey().startsWith("recipes/")) continue;

            String advKey = adv.getKey().toString();

            Set<String> union = new HashSet<>(globalProgress.getOrDefault(advKey, Collections.emptySet()));
            for (Player p : Bukkit.getOnlinePlayers()) {
                union.addAll(p.getAdvancementProgress(adv).getAwardedCriteria());
            }

            AdvancementProgress prog = player.getAdvancementProgress(adv);
            boolean updated = false;
            for (String c : union) {
                if (!prog.getAwardedCriteria().contains(c)) {
                    prog.awardCriteria(c);
                    updated = true;
                }
            }

            if (updated) {
                // Save world once per advancement if any new criteria were applied
                Bukkit.getWorlds().forEach(world -> world.save());
            }
        }
    }

    /** Periodically checks incremental progress and propagates to all online players */
    private void startProgressWatcher() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!syncEnabled) return;

            // Reload globalProgress from disk for offline players
            loadGlobalAdvancements();

            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID id = player.getUniqueId();
                lastProgress.putIfAbsent(id, new HashMap<>());

                for (Iterator<Advancement> it = Bukkit.advancementIterator(); it.hasNext();) {
                    Advancement adv = it.next();
                    if (adv.getKey().getKey().startsWith("recipes/")) continue;

                    AdvancementProgress progress = player.getAdvancementProgress(adv);
                    String key = adv.getKey().toString();

                    Set<String> previous = lastProgress.get(id).computeIfAbsent(key, k -> new HashSet<>());
                    Set<String> current = new HashSet<>(progress.getAwardedCriteria());

                    Set<String> newCriteria = new HashSet<>(current);
                    newCriteria.removeAll(previous);

                    if (!newCriteria.isEmpty()) {
                        syncCriteria(adv, newCriteria, player);
                    }

                    lastProgress.get(id).put(key, current);
                }
            }
        }, 100L, 100L);
    }

    /** Update globalProgress in memory and award criteria to all online players */
    public void syncCriteria(Advancement adv, Set<String> criteria, Player source) {
        String advKey = adv.getKey().toString();

        globalProgress.computeIfAbsent(advKey, k -> new HashSet<>()).addAll(criteria);

        Bukkit.getScheduler().runTask(this, () -> {
            boolean updated = false;

            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other.equals(source)) continue;

                AdvancementProgress progress = other.getAdvancementProgress(adv);
                for (String criterion : criteria) {
                    if (!progress.getAwardedCriteria().contains(criterion)) {
                        progress.awardCriteria(criterion);
                        updated = true;
                    }
                }
            }

            // Flush world once if any criteria were applied
            if (updated) {
                Bukkit.getWorlds().forEach(world -> world.save());
            }
        });
    }

    /** Merge disk + online progress and apply to all online players */
    public void resyncAllPlayers() {
        if (!syncEnabled) return;

        getLogger().info("Performing full AdSync resynchronization...");

        loadGlobalAdvancements();

        // Merge online players' in-memory progress
        for (Player p : Bukkit.getOnlinePlayers()) {
            for (Iterator<Advancement> it = Bukkit.advancementIterator(); it.hasNext();) {
                Advancement adv = it.next();
                if (adv.getKey().getKey().startsWith("recipes/")) continue;

                String advKey = adv.getKey().toString();
                Set<String> union = globalProgress.computeIfAbsent(advKey, k -> new HashSet<>());
                union.addAll(p.getAdvancementProgress(adv).getAwardedCriteria());
            }
        }

        int appliedCount = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            for (Iterator<Advancement> it = Bukkit.advancementIterator(); it.hasNext();) {
                Advancement adv = it.next();
                if (adv.getKey().getKey().startsWith("recipes/")) continue;

                String advKey = adv.getKey().toString();
                Set<String> union = globalProgress.getOrDefault(advKey, Collections.emptySet());
                AdvancementProgress progress = p.getAdvancementProgress(adv);

                boolean updated = false;
                for (String criterion : union) {
                    if (!progress.getAwardedCriteria().contains(criterion)) {
                        progress.awardCriteria(criterion);
                        updated = true;
                        appliedCount++;
                    }
                }

                if (updated) {
                    Bukkit.getWorlds().forEach(World::save);
                }
            }
        }

        getLogger().info("AdSync resynchronization complete. Applied " + appliedCount + " criteria.");
    }
}
