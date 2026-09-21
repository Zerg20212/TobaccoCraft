package com.example.tobaccocraft.minigame;

import com.example.tobaccocraft.TobaccoCraft;
import com.example.tobaccocraft.items.TobaccoItems;
import com.example.tobaccocraft.plants.TobaccoPlant;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

public final class MinigameManager {
    private final TobaccoCraft plugin;
    private final RandomGenerator random;
    private final Map<UUID, HarvestMinigame> active = new HashMap<>();
    private final Map<TobaccoPlant.Position, UUID> occupied = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public MinigameManager(TobaccoCraft plugin) { this(plugin, ThreadLocalRandom.current()); }
    MinigameManager(TobaccoCraft plugin, RandomGenerator random) { this.plugin = plugin; this.random = random; }
    public boolean isPlaying(Player player) { return active.containsKey(player.getUniqueId()); }

    public void start(Player player, TobaccoPlant plant) {
        if (!plugin.messages().require(player, "tobaccocraft.farmer")) return;
        if (isPlaying(player)) { plugin.messages().send(player, "already-playing"); return; }
        long now = System.currentTimeMillis();
        cooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
        long remaining = cooldowns.getOrDefault(player.getUniqueId(), 0L) - now;
        if (remaining > 0) {
            plugin.messages().send(player, "cooldown", "seconds", (remaining + 999) / 1000); return;
        }
        if (occupied.containsKey(plant.position())) { plugin.messages().send(player, "plant-busy"); return; }
        if (plugin.plants().expired(plant)) { plugin.plants().remove(plant); plugin.plants().save(); return; }
        plugin.plants().updateStage(plant);
        if (plant.stage() < 3) { plugin.messages().send(player, "not-grown", "stage", plant.stage()); return; }
        if (!plugin.plants().contains(plant) || !plugin.plants().intact(plant)) return;
        HarvestMinigame game = new HarvestMinigame(plugin, this, player, plant);
        active.put(player.getUniqueId(), game);
        occupied.put(plant.position(), player.getUniqueId());
        game.start();
    }

    public void click(Player player) {
        HarvestMinigame game = active.get(player.getUniqueId());
        if (game != null) game.click();
    }

    void finish(HarvestMinigame game, HarvestMinigame.Result result) {
        Player player = game.player();
        if (!active.remove(player.getUniqueId(), game)) return;
        occupied.remove(game.plant().position(), player.getUniqueId());
        if (result == HarvestMinigame.Result.SUCCESS) {
            if (plugin.plants().expired(game.plant()) || !plugin.plants().intact(game.plant()) || !plugin.plants().remove(game.plant())) {
                if (player.isOnline()) plugin.messages().action(player, "minigame-cancelled");
                return;
            }
            plugin.plants().save();
            dropRewards(player, game.plant());
            plugin.messages().action(player, "minigame-success");
        } else {
            if (result != HarvestMinigame.Result.CANCELLED) {
                cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + plugin.config().milliseconds("minigame.cooldown-seconds"));
            }
            if (player.isOnline()) plugin.messages().action(player, switch (result) {
                case MISS -> "minigame-miss";
                case TIMEOUT -> "minigame-timeout";
                default -> "minigame-cancelled";
            });
        }
    }

    void dropRewards(Player player, TobaccoPlant plant) {
        var variety = plugin.catalog().variety(plant.varietyId());
        if (variety == null) variety = plugin.catalog().variety("default");
        int seeds = 0;
        if (random.nextDouble(100) < variety.seedsChance()) {
            seeds = random.nextInt(variety.seedsMin(), variety.seedsMax() + 1);
            plant.world().dropItemNaturally(plant.location().add(0.5, 0.3, 0.5),
                    plugin.items().create(TobaccoItems.SEEDS, seeds, variety.id()));
        }
        int leaves = 0;
        if (random.nextDouble(100) < variety.leafChance()) {
            leaves = random.nextInt(variety.leafMin(), variety.leafMax() + 1);
            plant.world().dropItemNaturally(plant.location().add(0.5, 0.3, 0.5),
                    plugin.items().create(TobaccoItems.LEAF, leaves, variety.id()));
        }
        plugin.messages().send(player, "harvest-reward", "seeds", seeds, "leaves", leaves);
    }

    public void cancel(Player player) {
        HarvestMinigame game = active.get(player.getUniqueId());
        if (game != null) game.finish(HarvestMinigame.Result.CANCELLED);
    }
    public void cancelPlant(TobaccoPlant plant) {
        UUID owner = occupied.get(plant.position());
        HarvestMinigame game = owner == null ? null : active.get(owner);
        if (game != null) game.finish(HarvestMinigame.Result.CANCELLED);
    }
    public void cancelAll() {
        for (HarvestMinigame game : List.copyOf(active.values())) game.finish(HarvestMinigame.Result.CANCELLED);
    }
}
