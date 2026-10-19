package io.github.as9pa.packorganizer.gui.preview;

import static org.junit.Assert.*;

import org.junit.Test;

public class SubTickTest {
    private static final long T0 = 7_000_000_000L;

    @Test
    public void zeroBeforeTheFirstTick() {
        SubTick s = new SubTick();
        assertEquals(0f, s.partial(T0), 0f);
        assertEquals(0f, s.partial(T0 + 40_000_000L), 0f);
    }

    @Test
    public void zeroRightAtTheTick() {
        SubTick s = new SubTick();
        s.tick(T0);
        assertEquals(0f, s.partial(T0), 0f);
    }

    @Test
    public void midTickFraction() {
        SubTick s = new SubTick();
        s.tick(T0);
        assertEquals(0.5f, s.partial(T0 + 25_000_000L), 1e-6f);
        assertEquals(0.2f, s.partial(T0 + 10_000_000L), 1e-6f);
    }

    @Test
    public void clampedToOnePastOneTick() {
        SubTick s = new SubTick();
        s.tick(T0);
        assertEquals(1f, s.partial(T0 + SubTick.TICK_NANOS), 0f);
        assertEquals(1f, s.partial(T0 + 3 * SubTick.TICK_NANOS), 0f);
    }

    @Test
    public void clampedToZeroForAnEarlierClock() {
        SubTick s = new SubTick();
        s.tick(T0);
        assertEquals(0f, s.partial(T0 - 5_000_000L), 0f);
    }

    @Test
    public void tickResetsTheFraction() {
        SubTick s = new SubTick();
        s.tick(T0);
        assertEquals(1f, s.partial(T0 + 70_000_000L), 0f);
        s.tick(T0 + 70_000_000L);
        assertEquals(0f, s.partial(T0 + 70_000_000L), 0f);
        assertEquals(0.25f, s.partial(T0 + 82_500_000L), 1e-6f);
    }
}
