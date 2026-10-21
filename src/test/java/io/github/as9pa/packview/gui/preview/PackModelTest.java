package io.github.as9pa.packview.gui.preview;

import static org.junit.Assert.*;

import io.github.as9pa.packview.filter.ModelSpec;
import io.github.as9pa.packview.filter.SkyLayer;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class PackModelTest {
    private static final int OPAQUE = 0xFF336699;

    private static int[] texture(int w, int h, int... opaqueXY) {
        int[] argb = new int[w * h];
        for (int k = 0; k < opaqueXY.length; k += 2) {
            argb[opaqueXY[k + 1] * w + opaqueXY[k]] = OPAQUE;
        }
        return argb;
    }

    private static BufferedImage image(int w, int h, int... opaqueXY) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, w, h, texture(w, h, opaqueXY), 0, w);
        return img;
    }

    private static BufferedImage filled(int argb) {
        BufferedImage img = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 2; x++) {
                img.setRGB(x, y, argb);
            }
        }
        return img;
    }

    private static PackModel model(Map<String, BufferedImage> images, PackModel fallback) {
        return new PackModel(images, Collections.<SkyLayer>emptyList(), new BufferedImage[0], fallback);
    }

    @Test
    public void tintMultipliesColorAndKeepsAlpha() {
        int[] px = {0xFFFFFFFF, 0x80000000, 0x40808080};
        PackModel.tint(px, 0xA06540);
        assertEquals(0xFFA06540, px[0]);
        assertEquals(0x80000000, px[1]);
        // 0x80 * 0xA0 / 255 = 80.3, 0x80 * 0x65 / 255 = 50.7, 0x80 * 0x40 / 255 = 32.1
        assertEquals(0x40503320, px[2]);
    }

    @Test
    public void opaqueBoundsTrimToDrawnPixels() {
        assertArrayEquals(new int[] {3, 2, 1, 1}, PackModel.opaqueBounds(texture(16, 16, 3, 2), 16, 16));
        assertArrayEquals(new int[] {1, 4, 10, 7},
                PackModel.opaqueBounds(texture(16, 16, 1, 10, 10, 4, 5, 5), 16, 16));
        assertArrayEquals(new int[] {0, 0, 16, 16}, PackModel.opaqueBounds(texture(16, 16, 0, 0, 15, 15), 16, 16));
    }

    @Test
    public void opaqueBoundsOfTransparentImageAreNull() {
        assertNull(PackModel.opaqueBounds(new int[16 * 16], 16, 16));
    }

    @Test
    public void unionCoversBothBoxes() {
        assertArrayEquals(new int[] {1, 2, 9, 8}, PackModel.union(new int[] {1, 4, 3, 3}, new int[] {5, 2, 5, 8}));
        assertArrayEquals(new int[] {1, 4, 3, 3}, PackModel.union(new int[] {1, 4, 3, 3}, null));
        assertArrayEquals(new int[] {5, 2, 5, 8}, PackModel.union(null, new int[] {5, 2, 5, 8}));
        assertNull(PackModel.union(null, null));
    }

    @Test
    public void leatherIsTintedAndOverlaysAreNot() {
        Map<String, BufferedImage> images = new HashMap<>();
        images.put(ModelSpec.armor("leather", 1), filled(0xFFFFFFFF));
        images.put(ModelSpec.armor("leather", 2), filled(0xFFFFFFFF));
        images.put(ModelSpec.helmet("leather"), filled(0xFFFFFFFF));
        images.put(ModelSpec.armorOverlay(1), filled(0xFFFFFFFF));
        images.put(ModelSpec.LEATHER_HELMET_OVERLAY, filled(0xFFFFFFFF));
        images.put(ModelSpec.armor("iron", 1), filled(0xFFFFFFFF));
        PackModel model = model(images, null);
        assertEquals(0xFFA06540, model.image(ModelSpec.armor("leather", 1)).getRGB(1, 1));
        assertEquals(0xFFA06540, model.image(ModelSpec.armor("leather", 2)).getRGB(0, 0));
        assertEquals(0xFFA06540, model.image(ModelSpec.helmet("leather")).getRGB(0, 1));
        assertEquals(0xFFFFFFFF, model.image(ModelSpec.armorOverlay(1)).getRGB(0, 0));
        assertEquals(0xFFFFFFFF, model.image(ModelSpec.LEATHER_HELMET_OVERLAY).getRGB(0, 0));
        assertEquals(0xFFFFFFFF, model.image(ModelSpec.armor("iron", 1)).getRGB(0, 0));
    }

    @Test
    public void missingImagesComeFromTheFallback() {
        Map<String, BufferedImage> vanilla = new HashMap<>();
        BufferedImage vanillaSword = image(16, 16, 4, 4);
        vanilla.put(ModelSpec.sword("iron"), vanillaSword);
        vanilla.put(ModelSpec.BOW, image(16, 16, 1, 1));
        Map<String, BufferedImage> own = new HashMap<>();
        BufferedImage ownBow = image(32, 32, 2, 2);
        own.put(ModelSpec.BOW, ownBow);
        PackModel model = model(own, model(vanilla, null));
        assertSame(ownBow, model.image(ModelSpec.BOW));
        assertSame(vanillaSword, model.image(ModelSpec.sword("iron")));
        assertNull(model.image(ModelSpec.GLINT));
        assertTrue(model.sky().isEmpty());
    }

    @Test
    public void helmetIconBoundsTrimUnionOverlayAndFallBack() {
        Map<String, BufferedImage> vanilla = new HashMap<>();
        vanilla.put(ModelSpec.helmet("iron"), image(16, 16, 2, 3, 12, 9));
        vanilla.put(ModelSpec.LEATHER_HELMET_OVERLAY, image(16, 16, 14, 1));
        Map<String, BufferedImage> own = new HashMap<>();
        own.put(ModelSpec.helmet("leather"), image(16, 16, 3, 4, 9, 11));
        own.put(ModelSpec.helmet("gold"), image(32, 32));
        PackModel model = model(own, model(vanilla, null));
        // Own leather helmet, unioned with the vanilla overlay of the same size.
        assertArrayEquals(new int[] {3, 1, 12, 11}, model.helmetIconBounds("leather"));
        // Iron falls back to vanilla's icon.
        assertArrayEquals(new int[] {2, 3, 11, 7}, model.helmetIconBounds("iron"));
        // Nothing drawn: the whole image.
        assertArrayEquals(new int[] {0, 0, 32, 32}, model.helmetIconBounds("gold"));
        // No icon anywhere.
        assertArrayEquals(new int[4], model.helmetIconBounds("diamond"));
    }
}
