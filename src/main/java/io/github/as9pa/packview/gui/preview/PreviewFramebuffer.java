package io.github.as9pa.packview.gui.preview;

import io.github.as9pa.packview.Log;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.EXTFramebufferBlit;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

/**
 * Supersampled offscreen target for the preview panel, so the model's edges come out smooth.
 *
 * <p>Between {@link #begin} and {@link #end} the caller draws exactly as it would on screen, in GUI
 * coordinates with the GUI projection: the viewport is offset and scaled so the rect lands on the target's
 * pixels, {@link #SUPERSAMPLE} of them across and down per window pixel. {@code end()} averages each block
 * down into a plain texture at the rect's pixel size and draws it back at the same rect.
 *
 * <p>Why not MSAA: fixed-function multisampling shades a partly covered pixel once at its center, even when
 * the center is outside the triangle, so texture coordinates run past the face's UV rect into neighbouring
 * atlas texels; on alpha-tested faces those pass the test and leave faint dashed lines along the edges, and
 * without shaders there is no centroid sampling to stop it. Supersampling shades every sample at its own
 * center inside the triangle, as vanilla's single sample does.
 *
 * <p>Alpha: the target is cleared to (0, 0, 0, 0) and {@code begin()} leaves the blend function at
 * (SRC_ALPHA, ONE_MINUS_SRC_ALPHA, ONE, ONE_MINUS_SRC_ALPHA), so color builds up premultiplied by alpha
 * and alpha builds up as coverage. The downsample averages premultiplied samples, which is what edge pixels
 * need, and {@code end()} composites with (ONE, ONE_MINUS_SRC_ALPHA) so the panel behind shows through
 * transparent pixels without dark fringes. Code inside that sets a plain (SRC_ALPHA, ONE_MINUS_SRC_ALPHA)
 * leaves translucent pixels a little too see-through; opaque parts are exact either way.
 *
 * <p>When the doubled target would pass {@code GL_MAX_RENDERBUFFER_SIZE} the target is drawn at 1x, without
 * smoothing. Without GL 3.0 or the EXT framebuffer object and blit extensions, after any GL error here, or with
 * {@code -Dpackview.noMsaa=true}, {@code begin()} returns false for the rest of the session and the
 * caller draws directly. GL calls only, so client thread only.
 */
public final class PreviewFramebuffer {
    /** Samples per side of each window pixel: 2 gives 4 per pixel, on an ordered grid. */
    static final int SUPERSAMPLE = 2;

    /** Session-wide off switch: set by the system property or by the first failure, which logs once. */
    private static boolean disabled = Boolean.getBoolean("packview.noMsaa");
    /** GL_MAX_RENDERBUFFER_SIZE, 0 until the capabilities have been checked. */
    private static int maxSize;
    /** True for GL 3.0 entry points, false for the EXT ones. The enum values are the same in both. */
    private static boolean core;

    private int sampleFbo;
    private int colorBuffer;
    private int depthBuffer;
    private int resolveFbo;
    private int texture;
    /** The rect's size in window pixels, which is the resolve texture's size. */
    private int width;
    private int height;
    /** Samples per side in the current target: the sample buffers are width * factor by height * factor. */
    private int factor;

    private boolean active;
    private int rectX;
    private int rectY;
    private int rectW;
    private int rectH;

    private final IntBuffer query = BufferUtils.createIntBuffer(16);
    private final FloatBuffer queryF = BufferUtils.createFloatBuffer(16);
    private boolean saved;
    private final int[] viewport = new int[4];
    private final int[] scissorBox = new int[4];
    private final float[] clearColor = new float[4];
    private boolean scissor;
    private boolean blend;
    private int blendSrcRgb;
    private int blendDstRgb;
    private int blendSrcAlpha;
    private int blendDstAlpha;
    private boolean depthTest;
    private boolean depthMask;
    private boolean alphaTest;
    private boolean lighting;

