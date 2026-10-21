package io.github.as9pa.packview.gui.preview;

import io.github.as9pa.packview.filter.SkyLayer;

/**
 * Time of day for the sky backdrop: a full day every {@link #DAY_MS}, starting at sunrise, or frozen at noon
 * with the day cycle off. Colors follow vanilla {@code World.getSkyColor} for plains. No Minecraft classes.
 */
public final class SkyClock {
    /** One full day of the preview sky. */
    static final long DAY_MS = 20000L;
    /** 6:00, world time 0. */
    private static final int SUNRISE = 6 * 60;
    private static final int NOON = 12 * 60;
    /** Plains {@code getSkyColorByTemp(0.8)}. */
    private static final int PLAINS_SKY = 0x78A7FF;
    /** The sky turns 2 degrees per second, a full turn every 180 s. */
    private static final long TURN_MS = 180000L;
    /** Starting yaw: the sun begins right of the model. */
    private static final float START_YAW = 17f;

    private SkyClock() {}

    /** Minute of the day, 0..1439, for {@code ms} since the preview started; 12:00 when {@code cycle} is off. */
    public static int minuteOfDay(long ms, boolean cycle) {
        if (!cycle) return NOON;
        long into = Math.floorMod(ms, DAY_MS) * SkyLayer.MINUTES_PER_DAY / DAY_MS;
        return (int) ((SUNRISE + into) % SkyLayer.MINUTES_PER_DAY);
    }

    /**
     * Vanilla sky color as 0xRRGGBB: the plains color times the brightness from
     * {@code calculateCelestialAngle}, as mock {@code skyColor}.
     */
    public static int skyColor(int minuteOfDay) {
        double ticks = Math.floorMod(minuteOfDay - SUNRISE, SkyLayer.MINUTES_PER_DAY) * 24000.0 / SkyLayer.MINUTES_PER_DAY;
        double f = ticks / 24000.0 - 0.25;
        if (f < 0) f += 1;
        double f0 = f;
        f = 1 - (Math.cos(f * Math.PI) + 1) / 2;
        f = f0 + (f - f0) / 3;
        double b = Math.min(1, Math.max(0, Math.cos(f * Math.PI * 2) * 2 + 0.5));
        int r = (int) Math.round((PLAINS_SKY >> 16 & 0xFF) * b);
        int g = (int) Math.round((PLAINS_SKY >> 8 & 0xFF) * b);
        int bl = (int) Math.round((PLAINS_SKY & 0xFF) * b);
        return r << 16 | g << 8 | bl;
    }

    /** Camera yaw in degrees, as mock {@code frame}: 17 at the start, turning 2 degrees per second. */
    public static float rotation(long ms) {
        return START_YAW - Math.floorMod(ms, TURN_MS) * (360f / TURN_MS);
    }
}
