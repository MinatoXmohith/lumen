package com.mkdziner.tvhome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RemoteKeysTest {
    @Test
    public void theButtonsThatGetYouOutStayLocked() {
        for (int code : new int[]{19, 20, 21, 22, 23, 66, 4, 3, 26}) assertTrue(RemoteKeys.locked(code));
    }

    @Test
    public void brandButtonsCanBeChanged() {
        assertFalse(RemoteKeys.locked(85));   // play/pause
        assertFalse(RemoteKeys.locked(0));    // a brand button Android has no name for
    }
}
