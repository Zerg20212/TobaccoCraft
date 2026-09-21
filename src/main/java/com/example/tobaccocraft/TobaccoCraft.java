package com.example.tobaccocraft;

import com.example.tobaccocraft.commands.TobaccoCommand;
import com.example.tobaccocraft.catalog.CatalogManager;
import com.example.tobaccocraft.items.TobaccoItems;
import com.example.tobaccocraft.listeners.*;
import com.example.tobaccocraft.minigame.MinigameManager;
import com.example.tobaccocraft.plants.PlantManager;
import com.example.tobaccocraft.utils.ConfigManager;
import com.example.tobaccocraft.utils.MessageUtils;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.logging.Level;

public final class TobaccoCraft extends JavaPlugin {
    private ConfigManager config;
    private CatalogManager catalog;
    private CatalogEditor editor;
    private MessageUtils messages;
    private TobaccoItems items;
    private PlantManager plants;
    private MinigameManager minigames;
    private CraftListener crafting;
    private SmokeListener smoking;
    private boolean ready;

    @Override public void onEnable() {
        saveDefaultConfig();
        config = new ConfigManager(this);
        try {
            config.apply(config.read());
            messages = new MessageUtils(config);
            catalog = new CatalogManager(this);
            catalog.apply(catalog.read());
            items = new TobaccoItems(this);
            plants = new PlantManager(this);
            minigames = new MinigameManager(this);
            // При ошибке YAML плагин выключается, не перезаписывая повреждённый файл.
            var loaded = plants.read();
            plants.apply(loaded);
            crafting = new CraftListener(this);
            smoking = new SmokeListener(this);
            editor = new CatalogEditor(this);
            for (Listener listener : new Listener[]{new MinigameListener(this), new HarvestListener(this),
                    new PlantListener(this), crafting, smoking, editor, items}) {
                getServer().getPluginManager().registerEvents(listener, this);
            }
            TobaccoCommand handler = new TobaccoCommand(this);
            var command = Objects.requireNonNull(getCommand("tobacco"));
            command.setExecutor(handler); command.setTabCompleter(handler);
            ready = true;
            plants.start();
            getServer().getOnlinePlayers().forEach(items::refreshPlayer);
            getLogger().info("§aTobaccoCraft включён. Загружено кустов: " + plants.all().size());
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "§cНе удалось запустить TobaccoCraft", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    public void reloadData() throws Exception {
        // Читаем оба файла до остановки текущих игр и изменения рабочего состояния.
        var nextConfig = config.read();
        var nextCatalog = catalog.read();
        var nextPlants = plants.read();
        minigames.cancelAll();
        crafting.closeAll();
        editor.closeAll();
        config.apply(nextConfig);
        catalog.apply(nextCatalog);
        plants.apply(nextPlants);
        plants.start();
        getServer().getOnlinePlayers().forEach(items::refreshPlayer);
    }

    @Override public void onDisable() {
        if (minigames != null) minigames.cancelAll();
        if (crafting != null) crafting.closeAll();
        if (smoking != null) smoking.shutdown();
        if (editor != null) editor.shutdown();
        if (plants != null && ready) plants.shutdown();
    }

    public ConfigManager config() { return config; }
    public CatalogManager catalog() { return catalog; }
    public CatalogEditor editor() { return editor; }
    public CraftListener crafting() { return crafting; }
    public MessageUtils messages() { return messages; }
    public TobaccoItems items() { return items; }
    public PlantManager plants() { return plants; }
    public MinigameManager minigames() { return minigames; }
}
