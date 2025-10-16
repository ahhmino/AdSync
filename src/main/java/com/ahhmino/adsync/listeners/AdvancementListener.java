package com.ahhmino.adsync.listeners;

import com.ahhmino.adsync.AdSync;
import org.bukkit.Bukkit;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;

import java.util.Set;
import java.util.UUID;

public class AdvancementListener implements Listener {

    private final AdSync plugin;

    public AdvancementListener(AdSync plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        if (!plugin.isSyncEnabled()) return;

        Advancement advancement = event.getAdvancement();

        // Skip recipe advancements to reduce spam
        if (advancement.getKey().getKey().startsWith("recipes/")) return;

        Player player = event.getPlayer();
        Set<UUID> team = plugin.getTeam(player.getUniqueId());

        if (team == null) {
            // Global sync
            for (Player p : Bukkit.getOnlinePlayers()) {
                grantAdvancement(p, advancement);
            }
        } else {
            // Team sync
            for (UUID uuid : team) {
                Player teammate = Bukkit.getPlayer(uuid);
                if (teammate != null) {
                    grantAdvancement(teammate, advancement);
                }
            }
        }
    }

    private void grantAdvancement(Player player, Advancement advancement) {
        AdvancementProgress progress = player.getAdvancementProgress(advancement);
        if (!progress.isDone()) {
            for (String criterion : progress.getRemainingCriteria()) {
                progress.awardCriteria(criterion);
            }
        }
    }
}
