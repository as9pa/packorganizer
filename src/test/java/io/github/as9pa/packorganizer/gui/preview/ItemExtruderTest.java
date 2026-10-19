package io.github.as9pa.packorganizer.gui.preview;

import static org.junit.Assert.*;

import org.junit.Test;

public class ItemExtruderTest {
    private static final int OPAQUE = 0xFF336699;
    private static final float EPS = 1e-6F;

    private static float at(ItemMesh mesh, int quad, int vertex, int field) {
        return mesh.data[(quad * 4 + vertex) * ItemMesh.FLOATS_PER_VERTEX + field];
    }

    private static boolean isSide(ItemMesh mesh, int quad) {
        return at(mesh, quad, 0, 7) == 0;
    }

    private static int[] texture(int w, int h, int... opaqueXY) {
        int[] argb = new int[w * h];
        for (int k = 0; k < opaqueXY.length; k += 2) {
            argb[opaqueXY[k + 1] * w + opaqueXY[k]] = OPAQUE;
        }
        return argb;
    }

    @Test
    public void transparentTextureGivesOnlyFrontAndBack() {
        ItemMesh mesh = ItemExtruder.build(new int[16 * 16], 16, 16);
        assertEquals(2, mesh.quads);
        assertEquals(2 * 4 * ItemMesh.FLOATS_PER_VERTEX, mesh.data.length);
    }

    @Test
    public void singleOpaquePixelGivesFourSides() {
        ItemMesh mesh = ItemExtruder.build(texture(16, 16, 5, 7), 16, 16);
        assertEquals(2 + 4, mesh.quads);
    }

    @Test
    public void twoByOneBarGivesSixSides() {
        ItemMesh mesh = ItemExtruder.build(texture(16, 16, 3, 4, 4, 4), 16, 16);
        assertEquals(2 + 6, mesh.quads);
    }

    @Test
    public void anyAlphaAboveZeroCountsAsOpaque() {
        int[] argb = new int[16 * 16];
        argb[2 * 16 + 2] = 0x01FFFFFF;
        assertEquals(2 + 4, ItemExtruder.build(argb, 16, 16).quads);
    }

    @Test
    public void textureBordersCountAsTransparent() {
        ItemMesh mesh = ItemExtruder.build(texture(2, 1, 0, 0, 1, 0), 2, 1);
        assertEquals(2 + 6, mesh.quads);
    }

    @Test
    public void singlePixelSideUvsStayInsideThatTexel() {
        ItemMesh mesh = ItemExtruder.build(texture(16, 16, 5, 7), 16, 16);
        int sides = 0;
        for (int q = 0; q < mesh.quads; q++) {
            if (!isSide(mesh, q)) continue;
            sides++;
            for (int v = 0; v < 4; v++) {
                float u = at(mesh, q, v, 3);
                float tv = at(mesh, q, v, 4);
                assertTrue("u " + u, u >= 5 / 16F - EPS && u <= 6 / 16F + EPS);
                assertTrue("v " + tv, tv >= 7 / 16F - EPS && tv <= 8 / 16F + EPS);
            }
        }
        assertEquals(4, sides);
    }

    @Test
    public void singlePixelSidesSitOnThatTexelsEdges() {
        ItemMesh mesh = ItemExtruder.build(texture(16, 16, 5, 7), 16, 16);
        // Image row 7 from the top is model y 8/16..9/16 from the bottom.
        for (int q = 0; q < mesh.quads; q++) {
            if (!isSide(mesh, q)) continue;
            for (int v = 0; v < 4; v++) {
                float x = at(mesh, q, v, 0);
                float y = at(mesh, q, v, 1);
                assertTrue("x " + x, x >= 5 / 16F - EPS && x <= 6 / 16F + EPS);
                assertTrue("y " + y, y >= 8 / 16F - EPS && y <= 9 / 16F + EPS);
            }
            float nx = at(mesh, q, 0, 5);
            float ny = at(mesh, q, 0, 6);
            assertEquals(1, Math.abs(nx) + Math.abs(ny), EPS);
        }
    }

    @Test
    public void frontAndBackCoverTheWholeTexture() {
        ItemMesh mesh = ItemExtruder.build(texture(16, 16, 5, 7), 16, 16);
        float frontZ = 0;
        float backZ = 0;
        for (int q = 0; q < mesh.quads; q++) {
            if (isSide(mesh, q)) continue;
            float minX = 1, maxX = 0, minY = 1, maxY = 0, minU = 1, maxU = 0, minV = 1, maxV = 0;
            for (int v = 0; v < 4; v++) {
                minX = Math.min(minX, at(mesh, q, v, 0));
                maxX = Math.max(maxX, at(mesh, q, v, 0));
                minY = Math.min(minY, at(mesh, q, v, 1));
                maxY = Math.max(maxY, at(mesh, q, v, 1));
                minU = Math.min(minU, at(mesh, q, v, 3));
                maxU = Math.max(maxU, at(mesh, q, v, 3));
                minV = Math.min(minV, at(mesh, q, v, 4));
                maxV = Math.max(maxV, at(mesh, q, v, 4));
            }
            assertEquals(0, minX, EPS);
            assertEquals(1, maxX, EPS);
            assertEquals(0, minY, EPS);
            assertEquals(1, maxY, EPS);
            assertEquals(0, minU, EPS);
            assertEquals(1, maxU, EPS);
            assertEquals(0, minV, EPS);
            assertEquals(1, maxV, EPS);
            if (at(mesh, q, 0, 7) > 0) frontZ = at(mesh, q, 0, 2);
            else backZ = at(mesh, q, 0, 2);
        }
        assertEquals(8.5F / 16, frontZ, EPS);
        assertEquals(7.5F / 16, backZ, EPS);
    }

    @Test
    public void totalThicknessIsOneSixteenth() {
        ItemMesh mesh = ItemExtruder.build(texture(16, 16, 5, 7, 6, 7, 6, 8), 16, 16);
        float minZ = 1;
        float maxZ = 0;
        for (int q = 0; q < mesh.quads; q++) {
            for (int v = 0; v < 4; v++) {
                minZ = Math.min(minZ, at(mesh, q, v, 2));
                maxZ = Math.max(maxZ, at(mesh, q, v, 2));
            }
        }
        assertEquals(7.5F / 16, minZ, EPS);
        assertEquals(1 / 16F, maxZ - minZ, EPS);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shortPixelArrayIsRejected() {
        ItemExtruder.build(new int[15], 4, 4);
    }
}
