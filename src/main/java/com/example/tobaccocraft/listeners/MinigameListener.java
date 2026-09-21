package com.example.tobaccocraft.listeners;

import com.example.tobaccocraft.TobaccoCraft;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class MinigameListener implements Listener {
    private final TobaccoCraft plugin;
    public MinigameListener(TobaccoCraft plugin) { this.plugin = plugin; }

    // ЛКМ по воздуху может прийти уже отменённым ванильным предсказанием.
    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(PlayerInteractEvent event) {
        if (!plugin.minigames().isPlaying(event.getPlayer())) return;
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND
                && (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK)) {
            plugin.minigames().click(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST) public void onBreak(BlockBreakEvent e) {
        if (plugin.minigames().isPlaying(e.getPlayer())) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void onPlace(BlockPlaceEvent e) {
        if (plugin.minigames().isPlaying(e.getPlayer())) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void onEntity(PlayerInteractEntityEvent e) {
        if (plugin.minigames().isPlaying(e.getPlayer())) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void onAttack(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p && plugin.minigames().isPlaying(p)) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void onInventory(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && plugin.minigames().isPlaying(p)) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void onDrag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p && plugin.minigames().isPlaying(p)) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void onDrop(PlayerDropItemEvent e) {
        if (plugin.minigames().isPlaying(e.getPlayer())) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void onSwap(PlayerSwapHandItemsEvent e) {
        if (plugin.minigames().isPlaying(e.getPlayer())) e.setCancelled(true);
    }
    @EventHandler public void onQuit(PlayerQuitEvent e) { plugin.minigames().cancel(e.getPlayer()); }
    @EventHandler public void onDeath(PlayerDeathEvent e) { plugin.minigames().cancel(e.getEntity()); }
    @EventHandler public void onWorldChange(PlayerChangedWorldEvent e) { plugin.minigames().cancel(e.getPlayer()); }
}
