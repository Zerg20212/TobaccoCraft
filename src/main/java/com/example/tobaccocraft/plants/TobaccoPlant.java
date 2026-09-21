package com.example.tobaccocraft.plants;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.UUID;

public final class TobaccoPlant {
    public record Position(UUID world, int x, int y, int z) {
        public static Position of(Block block) {
            return new Position(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
        public String pdcKey() { return "plant_" + x + "_" + y + "_" + z; }
    }

    private final Position position;
    private final String worldName;
    private final long plantedAt;
    private int stage;
    private boolean fullyGrown;
    private long forcedMaturedAt;
    private final String varietyId;

    public TobaccoPlant(Position position, String worldName, long plantedAt) {
        this(position, worldName, plantedAt, false);
    }

    public TobaccoPlant(Position position, String worldName, long plantedAt, boolean fullyGrown) {
        this(position, worldName, plantedAt, fullyGrown, "default");
    }

    public TobaccoPlant(Position position, String worldName, long plantedAt, boolean fullyGrown, String varietyId) {
        this(position, worldName, plantedAt, fullyGrown, varietyId, fullyGrown ? System.currentTimeMillis() : 0);
    }

    public TobaccoPlant(Position position, String worldName, long plantedAt, boolean fullyGrown, String varietyId, long forcedMaturedAt) {
        if (forcedMaturedAt < 0) throw new IllegalArgumentException("Некорректное время созревания.");
        this.forcedMaturedAt = fullyGrown ? forcedMaturedAt : 0;
        com.example.tobaccocraft.catalog.CatalogManager.checkId(varietyId);
        this.varietyId = varietyId;
        this.position = position;
        this.worldName = worldName;
        this.plantedAt = plantedAt;
        this.fullyGrown = fullyGrown;
        if (fullyGrown) stage = 3;
    }

    public Position position() { return position; }
    public String worldName() { return worldName; }
    public String varietyId() { return varietyId; }
    public long plantedAt() { return plantedAt; }
    public int stage() { return stage; }
    public boolean fullyGrown() { return fullyGrown; }
    public long forcedMaturedAt() { return forcedMaturedAt; }
    public void growFully() { growFully(System.currentTimeMillis()); }
    public void growFully(long now) { if (!fullyGrown) forcedMaturedAt = now; fullyGrown = true; stage = 3; }
    public boolean expired(long now, long growthMillis, long wiltMillis) {
        long maturedAt = fullyGrown ? forcedMaturedAt : plantedAt + growthMillis;
        return wiltMillis > 0 && now >= maturedAt && now - maturedAt >= wiltMillis;
    }
    public int modelData() { return 1100 + stage; }
    public World world() { return Bukkit.getWorld(position.world()); }
    public Location location() {
        return new Location(world(), position.x(), position.y(), position.z());
    }
    public boolean isLoaded() {
        World world = world();
        return world != null && world.isChunkLoaded(position.x() >> 4, position.z() >> 4);
    }
    public String stageName() {
        return switch (stage) {
            case 0 -> "росточек";
            case 1 -> "молодой куст";
            case 2 -> "взрослый куст";
            default -> "готов к сбору";
        };
    }
    public void update(long now, long dayMillis, int daysRequired) {
        stage = fullyGrown ? 3 : stageAt(plantedAt, now, dayMillis, daysRequired);
    }

    /** При 10 днях границы точно равны 3, 6 и 10 дням; при настройке масштабируются. */
    public static int stageAt(long plantedAt, long now, long dayMillis, int daysRequired) {
        if (dayMillis <= 0 || daysRequired <= 0) throw new IllegalArgumentException("Некорректное время роста");
        double progress = Math.max(0.0, (double) now - plantedAt) / ((double) dayMillis * daysRequired);
        if (progress >= 1.0) return 3;
        if (progress >= 0.6) return 2;
        if (progress >= 0.3) return 1;
        return 0;
    }
}
