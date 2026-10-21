package io.github.as9pa.packview.gui.preview;

import static org.junit.Assert.*;

import java.awt.image.BufferedImage;
import org.junit.Test;

public class CapeImagesTest {
    private static final int RED = 0xFFFF0000;

    private static BufferedImage filled(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setRGB(x, y, RED);
            }
        }
        return img;
    }

    @Test
    public void doubleOptifineCapeGoesTopLeftOnDoubleCanvas() {
        BufferedImage out = CapeImages.optifineCanvas(filled(92, 44));
        assertEquals(128, out.getWidth());
        assertEquals(64, out.getHeight());
        assertEquals(RED, out.getRGB(0, 0));
        assertEquals(RED, out.getRGB(91, 43));
        assertEquals(0, out.getRGB(92, 0) >>> 24);
        assertEquals(0, out.getRGB(0, 44) >>> 24);
        assertEquals(0, out.getRGB(127, 63) >>> 24);
    }

    @Test
    public void optifineCapeGoesOnCapeCanvas() {
        BufferedImage out = CapeImages.optifineCanvas(filled(46, 22));
        assertEquals(64, out.getWidth());
        assertEquals(32, out.getHeight());
        assertEquals(RED, out.getRGB(45, 21));
        assertEquals(0, out.getRGB(46, 21) >>> 24);
    }

    @Test
    public void vanillaSizedCapeIsUnchanged() {
        BufferedImage src = filled(64, 32);
        assertSame(src, CapeImages.optifineCanvas(src));
    }

    @Test
    public void mismatchedMultiplesAreUnchanged() {
        BufferedImage src = filled(92, 22);
        assertSame(src, CapeImages.optifineCanvas(src));
    }

    @Test
    public void slimModelMetadataIsSlim() {
        assertTrue(CapeImages.isSlim("{\"timestamp\":1,\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/"
                + "texture/abc\",\"metadata\":{\"model\":\"slim\"}}}}"));
    }

    @Test
    public void skinWithoutMetadataIsNotSlim() {
        assertFalse(CapeImages.isSlim("{\"timestamp\":1,\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/"
                + "texture/abc\"}}}"));
    }

    @Test
    public void missingOrBrokenJsonIsNotSlim() {
        assertFalse(CapeImages.isSlim(null));
        assertFalse(CapeImages.isSlim(""));
        assertFalse(CapeImages.isSlim("{not json"));
        assertFalse(CapeImages.isSlim("{\"textures\":{}}"));
    }
}
