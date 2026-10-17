package io.github.as9pa.packorganizer.gui;

import io.github.as9pa.packorganizer.EntryExtension;
import io.github.as9pa.packorganizer.Log;
import io.github.as9pa.packorganizer.PackOrganizer;
import io.github.as9pa.packorganizer.Settings;
import io.github.as9pa.packorganizer.filter.ModelSpec;
import io.github.as9pa.packorganizer.filter.PackPaths;
import io.github.as9pa.packorganizer.gui.preview.DevShots;
import io.github.as9pa.packorganizer.gui.preview.HeadFollow;
import io.github.as9pa.packorganizer.gui.preview.HeldItem;
import io.github.as9pa.packorganizer.gui.preview.ModelHitTest;
import io.github.as9pa.packorganizer.gui.preview.PackModel;
import io.github.as9pa.packorganizer.gui.preview.PlayerModelRenderer;
import io.github.as9pa.packorganizer.gui.preview.PreviewFramebuffer;
import io.github.as9pa.packorganizer.gui.preview.PreviewSim;
import io.github.as9pa.packorganizer.gui.preview.PreviewTextures;
import io.github.as9pa.packorganizer.gui.preview.SkinSource;
import io.github.as9pa.packorganizer.gui.preview.SkinTextures;
import io.github.as9pa.packorganizer.gui.preview.SkyBackdrop;
import io.github.as9pa.packorganizer.gui.preview.SpinSpring;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.ResourcePackListEntry;
import net.minecraft.client.resources.ResourcePackListEntryFound;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Cursor;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/**
 * The player preview right of the Selected list: a tinted panel with the armor buttons and Enchanted toggle on top,
 * the shown pack's name and the item buttons at the bottom, and the pack's sky and player model in the view box
 * between them. List rows offer the hovered pack each frame; with nothing hovered the panel keeps the last hovered
 * pack while the mouse is over it, else shows the top turned-on pack.
 */
public final class PlayerPreviewPanel extends Gui {
    /** Narrower screens have no room right of the lists; the screen then looks like 0.2.0. */
    public static final int MIN_SCREEN_WIDTH = 680;
    /** The docked color wheel's tint. */
    private static final int BACKDROP = 0x8C000000;
    private static final String TITLE = "Pack preview";
    private static final String DEFAULT_NAME = "Default";
    private static final ResourceLocation WIDGETS = new ResourceLocation("textures/gui/widgets.png");
    /** From the screen center: the Selected list ends at +204. */
    private static final int LEFT_FROM_CENTER = 208;
    private static final int RIGHT_INSET = 8;
    private static final int BOTTOM_INSET = 51;
    private static final int EDGE = 4;
    private static final int BUTTON = 20;
    private static final int BUTTON_GAP = 2;
    /** Between the last armor button and the Enchanted toggle. */
    private static final int TOGGLE_GAP = 4;
    /** The button rows are 20 tall with 2 above (top) or 4 below (bottom); the view box keeps 2 more clear. */
    private static final int VIEW_TOP = 24;
    private static final int VIEW_BOTTOM = 28;
    /** Longer side of a button icon, GUI px. */
    private static final int ICON = 14;
    /** widgets.png button strip rows: normal, and hovered (also the active button). */
    private static final int BUTTON_V = 66;
    private static final int BUTTON_V_LIT = 86;
    /** Instant health, the healing splash potion's color. */
    private static final int SPLASH_TINT = 0xF82423;
    /** With nothing hovered, the last hovered pack stays this long so the mouse can cross into the panel. */
    private static final long HOVER_GRACE_MS = 300;
    private static final float SPIN_PER_PX = 1.5F;
    /** A left press in the view that moves more than this (screen px) becomes a spin instead of a click. */
    private static final int SPIN_START_PX = 3;
    /** GuiIngame's crosshair, icons.png (0,0,16,16), drawn this far up-left of the pointer. */
    private static final int CROSSHAIR = 16;
    private static final int CROSSHAIR_OFFSET = 7;

