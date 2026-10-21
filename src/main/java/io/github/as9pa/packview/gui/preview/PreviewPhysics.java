package io.github.as9pa.packview.gui.preview;

import io.github.as9pa.packview.gui.preview.Particle.ParticleKind;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Projectiles and particles of the player preview, in model space: blocks, feet at y 0 with an invisible ground there,
 * +z toward the viewer, yaw 0 facing +z and the right hand toward -x. One {@link #tick()} is one game tick (20 a
 * second) and, as in vanilla, one physics step of every projectile.
 */
public final class PreviewPhysics {
    /** EntityThrowable and EntityArrow spawn at eye height (1.62) minus 0.1 ... */
    static final double SPAWN_Y = 1.62 - 0.1;
    /** ... and 0.16 toward the right hand. */
    static final double HAND_OFFSET = 0.16;
    /** EntityArrow and EntityThrowable turn a fifth of the way to the motion each step. */
    static final float TURN = 0.2f;
    static final int ARROW_SHAKE = 7;
    static final int ARROW_GROUND_TICKS = 100;
    /** A pearl that has not landed after 1.5 s arrives anyway. */
    static final int PEARL_MAX_TICKS = 30;
    /** A hidden projectile still flying this many ticks after the throw is dropped, with no landing. */
    static final int HIDDEN_MAX_TICKS = 200;
    static final double MAX_ABS_Z = 6;
    /** The cast is shortened so the bobber lands about this far out. */
    static final double CAST_DISTANCE = 2;
    /** EntityFishHook in air: motionY -= 0.04, then motion *= 0.92 (gravity before drag, unlike the others). */
    static final double BOBBER_DRAG = 0.92, BOBBER_GRAVITY = 0.04;
    /** PotionHelper color of the splash potion (Healing). */
    static final int HEALING = 0xF82423;
    static final int CRIT_SPRITE = 65, MAGIC_CRIT_SPRITE = 66, SPELL_MOB_BASE = 128, SPELL_INSTANT_BASE = 144;

    private final Random rnd;
    private final List<Projectile> projectiles = new ArrayList<>();
    private final List<Particle> particles = new ArrayList<>();
    private final List<Emitter> emitters = new ArrayList<>();
    private final List<Projectile> projectileView = Collections.unmodifiableList(projectiles);
    private final List<Particle> particleView = Collections.unmodifiableList(particles);
    private double yaw, pitch;
    private double minX = Double.NEGATIVE_INFINITY, maxX = Double.POSITIVE_INFINITY, minY = Double.NEGATIVE_INFINITY;
    private Projectile bobber;
    private Runnable onPearlLanded;
    private Runnable onArrowLanded;
    private Runnable onPotionBroke;

    /** EntityParticleEmitter: 3 ticks of crit bursts from the middle of the body. */
    private static final class Emitter {
        final boolean magic;
        int age;

        Emitter(boolean magic) {
            this.magic = magic;
        }
    }

    public PreviewPhysics(Random rnd) {
        this.rnd = rnd;
    }

    /** The eased head direction; pitch is positive looking down. */
    public void setAim(float yawRad, float pitchRad) {
        yaw = yawRad;
        pitch = pitchRad;
    }

    /**
     * Flying projectiles past these (blocks) are hidden until they land; above the top they stay shown, the arc
     * brings them back.
     */
    public void setViewBounds(double minX, double maxX, double minY) {
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
    }

    /** Runs when a pearl lands or gives up; the listener emits {@link #portalBurst()} and plays the teleport sound. */
    public void setOnPearlLanded(Runnable listener) {
        onPearlLanded = listener;
    }

    /** Runs when an arrow sticks in the ground, also one that flew off the view and lands unseen. */
    public void setOnArrowLanded(Runnable listener) {
        onArrowLanded = listener;
    }

    /**
     * Runs when a splash potion shatters on the ground, after its particles; one that flew off the view runs it when
     * it lands unseen, with no particles.
     */
    public void setOnPotionBroke(Runnable listener) {
        onPotionBroke = listener;
    }

