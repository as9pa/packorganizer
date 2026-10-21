package io.github.as9pa.packview.gui.preview;

import io.github.as9pa.packview.filter.ModelSpec;
import java.util.List;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/**
 * The preview's thrown items, particles and fishing line, drawn after the model in the same target, plus the potion
 * effect list over the view, as mock {@code renderFX} and {@code drawHUD}. World space is
 * {@link PlayerModelRenderer#applyWorldTransform}'s; the projection is orthographic, so camera-facing quads are quads
 * in the x-y plane. Projectiles get a mild size cue from their depth, particles do not. GL calls only, so client
 * thread only.
 */
public final class PreviewWorld {
    /** RenderArrow's scale from arrow model px to blocks. */
    private static final float ARROW_SCALE = 0.05625F;
    /** Side of the pearl, potion and bobber billboards in blocks. */
    private static final float BILLBOARD = 0.25F;
    /** EntityFX's cell side in particles.png's 16 x 16 grid, a hair under 1/16 so neighbors do not bleed in. */
    private static final float CELL = 0.0624375F;
    /** RenderFish's line: segments from the bobber to the rod tip. */
    private static final int LINE_SEGMENTS = 16;
    /** InventoryEffectRenderer's rows: box size and spacing in GUI px. */
    private static final int EFFECT_W = 140;
    private static final int EFFECT_H = 32;
    private static final int EFFECT_STEP = 33;

    private PreviewWorld() {
    }

    /** Mock {@code depthScale}: nearer projectiles draw a little larger, farther ones smaller. */
    static float depthScale(double z) {
        return (float) Math.min(1.6, Math.max(0.6, 1 + z * 0.08));
    }

    /**
     * Draws the projectiles, particles and (with {@code rodTip}, world space, from the same frame's model) the
     * fishing line. {@code centerX}, {@code feetY} and {@code blockPx} are the model's, in GUI px; {@code partial}
     * is the client partial tick the projectiles and particles interpolate with, from their previous step to the
     * current one.
     */
    public static void draw(PackModel pack, PreviewPhysics world, float[] rodTip, float centerX, float feetY, float blockPx,
            float partial) {
        List<Projectile> projectiles = world.projectiles();
        List<Particle> particles = world.particles();
        if (projectiles.isEmpty() && particles.isEmpty()) {
            return;
        }
        GlStateManager.pushMatrix();
        PlayerModelRenderer.applyWorldTransform(centerX, feetY, blockPx);
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.color(1F, 1F, 1F, 1F);

        Projectile bobber = null;
        for (Projectile p : projectiles) {
            // Off the view and flying on to its landing sound; never drawn again.
            if (p.hidden) {
                continue;
            }
            double x = p.drawX(partial), y = p.drawY(partial), z = p.drawZ(partial);
            float ds = depthScale(z);
            switch (p.kind) {
                case ARROW:
                    arrow(pack, p, partial, x, y, z, ds);
                    break;
                case PEARL:
                    billboard(pack.texture(ModelSpec.ENDER_PEARL), x, y, z, BILLBOARD / 2 * ds, 0F, 0F, 1F, 1F, 1F, 1F, 1F);
                    break;
                case POTION:
                    // The item model's layers: the overlay in the potion color, then the bottle.
                    int c = PreviewPhysics.HEALING;
                    float h = BILLBOARD / 2 * ds;
                    billboard(pack.texture(ModelSpec.POTION_OVERLAY), x, y, z, h, 0F, 0F, 1F, 1F,
                            (c >> 16 & 255) / 255F, (c >> 8 & 255) / 255F, (c & 255) / 255F);
                    billboard(pack.texture(ModelSpec.POTION_SPLASH), x, y, z, h, 0F, 0F, 1F, 1F, 1F, 1F, 1F);
                    break;
                case BOBBER:
                    bobber = p;
                    // RenderFish: particles.png (8, 16) 8x8.
                    billboard(pack.texture(ModelSpec.PARTICLES), x, y, z, BILLBOARD / 2 * ds, 1 / 16F, 2 / 16F,
                            2 / 16F, 3 / 16F, 1F, 1F, 1F);
                    break;
                default:
                    break;
            }
        }
        if (bobber != null && rodTip != null) {
            line(bobber, rodTip, blockPx, partial);
        }
        if (!particles.isEmpty()) {
            particles(pack, particles, partial);
        }

        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.depthMask(true);
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.enableCull();
        GlStateManager.popMatrix();
    }