    private enum Kind { ARMOR, ENCHANTED, ITEM }

    private static final class Button {
        final Kind kind;
        /** Armor material or item settings id; unused for the toggle. */
        final String id;
        int x;
        int y;

        Button(Kind kind, String id) {
            this.kind = kind;
            this.id = id;
        }
    }

    // Written by the list rows during a frame, read by draw at the end of the same frame.
    private static boolean hoverOffered;
    private static File hoverFile;
    private static String hoverName;
    /** Shown instead of the system cursor over the view; made once, null until then or where unsupported. */
    private static Cursor emptyCursor;
    private static boolean cursorFailed;

    private final Minecraft mc;
    private final PlayerModelRenderer renderer = new PlayerModelRenderer();
    private final PreviewFramebuffer framebuffer = new PreviewFramebuffer();
    private final PlayerModelRenderer.ModelPose pose = new PlayerModelRenderer.ModelPose();
    private final HeadFollow head = new HeadFollow();
    private final SpinSpring spin = new SpinSpring();
    private final PreviewSim sim = new PreviewSim(new Random());
    private final List<Button> topRow = new ArrayList<>();
    private final List<Button> itemRow = new ArrayList<>();
    private final List<Button> buttons = new ArrayList<>();
    /** Opaque bounds per item image; weak so a released pack's images can go. */
    private final Map<BufferedImage, int[]> iconBounds = new WeakHashMap<>();
    private final long startMs = Minecraft.getSystemTime();

    private List<ResourcePackListEntry> selected = Collections.emptyList();
    private int x;
    private int y;
    private int width;
    private int height;
    private boolean wide;
    private int viewX;
    private int viewY;
    private int viewW;
    private int viewH;
    private boolean titleShown;
    private int nameWidth;

    /** The last hovered pack: a null file is the Default row. */
    private boolean hasLast;
    private File lastFile;
    private String lastName;
    private long lastHoverMs;
    /** The model on screen; kept while the next pack loads so the panel never blanks. */
    private PackModel shown;
    private String shownName = "";
    private HeldItem item;
    private int ticks;
    private long lastFrameNanos = -1;
    private boolean failed;
    /** True when {@link #shown} is the model for this frame's pack, not one kept while it loads. */
    private boolean shownCurrent;
    private int screenWidth = 1;
    private int screenHeight = 1;
    /** A left press in the view waiting for release, or turned into a spin once it moved far enough. */
    private boolean pressed;
    private int pressRawX;
    private int pressRawY;
    private float lastDragX;
    private boolean cursorHidden;

    // DevShots only: a forced pack (none forced is nothing hovered), mouse and spin; never set in a normal install.
    private boolean debug;
    private boolean debugForced;
    private File debugFile;
    private String debugName;
    private int debugMouseX;
    private int debugMouseY;
    private float debugSpin;
    private long debugSkyStart;

    public PlayerPreviewPanel(GuiScreen screen) {
        this.mc = screen.mc != null ? screen.mc : Minecraft.getMinecraft();
        for (String material : Settings.ARMORS) {
            topRow.add(new Button(Kind.ARMOR, material));
        }
        topRow.add(new Button(Kind.ENCHANTED, null));
        for (String id : Settings.ITEMS) {
            itemRow.add(new Button(Kind.ITEM, id));
        }
        buttons.addAll(topRow);
        buttons.addAll(itemRow);
        Settings settings = PackOrganizer.settings();
        item = HeldItem.fromId(settings.previewItem);
        sim.setItem(item);
        sim.setEnchanted(settings.previewEnchanted);
        DevShots.attach(this);
    }

    /** A list row drawn hovered this frame; a null file is the Default row. */
    public static void offerHover(File pack, String formattedName) {
        hoverOffered = true;
        hoverFile = pack;
        hoverName = formattedName;
    }

    /** Called at the start of every screen frame, before the lists draw their rows. */
    public static void resetHover() {
        hoverOffered = false;
        hoverFile = null;
        hoverName = null;
    }

