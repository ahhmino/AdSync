package com.ahhmino.adsync;

import com.ahhmino.adsync.commands.AdSyncCommand;
import com.ahhmino.adsync.listeners.AdvancementListener;
import org.bukkit.Bukkit;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class AdSyncPlugin extends JavaPlugin {
    private boolean syncEnabled;
    private final Map<UUID, Map<String, Set<String>>> lastProgress = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        syncEnabled = getConfig().getBoolean("sync-enabled", false);

        getServer().getPluginManager().registerEvents(new AdvancementListener(this), this);
        var cmd = new AdSyncCommand(this);
        Objects.requireNonNull(getCommand("adsync")).setExecutor(cmd);
        Objects.requireNonNull(getCommand("adsync")).setTabCompleter(cmd);

        getLogger().info("AdSync initialized. Sync is " + (syncEnabled ? "enabled" : "disabled"));

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

    /** Applies all known advancements to a joining player */
    public void applyGlobalAdvancements(Player player) {
        for (Iterator<Advancement> it = Bukkit.advancementIterator(); it.hasNext();) {
            Advancement adv = it.next();
            if (adv.getKey().getKey().startsWith("recipes/")) continue;

            boolean anyDone = Bukkit.getOnlinePlayers().stream()
                    .map(p -> p.getAdvancementProgress(adv))
                    .anyMatch(AdvancementProgress::isDone);

            if (anyDone) {
                Bukkit.getScheduler().runTask(this, () ->
                        player.getAdvancementProgress(adv).awardCriteria(Arrays.toString(adv.getCriteria().toArray(new String[0])))
                );
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
        for (Player other : Bukkit.getOnlinePlayers()) {
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

            // Collect all criteria any player has completed
            Set<String> combinedCriteria = new HashSet<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                combinedCriteria.addAll(p.getAdvancementProgress(adv).getAwardedCriteria());
            }

            if (combinedCriteria.isEmpty()) continue;

            // Apply combined progress to all players
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
