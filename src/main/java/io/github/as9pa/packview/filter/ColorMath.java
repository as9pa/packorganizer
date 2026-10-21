package io.github.as9pa.packview.filter;

/** HSL helpers and the color wheel geometry. Pure math, no Minecraft classes. */
public final class ColorMath {
    private ColorMath() {}

    /** Clamped smoothstep on [0, 1]. */
    public static double smoothstep(double t) {
        if (t <= 0) return 0;
        if (t >= 1) return 1;
        return t * t * (3 - 2 * t);
    }

    /** Wheel lightness at normalized radius r: white center, flat 0.5 band, dark rim. */
    public static double wheelLightness(double r) {
        if (r < 0.3) return 1 - 0.5 * smoothstep(r / 0.3);
        if (r < 0.66) return 0.5;
        return 0.5 - 0.47 * smoothstep((r - 0.66) / 0.34);
    }

    /**
     * Maps an offset from the wheel center, in units of the wheel radius, to {hue degrees, lightness}.
     * Hue runs clockwise from the top with red at 0. Returns null outside the wheel.
     */
    public static double[] pixelToPick(double px, double py) {
        double r = Math.sqrt(px * px + py * py);
        if (r > 1) return null;
        double hue = Math.toDegrees(Math.atan2(px, -py));
        if (hue < 0) hue += 360;
        if (hue >= 360) hue -= 360;
        return new double[] {hue, wheelLightness(r)};
    }

    /** RGB (alpha ignored) to {hue degrees [0, 360), saturation, lightness}. */
    public static float[] rgbToHsl(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float l = (max + min) / 2;
        float d = max - min;
        if (d == 0) return new float[] {0, 0, l};
        float s = l > 0.5f ? d / (2 - max - min) : d / (max + min);
        float h;
        if (max == r) h = (g - b) / d + (g < b ? 6 : 0);
        else if (max == g) h = (b - r) / d + 2;
        else h = (r - g) / d + 4;
        h *= 60;
        if (h >= 360) h -= 360;
        return new float[] {h, s, l};
    }

    /** HSL (hue in degrees) to 0xRRGGBB with no alpha. */
    public static int hslToRgb(float h, float s, float l) {
        h = ((h % 360) + 360) % 360;
        s = clamp01(s);
        l = clamp01(l);
        float c = (1 - Math.abs(2 * l - 1)) * s;
        float x = c * (1 - Math.abs((h / 60) % 2 - 1));
        float m = l - c / 2;
        float r, g, b;
        if (h < 60) { r = c; g = x; b = 0; }
        else if (h < 120) { r = x; g = c; b = 0; }
        else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; }
        else if (h < 300) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        return (channel(r + m) << 16) | (channel(g + m) << 8) | channel(b + m);
    }

    private static float clamp01(float v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    private static int channel(float v) {
        return Math.round(clamp01(v) * 255);
    }
}
