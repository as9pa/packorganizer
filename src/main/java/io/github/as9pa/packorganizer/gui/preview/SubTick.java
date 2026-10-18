package io.github.as9pa.packorganizer.gui.preview;

/**
 * How far the preview is into its current 20 Hz tick, from the real clock. The screen's partial tick freezes while
 * the game is paused (Resource Packs opened from the pause menu) though GUI ticks keep running, which steps the swing,
 * hurt flash, arm sway and particles at 20 Hz; this one keeps moving, paused or not.
 */
public final class SubTick {
    /** One game tick at 20 a second. */
    static final long TICK_NANOS = 50_000_000L;

    private boolean started;
    private long tickNanos;

    /** A preview tick ran at {@code nanoTime}: the fraction starts again from 0. */
    public void tick(long nanoTime) {
        started = true;
        tickNanos = nanoTime;
    }

    /** 0..1 through the tick since the last {@link #tick}; held at 1 past a full tick, 0 before the first. */
    public float partial(long nanoTime) {
        if (!started) return 0;
        float f = (nanoTime - tickNanos) / (float) TICK_NANOS;
        return f <= 0 ? 0 : f >= 1 ? 1 : f;
    }
}
