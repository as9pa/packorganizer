package io.github.as9pa.packorganizer.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class ColorScoreTest {
    private static final double EPS = 1e-9;

    private static ColorFeatures single(int hueBin, int lightBin) {
        ColorFeatures f = new ColorFeatures();
        f.bins[ColorFeatures.bin(hueBin, lightBin)] = 1;
        return f;
    }

    @Test
    public void exactHueAndLightnessScoresOne() {
        assertEquals(1.0, ColorScore.score(single(0, 1), 5, 0.5), EPS);
    }

    @Test
    public void hueFallsOffAsGaussian() {
        assertEquals(Math.exp(-0.5), ColorScore.score(single(0, 1), 27, 0.5), EPS);
    }

    @Test
    public void hueDistanceWrapsAround() {
        // Bin 35 centre is 355, which is 10 degrees from 5 across red.
        assertEquals(10, ColorScore.angularDistance(355, 5), EPS);
        assertEquals(Math.exp(-100.0 / (2 * 22.0 * 22.0)), ColorScore.score(single(35, 1), 5, 0.5), EPS);
    }

    @Test
    public void lightnessDampingHasFloor() {
        assertEquals(1 - 0.25 * 2.5, ColorScore.score(single(0, 1), 5, 0.75), EPS);
        assertEquals(0.15, ColorScore.score(single(0, 0), 5, 0.75), EPS);
    }

    @Test
    public void thresholdBlendsAtEnds() {
        assertEquals(0.2, ColorScore.threshold(0.5), EPS);
        assertEquals(0.2, ColorScore.threshold(0.12), EPS);
        assertEquals(0.7, ColorScore.threshold(0), EPS);
        assertEquals(0.45, ColorScore.threshold(0.06), EPS);
        assertEquals(0.2, ColorScore.threshold(0.88), EPS);
        assertEquals(0.3, ColorScore.threshold(1), EPS);
        assertEquals(0.25, ColorScore.threshold(0.94), EPS);
    }

    @Test
    public void darkAndWhiteBlendScore() {
        ColorFeatures f = new ColorFeatures();
        f.dark = 0.6;
        f.white = 0.4;
        assertEquals(0.6, ColorScore.score(f, 0, 0), EPS);
        assertEquals(0.3, ColorScore.score(f, 0, 0.06), EPS);
        assertEquals(0.4, ColorScore.score(f, 0, 1), EPS);
        assertEquals(0.0, ColorScore.score(f, 0, 0.5), EPS);
    }

    @Test
    public void packScoreTakesMaxAndUsesDefaultsForMissingSlots() {
        ColorFeatures red = single(0, 1);
        ColorFeatures blue = single(24, 1);
        assertEquals(1.0, ColorScore.packScore(blue, red, blue, blue, 5, 0.5), EPS);
        assertEquals(1.0, ColorScore.packScore(blue, null, blue, red, 5, 0.5), EPS);
        assertEquals(1.0, ColorScore.packScore(null, blue, red, blue, 5, 0.5), EPS);
        assertEquals(ColorScore.score(blue, 5, 0.5), ColorScore.packScore(null, null, blue, blue, 5, 0.5), EPS);
        assertEquals(0.0, ColorScore.packScore(null, null, null, null, 5, 0.5), EPS);
    }

    @Test
    public void shownIndicesKeepsCloseOnes() {
        List<Double> scores = Arrays.asList(0.1, 0.5, 0.2, 0.05);
        assertEquals(Arrays.asList(1, 2), ColorScore.shownIndices(scores, 0.5));
    }

    @Test
    public void shownIndicesFallsBackToTopTen() {
        List<Double> scores = new ArrayList<Double>();
        for (int i = 0; i < 15; i++) scores.add(i / 100.0);
        assertEquals(Arrays.asList(5, 6, 7, 8, 9, 10, 11, 12, 13, 14), ColorScore.shownIndices(scores, 0.5));
        assertEquals(Arrays.asList(0, 1), ColorScore.shownIndices(Arrays.asList(0.0, 0.1), 0.5));
        assertTrue(ColorScore.shownIndices(new ArrayList<Double>(), 0.5).isEmpty());
    }
}
