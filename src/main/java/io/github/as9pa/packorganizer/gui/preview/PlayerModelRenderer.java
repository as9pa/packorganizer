package io.github.as9pa.packorganizer.gui.preview;

import io.github.as9pa.packorganizer.PackOrganizer;
import io.github.as9pa.packorganizer.filter.ModelSpec;
import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.util.MathHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * Draws the preview player: the vanilla {@code ModelPlayer} skin, {@code ModelBiped} armor, the {@code LayerCape}
 * cape at rest and the held item in its vanilla third person transform. Parts are posed by setting their angles
 * with the {@code ModelBiped.setRotationAngles} math and drawn one by one, because the models' own render and
 * setRotationAngles need an entity. Client thread only.
 *
 * <p>Coordinate contract shared with the projectile, particle and rod line renderers: world space is blocks with
 * the origin at the feet, +y up, +x to the screen's right and +z toward the viewer, set up from GUI coordinates by
 * {@link #applyWorldTransform}. The model is turned about +y by {@code spin - bodyYaw} degrees (mock
 * {@code pose()}), so at 0 it faces the viewer; projectiles live in unrotated world space.
 */
public final class PlayerModelRenderer {
    /** Model pixels to blocks, the scale every part renders at. */
    private static final float PX = 0.0625F;
    /** GuiInventory.drawEntityOnScreen's depth, so the model sits in front of the GUI behind it. */
    private static final float GUI_Z = 50F;
    /** ModelBiped.setRotationAngles' degrees to radians divisor. */
    private static final float DEG = 57.295776F;
    private static final int SPLASH_TINT = 0xF82423;
    /** Lowest texel alpha (of 255) that survives vanilla's GL_GREATER 0.1 alpha test. */
    private static final int TIP_ALPHA = 26;

    private final ModelPlayer wide = new ModelPlayer(0F, false);
    private final ModelPlayer slim = new ModelPlayer(0F, true);
    /** LayerBipedArmor's models: layer_1 for helmet, chestplate and boots, layer_2 for leggings. */
    private final ModelBiped armor = new ModelBiped(1.0F);
    private final ModelBiped leggings = new ModelBiped(0.5F);

    private final FloatBuffer matrix = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer hurtColor = BufferUtils.createFloatBuffer(4);
    /** Rod tips in item mesh units per texture image; weak so released packs drop out. */
    private final Map<BufferedImage, float[]> tips = new WeakHashMap<>();

    private final List<float[]> corners = new ArrayList<>();
    private float[] rodTip;
    /** Current modelview to the GUI space render was called in, and to world space, during a render. */
    private float[] guiInverse;
    private float[] worldInverse;

    /** Everything that poses the model for one frame. Angles are degrees. */
    public static final class ModelPose {
        /** Body and head yaw, positive turns toward the screen's left (head follow). */
        public float bodyYaw;
        public float headYaw;
        /** Head pitch, negative looks up, as vanilla rotationPitch. */
        public float pitch;
        /** Drag spin about +y. */
        public float spin;
        /** Swing progress 0..1, 0 when not swinging. */
        public float swing;
        /** Sword held up (ModelBiped heldItemRight 3). */
        public boolean blocking;
        /** Bow drawn (ModelBiped aimedBow). */
        public boolean aimingBow;
        /** -1 the standing bow, 0..2 the bow_pulling stages. */
        public int bowFrame = -1;
        /** The rod shows its cast texture. */
        public boolean rodCast;
        /** Red mixed into body and armor, 0..0.3 (vanilla's hurt flash is 0.3). */
        public float hurt;
        /** Armor material from {@link ModelSpec#MATERIALS}; also picks the sword. Null draws no armor. */
        public String armor = "diamond";
        /** Null holds nothing. */
        public HeldItem item = HeldItem.SWORD;
        /** Glint on sword, bow and armor; drawn by the glint passes, not here. */
        public boolean enchanted;
        /** Age in ticks with the partial tick, for the vanilla idle arm sway. */
        public float ticks;
    }

