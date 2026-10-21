package io.github.as9pa.packview.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ResolutionScannerTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static final String ITEMS = "assets/minecraft/textures/items/";
    private static final String BLOCKS = "assets/minecraft/textures/blocks/";
    private static final String ARMOR = "assets/minecraft/textures/models/armor/";
    private static final String[] WOOL_COLOURS = {
        "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
        "silver", "cyan", "purple", "blue", "brown", "green", "red", "black",
    };
    private static final String SWORD = "assets/minecraft/textures/items/diamond_sword.png";
    private static final String STONE = "assets/minecraft/textures/blocks/stone.png";
    private static final String DIRT = "assets/minecraft/textures/blocks/dirt.png";
    private static final String SKY = "assets/minecraft/mcpatcher/sky/world0/sky1.png";

    @Test
    public void zipPackVotesAcrossSampledTextures() throws IOException {
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put("pack.mcmeta", TestPacks.MCMETA);
        e.put(SWORD, TestPacks.png(32, 32));
        e.put(STONE, TestPacks.png(32, 32));
        e.put(DIRT, TestPacks.png(16, 16));
        assertEquals(PackResolution.textures(32), ResolutionScanner.scan(TestPacks.zip(new File(tmp.getRoot(), "a.zip"), e)));
    }

    @Test
    public void folderPackIsScannedToo() throws IOException {
        File folder = tmp.newFolder("FolderPack");
        TestPacks.write(new File(folder, "pack.mcmeta"), TestPacks.MCMETA);
        TestPacks.write(new File(folder, STONE), TestPacks.png(64, 64));
        assertEquals(PackResolution.textures(64), ResolutionScanner.scan(folder));
    }

    @Test
    public void packWithNoSampledTexturesIsOverlay() throws IOException {
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put("pack.mcmeta", TestPacks.MCMETA);
        e.put(SKY, TestPacks.png(512, 512));
        assertEquals(PackResolution.OVERLAY, ResolutionScanner.scan(TestPacks.zip(new File(tmp.getRoot(), "o.zip"), e)));
    }

    @Test
    public void unreadableSampledTextureIsIgnored() throws IOException {
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put(STONE, "not a png".getBytes());
        e.put(DIRT, TestPacks.png(16, 16));
        assertEquals(PackResolution.textures(16), ResolutionScanner.scan(TestPacks.zip(new File(tmp.getRoot(), "b.zip"), e)));
    }

    @Test
    public void missingFileIsUnknown() {
        assertEquals(PackResolution.UNKNOWN, ResolutionScanner.scan(new File(tmp.getRoot(), "gone.zip")));
    }

    @Test
    public void corruptZipIsUnknown() throws IOException {
        File bad = tmp.newFile("bad.zip");
        TestPacks.write(bad, "this is not a zip".getBytes());
        assertEquals(PackResolution.UNKNOWN, ResolutionScanner.scan(bad));
    }

    @Test
    public void nestedFolderZipDoesNotCrash() throws IOException {
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put("MyPack/pack.mcmeta", TestPacks.MCMETA);
        e.put("MyPack/" + STONE, TestPacks.png(32, 32));
        // Vanilla never loads such a zip, so the scanner is never asked; if it is, it must simply not throw.
        assertEquals(PackResolution.OVERLAY, ResolutionScanner.scan(TestPacks.zip(new File(tmp.getRoot(), "n.zip"), e)));
    }

    @Test
    public void overlayInNameBeatsTexturesAndNameToken() throws IOException {
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put("pack.mcmeta", TestPacks.MCMETA);
        e.put(SWORD, TestPacks.png(32, 32));
        e.put(STONE, TestPacks.png(32, 32));
        assertEquals(PackResolution.OVERLAY, ResolutionScanner.scan(TestPacks.zip(new File(tmp.getRoot(), "Faithful 32x Overlay.zip"), e)));
        File folder = tmp.newFolder("§bSky §7Overlay");
        TestPacks.write(new File(folder, STONE), TestPacks.png(64, 64));
        assertEquals(PackResolution.OVERLAY, ResolutionScanner.scan(folder));
        assertEquals(PackResolution.textures(32), ResolutionScanner.scan(TestPacks.zip(new File(tmp.getRoot(), "Overlayer.zip"), e)));
    }

    @Test
    public void nameTokenOverridesTextureWidths() throws IOException {
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put("pack.mcmeta", TestPacks.MCMETA);
        e.put(SWORD, TestPacks.png(16, 16));
        e.put(STONE, TestPacks.png(16, 16));
        PackResolution r = ResolutionScanner.scan(TestPacks.zip(new File(tmp.getRoot(), "\u00a76Kirby \u00a77[256x].zip"), e));
        assertEquals(PackResolution.stated(256), r);
        assertEquals(128, r.res);
        assertEquals("256x", r.badge());
    }

    @Test
    public void descriptionTokenIsUsedWhenTheNameHasNone() throws IOException {
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put("pack.mcmeta", "{\"pack\":{\"pack_format\":1,\"description\":\"\u00a7732x edit\"}}".getBytes("UTF-8"));
        e.put(STONE, TestPacks.png(16, 16));
        assertEquals(PackResolution.stated(32), ResolutionScanner.scan(TestPacks.zip(new File(tmp.getRoot(), "d.zip"), e)));
    }

    @Test
    public void folderPackDescriptionIsReadToo() throws IOException {
        File folder = tmp.newFolder("Plain");
        TestPacks.write(new File(folder, "pack.mcmeta"), "{\"pack\":{\"pack_format\":1,\"description\":\"8x\"}}".getBytes("UTF-8"));
        TestPacks.write(new File(folder, STONE), TestPacks.png(64, 64));
        assertEquals(PackResolution.stated(8), ResolutionScanner.scan(folder));
    }

    @Test
    public void nameTokenWinsOverDescriptionToken() throws IOException {
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put("pack.mcmeta", "{\"pack\":{\"pack_format\":1,\"description\":\"64x\"}}".getBytes("UTF-8"));
        assertEquals(PackResolution.stated(16), ResolutionScanner.scan(TestPacks.zip(new File(tmp.getRoot(), "Fade 16x.zip"), e)));
    }

    @Test
    public void armorLayerWidthIsDividedByFour() throws IOException {
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put(ARMOR + "diamond_layer_1.png", TestPacks.png(128, 64));
        assertEquals(PackResolution.textures(32), ResolutionScanner.scan(TestPacks.zip(new File(tmp.getRoot(), "armor.zip"), e)));
    }

    @Test
    public void armorLayersVoteOncePerFile() throws IOException {
        File folder = tmp.newFolder("Armor");
        TestPacks.write(new File(folder, ARMOR + "iron_layer_1.png"), TestPacks.png(256, 128));
        TestPacks.write(new File(folder, ARMOR + "iron_layer_2.png"), TestPacks.png(256, 128));
        TestPacks.write(new File(folder, STONE), TestPacks.png(16, 16));
        assertEquals(PackResolution.textures(64), ResolutionScanner.scan(folder));
    }

    @Test
    public void allWoolCastsOneVote() throws IOException {
        Map<String, byte[]> e = new LinkedHashMap<>();
        for (String colour : WOOL_COLOURS) {
            e.put(BLOCKS + "wool_colored_" + colour + ".png", TestPacks.png(16, 16));
        }
        e.put(SWORD, TestPacks.png(32, 32));
        e.put(ITEMS + "stone_sword.png", TestPacks.png(32, 32));
        e.put(ITEMS + "ender_pearl.png", TestPacks.png(32, 32));
        assertEquals(PackResolution.textures(32), ResolutionScanner.scan(TestPacks.zip(new File(tmp.getRoot(), "wool.zip"), e)));
    }

    @Test
    public void woolVoteIsItsMostCommonWidthTieToLarger() throws IOException {
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put(BLOCKS + "wool_colored_red.png", TestPacks.png(16, 16));
        e.put(BLOCKS + "wool_colored_blue.png", TestPacks.png(64, 64));
        e.put(STONE, TestPacks.png(16, 16));
        // Wool votes 64 once, stone votes 16 once; the tie goes to 64.
        assertEquals(PackResolution.textures(64), ResolutionScanner.scan(TestPacks.zip(new File(tmp.getRoot(), "w2.zip"), e)));
    }

    @Test
    public void lotusStyleArmorIconsAt20StayIn16() throws IOException {
        Map<String, byte[]> e = new LinkedHashMap<>();
        for (String m : new String[] {"iron", "gold", "diamond"}) {
            for (String p : new String[] {"helmet", "chestplate", "leggings", "boots"}) {
                e.put(ITEMS + m + "_" + p + ".png", TestPacks.png(20, 20));
            }
        }
        e.put(SWORD, TestPacks.png(16, 16));
        e.put(STONE, TestPacks.png(16, 16));
        assertEquals(PackResolution.textures(16), ResolutionScanner.scan(TestPacks.zip(new File(tmp.getRoot(), "lotus.zip"), e)));
    }

    @Test
    public void sampleListsAreTheSpecLists() {
        List<String> samples = Arrays.asList(ResolutionScanner.SAMPLES);
        assertEquals(new HashSet<>(samples).size(), samples.size());
        assertEquals(SWORD, ResolutionScanner.SAMPLES[0]);
        for (String sword : new String[] {"wood", "stone", "iron", "gold", "diamond"}) {
            assertTrue(sword, samples.contains(ITEMS + sword + "_sword.png"));
        }
        for (String m : new String[] {"leather", "chainmail", "iron", "gold", "diamond"}) {
            for (String p : new String[] {"helmet", "chestplate", "leggings", "boots"}) {
                assertTrue(m + p, samples.contains(ITEMS + m + "_" + p + ".png"));
            }
        }
        for (String s : new String[] {"bow_standby", "fishing_rod_uncast", "apple_golden", "ender_pearl", "potion_bottle_splash"}) {
            assertTrue(s, samples.contains(ITEMS + s + ".png"));
        }
        for (String s : new String[] {"potion_bottle_drinkable", "fishing_rod_cast", "arrow", "fireball", "egg", "shears",
                "iron_ingot", "gold_ingot", "diamond", "emerald", "beef_cooked", "bucket_water", "bucket_lava", "snowball",
                "diamond_pickaxe", "iron_pickaxe", "diamond_axe", "iron_axe"}) {
            assertTrue(s, samples.contains(ITEMS + s + ".png"));
        }
        assertFalse(samples.contains(ITEMS + "bow_standing.png"));
        for (String s : new String[] {"stone", "planks_oak", "sandstone_normal", "dirt", "cobblestone"}) {
            assertTrue(s, samples.contains(BLOCKS + s + ".png"));
        }
        assertEquals(53, samples.size());
        assertEquals(10, ResolutionScanner.ARMOR_LAYERS.length);
        assertTrue(Arrays.asList(ResolutionScanner.ARMOR_LAYERS).contains(ARMOR + "chainmail_layer_2.png"));
        assertEquals(16, ResolutionScanner.WOOL.length);
        assertFalse(samples.contains(BLOCKS + "wool_colored_white.png"));
        for (String colour : WOOL_COLOURS) {
            assertTrue(colour, Arrays.asList(ResolutionScanner.WOOL).contains(BLOCKS + "wool_colored_" + colour + ".png"));
        }
    }
}