    /**
     * Binds the supersampled target for the GUI rect and clears it to transparent.
     *
     * @return false when the offscreen target is unavailable; draw directly (with a scissor) and skip {@link #end}
     */
    public boolean begin(int x, int y, int w, int h) {
        if (active) {
            // The last begin never reached end (the caller threw); put the screen back before saving again.
            active = false;
            restoreTarget();
            restoreState();
        }
        if (w <= 0 || h <= 0 || !supported()) return false;
        Minecraft mc = Minecraft.getMinecraft();
        int[] px = pixelRect(x, y, w, h, new ScaledResolution(mc).getScaleFactor(), mc.displayHeight);
        int f = factor(px[2], px[3], mc.displayWidth, mc.displayHeight, maxSize);
        try {
            drainErrors();
            saveState();
            String problem = px[2] == width && px[3] == height && f == factor ? null : allocate(px[2], px[3], f);
            if (problem == null) {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                bindFramebuffer(GL30.GL_FRAMEBUFFER, sampleFbo);
                // The GUI projection spans the viewport, so scaling the whole window by f scales the rect with it.
                GlStateManager.viewport(-px[0] * f, -px[1] * f, mc.displayWidth * f, mc.displayHeight * f);
                GlStateManager.clearColor(0.0F, 0.0F, 0.0F, 0.0F);
                GlStateManager.depthMask(true);
                GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
                problem = glError("begin");
            }
            if (problem != null) {
                abort(problem);
                return false;
            }
        } catch (RuntimeException e) {
            abort(e.toString());
            return false;
        }
        rectX = x;
        rectY = y;
        rectW = w;
        rectH = h;
        active = true;
        return true;
    }