    /**
     * Draws the player at GUI {@code centerX}, feet at GUI {@code feetY}, {@code blockPx} GUI px per block. The
     * modelview on entry is taken as GUI space for {@link #lastPartCorners()}. A null skin draws armor, item and no
     * body.
     */
    public void render(PackModel pack, ModelPose pose, SkinTextures skin, float centerX, float feetY, float blockPx) {
        corners.clear();
        rodTip = null;
        guiInverse = invertAffine(modelview());
        ModelPlayer model = skin != null && skin.slim ? slim : wide;

        GlStateManager.enableColorMaterial();
        GlStateManager.pushMatrix();
        applyWorldTransform(centerX, feetY, blockPx);
        worldInverse = invertAffine(modelview());
        GlStateManager.rotate(135F, 0F, 1F, 0F);
        RenderHelper.enableStandardItemLighting();
        GlStateManager.rotate(-135F, 0F, 1F, 0F);

        // RendererLivingEntity.doRender: rotateCorpse (180 - yaw), then prepareScale with RenderPlayer's 0.9375.
        GlStateManager.disableCull();
        GlStateManager.rotate(pose.spin - pose.bodyYaw, 0F, 1F, 0F);
        GlStateManager.rotate(180F, 0F, 1F, 0F);
        GlStateManager.enableRescaleNormal();
        GlStateManager.scale(-1F, -1F, 1F);
        GlStateManager.scale(0.9375F, 0.9375F, 0.9375F);
        GlStateManager.translate(0F, -1.5078125F, 0F);
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.color(1F, 1F, 1F, 1F);

        setAngles(model, pose);
        ModelBase.copyModelAngles(model.bipedLeftLeg, model.bipedLeftLegwear);
        ModelBase.copyModelAngles(model.bipedRightLeg, model.bipedRightLegwear);
        ModelBase.copyModelAngles(model.bipedLeftArm, model.bipedLeftArmwear);
        ModelBase.copyModelAngles(model.bipedRightArm, model.bipedRightArmwear);
        ModelBase.copyModelAngles(model.bipedBody, model.bipedBodyWear);
        setAngles(armor, pose);
        setAngles(leggings, pose);

        boolean hurt = pose.hurt > 0F;
        if (hurt) {
            hurtTint(Math.min(pose.hurt, 1F));
        }
        if (skin != null) {
            GlStateManager.bindTexture(skin.skin);
            renderSkin(model);
        }
        if (pose.armor != null && ModelSpec.MATERIALS.contains(pose.armor)) {
            renderArmor(pack, pose.armor, pose.enchanted, pose.ticks);
        }
        if (hurt) {
            clearTint();
        }
        GlStateManager.color(1F, 1F, 1F, 1F);
        if (pose.item != null) {
            renderHeldItem(pack, pose, model);
        }
        if (skin != null && skin.cape != 0 && PackOrganizer.settings().showCape) {
            renderCape(model, skin.cape);
        }

        GlStateManager.enableCull();
        GlStateManager.popMatrix();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.disableTexture2D();
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.color(1F, 1F, 1F, 1F);
        guiInverse = null;
        worldInverse = null;
    }

    /**
     * From GUI coordinates to world space: origin at GUI ({@code centerX}, {@code feetY}), {@code blockPx} GUI px
     * per block, +y up and +z toward the viewer. The same as GuiInventory.drawEntityOnScreen's
     * {@code scale(-s, s, s)} then {@code rotate(180, z)}.
     */
    public static void applyWorldTransform(float centerX, float feetY, float blockPx) {
        GlStateManager.translate(centerX, feetY, GUI_Z);
        GlStateManager.scale(blockPx, -blockPx, blockPx);
    }

    /**
     * The 8 corners of every body, armor and cape box drawn by the last render, as 16 floats of GUI x, y pairs in
     * the order (x0,y0,z0), (x1,y0,z0), (x1,y1,z0), (x0,y1,z0), then the same at z1. The held item is not included.
     */
    public List<float[]> lastPartCorners() {
        return Collections.unmodifiableList(corners);
    }

