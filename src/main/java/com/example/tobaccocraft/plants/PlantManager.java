package com.example.tobaccocraft.plants;

import com.example.tobaccocraft.TobaccoCraft;
import com.example.tobaccocraft.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class PlantManager {
    private final TobaccoCraft plugin;
    private final File dataFile;
    private final NamespacedKey displayKey;
    private final Map<TobaccoPlant.Position, TobaccoPlant> plants = new LinkedHashMap<>();
    private final Map<TobaccoPlant.Position, ItemDisplay> displays = new HashMap<>();
    private BukkitTask growthTask, lifecycleTask;

    public PlantManager(TobaccoCraft plugin) {
        this.plugin = plugin;
        dataFile = new File(plugin.getDataFolder(), "data.yml");
        displayKey = new NamespacedKey(plugin, "plant_display");
    }

    public Collection<TobaccoPlant> all() { return List.copyOf(plants.values()); }
    public TobaccoPlant get(Block block) {
        TobaccoPlant plant = plants.get(TobaccoPlant.Position.of(block));
        if (plant == null) plant = plants.get(TobaccoPlant.Position.of(block.getRelative(BlockFace.DOWN)));
        return plant;
    }
    public boolean contains(TobaccoPlant plant) { return plants.get(plant.position()) == plant; }
    public boolean isProtected(Block block) {
        return get(block) != null || plants.containsKey(TobaccoPlant.Position.of(block.getRelative(BlockFace.UP)));
    }
    public void updateStage(TobaccoPlant plant) {
        var variety = variety(plant);
        plant.update(System.currentTimeMillis(), variety.growthMillis(), 1);
    }

    private com.example.tobaccocraft.catalog.TobaccoVariety variety(TobaccoPlant plant) {
        var result = plugin.catalog().variety(plant.varietyId());
        return result == null ? plugin.catalog().variety("default") : result;
    }
    public boolean expired(TobaccoPlant plant) {
        var v = variety(plant);
        return plant.expired(System.currentTimeMillis(), v.growthMillis(), v.wiltMillis());
    }

    public TobaccoPlant plant(Block bottom) { return plant(bottom, "default"); }

    public TobaccoPlant plant(Block bottom, String varietyId) {
        if (plugin.catalog().variety(varietyId) == null) throw new IllegalArgumentException("Неизвестный сорт.");
        setFern(bottom, Bisected.Half.BOTTOM);
        setFern(bottom.getRelative(BlockFace.UP), Bisected.Half.TOP);
        TobaccoPlant plant = new TobaccoPlant(TobaccoPlant.Position.of(bottom), bottom.getWorld().getName(),
                System.currentTimeMillis(), false, varietyId);
        plants.put(plant.position(), plant);
        refresh(plant);
        save();
        return plant;
    }

    private static void setFern(Block block, Bisected.Half half) {
        Bisected data = (Bisected) Bukkit.createBlockData(Material.LARGE_FERN);
        data.setHalf(half);
        block.setBlockData(data, false);
    }

    public boolean intact(TobaccoPlant plant) {
        if (!plant.isLoaded()) return false;
        Block block = plant.location().getBlock();
        return fernHalf(block, Bisected.Half.BOTTOM)
                && fernHalf(block.getRelative(BlockFace.UP), Bisected.Half.TOP)
                && block.getRelative(BlockFace.DOWN).getType() == Material.FARMLAND;
    }

    private static boolean fernHalf(Block block, Bisected.Half half) {
        return block.getType() == Material.LARGE_FERN
                && block.getBlockData() instanceof Bisected data && data.getHalf() == half;
    }

    public void refresh(TobaccoPlant plant) {
        updateStage(plant);
        if (!plant.isLoaded() || !contains(plant)) return;
        if (expired(plant)) { remove(plant); save(); return; }
        Block block = plant.location().getBlock();
        // Обычный блок не PersistentDataHolder: индексируем запись координатами в PDC чанка.
        block.getChunk().getPersistentDataContainer().set(new NamespacedKey(plugin, plant.position().pdcKey()),
                PersistentDataType.LONG_ARRAY, new long[]{plant.plantedAt(), plant.stage(), plant.modelData(), plant.fullyGrown() ? 1 : 0, plant.forcedMaturedAt()});
        block.getChunk().getPersistentDataContainer().set(new NamespacedKey(plugin, "variety_" + plant.position().pdcKey()),
                PersistentDataType.STRING, plant.varietyId());
        if (!plugin.config().flag("growth.show-stage-display")) {
            removeDisplay(plant.position());
            return;
        }
        ItemDisplay display = displays.get(plant.position());
        if (display == null || !display.isValid()) {
            display = block.getWorld().spawn(block.getLocation().add(0.5, 1.1, 0.5), ItemDisplay.class, entity -> {
                entity.setPersistent(false);
                entity.setInvulnerable(true);
                entity.getPersistentDataContainer().set(displayKey, PersistentDataType.BYTE, (byte) 1);
                entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            });
            displays.put(plant.position(), display);
        }
        display.setItemStack(new ItemBuilder(Material.LARGE_FERN).model(plant.modelData()).build());
        float scale = 0.4f + plant.stage() * 0.2f;
        display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                new Vector3f(scale), new AxisAngle4f()));
        var variety = plugin.catalog().variety(plant.varietyId());
        String name = variety == null ? "§cНеизвестный сорт" : variety.name();
        display.setCustomName(name + " §7— " + plant.stageName() + " [" + plant.stage() + "/3]");
        display.setCustomNameVisible(true);
    }

    public boolean remove(TobaccoPlant plant) {
        if (!contains(plant)) return false;
        plants.remove(plant.position());
        if (plugin.minigames() != null) plugin.minigames().cancelPlant(plant);
        removeDisplay(plant.position());
        World world = plant.world();
        if (world != null) {
            Block bottom = plant.location().getBlock(); // Удаление/clear намеренно загружает нужный чанк.
            Block top = bottom.getRelative(BlockFace.UP);
            if (fernHalf(top, Bisected.Half.TOP)) top.setType(Material.AIR, false);
            if (fernHalf(bottom, Bisected.Half.BOTTOM)) bottom.setType(Material.AIR, false);
            bottom.getChunk().getPersistentDataContainer().remove(new NamespacedKey(plugin, plant.position().pdcKey()));
            bottom.getChunk().getPersistentDataContainer().remove(new NamespacedKey(plugin, "variety_" + plant.position().pdcKey()));
        }
        return true;
    }

    /** Выращивает только существующие незрелые кусты в сфере вокруг игрока. */
    public int growNearby(Location center, double radius) {
        if (center.getWorld() == null || !Double.isFinite(radius) || radius < 0) {
            throw new IllegalArgumentException("Некорректный центр или радиус выращивания");
        }
        int count = 0;
        for (TobaccoPlant plant : all()) {
            if (!plant.position().world().equals(center.getWorld().getUID())
                    || plant.location().distanceSquared(center) > radius * radius) continue;
            if (!plant.isLoaded()) center.getWorld().getChunkAt(plant.position().x() >> 4, plant.position().z() >> 4);
            if (!intact(plant)) continue;
            updateStage(plant);
            if (plant.stage() == 3) continue;
            plant.growFully();
            refresh(plant);
            count++;
        }
        if (count > 0) save();
        return count;
    }

    public int clear(Location center, double radius) {
        int count = 0;
        for (TobaccoPlant plant : all()) {
            if (plant.position().world().equals(center.getWorld().getUID())
                    && plant.location().distanceSquared(center) <= radius * radius && remove(plant)) count++;
        }
        save();
        return count;
    }

    private void removeDisplay(TobaccoPlant.Position position) {
        ItemDisplay display = displays.remove(position);
        if (display != null) display.remove();
    }

    public void unloadChunk(Chunk chunk) {
        for (TobaccoPlant.Position position : List.copyOf(displays.keySet())) {
            if (inChunk(position, chunk)) removeDisplay(position);
        }
    }

    private static boolean inChunk(TobaccoPlant.Position position, Chunk chunk) {
        return position.world().equals(chunk.getWorld().getUID())
                && (position.x() >> 4) == chunk.getX() && (position.z() >> 4) == chunk.getZ();
    }

    public void loadChunk(Chunk chunk) {
        unloadChunk(chunk);
        for (Entity entity : chunk.getEntities()) {
            if (entity.getPersistentDataContainer().has(displayKey, PersistentDataType.BYTE)) entity.remove();
        }
        var pdc = chunk.getPersistentDataContainer();
        for (NamespacedKey key : List.copyOf(pdc.getKeys())) {
            if (key.getNamespace().equals(displayKey.getNamespace()) && (key.getKey().startsWith("plant_") || key.getKey().startsWith("variety_plant_"))) pdc.remove(key);
        }
        for (TobaccoPlant plant : all()) {
            if (!inChunk(plant.position(), chunk)) continue;
            if (intact(plant)) refresh(plant);
            else remove(plant);
        }
    }

    public void start() {
        if (growthTask != null) growthTask.cancel();
        if (lifecycleTask != null) lifecycleTask.cancel();
        // Быстрые сорта и увядание проверяются раз в секунду без загрузки далёких чанков.
        lifecycleTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            boolean changed = false;
            for (var plant : all()) {
                if (!plant.isLoaded()) continue;
                if (expired(plant) || !intact(plant)) { remove(plant); changed = true; }
                else { int stage = plant.stage(); updateStage(plant); if (stage != plant.stage()) refresh(plant); }
            }
            if (changed) save();
        }, 20, 20);
        long ticks = Math.max(1, plugin.config().minutesToMillis("growth.check-interval-minutes") / 50);
        growthTask = new BukkitRunnable() {
            @Override public void run() {
                for (TobaccoPlant plant : all()) {
                    if (plant.isLoaded() && !intact(plant)) remove(plant);
                    else refresh(plant);
                }
                save();
            }
        }.runTaskTimer(plugin, ticks, ticks);
    }

    public List<TobaccoPlant> read() throws IOException, InvalidConfigurationException {
        YamlConfiguration data = new YamlConfiguration();
        if (!dataFile.exists()) return List.of();
        data.load(dataFile);
        if (!data.isList("plants")) throw new IllegalArgumentException("В data.yml отсутствует список plants");
        Map<TobaccoPlant.Position, TobaccoPlant> loaded = new LinkedHashMap<>();
        for (Object entry : data.getList("plants", List.of())) {
            if (!(entry instanceof Map<?, ?> row)) throw new IllegalArgumentException("Некорректная запись куста");
            UUID worldId = UUID.fromString(String.valueOf(row.get("world")));
            String name = String.valueOf(row.get("world-name"));
            int x = Math.toIntExact(number(row, "x")), y = Math.toIntExact(number(row, "y")), z = Math.toIntExact(number(row, "z"));
            long plantedAt = number(row, "planted-at");
            World world = Bukkit.getWorld(worldId);
            if (plantedAt < 0 || Math.abs((long) x) > 30000000 || Math.abs((long) z) > 30000000
                    || (world != null && (y <= world.getMinHeight() || y + 1 >= world.getMaxHeight()))) {
                throw new IllegalArgumentException("Некорректные координаты или время посадки в data.yml");
            }
            var position = new TobaccoPlant.Position(worldId, x, y, z);
            if (loaded.put(position, new TobaccoPlant(position, name, plantedAt, Boolean.TRUE.equals(row.get("fully-grown")),
                    row.get("variety") instanceof String varietyId ? varietyId : "default",
                    row.containsKey("forced-matured-at") ? number(row, "forced-matured-at") : System.currentTimeMillis())) != null) {
                throw new IllegalArgumentException("Дублирующийся куст в data.yml: " + position);
            }
        }
        return new ArrayList<>(loaded.values());
    }

    private static long number(Map<?, ?> row, String key) {
        Object value = row.get(key);
        if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue()) || n.doubleValue() != n.longValue()) {
            throw new IllegalArgumentException("Некорректное поле куста: " + key);
        }
        return n.longValue();
    }

    public void apply(List<TobaccoPlant> loaded) {
        var positions = loaded.stream().map(TobaccoPlant::position).collect(java.util.stream.Collectors.toSet());
        for (TobaccoPlant old : all()) if (!positions.contains(old.position())) remove(old);
        plants.clear();
        for (TobaccoPlant plant : loaded) { updateStage(plant); plants.put(plant.position(), plant); }
        for (World world : Bukkit.getWorlds()) for (Chunk chunk : world.getLoadedChunks()) loadChunk(chunk);
        save(); // Закрепляем миграцию времени принудительного созревания старых записей.
    }

    /** Замена через временный файл не оставляет наполовину записанный YAML. */
    public void save() {
        YamlConfiguration data = new YamlConfiguration();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (TobaccoPlant plant : plants.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("world", plant.position().world().toString());
            row.put("world-name", plant.worldName());
            row.put("x", plant.position().x()); row.put("y", plant.position().y()); row.put("z", plant.position().z());
            row.put("planted-at", plant.plantedAt()); row.put("stage", plant.stage());
            row.put("fully-grown", plant.fullyGrown());
            row.put("forced-matured-at", plant.forcedMaturedAt());
            row.put("variety", plant.varietyId());
            rows.add(row);
        }
        data.set("plants", rows);
        var target = dataFile.toPath();
        var temporary = target.resolveSibling("data.yml.tmp");
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(temporary, data.saveToString(), StandardCharsets.UTF_8);
            try { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException e) { plugin.getLogger().log(Level.SEVERE, "§cНе удалось сохранить кусты в data.yml", e); }
    }

    public void shutdown() {
        if (growthTask != null) growthTask.cancel();
        if (lifecycleTask != null) lifecycleTask.cancel();
        save();
        for (var position : List.copyOf(displays.keySet())) removeDisplay(position);
    }
}