    /** Downsamples the target, draws it at the rect from {@link #begin}, and restores the state begin found. */
    public void end() {
        if (!active) return;
        active = false;
        try {
            // Errors left by the caller's drawing are not the framebuffer's fault; do not let them turn it off.
            drainErrors();
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            bindFramebuffer(GL30.GL_READ_FRAMEBUFFER, sampleFbo);
            bindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, resolveFbo);
            // At exactly 2:1 each destination center falls on the corner shared by a 2x2 block, so the linear
            // filter averages the block; at 1:1 it lands on texel centers and copies.
            int sw = width * factor;
            int sh = height * factor;
            if (core) {
                GL30.glBlitFramebuffer(0, 0, sw, sh, 0, 0, width, height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
            } else {
                EXTFramebufferBlit.glBlitFramebufferEXT(0, 0, sw, sh, 0, 0, width, height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
            }
            String problem = glError("downsample");
            if (problem == null) {
                restoreTarget();
                drawTexture();
                problem = glError("composite");
            }
            if (problem != null) {
                abort(problem);
                return;
            }
        } catch (RuntimeException e) {
            abort(e.toString());
            return;
        }
        restoreState();
    }

    /** Frees the GL objects. The next {@link #begin} creates them again if the session still allows it. */
    public void delete() {
        active = false;
        if (sampleFbo != 0) deleteFramebuffer(sampleFbo);
        if (resolveFbo != 0) deleteFramebuffer(resolveFbo);
        if (colorBuffer != 0) deleteRenderbuffer(colorBuffer);
        if (depthBuffer != 0) deleteRenderbuffer(depthBuffer);
        if (texture != 0) GlStateManager.deleteTexture(texture);
        sampleFbo = resolveFbo = colorBuffer = depthBuffer = texture = 0;
        width = height = factor = 0;
    }

    /**
     * Samples per side for a rect of w x h window pixels in a displayW x displayH window: {@link #SUPERSAMPLE},
     * or 1 when the scaled rect or the scaled window would pass maxSize. The window counts because the viewport
     * spans all of it, and GL only promises a viewport limit at least as large as the renderbuffer limit.
     */
    static int factor(int w, int h, int displayW, int displayH, int maxSize) {
        long across = (long) Math.max(w, displayW) * SUPERSAMPLE;
        long down = (long) Math.max(h, displayH) * SUPERSAMPLE;
        return across <= maxSize && down <= maxSize ? SUPERSAMPLE : 1;
    }

    /**
     * GUI rect to window pixels: {x, y, w, h} with y measured up from the bottom of the window, as GL wants.
     * The GUI projection maps GUI unit g to pixel g * scale, so this is exact.
     */
    static int[] pixelRect(int x, int y, int w, int h, int scale, int displayHeight) {
        return new int[] {x * scale, displayHeight - (y + h) * scale, w * scale, h * scale};
    }

    private static boolean supported() {
        if (disabled) return false;
        if (maxSize > 0) return true;
        try {
            ContextCapabilities caps = GLContext.getCapabilities();
            if (caps.OpenGL30) {
                core = true;
            } else if (caps.GL_EXT_framebuffer_object && caps.GL_EXT_framebuffer_blit) {
                core = false;
            } else {
                fail("no GL 3.0 or EXT framebuffer object and blit");
                return false;
            }
            drainErrors();
            int max = GL11.glGetInteger(core ? GL30.GL_MAX_RENDERBUFFER_SIZE : EXTFramebufferObject.GL_MAX_RENDERBUFFER_SIZE_EXT);
            String problem = glError("max renderbuffer size");
            if (problem != null) {
                fail(problem);
                return false;
            }
            if (max <= 0) {
                fail("GL_MAX_RENDERBUFFER_SIZE is " + max);
                return false;
            }
            maxSize = max;
            return true;
        } catch (RuntimeException e) {
            fail(e.toString());
            return false;
        }
    }

    /**
     * Creates the targets for a w x h rect, the sample buffers f times that per side; returns a problem
     * description, or null when both are complete.
     */
    private String allocate(int w, int h, int f) {
        delete();
        colorBuffer = genRenderbuffer();
        bindRenderbuffer(colorBuffer);
        renderbufferStorage(GL11.GL_RGBA8, w * f, h * f);
        depthBuffer = genRenderbuffer();
        bindRenderbuffer(depthBuffer);
        renderbufferStorage(GL14.GL_DEPTH_COMPONENT24, w * f, h * f);
        bindRenderbuffer(0);
        sampleFbo = genFramebuffer();
        bindFramebuffer(GL30.GL_FRAMEBUFFER, sampleFbo);
        framebufferRenderbuffer(GL30.GL_COLOR_ATTACHMENT0, colorBuffer);
        framebufferRenderbuffer(GL30.GL_DEPTH_ATTACHMENT, depthBuffer);
        int status = checkFramebufferStatus();
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) return "supersampled framebuffer incomplete: 0x" + Integer.toHexString(status);

        texture = GlStateManager.generateTexture();
        GlStateManager.bindTexture(texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        resolveFbo = genFramebuffer();
        bindFramebuffer(GL30.GL_FRAMEBUFFER, resolveFbo);
        framebufferTexture2D(texture);
        status = checkFramebufferStatus();
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) return "resolve framebuffer incomplete: 0x" + Integer.toHexString(status);

        width = w;
        height = h;
        factor = f;
        return glError("allocate");
    }

    /** Draws the resolved texture over the GUI rect with premultiplied blending. Texture row 0 is the bottom. */
    private void drawTexture() {
        GlStateManager.enableTexture2D();
        GlStateManager.bindTexture(texture);
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.disableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer renderer = tessellator.getWorldRenderer();
        renderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        renderer.pos(rectX, rectY + rectH, 0.0D).tex(0.0D, 0.0D).endVertex();
        renderer.pos(rectX + rectW, rectY + rectH, 0.0D).tex(1.0D, 0.0D).endVertex();
        renderer.pos(rectX + rectW, rectY, 0.0D).tex(1.0D, 1.0D).endVertex();
        renderer.pos(rectX, rectY, 0.0D).tex(0.0D, 1.0D).endVertex();
        tessellator.draw();
    }

