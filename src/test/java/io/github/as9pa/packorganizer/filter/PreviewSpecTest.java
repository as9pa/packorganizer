package io.github.as9pa.packorganizer.filter;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import org.junit.Test;

public class PreviewSpecTest {
    private static BufferedImage img(int w, int h) {
        return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    }

    @Test
    public void listsSixteenTexturePathsWithTheHeartFromIcons() {
        assertEquals(16, PreviewSpec.CELLS);
        assertEquals(16, PreviewSpec.PATHS.size());
        assertEquals("assets/minecraft/textures/blocks/stone.png", PreviewSpec.PATHS.get(0));
        assertEquals("assets/minecraft/textures/blocks/wool_colored_white.png", PreviewSpec.PATHS.get(3));
        assertEquals("assets/minecraft/textures/items/bow_standby.png", PreviewSpec.PATHS.get(7));
        assertEquals("assets/minecraft/textures/items/potion_bottle_splash.png", PreviewSpec.PATHS.get(11));
        assertEquals(PreviewSpec.ICONS, PreviewSpec.PATHS.get(PreviewSpec.HEART));
        assertEquals("assets/minecraft/textures/gui/icons.png", PreviewSpec.ICONS);
        assertEquals("assets/minecraft/textures/items/snowball.png", PreviewSpec.PATHS.get(15));
    }

    @Test
    public void heartCropScalesWithIconsWidth() {
        assertArrayEquals(new int[] {52, 0, 9, 9}, PreviewSpec.heartCrop(256));
        assertArrayEquals(new int[] {104, 0, 18, 18}, PreviewSpec.heartCrop(512));
        assertArrayEquals(new int[] {26, 0, 4, 4}, PreviewSpec.heartCrop(128));
    }

    @Test
    public void cropsHeartOrNullWhenTooSmall() {
        BufferedImage heart = PreviewSpec.cropHeart(img(512, 512));
        assertEquals(18, heart.getWidth());
        assertEquals(18, heart.getHeight());
        assertNull(PreviewSpec.cropHeart(img(512, 10)));
        assertNull(PreviewSpec.cropHeart(img(16, 16)));
    }

    @Test
    public void animatedStripKeepsTheTopSquareFrame() {
        BufferedImage strip = img(16, 64);
        BufferedImage frame = PreviewSpec.firstFrame(strip);
        assertEquals(16, frame.getWidth());
        assertEquals(16, frame.getHeight());
        BufferedImage square = img(32, 32);
        assertSame(square, PreviewSpec.firstFrame(square));
    }

    @Test
    public void cellIsTheLargestSideCappedAt64() {
        assertEquals(32, PreviewSpec.cellSize(Arrays.asList(img(16, 16), null, img(32, 32))));
        assertEquals(64, PreviewSpec.cellSize(Arrays.asList(img(128, 128), img(16, 16))));
        assertEquals(PreviewSpec.DEFAULT_CELL, PreviewSpec.cellSize(Arrays.asList((BufferedImage) null)));
        assertEquals(PreviewSpec.DEFAULT_CELL, PreviewSpec.cellSize(Arrays.<BufferedImage>asList()));
    }

    @Test
    public void atlasIsTwoRowsOfEight() {
        assertEquals(8, PreviewSpec.COLUMNS);
        assertEquals(2, PreviewSpec.ROWS);
        assertEquals(256, PreviewSpec.atlasWidth(32));
        assertEquals(64, PreviewSpec.atlasHeight(32));
        assertEquals(0, PreviewSpec.cellX(0, 32));
        assertEquals(0, PreviewSpec.cellY(0, 32));
        assertEquals(224, PreviewSpec.cellX(7, 32));
        assertEquals(32, PreviewSpec.cellX(9, 32));
        assertEquals(32, PreviewSpec.cellY(9, 32));
    }
}
