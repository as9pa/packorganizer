package io.github.as9pa.packorganizer.filter;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.awt.image.BufferedImage;
import org.junit.Test;

public class PotionIconTest {
    private static final int RED = 0xFFF82423;
    private static final int CLEAR = 0x00000000;

    @Test
    public void tintMultipliesEachChannelRoundedAndKeepsAlpha() {
        int[] argb = {0xFFFFFFFF, 0x80808080, 0xFF000000, 0x00FFFFFF};
        PotionIcon.tint(argb, PotionIcon.HEALING);
        // 128 * 0xF8 / 255 = 124.49 -> 0x7C; 128 * 0x24 / 255 = 18.07 -> 0x12; 128 * 0x23 / 255 = 17.57 -> 0x12.
        assertArrayEquals(new int[] {RED, 0x807C1212, 0xFF000000, 0x00F82423}, argb);
    }

    @Test
    public void bottleIsDrawnOverTheTintedOverlay() {
        BufferedImage overlay = img(3, 1, 0xFFFFFFFF, 0xFFFFFFFF, 0xFF000000);
        BufferedImage bottle = img(3, 1, 0xFF112233, CLEAR, 0x80FFFFFF);
        BufferedImage out = PotionIcon.compose(bottle, overlay, PotionIcon.HEALING);
        // Opaque bottle pixels win untinted, clear ones show the red liquid, half-alpha ones blend.
        assertArrayEquals(new int[] {0xFF112233, RED, 0xFF808080}, pixels(out));
    }

    @Test
    public void overUsesSourceOverAlpha() {
        assertEquals(0xFF123456, PotionIcon.over(0xFF123456, 0xFF000000));
        assertEquals(0x80ABCDEF, PotionIcon.over(CLEAR, 0x80ABCDEF));
        assertEquals(0x80FFFFFF, PotionIcon.over(0x80FFFFFF, CLEAR));
        // 0.5 over 0.5: alpha 0.75, color weighted 2:1 toward the source.
        assertEquals(0xC0AA0055, PotionIcon.over(0x80FF0000, 0x800000FF));
    }

    @Test
    public void smallerLayerIsScaledUpWithNearestNeighbour() {
        BufferedImage overlay = img(2, 2, 0xFFFFFFFF, CLEAR, CLEAR, CLEAR);
        BufferedImage bottle = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        bottle.setRGB(3, 3, 0xFF0000FF);
        BufferedImage out = PotionIcon.compose(bottle, overlay, PotionIcon.HEALING);
        assertEquals(4, out.getWidth());
        assertEquals(4, out.getHeight());
        assertArrayEquals(new int[] {
                RED, RED, CLEAR, CLEAR,
                RED, RED, CLEAR, CLEAR,
                CLEAR, CLEAR, CLEAR, CLEAR,
                CLEAR, CLEAR, CLEAR, 0xFF0000FF}, pixels(out));

        BufferedImage small = img(1, 1, 0xFF00FF00);
        BufferedImage big = PotionIcon.compose(small, img(2, 2, CLEAR, CLEAR, CLEAR, CLEAR), PotionIcon.HEALING);
        assertEquals(2, big.getWidth());
        assertArrayEquals(new int[] {0xFF00FF00, 0xFF00FF00, 0xFF00FF00, 0xFF00FF00}, pixels(big));
    }

    @Test
    public void eachLayerComesFromThePackElseVanilla() {
        BufferedImage packBottle = img(2, 1, 0xFF0000AA, CLEAR);
        BufferedImage vanillaBottle = img(2, 1, 0xFF00AA00, CLEAR);
        BufferedImage packOverlay = img(2, 1, 0xFFFFFFFF, 0xFFFFFFFF);
        BufferedImage vanillaOverlay = img(2, 1, 0xFF000000, 0xFF000000);

        assertArrayEquals(new int[] {0xFF0000AA, RED},
                pixels(PotionIcon.healing(packBottle, packOverlay, vanillaBottle, vanillaOverlay)));
        assertArrayEquals(new int[] {0xFF0000AA, 0xFF000000},
                pixels(PotionIcon.healing(packBottle, null, vanillaBottle, vanillaOverlay)));
        assertArrayEquals(new int[] {0xFF00AA00, RED},
                pixels(PotionIcon.healing(null, packOverlay, vanillaBottle, vanillaOverlay)));
        assertArrayEquals(new int[] {0xFF00AA00, 0xFF000000},
                pixels(PotionIcon.healing(null, null, vanillaBottle, vanillaOverlay)));
    }

    @Test
    public void missingLayersAreLeftOut() {
        assertNull(PotionIcon.healing(null, null, null, null));
        assertArrayEquals(new int[] {0xFF00AA00, CLEAR},
                pixels(PotionIcon.healing(null, null, img(2, 1, 0xFF00AA00, CLEAR), null)));
        assertArrayEquals(new int[] {RED, CLEAR},
                pixels(PotionIcon.healing(null, null, null, img(2, 1, 0xFFFFFFFF, CLEAR))));
    }

    @Test
    public void composeReadsSubimagesInTheirOwnCoordinates() {
        BufferedImage strip = img(1, 2, 0xFF00AA00, 0xFFAA0000);
        BufferedImage out = PotionIcon.compose(PreviewSpec.firstFrame(strip), null, PotionIcon.HEALING);
        assertArrayEquals(new int[] {0xFF00AA00}, pixels(out));
    }

    private static BufferedImage img(int w, int h, int... argb) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, w, h, argb, 0, w);
        return img;
    }

    private static int[] pixels(BufferedImage img) {
        return img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
    }
}
