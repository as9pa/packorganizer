package io.github.as9pa.packview.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class PackResolutionTest {
    @Test
    public void bucketsRoundDownToAPowerOfTwoWithin16To128() {
        assertEquals(16, PackResolution.bucket(8));
        assertEquals(16, PackResolution.bucket(16));
        assertEquals(16, PackResolution.bucket(17));
        assertEquals(16, PackResolution.bucket(20));
        assertEquals(16, PackResolution.bucket(24));
        assertEquals(16, PackResolution.bucket(31));
        assertEquals(32, PackResolution.bucket(32));
        assertEquals(32, PackResolution.bucket(48));
        assertEquals(32, PackResolution.bucket(63));
        assertEquals(64, PackResolution.bucket(64));
        assertEquals(64, PackResolution.bucket(127));
        assertEquals(128, PackResolution.bucket(128));
        assertEquals(128, PackResolution.bucket(256));
        assertEquals(128, PackResolution.bucket(512));
        assertEquals(16, PackResolution.bucket(1));
    }

    @Test
    public void emptyWidthsIsOverlay() {
        assertEquals(PackResolution.OVERLAY, PackResolution.fromWidths(Collections.<Integer>emptyList()));
    }

    @Test
    public void mostCommonWidthWins() {
        assertEquals(PackResolution.textures(16), PackResolution.fromWidths(Arrays.asList(16, 16, 32)));
        assertEquals(PackResolution.textures(64), PackResolution.fromWidths(Arrays.asList(64, 16, 64, 64, 32)));
    }

    @Test
    public void tieGoesToTheLargerWidth() {
        assertEquals(PackResolution.textures(32), PackResolution.fromWidths(Arrays.asList(16, 32)));
        assertEquals(PackResolution.textures(32), PackResolution.fromWidths(Arrays.asList(32, 32, 16, 16)));
        assertEquals(PackResolution.textures(32), PackResolution.fromWidths(Arrays.asList(16, 16, 32, 32)));
    }

    @Test
    public void widthsAreBucketedAfterVoting() {
        assertEquals(PackResolution.textures(128), PackResolution.fromWidths(Arrays.asList(512, 256)));
        assertEquals(PackResolution.textures(16), PackResolution.fromWidths(Arrays.asList(20, 20, 64)));
    }

    @Test
    public void voteWonByWidth20LandsInThe16Bucket() {
        assertEquals(PackResolution.textures(16), PackResolution.fromWidths(Arrays.asList(20, 20, 20, 16, 16)));
    }

    @Test
    public void mostCommonBreaksTiesToTheLargerWidth() {
        assertEquals(32, PackResolution.mostCommon(Arrays.asList(16, 32)));
        assertEquals(16, PackResolution.mostCommon(Arrays.asList(16, 16, 32)));
        assertEquals(0, PackResolution.mostCommon(Collections.<Integer>emptyList()));
    }

    @Test
    public void badgeText() {
        assertEquals("16x", PackResolution.textures(16).badge());
        assertEquals("128x", PackResolution.textures(128).badge());
        assertEquals("overlay", PackResolution.OVERLAY.badge());
        assertEquals("", PackResolution.UNKNOWN.badge());
    }

    @Test
    public void equalityIsByValue() {
        assertEquals(PackResolution.textures(32), PackResolution.textures(32));
        assertEquals(PackResolution.textures(32).hashCode(), PackResolution.textures(32).hashCode());
        assertNotEquals(PackResolution.textures(32), PackResolution.textures(16));
        assertNotEquals(PackResolution.OVERLAY, PackResolution.UNKNOWN);
    }

    @Test
    public void statedValueKeepsItsNumberButFiltersByBucket() {
        PackResolution r = PackResolution.stated(256);
        assertEquals(PackResolution.Kind.TEXTURES, r.kind);
        assertEquals(128, r.res);
        assertEquals(256, r.stated);
        assertEquals("256x", r.badge());
        assertEquals(16, PackResolution.stated(8).res);
        assertEquals("8x", PackResolution.stated(8).badge());
        assertNotEquals(PackResolution.textures(128), r);
        assertEquals(0, PackResolution.textures(32).stated);
    }
}
