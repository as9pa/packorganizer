package io.github.as9pa.packorganizer.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Arrays;
import org.junit.Test;

public class ColorMathTest {
    private static final double EPS = 1e-6;

    @Test
    public void wheelProfileValues() {
        assertEquals(1.0, ColorMath.wheelLightness(0), EPS);
        assertEquals(0.75, ColorMath.wheelLightness(0.15), EPS);
        assertEquals(0.5, ColorMath.wheelLightness(0.3), EPS);
        assertEquals(0.5, ColorMath.wheelLightness(0.5), EPS);
        assertEquals(0.5, ColorMath.wheelLightness(0.66), EPS);
        assertEquals(0.5 - 0.47 * 0.5, ColorMath.wheelLightness(0.83), EPS);
        assertEquals(0.03, ColorMath.wheelLightness(1.0), EPS);
    }

    @Test
    public void smoothstepClampsAndEases() {
        assertEquals(0, ColorMath.smoothstep(-1), EPS);
        assertEquals(0.5, ColorMath.smoothstep(0.5), EPS);
        assertEquals(1, ColorMath.smoothstep(2), EPS);
    }

    @Test
    public void hueAngleIsClockwiseFromTop() {
        assertEquals(0, ColorMath.pixelToPick(0, -0.5)[0], EPS);
        assertEquals(90, ColorMath.pixelToPick(0.5, 0)[0], EPS);
        assertEquals(180, ColorMath.pixelToPick(0, 0.5)[0], EPS);
        assertEquals(270, ColorMath.pixelToPick(-0.5, 0)[0], EPS);
        assertEquals(0.5, ColorMath.pixelToPick(0, -0.5)[1], EPS);
        assertEquals(1.0, ColorMath.pixelToPick(0, 0)[1], EPS);
        assertNull(ColorMath.pixelToPick(0.8, 0.8));
    }

    @Test
    public void hslRoundTrip() {
        float[] red = ColorMath.rgbToHsl(0xFF0000);
        assertEquals(0, red[0], EPS);
        assertEquals(1, red[1], EPS);
        assertEquals(0.5, red[2], EPS);
        float[] blue = ColorMath.rgbToHsl(0x0000FF);
        assertEquals(240, blue[0], EPS);
        assertEquals(0xFF0000, ColorMath.hslToRgb(0, 1, 0.5f));
        assertEquals(0x00FF00, ColorMath.hslToRgb(120, 1, 0.5f));
        assertEquals(0xFFFFFF, ColorMath.hslToRgb(0, 0, 1));
    }

    private static int[] fill(int argb, int n) {
        int[] a = new int[n];
        Arrays.fill(a, argb);
        return a;
    }

    @Test
    public void featuresOfPureRed() {
        ColorFeatures f = ColorFeatures.from(fill(0xFFFF0000, 16));
        assertEquals(0, f.dark, EPS);
        assertEquals(0, f.white, EPS);
        assertEquals(1, f.bins[ColorFeatures.bin(0, 1)], EPS);
        assertEquals(1, f.total(), EPS);
    }

    @Test
    public void featuresOfBlackAndWhite() {
        assertEquals(1, ColorFeatures.from(fill(0xFF000000, 4)).dark, EPS);
        assertEquals(1, ColorFeatures.from(fill(0xFFFFFFFF, 4)).white, EPS);
    }

    @Test
    public void transparentPixelsIgnoredAndSharesSumToOne() {
        int[] px = {0xFFFF0000, 0xFF000000, 0x7FFFFFFF, 0x00FFFFFF, 0xFFFFFFFF, 0xFF0000FF};
        ColorFeatures f = ColorFeatures.from(px);
        assertEquals(0.25, f.dark, EPS);
        assertEquals(0.25, f.white, EPS);
        assertEquals(0.25, f.bins[ColorFeatures.bin(0, 1)], EPS);
        assertEquals(0.25, f.bins[ColorFeatures.bin(24, 1)], EPS);
        assertEquals(1, f.total(), EPS);
    }

    @Test
    public void lightnessBins() {
        // l = 0.25 -> bin 0, l = 0.75 (s = 1) -> bin 2
        assertEquals(1, ColorFeatures.from(fill(0xFF800000, 1)).bins[ColorFeatures.bin(0, 0)], 0);
        assertEquals(1, ColorFeatures.from(fill(0xFFFF8080, 1)).bins[ColorFeatures.bin(0, 2)], 0);
    }

    @Test
    public void allTransparentGivesZeros() {
        ColorFeatures f = ColorFeatures.from(fill(0x00FF0000, 3));
        assertEquals(0, f.total(), EPS);
    }

    @Test
    public void sparseJsonRoundTrip() {
        int[] px = {0xFFFF0000, 0xFF000000, 0xFFFFFFFF, 0xFF0000FF};
        ColorFeatures f = ColorFeatures.from(px);
        JsonObject json = f.toJson();
        assertEquals(2, json.getAsJsonObject("b").entrySet().size());
        ColorFeatures back = ColorFeatures.fromJson(new JsonParser().parse(json.toString()));
        assertEquals(f.dark, back.dark, EPS);
        assertEquals(f.white, back.white, EPS);
        for (int i = 0; i < ColorFeatures.BIN_COUNT; i++) {
            assertEquals(f.bins[i], back.bins[i], EPS);
        }
    }

    @Test
    public void malformedJsonGivesNull() {
        assertNull(ColorFeatures.fromJson(new JsonParser().parse("[1,2]")));
        assertNull(ColorFeatures.fromJson(null));
    }
}
