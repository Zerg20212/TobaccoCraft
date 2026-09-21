package com.example.tobaccocraft.plants;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PlantLifetimeTest {
    private TobaccoPlant plant() {
        return new TobaccoPlant(new TobaccoPlant.Position(UUID.randomUUID(), 1, 64, 1), "world", 1000);
    }
    @Test void varietiesHaveIndependentGrowthSpeed() {
        var fast = plant(); var slow = plant();
        fast.update(11000, 10000, 1); slow.update(11000, 100000, 1);
        assertEquals(3, fast.stage()); assertEquals(0, slow.stage());
    }
    @Test void witheringStartsAtMaturityAndIncludesExactDeadline() {
        var plant = plant();
        assertFalse(plant.expired(5000, 10000, 2000));
        assertFalse(plant.expired(12999, 10000, 2000));
        assertTrue(plant.expired(13000, 10000, 2000));
    }
    @Test void disabledWitheringNeverExpires() {
        assertFalse(plant().expired(Long.MAX_VALUE, 10000, 0));
    }
    @Test void offlineTimeCountsFromNaturalMaturity() {
        assertTrue(plant().expired(1000000, 10000, 2000));
    }
    @Test void forcedGrowthStartsLifetimeImmediatelyAndDoesNotExtendIt() {
        var plant = plant(); plant.growFully(2000); plant.growFully(2500);
        assertFalse(plant.expired(3999, 1000000, 2000));
        assertTrue(plant.expired(4000, 1000000, 2000));
        assertEquals(2000, plant.forcedMaturedAt());
    }
    @Test void restoredForcedMaturityPreservesDeadline() {
        var original = plant(); original.growFully(2000);
        var restored = new TobaccoPlant(original.position(), original.worldName(), original.plantedAt(), true, "default", original.forcedMaturedAt());
        assertTrue(restored.expired(4000, 1000000, 2000));
    }
}
