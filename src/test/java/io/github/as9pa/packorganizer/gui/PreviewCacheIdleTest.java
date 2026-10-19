package io.github.as9pa.packorganizer.gui;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PreviewCacheIdleTest {
    @Test
    public void idleBeforeAnyRequest() {
        // No request starts the loader, so nothing is queued or in flight.
        assertTrue(PreviewCache.idle());
    }
}