    /**
     * RenderArrow: turned to the facing interpolated from the previous step (yaw - 90 about y, then pitch about z),
     * the stuck shake, rolled 45 degrees about x, scaled and shifted -4; a 5x5 back square at x -7 and two crossed
     * 16x5 planes from entity/arrow.png.
     */
    private static void arrow(PackModel pack, Projectile p, float partial, double x, double y, double z, float ds) {
        int texture = pack.texture(ModelSpec.ARROW);
        if (texture == 0) {
            return;
        }
        GlStateManager.bindTexture(texture);
        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.rotate(p.drawYaw(partial) - 90F, 0F, 1F, 0F);
        float pitch = p.drawPitch(partial);
        float shake = p.shake > 0 ? p.shake - partial : 0F;
        if (shake > 0F) {
            pitch += -(float) Math.sin(shake * 3F) * shake;
        }
        GlStateManager.rotate(pitch, 0F, 0F, 1F);
        GlStateManager.rotate(45F, 1F, 0F, 0F);
        float s = ARROW_SCALE * ds;
        GlStateManager.scale(s, s, s);
        GlStateManager.translate(-4F, 0F, 0F);
        float shaftV = 5 / 32F, backU = 0.15625F, backV0 = 5 / 32F, backV1 = 10 / 32F;
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        wr.pos(-7, -2, -2).tex(0, backV0).endVertex();
        wr.pos(-7, -2, 2).tex(backU, backV0).endVertex();
        wr.pos(-7, 2, 2).tex(backU, backV1).endVertex();
        wr.pos(-7, 2, -2).tex(0, backV1).endVertex();
        wr.pos(-8, -2, 0).tex(0, 0).endVertex();
        wr.pos(8, -2, 0).tex(0.5, 0).endVertex();
        wr.pos(8, 2, 0).tex(0.5, shaftV).endVertex();
        wr.pos(-8, 2, 0).tex(0, shaftV).endVertex();
        wr.pos(-8, 0, -2).tex(0, 0).endVertex();
        wr.pos(8, 0, -2).tex(0.5, 0).endVertex();
        wr.pos(8, 0, 2).tex(0.5, shaftV).endVertex();
        wr.pos(-8, 0, 2).tex(0, shaftV).endVertex();
        tess.draw();
        GlStateManager.popMatrix();
    }

    /** A camera-facing quad of half side {@code h} blocks centered on (x, y, z), texture rect top left first. */
    private static void billboard(int texture, double x, double y, double z, float h, float u0, float v0, float u1,
            float v1, float r, float g, float b) {
        if (texture == 0) {
            return;
        }
        GlStateManager.bindTexture(texture);
        WorldRenderer wr = Tessellator.getInstance().getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        quad(wr, x, y, z, h, u0, v0, u1, v1, r, g, b, 1F);
        Tessellator.getInstance().draw();
    }

    private static void quad(WorldRenderer wr, double x, double y, double z, float h, float u0, float v0, float u1,
            float v1, float r, float g, float b, float a) {
        wr.pos(x - h, y + h, z).tex(u0, v0).color(r, g, b, a).endVertex();
        wr.pos(x + h, y + h, z).tex(u1, v0).color(r, g, b, a).endVertex();
        wr.pos(x + h, y - h, z).tex(u1, v1).color(r, g, b, a).endVertex();
        wr.pos(x - h, y - h, z).tex(u0, v1).color(r, g, b, a).endVertex();
    }

    /**
     * RenderFish's line: 16 segments from the top of the bobber sprite to the rod tip, x and z straight, y along
     * {@code (f*f+f)/2} so it sags toward the bobber; black, 1 GUI px wide.
     */
    private static void line(Projectile b, float[] tip, float blockPx, float partial) {
        double bx = b.drawX(partial), bz = b.drawZ(partial);
        double by = b.drawY(partial) + BILLBOARD / 2 * depthScale(bz);
        double half = 0.5 / blockPx;
        GlStateManager.disableTexture2D();
        GlStateManager.color(1F, 1F, 1F, 1F);
        WorldRenderer wr = Tessellator.getInstance().getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        double lx = 0, ly = 0, lz = 0;
        for (int k = 0; k <= LINE_SEGMENTS; k++) {
            double f = k / (double) LINE_SEGMENTS;
            double cx = bx + (tip[0] - bx) * f;
            double cy = by + (tip[1] - by) * (f * f + f) / 2;
            double cz = bz + (tip[2] - bz) * f;
            if (k > 0) {
                double ex = cx - lx, ey = cy - ly, len = Math.sqrt(ex * ex + ey * ey);
                if (len == 0) {
                    len = 1;
                }
                double nx = -ey / len * half, ny = ex / len * half;
                wr.pos(lx - nx, ly - ny, lz).color(0F, 0F, 0F, 1F).endVertex();
                wr.pos(cx - nx, cy - ny, cz).color(0F, 0F, 0F, 1F).endVertex();
                wr.pos(cx + nx, cy + ny, cz).color(0F, 0F, 0F, 1F).endVertex();
                wr.pos(lx + nx, ly + ny, lz).color(0F, 0F, 0F, 1F).endVertex();
            }
            lx = cx;
            ly = cy;
            lz = cz;
        }
        Tessellator.getInstance().draw();
        GlStateManager.enableTexture2D();
    }

