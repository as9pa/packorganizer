package io.github.as9pa.packorganizer.gui.preview;

/**
 * The preview's spin angle in degrees: follows the mouse each frame while dragged, then a critically damped
 * spring brings the model back to face front with no jolt at release, settling in about 1.8 s.
 */
public final class SpinSpring {
    private static final float OMEGA = 3.5f;
    private static final float MAX_STEP = 0.05f;
    /** A release this long after the last drag means the mouse sat still: no fling. */
    private static final long STILL_NANOS = 80_000_000L;
    private static final float MAX_SPEED = 900;
    /** The fling is the drag speed averaged over about this long, so it matches the motion just before letting go. */
    private static final float SPEED_SECONDS = 0.04f;
    /** Frame gaps beyond this are not simulated; the spring has long settled by then. */
    private static final float MAX_SECONDS = 10;

    private float angle;
    /** Degrees per second. */
    private float velocity;
    private boolean dragging;
    private long lastDragNanos;
    /** The last drag that turned the model; frames with the mouse still do not count. */
    private long lastMoveNanos;

    /**
     * Degrees to turn for the pointer moving from window pixel column {@code fromRawX} to {@code toRawX}, at
     * {@code degPerGuiPx} per GUI pixel; a GUI pixel is {@code displayWidth / guiWidth} window pixels. Right turns
     * positive.
     */
    public static float dragDegrees(int fromRawX, int toRawX, int guiWidth, int displayWidth, float degPerGuiPx) {
        return (toRawX - fromRawX) * degPerGuiPx * guiWidth / Math.max(1, displayWidth);
    }

    /** A press in the view has moved far enough to become a spin; the spring's own speed is dropped. */
    public void press(long nanoTime) {
        dragging = true;
        velocity = 0;
        lastDragNanos = nanoTime;
        lastMoveNanos = nanoTime;
    }

    /**
     * One frame of the drag: {@code deltaDeg} is the turn since the last call, 0 when the mouse held still. Called every
     * frame, so the speed is a time-weighted average of the frames' samples whatever the frame rate.
     */
    public void drag(float deltaDeg, long nanoTime) {
        if (!dragging) return;
        angle += deltaDeg;
        float dt = (nanoTime - lastDragNanos) / 1e9f;
        if (dt > 0) {
            float weight = 1 - (float) Math.exp(-dt / SPEED_SECONDS);
            velocity += (deltaDeg / dt - velocity) * weight;
        }
        lastDragNanos = nanoTime;
        if (deltaDeg != 0) {
            lastMoveNanos = nanoTime;
        }
    }

    public void release(long nanoTime) {
        if (!dragging) return;
        dragging = false;
        angle = ((angle % 360) + 540) % 360 - 180; // ease back the short way round
        // Keep the fling unless the mouse sat still before letting go.
        velocity = nanoTime - lastMoveNanos > STILL_NANOS ? 0 : Math.max(-MAX_SPEED, Math.min(MAX_SPEED, velocity));
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
