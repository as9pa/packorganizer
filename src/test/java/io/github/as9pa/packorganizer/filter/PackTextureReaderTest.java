package io.github.as9pa.packorganizer.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PackTextureReaderTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static final String STONE = "assets/minecraft/textures/blocks/stone.png";
    private static final String POTION = "assets/minecraft/textures/items/potion_bottle_splash.png";

    @Test
    public void readsZipTexturesCropsHeartAndFirstFrame() throws Exception {
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put("pack.mcmeta", TestPacks.MCMETA);
        e.put(STONE, TestPacks.png(32, 32));
        e.put(POTION, TestPacks.png(16, 64));
        e.put(PreviewSpec.ICONS, TestPacks.png(512, 512));
        File pack = TestPacks.zip(new File(tmp.getRoot(), "p.zip"), e);

        BufferedImage[] got = PackTextureReader.read(pack);
        assertEquals(PreviewSpec.CELLS, got.length);
        assertEquals(32, got[0].getWidth());
        assertEquals(16, got[11].getWidth());
        assertEquals(16, got[11].getHeight());
        assertEquals(18, got[PreviewSpec.HEART].getWidth());
        assertNull(got[1]);
        assertNull(got[15]);
    }

    @Test
    public void readsFolderPacks() throws Exception {
        File dir = tmp.newFolder("folder");
        TestPacks.write(new File(dir, "pack.mcmeta"), TestPacks.MCMETA);
        TestPacks.write(new File(dir, STONE), TestPacks.png(64, 64));
        TestPacks.write(new File(dir, PreviewSpec.ICONS), TestPacks.png(256, 256));

        BufferedImage[] got = PackTextureReader.read(dir);
        assertEquals(64, got[0].getWidth());
        assertEquals(9, got[PreviewSpec.HEART].getWidth());
        assertNull(got[2]);
    }

    @Test
    public void corruptOrMissingFilesYieldEmptyCells() throws Exception {
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put(STONE, "not a png".getBytes());
        File pack = TestPacks.zip(new File(tmp.getRoot(), "bad.zip"), e);
        BufferedImage[] got = PackTextureReader.read(pack);
        assertEquals(PreviewSpec.CELLS, got.length);
        assertNull(got[0]);

        File notZip = new File(tmp.getRoot(), "junk.zip");
        TestPacks.write(notZip, "junk".getBytes());
        BufferedImage[] none = PackTextureReader.read(notZip);
        assertEquals(PreviewSpec.CELLS, none.length);
        for (BufferedImage b : none) {
            assertNull(b);
        }
        assertNotNull(PackTextureReader.read(new File(tmp.getRoot(), "absent.zip")));
    }

    @Test
    public void readLayersAddsThePotionOverlayAfterTheCells() throws Exception {
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put(POTION, TestPacks.png(32, 32));
        e.put(PreviewSpec.POTION_OVERLAY, TestPacks.png(16, 48));
        File pack = TestPacks.zip(new File(tmp.getRoot(), "potion.zip"), e);

        BufferedImage[] layers = PackTextureReader.readLayers(pack);
        assertEquals(PreviewSpec.CELLS + 1, layers.length);
        assertEquals(32, layers[PreviewSpec.POTION].getWidth());
        assertEquals(16, layers[PreviewSpec.OVERLAY].getWidth());
        assertEquals(16, layers[PreviewSpec.OVERLAY].getHeight());
        assertEquals(PreviewSpec.CELLS, PackTextureReader.read(pack).length);

        File dir = tmp.newFolder("bottle-only");
        TestPacks.write(new File(dir, POTION), TestPacks.png(16, 16));
        assertNull(PackTextureReader.readLayers(dir)[PreviewSpec.OVERLAY]);
    }

    @Test
    public void subsamplingKeepsAtLeastTheCellAndNeverUpscales() {
        assertEquals(1, PackTextureReader.subsampling(0, PreviewSpec.MAX_CELL));
        assertEquals(1, PackTextureReader.subsampling(16, PreviewSpec.MAX_CELL));
        assertEquals(1, PackTextureReader.subsampling(64, PreviewSpec.MAX_CELL));
        assertEquals(1, PackTextureReader.subsampling(127, PreviewSpec.MAX_CELL));
        assertEquals(2, PackTextureReader.subsampling(128, PreviewSpec.MAX_CELL));
        assertEquals(8, PackTextureReader.subsampling(1000, PreviewSpec.MAX_CELL));
        assertEquals(64, PackTextureReader.subsampling(4096, PreviewSpec.MAX_CELL));
        assertEquals(1, PackTextureReader.subsampling(4096, 0));
    }

    private static final int RED = 0xFFFF0000;
    private static final int BLUE = 0xFF0000FF;
    private static final int GREEN = 0xFF00FF00;

    @Test
    public void largeTexturesDecodeSubsampledFromWidthAndKeepTheirPixels() throws Exception {
        BufferedImage strip = new BufferedImage(1024, 2048, BufferedImage.TYPE_INT_ARGB);
        fill(strip, 0, 0, 16, 16, RED);
        fill(strip, 512, 512, 16, 16, BLUE);
        fill(strip, 0, 1024, 1024, 1024, GREEN);
        // the heart sits at (52, 0, 9, 9) of 256, so (832, 0, 144, 144) here
        BufferedImage icons = new BufferedImage(4096, 256, BufferedImage.TYPE_INT_ARGB);
        fill(icons, 832, 0, 2, 2, RED);
        fill(icons, 974, 142, 2, 2, BLUE);
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put(STONE, png(strip));
        e.put(POTION, TestPacks.png(64, 1024));
        e.put(PreviewSpec.ICONS, png(icons));
        File pack = TestPacks.zip(new File(tmp.getRoot(), "big.zip"), e);

        BufferedImage[] got = PackTextureReader.read(pack);
        BufferedImage stone = got[0];
        assertEquals(64, stone.getWidth());
        assertEquals(64, stone.getHeight());
        assertEquals(RED, stone.getRGB(0, 0));
        assertEquals(BLUE, stone.getRGB(32, 32));
        assertEquals(0, stone.getRGB(0, 63));
        assertEquals(64, got[PreviewSpec.POTION].getWidth());
        assertEquals(64, got[PreviewSpec.POTION].getHeight());
        BufferedImage heart = got[PreviewSpec.HEART];
        assertEquals(72, heart.getWidth());
        assertEquals(72, heart.getHeight());
        assertEquals(RED, heart.getRGB(0, 0));
        assertEquals(BLUE, heart.getRGB(71, 71));

        String rel = "blocks/stone.png";
        BufferedImage full = PackTextureReader.readPaths(pack, Arrays.asList(rel)).get(rel);
        assertEquals(1024, full.getWidth());
        assertEquals(2048, full.getHeight());
    }

    private static void fill(BufferedImage img, int x, int y, int w, int h, int argb) {
        for (int py = y; py < y + h; py++) {
            for (int px = x; px < x + w; px++) {
                img.setRGB(px, py, argb);
            }
        }
    }

    private static byte[] png(BufferedImage img) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static final String ARMOR = "models/armor/diamond_layer_1.png";
    private static final String PULL = "items/bow_pulling_0.png";
    private static final String GLINT = "misc/enchanted_item_glint.png";
    private static final List<String> MODEL = Arrays.asList(ARMOR, PULL, GLINT, "items/bow_standby.png");

    @Test
    public void readPathsFromZipCropsOnlyItemStrips() throws Exception {
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put("pack.mcmeta", TestPacks.MCMETA);
        e.put(ModelSpec.ROOT + ARMOR, TestPacks.png(64, 32));
        e.put(ModelSpec.ROOT + PULL, TestPacks.png(16, 48));
        e.put(ModelSpec.ROOT + GLINT, TestPacks.png(16, 32));
        File pack = TestPacks.zip(new File(tmp.getRoot(), "m.zip"), e);

        assertModel(PackTextureReader.readPaths(pack, MODEL));
    }

    @Test
    public void readPathsFromFolder() throws Exception {
        File dir = tmp.newFolder("model");
        TestPacks.write(new File(dir, "pack.mcmeta"), TestPacks.MCMETA);
        TestPacks.write(new File(dir, ModelSpec.ROOT + ARMOR), TestPacks.png(64, 32));
        TestPacks.write(new File(dir, ModelSpec.ROOT + PULL), TestPacks.png(16, 48));
        TestPacks.write(new File(dir, ModelSpec.ROOT + GLINT), TestPacks.png(16, 32));

        assertModel(PackTextureReader.readPaths(dir, MODEL));
    }

    private static void assertModel(Map<String, BufferedImage> got) {
        assertEquals(3, got.size());
        assertEquals(64, got.get(ARMOR).getWidth());
        assertEquals(32, got.get(ARMOR).getHeight());
        assertEquals(16, got.get(PULL).getWidth());
        assertEquals(16, got.get(PULL).getHeight());
        assertEquals(32, got.get(GLINT).getHeight());
        assertFalse(got.containsKey("items/bow_standby.png"));
    }

    @Test
    public void readPathsSkipsCorruptAndUnreadablePacks() throws Exception {
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put(ModelSpec.ROOT + ARMOR, "not a png".getBytes());
        e.put(ModelSpec.ROOT + PULL, TestPacks.png(16, 16));
        File pack = TestPacks.zip(new File(tmp.getRoot(), "badm.zip"), e);
        Map<String, BufferedImage> got = PackTextureReader.readPaths(pack, MODEL);
        assertFalse(got.containsKey(ARMOR));
        assertEquals(16, got.get(PULL).getWidth());

        File dir = tmp.newFolder("badfolder");
        TestPacks.write(new File(dir, ModelSpec.ROOT + ARMOR), "not a png".getBytes());
        assertTrue(PackTextureReader.readPaths(dir, MODEL).isEmpty());

        File notZip = new File(tmp.getRoot(), "junkm.zip");
        TestPacks.write(notZip, "junk".getBytes());
        assertTrue(PackTextureReader.readPaths(notZip, MODEL).isEmpty());
        assertTrue(PackTextureReader.readPaths(new File(tmp.getRoot(), "absentm.zip"), MODEL).isEmpty());
    }
}
