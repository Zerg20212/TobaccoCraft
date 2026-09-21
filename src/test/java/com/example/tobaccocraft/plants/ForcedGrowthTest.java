package com.example.tobaccocraft.plants;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ForcedGrowthTest {
    @Test void grownPlantRemainsMatureAfterGrowthTickAndConfigChange() {
        var plant = new TobaccoPlant(new TobaccoPlant.Position(UUID.randomUUID(), 0, 64, 0), "world", 10000);
        plant.growFully();
        plant.update(10000, 60000, 36500);
        assertEquals(3, plant.stage());
        assertEquals(1103, plant.modelData());
        assertTrue(plant.fullyGrown());
        assertEquals(10000, plant.plantedAt());
    }

    @Test void persistedFlagRestoresMaturityWithoutChangingPlantingTime() {
        var plant = new TobaccoPlant(new TobaccoPlant.Position(UUID.randomUUID(), 0, 64, 0), "world", 10000, true);
        plant.update(10000, 1200000, 10);
        assertEquals(3, plant.stage());
        assertEquals(10000, plant.plantedAt());
    }

    @Test void oldRecordsWithoutFlagStillGrowNormally() {
        var plant = new TobaccoPlant(new TobaccoPlant.Position(UUID.randomUUID(), 0, 64, 0), "world", 10000);
        plant.update(10000, 1200000, 10);
        assertEquals(0, plant.stage());
        assertFalse(plant.fullyGrown());
    }
}
