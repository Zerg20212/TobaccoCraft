package com.example.tobaccocraft.minigame;

import com.example.tobaccocraft.TobaccoCraft;
import com.example.tobaccocraft.plants.TobaccoPlant;
import com.example.tobaccocraft.utils.MessageUtils;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class HarvestMinigame {
    public enum Result { SUCCESS, MISS, TIMEOUT, CANCELLED }
    private final TobaccoCraft plugin;
    private final MinigameManager manager;
    private final Player player;
    private final TobaccoPlant plant;
    private final int slots, interval;
    private final Set<Integer> green;
    private final long deadline;
    private int marker = 0, direction = 1, ticks = 0;
    private boolean finished;
    private BukkitTask task;

    public HarvestMinigame(TobaccoCraft plugin, MinigameManager manager, Player player, TobaccoPlant plant) {
        this.plugin = plugin; this.manager = manager; this.player = player; this.plant = plant;
        slots = plugin.config().integer("minigame.slots-total");
        interval = plugin.config().integer("minigame.red-move-interval-ticks");
        var candidates = new ArrayList<Integer>();
        for (int i = 0; i < slots; i++) candidates.add(i);
        Collections.shuffle(candidates);
        green = new HashSet<>(candidates.subList(0, plugin.config().integer("minigame.green-slots-count")));
        deadline = System.currentTimeMillis() + plugin.config().milliseconds("minigame.timeout-seconds");
    }

    public Player player() { return player; }
    public TobaccoPlant plant() { return plant; }

    public void start() {
        render();
        task = new BukkitRunnable() {
            @Override public void run() {
                if (!valid()) { finish(Result.CANCELLED); return; }
                if (System.currentTimeMillis() >= deadline) { finish(Result.TIMEOUT); return; }
                if (++ticks % interval == 0) {
                    if (marker + direction < 0 || marker + direction >= slots) direction = -direction;
                    marker += direction;
                    render();
                }
            }
        }.runTaskTimer(plugin, 1, 1);
    }

    private boolean valid() {
        return player.isOnline() && !player.isDead() && player.hasPermission("tobaccocraft.farmer")
                && plugin.plants().contains(plant) && !plugin.plants().expired(plant) && plugin.plants().intact(plant)
                && player.getWorld().getUID().equals(plant.position().world())
                && player.getLocation().distanceSquared(plant.location()) <= 64;
    }

    private void render() {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < slots; i++) {
            if (i > 0) line.append(' ');
            // Зелёная рамка остаётся видна, когда красный маркер находится на цели.
            if (i == marker) line.append(green.contains(i) ? "§a[§c■§a]" : "§c[■]");
            else line.append(green.contains(i) ? "§a[■]" : "§7[ ]");
        }
        MessageUtils.actionBar(player, line.toString());
    }

    public void click() {
        if (finished) return;
        if (!valid()) finish(Result.CANCELLED);
        else if (System.currentTimeMillis() >= deadline) finish(Result.TIMEOUT);
        else finish(green.contains(marker) ? Result.SUCCESS : Result.MISS);
    }

    public void finish(Result result) {
        if (finished) return;
        finished = true;
        if (task != null) task.cancel();
        manager.finish(this, result);
    }
}
