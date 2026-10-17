package io.github.as9pa.packorganizer.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
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
}
