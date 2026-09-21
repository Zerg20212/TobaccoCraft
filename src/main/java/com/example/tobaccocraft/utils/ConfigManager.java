package com.example.tobaccocraft.utils;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Проверка всей конфигурации до замены действующих настроек. */
public final class ConfigManager {
    private final JavaPlugin plugin;
    private YamlConfiguration values;

    public ConfigManager(JavaPlugin plugin) { this.plugin = plugin; }

    public YamlConfiguration read() throws IOException, InvalidConfigurationException {
        YamlConfiguration next = new YamlConfiguration();
        next.load(new File(plugin.getDataFolder(), "config.yml"));
        try (var stream = Objects.requireNonNull(plugin.getResource("config.yml"));
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            next.setDefaults(YamlConfiguration.loadConfiguration(reader));
        }
        positive(next, "growth.days-required", 1, 36500);
        positive(next, "growth.real-time-per-day-minutes", 0.001, 525600);
        positive(next, "growth.check-interval-minutes", 0.001, 525600);
        integer(next, "growth.days-required");
        for (String key : new String[]{"minigame.slots-total", "minigame.green-slots-count",
                "minigame.red-move-interval-ticks", "smoking.effects-duration-seconds",
                "smoking.smoke-duration-seconds", "smoking.overdose-threshold",
                "minigame.reward.seeds-min", "minigame.reward.seeds-max",
                "minigame.reward.leaf-min", "minigame.reward.leaf-max"}) integer(next, key);
        positive(next, "minigame.slots-total", 2, 40);
        positive(next, "minigame.green-slots-count", 1, next.getInt("minigame.slots-total") - 1);
        positive(next, "minigame.red-move-interval-ticks", 1, 1200);
        positive(next, "minigame.timeout-seconds", 0.1, 600);
        positive(next, "minigame.cooldown-seconds", 0, 86400);
        positive(next, "smoking.effects-duration-seconds", 1, 86400);
        positive(next, "smoking.smoke-duration-seconds", 1, 86400);
        positive(next, "smoking.overdose-threshold", 1, 1000);
        positive(next, "smoking.overdose-damage", 0, 2048);
        for (String reward : new String[]{"seeds", "leaf"}) {
            String prefix = "minigame.reward." + reward;
            positive(next, prefix + "-min", 1, 64);
            positive(next, prefix + "-max", next.getInt(prefix + "-min"), 64);
        }
        return next;
    }

    private static void integer(YamlConfiguration config, String path) {
        Object raw = config.get(path);
        double value = raw instanceof Number number ? number.doubleValue() : Double.NaN;
        if (!Double.isFinite(value) || value != Math.rint(value)) {
            throw new IllegalArgumentException("Параметр " + path + " должен быть целым числом");
        }
    }

    private static void positive(YamlConfiguration config, String path, double min, double max) {
        Object raw = config.get(path);
        double value = raw instanceof Number number ? number.doubleValue() : Double.NaN;
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException("Параметр " + path + " должен быть от " + min + " до " + max);
        }
    }

    public void apply(YamlConfiguration next) { values = next; }
    public int integer(String path) { return values.getInt(path); }
    public double number(String path) { return values.getDouble(path); }
    public boolean flag(String path) { return values.getBoolean(path); }
    public long milliseconds(String path) { return Math.round(number(path) * 1000.0); }
    public long minutesToMillis(String path) { return Math.round(number(path) * 60000.0); }
    public String message(String key) {
        String value = values.getString("messages." + key);
        return value == null ? "§cСообщение не настроено: " + key : value;
    }
}
