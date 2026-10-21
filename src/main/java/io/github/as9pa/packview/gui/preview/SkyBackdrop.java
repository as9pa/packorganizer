package io.github.as9pa.packview.gui.preview;

import io.github.as9pa.packview.PackView;
import io.github.as9pa.packview.filter.SkyLayer;
import java.nio.IntBuffer;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.util.glu.Project;

/**
 * The hovered pack's custom sky behind the preview model, as mock {@code buildSky} and {@code frame}: a cube seen
 * from inside with a 70 degree camera tilted 10 degrees up and slowly turning, over the vanilla sky color for the
 * time of day. Layers draw in order, each faded by its own window: {@code add} adds the image scaled by its
 * brightness, anything else alpha blends with the brightness as alpha.
 *
 * <p>Draws into a GUI rect whatever the current viewport is, so it works inside {@link PreviewFramebuffer} (whose
 * viewport is offset and scaled to the target) and on screen. Depth writes are off, so the model drawn next covers
 * it. GL calls only, so client thread only.
 */
public final class SkyBackdrop {
    private static final float FOV = 70f;
    private static final float PITCH = 10f;
    private static final float NEAR = 0.01f;
    private static final float FAR = 10f;

    /**
     * Tile of the 3x2 sky image per cube face (top row: bottom, top, horizon; bottom row: three horizons), in the
     * order of {@link #FACES}.
     */
    private static final int[] TILES = {3, 4, 5, 2, 1, 0};
    /**
     * Cube face corners where the tile's top-left, top-right, bottom-right and bottom-left texels go, as mock
     * {@code skyFaces}: the ring is -Z, +X, +Z, -X, the top tile is turned 270 degrees and the bottom one 90 (found by
     * matching edge pixels). Corners are exact +-1 so neighbouring faces share edges: no gap, and no overlap strip
     * that would blend twice while a layer fades.
     */
    private static final int[][][] FACES = {
        {{-1, 1, -1}, {1, 1, -1}, {1, -1, -1}, {-1, -1, -1}},
        {{1, 1, -1}, {1, 1, 1}, {1, -1, 1}, {1, -1, -1}},
        {{1, 1, 1}, {-1, 1, 1}, {-1, -1, 1}, {1, -1, 1}},
        {{-1, 1, 1}, {-1, 1, -1}, {-1, -1, -1}, {-1, -1, 1}},
        {{-1, 1, -1}, {-1, 1, 1}, {1, 1, 1}, {1, 1, -1}},
        {{1, -1, -1}, {1, -1, 1}, {-1, -1, 1}, {-1, -1, -1}},
    };
    /** Tile corner per face corner, as (right, down): top-left, top-right, bottom-right, bottom-left. */
    private static final int[][] CORNER_UV = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};

    private static final IntBuffer QUERY = BufferUtils.createIntBuffer(16);

    private SkyBackdrop() {}

    /**
     * Draws the pack's sky into the GUI rect at {@code ms} since the preview started; {@code cycle} false freezes the
     * time at noon. Draws nothing for a null pack, a pack without sky layers, or with Pack sky backdrop off.
     */
    public static void draw(PackModel pack, int x, int y, int w, int h, long ms, boolean cycle) {
        if (pack == null || w <= 0 || h <= 0 || !PackView.settings().skyBackdrop) return;
        List<SkyLayer> layers = pack.sky();
        if (layers.isEmpty()) return;

        QUERY.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, QUERY);
        int vx = QUERY.get(0), vy = QUERY.get(1), vw = QUERY.get(2), vh = QUERY.get(3);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        int blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean fog = GL11.glIsEnabled(GL11.GL_FOG);
        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean alphaTest = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean texture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);

        // The GUI projection spans the whole viewport, so the rect's pixels follow from the GUI scale times the
        // viewport pixels per window pixel (PreviewFramebuffer.SUPERSAMPLE inside its target, 1 on screen).
        Minecraft mc = Minecraft.getMinecraft();
        int scale = new ScaledResolution(mc).getScaleFactor();
        int sx = scale * vw / Math.max(1, mc.displayWidth);
        int sy = scale * vh / Math.max(1, mc.displayHeight);
        GlStateManager.viewport(vx + x * sx, vy + vh - (y + h) * sy, w * sx, h * sy);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        try {
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            GlStateManager.disableFog();
            GlStateManager.disableLighting();
            GlStateManager.disableAlpha();
            GlStateManager.disableCull();

            int minute = SkyClock.minuteOfDay(ms, cycle);
            fillSkyColor(SkyClock.skyColor(minute));

            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            Project.gluPerspective(FOV, w / (float) h, NEAR, FAR);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.rotate(-PITCH, 1f, 0f, 0f);
            GlStateManager.rotate(-SkyClock.rotation(ms), 0f, 1f, 0f);

            GlStateManager.enableBlend();
            GlStateManager.enableTexture2D();
            float fade = pack.skyFade();
            for (int i = 0; i < layers.size(); i++) {
                drawLayer(pack, i, layers.get(i), minute, fade);
            }
        } finally {
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.popMatrix();
            GlStateManager.viewport(vx, vy, vw, vh);
            if (blend) {
                GlStateManager.enableBlend();
            } else {
                GlStateManager.disableBlend();
            }
            GlStateManager.tryBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
            if (depthTest) {
                GlStateManager.enableDepth();
            } else {
                GlStateManager.disableDepth();
            }
            GlStateManager.depthMask(depthMask);
            if (fog) {
                GlStateManager.enableFog();
            } else {
                GlStateManager.disableFog();
            }
            if (lighting) {
                GlStateManager.enableLighting();
            } else {
                GlStateManager.disableLighting();
            }
            if (alphaTest) {
                GlStateManager.enableAlpha();
            } else {
                GlStateManager.disableAlpha();
            }
            if (cull) {
                GlStateManager.enableCull();
            } else {
                GlStateManager.disableCull();
            }
            if (texture) {
                GlStateManager.enableTexture2D();
            } else {
                GlStateManager.disableTexture2D();
            }
            // The vertex colors leave the current color unknown to the state cache.
            GlStateManager.resetColor();
            GlStateManager.color(1f, 1f, 1f, 1f);
        }
    }

    /** The vanilla sky color over the whole viewport, opaque, with identity matrices. */
    private static void fillSkyColor(int rgb) {
        float r = (rgb >> 16 & 0xFF) / 255f, g = (rgb >> 8 & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        GlStateManager.disableBlend();
        GlStateManager.disableTexture2D();
        WorldRenderer wr = Tessellator.getInstance().getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(-1, 1, 0).color(r, g, b, 1f).endVertex();
        wr.pos(1, 1, 0).color(r, g, b, 1f).endVertex();
        wr.pos(1, -1, 0).color(r, g, b, 1f).endVertex();
        wr.pos(-1, -1, 0).color(r, g, b, 1f).endVertex();
        Tessellator.getInstance().draw();
    }

    /**
     * One layer at its brightness for the minute. While the sharp image fades in over the shrunk one, both draw so
     * the result is the shrunk image weighted {@code 1 - fade} plus the sharp one weighted {@code fade}.
     */
    private static void drawLayer(PackModel pack, int index, SkyLayer layer, int minute, float fade) {
        float bright = layer.brightness(minute);
        if (bright <= 0) return;
        int sharp = pack.skyTexture(index);
        if (sharp == 0) return;
        int low = pack.skyLowTexture(index);
        if ("add".equals(layer.blend)) {
            // Destination alpha stays as the sky color left it.
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE);
            if (low != 0) {
                drawCube(low, bright * (1 - fade), 1f);
                drawCube(sharp, bright * fade, 1f);
            } else {
                drawCube(sharp, bright, 1f);
            }
        } else {
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ZERO, GL11.GL_ONE);
            if (low != 0) {
                // Two alpha blends a1 then a2 leave the destination weighted (1 - a1)(1 - a2), the shrunk image
                // a1(1 - a2) and the sharp one a2; these alphas make that 1 - b, b(1 - fade) and b * fade.
                float a2 = bright * fade;
                float rest = 1 - a2;
                float a1 = rest <= 1e-6f ? 1f : bright * (1 - fade) / rest;
                drawCube(low, 1f, a1);
                drawCube(sharp, 1f, a2);
            } else {
                drawCube(sharp, 1f, bright);
            }
        }
    }

    /** The six faces from one 3x2 sky image, with every vertex colored (scale, scale, scale, alpha). */
    private static void drawCube(int textureId, float scale, float alpha) {
        if (scale <= 0 || alpha <= 0) return;
        GlStateManager.bindTexture(textureId);
        int texW = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int texH = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        if (texW <= 0 || texH <= 0) return;
        // Half a texel in from each tile edge so smooth filtering never pulls in the neighbouring tile.
        double insetU = 0.5 / texW, insetV = 0.5 / texH;
        WorldRenderer wr = Tessellator.getInstance().getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        for (int f = 0; f < FACES.length; f++) {
            int tile = TILES[f];
            double u0 = (tile % 3) / 3.0 + insetU, u1 = (tile % 3 + 1) / 3.0 - insetU;
            double v0 = (tile / 3) / 2.0 + insetV, v1 = (tile / 3 + 1) / 2.0 - insetV;
            for (int c = 0; c < 4; c++) {
                int[] p = FACES[f][c];
                wr.pos(p[0], p[1], p[2])
                        .tex(CORNER_UV[c][0] == 0 ? u0 : u1, CORNER_UV[c][1] == 0 ? v0 : v1)
                        .color(scale, scale, scale, alpha)
                        .endVertex();
            }
        }
        Tessellator.getInstance().draw();
    }
}
