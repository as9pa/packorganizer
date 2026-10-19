package io.github.as9pa.packorganizer.gui.preview;

import java.nio.FloatBuffer;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/**
 * Vanilla's enchantment glint as extra passes over geometry that was just drawn: RenderItem.renderEffect for items
 * and LayerArmorBase.renderGlint for armor. Each pass redraws the geometry with the glint texture through a texture
 * matrix, with depth {@code GL_EQUAL} so only the pixels the geometry wrote change, no depth write, no lighting and
 * additive {@code GL_SRC_COLOR, GL_ONE} blending. The geometry must not bind textures or set the color, and runs with
 * the modelview matrix current. Every piece of
 * state changed here is read back from GL first and put back afterwards. Client thread only.
 */
public final class Glint {
    /** RenderItem.renderEffect's vertex color 0xFF8040CC. */
    private static final float[] ITEM_COLOR = {0x80 / 255F, 0x40 / 255F, 0xCC / 255F};
    /** LayerArmorBase.renderGlint's (0.5, 0.25, 0.8) times 0.76. */
    private static final float[] ARMOR_COLOR = {0.38F, 0.19F, 0.608F};
    /**
     * Item UVs here are the sprite's own 0..1; vanilla's are a 16 px sprite in the 1024 px items atlas, so they are
     * scaled down to that share of the atlas before the glint matrix, as in the mock.
     */
    private static final float SPRITE = 1F / 64F;

    private static final FloatBuffer COLOR = BufferUtils.createFloatBuffer(16);

    private Glint() {}

    /**
     * The item glint over {@code drawGeometry} (0..1 sprite UVs) at {@code ms} milliseconds of the glint clock: two
     * passes scaled 8, sliding and turned -50 and 10 degrees. Nothing when {@code glintTex} is 0.
     */
    public static void item(Runnable drawGeometry, int glintTex, long ms) {
        if (glintTex == 0) {
            return;
        }
        Saved saved = begin(glintTex);
        float[] slide = {(ms % 3000L) / 3000F / 8F, -(ms % 4873L) / 4873F / 8F};
        float[] turn = {-50F, 10F};
        for (int i = 0; i < 2; i++) {
            GlStateManager.color(ITEM_COLOR[0], ITEM_COLOR[1], ITEM_COLOR[2], 1F);
            GlStateManager.matrixMode(GL11.GL_TEXTURE);
            GlStateManager.pushMatrix();
            GlStateManager.scale(8F, 8F, 8F);
            GlStateManager.translate(slide[i], 0F, 0F);
            GlStateManager.rotate(turn[i], 0F, 0F, 1F);
            GlStateManager.scale(SPRITE, SPRITE, 1F);
            draw(drawGeometry);
        }
        end(saved);
    }

    /**
     * The armor glint over {@code drawGeometry} (armor texture UVs) at {@code ticks} ticks of age with the partial
     * tick: two passes scaled 1/3, turned {@code 30 - 60i} degrees and sliding up. Nothing when {@code glintTex} is 0.
     */
    public static void armor(Runnable drawGeometry, int glintTex, float ticks) {
        if (glintTex == 0) {
            return;
        }
        Saved saved = begin(glintTex);
        for (int i = 0; i < 2; i++) {
            GlStateManager.color(ARMOR_COLOR[0], ARMOR_COLOR[1], ARMOR_COLOR[2], 1F);
            GlStateManager.matrixMode(GL11.GL_TEXTURE);
            GlStateManager.pushMatrix();
            GlStateManager.loadIdentity();
            GlStateManager.scale(1F / 3F, 1F / 3F, 1F / 3F);
            GlStateManager.rotate(30F - i * 60F, 0F, 0F, 1F);
            GlStateManager.translate(0F, ticks * (0.001F + i * 0.003F) * 20F, 0F);
            draw(drawGeometry);
        }
        end(saved);
    }

    /** Reads the state the passes change, then sets it up with the texture matrix current. */
    private static Saved begin(int glintTex) {
        Saved s = new Saved();
        s.matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        s.texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        s.depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        s.depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        s.lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        s.blend = GL11.glIsEnabled(GL11.GL_BLEND);
        s.srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        s.dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        s.srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        s.dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        COLOR.clear();
        GL11.glGetFloat(GL11.GL_CURRENT_COLOR, COLOR);
        s.color = new float[] {COLOR.get(0), COLOR.get(1), COLOR.get(2), COLOR.get(3)};

        GlStateManager.depthMask(false);
        GlStateManager.depthFunc(GL11.GL_EQUAL);
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_COLOR, GL11.GL_ONE, GL11.GL_SRC_COLOR, GL11.GL_ONE);
        GlStateManager.bindTexture(glintTex);
        // The vanilla glint's mcmeta asks for blur; the texture is only ever drawn here.
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        return s;
    }

    /**
     * Runs the geometry with the modelview matrix current, as LayerArmorBase.renderGlint does: ModelRenderer.render
     * translates and rotates whatever matrix is current, so with the texture matrix current every turned or moved
     * part would land somewhere else and fail {@code GL_EQUAL}. Pops the pass's texture matrix afterwards.
     */
    private static void draw(Runnable drawGeometry) {
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        drawGeometry.run();
        GlStateManager.matrixMode(GL11.GL_TEXTURE);
        GlStateManager.popMatrix();
    }

    private static void end(Saved s) {
        GlStateManager.matrixMode(s.matrixMode);
        GlStateManager.bindTexture(s.texture);
        GlStateManager.tryBlendFuncSeparate(s.srcRgb, s.dstRgb, s.srcAlpha, s.dstAlpha);
        if (!s.blend) {
            GlStateManager.disableBlend();
        }
        if (s.lighting) {
            GlStateManager.enableLighting();
        }
        GlStateManager.depthFunc(s.depthFunc);
        GlStateManager.depthMask(s.depthMask);
        GlStateManager.color(s.color[0], s.color[1], s.color[2], s.color[3]);
    }

    private static final class Saved {
        int matrixMode;
        int texture;
        int depthFunc;
        boolean depthMask;
        boolean lighting;
        boolean blend;
        int srcRgb;
        int dstRgb;
        int srcAlpha;
        int dstAlpha;
        float[] color;
    }
}