    /** The screen's Selected entries, top first; vanilla replaces the list on some initGui calls. */
    public void setSelected(List<ResourcePackListEntry> selected) {
        this.selected = selected == null ? Collections.<ResourcePackListEntry>emptyList() : selected;
    }

    public void layout(int screenWidth, int screenHeight) {
        this.screenWidth = Math.max(1, screenWidth);
        this.screenHeight = Math.max(1, screenHeight);
        wide = screenWidth >= MIN_SCREEN_WIDTH;
        x = screenWidth / 2 + LEFT_FROM_CENTER;
        y = PacksToolbar.TOOLBAR_Y;
        width = screenWidth - RIGHT_INSET - x;
        height = screenHeight - BOTTOM_INSET - y;
        viewX = x + EDGE;
        viewY = y + VIEW_TOP;
        viewW = width - 2 * EDGE;
        viewH = height - VIEW_TOP - VIEW_BOTTOM;
        placeRow(topRow, y + 2, topRow.size() - 2);
        placeRow(itemRow, y + height - BUTTON - EDGE, -1);
        FontRenderer font = mc.fontRendererObj;
        titleShown = x + EDGE + font.getStringWidth(TITLE) + 2 <= topRow.get(0).x;
        nameWidth = itemRow.get(0).x - 2 - (x + EDGE);
    }

    /**
     * Right-aligns a row 4 px from the panel edge with 2 px gaps ({@link #TOGGLE_GAP} after {@code wideGapAfter});
     * a panel too narrow for that gets the buttons touching and centered.
     */
    private void placeRow(List<Button> row, int top, int wideGapAfter) {
        int n = row.size();
        boolean fits = rowSpan(n, BUTTON_GAP, wideGapAfter) <= width - 2 * EDGE;
        int gap = fits ? BUTTON_GAP : 0;
        int span = rowSpan(n, gap, fits ? wideGapAfter : -1);
        int bx = fits ? x + width - EDGE - span : x + (width - span) / 2;
        for (int i = 0; i < n; i++) {
            Button b = row.get(i);
            b.x = bx;
            b.y = top;
            bx += BUTTON + (i == wideGapAfter && fits ? TOGGLE_GAP : gap);
        }
    }

    private static int rowSpan(int n, int gap, int wideGapAfter) {
        return n * BUTTON + (n - 1) * gap + (wideGapAfter >= 0 ? TOGGLE_GAP - gap : 0);
    }

    public boolean visible() {
        return wide && PackOrganizer.settings().playerPreview;
    }

