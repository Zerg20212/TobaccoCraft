package com.example.tobaccocraft.listeners;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SmokeWindowTest {
    @Test void threeWithinFiveMinutesAreCounted() {
        assertArrayEquals(new long[]{1000, 120000, 240000},
                SmokeListener.recentTimes(new long[]{1000, 120000, 240000}, 240000, 300000));
    }
    @Test void chainedCigarettesDoNotKeepOldOnesAlive() {
        assertArrayEquals(new long[]{240000, 480000},
                SmokeListener.recentTimes(new long[]{1000, 240000, 480000}, 480000, 300000));
    }
    @Test void exactExpiryIsExcluded() {
        assertArrayEquals(new long[]{1001}, SmokeListener.recentTimes(new long[]{1000, 1001}, 301000, 300000));
    }
    @Test void logoutOrRestartDoesNotExtendWindow() {
        assertArrayEquals(new long[0], SmokeListener.recentTimes(new long[]{1000, 2000}, 500000, 300000));
    }
    @Test void futureTimestampsAreIgnored() {
        assertArrayEquals(new long[0], SmokeListener.recentTimes(new long[]{2000}, 1000, 300000));
    }
}
