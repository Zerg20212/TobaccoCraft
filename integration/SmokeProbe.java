package com.example.tobaccocraft.integration;

import com.example.tobaccocraft.TobaccoCraft;
import com.example.tobaccocraft.items.TobaccoItems;
import com.example.tobaccocraft.plants.TobaccoPlant;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Files;
import java.util.List;
import java.util.logging.Level;

/** Отдельный тестовый плагин; в основной JAR не входит. Только для пустого тестового сервера. */
public final class SmokeProbe extends JavaPlugin {
    private TobaccoCraft tobacco;
    private TobaccoPlant plant;
    private TobaccoPlant edgePlant, outsidePlant;

    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, this::checkInitial, 20);
    }

    private void checkInitial() {
        try {
            tobacco = (TobaccoCraft) Bukkit.getPluginManager().getPlugin("TobaccoCraft");
            check(tobacco != null && tobacco.isEnabled(), "Плагин включён");
            for (String id : List.of("seeds", "leaf", "shears", "cigarette", "machine")) {
                var item = tobacco.items().create(id);
                check(tobacco.items().is(item, id), "PDC предмета: " + id);
                var copied = item.clone();
                var meta = copied.getItemMeta();
                meta.getPersistentDataContainer().remove(new NamespacedKey(tobacco, "item_type"));
                copied.setItemMeta(meta);
                check(!tobacco.items().is(copied, id), "Защита от подделки: " + id);
            }
            check(!TobaccoItems.isPlainPaper(tobacco.items().create("cigarette")), "Сигарета не заменяет бумагу");
            var world = Bukkit.getWorlds().getFirst();
            Block bottom = world.getBlockAt(32, 100, 32);
            bottom.getRelative(BlockFace.DOWN).setType(Material.FARMLAND, false);
            plant = tobacco.plants().plant(bottom);
            check(tobacco.plants().intact(plant), "Обе половины папоротника установлены");
            check(tobacco.plants().get(bottom.getRelative(BlockFace.UP)) == plant, "Опознание верхней половины");
            check(tobacco.plants().isProtected(bottom.getRelative(BlockFace.DOWN)), "Защита грядки");
            long plantedAt = plant.plantedAt();
            Block edge = bottom.getRelative(5, 0, 0), outside = bottom.getRelative(6, 0, 0);
            edge.getRelative(BlockFace.DOWN).setType(Material.FARMLAND, false);
            outside.getRelative(BlockFace.DOWN).setType(Material.FARMLAND, false);
            edgePlant = tobacco.plants().plant(edge);
            outsidePlant = tobacco.plants().plant(outside);
            check(tobacco.plants().growNearby(bottom.getLocation(), 5) == 2, "Выращивание в радиусе 5 включая границу");
            check(edgePlant.stage() == 3 && outsidePlant.stage() == 0, "Куст вне радиуса не затронут");
            check(tobacco.plants().growNearby(bottom.getLocation(), 5) == 0, "Повторное выращивание не считает зрелые кусты");
            tobacco.reloadData();
            plant = tobacco.plants().get(bottom);
            check(plant != null && plant.stage() == 3 && plant.fullyGrown(), "Принудительная зрелость переживает reload data.yml");
            check(tobacco.plants().get(edge).stage() == 3 && tobacco.plants().get(outside).stage() == 0, "Граница радиуса сохраняется после reload");
            var data = bottom.getChunk().getPersistentDataContainer().get(
                    new NamespacedKey(tobacco, plant.position().pdcKey()), PersistentDataType.LONG_ARRAY);
            check(data != null && data[0] == plantedAt && data[1] == 3 && data[2] == 1103 && data.length == 5 && data[3] == 1 && data[4] == plant.forcedMaturedAt(), "Данные куста в PDC чанка");
            long displays = world.getNearbyEntities(bottom.getLocation(), 3, 3, 3).stream()
                    .filter(e -> e instanceof ItemDisplay).count();
            check(displays == 1, "После reload ровно одна модель куста");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tobacco info");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tobacco reload");
            plant = tobacco.plants().get(bottom);
            Bukkit.getScheduler().runTaskLater(this, this::checkAfterTicks, 100);
        } catch (Throwable failure) { fail(failure); }
    }

    private void checkAfterTicks() {
        try {
            check(tobacco.plants().intact(plant), "Папоротник и грядка пережили физику мира");
            var bottom = plant.location().getBlock();
            check(tobacco.plants().clear(plant.location(), 50) == 3, "Удаление в радиусе");
            check(bottom.getType() == Material.AIR && bottom.getRelative(BlockFace.UP).getType() == Material.AIR,
                    "Обе половины удалены");
            check(bottom.getRelative(BlockFace.DOWN).getType() == Material.FARMLAND, "Грядка осталась");
            check(tobacco.plants().read().isEmpty(), "Удаление сохранено в data.yml");
            check(!bottom.getChunk().getPersistentDataContainer().has(new NamespacedKey(tobacco, plant.position().pdcKey())),
                    "Запись PDC очищена");
            new EditorScenario(this, tobacco, () -> {
                try {
                    getLogger().info("TOBACCO_SMOKE_TEST_OK");
                    Files.writeString(getDataFolder().toPath().resolveSibling("smoke-result.txt"), "OK");
                    Bukkit.shutdown();
                } catch (Exception error) { fail(error); }
            }, this::fail).start();
        } catch (Throwable failure) { fail(failure); }
    }

    private void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        getLogger().info("Проверено: " + description);
    }
    private void fail(Throwable failure) {
        getLogger().log(Level.SEVERE, "TOBACCO_SMOKE_TEST_FAILED", failure);
        Bukkit.shutdown();
    }
}
