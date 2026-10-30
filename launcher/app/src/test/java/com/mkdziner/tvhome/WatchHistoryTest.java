package com.mkdziner.tvhome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WatchHistoryTest {
    @Test
    public void nearlyAtTheEndCountsAsFinished() {
        assertTrue(WatchHistory.finished(960_000, 1_000_000));
        assertFalse(WatchHistory.finished(500_000, 1_000_000));
    }

    @Test
    public void unknownLengthIsNeverFinished() {
        assertFalse(WatchHistory.finished(9_000_000, 0));
    }
}
