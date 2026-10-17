package io.github.as9pa.packorganizer.gui;

import io.github.as9pa.packorganizer.filter.ColorMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

/**
 * The "Pack color" panel: a hue/lightness wheel the user clicks or drags to pick a colour, a Clear
 * button and a note line. The pick is static session state, so it survives reopening the screen.
 */
public final class ColorWheelPanel {
    public static final int WIDTH = 120;
    public static final int HEIGHT = 152;
    private static final int PAD = 4;
    private static final int WHEEL_TOP = 24;
    private static final int CLEAR_WIDTH = 40;
    private static final int BACKDROP = 0x8C000000;
    private static final int POPUP_BACKDROP = 0xF0101010;
    private static final int POPUP_BORDER = 0xFF555555;
    private static final int TITLE_COLOUR = 0xFFFFFF;
    private static final int NOTE_COLOUR = 0xA0A0A0;
    private static final String IDLE_NOTE = "Click the wheel to sort";

    private static boolean picked;
    private static double pickHue;
    private static double pickLightness;
    private static double pickX;
    private static double pickY;
    private static ResourceLocation wheelTexture;

    private final Minecraft mc = Minecraft.getMinecraft();
    private int x;
    private int y;
    private GuiButton clearButton;
    private String note;
    private Runnable onChange;
    private boolean dragging;
    /** Drawn as a popup over the lists: opaque backdrop and a border instead of the docked tint. */
    private boolean popup;

    public ColorWheelPanel(int x, int y) {
        init(x, y);
    }

