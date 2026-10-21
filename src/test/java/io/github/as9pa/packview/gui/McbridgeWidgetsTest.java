package io.github.as9pa.packview.gui;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import io.github.as9pa.packview.filter.PackFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class McbridgeWidgetsTest {
    private final List<Object> out = new ArrayList<>();

    @Test
    public void addPutsTheRequiredKeysAndAppends() {
        McbridgeWidgets.Widget widget = McbridgeWidgets.add(out, "search", McbridgeWidgets.TEXT, 10, 28, 140, 20);
        assertEquals(1, out.size());
        assertSame(widget, out.get(0));
        assertEquals(Arrays.asList("id", "kind", "x", "y", "w", "h"), new ArrayList<>(widget.keySet()));
        assertEquals("search", widget.get("id"));
        assertEquals("text", widget.get("kind"));
        assertEquals(10, widget.get("x"));
        assertEquals(28, widget.get("y"));
        assertEquals(140, widget.get("w"));
        assertEquals(20, widget.get("h"));
    }

    @Test
    public void toPixelsScalesEveryBoxByTheGuiScale() {
        McbridgeWidgets.add(out, "search", McbridgeWidgets.TEXT, 10, 28, 140, 20).label("Search packs");
        McbridgeWidgets.toPixels(out, 2);
        Map<?, ?> widget = (Map<?, ?>) out.get(0);
        assertEquals(20, widget.get("x"));
        assertEquals(56, widget.get("y"));
        assertEquals(280, widget.get("w"));
        assertEquals(40, widget.get("h"));
        assertEquals("Search packs", widget.get("label"));
    }

    @Test
    public void optionalKeysChainAndKeepTheirTypes() {
        Map<String, Object> widget = McbridgeWidgets.add(out, "colorwheel:clear", McbridgeWidgets.BUTTON, 0, 0, 40, 20)
                .label("Clear").enabled(true).hovered(false).selected(true).checked(false).value(3).parent("colorwheel");
        assertEquals("Clear", widget.get("label"));
        assertEquals(Boolean.TRUE, widget.get("enabled"));
        assertEquals(Boolean.FALSE, widget.get("hovered"));
        assertEquals(Boolean.TRUE, widget.get("selected"));
        assertEquals(Boolean.FALSE, widget.get("checked"));
        assertEquals(3, widget.get("value"));
        assertEquals("colorwheel", widget.get("parent"));
    }

    @Test
    public void negativeSizesClampToZero() {
        Map<String, Object> widget = McbridgeWidgets.add(out, "a", McbridgeWidgets.OTHER, 5, 5, -3, -1);
        assertEquals(0, widget.get("w"));
        assertEquals(0, widget.get("h"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyIdIsRejected() {
        McbridgeWidgets.add(out, "", McbridgeWidgets.OTHER, 0, 0, 1, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullIdIsRejected() {
        McbridgeWidgets.add(out, null, McbridgeWidgets.OTHER, 0, 0, 1, 1);
    }

    @Test
    public void chipIdsMatchTheContract() {
        List<String> ids = new ArrayList<>();
        for (PackFilter.Chip chip : PackFilter.Chip.values()) {
            ids.add(McbridgeWidgets.chipId(chip));
        }
        assertEquals(Arrays.asList("chip:all", "chip:16x", "chip:32x", "chip:64x", "chip:128x", "chip:overlay"), ids);
    }

    @Test
    public void pickValueIsNoneWithoutAPick() {
        assertEquals("none", McbridgeWidgets.pickValue(false, 120, 0.5));
    }

    @Test
    public void pickValueShowsHueAndLightness() {
        assertEquals("hue 120.0, lightness 0.500", McbridgeWidgets.pickValue(true, 120, 0.5));
        assertEquals("hue 0.3, lightness 0.125", McbridgeWidgets.pickValue(true, 0.25, 0.125));
    }

    @Test
    public void outfitValueNamesTheItemAndArmor() {
        assertEquals("item bow, armor diamond", McbridgeWidgets.outfitValue("bow", "diamond"));
        assertEquals("item none, armor none", McbridgeWidgets.outfitValue("none", null));
    }

    @Test
    public void modelBoundsWrapThePartCornersInsideTheView() {
        List<float[]> parts = Arrays.asList(new float[] {50.5f, 40.2f, 60f, 90.7f}, new float[] {45f, 60f, 70.1f, 30f});
        assertArrayEquals(new int[] {45, 30, 26, 61}, McbridgeWidgets.modelBounds(parts, 0, 0, 200, 200));
        // Clipped to the view where it overflows.
        assertArrayEquals(new int[] {50, 35, 21, 45}, McbridgeWidgets.modelBounds(parts, 50, 35, 100, 45));
    }

    @Test
    public void modelBoundsFallBackToTheViewWithNothingDrawnInIt() {
        int[] view = {10, 20, 30, 40};
        assertArrayEquals(view, McbridgeWidgets.modelBounds(new ArrayList<float[]>(), 10, 20, 30, 40));
        List<float[]> outside = Arrays.<float[]>asList(new float[] {100f, 100f, 110f, 120f});
        assertArrayEquals(view, McbridgeWidgets.modelBounds(outside, 10, 20, 30, 40));
    }
}
