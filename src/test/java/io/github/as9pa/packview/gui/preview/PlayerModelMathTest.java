package io.github.as9pa.packview.gui.preview;

import static org.junit.Assert.*;

import org.junit.Test;

public class PlayerModelMathTest {
    private static final float EPS = 1e-5F;

    /** Column-major translate(tx, ty, tz) * rotate(deg about z) * scale(s, -s, s). */
    private static float[] trs(float tx, float ty, float tz, float deg, float s) {
        float c = (float) Math.cos(Math.toRadians(deg));
        float n = (float) Math.sin(Math.toRadians(deg));
        float[] t = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, tx, ty, tz, 1};
        float[] r = {c, n, 0, 0, -n, c, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
        float[] sc = {s, 0, 0, 0, 0, -s, 0, 0, 0, 0, s, 0, 0, 0, 0, 1};
        return PlayerModelRenderer.multiply(PlayerModelRenderer.multiply(t, r), sc);
    }

    @Test
    public void inverseUndoesTheMatrix() {
        float[] m = trs(120, 80, 50, 30, 40);
        float[] id = PlayerModelRenderer.multiply(PlayerModelRenderer.invertAffine(m), m);
        for (int i = 0; i < 16; i++) {
            assertEquals("element " + i, i % 5 == 0 ? 1F : 0F, id[i], EPS);
        }
    }

    @Test
    public void transformAppliesScaleThenRotationThenTranslation() {
        float[] m = trs(10, 20, 30, 90, 2);
        float[] out = new float[3];
        PlayerModelRenderer.transformPoint(m, 1, 1, 1, out, 0);
        // scale (2, -2, 2), rotate 90 about z: (x, y) -> (-y, x) gives (2, 2), then translate.
        assertEquals(12F, out[0], EPS);
        assertEquals(22F, out[1], EPS);
        assertEquals(32F, out[2], EPS);
    }

    @Test
    public void worldTransformMapsFeetAndUp() {
        // applyWorldTransform: GUI (centerX, feetY) is the origin and one block up is blockPx GUI px higher.
        float[] world = trs(100, 150, 50, 0, 40);
        float[] out = new float[3];
        PlayerModelRenderer.transformPoint(world, 0, 1.8F, 0, out, 0);
        assertEquals(100F, out[0], EPS);
        assertEquals(150F - 1.8F * 40, out[1], 1e-4F);
    }

    @Test
    public void boxCornersComeInTheDocumentedOrder() {
        float[] id = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
        float[] c = PlayerModelRenderer.boxCorners(id, -1, -2, -3, 4, 5, 6);
        assertEquals(16, c.length);
        float[] expected = {-1, -2, 4, -2, 4, 5, -1, 5, -1, -2, 4, -2, 4, 5, -1, 5};
        assertArrayEquals(expected, c, EPS);
    }

    @Test
    public void boxCornersFollowTheDepthThroughRotation() {
        // Rotating 90 degrees about y sends z to x, so the z0 and z1 faces land at different screen x.
        float[] rotY = {0, 0, -1, 0, 0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 1};
        float[] c = PlayerModelRenderer.boxCorners(rotY, 0, 0, 1, 1, 1, 2);
        assertEquals(1F, c[0], EPS);
        assertEquals(2F, c[8], EPS);
    }

    @Test
    public void displaysAreScaledToBlocksAndClamped() {
        ItemDisplays.Display sword = ItemDisplays.HANDHELD;
        assertEquals(0F, sword.x, EPS);
        assertEquals(1.25F / 16F, sword.y, EPS);
        assertEquals(-3.5F / 16F, sword.z, EPS);
        assertEquals(90F, sword.rotY, EPS);
        assertEquals(-35F, sword.rotZ, EPS);
        assertEquals(0.85F, sword.scaleX, EPS);

        ItemDisplays.Display big = ItemDisplays.Display.of(
                new float[] {0, 0, 0}, new float[] {40, -40, 8}, new float[] {9, -9, 2});
        assertEquals(1.5F, big.x, EPS);
        assertEquals(-1.5F, big.y, EPS);
        assertEquals(0.5F, big.z, EPS);
        assertEquals(4F, big.scaleX, EPS);
        assertEquals(-4F, big.scaleY, EPS);
        assertEquals(2F, big.scaleZ, EPS);
    }

    @Test
    public void eachItemGetsItsModelsDisplay() {
        assertSame(ItemDisplays.HANDHELD, ItemDisplays.of(HeldItem.SWORD));
        assertSame(ItemDisplays.BOW, ItemDisplays.of(HeldItem.BOW));
        assertSame(ItemDisplays.FISHING_ROD, ItemDisplays.of(HeldItem.FISHING_ROD));
        assertSame(ItemDisplays.GENERATED, ItemDisplays.of(HeldItem.ENDER_PEARL));
        assertSame(ItemDisplays.GENERATED, ItemDisplays.of(HeldItem.GOLDEN_APPLE));
        assertSame(ItemDisplays.GENERATED, ItemDisplays.of(HeldItem.SPLASH_POTION));
        assertEquals(0.75F / 16F, ItemDisplays.BOW.x, EPS);
        assertEquals(180F, ItemDisplays.FISHING_ROD.rotX, EPS);
        assertEquals(-90F, ItemDisplays.GENERATED.rotX, EPS);
        assertEquals(0.55F, ItemDisplays.GENERATED.scaleZ, EPS);
    }
}
