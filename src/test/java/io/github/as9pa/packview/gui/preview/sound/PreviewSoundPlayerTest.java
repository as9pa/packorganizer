package io.github.as9pa.packview.gui.preview.sound;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import org.junit.Test;

public class PreviewSoundPlayerTest {
    private static byte[] read(InputStream in) throws IOException {
        try (InputStream s = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[3];
            int n;
            while ((n = s.read(buf)) != -1) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private static byte[] clip() {
        byte[] b = new byte[1000];
        for (int i = 0; i < b.length; i++) b[i] = (byte) (i * 31 + 7);
        b[0] = 'O';
        b[1] = 'g';
        b[2] = 'g';
        b[3] = 'S';
        return b;
    }

    @Test
    public void pitchIsClampedToTheVanillaRange() {
        assertEquals(0.5f, PreviewSoundPlayer.pitch(0.1f), 0f);
        assertEquals(0.5f, PreviewSoundPlayer.pitch(-3f), 0f);
        assertEquals(2f, PreviewSoundPlayer.pitch(3f), 0f);
        assertEquals(1.2f, PreviewSoundPlayer.pitch(1.2f), 0f);
        assertEquals(0.5f, PreviewSoundPlayer.pitch(0.5f), 0f);
        assertEquals(2f, PreviewSoundPlayer.pitch(2f), 0f);
    }

    @Test
    public void nanPitchPlaysAtNormalPitch() {
        assertEquals(1f, PreviewSoundPlayer.pitch(Float.NaN), 0f);
    }

    @Test
    public void volumeIsScaledByThePlayersSlider() {
        assertEquals(0.25f, PreviewSoundPlayer.volume(0.5f, 0.5f), 1e-6f);
        assertEquals(0.8f, PreviewSoundPlayer.volume(0.8f, 1f), 1e-6f);
        assertEquals(0.6f, PreviewSoundPlayer.volume(1f, 0.6f), 1e-6f);
    }

    @Test
    public void volumeIsClampedToZeroToOne() {
        assertEquals(1f, PreviewSoundPlayer.volume(2f, 1f), 0f);
        assertEquals(1f, PreviewSoundPlayer.volume(4f, 0.5f), 0f);
        assertEquals(0f, PreviewSoundPlayer.volume(-1f, 1f), 0f);
        assertEquals(0f, PreviewSoundPlayer.volume(Float.NaN, 1f), 0f);
        assertEquals(0f, PreviewSoundPlayer.volume(1f, Float.NaN), 0f);
    }

    @Test
    public void silentSliderOrClipGivesZero() {
        assertEquals(0f, PreviewSoundPlayer.volume(1f, 0f), 0f);
        assertEquals(0f, PreviewSoundPlayer.volume(0f, 1f), 0f);
    }

    @Test
    public void sourceNamesAreDistinctAndPrefixed() {
        assertEquals("po1", PreviewSoundPlayer.sourceName(1));
        assertEquals("po2", PreviewSoundPlayer.sourceName(2));
        assertNotEquals(PreviewSoundPlayer.sourceName(1), PreviewSoundPlayer.sourceName(11));
        // Vanilla names its sources with UUID strings, whose hex digits never include p or o.
        assertTrue(PreviewSoundPlayer.sourceName(Long.MAX_VALUE).matches("po\\d+"));
    }

    @Test
    public void urlServesExactlyTheGivenBytes() throws IOException {
        byte[] ogg = clip();
        URL url = PreviewSoundPlayer.url("pack/zip/sounds/random/bow.ogg", ogg);
        assertArrayEquals(ogg, read(url.openStream()));
    }

    @Test
    public void urlServesTheBytesAgainOnEachOpen() throws IOException {
        byte[] ogg = clip();
        URL url = PreviewSoundPlayer.url("a.ogg", ogg);
        assertArrayEquals(ogg, read(url.openStream()));
        URLConnection c = url.openConnection();
        c.connect();
        assertArrayEquals(ogg, read(c.getInputStream()));
    }

    @Test
    public void urlReportsTheContentLength() throws IOException {
        byte[] ogg = clip();
        URLConnection c = PreviewSoundPlayer.url("a.ogg", ogg).openConnection();
        assertEquals(ogg.length, c.getContentLength());
        assertEquals(ogg.length, c.getContentLengthLong());
    }

    @Test
    public void urlUsesOurProtocol() throws IOException {
        URL url = PreviewSoundPlayer.url("a.ogg", clip());
        assertEquals("packview", url.getProtocol());
    }

    @Test
    public void idsWithUrlSyntaxStillServeTheBytes() throws IOException {
        byte[] ogg = clip();
        for (String id : new String[] {"My Pack #2/sounds/a b.ogg", "x?y=1.ogg", "//host:99/a.ogg", "[::1]/%zz.ogg"}) {
            assertArrayEquals(id, ogg, read(PreviewSoundPlayer.url(id, ogg).openStream()));
        }
    }

    @Test
    public void noGlobalProtocolHandlerIsRegistered() throws IOException {
        PreviewSoundPlayer.url("a.ogg", clip());
        try {
            new URL("packview:a.ogg");
            fail("packview: resolved without our handler");
        } catch (MalformedURLException expected) {
            // Only the URLs we build carry the handler.
        }
    }
}