    /** ItemBow.onPlayerStoppedUsing with the charge already eased (0..1); below 0.1 nothing is shot. */
    public void shootArrow(float charge) {
        if (charge < 0.1f) {
            return;
        }
        float f = Math.min(charge, 1f);
        // new EntityArrow(world, player, f * 2) aims at velocity * 1.5.
        Projectile p = launch(Projectile.Kind.ARROW, look(0), f * 3.0, 0.99, 0.05);
        p.critical = f >= 1f;
        critTrail(p);
    }

    public void throwPearl() {
        launch(Projectile.Kind.PEARL, look(0), 1.5, 0.99, 0.03);
    }

    /** EntityPotion: aimed 20 degrees above the look direction. */
    public void throwPotion() {
        launch(Projectile.Kind.POTION, look(Math.toRadians(-20)), 0.5, 0.99, 0.05);
    }

    /** Shortened cast: the speed is searched so the bobber lands about {@link #CAST_DISTANCE} out. */
    public void cast() {
        reelIn();
        double[] d = look(0);
        double lo = 0.02, hi = 3;
        for (int i = 0; i < 40; i++) {
            double mid = (lo + hi) / 2;
            if (castDistance(d, mid) < CAST_DISTANCE) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        bobber = launch(Projectile.Kind.BOBBER, d, lo, BOBBER_DRAG, BOBBER_GRAVITY);
    }

    public void reelIn() {
        if (bobber != null) {
            gone(bobber);
        }
    }

    public boolean rodOut() {
        return bobber != null;
    }

    /** EntityLivingBase.updateItemUse for EAT: item crack particles of the apple in front of the mouth. */
    public void crumbs(int n, double eyeY) {
        for (int i = 0; i < n; i++) {
            double[] v = rotate((rnd.nextDouble() - 0.5) * 0.1, rnd.nextDouble() * 0.1 + 0.1, 0);
            double[] p = rotate((rnd.nextDouble() - 0.5) * 0.3, -rnd.nextDouble() * 0.6 - 0.3, 0.6);
            crack(false, p[0], p[1] + eyeY, p[2], v[0], v[1] + 0.05, v[2]);
        }
    }

    /** A sword hit: 3 ticks of 16 tries each, EntityCrit2FX look; magic is CRIT_MAGIC (sprite 66, bluish). */
    public void critBurst(boolean magic) {
        emitters.add(new Emitter(magic));
    }

    /** EntityEnderPearl.onImpact: 32 portal particles around the model, easing in while sinking 1 block. */
    public void portalBurst() {
        for (int i = 0; i < 32; i++) {
            double f = rnd.nextDouble() * 0.6 + 0.4, oy = rnd.nextDouble() * 2;
            double vx = rnd.nextGaussian(), vz = rnd.nextGaussian();
            Particle p = spawn(ParticleKind.PORTAL, vx, oy + 1, vz, vx, 0, vz, rnd.nextInt(10) + 40,
                    (float) (0.1 * (rnd.nextDouble() * 0.2 + 0.5)));
            p.sprite = rnd.nextInt(8);
            color(p, f * 0.9, f * 0.3, f);
            p.oy = oy;
        }
    }

    /** One SPELL_MOB swirl particle inside the body box (updatePotionEffects), in the given color. */
    public void spell(int rgb) {
        spawnSpell(ParticleKind.SPELL_MOB, (rnd.nextDouble() - 0.5) * 0.6, rnd.nextDouble() * 1.8,
                (rnd.nextDouble() - 0.5) * 0.6, (rnd.nextDouble() - 0.5) * 0.01, rnd.nextDouble() * 0.01,
                (rnd.nextDouble() - 0.5) * 0.01, red(rgb), green(rgb), blue(rgb));
    }

    public void tick() {
        for (Projectile p : new ArrayList<>(projectiles)) {
            p.age++;
            step(p);
            if (!projectiles.contains(p)) {
                continue;
            }
            critTrail(p);
            if (p.stuck && p.kind == Projectile.Kind.ARROW && ++p.groundTicks >= ARROW_GROUND_TICKS) {
                gone(p);
            }
        }
        particles.removeIf(this::stepParticle);
        for (Iterator<Emitter> it = emitters.iterator(); it.hasNext(); ) {
            Emitter em = it.next();
            for (int k = 0; k < 16; k++) {
                double d0 = rnd.nextDouble() * 2 - 1, d1 = rnd.nextDouble() * 2 - 1, d2 = rnd.nextDouble() * 2 - 1;
                if (d0 * d0 + d1 * d1 + d2 * d2 > 1) {
                    continue;
                }
                double c = rnd.nextDouble() * 0.3 + 0.6;
                Particle p = spawn(em.magic ? ParticleKind.MAGIC_CRIT : ParticleKind.CRIT,
                        d0 * 0.6 / 4, 0.9 + d1 * 1.8 / 4, d2 * 0.6 / 4,
                        d0 * 0.4 + (rnd.nextDouble() - 0.5) * 0.02, (d1 + 0.2) * 0.4 + (rnd.nextDouble() - 0.5) * 0.02,
                        d2 * 0.4 + (rnd.nextDouble() - 0.5) * 0.02,
                        (int) (6 / (rnd.nextDouble() * 0.8 + 0.6)), (float) (0.1 * 0.75 * fxScale()));
                p.sprite = em.magic ? MAGIC_CRIT_SPRITE : CRIT_SPRITE;
                if (em.magic) {
                    color(p, c * 0.3, c * 0.8, c);
                } else {
                    color(p, c, c, c);
                }
            }
            if (++em.age >= 3) {
                it.remove();
            }
        }
    }

    /**
     * True while an arrow, pearl or potion has not landed (hidden ones included) or a stuck arrow still shakes. The
     * bobber is left out: it stays cast until reeled in.
     */
    public boolean flying() {
        for (Projectile p : projectiles) {
            if (p.kind != Projectile.Kind.BOBBER && (!p.stuck || p.shake > 0)) {
                return true;
            }
        }
        return false;
    }

    /** Every projectile still in flight or stuck, hidden ones included; drawing skips the hidden. */
    public List<Projectile> projectiles() {
        return projectileView;
    }

    public List<Particle> particles() {
        return particleView;
    }

    /** Spawn like EntityThrowable and EntityArrow, then take the first step at once. */
    private Projectile launch(Projectile.Kind kind, double[] dir, double speed, double drag, double gravity) {
        Projectile p = new Projectile(kind, -Math.cos(yaw) * HAND_OFFSET, SPAWN_Y, Math.sin(yaw) * HAND_OFFSET,
                dir[0] * speed, dir[1] * speed, dir[2] * speed, drag, gravity);
        // setThrowableHeading faces the heading at once, with no turn to ease.
        p.yaw = p.prevYaw = heading(p.vx, p.vz);
        p.pitch = p.prevPitch = elevation(p.vx, p.vy, p.vz);
        projectiles.add(p);
        step(p);
        return p;
    }

    /**
     * One physics step: the previous position and facing are kept for drawing, the projectile moves by its motion,
     * turns toward it, then drag and gravity apply. The landing is handled one step after touchdown, once the last
     * segment has been drawn.
     */
    private void step(Projectile p) {
        if (p.onGround && !p.stuck) {
            land(p);
            if (!projectiles.contains(p)) {
                return;
            }
        }
        // The bobber stays cast wherever it goes (the view clips it); only reeling in removes it.
        if (!p.onGround && p.kind != Projectile.Kind.BOBBER) {
            if ((p.kind == Projectile.Kind.PEARL && p.age > PEARL_MAX_TICKS)
                    || (p.hidden && p.age > HIDDEN_MAX_TICKS)) {
                gone(p);
                return;
            }
            // Off the view it flies on unseen, so its landing still sounds.
            if (outside(p) || Math.abs(p.z) > MAX_ABS_Z) {
                p.hidden = true;
            }
        }
        p.px = p.x;
        p.py = p.y;
        p.pz = p.z;
        p.prevYaw = p.yaw;
        p.prevPitch = p.pitch;
        if (p.onGround) {
            if (p.shake > 0) {
                p.shake--;
            }
            return;
        }
        double nx = p.x + p.vx, ny = p.y + p.vy, nz = p.z + p.vz;
        if (ny <= 0) {
            double f = p.y / (p.y - ny);
            nx = p.x + p.vx * f;
            nz = p.z + p.vz * f;
            ny = 0;
            p.onGround = true;
        }
        p.x = nx;
        p.y = ny;
        p.z = nz;
        turn(p);
        if (!p.onGround) {
            p.vx *= p.drag;
            if (p.kind == Projectile.Kind.BOBBER) {
                p.vy = (p.vy - p.gravity) * p.drag;
            } else {
                p.vy = p.vy * p.drag - p.gravity;
            }
            p.vz *= p.drag;
        }
    }

    /**
     * EntityArrow and EntityThrowable after the move: the facing of the motion just traveled, eased by {@link #TURN}
     * from the previous one, the previous one first moved by 360 degrees so the ease (and the drawn lerp) takes the
     * short way round.
     */
    private static void turn(Projectile p) {
        float yaw = heading(p.vx, p.vz), pitch = elevation(p.vx, p.vy, p.vz);
        while (pitch - p.prevPitch < -180f) {
            p.prevPitch -= 360f;
        }
        while (pitch - p.prevPitch >= 180f) {
            p.prevPitch += 360f;
        }
        while (yaw - p.prevYaw < -180f) {
            p.prevYaw -= 360f;
        }
        while (yaw - p.prevYaw >= 180f) {
            p.prevYaw += 360f;
        }
        p.pitch = p.prevPitch + (pitch - p.prevPitch) * TURN;
        p.yaw = p.prevYaw + (yaw - p.prevYaw) * TURN;
    }

    static float heading(double vx, double vz) {
        return (float) Math.toDegrees(Math.atan2(vx, vz));
    }

    static float elevation(double vx, double vy, double vz) {
        return (float) Math.toDegrees(Math.atan2(vy, Math.sqrt(vx * vx + vz * vz)));
    }

    private boolean outside(Projectile p) {
        return p.x < minX || p.x > maxX || p.y < minY;
    }

    private void land(Projectile p) {
        p.stuck = true;
        if (p.kind == Projectile.Kind.ARROW) {
            p.shake = ARROW_SHAKE;
            p.groundTicks = 0;
            if (onArrowLanded != null) {
                onArrowLanded.run();
            }
            // Nobody sees a hidden arrow stick, so it does not stay.
            if (p.hidden) {
                gone(p);
            }
        } else if (p.kind == Projectile.Kind.PEARL) {
            gone(p);
        } else if (p.kind == Projectile.Kind.POTION) {
            if (!p.hidden) {
                shatter(p.x, p.z);
            }
            gone(p);
            if (onPotionBroke != null) {
                onPotionBroke.run();
            }
        }
    }

    private void gone(Projectile p) {
        if (!projectiles.remove(p)) {
            return;
        }
        if (p.kind == Projectile.Kind.PEARL && onPearlLanded != null) {
            onPearlLanded.run();
        }
        if (p == bobber) {
            bobber = null;
        }
    }

    /** Horizontal distance from the model where a bobber launched at this speed meets the ground. */
    private double castDistance(double[] d, double speed) {
        double x = -Math.cos(yaw) * HAND_OFFSET, y = SPAWN_Y, z = Math.sin(yaw) * HAND_OFFSET;
        double vx = d[0] * speed, vy = d[1] * speed, vz = d[2] * speed;
        for (int i = 0; i < 400; i++) {
            double ny = y + vy;
            if (ny <= 0) {
                double f = y / (y - ny);
                x += vx * f;
                z += vz * f;
                break;
            }
            x += vx;
            y = ny;
            z += vz;
            vx *= BOBBER_DRAG;
            vy = (vy - BOBBER_GRAVITY) * BOBBER_DRAG;
            vz *= BOBBER_DRAG;
        }
        return Math.hypot(x, z);
    }

    /** RenderGlobal.playAuxSFX 2002: glass cracks and a ring of instant spell, scaled to spread about 1.5 blocks. */
    private void shatter(double x, double z) {
        for (int i = 0; i < 8; i++) {
            crack(true, x, 0.05, z, rnd.nextGaussian() * 0.15, rnd.nextDouble() * 0.2, rnd.nextGaussian() * 0.15);
        }
        float r = red(HEALING), g = green(HEALING), b = blue(HEALING);
        for (int i = 0; i < 100; i++) {
            double d = rnd.nextDouble() * 4, a = rnd.nextDouble() * Math.PI * 2;
            float f = (float) (0.75 + rnd.nextDouble() * 0.25);
            spawnSpell(ParticleKind.SPELL_INSTANT, x + Math.cos(a) * d * 0.1, 0.3, z + Math.sin(a) * d * 0.1,
                    Math.cos(a) * d * 0.027, (0.01 + rnd.nextDouble() * 0.5) * 0.2, Math.sin(a) * d * 0.027,
                    r * f, g * f, b * f);
        }
    }

    /**
     * EntityArrow's crit trail: 4 EntityCritFX at quarters of the segment the arrow is about to be drawn along, all
     * at once, with 0.4 of (-motion + 0.2 up) as their speed. None on the landing segment, none while hidden.
     */
    private void critTrail(Projectile p) {
        if (!p.critical || p.onGround || p.hidden) {
            return;
        }
        double mx = p.x - p.px, my = p.y - p.py, mz = p.z - p.pz;
        for (int k = 0; k < 4; k++) {
            double f = k / 4.0;
            double c = rnd.nextDouble() * 0.3 + 0.6;
            Particle q = spawn(ParticleKind.CRIT, p.px + mx * f, p.py + my * f, p.pz + mz * f,
                    (rnd.nextDouble() - 0.5) * 0.02 - mx * 0.4, (rnd.nextDouble() - 0.5) * 0.02 + (0.2 - my) * 0.4,
                    (rnd.nextDouble() - 0.5) * 0.02 - mz * 0.4,
                    (int) (6 / (rnd.nextDouble() * 0.8 + 0.6)), (float) (0.1 * 0.75 * fxScale()));
            q.sprite = CRIT_SPRITE;
            color(q, c, c, c);
        }
    }

    /** EntityBreakingFX: a 4x4 texel patch of the item sprite, half scale, gravity 0.04, drag 0.98. */
    private void crack(boolean bottle, double x, double y, double z, double vx, double vy, double vz) {
        Particle p = spawn(ParticleKind.ITEM_CRACK, x, y, z, vx + (rnd.nextDouble() - 0.5) * 0.02,
                vy + rnd.nextDouble() * 0.01, vz + (rnd.nextDouble() - 0.5) * 0.02,
                (int) (4 / (rnd.nextDouble() * 0.9 + 0.1)), (float) (0.1 * fxScale() / 2));
        p.u0 = (float) (rnd.nextDouble() * (1 - Particle.PATCH));
        p.v0 = (float) (rnd.nextDouble() * (1 - Particle.PATCH));
        p.bottle = bottle;
        p.sprite = -1;
        color(p, 1, 1, 1);
    }

    /** EntitySpellParticleFX: 8 frames from the base, played backward over the life, rising. */
    private void spawnSpell(ParticleKind kind, double x, double y, double z, double vx, double vy, double vz,
            float r, float g, float b) {
        Particle p = spawn(kind, x, y, z, vx, vy, vz, (int) (8 / (rnd.nextDouble() * 0.8 + 0.2)),
                (float) (0.1 * 0.75 * fxScale()));
        p.base = kind == ParticleKind.SPELL_MOB ? SPELL_MOB_BASE : SPELL_INSTANT_BASE;
        p.sprite = p.base + 7;
        p.r = r;
        p.g = g;
        p.b = b;
    }

    private Particle spawn(ParticleKind kind, double x, double y, double z, double vx, double vy, double vz,
            int maxAge, float scale) {
        Particle p = new Particle(kind, x, y, z, vx, vy, vz, maxAge, scale);
        particles.add(p);
        return p;
    }

    /** One game tick of a particle; true when it has expired. */
    private boolean stepParticle(Particle p) {
        p.px = p.x;
        p.py = p.y;
        p.pz = p.z;
        if (p.age++ >= p.maxAge) {
            return true;
        }
        if (p.kind == ParticleKind.PORTAL) {
            // EntityPortalFX: eases in from its offset to the origin while sinking 1 block.
            double a = (double) p.age / p.maxAge, f = 1 - (-a + a * a * 2);
            p.x = p.ox + p.vx * f;
            p.y = p.oy + p.vy * f + (1 - a);
            p.z = p.oz + p.vz * f;
            return false;
        }
        boolean crit = p.kind == ParticleKind.CRIT || p.kind == ParticleKind.MAGIC_CRIT;
        boolean spell = p.kind == ParticleKind.SPELL_MOB || p.kind == ParticleKind.SPELL_INSTANT;
        if (spell) {
            p.sprite = p.base + Math.max(0, 7 - p.age * 8 / p.maxAge);
            p.vy += 0.004;
        }
        if (p.kind == ParticleKind.ITEM_CRACK) {
            p.vy -= 0.04;
        }
        p.x += p.vx;
        p.y += p.vy;
        p.z += p.vz;
        double drag = crit ? 0.7 : p.kind == ParticleKind.ITEM_CRACK ? 0.98 : 0.96;
        p.vx *= drag;
        p.vy *= drag;
        p.vz *= drag;
        if (crit) {
            p.vy -= 0.02;
            p.g *= 0.96f;
            p.b *= 0.9f;
        }
        if (p.y < 0) {
            p.y = 0;
            p.vx *= 0.7;
            p.vz *= 0.7;
            p.vy = 0;
        }
        return false;
    }

    /** Vanilla heading: x and z from the pitch, y from pitch + offset (EntityPotion -20 deg), normalized. */
    private double[] look(double offset) {
        double x = Math.sin(yaw) * Math.cos(pitch), y = -Math.sin(pitch + offset), z = Math.cos(yaw) * Math.cos(pitch);
        double l = Math.sqrt(x * x + y * y + z * z);
        return new double[] {x / l, y / l, z / l};
    }

    /** Vec3.rotatePitch(-pitch) then rotateYaw(-yaw); vanilla yaw is -yaw in these axes. */
    private double[] rotate(double x, double y, double z) {
        double cp = Math.cos(-pitch), sp = Math.sin(-pitch), cy = Math.cos(yaw), sy = Math.sin(yaw);
        double ry = y * cp + z * sp, rz = z * cp - y * sp;
        return new double[] {x * cy + rz * sy, ry, rz * cy - x * sy};
    }

    /** EntityFX.particleScale. */
    private double fxScale() {
        return (rnd.nextDouble() * 0.5 + 0.5) * 2;
    }

    private static void color(Particle p, double r, double g, double b) {
        p.r = (float) r;
        p.g = (float) g;
        p.b = (float) b;
    }

    private static float red(int rgb) {
        return (rgb >> 16 & 255) / 255f;
    }

    private static float green(int rgb) {
        return (rgb >> 8 & 255) / 255f;
    }

    private static float blue(int rgb) {
        return (rgb & 255) / 255f;
    }
}