    private void saveState() {
        query.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, query);
        for (int i = 0; i < 4; i++) viewport[i] = query.get(i);
        query.clear();
        GL11.glGetInteger(GL11.GL_SCISSOR_BOX, query);
        for (int i = 0; i < 4; i++) scissorBox[i] = query.get(i);
        queryF.clear();
        GL11.glGetFloat(GL11.GL_COLOR_CLEAR_VALUE, queryF);
        for (int i = 0; i < 4; i++) clearColor[i] = queryF.get(i);
        scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        blend = GL11.glIsEnabled(GL11.GL_BLEND);
        blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        alphaTest = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        saved = true;
    }

    /** Puts back the screen's framebuffer, viewport and scissor. */
    private void restoreTarget() {
        if (!saved) return;
        if (OpenGlHelper.isFramebufferEnabled()) {
            Minecraft.getMinecraft().getFramebuffer().bindFramebuffer(true);
        } else {
            bindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        }
        GlStateManager.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        GL11.glScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
        if (scissor) {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
        } else {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }

    /** Puts back blend, depth, alpha test, lighting and clear color as begin found them. */
    private void restoreState() {
        if (!saved) return;
        saved = false;
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
        if (alphaTest) {
            GlStateManager.enableAlpha();
        } else {
            GlStateManager.disableAlpha();
        }
        if (lighting) {
            GlStateManager.enableLighting();
        } else {
            GlStateManager.disableLighting();
        }
        GlStateManager.clearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
    }

    /** Turns the path off for the session, frees this target and leaves the screen as begin found it. */
    private void abort(String problem) {
        fail(problem);
        try {
            delete();
        } catch (RuntimeException ignored) {
            // Already failing; the restore below matters more than the leak.
        }
        restoreTarget();
        restoreState();
    }

    private static void fail(String problem) {
        if (disabled) return;
        disabled = true;
        Log.LOG.warn("packview: supersampled preview off, drawing directly: {}", problem);
    }

    private static String glError(String where) {
        int error = GL11.glGetError();
        return error == GL11.GL_NO_ERROR ? null : "GL error 0x" + Integer.toHexString(error) + " in " + where;
    }

    private static void drainErrors() {
        for (int i = 0; i < 16 && GL11.glGetError() != GL11.GL_NO_ERROR; i++) {
            // discard
        }
    }

    private static int genFramebuffer() {
        return core ? GL30.glGenFramebuffers() : EXTFramebufferObject.glGenFramebuffersEXT();
    }

    private static void bindFramebuffer(int target, int fbo) {
        if (core) {
            GL30.glBindFramebuffer(target, fbo);
        } else {
            EXTFramebufferObject.glBindFramebufferEXT(target, fbo);
        }
    }

    private static void deleteFramebuffer(int fbo) {
        if (core) {
            GL30.glDeleteFramebuffers(fbo);
        } else {
            EXTFramebufferObject.glDeleteFramebuffersEXT(fbo);
        }
    }

    private static int checkFramebufferStatus() {
        return core
                ? GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)
                : EXTFramebufferObject.glCheckFramebufferStatusEXT(GL30.GL_FRAMEBUFFER);
    }

    private static int genRenderbuffer() {
        return core ? GL30.glGenRenderbuffers() : EXTFramebufferObject.glGenRenderbuffersEXT();
    }

    private static void bindRenderbuffer(int renderbuffer) {
        if (core) {
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, renderbuffer);
        } else {
            EXTFramebufferObject.glBindRenderbufferEXT(GL30.GL_RENDERBUFFER, renderbuffer);
        }
    }

    private static void deleteRenderbuffer(int renderbuffer) {
        if (core) {
            GL30.glDeleteRenderbuffers(renderbuffer);
        } else {
            EXTFramebufferObject.glDeleteRenderbuffersEXT(renderbuffer);
        }
    }

    private static void renderbufferStorage(int format, int w, int h) {
        if (core) {
            GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, format, w, h);
        } else {
            EXTFramebufferObject.glRenderbufferStorageEXT(GL30.GL_RENDERBUFFER, format, w, h);
        }
    }

    private static void framebufferRenderbuffer(int attachment, int renderbuffer) {
        if (core) {
            GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, attachment, GL30.GL_RENDERBUFFER, renderbuffer);
        } else {
            EXTFramebufferObject.glFramebufferRenderbufferEXT(GL30.GL_FRAMEBUFFER, attachment, GL30.GL_RENDERBUFFER, renderbuffer);
        }
    }

    private static void framebufferTexture2D(int tex) {
        if (core) {
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, tex, 0);
        } else {
            EXTFramebufferObject.glFramebufferTexture2DEXT(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, tex, 0);
        }
    }
}
