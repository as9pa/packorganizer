package io.github.as9pa.packview.gui.preview;

import static org.junit.Assert.*;

import org.junit.Test;

public class SkyClockTest {
    @Test
    public void dayStartsAtSunrise() {
        assertEquals(360, SkyClock.minuteOfDay(0, true));
    }

    @Test
    public void quarterDayIsNoon() {
        assertEquals(720, SkyClock.minuteOfDay(5000, true));
    }

    @Test
    public void fullDayComesBackToSunrise() {
        assertEquals(360, SkyClock.minuteOfDay(20000, true));
    }

    @Test
    public void cycleOffStaysAtNoon() {
        assertEquals(720, SkyClock.minuteOfDay(0, false));
        assertEquals(720, SkyClock.minuteOfDay(5000, false));
        assertEquals(720, SkyClock.minuteOfDay(13333, false));
    }

    @Test
    public void noonIsThePlainsSkyColor() {
        // mock skyColor(720): 0x78A7FF at full brightness
        assertColor(0x78A7FF, SkyClock.skyColor(720));
    }

    @Test
    public void midnightIsBlack() {
        // mock skyColor(0): brightness clamps to 0
        assertColor(0x000000, SkyClock.skyColor(0));
    }

    @Test
    public void skyTurnsTwoDegreesPerSecond() {
        assertEquals(17, SkyClock.rotation(0), 1e-4);
        assertEquals(15, SkyClock.rotation(1000), 1e-4);
    }

    private static void assertColor(int expected, int actual) {
        for (int shift = 0; shift <= 16; shift += 8) {
            int e = expected >> shift & 0xFF;
            int a = actual >> shift & 0xFF;
            assertTrue("channel " + shift + ": expected " + e + " got " + a, Math.abs(e - a) <= 2);
        }
    }
}
