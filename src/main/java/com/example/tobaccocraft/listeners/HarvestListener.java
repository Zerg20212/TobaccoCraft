package com.example.tobaccocraft.listeners;

import com.example.tobaccocraft.TobaccoCraft;
import com.example.tobaccocraft.items.TobaccoItems;
import com.example.tobaccocraft.plants.TobaccoPlant;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class HarvestListener implements Listener {
    private final TobaccoCraft plugin;
    public HarvestListener(TobaccoCraft plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHarvest(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null || plugin.minigames().isPlaying(event.getPlayer())) return;
        TobaccoPlant plant = plugin.plants().get(event.getClickedBlock());
        if (plant == null || !plugin.items().is(event.getItem(), TobaccoItems.SHEARS)) return;
        event.setCancelled(true);
        plugin.minigames().start(event.getPlayer(), plant);
    }
}
