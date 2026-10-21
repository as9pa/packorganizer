package io.github.as9pa.packview.filter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Scores color features against a wheel pick (hue degrees, lightness). Pure math, no Minecraft classes. */
public final class ColorScore {
    public static final double SIGMA = 22;
    public static final double DARK_EDGE = 0.12;
    public static final double WHITE_EDGE = 0.88;
    public static final double BASE_THRESHOLD = 0.2;
    public static final double DARK_THRESHOLD = 0.7;
    public static final double WHITE_THRESHOLD = 0.3;
    public static final int FALLBACK_COUNT = 10;

    private static final double[] LIGHT_CENTERS = {0.25, 0.5, 0.75};

    private ColorScore() {}

    /** Hue match: share-weighted gaussian on angular distance, damped by lightness distance. */
    public static double hueScore(ColorFeatures f, double hue, double l) {
        double sum = 0;
        for (int h = 0; h < ColorFeatures.HUE_BINS; h++) {
            double g = gauss(angularDistance(h * 10 + 5, hue));
            for (int b = 0; b < ColorFeatures.LIGHT_BINS; b++) {
                double share = f.bins[ColorFeatures.bin(h, b)];
                if (share == 0) continue;
                sum += share * g * Math.max(0.15, 1 - Math.abs(LIGHT_CENTERS[b] - l) * 2.5);
            }
        }
        return sum;
    }

    /** Score of one texture; near the dark or white end it blends toward the dark or white share. */
    public static double score(ColorFeatures f, double hue, double l) {
        if (f == null) return 0;
        double h = hueScore(f, hue, l);
        if (l < DARK_EDGE) return lerp(h, f.dark, darkT(l));
        if (l > WHITE_EDGE) return lerp(h, f.white, whiteT(l));
        return h;
    }

    /** Score a pack must reach to count as close at lightness l. */
    public static double threshold(double l) {
        if (l < DARK_EDGE) return lerp(BASE_THRESHOLD, DARK_THRESHOLD, darkT(l));
        if (l > WHITE_EDGE) return lerp(BASE_THRESHOLD, WHITE_THRESHOLD, whiteT(l));
        return BASE_THRESHOLD;
    }

    /** Max of sword and armor scores; a null slot falls back to the default pack's features. */
    public static double packScore(
            ColorFeatures sword,
            ColorFeatures armor,
            ColorFeatures defaultSword,
            ColorFeatures defaultArmor,
            double hue,
            double l) {
        double s = score(sword != null ? sword : defaultSword, hue, l);
        double a = score(armor != null ? armor : defaultArmor, hue, l);
        return Math.max(s, a);
    }

    /**
     * Indices (ascending) of the packs to show: every score at or above {@link #threshold(double)};
     * if none is close, the top {@link #FALLBACK_COUNT} by score.
     */
    public static List<Integer> shownIndices(List<Double> scores, double l) {
        double t = threshold(l);
        List<Integer> shown = new ArrayList<Integer>();
        for (int i = 0; i < scores.size(); i++) {
            if (scores.get(i) >= t) shown.add(i);
        }
        if (!shown.isEmpty()) return shown;
        List<Integer> order = new ArrayList<Integer>();
        for (int i = 0; i < scores.size(); i++) order.add(i);
        Collections.sort(order, (x, y) -> {
            int c = Double.compare(scores.get(y), scores.get(x));
            return c != 0 ? c : Integer.compare(x, y);
        });
        List<Integer> top = new ArrayList<Integer>(order.subList(0, Math.min(FALLBACK_COUNT, order.size())));
        Collections.sort(top);
        return top;
    }

    /** Shortest distance between two hue angles in degrees, in [0, 180]. */
    static double angularDistance(double a, double b) {
        double d = Math.abs(a - b) % 360;
        return d > 180 ? 360 - d : d;
    }

    private static double gauss(double d) {
        return Math.exp(-(d * d) / (2 * SIGMA * SIGMA));
    }

    private static double darkT(double l) {
        return (DARK_EDGE - l) / DARK_EDGE;
    }

    private static double whiteT(double l) {
        return (l - WHITE_EDGE) / (1 - WHITE_EDGE);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
