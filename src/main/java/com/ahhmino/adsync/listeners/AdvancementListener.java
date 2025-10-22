package com.ahhmino.adsync.listeners;

import com.ahhmino.adsync.AdSyncPlugin;
import org.bukkit.Bukkit;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.HashSet;
import java.util.Set;

public class AdvancementListener implements Listener {
    private final AdSyncPlugin plugin;

    public AdvancementListener(AdSyncPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!plugin.isSyncEnabled()) return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            plugin.applyGlobalAdvancements(e.getPlayer());
            // World save is already handled in applyGlobalAdvancements per advancement
        }, 60L);
    }

    @EventHandler
    public void onAdvancementDone(PlayerAdvancementDoneEvent event) {
        if (!plugin.isSyncEnabled()) return;

        Advancement adv = event.getAdvancement();
        if (adv.getKey().getKey().startsWith("recipes/")) return;

        Player player = event.getPlayer();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            AdvancementProgress progress = player.getAdvancementProgress(adv);
            Set<String> completed = new HashSet<>(progress.getAwardedCriteria());
            if (!completed.isEmpty()) {
                plugin.syncCriteria(adv, completed, player);
                // World flush is handled inside syncCriteria after applying criteria
            }
        }, 2L);
    }
}
