package io.github.as9pa.packorganizer.gui.preview;

import java.util.List;

/**
 * Whether a GUI point lands on the drawn model: inside the convex hull of any box's 8 projected corners, as
 * {@link PlayerModelRenderer#lastPartCorners()} gives them. A box seen edge-on has no area and is never hit.
 */
public final class ModelHitTest {
    private ModelHitTest() {
    }

    /** @param boxCorners 16 floats per box, the x, y pairs of its 8 corners in any order */
    public static boolean hits(List<float[]> boxCorners, float x, float y) {
        float[] hull = new float[18];
        for (float[] corners : boxCorners) {
            if (corners != null && corners.length >= 16 && inHull(corners, hull, x, y)) {
                return true;
            }
        }
        return false;
    }

    /** Andrew's monotone chain over the 8 corners into {@code hull}, then the point against every edge. */
    private static boolean inHull(float[] corners, float[] hull, float x, float y) {
        float[] p = sortedPoints(corners);
        int n = 0;
        for (int i = 0; i < 8; i++) {   // lower chain
            while (n >= 2 && cross(hull, n - 2, n - 1, p[i * 2], p[i * 2 + 1]) <= 0) {
                n--;
            }
            hull[n * 2] = p[i * 2];
            hull[n * 2 + 1] = p[i * 2 + 1];
            n++;
        }
        int lower = n + 1;
        for (int i = 6; i >= 0; i--) {   // upper chain
            while (n >= lower && cross(hull, n - 2, n - 1, p[i * 2], p[i * 2 + 1]) <= 0) {
                n--;
            }
            hull[n * 2] = p[i * 2];
            hull[n * 2 + 1] = p[i * 2 + 1];
            n++;
        }
        n--;   // the last point repeats the first
        if (n < 3) {
            return false;
        }
        for (int i = 0; i < n; i++) {
            if (cross(hull, i, (i + 1) % n, x, y) < 0) {
                return false;
            }
        }
        return true;
    }

    /** The 8 points sorted by x, then y. */
    private static float[] sortedPoints(float[] corners) {
        float[] p = new float[16];
        System.arraycopy(corners, 0, p, 0, 16);
        for (int i = 1; i < 8; i++) {
            float px = p[i * 2];
            float py = p[i * 2 + 1];
            int j = i - 1;
            while (j >= 0 && (p[j * 2] > px || (p[j * 2] == px && p[j * 2 + 1] > py))) {
                p[(j + 1) * 2] = p[j * 2];
                p[(j + 1) * 2 + 1] = p[j * 2 + 1];
                j--;
            }
            p[(j + 1) * 2] = px;
            p[(j + 1) * 2 + 1] = py;
        }
        return p;
    }

    /** Cross product of (b - a) and (point - a); positive when the point is left of a to b. */
    private static float cross(float[] hull, int a, int b, float x, float y) {
        float ax = hull[a * 2];
        float ay = hull[a * 2 + 1];
        return (hull[b * 2] - ax) * (y - ay) - (hull[b * 2 + 1] - ay) * (x - ax);
    }
}
