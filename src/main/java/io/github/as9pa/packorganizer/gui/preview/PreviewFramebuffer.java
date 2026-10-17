package io.github.as9pa.packorganizer.gui.preview;

import io.github.as9pa.packorganizer.Log;
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
import org.lwjgl.opengl.EXTFramebufferMultisample;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

/**
 * Multisampled offscreen target for the preview panel, so the model's edges come out smooth.
 *
 * <p>Between {@link #begin} and {@link #end} the caller draws exactly as it would on screen, in GUI
 * coordinates with the GUI projection: the viewport is offset so the rect lands on the target's pixels.
 * {@code end()} resolves the samples into a plain texture and draws it back at the same rect.
 *
 * <p>Alpha: the target is cleared to (0, 0, 0, 0) and {@code begin()} leaves the blend function at
 * (SRC_ALPHA, ONE_MINUS_SRC_ALPHA, ONE, ONE_MINUS_SRC_ALPHA), so color builds up premultiplied by alpha
 * and alpha builds up as coverage. The resolve averages premultiplied samples, which is what edge pixels
 * need, and {@code end()} composites with (ONE, ONE_MINUS_SRC_ALPHA) so the panel behind shows through
 * transparent pixels without dark fringes. Code inside that sets a plain (SRC_ALPHA, ONE_MINUS_SRC_ALPHA)
 * leaves translucent pixels a little too see-through; opaque parts are exact either way.
 *
 * <p>Without GL 3.0 or the EXT multisample and blit extensions, after any GL error here, or with
 * {@code -Dpackorganizer.noMsaa=true}, {@code begin()} returns false for the rest of the session and the
 * caller draws directly. GL calls only, so client thread only.
 */
public final class PreviewFramebuffer {
    private static final int MAX_SAMPLES = 4;

    /** Session-wide off switch: set by the system property or by the first failure, which logs once. */
    private static boolean disabled = Boolean.getBoolean("packorganizer.noMsaa");
    /** Samples per pixel, 0 until the capabilities have been checked. */
    private static int samples;
    /** True for GL 3.0 entry points, false for the EXT ones. The enum values are the same in both. */
    private static boolean core;

    private int msaaFbo;
    private int colorBuffer;
    private int depthBuffer;
    private int resolveFbo;
    private int texture;
    private int width;
    private int height;

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
     * Binds the multisampled target for the GUI rect and clears it to transparent.
     *
     * @return false when multisampling is unavailable; draw directly (with a scissor) and skip {@link #end}
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
        try {
            drainErrors();
            saveState();
            String problem = px[2] == width && px[3] == height ? null : allocate(px[2], px[3]);
            if (problem == null) {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                bindFramebuffer(GL30.GL_FRAMEBUFFER, msaaFbo);
                GlStateManager.viewport(-px[0], -px[1], mc.displayWidth, mc.displayHeight);
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

    /** Resolves the samples, draws them at the rect from {@link #begin}, and restores the state begin found. */
    public void end() {
        if (!active) return;
        active = false;
        try {
            // Errors left by the caller's drawing are not the framebuffer's fault; do not let them turn it off.
            drainErrors();
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            bindFramebuffer(GL30.GL_READ_FRAMEBUFFER, msaaFbo);
            bindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, resolveFbo);
            if (core) {
                GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            } else {
                EXTFramebufferBlit.glBlitFramebufferEXT(0, 0, width, height, 0, 0, width, height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            }
            String problem = glError("resolve");
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
        if (msaaFbo != 0) deleteFramebuffer(msaaFbo);
        if (resolveFbo != 0) deleteFramebuffer(resolveFbo);
        if (colorBuffer != 0) deleteRenderbuffer(colorBuffer);
        if (depthBuffer != 0) deleteRenderbuffer(depthBuffer);
        if (texture != 0) GlStateManager.deleteTexture(texture);
        msaaFbo = resolveFbo = colorBuffer = depthBuffer = texture = 0;
        width = height = 0;
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
        if (samples > 0) return true;
        try {
            ContextCapabilities caps = GLContext.getCapabilities();
            if (caps.OpenGL30) {
                core = true;
            } else if (caps.GL_EXT_framebuffer_object && caps.GL_EXT_framebuffer_multisample && caps.GL_EXT_framebuffer_blit) {
                core = false;
            } else {
                fail("no GL 3.0 or EXT framebuffer multisample and blit");
                return false;
            }
            drainErrors();
            int max = GL11.glGetInteger(core ? GL30.GL_MAX_SAMPLES : EXTFramebufferMultisample.GL_MAX_SAMPLES_EXT);
            String problem = glError("max samples");
            if (problem != null) {
                fail(problem);
                return false;
            }
            if (max < 2) {
                fail("GL_MAX_SAMPLES is " + max);
                return false;
            }
            samples = Math.min(MAX_SAMPLES, max);
            return true;
        } catch (RuntimeException e) {
            fail(e.toString());
            return false;
        }
    }

    /** Creates the targets at the new size; returns a problem description, or null when both are complete. */
    private String allocate(int w, int h) {
        delete();
        colorBuffer = genRenderbuffer();
        bindRenderbuffer(colorBuffer);
        renderbufferStorageMultisample(GL11.GL_RGBA8, w, h);
        depthBuffer = genRenderbuffer();
        bindRenderbuffer(depthBuffer);
        renderbufferStorageMultisample(GL14.GL_DEPTH_COMPONENT24, w, h);
        bindRenderbuffer(0);
        msaaFbo = genFramebuffer();
        bindFramebuffer(GL30.GL_FRAMEBUFFER, msaaFbo);
        framebufferRenderbuffer(GL30.GL_COLOR_ATTACHMENT0, colorBuffer);
        framebufferRenderbuffer(GL30.GL_DEPTH_ATTACHMENT, depthBuffer);
        int status = checkFramebufferStatus();
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) return "multisampled framebuffer incomplete: 0x" + Integer.toHexString(status);

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
        Log.LOG.warn("packorganizer: multisampled preview off, drawing directly: {}", problem);
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

    private static void renderbufferStorageMultisample(int format, int w, int h) {
        if (core) {
            GL30.glRenderbufferStorageMultisample(GL30.GL_RENDERBUFFER, samples, format, w, h);
        } else {
            EXTFramebufferMultisample.glRenderbufferStorageMultisampleEXT(GL30.GL_RENDERBUFFER, samples, format, w, h);
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
