package com.example.tobaccocraft.utils;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfigManagerTest {
    @TempDir Path directory;
    ConfigManager manager;
    @BeforeEach void setup() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getResource("config.yml")).thenAnswer(invocation -> getClass().getResourceAsStream("/config.yml"));
        manager = new ConfigManager(plugin);
        Files.writeString(directory.resolve("config.yml"), "{}");
    }
    @Test void omittedFieldsUseDefaults() throws Exception {
        manager.apply(manager.read());
        assertEquals(10, manager.integer("growth.days-required"));
        assertEquals(1200000, manager.minutesToMillis("growth.real-time-per-day-minutes"));
        assertEquals(300000, manager.milliseconds("smoking.smoke-duration-seconds"));
        assertEquals("§cУ вас нет прав!", manager.message("no-permission"));
    }
    @ParameterizedTest
    @ValueSource(strings = {
            "minigame:\n  slots-total: 1", "minigame:\n  green-slots-count: 6",
            "minigame:\n  red-move-interval-ticks: 0", "minigame:\n  slots-total: 6.5",
            "minigame:\n  reward:\n    seeds-min: 5\n    seeds-max: 2",
            "growth:\n  real-time-per-day-minutes: 0", "growth:\n  days-required: -1",
            "smoking:\n  overdose-damage: .nan", "smoking:\n  overdose-threshold: 0"
    })
    void invalidValuesDoNotReplaceActiveConfig(String text) throws Exception {
        manager.apply(manager.read());
        Files.writeString(directory.resolve("config.yml"), text);
        assertThrows(IllegalArgumentException.class, manager::read);
        assertEquals(6, manager.integer("minigame.slots-total"));
    }
}
