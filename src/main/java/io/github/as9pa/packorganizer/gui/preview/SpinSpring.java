package io.github.as9pa.packorganizer.gui.preview;

/**
 * The preview's spin angle in degrees: follows the mouse while dragged, then a critically damped
 * spring brings the model back to face front with no jolt at release, settling in about 1.8 s.
 */
public final class SpinSpring {
    private static final float OMEGA = 3.5f;
    private static final float MAX_STEP = 0.05f;
    /** A release this long after the last drag means the mouse sat still: no fling. */
    private static final long STILL_NANOS = 80_000_000L;
    private static final float MAX_SPEED = 900;
    /** Frame gaps beyond this are not simulated; the spring has long settled by then. */
    private static final float MAX_SECONDS = 10;

    private float angle;
    /** Degrees per second. */
    private float velocity;
    private boolean dragging;
    private long lastDragNanos;

    /** A press in the view has moved far enough to become a spin. */
    public void press() {
        dragging = true;
    }

    public void drag(float deltaDeg, long nanoTime) {
        if (!dragging) return;
        angle += deltaDeg;
        float dt = Math.max(0.004f, (nanoTime - lastDragNanos) / 1e9f);
        velocity = velocity * 0.6f + deltaDeg / dt * 0.4f; // smoothed drag speed
        lastDragNanos = nanoTime;
    }

    public void release(long nanoTime) {
        if (!dragging) return;
        dragging = false;
        angle = ((angle % 360) + 540) % 360 - 180; // ease back the short way round
        // Keep the fling unless the mouse sat still before letting go.
        velocity = nanoTime - lastDragNanos > STILL_NANOS ? 0 : Math.max(-MAX_SPEED, Math.min(MAX_SPEED, velocity));
    }

    public void step(float seconds) {
        if (dragging || !(seconds > 0)) return;
        float left = Math.min(seconds, MAX_SECONDS);
        while (left > 0) {
            float h = Math.min(left, MAX_STEP);
            velocity += (-OMEGA * OMEGA * angle - 2 * OMEGA * velocity) * h;
            angle += velocity * h;
            left -= h;
        }
    }

    public float angle() {
        return angle;
    }

    public boolean dragging() {
        return dragging;
    }
}