    public boolean contains(int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public void draw(int mouseX, int mouseY, float partial) {
        if (!visible()) {
            hideCursor(false);
            return;
        }
        if (debug) {
            mouseX = debugMouseX;
            mouseY = debugMouseY;
            hoverOffered = debugForced;
            hoverFile = debugFile;
            hoverName = debugName;
            hasLast &= debugForced;
        }
        long now = System.nanoTime();
        float seconds = lastFrameNanos < 0 ? 0F : (now - lastFrameNanos) / 1e9F;
        boolean firstFrame = lastFrameNanos < 0;
        lastFrameNanos = now;
        choosePack(mouseX, mouseY);

        Gui.drawRect(x, y, x + width, y + height, BACKDROP);
        int scale = new ScaledResolution(mc).getScaleFactor();
        drawView(mouseX, mouseY, partial, seconds, firstFrame, scale);
        drawButtons(mouseX, mouseY, scale);
        FontRenderer font = mc.fontRendererObj;
        if (titleShown) {
            font.drawStringWithShadow(TITLE, x + EDGE, y + 8, 0xFFFFFF);
        }
        if (nameWidth > 0) {
            // Centered on the bottom button row like the title on the top one.
            font.drawStringWithShadow(font.trimStringToWidth(shownName, nameWidth), x + EDGE, y + height - 18, 0xFFFFFF);
        }
        // Over the view (not a button), or while spinning, the pack's crosshair stands in for the cursor.
        boolean crosshair = shown != null && (spin.dragging() || (inView(mouseX, mouseY) && buttonAt(mouseX, mouseY) == null));
        if (!debug) {
            hideCursor(crosshair);
        }
        if (crosshair) {
            drawCrosshair(mouseX, mouseY);
        }
    }

    private boolean inView(int mouseX, int mouseY) {
        return mouseX >= viewX && mouseX < viewX + viewW && mouseY >= viewY && mouseY < viewY + viewH;
    }

    /** GuiIngame's crosshair: inverts what is under it, texels under alpha 0.1 are dropped. */
    private void drawCrosshair(int mouseX, int mouseY) {
        int texture = shown.texture(ModelSpec.ICONS);
        if (texture == 0) {
            return;
        }
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        GlStateManager.disableDepth();   // the unmultisampled model leaves its depth in front of z 0
        GlStateManager.bindTexture(texture);
        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(775, 769, 1, 0);
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        Gui.drawModalRectWithCustomSizedTexture(mouseX - CROSSHAIR_OFFSET, mouseY - CROSSHAIR_OFFSET, 0, 0,
                CROSSHAIR, CROSSHAIR, 256, 256);
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        if (depth) {
            GlStateManager.enableDepth();
        }
    }

    /** Hides the system cursor for the crosshair or brings it back; without native cursor support it stays. */
    private void hideCursor(boolean hide) {
        if (hide == cursorHidden || (hide && cursorFailed)) {
            return;
        }
        try {
            if (hide) {
                Cursor empty = emptyCursor();
                if (empty == null) {
                    return;
                }
                Mouse.setNativeCursor(empty);
            } else {
                Mouse.setNativeCursor(null);
            }
            cursorHidden = hide;
        } catch (LWJGLException | RuntimeException e) {
            if (!cursorFailed) {
                cursorFailed = true;
                Log.LOG.warn("Player preview could not change the mouse cursor", e);
            }
        }
    }

    /** An empty 1x1 native cursor, made on first use; null where transparent native cursors are not supported. */
    private static Cursor emptyCursor() throws LWJGLException {
        if (emptyCursor == null && (Cursor.getCapabilities() & Cursor.CURSOR_ONE_BIT_TRANSPARENCY) != 0) {
            emptyCursor = new Cursor(1, 1, 0, 0, 1, BufferUtils.createIntBuffer(1), null);
        }
        return emptyCursor;
    }

    /** Hovered pack, else the last hovered while the mouse is over the panel (or just left a row), else the top on. */
    private void choosePack(int mouseX, int mouseY) {
        long now = Minecraft.getSystemTime();
        if (hoverOffered) {
            hasLast = true;
            lastFile = hoverFile;
            lastName = hoverName;
            lastHoverMs = now;
        } else if (hasLast && !contains(mouseX, mouseY) && now - lastHoverMs > HOVER_GRACE_MS) {
            hasLast = false;
        } else if (hasLast && contains(mouseX, mouseY)) {
            lastHoverMs = now;
        }
        File file;
        String name;
        if (hasLast) {
            file = lastFile;
            name = lastName;
        } else {
            file = null;
            name = DEFAULT_NAME;
            for (ResourcePackListEntry entry : selected) {
                if (!(entry instanceof ResourcePackListEntryFound)) {
                    continue;
                }
                ResourcePackRepository.Entry pack = ((ResourcePackListEntryFound) entry).func_148318_i();
                if (pack instanceof EntryExtension && ((EntryExtension) pack).packorganizer$file() != null) {
                    file = ((EntryExtension) pack).packorganizer$file();
                    name = PackPaths.shortName(pack.getResourcePackName());
                    break;
                }
            }
        }
        PackModel next = file == null ? PreviewTextures.defaults() : PreviewTextures.get(file);
        if (next != null) {
            shown = next;
        }
        shownCurrent = next != null;
        shownName = name == null ? "" : panelName(name);
    }

    /**
     * The name as the mock shows it in the narrow name slot: without a ".zip" ending and without the leading "!"
     * and spaces packs use to sort first (color codes kept), so "!          Lotus.zip" shows as "Lotus".
     */
    static String panelName(String name) {
        if (name.regionMatches(true, Math.max(0, name.length() - 4), ".zip", 0, 4) && name.length() > 4) {
            name = name.substring(0, name.length() - 4);
        }
        StringBuilder codes = new StringBuilder();
        int i = 0;
        while (i < name.length()) {
            char c = name.charAt(i);
            if (c == '§' && i + 1 < name.length()) {
                codes.append(c).append(name.charAt(i + 1));
                i += 2;
            } else if (c == '!' || Character.isWhitespace(c)) {
                i++;
            } else {
                break;
            }
        }
        return i == 0 || i == name.length() ? name : codes + name.substring(i);
    }

    /** Sky and model inside the multisampled target, or scissored straight to the screen without it. */
    private void drawView(int mouseX, int mouseY, float partial, float seconds, boolean firstFrame, int scale) {
        if (viewW <= 0 || viewH <= 0) {
            return;
        }
        // The mock's layout: model sized from the view box, centered, feet 12% above its bottom.
        float block = Math.min(viewW * 0.45F, viewH * 0.30F);
        float centerX = viewX + viewW / 2F;
        float feetY = viewY + viewH - 0.12F * viewH;
        float eyeY = feetY - block * 50F / 30F;
        float norm = 30F / block;   // inventory units are 30 per block
        head.target((centerX - mouseX) * norm, (eyeY - mouseY) * norm);
        if (firstFrame) {
            head.snap();
        } else {
            head.step(seconds);
        }
        spin.step(seconds);
        updatePose(partial);
        // Projectiles leave along the eased head direction, turned with the spin (yaw 0 faces +z).
        sim.setAim((float) Math.toRadians(pose.spin - pose.headYaw), (float) Math.toRadians(pose.pitch));

        boolean multisampled = framebuffer.begin(viewX, viewY, viewW, viewH);
        try {
            if (!multisampled) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
                GL11.glScissor(viewX * scale, mc.displayHeight - (viewY + viewH) * scale, viewW * scale, viewH * scale);
                GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
            }
            Settings settings = PackOrganizer.settings();
            SkyBackdrop.draw(shown, viewX, viewY, viewW, viewH, Minecraft.getSystemTime() - (debug ? debugSkyStart : startMs), settings.skyCycle);
            if (shown != null) {
                renderer.render(shown, pose, skin(settings), centerX, feetY, block);
            }
        } catch (RuntimeException e) {
            if (!failed) {
                failed = true;
                Log.LOG.warn("Player preview failed to draw", e);
            }
        } finally {
            if (multisampled) {
                framebuffer.end();
            } else {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }
        }
    }

