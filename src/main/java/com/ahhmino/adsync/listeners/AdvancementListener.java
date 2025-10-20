package com.ahhmino.adsync.listeners;

import com.ahhmino.adsync.AdSyncPlugin;
import org.bukkit.Bukkit;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Arrays;

public class AdvancementListener implements Listener {
    private final AdSyncPlugin plugin;

    public AdvancementListener(AdSyncPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (plugin.isSyncEnabled()) {
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    plugin.applyGlobalAdvancements(e.getPlayer()), 20L
            );
        }
    }

    @EventHandler
    public void onAdvancementDone(PlayerAdvancementDoneEvent event) {
        if (!plugin.isSyncEnabled()) return;
        Advancement adv = event.getAdvancement();
        if (adv.getKey().getKey().startsWith("recipes/")) return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.equals(event.getPlayer())) continue;
                p.getAdvancementProgress(adv).awardCriteria(
                        Arrays.toString(adv.getCriteria().toArray(new String[0]))
                );
            }
        }, 1L);
    }
}
