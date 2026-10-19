package io.github.as9pa.packorganizer.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SkyReaderTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static final String MCPATCHER = "assets/minecraft/mcpatcher/sky/world0/";
    private static final String OPTIFINE = "assets/minecraft/optifine/sky/world0/";

    @Test
    public void parsesLotusStyleNightLayer() {
        SkyLayer l = SkyReader.parse("source=./starfield.png\nstartFadeIn=19:20\nendFadeIn=20:00\nendFadeOut=4:35\nblend=add\nrotate=true\n", "mcpatcher/sky/world0");
        assertEquals("mcpatcher/sky/world0/starfield.png", l.source);
        assertEquals(19 * 60 + 20, l.startFadeIn);
        assertEquals(20 * 60, l.endFadeIn);
        assertEquals(4 * 60 + 35, l.endFadeOut);
        assertEquals(4 * 60 + 35 - 40, l.startFadeOut);
        assertEquals(1f, l.brightness(0), 1e-4f); // midnight, fully in
        assertEquals(0.5f, l.brightness(19 * 60 + 40), 1e-3f);
        assertEquals(0f, l.brightness(12 * 60), 1e-4f);
    }

    @Test
    public void defaultsWhenKeysMissing() {
        SkyLayer l = SkyReader.parse("startFadeIn=6:00\nendFadeIn=7:30\nendFadeOut=17:30\n", "optifine/sky/world0", 2);
        assertEquals("optifine/sky/world0/sky2.png", l.source);
        assertEquals("add", l.blend);
        assertTrue(l.rotate);
        assertEquals(1f, l.speed, 1e-6f);
        assertEquals(16 * 60, l.startFadeOut);
    }

    @Test
    public void readsEscapedTimesAndExplicitKeys() {
        SkyLayer l = SkyReader.parse("#Tue Jul 09 12:46:46 EDT 2013\nstartFadeIn=6\\:00\nblend=replace\nrotate=false\n"
                + "endFadeIn=7\\:30\nendFadeOut=17\\:30\nstartFadeOut=17\\:00\nspeed=0.5\nsource=./skybox.png\n",
                "mcpatcher/sky/world0", 2);
        assertEquals("mcpatcher/sky/world0/skybox.png", l.source);
        assertEquals("replace", l.blend);
        assertFalse(l.rotate);
        assertEquals(0.5f, l.speed, 1e-6f);
        assertEquals(17 * 60, l.startFadeOut);
        assertEquals(0f, l.brightness(5 * 60 + 59), 1e-4f);
        assertEquals(0.5f, l.brightness(6 * 60 + 45), 1e-3f);
        assertEquals(1f, l.brightness(12 * 60), 1e-4f);
        assertEquals(0.5f, l.brightness(17 * 60 + 15), 1e-3f);
        assertEquals(0f, l.brightness(17 * 60 + 30), 1e-4f);
        assertEquals(0f, l.brightness(23 * 60), 1e-4f);
    }

    @Test
    public void resolvesSourcePaths() {
        String times = "startFadeIn=6:00\nendFadeIn=7:00\nendFadeOut=18:00\n";
        String folder = "mcpatcher/sky/world0";
        assertEquals("textures/environment/sky.png",
                SkyReader.parse(times + "source=assets/minecraft/textures/environment/sky.png", folder).source);
        assertEquals("textures/environment/sky.png",
                SkyReader.parse(times + "source=textures/environment/sky.png", folder).source);
        assertEquals("optifine/sky/world0/night.png", SkyReader.parse(times + "source=~/sky/world0/night.png", folder).source);
        assertEquals("textures/sky.png", SkyReader.parse(times + "source=minecraft:textures/sky.png", folder).source);
        assertEquals("mcpatcher/sky/world0/clouds.png", SkyReader.parse(times + "source=clouds.png", folder).source);
        assertEquals("mcpatcher/sky/world0/sub/a.png", SkyReader.parse(times + "source=./sub/a.png", folder).source);
        assertNull(SkyReader.parse(times + "source=../../escape.png", folder));
        assertNull(SkyReader.parse(times, folder)); // no source and no N to default from
    }

    @Test
    public void badOrMissingTimesGiveNull() {
        assertNull(SkyReader.parse("startFadeIn=6:00\nendFadeIn=7:00\n", "mcpatcher/sky/world0", 1));
        assertNull(SkyReader.parse("startFadeIn=dawn\nendFadeIn=7:00\nendFadeOut=18:00\n", "mcpatcher/sky/world0", 1));
        assertNull(SkyReader.parse("startFadeIn=6:00\nendFadeIn=7:61\nendFadeOut=18:00\n", "mcpatcher/sky/world0", 1));
        assertNull(SkyReader.parse("", "mcpatcher/sky/world0", 1));
    }

    @Test
    public void readsLayersFromZipInOrder() throws Exception {
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put("pack.mcmeta", TestPacks.MCMETA);
        e.put(MCPATCHER + "sky10.properties", props("source=./skybox.png\nstartFadeIn=17:00\nendFadeIn=17:40\nendFadeOut=20:00\n"));
        e.put(MCPATCHER + "sky2.properties", props("startFadeIn=6\\:00\nendFadeIn=7\\:30\nendFadeOut=17\\:30\n"));
        e.put(MCPATCHER + "sky3.properties", props("startFadeIn=noon\nendFadeIn=7:30\nendFadeOut=17:30\n"));
        e.put(MCPATCHER + "sky4.properties", props("source=./missing.png\nstartFadeIn=6:00\nendFadeIn=7:30\nendFadeOut=17:30\n"));
        e.put(MCPATCHER + "sky1.properties", props("source=./starfield.png\nstartFadeIn=19:20\nendFadeIn=20:00\nendFadeOut=4:35\n"));
        e.put(MCPATCHER + "notes.properties", props("startFadeIn=6:00\nendFadeIn=7:30\nendFadeOut=17:30\n"));
        e.put(MCPATCHER + "starfield.png", TestPacks.png(6, 4));
        e.put(MCPATCHER + "sky2.png", TestPacks.png(6, 4));
        e.put(MCPATCHER + "skybox.png", TestPacks.png(6, 4));
        File pack = TestPacks.zip(new File(tmp.getRoot(), "sky.zip"), e);

        List<SkyLayer> layers = SkyReader.layers(pack);
        assertEquals(3, layers.size());
        assertEquals("mcpatcher/sky/world0/starfield.png", layers.get(0).source);
        assertEquals("mcpatcher/sky/world0/sky2.png", layers.get(1).source);
        assertEquals("mcpatcher/sky/world0/skybox.png", layers.get(2).source);
    }

    @Test
    public void prefersMcpatcherOverOptifine() throws Exception {
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put(OPTIFINE + "sky1.properties", props("startFadeIn=6:00\nendFadeIn=7:30\nendFadeOut=17:30\n"));
        e.put(OPTIFINE + "sky2.properties", props("startFadeIn=6:00\nendFadeIn=7:30\nendFadeOut=17:30\n"));
        e.put(OPTIFINE + "sky1.png", TestPacks.png(6, 4));
        e.put(OPTIFINE + "sky2.png", TestPacks.png(6, 4));
        e.put(MCPATCHER + "sky1.properties", props("source=./day.png\nstartFadeIn=6:00\nendFadeIn=7:30\nendFadeOut=17:30\n"));
        e.put(MCPATCHER + "day.png", TestPacks.png(6, 4));
        File pack = TestPacks.zip(new File(tmp.getRoot(), "both.zip"), e);

        List<SkyLayer> layers = SkyReader.layers(pack);
        assertEquals(1, layers.size());
        assertEquals("mcpatcher/sky/world0/day.png", layers.get(0).source);
    }

    @Test
    public void readsFolderPacksAndFallsBackToOptifine() throws Exception {
        File dir = tmp.newFolder("folder");
        TestPacks.write(new File(dir, "pack.mcmeta"), TestPacks.MCMETA);
        TestPacks.write(new File(dir, MCPATCHER + "sky1.properties"), props("startFadeIn=bad\nendFadeIn=7:30\nendFadeOut=17:30\n"));
        TestPacks.write(new File(dir, MCPATCHER + "sky1.png"), TestPacks.png(6, 4));
        TestPacks.write(new File(dir, OPTIFINE + "sky2.properties"), props("startFadeIn=6:00\nendFadeIn=7:30\nendFadeOut=17:30\n"));
        TestPacks.write(new File(dir, OPTIFINE + "sky1.properties"), props("source=textures/environment/night.png\nstartFadeIn=19:00\nendFadeIn=20:00\nendFadeOut=5:00\n"));
        TestPacks.write(new File(dir, OPTIFINE + "sky2.png"), TestPacks.png(6, 4));
        TestPacks.write(new File(dir, "assets/minecraft/textures/environment/night.png"), TestPacks.png(6, 4));

        List<SkyLayer> layers = SkyReader.layers(dir);
        assertEquals(2, layers.size());
        assertEquals("textures/environment/night.png", layers.get(0).source);
        assertEquals("optifine/sky/world0/sky2.png", layers.get(1).source);
        assertEquals(6, SkyReader.image(dir, layers.get(1).source, 384).getWidth());
    }

    @Test
    public void imageIsScaledToMaxWidth() throws Exception {
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put(MCPATCHER + "big.png", TestPacks.png(1536, 1024));
        e.put(MCPATCHER + "odd.png", TestPacks.png(1000, 500));
        e.put(MCPATCHER + "small.png", TestPacks.png(192, 128));
        e.put(MCPATCHER + "broken.png", "not a png".getBytes());
        File pack = TestPacks.zip(new File(tmp.getRoot(), "img.zip"), e);

        BufferedImage big = SkyReader.image(pack, "mcpatcher/sky/world0/big.png", 384);
        assertEquals(384, big.getWidth());
        assertEquals(256, big.getHeight());
        BufferedImage odd = SkyReader.image(pack, "mcpatcher/sky/world0/odd.png", 384);
        assertEquals(384, odd.getWidth());
        assertEquals(192, odd.getHeight());
        BufferedImage small = SkyReader.image(pack, "mcpatcher/sky/world0/small.png", 384);
        assertEquals(192, small.getWidth());
        assertEquals(128, small.getHeight());
        assertNull(SkyReader.image(pack, "mcpatcher/sky/world0/broken.png", 384));
        assertNull(SkyReader.image(pack, "mcpatcher/sky/world0/absent.png", 384));
        assertNull(SkyReader.image(new File(tmp.getRoot(), "absent.zip"), "mcpatcher/sky/world0/big.png", 384));
    }

    @Test
    public void packWithoutSkyGivesEmptyList() throws Exception {
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put("pack.mcmeta", TestPacks.MCMETA);
        e.put("assets/minecraft/textures/blocks/stone.png", TestPacks.png(16, 16));
        File pack = TestPacks.zip(new File(tmp.getRoot(), "plain.zip"), e);
        assertTrue(SkyReader.layers(pack).isEmpty());

        assertTrue(SkyReader.layers(tmp.newFolder("empty")).isEmpty());
        assertTrue(SkyReader.layers(new File(tmp.getRoot(), "absent.zip")).isEmpty());
        File junk = new File(tmp.getRoot(), "junk.zip");
        TestPacks.write(junk, "junk".getBytes());
        assertTrue(SkyReader.layers(junk).isEmpty());
    }

    private static byte[] props(String text) {
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }
}
