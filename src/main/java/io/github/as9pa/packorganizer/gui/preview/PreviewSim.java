package io.github.as9pa.packorganizer.gui.preview;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * What the preview player does with the held item: blocking, drawing and shooting the bow, throwing, eating, casting,
 * the effects the apple gives and sword hits on the model. One {@link #tick()} is one game tick (20 a second); the
 * projectiles and particles live in {@link #world()}.
 */
public final class PreviewSim {
    /** EntityPlayer eye height, where the apple crumbs come from. */
    static final double EYE_Y = 1.62;
    /** ItemFood.getMaxItemUseDuration. */
    static final int EAT_TICKS = 32;
    /** EntityLivingBase.hurtTime after a hit. */
    static final int HURT_TICKS = 10;
    /** RendererLivingEntity tints a hurt entity red at this alpha for the whole hurt time. */
    static final float HURT_TINT = 0.3f;

    private final Random rnd;
    private final PreviewPhysics physics;
    private final SwingTimer swing = new SwingTimer();
    private final List<Effect> effects = new ArrayList<>();
    private final List<Effect> effectView = Collections.unmodifiableList(effects);
    private HeldItem item = HeldItem.SWORD;
    private boolean enchanted;
    /** Right click is using the item: the sword blocks, the bow draws, the apple is eaten. */
    private boolean using;
    private int useTicks;
    private int hurtTicks;

    public PreviewSim(Random rnd) {
        this.rnd = rnd;
        physics = new PreviewPhysics(rnd);
        // EntityEnderPearl.onImpact: portal particles around the thrower and the fall damage flash.
        physics.setOnPearlLanded(() -> {
            physics.portalBurst();
            hurtTicks = HURT_TICKS;
        });
    }

    /** Switching items stops any use and reels the rod in; leaving the apple clears its effects. */
    public void setItem(HeldItem item) {
        if (item == this.item) {
            return;
        }
        if (this.item == HeldItem.GOLDEN_APPLE) {
            effects.clear();
        }
        this.item = item;
        stopUsing();
        physics.reelIn();
    }

    public void setEnchanted(boolean enchanted) {
        this.enchanted = enchanted;
    }

    /** The eased head direction; pitch is positive looking down. */
    public void setAim(float yawRad, float pitchRad) {
        physics.setAim(yawRad, pitchRad);
    }

    /** Right button down: the sword, bow and apple start their use; the others act at once with a swing. */
    public void rightPress() {
        switch (item) {
            case SWORD:
            case BOW:
            case GOLDEN_APPLE:
                if (!using) {
                    using = true;
                    useTicks = 0;
                }
                break;
            case ENDER_PEARL:
                swing.swing();
                physics.throwPearl();
                break;
            case SPLASH_POTION:
                swing.swing();
                physics.throwPotion();
                break;
            case FISHING_ROD:
                swing.swing();
                if (physics.rodOut()) {
                    physics.reelIn();
                } else {
                    physics.cast();
                }
                break;
        }
    }

    /** Right button up: the bow shoots (ItemBow.onPlayerStoppedUsing), an unfinished apple is not eaten. */
    public void rightRelease() {
        if (!using) {
            return;
        }
        if (item == HeldItem.BOW) {
            float f = useTicks / 20f;
            f = (f * f + f * 2) / 3;
            if (f >= 0.1f) {
                physics.shootArrow(Math.min(f, 1f));
            }
        }
        stopUsing();
    }

    /** A sword click released on the model (not the held item) is a hit; anything else swings the arm. */
    public void leftClick(boolean onModel) {
        if (onModel && item == HeldItem.SWORD) {
            hurtTicks = HURT_TICKS;
            physics.critBurst(false);
            if (enchanted) {
                physics.critBurst(true);
            }
        } else {
            swing.swing();
        }
    }

    public void tick() {
        if (hurtTicks > 0) {
            hurtTicks--;
        }
        for (Iterator<Effect> it = effects.iterator(); it.hasNext(); ) {
            if (--it.next().ticksLeft <= 0) {
                it.remove();
            }
        }
        swing.tick();
        physics.tick();
        if (using) {
            useTicks++;
            if (item == HeldItem.GOLDEN_APPLE) {
                eat();
            }
        }
        // EntityLivingBase.updatePotionEffects: a swirl particle on half the ticks in the mixed color.
        if (!effects.isEmpty() && rnd.nextBoolean()) {
            physics.spell(effectColor());
        }
    }

    public SwingTimer swing() {
        return swing;
    }

    public boolean blocking() {
        return using && item == HeldItem.SWORD;
    }

    public boolean aimingBow() {
        return using && item == HeldItem.BOW;
    }

    /** ItemBow.getModel: -1 standby, then pulling_0, pulling_1 and pulling_2. */
    public int bowFrame() {
        if (!aimingBow()) {
            return -1;
        }
        if (useTicks >= 18) {
            return 2;
        }
        if (useTicks > 13) {
            return 1;
        }
        return useTicks > 0 ? 0 : -1;
    }

    public boolean rodCast() {
        return physics.rodOut();
    }

    /** Ticks the current use has run, 0 when nothing is in use. */
    public int useTicks() {
        return useTicks;
    }

    public List<Effect> effects() {
        return effectView;
    }

    /** PotionHelper.calcPotionLiquidColor: each effect counts amplifier + 1 times; 0 with no effects. */
    public int effectColor() {
        int r = 0, g = 0, b = 0, n = 0;
        for (Effect e : effects) {
            for (int i = 0; i <= e.amplifier; i++) {
                r += (e.color >> 16) & 255;
                g += (e.color >> 8) & 255;
                b += e.color & 255;
                n++;
            }
        }
        return n == 0 ? 0 : (r / n) << 16 | (g / n) << 8 | (b / n);
    }

    /** Red tint alpha for the body and armor: vanilla holds it flat while hurt, so the partial tick is unused. */
    public float hurt(float partial) {
        return hurtTicks > 0 ? HURT_TINT : 0f;
    }

    public PreviewPhysics world() {
        return physics;
    }

    /** Adds the effect, replacing a running one of the same name. */
    void addEffect(Effect effect) {
        for (Iterator<Effect> it = effects.iterator(); it.hasNext(); ) {
            if (it.next().name.equals(effect.name)) {
                it.remove();
            }
        }
        effects.add(effect);
    }

    /** EntityPlayer.onUpdate for food in use: crumbs every 4 ticks once 25 or fewer remain, then the last bite. */
    private void eat() {
        int remaining = EAT_TICKS - useTicks;
        if (remaining > 0 && remaining <= 25 && remaining % 4 == 0) {
            physics.crumbs(5, EYE_Y);
        } else if (remaining <= 0) {
            physics.crumbs(16, EYE_Y);
            addEffect(Effect.regeneration());
            addEffect(Effect.absorption());
            stopUsing();
        }
    }

    private void stopUsing() {
        using = false;
        useTicks = 0;
    }
}
