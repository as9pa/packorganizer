package io.github.as9pa.packorganizer.filter;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map;

/**
 * Color share histogram of a texture. Each opaque pixel lands in exactly one bucket: dark, white,
 * or one of 36 hue x 3 lightness bins. Shares sum to 1 (all zero when no pixel counted).
 */
public final class ColorFeatures {
    public static final int HUE_BINS = 36;
    public static final int LIGHT_BINS = 3;
    public static final int BIN_COUNT = HUE_BINS * LIGHT_BINS;

    public double dark;
    public double white;
    public final double[] bins = new double[BIN_COUNT];

    /** Index of hue bin (0..35, 10 degrees each) and lightness bin (0..2). */
    public static int bin(int hueBin, int lightBin) {
        return hueBin * LIGHT_BINS + lightBin;
    }

    public static ColorFeatures from(int[] argb) {
        ColorFeatures f = new ColorFeatures();
        int counted = 0;
        for (int p : argb) {
            if (((p >>> 24) & 0xFF) < 128) continue;
            counted++;
            int max = Math.max((p >> 16) & 0xFF, Math.max((p >> 8) & 0xFF, p & 0xFF));
            if (max < 56) {
                f.dark++;
                continue;
            }
            float[] hsl = ColorMath.rgbToHsl(p);
            if (hsl[1] < 0.2f && hsl[2] > 0.75f) {
                f.white++;
                continue;
            }
            int hue = Math.min(HUE_BINS - 1, (int) (hsl[0] / 10));
            int light = hsl[2] < 0.35f ? 0 : hsl[2] < 0.65f ? 1 : 2;
            f.bins[bin(hue, light)]++;
        }
        if (counted > 0) {
            f.dark /= counted;
            f.white /= counted;
            for (int i = 0; i < BIN_COUNT; i++) f.bins[i] /= counted;
        }
        return f;
    }

    /** Sum of all shares: 1 for any texture with an opaque pixel, else 0. */
    public double total() {
        double t = dark + white;
        for (double b : bins) t += b;
        return t;
    }

    /** Sparse encoding: {"d": dark, "w": white, "b": {"index": share}} with zero bins omitted. */
    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("d", dark);
        o.addProperty("w", white);
        JsonObject b = new JsonObject();
        for (int i = 0; i < BIN_COUNT; i++) {
            if (bins[i] != 0) b.addProperty(Integer.toString(i), bins[i]);
        }
        o.add("b", b);
        return o;
    }

    /** Decodes {@link #toJson()} output; returns null for anything malformed. */
    public static ColorFeatures fromJson(JsonElement element) {
        try {
            if (element == null || !element.isJsonObject()) return null;
            JsonObject o = element.getAsJsonObject();
            ColorFeatures f = new ColorFeatures();
            f.dark = o.get("d").getAsDouble();
            f.white = o.get("w").getAsDouble();
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("b").entrySet()) {
                int i = Integer.parseInt(e.getKey());
                if (i < 0 || i >= BIN_COUNT) return null;
                f.bins[i] = e.getValue().getAsDouble();
            }
            return f;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