    private void updatePose(float partial) {
        Settings settings = PackOrganizer.settings();
        pose.bodyYaw = head.bodyYaw();
        pose.headYaw = head.headYaw();
        pose.pitch = head.pitch();
        pose.spin = spin.angle() + debugSpin;
        pose.swing = sim.swing().progress(partial);
        pose.blocking = sim.blocking();
        pose.aimingBow = sim.aimingBow();
        pose.bowFrame = sim.bowFrame();
        pose.rodCast = sim.rodCast();
        pose.hurt = sim.hurt(partial);
        pose.armor = settings.previewArmor;
        pose.item = item;
        pose.enchanted = settings.previewEnchanted;
        pose.ticks = ticks + partial;
    }

    private static SkinTextures skin(Settings settings) {
        SkinTextures skin = SkinSource.get();
        if (skin != null && skin.cape != 0 && !settings.showCape) {
            skin = new SkinTextures(skin.skin, skin.slim, 0);
        }
        return skin;
    }

    private void drawButtons(int mouseX, int mouseY, int scale) {
        Button hovered = buttonAt(mouseX, mouseY);
        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.blendFunc(770, 771);
        // GuiButton: the left and right 10 px of the 200 px strip.
        mc.getTextureManager().bindTexture(WIDGETS);
        for (Button b : buttons) {
            int v = b == hovered || active(b) ? BUTTON_V_LIT : BUTTON_V;
            drawTexturedModalRect(b.x, b.y, 0, v, BUTTON / 2, BUTTON);
            drawTexturedModalRect(b.x + BUTTON / 2, b.y, 200 - BUTTON / 2, v, BUTTON / 2, BUTTON);
        }
        if (shown != null) {
            for (Button b : buttons) {
                drawIcon(shown, b, scale);
            }
        }
        GlStateManager.color(1F, 1F, 1F, 1F);
    }

