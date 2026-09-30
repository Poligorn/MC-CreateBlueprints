package com.blueprintforge.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ArchivePressTest {
    @Test
    void idleAndAStoppedShaftHoldTheHeadUp() {
        assertEquals(0.0F, ArchivePress.headDown(false, 32, 10, 100, 0));
        assertEquals(0.0F, ArchivePress.headDown(true, 0, 10, 100, 0.5F));
        assertEquals(0.0F, ArchivePress.headDown(true, 32, 0, 0, 0));
    }

    @Test
    void aWorkingArchiveStrikesAndThenRises() {
        float raised = ArchivePress.headDown(true, 16, 1, 200, 0);
        float down = 0;
        for (int tick = 0; tick < 80; tick++) {
            down = Math.max(down, ArchivePress.headDown(true, 16, tick, 200, 0));
        }
        assertEquals(0.0F, raised);
        assertEquals(1.0F, down);
        assertTrue(ArchivePress.strikeLength(256) < ArchivePress.strikeLength(16));
    }
}