package io.github.as9pa.packview.gui;

import io.github.as9pa.packview.PackView;
import io.github.as9pa.packview.filter.PackFilter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;

/**
 * Describes our widgets on the resource packs screen to the mcbridge automation mod, which reads them through the
 * screen's public mcbridgeDescribe(): one map per widget with an id, a kind and its bounds in GUI pixels, plus the
 * optional state keys of its contract. The screen mixin collects the toolbar here, then appends its other panels.
 */
public final class McbridgeWidgets {
    public static final String BUTTON = "button";
    public static final String TOGGLE = "toggle";
    public static final String TEXT = "text";
    public static final String OTHER = "other";

    private McbridgeWidgets() {}

    /** One widget's description; the setters return it so a call site reads as one expression. */
    public static final class Widget extends LinkedHashMap<String, Object> {
        Widget(String id, String kind, int x, int y, int w, int h) {
            if (id == null || id.isEmpty()) {
                throw new IllegalArgumentException("widget id must be non-empty");
            }
            put("id", id);
            put("kind", kind);
            put("x", x);
            put("y", y);
            put("w", Math.max(0, w));
            put("h", Math.max(0, h));
        }

        public Widget label(String label) {
            put("label", label);
            return this;
        }

        public Widget enabled(boolean enabled) {
            put("enabled", enabled);
            return this;
        }

        public Widget hovered(boolean hovered) {
            put("hovered", hovered);
            return this;
        }

        public Widget selected(boolean selected) {
            put("selected", selected);
            return this;
        }

        public Widget checked(boolean checked) {
            put("checked", checked);
            return this;
        }

        /** A String or a Number. */
        public Widget value(Object value) {
            put("value", value);
            return this;
        }

        public Widget parent(String parent) {
            put("parent", parent);
            return this;
        }
    }

    /**
     * Turns the GUI-pixel boxes in out into framebuffer pixels, the space mcbridge reads describe bounds in.
     */
    public static void toPixels(List<Object> out, int scale) {
        for (Object o : out) {
            if (o instanceof Widget) {
                Widget widget = (Widget) o;
                for (String key : new String[] {"x", "y", "w", "h"}) {
                    widget.put(key, ((Number) widget.get(key)).intValue() * scale);
                }
            }
        }
    }

    /** Adds a widget to out and returns it for the optional keys. */
    public static Widget add(List<Object> out, String id, String kind, int x, int y, int w, int h) {
        Widget widget = new Widget(id, kind, x, y, w, h);
        out.add(widget);
        return widget;
    }

    /** A button's bounds, label, enabled and hovered state (hovered as of its last draw). */
    public static Widget addButton(List<Object> out, String id, String kind, GuiButton button) {
        return add(out, id, kind, button.xPosition, button.yPosition, button.width, button.height)
                .label(button.displayString).enabled(button.enabled).hovered(button.isMouseOver());
    }

    /** "chip:all", "chip:16x" ... "chip:128x", "chip:overlay": the chip's label, lower case, without the "+". */
    public static String chipId(PackFilter.Chip chip) {
        return "chip:" + chip.label.toLowerCase(Locale.ROOT).replace("+", "");
    }

    /** The color pick as mcbridge shows it, or "none" without one. */
    public static String pickValue(boolean picked, double hue, double lightness) {
        return picked ? String.format(Locale.ROOT, "hue %.1f, lightness %.3f", hue, lightness) : "none";
    }

    /** The player preview's outfit as mcbridge shows it: "item bow, armor diamond", a null armor is "none". */
    public static String outfitValue(String item, String armor) {
        return "item " + item + ", armor " + (armor == null ? "none" : armor);
    }

    /**
     * {x, y, w, h} around the model's drawn part corners (x, y pairs in GUI px) clipped to the view, where a click can
     * land on it; the whole view when nothing was drawn or the model is out of it.
     */
    public static int[] modelBounds(List<float[]> partCorners, int viewX, int viewY, int viewW, int viewH) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (float[] corners : partCorners) {
            for (int i = 0; i + 1 < corners.length; i += 2) {
                minX = Math.min(minX, corners[i]);
                maxX = Math.max(maxX, corners[i]);
                minY = Math.min(minY, corners[i + 1]);
                maxY = Math.max(maxY, corners[i + 1]);
            }
        }
        int x0 = Math.max(viewX, (int) Math.floor(minX));
        int y0 = Math.max(viewY, (int) Math.floor(minY));
        int x1 = Math.min(viewX + viewW, (int) Math.ceil(maxX));
        int y1 = Math.min(viewY + viewH, (int) Math.ceil(maxY));
        if (!(minX <= maxX) || x1 <= x0 || y1 <= y0) {
            return new int[] {viewX, viewY, viewW, viewH};
        }
        return new int[] {x0, y0, x1 - x0, y1 - y0};
    }

    /** The search box, the resolution chips, the Previews toggle, and the Color chip and wheel when there are any. */
    public static void describeToolbar(PacksToolbar toolbar, List<Object> out) {
        GuiTextField search = toolbar.searchField();
        add(out, "search", TEXT, toolbar.searchLeft(), toolbar.top(), toolbar.searchWidth(), PacksToolbar.HEIGHT)
                .label("Search packs").value(search.getText()).selected(search.isFocused());
        for (ChipButton chip : toolbar.resolutionChips()) {
            addButton(out, chipId(chip.chip), TOGGLE, chip).selected(chip.isActive());
        }
        ChipButton previews = toolbar.previewsToggle();
        addButton(out, "previews", TOGGLE, previews).checked(PackView.settings().listPreviews);
        GuiButton colorChip = toolbar.colorChip();
        if (colorChip != null) {
            addButton(out, "color", BUTTON, colorChip);
        }
        ColorWheelPanel panel = toolbar.shownPanel();
        if (panel != null) {
            boolean picked = ColorWheelPanel.hasPick();
            add(out, "colorwheel", OTHER, panel.x(), panel.y(), ColorWheelPanel.WIDTH, ColorWheelPanel.HEIGHT)
                    .label("Pack color").value(pickValue(picked, ColorWheelPanel.hue(), ColorWheelPanel.lightness()));
            // The panel enables Clear only while drawing, so take enabled from the pick itself.
            addButton(out, "colorwheel:clear", BUTTON, panel.clearButton()).enabled(picked).parent("colorwheel");
        }
    }
}