    /** Places the panel with its top-left corner at (x, y). */
    public void init(int x, int y) {
        this.x = x;
        this.y = y;
        clearButton = new GuiButton(0, x + WIDTH - PAD - CLEAR_WIDTH, y + 2, CLEAR_WIDTH, 20, "Clear");
        dragging = false;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public static boolean hasPick() {
        return picked;
    }

    /** Picked hue in degrees, clockwise from red at the top. Meaningless without {@link #hasPick()}. */
    public static double hue() {
        return pickHue;
    }

    /** Picked HSL lightness in [0, 1]. Meaningless without {@link #hasPick()}. */
    public static double lightness() {
        return pickLightness;
    }

    /** Drops the pick. Does not fire the change listener. */
    public static void clear() {
        picked = false;
    }

    /** Sets the note line (for example "12 close packs"); null or empty shows the idle hint. */
    public void setNote(String note) {
        this.note = note;
    }

    /** Called after the pick changes or is cleared through the panel. */
    public void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    /** Popup panels sit over the pack list, so they get an opaque backdrop the list cannot show through. */
    public void setPopup(boolean popup) {
        this.popup = popup;
    }

    public boolean isMouseOver(int mouseX, int mouseY) {
        return mouseX >= x && mouseY >= y && mouseX < x + WIDTH && mouseY < y + HEIGHT;
    }

    public void draw(int mouseX, int mouseY) {
        if (popup) {
            Gui.drawRect(x - 1, y - 1, x + WIDTH + 1, y + HEIGHT + 1, POPUP_BORDER);
            Gui.drawRect(x, y, x + WIDTH, y + HEIGHT, POPUP_BACKDROP);
        } else {
            Gui.drawRect(x, y, x + WIDTH, y + HEIGHT, BACKDROP);
        }
        FontRenderer font = mc.fontRendererObj;
        font.drawStringWithShadow("Pack color", x + PAD, y + 8, TITLE_COLOUR);

        clearButton.enabled = picked;
        clearButton.drawButton(mc, mouseX, mouseY);

        int wheelX = wheelLeft();
        int wheelY = wheelTop();
        mc.getTextureManager().bindTexture(wheel());
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        Gui.drawModalRectWithCustomSizedTexture(wheelX, wheelY, 0.0F, 0.0F, Geometry.SIZE, Geometry.SIZE,
                Geometry.SIZE, Geometry.SIZE);
        GlStateManager.disableBlend();

        if (picked) {
            int cx = wheelX + Geometry.toPixel(pickX);
            int cy = wheelY + Geometry.toPixel(pickY);
            int colour = 0xFF000000 | ColorMath.hslToRgb((float) pickHue, 1.0F, (float) pickLightness);
            Gui.drawRect(cx - 3, cy - 3, cx + 4, cy + 4, 0xFF000000);
            Gui.drawRect(cx - 2, cy - 2, cx + 3, cy + 3, 0xFFFFFFFF);
            Gui.drawRect(cx - 1, cy - 1, cx + 2, cy + 2, colour);
        }

        String shown = note == null || note.isEmpty() || !picked ? IDLE_NOTE : note;
        font.drawStringWithShadow(shown, x + PAD, wheelY + Geometry.SIZE + 4, NOTE_COLOUR);
    }

    /** Returns true when the click landed on the panel (consumed, even if it changed nothing). */
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        dragging = false;
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }
        if (button != 0) {
            return true;
        }
        clearButton.enabled = picked;
        if (clearButton.mousePressed(mc, mouseX, mouseY)) {
            clearButton.playPressSound(mc.getSoundHandler());
            clear();
            fireChange();
            return true;
        }
        double ox = Geometry.toOffset(mouseX - wheelLeft());
        double oy = Geometry.toOffset(mouseY - wheelTop());
        if (ColorMath.pixelToPick(ox, oy) != null) {
            dragging = true;
            pick(ox, oy);
        }
        return true;
    }

    /** Returns true while a drag that began on the wheel is in progress. */
    public boolean mouseDragged(int mouseX, int mouseY) {
        if (!dragging) {
            return false;
        }
        double[] offset = Geometry.clampToWheel(Geometry.toOffset(mouseX - wheelLeft()),
                Geometry.toOffset(mouseY - wheelTop()));
        pick(offset[0], offset[1]);
        return true;
    }

    private void pick(double ox, double oy) {
        double[] hl = ColorMath.pixelToPick(ox, oy);
        if (hl == null) {
            return;
        }
        boolean changed = !picked || hl[0] != pickHue || hl[1] != pickLightness;
        picked = true;
        pickHue = hl[0];
        pickLightness = hl[1];
        pickX = ox;
        pickY = oy;
        if (changed) {
            fireChange();
        }
    }

    private void fireChange() {
        if (onChange != null) {
            onChange.run();
        }
    }

    private int wheelLeft() {
        return x + PAD;
    }

    private int wheelTop() {
        return y + WHEEL_TOP;
    }

    private ResourceLocation wheel() {
        if (wheelTexture == null) {
            DynamicTexture texture = new DynamicTexture(Geometry.SIZE, Geometry.SIZE);
            int[] pixels = Geometry.wheelPixels();
            System.arraycopy(pixels, 0, texture.getTextureData(), 0, pixels.length);
            texture.updateDynamicTexture();
            wheelTexture = mc.getTextureManager().getDynamicTextureLocation("packorganizer_wheel", texture);
        }
        return wheelTexture;
    }

    /** Pure wheel geometry, kept free of Minecraft classes so tests can load it. */
    static final class Geometry {
        static final int SIZE = 112;
        static final double RADIUS = SIZE / 2.0;
        /** Drags past the rim stick just inside it, so the pick stays on the wheel. */
        private static final double RIM = 0.999;

        private Geometry() {}

        /** Pixel column or row inside the wheel square to an offset from its centre in wheel radii. */
        static double toOffset(int pixel) {
            return (pixel + 0.5 - RADIUS) / RADIUS;
        }

        /** Inverse of {@link #toOffset}: offset in wheel radii to a pixel inside the wheel square. */
        static int toPixel(double offset) {
            int p = (int) Math.floor(offset * RADIUS + RADIUS);
            return p < 0 ? 0 : p >= SIZE ? SIZE - 1 : p;
        }

        /** Pulls an offset outside the wheel back onto its rim along the same angle. */
        static double[] clampToWheel(double ox, double oy) {
            double r = Math.sqrt(ox * ox + oy * oy);
            if (r <= RIM) {
                return new double[] {ox, oy};
            }
            return new double[] {ox / r * RIM, oy / r * RIM};
        }

        /** SIZE x SIZE ARGB pixels: hue by angle (red at top), wheel lightness by radius, full saturation. */
        static int[] wheelPixels() {
            int[] pixels = new int[SIZE * SIZE];
            for (int py = 0; py < SIZE; py++) {
                for (int px = 0; px < SIZE; px++) {
                    double[] hl = ColorMath.pixelToPick(toOffset(px), toOffset(py));
                    if (hl != null) {
                        pixels[py * SIZE + px] = 0xFF000000 | ColorMath.hslToRgb((float) hl[0], 1.0F, (float) hl[1]);
                    }
                }
            }
            return pixels;
        }
    }
}