    /** The fishing rod's tip in world space from the last render, or null when the rod was not held. */
    public float[] lastRodTip() {
        return rodTip == null ? null : rodTip.clone();
    }

    /** ModelBiped.setRotationAngles standing still with an item in the right hand, as javap shows it. */
    private static void setAngles(ModelBiped m, ModelPose p) {
        m.bipedHead.rotateAngleY = (p.headYaw - p.bodyYaw) / DEG;
        m.bipedHead.rotateAngleX = p.pitch / DEG;
        m.bipedHead.rotateAngleZ = 0F;
        // Walk cycle at zero limb swing, not riding, nothing in the left hand.
        m.bipedRightArm.rotateAngleX = 0F;
        m.bipedRightArm.rotateAngleY = 0F;
        m.bipedRightArm.rotateAngleZ = 0F;
        m.bipedLeftArm.rotateAngleX = 0F;
        m.bipedLeftArm.rotateAngleY = 0F;
        m.bipedLeftArm.rotateAngleZ = 0F;
        m.bipedRightLeg.rotateAngleX = 0F;
        m.bipedRightLeg.rotateAngleY = 0F;
        m.bipedRightLeg.rotateAngleZ = 0F;
        m.bipedLeftLeg.rotateAngleX = 0F;
        m.bipedLeftLeg.rotateAngleY = 0F;
        m.bipedLeftLeg.rotateAngleZ = 0F;
        m.bipedBody.rotateAngleZ = 0F;
        // heldItemRight: 1 holding, 3 blocking.
        int held = p.blocking ? 3 : 1;
        m.bipedRightArm.rotateAngleX = m.bipedRightArm.rotateAngleX * 0.5F - ((float) Math.PI / 10F) * held;
        if (held == 3) {
            m.bipedRightArm.rotateAngleY = -0.5235988F;
        }
        // Swing block; vanilla runs it every frame, a swing of 0 only resets the arm pivots.
        float swing = p.swing;
        float bodyY = MathHelper.sin(MathHelper.sqrt_float(swing) * (float) Math.PI * 2F) * 0.2F;
        m.bipedBody.rotateAngleY = bodyY;
        m.bipedRightArm.rotationPointZ = MathHelper.sin(bodyY) * 5F;
        m.bipedRightArm.rotationPointX = -MathHelper.cos(bodyY) * 5F;
        m.bipedLeftArm.rotationPointZ = -MathHelper.sin(bodyY) * 5F;
        m.bipedLeftArm.rotationPointX = MathHelper.cos(bodyY) * 5F;
        m.bipedRightArm.rotateAngleY += bodyY;
        m.bipedLeftArm.rotateAngleY += bodyY;
        m.bipedLeftArm.rotateAngleX += bodyY;
        float f = 1F - swing;
        f = f * f;
        f = f * f;
        f = 1F - f;
        float f1 = MathHelper.sin(f * (float) Math.PI);
        float f2 = MathHelper.sin(swing * (float) Math.PI) * -(m.bipedHead.rotateAngleX - 0.7F) * 0.75F;
        m.bipedRightArm.rotateAngleX = (float) (m.bipedRightArm.rotateAngleX - (f1 * 1.2D + f2));
        m.bipedRightArm.rotateAngleY += bodyY * 2F;
        m.bipedRightArm.rotateAngleZ += MathHelper.sin(swing * (float) Math.PI) * -0.4F;
        // Not sneaking.
        m.bipedBody.rotateAngleX = 0F;
        m.bipedRightLeg.rotationPointZ = 0.1F;
        m.bipedLeftLeg.rotationPointZ = 0.1F;
        m.bipedRightLeg.rotationPointY = 12F;
        m.bipedLeftLeg.rotationPointY = 12F;
        m.bipedHead.rotationPointY = 0F;
        // Idle sway.
        float t = p.ticks;
        m.bipedRightArm.rotateAngleZ += MathHelper.cos(t * 0.09F) * 0.05F + 0.05F;
        m.bipedLeftArm.rotateAngleZ -= MathHelper.cos(t * 0.09F) * 0.05F + 0.05F;
        m.bipedRightArm.rotateAngleX += MathHelper.sin(t * 0.067F) * 0.05F;
        m.bipedLeftArm.rotateAngleX -= MathHelper.sin(t * 0.067F) * 0.05F;
        if (p.aimingBow) {
            m.bipedRightArm.rotateAngleZ = 0F;
            m.bipedLeftArm.rotateAngleZ = 0F;
            m.bipedRightArm.rotateAngleY = -0.1F + m.bipedHead.rotateAngleY;
            m.bipedLeftArm.rotateAngleY = 0.1F + m.bipedHead.rotateAngleY + 0.4F;
            m.bipedRightArm.rotateAngleX = -((float) Math.PI / 2F) + m.bipedHead.rotateAngleX;
            m.bipedLeftArm.rotateAngleX = -((float) Math.PI / 2F) + m.bipedHead.rotateAngleX;
            m.bipedRightArm.rotateAngleZ += MathHelper.cos(t * 0.09F) * 0.05F + 0.05F;
            m.bipedLeftArm.rotateAngleZ -= MathHelper.cos(t * 0.09F) * 0.05F + 0.05F;
            m.bipedRightArm.rotateAngleX += MathHelper.sin(t * 0.067F) * 0.05F;
            m.bipedLeftArm.rotateAngleX -= MathHelper.sin(t * 0.067F) * 0.05F;
        }
        ModelBase.copyModelAngles(m.bipedHead, m.bipedHeadwear);
    }

