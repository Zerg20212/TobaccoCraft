package com.example.tobaccocraft.listeners;

import com.example.tobaccocraft.TobaccoCraft;
import com.example.tobaccocraft.items.TobaccoItems;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import java.util.Arrays;

public final class SmokeListener implements Listener {
    private final TobaccoCraft plugin;
    private final NamespacedKey untilKey, countKey, timesKey, windowKey;
    private final BukkitTask cleanup;

    public SmokeListener(TobaccoCraft plugin) {
        this.plugin = plugin;
        untilKey = new NamespacedKey(plugin, "tobacco_smoke_until");
        countKey = new NamespacedKey(plugin, "tobacco_smoke_count");
        timesKey = new NamespacedKey(plugin, "tobacco_smoke_times");
        windowKey = new NamespacedKey(plugin, "tobacco_smoke_window");
        cleanup = Bukkit.getScheduler().runTaskTimer(plugin,
                () -> Bukkit.getOnlinePlayers().forEach(p -> update(p, System.currentTimeMillis())), 20, 20);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSmoke(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
                || !plugin.items().is(event.getItem(), TobaccoItems.CIGARETTE)) return;
        if (plugin.minigames().isPlaying(event.getPlayer())) { event.setCancelled(true); return; }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.useInteractedBlock() == Event.Result.DENY) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!plugin.messages().require(player, "tobaccocraft.farmer")) return;
        var recipe = plugin.catalog().recipe(plugin.items().recipeId(event.getItem()));
        var overdose = recipe.overdose();
        long now = System.currentTimeMillis();
        long[] history = update(player, now);
        long[] times = Arrays.copyOf(history, history.length + 1);
        times[times.length - 1] = now;
        if (times.length > 1000) times = Arrays.copyOfRange(times, times.length - 1000, times.length);
        long window = overdose.windowSeconds() * 1000L;
        int count = recentTimes(times, now, window).length;
        var pdc = player.getPersistentDataContainer();
        pdc.set(untilKey, PersistentDataType.LONG, Math.max(pdc.getOrDefault(untilKey, PersistentDataType.LONG, 0L), now + window));
        pdc.set(timesKey, PersistentDataType.LONG_ARRAY, times);
        pdc.set(countKey, PersistentDataType.INTEGER, count);
        pdc.set(windowKey, PersistentDataType.LONG, window);
        var item = player.getInventory().getItemInMainHand();
        item.setAmount(item.getAmount() - 1);
        player.getInventory().setItemInMainHand(item);
        recipe.effects().forEach(effect -> player.addPotionEffect(effect.toPotionEffect()));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.6f, 1.2f);
        player.getWorld().spawnParticle(Particle.SMOKE, player.getEyeLocation(), 18, 0.25, 0.2, 0.25, 0.015);
        plugin.messages().send(player, "smoked");
        if (count >= overdose.threshold()) {
            pdc.set(countKey, PersistentDataType.INTEGER, 0);
            pdc.remove(timesKey);
            overdose.effects().forEach(effect -> player.addPotionEffect(effect.toPotionEffect()));
            if (overdose.damage() > 0) player.damage(overdose.damage());
            var drop = overdose.createDrop();
            if (drop != null) player.getWorld().dropItemNaturally(player.getLocation(), drop);
            if (!overdose.message().isBlank()) player.sendMessage(overdose.message());
        }
    }

    public static long[] recentTimes(long[] times, long now, long windowMillis) {
        return Arrays.stream(times).filter(time -> time > now - windowMillis && time <= now).toArray();
    }

    private long[] update(Player player, long now) {
        var pdc = player.getPersistentDataContainer();
        long[] times = pdc.getOrDefault(timesKey, PersistentDataType.LONG_ARRAY, new long[0]);
        // История общая для всех сигарет: смена сорта не обнуляет перекуривание.
        long[] recent = recentTimes(times, now, plugin.catalog().maxWindowMillis());
        if (recent.length == 0) pdc.remove(timesKey);
        else if (recent.length != times.length) pdc.set(timesKey, PersistentDataType.LONG_ARRAY, recent);
        if (pdc.getOrDefault(untilKey, PersistentDataType.LONG, 0L) <= now) {
            pdc.remove(untilKey); pdc.remove(countKey); pdc.remove(windowKey);
        } else {
            long window = pdc.getOrDefault(windowKey, PersistentDataType.LONG, 300000L);
            pdc.set(countKey, PersistentDataType.INTEGER, recentTimes(recent, now, window).length);
        }
        return recent;
    }
    @EventHandler public void onJoin(PlayerJoinEvent event) { update(event.getPlayer(), System.currentTimeMillis()); }
    public void shutdown() { cleanup.cancel(); }
}
