package com.example.tobaccocraft.plants;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class TobaccoPlantTest {
    private static final long DAY = 20 * 60 * 1000L;
    private static final long PLANTED = 1700000000000L;
    @ParameterizedTest
    @CsvSource({"0,0", "2,0", "3,1", "5,1", "6,2", "9,2", "10,3", "50,3"})
    void defaultStages(int days, int expected) {
        assertEquals(expected, TobaccoPlant.stageAt(PLANTED, PLANTED + days * DAY, DAY, 10));
    }
    @Test void preciseBoundaries() {
        assertEquals(0, TobaccoPlant.stageAt(PLANTED, PLANTED + 3 * DAY - 1, DAY, 10));
        assertEquals(1, TobaccoPlant.stageAt(PLANTED, PLANTED + 6 * DAY - 1, DAY, 10));
        assertEquals(2, TobaccoPlant.stageAt(PLANTED, PLANTED + 10 * DAY - 1, DAY, 10));
    }
    @Test void customTotalScalesAllStages() {
        assertEquals(1, TobaccoPlant.stageAt(PLANTED, PLANTED + 6 * DAY, DAY, 20));
        assertEquals(2, TobaccoPlant.stageAt(PLANTED, PLANTED + 12 * DAY, DAY, 20));
        assertEquals(3, TobaccoPlant.stageAt(PLANTED, PLANTED + 20 * DAY, DAY, 20));
    }
    @Test void backwardsClockDoesNotMaturePlant() {
        assertEquals(0, TobaccoPlant.stageAt(PLANTED, PLANTED - DAY, DAY, 10));
    }
    @Test void rejectsInvalidDuration() {
        assertThrows(IllegalArgumentException.class, () -> TobaccoPlant.stageAt(PLANTED, PLANTED, 0, 10));
    }
}
