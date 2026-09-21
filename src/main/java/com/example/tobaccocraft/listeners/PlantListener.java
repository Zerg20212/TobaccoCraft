package com.example.tobaccocraft.listeners;

import com.example.tobaccocraft.TobaccoCraft;
import com.example.tobaccocraft.items.TobaccoItems;
import com.example.tobaccocraft.plants.TobaccoPlant;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class PlantListener implements Listener {
    private final TobaccoCraft plugin;
    public PlantListener(TobaccoCraft plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;
        if (event.getAction() == Action.PHYSICAL && plugin.plants().isProtected(clicked)) {
            event.setCancelled(true); return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || plugin.minigames().isPlaying(event.getPlayer())) return;
        if (event.getHand() != EquipmentSlot.HAND) {
            if (plugin.plants().get(clicked) != null || plugin.items().is(event.getItem(), TobaccoItems.SEEDS)) event.setCancelled(true);
            return;
        }
        TobaccoPlant plant = plugin.plants().get(clicked);
        if (plant != null) {
            event.setCancelled(true);
            if (!plugin.messages().require(event.getPlayer(), "tobaccocraft.farmer")) return;
            plugin.plants().refresh(plant);
            if (!plugin.plants().contains(plant)) return;
            plugin.messages().send(event.getPlayer(), "plant-stage", "stage", plant.stage(), "name", plant.stageName());
            if (plant.stage() == 3) plugin.messages().send(event.getPlayer(), "need-shears");
            return;
        }
        if (!plugin.items().is(event.getItem(), TobaccoItems.SEEDS)) return;
        event.setCancelled(true); // Семена табака никогда не превращаются в обычную пшеницу.
        if (!plugin.messages().require(event.getPlayer(), "tobaccocraft.farmer")) return;
        if (clicked.getType() != Material.FARMLAND) return;
        Block bottom = clicked.getRelative(BlockFace.UP);
        if (bottom.getY() + 1 >= clicked.getWorld().getMaxHeight() || !bottom.getType().isAir()
                || !bottom.getRelative(BlockFace.UP).getType().isAir() || plugin.plants().get(bottom) != null) {
            plugin.messages().send(event.getPlayer(), "need-space"); return;
        }
        plugin.plants().plant(bottom, plugin.items().varietyId(event.getItem()));
        var item = event.getPlayer().getInventory().getItemInMainHand();
        item.setAmount(item.getAmount() - 1);
        event.getPlayer().getInventory().setItemInMainHand(item);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (plugin.plants().isProtected(e.getBlock())) {
            e.setCancelled(true);
            plugin.messages().send(e.getPlayer(), "plant-protected");
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onPhysics(BlockPhysicsEvent e) {
        if (plugin.plants().isProtected(e.getBlock())) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onFade(BlockFadeEvent e) {
        if (plugin.plants().isProtected(e.getBlock())) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onBurn(BlockBurnEvent e) {
        if (plugin.plants().isProtected(e.getBlock())) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onFlow(BlockFromToEvent e) {
        if (plugin.plants().isProtected(e.getToBlock())) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onBucket(PlayerBucketEmptyEvent e) {
        if (plugin.plants().isProtected(e.getBlock())) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onChange(EntityChangeBlockEvent e) {
        if (plugin.plants().isProtected(e.getBlock())) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(plugin.plants()::isProtected);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(plugin.plants()::isProtected);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onExtend(BlockPistonExtendEvent e) {
        if (e.getBlocks().stream().anyMatch(b -> plugin.plants().isProtected(b)
                || plugin.plants().isProtected(b.getRelative(e.getDirection())))) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onRetract(BlockPistonRetractEvent e) {
        if (e.getBlocks().stream().anyMatch(b -> plugin.plants().isProtected(b)
                || plugin.plants().isProtected(b.getRelative(e.getDirection())))) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onPlace(BlockPlaceEvent e) {
        if (plugin.plants().isProtected(e.getBlockPlaced())) e.setCancelled(true);
    }
    @EventHandler public void onLoad(ChunkLoadEvent e) {
        int x = e.getChunk().getX(), z = e.getChunk().getZ();
        var world = e.getWorld();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (world.isChunkLoaded(x, z)) plugin.plants().loadChunk(world.getChunkAt(x, z));
        });
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onUnload(ChunkUnloadEvent e) {
        plugin.plants().unloadChunk(e.getChunk());
    }
}
