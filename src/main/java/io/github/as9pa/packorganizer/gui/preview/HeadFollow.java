package io.github.as9pa.packorganizer.gui.preview;

/**
 * Where the preview player looks: body yaw, head yaw and pitch in degrees, eased toward the cursor.
 * Same curve as the inventory's {@code drawEntityOnScreen} but softened, so the range is wider
 * without ever turning all the way.
 */
public final class HeadFollow {
    /** Seconds to cover 63% of the way to the target. */
    private static final double EASE_SECONDS = 0.1;

    private float targetBody;
    private float targetHead;
    private float targetPitch;
    private float body;
    private float head;
    private float pitch;

    /**
     * @param dx cursor minus eye in inventory units (30 per block), positive when the cursor is left of the model
     * @param dy cursor minus eye in inventory units, positive when the cursor is above the eye
     */
    public void target(float dx, float dy) {
        double ax = Math.atan(soft(dx, 150) / 40);
        double ay = Math.atan(soft(dy, 100) / 40);
        // Radians times these factors read as degrees: about 30 deg of pitch across the screen, 42 max.
        targetBody = (float) (ax * 22.5);
        targetHead = (float) (ax * 45);
        targetPitch = (float) (-ay * 35);
    }

    /** Eases toward the target so the head glides after the cursor instead of snapping. */
    public void step(float seconds) {
        if (!(seconds > 0)) return;
        float k = (float) (1 - Math.exp(-seconds / EASE_SECONDS));
        body += (targetBody - body) * k;
        head += (targetHead - head) * k;
        pitch += (targetPitch - pitch) * k;
    }

    /** Jumps straight to the target, for the first frame. */
    public void snap() {
        body = targetBody;
        head = targetHead;
        pitch = targetPitch;
    }

    public float bodyYaw() {
        return body;
    }

    public float headYaw() {
        return head;
    }

    public float pitch() {
        return pitch;
    }

    /** Grows like d near zero and never reaches k. */
    private static double soft(double d, double k) {
        return d / (1 + Math.abs(d) / k);
    }
}