    /** ModelPlayer.render's part order; the hat is ModelBiped's headwear at +0.5, the other overlays +0.25. */
    private void renderSkin(ModelPlayer m) {
        renderPart(m.bipedHead, 0F);
        renderPart(m.bipedBody, 0F);
        renderPart(m.bipedRightArm, 0F);
        renderPart(m.bipedLeftArm, 0F);
        renderPart(m.bipedRightLeg, 0F);
        renderPart(m.bipedLeftLeg, 0F);
        renderPart(m.bipedHeadwear, 0.5F);
        renderPart(m.bipedLeftLegwear, 0.25F);
        renderPart(m.bipedRightLegwear, 0.25F);
        renderPart(m.bipedLeftArmwear, 0.25F);
        renderPart(m.bipedRightArmwear, 0.25F);
        renderPart(m.bipedBodyWear, 0.25F);
    }

    /**
     * LayerArmorBase: helmet, chestplate, leggings, boots, with LayerBipedArmor's parts per slot; {@code glint} adds
     * each slot's glint at {@code ticks}.
     */
    private void renderArmor(PackModel pack, String material, boolean glint, float ticks) {
        renderArmorPiece(pack, material, glint, ticks, armor, 1, armor.bipedHead, armor.bipedHeadwear);
        renderArmorPiece(pack, material, glint, ticks, armor, 1, armor.bipedBody, armor.bipedRightArm,
                armor.bipedLeftArm);
        renderArmorPiece(pack, material, glint, ticks, leggings, 2, leggings.bipedBody, leggings.bipedRightLeg,
                leggings.bipedLeftLeg);
        renderArmorPiece(pack, material, glint, ticks, armor, 1, armor.bipedRightLeg, armor.bipedLeftLeg);
    }

    /** One slot; leather gets its untinted overlay drawn over the tinted base, then the glint, as vanilla. */
    private void renderArmorPiece(PackModel pack, String material, boolean glint, float ticks, ModelBiped model,
                                  int layer, ModelRenderer... parts) {
        float inflate = model == armor ? 1.0F : 0.5F;
        boolean drawn = false;
        int base = pack.texture(ModelSpec.armor(material, layer));
        if (base != 0) {
            GlStateManager.bindTexture(base);
            for (ModelRenderer part : parts) {
                renderPart(part, part == model.bipedHeadwear ? inflate + 0.5F : inflate);
            }
            drawn = true;
        }
        if ("leather".equals(material)) {
            int overlay = pack.texture(ModelSpec.armorOverlay(layer));
            if (overlay != 0) {
                GlStateManager.bindTexture(overlay);
                for (ModelRenderer part : parts) {
                    part.render(PX);
                }
                drawn = true;
            }
        }
        if (glint && drawn) {
            Glint.armor(() -> {
                for (ModelRenderer part : parts) {
                    part.render(PX);
                }
            }, pack.texture(ModelSpec.GLINT), ticks);
        }
    }