    private boolean active(Button b) {
        Settings settings = PackOrganizer.settings();
        switch (b.kind) {
            case ARMOR:
                return b.id.equals(settings.previewArmor);
            case ENCHANTED:
                return settings.previewEnchanted;
            default:
                return b.id.equals(settings.previewItem);
        }
    }

    /** The button's icon trimmed to its drawn pixels, longer side {@link #ICON} GUI px, centered on whole pixels. */
    private void drawIcon(PackModel pack, Button b, int scale) {
        String path;
        String under = null;
        String over = null;
        float[] uv;
        if (b.kind == Kind.ARMOR) {
            path = ModelSpec.helmet(b.id);
            if ("leather".equals(b.id)) {
                over = ModelSpec.LEATHER_HELMET_OVERLAY;
            }
            BufferedImage img = pack.image(path);
            int[] r = pack.helmetIconBounds(b.id);
            if (img == null || r[2] <= 0 || r[3] <= 0) {
                return;
            }
            uv = fractions(r, img);
        } else {
            path = b.kind == Kind.ENCHANTED ? ModelSpec.BOOK : itemPath(HeldItem.fromId(b.id));
            if (b.kind == Kind.ITEM && HeldItem.fromId(b.id) == HeldItem.SPLASH_POTION) {
                under = ModelSpec.POTION_OVERLAY;
            }
            uv = opaqueFractions(pack, path);
            if (uv == null) {
                return;
            }
            float[] underUv = under == null ? null : opaqueFractions(pack, under);
            if (underUv != null) {
                uv = new float[] {Math.min(uv[0], underUv[0]), Math.min(uv[1], underUv[1]),
                        Math.max(uv[2], underUv[2]), Math.max(uv[3], underUv[3])};
            }
        }
        BufferedImage img = pack.image(path);
        float texW = (uv[2] - uv[0]) * img.getWidth();
        float texH = (uv[3] - uv[1]) * img.getHeight();
        float n = ICON * scale / Math.max(texW, texH);
        int pw = Math.round(texW * n);
        int ph = Math.round(texH * n);
        float x0 = (float) Math.floor((b.x + BUTTON / 2F) * scale - pw / 2F) / scale;
        float y0 = (float) Math.floor((b.y + BUTTON / 2F) * scale - ph / 2F) / scale;
        float x1 = x0 + pw / (float) scale;
        float y1 = y0 + ph / (float) scale;
        if (under != null) {
            GlStateManager.color((SPLASH_TINT >> 16 & 255) / 255F, (SPLASH_TINT >> 8 & 255) / 255F,
                    (SPLASH_TINT & 255) / 255F, 1F);
            layer(pack.texture(under), x0, y0, x1, y1, uv);
            GlStateManager.color(1F, 1F, 1F, 1F);
        }
        layer(pack.texture(path), x0, y0, x1, y1, uv);
        if (over != null) {
            layer(pack.texture(over), x0, y0, x1, y1, uv);
        }
    }

    /** The texture the button shows for an item; the sword follows the armor. */
    private static String itemPath(HeldItem held) {
        switch (held) {
            case BOW:
                return ModelSpec.BOW;
            case ENDER_PEARL:
                return ModelSpec.ENDER_PEARL;
            case GOLDEN_APPLE:
                return ModelSpec.GOLDEN_APPLE;
            case SPLASH_POTION:
                return ModelSpec.POTION_SPLASH;
            case FISHING_ROD:
                return ModelSpec.ROD;
            default:
                return ModelSpec.sword(PackOrganizer.settings().previewArmor);
        }
    }

