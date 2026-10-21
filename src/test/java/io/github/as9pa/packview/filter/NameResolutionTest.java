package io.github.as9pa.packview.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class NameResolutionTest {
    @Test
    public void colourCodesAreStrippedFirst() {
        assertEquals(32, NameResolution.find("\u00a76Kirby \u00a77[32x]"));
        assertEquals(16, NameResolution.find("\u00a7a16\u00a7bx pack"));
    }

    @Test
    public void realStyleNamesMatch() {
        assertEquals(64, NameResolution.find("Faithful 64x"));
        assertEquals(256, NameResolution.find("pack [256x] v2"));
        assertEquals(16, NameResolution.find("xyz 16 x"));
        assertEquals(128, NameResolution.find("128X"));
        assertEquals(8, NameResolution.find("tiny 8x.zip"));
        assertEquals(512, NameResolution.find("Huge (512x)"));
    }

    @Test
    public void lookalikesDoNotMatch() {
        assertEquals(0, NameResolution.find("Pack 1.7-1.8"));
        assertEquals(0, NameResolution.find("100k pack"));
        assertEquals(0, NameResolution.find("Fade 16xx"));
        assertEquals(0, NameResolution.find("x32 thing"));
        assertEquals(0, NameResolution.find("116x"));
        assertEquals(0, NameResolution.find("24x"));
        assertEquals(0, NameResolution.find("16x2"));
        assertEquals(0, NameResolution.find(""));
        assertEquals(0, NameResolution.find(null));
    }

    @Test
    public void bracketedBareNumberMatches() {
        assertEquals(32, NameResolution.find("PotPVP HP2019[32]"));
        assertEquals(64, NameResolution.find("pack [ 64 ]"));
        assertEquals(8, NameResolution.find("[8] tiny"));
        assertEquals(512, NameResolution.find("\u00a78[\u00a77512\u00a78]"));
        assertEquals(0, NameResolution.find("pack [20]"));
        assertEquals(0, NameResolution.find("pack [1.8]"));
        assertEquals(0, NameResolution.find("pack [W]"));
        assertEquals(0, NameResolution.find("pack 32"));
    }

    @Test
    public void nxTokenBeatsBracketedNumberInTheSameString() {
        assertEquals(64, NameResolution.find("[32] edit 64x"));
        assertEquals(16, NameResolution.find("16x [128]"));
    }

    @Test
    public void realPackNames() {
        assertEquals(0, NameResolution.find("Lotus [20x]"));
        assertEquals(32, NameResolution.find("PotPVP HP2019[32]"));
        assertEquals(32, NameResolution.find("Mikasa 32x xThonyG 10k[W]"));
        assertEquals(0, NameResolution.find("Vaes 15 k OG VERSION"));
        assertEquals(256, NameResolution.find("\u00a79Dark Sea \u00a78[\u00a77256x\u00a78]"));
        assertEquals(16, NameResolution.find("kim\u00a7derA \u00a78[16\u00a7dx\u00a78]"));
    }

    @Test
    public void firstTokenWins() {
        assertEquals(32, NameResolution.find("32x edit of a 16x pack"));
    }

    @Test
    public void overlayWordInNameMeansOverlay() {
        assertTrue(NameResolution.isOverlay("§bSky §7Overlay"));
        assertTrue(NameResolution.isOverlay("Faithful 32x Overlay"));
        assertTrue(NameResolution.isOverlay("sword OVERLAY.zip"));
        assertTrue(NameResolution.isOverlay("pvp overlays"));
        assertTrue(NameResolution.isOverlay("§aOver§blay"));
        assertFalse(NameResolution.isOverlay("Overlayer"));
        assertFalse(NameResolution.isOverlay("Faithful 32x"));
        assertFalse(NameResolution.isOverlay(""));
        assertFalse(NameResolution.isOverlay(null));
    }

    @Test
    public void descriptionIsReadFromMcmeta() {
        assertEquals("Made by Kirby 32x", NameResolution.description(mcmeta("{\"pack\":{\"pack_format\":1,\"description\":\"Made by Kirby 32x\"}}")));
        assertEquals(64, NameResolution.find(NameResolution.description(
                mcmeta("{\"pack\":{\"pack_format\":1,\"description\":{\"text\":\"Edit 64x\",\"color\":\"gold\"}}}"))));
        assertEquals("", NameResolution.description(mcmeta("{{{ not json")));
        assertEquals("", NameResolution.description(mcmeta("{\"pack\":{\"pack_format\":1}}")));
    }

    private static ByteArrayInputStream mcmeta(String json) {
        return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    }
}
