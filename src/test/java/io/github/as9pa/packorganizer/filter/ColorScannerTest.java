package io.github.as9pa.packorganizer.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ColorScannerTest {
    private static final int RED = 0xFFFF0000;
    private static final int BLUE = 0xFF0000FF;
    private static final int GREEN = 0xFF00FF00;
    private static final int RED_BIN = ColorFeatures.bin(0, 1);
    private static final int BLUE_BIN = ColorFeatures.bin(24, 1);
    private static final int GREEN_BIN = ColorFeatures.bin(12, 1);
    private static final double EPS = 1e-9;

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    /** A w x h PNG whose top {@code topRows} rows are {@code top} and the rest {@code rest}. */
    private static byte[] png(int w, int h, int topRows, int top, int rest) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setRGB(x, y, y < topRows ? top : rest);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static byte[] solid(int w, int h, int argb) throws IOException {
        return png(w, h, h, argb, argb);
    }

    private File zip(Map<String, byte[]> entries) throws IOException {
        entries.put("pack.mcmeta", TestPacks.MCMETA);
        return TestPacks.zip(new File(tmp.getRoot(), "p.zip"), entries);
    }

    @Test
    public void swordOnlyLeavesArmorNull() throws IOException {
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put(ColorScanner.SWORD, solid(16, 16, RED));
        PackColors c = ColorScanner.scan(zip(e));
        assertNotNull(c.sword);
        assertEquals(1.0, c.sword.bins[RED_BIN], EPS);
        assertNull(c.armor);
    }

    @Test
    public void tallSwordIsCroppedToItsTopSquare() throws IOException {
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put(ColorScanner.SWORD, png(16, 48, 16, RED, BLUE));
        PackColors c = ColorScanner.scan(zip(e));
        assertEquals(1.0, c.sword.bins[RED_BIN], EPS);
        assertEquals(0.0, c.sword.bins[BLUE_BIN], EPS);
    }

    @Test
    public void armorLayersArePooledAndBeatTheItemIcons() throws IOException {
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put(ColorScanner.ARMOR_LAYERS[0], solid(64, 32, RED));
        e.put(ColorScanner.ARMOR_LAYERS[1], solid(64, 32, BLUE));
        e.put(ColorScanner.ARMOR_ITEMS[0], solid(16, 16, GREEN));
        PackColors c = ColorScanner.scan(zip(e));
        assertNull(c.sword);
        assertEquals(0.5, c.armor.bins[RED_BIN], EPS);
        assertEquals(0.5, c.armor.bins[BLUE_BIN], EPS);
        assertEquals(0.0, c.armor.bins[GREEN_BIN], EPS);
    }

    @Test
    public void armorFallsBackToTheFourItemIcons() throws IOException {
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put(ColorScanner.ARMOR_ITEMS[0], solid(16, 16, GREEN));
        e.put(ColorScanner.ARMOR_ITEMS[3], solid(16, 16, BLUE));
        PackColors c = ColorScanner.scan(zip(e));
        assertEquals(0.5, c.armor.bins[GREEN_BIN], EPS);
        assertEquals(0.5, c.armor.bins[BLUE_BIN], EPS);
    }

    @Test
    public void folderPackIsRead() throws IOException {
        File pack = tmp.newFolder("Folder Pack");
        TestPacks.write(new File(pack, "pack.mcmeta"), TestPacks.MCMETA);
        TestPacks.write(new File(pack, ColorScanner.SWORD), solid(16, 16, BLUE));
        TestPacks.write(new File(pack, ColorScanner.ARMOR_LAYERS[1]), solid(64, 32, RED));
        PackColors c = ColorScanner.scan(pack);
        assertEquals(1.0, c.sword.bins[BLUE_BIN], EPS);
        assertEquals(1.0, c.armor.bins[RED_BIN], EPS);
    }

    @Test
    public void packWithoutThoseTexturesHasNoColors() throws IOException {
        assertEquals(PackColors.NONE, ColorScanner.scan(zip(new LinkedHashMap<String, byte[]>())));
    }

    @Test
    public void unreadableTextureCountsAsAbsent() throws IOException {
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put(ColorScanner.SWORD, "not a png".getBytes());
        e.put(ColorScanner.ARMOR_ITEMS[1], solid(16, 16, RED));
        PackColors c = ColorScanner.scan(zip(e));
        assertNull(c.sword);
        assertEquals(1.0, c.armor.bins[RED_BIN], EPS);
    }

    @Test
    public void missingOrBrokenPackNeverThrows() throws IOException {
        assertEquals(PackColors.NONE, ColorScanner.scan(new File(tmp.getRoot(), "gone.zip")));
        File broken = tmp.newFile("broken.zip");
        TestPacks.write(broken, "not a zip".getBytes());
        assertEquals(PackColors.NONE, ColorScanner.scan(broken));
    }
}