    /** LayerCape with the player standing still: 2 px behind, 6 degrees out, turned to face backwards. */
    private void renderCape(ModelPlayer model, int cape) {
        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.bindTexture(cape);
        GlStateManager.pushMatrix();
        GlStateManager.translate(0F, 0F, 0.125F);
        GlStateManager.rotate(6F, 1F, 0F, 0F);
        GlStateManager.rotate(180F, 0F, 1F, 0F);
        model.renderCape(PX);
        // ModelPlayer's bipedCape: addBox(-5, 0, -1, 10, 16, 1) at the origin, unrotated.
        corners.add(boxCorners(multiply(guiInverse, modelview()), -5F * PX, 0F, -1F * PX, 5F * PX, 16F * PX, 0F));
        GlStateManager.popMatrix();
    }

    /**
     * LayerHeldItem, ItemRenderer.renderItem and RenderItem.renderItemModelTransform for a flat item: the right
     * hand, the 2x preTransform of non-gui3d models, the third person display, then renderItem's half scale and
     * centering around the 0..1 mesh.
     */
    private void renderHeldItem(PackModel pack, ModelPose pose, ModelPlayer model) {
        String path;
        String under = null;
        switch (pose.item) {
            case SWORD:
                path = ModelSpec.sword(pose.armor != null && ModelSpec.MATERIALS.contains(pose.armor)
                        ? pose.armor : "diamond");
                break;
            case BOW:
                path = pose.bowFrame >= 0 && pose.bowFrame < ModelSpec.BOW_PULL.length
                        ? ModelSpec.BOW_PULL[pose.bowFrame] : ModelSpec.BOW;
                break;
            case ENDER_PEARL:
                path = ModelSpec.ENDER_PEARL;
                break;
            case GOLDEN_APPLE:
                path = ModelSpec.GOLDEN_APPLE;
                break;
            case SPLASH_POTION:
                under = ModelSpec.POTION_OVERLAY;
                path = ModelSpec.POTION_SPLASH;
                break;
            default:
                path = pose.rodCast ? ModelSpec.ROD_CAST : ModelSpec.ROD;
                break;
        }
        ItemDisplays.Display d = ItemDisplays.of(pose.item);

        GlStateManager.pushMatrix();
        model.postRenderArm(PX);
        GlStateManager.translate(-0.0625F, 0.4375F, 0.0625F);
        GlStateManager.scale(2F, 2F, 2F);
        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.translate(d.x, d.y, d.z);
        GlStateManager.rotate(d.rotY, 0F, 1F, 0F);
        GlStateManager.rotate(d.rotX, 1F, 0F, 0F);
        GlStateManager.rotate(d.rotZ, 0F, 0F, 1F);
        GlStateManager.scale(d.scaleX, d.scaleY, d.scaleZ);
        GlStateManager.scale(0.5F, 0.5F, 0.5F);
        GlStateManager.translate(-0.5F, -0.5F, -0.5F);
        if (under != null) {
            GlStateManager.color((SPLASH_TINT >> 16 & 255) / 255F, (SPLASH_TINT >> 8 & 255) / 255F,
                    (SPLASH_TINT & 255) / 255F, 1F);
            drawItem(pack, under);
            GlStateManager.color(1F, 1F, 1F, 1F);
        }
        boolean drawn = drawItem(pack, path);
        if (drawn && pose.enchanted && (pose.item == HeldItem.SWORD || pose.item == HeldItem.BOW)) {
            int list = pack.itemList(path);
            Glint.item(() -> GlStateManager.callList(list), pack.texture(ModelSpec.GLINT), Minecraft.getSystemTime());
        }
        if (pose.item == HeldItem.FISHING_ROD) {
            float[] tip = tip(pack.image(path));
            float[] at = new float[3];
            transformPoint(multiply(worldInverse, modelview()), tip[0], tip[1], tip[2], at, 0);
            rodTip = at;
        }
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    /** False when the texture or its mesh is missing and nothing was drawn. */
    private static boolean drawItem(PackModel pack, String path) {
        int texture = pack.texture(path);
        int list = pack.itemList(path);
        if (texture != 0 && list > 0) {
            GlStateManager.bindTexture(texture);
            GlStateManager.callList(list);
            return true;
        }
        return false;
    }

    /**
     * The rod tip in item mesh units: the opaque texel furthest up and right (largest column minus row), the
     * mesh's top-right corner when the image is missing or empty.
     */
    private float[] tip(BufferedImage img) {
        if (img == null) {
            return new float[] {1F, 1F, 0.5F};
        }
        float[] cached = tips.get(img);
        if (cached != null) {
            return cached;
        }
        int w = img.getWidth();
        int h = img.getHeight();
        int[] argb = img.getRGB(0, 0, w, h, null, 0, w);
        int best = Integer.MIN_VALUE;
        float[] tip = {1F, 1F, 0.5F};
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                if ((argb[j * w + i] >>> 24) >= TIP_ALPHA && i - j > best) {
                    best = i - j;
                    tip = new float[] {(i + 0.5F) / w, (h - 1 - j + 0.5F) / h, 0.5F};
                }
            }
        }
        tips.put(img, tip);
        return tip;
    }

    /** Draws a part and keeps the corners of its boxes, grown by the part's inflate, in GUI space. */
    private void renderPart(ModelRenderer part, float inflate) {
        if (part.isHidden || !part.showModel) {
            return;
        }
        part.render(PX);
        GlStateManager.pushMatrix();
        part.postRender(PX);
        float[] toGui = multiply(guiInverse, modelview());
        GlStateManager.popMatrix();
        for (ModelBox box : part.cubeList) {
            corners.add(boxCorners(toGui,
                    (box.posX1 - inflate) * PX, (box.posY1 - inflate) * PX, (box.posZ1 - inflate) * PX,
                    (box.posX2 + inflate) * PX, (box.posY2 + inflate) * PX, (box.posZ2 + inflate) * PX));
        }
    }

    /**
     * RendererLivingEntity.setBrightness' hurt path on the lightmap unit: mix the lit texel toward red by
     * {@code amount}, keeping its alpha. The unit's own texture is only bound to make it complete.
     */
    private void hurtTint(float amount) {
        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.enableTexture2D();
        GlStateManager.bindTexture(TextureUtil.missingTexture.getGlTextureId());
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, OpenGlHelper.GL_COMBINE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_COMBINE_RGB, OpenGlHelper.GL_INTERPOLATE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_SOURCE0_RGB, OpenGlHelper.GL_CONSTANT);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_SOURCE1_RGB, OpenGlHelper.GL_PREVIOUS);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_SOURCE2_RGB, OpenGlHelper.GL_CONSTANT);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_OPERAND0_RGB, GL11.GL_SRC_COLOR);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_OPERAND1_RGB, GL11.GL_SRC_COLOR);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_OPERAND2_RGB, GL11.GL_SRC_ALPHA);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_COMBINE_ALPHA, GL11.GL_REPLACE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_SOURCE0_ALPHA, OpenGlHelper.GL_PREVIOUS);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_OPERAND0_ALPHA, GL11.GL_SRC_ALPHA);
        hurtColor.clear();
        hurtColor.put(1F).put(0F).put(0F).put(amount);
        hurtColor.flip();
        GL11.glTexEnv(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_COLOR, hurtColor);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
    }

    /** Puts the lightmap unit back as RendererLivingEntity.unsetBrightness leaves it, then turns it off. */
    private static void clearTint() {
        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, OpenGlHelper.GL_COMBINE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_COMBINE_RGB, GL11.GL_MODULATE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_OPERAND0_RGB, GL11.GL_SRC_COLOR);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_OPERAND1_RGB, GL11.GL_SRC_COLOR);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_SOURCE0_RGB, GL11.GL_TEXTURE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_SOURCE1_RGB, OpenGlHelper.GL_PREVIOUS);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_COMBINE_ALPHA, GL11.GL_MODULATE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_OPERAND0_ALPHA, GL11.GL_SRC_ALPHA);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_SOURCE0_ALPHA, GL11.GL_TEXTURE);
        GlStateManager.disableTexture2D();
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
    }

    private float[] modelview() {
        matrix.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, matrix);
        float[] m = new float[16];
        matrix.get(m);
        return m;
    }

    /** {@code a * b} for column-major 4x4 matrices, as GL composes them. */
    static float[] multiply(float[] a, float[] b) {
        float[] r = new float[16];
        for (int c = 0; c < 4; c++) {
            for (int row = 0; row < 4; row++) {
                float s = 0F;
                for (int k = 0; k < 4; k++) {
                    s += a[k * 4 + row] * b[c * 4 + k];
                }
                r[c * 4 + row] = s;
            }
        }
        return r;
    }

    /** Inverse of a column-major affine matrix (bottom row 0, 0, 0, 1), as every modelview here is. */
    static float[] invertAffine(float[] m) {
        float a = m[0], b = m[4], c = m[8];
        float d = m[1], e = m[5], f = m[9];
        float g = m[2], h = m[6], k = m[10];
        float c00 = e * k - f * h, c01 = c * h - b * k, c02 = b * f - c * e;
        float c10 = f * g - d * k, c11 = a * k - c * g, c12 = c * d - a * f;
        float c20 = d * h - e * g, c21 = b * g - a * h, c22 = a * e - b * d;
        float det = a * c00 + b * c10 + c * c20;
        float inv = det == 0F ? 0F : 1F / det;
        float[] r = new float[16];
        r[0] = c00 * inv;
        r[4] = c01 * inv;
        r[8] = c02 * inv;
        r[1] = c10 * inv;
        r[5] = c11 * inv;
        r[9] = c12 * inv;
        r[2] = c20 * inv;
        r[6] = c21 * inv;
        r[10] = c22 * inv;
        float tx = m[12], ty = m[13], tz = m[14];
        r[12] = -(r[0] * tx + r[4] * ty + r[8] * tz);
        r[13] = -(r[1] * tx + r[5] * ty + r[9] * tz);
        r[14] = -(r[2] * tx + r[6] * ty + r[10] * tz);
        r[15] = 1F;
        return r;
    }

    /** Writes x, y, z of {@code m * (x, y, z, 1)} to {@code out} from {@code at}. */
    static void transformPoint(float[] m, float x, float y, float z, float[] out, int at) {
        out[at] = m[0] * x + m[4] * y + m[8] * z + m[12];
        out[at + 1] = m[1] * x + m[5] * y + m[9] * z + m[13];
        out[at + 2] = m[2] * x + m[6] * y + m[10] * z + m[14];
    }

    /** The box's 8 corners through {@code m} as x, y pairs, in the order {@link #lastPartCorners()} documents. */
    static float[] boxCorners(float[] m, float x0, float y0, float z0, float x1, float y1, float z1) {
        float[] out = new float[16];
        float[] p = new float[3];
        for (int i = 0; i < 8; i++) {
            int q = i & 3;
            float x = q == 1 || q == 2 ? x1 : x0;
            float y = q >= 2 ? y1 : y0;
            float z = i >= 4 ? z1 : z0;
            transformPoint(m, x, y, z, p, 0);
            out[i * 2] = p[0];
            out[i * 2 + 1] = p[1];
        }
        return out;
    }
}
