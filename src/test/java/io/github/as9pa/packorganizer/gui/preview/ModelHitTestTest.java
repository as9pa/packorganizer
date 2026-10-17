package io.github.as9pa.packorganizer.gui.preview;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class ModelHitTestTest {
    /** The 8 corners of [x0,x1]x[y0,y1]x[z0,z1] in lastPartCorners order, rotated by yaw (about y) then roll (about z) and dropped onto x, y. */
    private static float[] box(float x0, float y0, float z0, float x1, float y1, float z1, double yawDeg, double rollDeg) {
        double cy = Math.cos(Math.toRadians(yawDeg)), sy = Math.sin(Math.toRadians(yawDeg));
        double cr = Math.cos(Math.toRadians(rollDeg)), sr = Math.sin(Math.toRadians(rollDeg));
        float[] out = new float[16];
        for (int i = 0; i < 8; i++) {
            int q = i & 3;
            double x = q == 1 || q == 2 ? x1 : x0;
            double y = q >= 2 ? y1 : y0;
            double z = i >= 4 ? z1 : z0;
            double rx = x * cy + z * sy;
            double rx2 = rx * cr - y * sr;
            double ry2 = rx * sr + y * cr;
            out[i * 2] = (float) rx2;
            out[i * 2 + 1] = (float) ry2;
        }
        return out;
    }

    private static float[] unitCube() {
        return box(0, 0, 0, 1, 1, 1, 0, 0);
    }

    @Test
    public void unitCubeHitsItsCentre() {
        assertTrue(ModelHitTest.hits(Collections.singletonList(unitCube()), 0.5F, 0.5F));
    }

    @Test
    public void unitCubeMissesOutside() {
        List<float[]> boxes = Collections.singletonList(unitCube());
        assertFalse(ModelHitTest.hits(boxes, 1.5F, 0.5F));
        assertFalse(ModelHitTest.hits(boxes, 0.5F, -0.2F));
        assertFalse(ModelHitTest.hits(boxes, -0.01F, 1.01F));
    }

    @Test
    public void rotatedBoxHitsInsideItsHull() {
        // Rolled 45 degrees the 2x2 face becomes a diamond reaching sqrt(2) along each axis.
        List<float[]> boxes = Collections.singletonList(box(-1, -1, -1, 1, 1, 1, 0, 45));
        assertTrue(ModelHitTest.hits(boxes, 0F, 0F));
        assertTrue(ModelHitTest.hits(boxes, 1.3F, 0F));
        assertTrue(ModelHitTest.hits(boxes, 0F, -1.3F));
        // Inside the unrotated square but outside the diamond.
        assertFalse(ModelHitTest.hits(boxes, 0.9F, 0.9F));
    }

    @Test
    public void boxTurnedToShowTwoFacesHitsAcrossBoth() {
        // Turned 45 degrees about y, a 1x1x1 cube spans x from -sqrt(2)/2 to sqrt(2)/2 with both side faces visible.
        List<float[]> boxes = Collections.singletonList(box(-0.5F, 0, -0.5F, 0.5F, 1, 0.5F, 45, 0));
        assertTrue(ModelHitTest.hits(boxes, 0.65F, 0.5F));
        assertTrue(ModelHitTest.hits(boxes, -0.65F, 0.5F));
        assertFalse(ModelHitTest.hits(boxes, 0.75F, 0.5F));
    }

    @Test
    public void anyBoxCounts() {
        List<float[]> boxes = Arrays.asList(unitCube(), box(5, 5, 0, 6, 6, 1, 0, 0));
        assertTrue(ModelHitTest.hits(boxes, 5.5F, 5.5F));
        assertFalse(ModelHitTest.hits(boxes, 3F, 3F));
    }

    @Test
    public void flatOrEmptyMisses() {
        assertFalse(ModelHitTest.hits(Collections.<float[]>emptyList(), 0F, 0F));
        // A zero-thickness box seen edge-on projects to a line and has no area to hit.
        assertFalse(ModelHitTest.hits(Collections.singletonList(box(0, 0, 0, 0, 1, 1, 0, 0)), 0F, 0.5F));
    }
}
