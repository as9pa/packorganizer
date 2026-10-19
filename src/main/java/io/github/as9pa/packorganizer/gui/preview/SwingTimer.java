package io.github.as9pa.packorganizer.gui.preview;

/**
 * Arm swing timing from {@code EntityLivingBase}: six ticks at 20 per second (no haste or mining
 * fatigue), and a new swing restarts the arm only once the current one is halfway through.
 */
public final class SwingTimer {
    /** {@code getArmSwingAnimationEnd()} with no dig potion active. */
    private static final int SWING_TICKS = 6;

    /** Vanilla {@code swingProgressInt}. */
    private int ticks;
    private boolean swinging;

    public void swing() {
        if (!swinging || ticks >= SWING_TICKS / 2) {
            // Vanilla sets -1 and its next updateArmSwingProgress brings it to 0 at progress 0;
            // starting at 0 counts the six ticks from the click, as the mock does.
            ticks = 0;
            swinging = true;
        }
    }

    /** One game tick: vanilla {@code updateArmSwingProgress}. */
    public void tick() {
        if (!swinging) return;
        if (++ticks >= SWING_TICKS) {
            ticks = 0;
            swinging = false;
        }
    }

    /** True from the swing until its last tick has run. */
    public boolean swinging() {
        return swinging;
    }

    /**
     * 0..1 through the swing, 0 when idle. At {@code partialTicks} 0 this is vanilla
     * {@code swingProgress}; between ticks it runs on toward the next value.
     */
    public float progress(float partialTicks) {
        if (!swinging) return 0;
        return Math.min(1, (ticks + partialTicks) / SWING_TICKS);
    }
}
