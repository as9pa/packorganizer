package io.github.as9pa.packorganizer.gui.preview.sound;

import java.util.Random;

/**
 * Vanilla 1.8.9 volume and pitch for each moment the preview plays a sound (Random passed in so tests are
 * deterministic). Volumes not listed here are 1.0. These are the raw values vanilla passes to playSound; its
 * SoundManager then clamps pitch to 0.5..2.0 before playing.
 */
public final class VanillaSoundRules {
    /** ItemEnderPearl, splash ItemPotion and ItemFishingRod casting: random.bow at half volume. */
    public static final float THROW_VOLUME = 0.5F;
    /** ItemFood.onItemUseFinish: random.burp at half volume. */
    public static final float BURP_VOLUME = 0.5F;

    private VanillaSoundRules() {}

    /** EntityLivingBase.attackEntityFrom (an adult): game.player.hurt, volume 1.0. */
    public static float hurtPitch(Random r) {
        return (r.nextFloat() - r.nextFloat()) * 0.2F + 1.0F;
    }

    /** ItemBow.onPlayerStoppedUsing: random.bow, volume 1.0; {@code charge} is the bow's f, 0..1. */
    public static float bowReleasePitch(Random r, float charge) {
        return 1.0F / (r.nextFloat() * 0.4F + 1.2F) + charge * 0.5F;
    }

    /** ItemEnderPearl, splash ItemPotion and ItemFishingRod casting: random.bow, volume {@link #THROW_VOLUME}. */
    public static float throwPitch(Random r) {
        return 0.4F / (r.nextFloat() * 0.4F + 0.8F);
    }

    /** EntityArrow hitting a block: random.bowhit, volume 1.0. */
    public static float bowHitPitch(Random r) {
        return 1.2F / (r.nextFloat() * 0.2F + 0.9F);
    }

    /** EntityLivingBase.updateItemUse for food: random.eat. Vanilla draws the volume before the pitch. */
    public static float eatVolume(Random r) {
        return 0.5F + 0.5F * (float) r.nextInt(2);
    }

    /** EntityLivingBase.updateItemUse for food: random.eat, drawn after {@link #eatVolume}. */
    public static float eatPitch(Random r) {
        return (r.nextFloat() - r.nextFloat()) * 0.2F + 1.0F;
    }

    /** ItemFood.onItemUseFinish: random.burp, volume {@link #BURP_VOLUME}. */
    public static float burpPitch(Random r) {
        return r.nextFloat() * 0.1F + 0.9F;
    }

    /** RenderGlobal.playAuxSFX 2002, a splash potion breaking: game.potion.smash, volume 1.0. */
    public static float glassPitch(Random r) {
        return r.nextFloat() * 0.1F + 0.9F;
    }
}
