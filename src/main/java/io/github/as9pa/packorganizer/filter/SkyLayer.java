package io.github.as9pa.packorganizer.filter;

/**
 * One MCPatcher or OptiFine custom sky layer from a skyN.properties file. Times are minutes of the day, 0..1439,
 * with 6:00 as sunrise. No Minecraft classes.
 */
public final class SkyLayer {
    public static final int MINUTES_PER_DAY = 24 * 60;

    /** The image path relative to assets/minecraft/, or a pack-root path when it starts with "assets/". */
    public final String source;
    public final int startFadeIn;
    public final int endFadeIn;
    public final int endFadeOut;
    public final int startFadeOut;
    /** Lower case, like "add" or "replace". */
    public final String blend;
    public final boolean rotate;
    public final float speed;

    public SkyLayer(String source, int startFadeIn, int endFadeIn, int endFadeOut, int startFadeOut, String blend,
            boolean rotate, float speed) {
        this.source = source;
        this.startFadeIn = startFadeIn;
        this.endFadeIn = endFadeIn;
        this.endFadeOut = endFadeOut;
        this.startFadeOut = startFadeOut;
        this.blend = blend;
        this.rotate = rotate;
        this.speed = speed;
    }

    /**
     * 0 outside [startFadeIn, endFadeOut], rising linearly to 1 at endFadeIn, 1 until startFadeOut, then falling
     * linearly to 0 at endFadeOut. Windows may wrap past midnight.
     */
    public float brightness(int minuteOfDay) {
        int t = sinceStart(minuteOfDay);
        int in = sinceStart(endFadeIn);
        int out = sinceStart(startFadeOut);
        int end = sinceStart(endFadeOut);
        if (t > end) {
            return 0f;
        }
        if (t < in) {
            return t / (float) in;
        }
        if (t <= out) {
            return 1f;
        }
        return (end - t) / (float) (end - out);
    }

    private int sinceStart(int minute) {
        return Math.floorMod(minute - startFadeIn, MINUTES_PER_DAY);
    }
}