    /**
     * EffectRenderer: camera-facing quads of the particle's half size, from its particles.png cell, or for item
     * cracks from a 4x4 texel patch of the apple or bottle; portal particles grow in like EntityPortalFX. Blended
     * over what is there without writing depth, texels under alpha 0.1 dropped like the mock.
     */
    private static void particles(PackModel pack, List<Particle> particles, float partial) {
        GlStateManager.depthMask(false);
        int[] textures = {pack.texture(ModelSpec.PARTICLES), pack.texture(ModelSpec.GOLDEN_APPLE),
                pack.texture(ModelSpec.POTION_SPLASH)};
        WorldRenderer wr = Tessellator.getInstance().getWorldRenderer();
        // One batch per texture: particles.png, then the apple's and the bottle's crack patches.
        for (int t = 0; t < textures.length; t++) {
            if (textures[t] == 0) {
                continue;
            }
            GlStateManager.bindTexture(textures[t]);
            wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
            for (Particle p : particles) {
                boolean crack = p.kind == Particle.ParticleKind.ITEM_CRACK;
                int which = !crack ? 0 : p.bottle ? 2 : 1;
                if (which != t) {
                    continue;
                }
                double x = p.px + (p.x - p.px) * partial;
                double y = p.py + (p.y - p.py) * partial;
                double z = p.pz + (p.z - p.pz) * partial;
                float h = p.scale;
                if (p.kind == Particle.ParticleKind.PORTAL) {
                    float k = 1F - Math.min(1F, (p.age + partial) / p.maxAge);
                    h *= 1F - k * k;
                }
                float u0, v0, u1, v1;
                if (crack) {
                    u0 = p.u0;
                    v0 = p.v0;
                    u1 = u0 + Particle.PATCH;
                    v1 = v0 + Particle.PATCH;
                } else {
                    u0 = (p.sprite % 16) / 16F;
                    v0 = (p.sprite / 16) / 16F;
                    u1 = u0 + CELL;
                    v1 = v0 + CELL;
                }
                quad(wr, x, y, z, h, u0, v0, u1, v1, p.r, p.g, p.b, 1F);
            }
            Tessellator.getInstance().draw();
        }
        GlStateManager.depthMask(true);
    }

    /**
     * InventoryEffectRenderer's list from GUI ({@code x}, {@code y}) down: per effect the inventory.png
     * (0, 166, 140, 32) box, its 18 px icon, the name in white and the time left as m:ss in gray, 33 px apart.
     */
    public static void drawEffects(PackModel pack, List<Effect> effects, FontRenderer font, int x, int y) {
        if (effects.isEmpty()) {
            return;
        }
        int texture = pack.texture(ModelSpec.INVENTORY);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        GlStateManager.disableDepth();   // the directly drawn model leaves its depth in front of z 0
        GlStateManager.disableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        for (int i = 0; i < effects.size(); i++) {
            Effect e = effects.get(i);
            int top = y + i * EFFECT_STEP;
            if (texture != 0) {
                GlStateManager.bindTexture(texture);
                GlStateManager.color(1F, 1F, 1F, 1F);
                rect(x, top, EFFECT_W, EFFECT_H, 0, 166);
                rect(x + 6, top + 7, 18, 18, e.iconIndex % 8 * 18, 198 + e.iconIndex / 8 * 18);
            }
            font.drawStringWithShadow(e.name, x + 28, top + 6, 0xFFFFFF);
            font.drawStringWithShadow(duration(e.ticksLeft), x + 28, top + 16, 0x7F7F7F);
        }
        GlStateManager.color(1F, 1F, 1F, 1F);
        if (depth) {
            GlStateManager.enableDepth();
        }
    }

    /** StringUtils.ticksToElapsedTime: m:ss. */
    static String duration(int ticks) {
        int seconds = Math.max(0, ticks) / 20;
        int s = seconds % 60;
        return seconds / 60 + ":" + (s < 10 ? "0" : "") + s;
    }

    /** A GUI rect from a 256 px texture's (u, v) at 1:1, whatever the texture's real size. */
    private static void rect(int x, int y, int w, int h, int u, int v) {
        float k = 1 / 256F;
        WorldRenderer wr = Tessellator.getInstance().getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        wr.pos(x, y + h, 0).tex(u * k, (v + h) * k).endVertex();
        wr.pos(x + w, y + h, 0).tex((u + w) * k, (v + h) * k).endVertex();
        wr.pos(x + w, y, 0).tex((u + w) * k, v * k).endVertex();
        wr.pos(x, y, 0).tex(u * k, v * k).endVertex();
        Tessellator.getInstance().draw();
    }
}