    /** {u0, v0, u1, v1} of an image's opaque pixels (the whole image when none are), or null without the image. */
    private float[] opaqueFractions(PackModel pack, String path) {
        BufferedImage img = pack.image(path);
        if (img == null) {
            return null;
        }
        int[] r = iconBounds.get(img);
        if (r == null) {
            r = opaqueBounds(img);
            iconBounds.put(img, r);
        }
        return fractions(r, img);
    }

    private static int[] opaqueBounds(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int[] argb = img.getRGB(0, 0, w, h, null, 0, w);
        int x0 = w;
        int y0 = h;
        int x1 = -1;
        int y1 = -1;
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                if (argb[j * w + i] >>> 24 == 0) {
                    continue;
                }
                x0 = Math.min(x0, i);
                y0 = Math.min(y0, j);
                x1 = Math.max(x1, i);
                y1 = Math.max(y1, j);
            }
        }
        return x1 < 0 ? new int[] {0, 0, w, h} : new int[] {x0, y0, x1 - x0 + 1, y1 - y0 + 1};
    }

    private static float[] fractions(int[] r, BufferedImage img) {
        float w = img.getWidth();
        float h = img.getHeight();
        return new float[] {r[0] / w, r[1] / h, (r[0] + r[2]) / w, (r[1] + r[3]) / h};
    }

    private void layer(int texture, float x0, float y0, float x1, float y1, float[] uv) {
        if (texture == 0) {
            return;
        }
        GlStateManager.bindTexture(texture);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        wr.pos(x0, y1, zLevel).tex(uv[0], uv[3]).endVertex();
        wr.pos(x1, y1, zLevel).tex(uv[2], uv[3]).endVertex();
        wr.pos(x1, y0, zLevel).tex(uv[2], uv[1]).endVertex();
        wr.pos(x0, y0, zLevel).tex(uv[0], uv[1]).endVertex();
        tessellator.draw();
    }

    private Button buttonAt(int mouseX, int mouseY) {
        for (Button b : buttons) {
            if (mouseX >= b.x && mouseY >= b.y && mouseX < b.x + BUTTON && mouseY < b.y + BUTTON) {
                return b;
            }
        }
        return null;
    }

    /**
     * A left click on a panel button picks the armor, item or Enchanted; true when the panel took the click. A press
     * in the view waits for release (a click, or a spin once it moves), and anywhere else it swings the arm.
     */
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (!visible() || mouseButton != 0) {
            return false;
        }
        spin.release(System.nanoTime());   // a release lost outside the window ends the old spin
        Button b = buttonAt(mouseX, mouseY);
        if (b == null) {
            if (inView(mouseX, mouseY)) {
                pressed = true;
                pressRawX = Mouse.getEventX();
                pressRawY = Mouse.getEventY();
                lastDragX = guiX(pressRawX);
            } else {
                pressed = false;
                sim.leftClick(false);
            }
            return false;
        }
        pressed = false;
        mc.getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("gui.button.press"), 1.0F));
        Settings settings = PackOrganizer.settings();
        switch (b.kind) {
            case ARMOR:
                settings.previewArmor = b.id;
                break;
            case ENCHANTED:
                settings.previewEnchanted = !settings.previewEnchanted;
                sim.setEnchanted(settings.previewEnchanted);
                break;
            default:
                settings.previewItem = b.id;
                item = HeldItem.fromId(b.id);
                sim.setItem(item);
                break;
        }
        // Written to disk with the rest of the settings when the screen closes.
        return true;
    }

    /** The right button went down anywhere on the screen: use the held item. */
    public void rightPressed() {
        if (visible()) {
            sim.rightPress();
        }
    }

    /**
     * The mouse moved with a button held (during GuiScreen's mouse event): a left press in the view that moves more
     * than {@link #SPIN_START_PX} turns into a spin at {@link #SPIN_PER_PX} degrees per GUI px.
     */
    public void mouseDragged(int mouseButton) {
        if (!pressed || mouseButton != 0) {
            return;
        }
        int rawX = Mouse.getEventX();
        int rawY = Mouse.getEventY();
        if (!spin.dragging()) {
            int dx = rawX - pressRawX;
            int dy = rawY - pressRawY;
            if (dx * dx + dy * dy <= SPIN_START_PX * SPIN_START_PX) {
                return;
            }
            spin.press();
        }
        float gx = guiX(rawX);
        spin.drag((gx - lastDragX) * SPIN_PER_PX, System.nanoTime());
        lastDragX = gx;
    }

    /**
     * A button came up (during GuiScreen's mouse event). Right ends the item use. Left ends a spin, or else is a
     * click in the view: with the sword on the model (not the held item) a hit, otherwise a swing.
     */
    public void mouseReleased(int mouseButton) {
        if (mouseButton == 1) {
            sim.rightRelease();
            return;
        }
        if (mouseButton != 0 || !pressed) {
            return;
        }
        pressed = false;
        if (spin.dragging()) {
            spin.release(System.nanoTime());
            return;
        }
        if (!visible()) {
            return;
        }
        int rawX = Mouse.getEventX();
        int rawY = Mouse.getEventY();
        boolean onModel = item == HeldItem.SWORD && ModelHitTest.hits(renderer.lastPartCorners(), guiX(rawX), guiY(rawY));
        sim.leftClick(onModel);
    }

    /** GUI x of the centre of a display pixel column, unrounded, scaled the way GuiScreen scales mouse events. */
    private float guiX(int rawX) {
        return (rawX + 0.5F) * screenWidth / Math.max(1, mc.displayWidth);
    }

    /** GUI y (down from the top) of the centre of a display pixel row given bottom up, as LWJGL reports it. */
    private float guiY(int rawY) {
        return (mc.displayHeight - rawY - 0.5F) * screenHeight / Math.max(1, mc.displayHeight);
    }

    /** One client tick: item use, effects, projectiles and the arm swing. */
    public void tick() {
        if (!visible()) {
            return;
        }
        ticks++;
        sim.tick();
    }

    /** DevShots only: forces the shown pack ({@code forced} false is nothing hovered), the mouse and a spin. */
    public void debugOverride(boolean forced, File pack, String name, int mouseX, int mouseY, float spinDeg) {
        debug = true;
        debugForced = forced;
        debugFile = pack;
        debugName = name;
        debugMouseX = mouseX;
        debugMouseY = mouseY;
        debugSpin = spinDeg;
        debugSkyStart = Minecraft.getSystemTime();   // each state's sky starts turning from the mock's t=0
    }

    /** DevShots only: holds the item as if its button was clicked. */
    public void debugItem(HeldItem held) {
        PackOrganizer.settings().previewItem = held.id;
        item = held;
        sim.setItem(held);
    }

    /** DevShots only: turns Enchanted on or off as if its button was clicked. */
    public void debugEnchanted(boolean on) {
        PackOrganizer.settings().previewEnchanted = on;
        sim.setEnchanted(on);
    }

    /** DevShots only: the right button goes down or up. */
    public void debugUse(boolean down) {
        if (down) {
            sim.rightPress();
        } else {
            sim.rightRelease();
        }
    }

    /** DevShots only: a sword click lands on the model. */
    public void debugHit() {
        sim.leftClick(true);
    }

    /** DevShots only: the model for this frame's pack, or null while it loads. */
    public PackModel debugModel() {
        return shownCurrent ? shown : null;
    }

    /** DevShots only: the panel rect {x, y, width, height} in GUI px. */
    public int[] debugBounds() {
        return new int[] {x, y, width, height};
    }

    /** Frees the framebuffer and every preview texture and brings the cursor back; call when the screen closes. */
    public void close() {
        hideCursor(false);
        pressed = false;
        DevShots.detach(this);
        framebuffer.delete();
        PreviewTextures.clear();
        shown = null;
        iconBounds.clear();
    }
}
