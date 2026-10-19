package io.github.as9pa.packorganizer.gui.preview;

import static org.junit.Assert.*;

import io.github.as9pa.packorganizer.gui.preview.Particle.ParticleKind;
import java.util.Random;
import org.junit.Test;

public class PreviewPhysicsTest {
    /** Yaw 90 deg: flight along +x, so a fast arrow stays inside |z| <= 6. */
    private static final float SIDEWAYS = (float) (Math.PI / 2);
    private static final double EPS = 1e-9;

    private static PreviewPhysics physics() {
        return new PreviewPhysics(new Random(7));
    }

    private static void tick(PreviewPhysics w, int n) {
        for (int i = 0; i < n; i++) {
            w.tick();
        }
    }

    private static int count(PreviewPhysics w, ParticleKind kind) {
        int n = 0;
        for (Particle p : w.particles()) {
            if (p.kind == kind) {
                n++;
            }
        }
        return n;
    }

    @Test
    public void fullChargeArrowFollowsTheVanillaIntegrator() {
        PreviewPhysics w = physics();
        w.setAim(SIDEWAYS, 0f);
        w.shootArrow(1f);
        assertEquals(1, w.projectiles().size());
        Projectile arrow = w.projectiles().get(0);
        assertSame(Projectile.Kind.ARROW, arrow.kind);
        assertTrue(arrow.critical);

        // EntityArrow: move, then motion * 0.99 and motionY - 0.05, with the ground plane at y 0.
        double yaw = SIDEWAYS;
        double x = -Math.cos(yaw) * 0.16, y = 1.62 - 0.1, z = Math.sin(yaw) * 0.16;
        double vx = Math.sin(yaw) * 3, vy = 0, vz = Math.cos(yaw) * 3;
        assertIntegrates(w, arrow, x, y, z, vx, vy, vz, 0.99, 0.05, false, 30);
        assertTrue(arrow.onGround);
        assertTrue(w.projectiles().contains(arrow));
    }

    /**
     * Steps the reference integrator next to {@code w}, one game tick per step (the first step runs on the throw
     * itself), and checks the previous and current position and the ground flag after each. Vanilla moves, then
     * scales the motion by the drag and takes the gravity off; the fishing hook takes the gravity off first.
     */
    private static void assertIntegrates(PreviewPhysics w, Projectile p, double x, double y, double z, double vx,
            double vy, double vz, double drag, double gravity, boolean gravityFirst, int steps) {
        boolean ground = false;
        for (int step = 1; step <= steps; step++) {
            double ox = x, oy = y, oz = z;
            if (!ground) {
                double nx = x + vx, ny = y + vy, nz = z + vz;
                if (ny <= 0) {
                    double f = y / (y - ny);
                    nx = x + vx * f;
                    nz = z + vz * f;
                    ny = 0;
                    ground = true;
                }
                x = nx;
                y = ny;
                z = nz;
                if (!ground) {
                    vx *= drag;
                    vy = gravityFirst ? (vy - gravity) * drag : vy * drag - gravity;
                    vz *= drag;
                }
            }
            if (step > 1) {
                tick(w, 1);
            }
            assertEquals("x at step " + step, x, p.x, EPS);
            assertEquals("y at step " + step, y, p.y, EPS);
            assertEquals("z at step " + step, z, p.z, EPS);
            assertEquals("previous x at step " + step, ox, p.px, EPS);
            assertEquals("previous y at step " + step, oy, p.py, EPS);
            assertEquals("previous z at step " + step, oz, p.pz, EPS);
            assertEquals("ground at step " + step, ground, p.onGround);
        }
    }

    /** The vanilla heading in these axes: yaw 0 faces +z, pitch positive looks down, y aimed {@code offsetDeg} off. */
    private static double[] heading(double yaw, double pitch, double offsetDeg, double speed) {
        double x = Math.sin(yaw) * Math.cos(pitch), y = -Math.sin(pitch + Math.toRadians(offsetDeg));
        double z = Math.cos(yaw) * Math.cos(pitch), l = Math.sqrt(x * x + y * y + z * z);
        return new double[] {x / l * speed, y / l * speed, z / l * speed};
    }

    @Test
    public void halfChargeArrowLeavesAtOneAndAHalf() {
        PreviewPhysics w = physics();
        w.setAim(SIDEWAYS, (float) Math.toRadians(-10));
        w.shootArrow(0.5f);
        Projectile arrow = w.projectiles().get(0);
        double[] v = heading(SIDEWAYS, (float) Math.toRadians(-10), 0, 1.5);
        assertIntegrates(w, arrow, -Math.cos(SIDEWAYS) * 0.16, 1.52, Math.sin(SIDEWAYS) * 0.16, v[0], v[1], v[2],
                0.99, 0.05, false, 20);
    }

    @Test
    public void pearlFollowsTheThrowableIntegrator() {
        // EntityThrowable: getVelocity 1.5, getGravityVelocity 0.03, drag 0.99.
        PreviewPhysics w = physics();
        w.setAim(SIDEWAYS, (float) Math.toRadians(-30));
        w.throwPearl();
        Projectile pearl = w.projectiles().get(0);
        double[] v = heading(SIDEWAYS, (float) Math.toRadians(-30), 0, 1.5);
        assertIntegrates(w, pearl, -Math.cos(SIDEWAYS) * 0.16, 1.52, Math.sin(SIDEWAYS) * 0.16, v[0], v[1], v[2],
                0.99, 0.03, false, 25);
    }

    @Test
    public void potionLeavesTwentyDegreesHighAtHalfABlock() {
        // EntityPotion: getVelocity 0.5, getGravityVelocity 0.05, getInaccuracy -20 on the pitch of motionY only.
        PreviewPhysics w = physics();
        w.setAim(SIDEWAYS, 0f);
        w.throwPotion();
        Projectile potion = w.projectiles().get(0);
        double[] v = heading(SIDEWAYS, 0, -20, 0.5);
        assertTrue(v[1] > 0);
        assertIntegrates(w, potion, -Math.cos(SIDEWAYS) * 0.16, 1.52, Math.sin(SIDEWAYS) * 0.16, v[0], v[1], v[2],
                0.99, 0.05, false, 12);
    }

    @Test
    public void bobberTakesGravityBeforeDrag() {
        PreviewPhysics w = physics();
        w.setAim(SIDEWAYS, (float) Math.toRadians(-15));
        w.cast();
        Projectile b = w.projectiles().get(0);
        // The searched speed shows in the first step, taken on the cast; it keeps the look direction.
        double vx = b.x - b.px, vy = b.y - b.py, vz = b.z - b.pz, speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
        double[] d = heading(SIDEWAYS, (float) Math.toRadians(-15), 0, speed);
        assertEquals(d[0], vx, EPS);
        assertEquals(d[1], vy, EPS);
        assertEquals(d[2], vz, EPS);
        assertIntegrates(w, b, b.px, b.py, b.pz, vx, vy, vz, 0.92, 0.04, true, 40);
        assertTrue(b.onGround);
        assertEquals(2, Math.hypot(b.x, b.z), 0.15);
    }

    @Test
    public void drawPositionLerpsFromThePreviousStep() {
        PreviewPhysics w = physics();
        w.setAim(SIDEWAYS, (float) Math.toRadians(-20));
        w.throwPearl();
        tick(w, 4);
        Projectile p = w.projectiles().get(0);
        assertTrue(p.x != p.px && p.y != p.py);
        assertEquals(p.px, p.drawX(0f), EPS);
        assertEquals(p.py, p.drawY(0f), EPS);
        assertEquals(p.pz, p.drawZ(0f), EPS);
        assertEquals((p.px + p.x) / 2, p.drawX(0.5f), EPS);
        assertEquals((p.py + p.y) / 2, p.drawY(0.5f), EPS);
        assertEquals((p.pz + p.z) / 2, p.drawZ(0.5f), EPS);
        assertEquals(p.x, p.drawX(1f), EPS);
        assertEquals(p.y, p.drawY(1f), EPS);
        assertEquals(p.z, p.drawZ(1f), EPS);
    }

    @Test
    public void arrowFacingTurnsAFifthOfTheWayAndLerps() {
        PreviewPhysics w = physics();
        w.setAim(SIDEWAYS, (float) Math.toRadians(-30));
        w.shootArrow(1f);
        Projectile a = w.projectiles().get(0);
        // setThrowableHeading: faces the heading, yaw atan2(vx, vz), pitch 30 up.
        assertEquals(90, a.yaw, 1e-3);
        assertEquals(30, a.pitch, 1e-3);
        for (int t = 0; t < 5; t++) {
            float yaw = a.yaw, pitch = a.pitch;
            tick(w, 1);
            double mx = a.x - a.px, my = a.y - a.py, mz = a.z - a.pz;
            float toYaw = (float) Math.toDegrees(Math.atan2(mx, mz));
            float toPitch = (float) Math.toDegrees(Math.atan2(my, Math.sqrt(mx * mx + mz * mz)));
            assertEquals(yaw, a.prevYaw, 1e-4);
            assertEquals(pitch, a.prevPitch, 1e-4);
            assertEquals(pitch + (toPitch - pitch) * 0.2f, a.pitch, 1e-3);
            assertEquals(yaw + (toYaw - yaw) * 0.2f, a.yaw, 1e-3);
            assertTrue("pitch falls behind the motion", a.pitch > toPitch);
            assertEquals(a.prevPitch, a.drawPitch(0f), 1e-6);
            assertEquals((a.prevPitch + a.pitch) / 2, a.drawPitch(0.5f), 1e-4);
            assertEquals(a.pitch, a.drawPitch(1f), 1e-6);
            assertEquals(a.prevYaw, a.drawYaw(0f), 1e-6);
            assertEquals(a.yaw, a.drawYaw(1f), 1e-6);
        }
    }

    @Test
    public void criticalArrowTrailsFourCritsPerStep() {
        PreviewPhysics w = physics();
        w.setAim(SIDEWAYS, -0.3f);
        w.shootArrow(1f);
        Projectile a = w.projectiles().get(0);
        // EntityArrow: all 4 at once along the segment the arrow is about to be drawn along.
        assertEquals(4, w.particles().size());
        for (int k = 0; k < 4; k++) {
            Particle p = w.particles().get(k);
            assertEquals(a.px + (a.x - a.px) * k / 4, p.x, EPS);
            assertEquals(a.py + (a.y - a.py) * k / 4, p.y, EPS);
            assertEquals(a.pz + (a.z - a.pz) * k / 4, p.z, EPS);
            // 0.4 of the reversed motion, 0.2 up, give or take 0.01.
            assertEquals(-(a.x - a.px) * 0.4, p.vx, 0.01 + EPS);
            assertEquals((0.2 - (a.y - a.py)) * 0.4, p.vy, 0.01 + EPS);
        }
        tick(w, 1);
        assertEquals(8, w.particles().size());
        for (Particle p : w.particles()) {
            assertSame(ParticleKind.CRIT, p.kind);
            assertEquals(65, p.sprite);
        }
    }

    @Test
    public void halfChargeArrowIsNotCritical() {
        PreviewPhysics w = physics();
        w.setAim(SIDEWAYS, 0f);
        w.shootArrow(0.5f);
        assertEquals(1, w.projectiles().size());
        assertFalse(w.projectiles().get(0).critical);
        tick(w, 6);
        assertTrue(w.particles().isEmpty());
    }

    @Test
    public void tooWeakAShotShootsNothing() {
        PreviewPhysics w = physics();
        w.shootArrow(0.05f);
        assertTrue(w.projectiles().isEmpty());
    }

    @Test
    public void potionThrownStraightDownShatters() {
        PreviewPhysics w = physics();
        w.setAim(0f, (float) (Math.PI / 2));
        w.throwPotion();
        assertEquals(1, w.projectiles().size());
        assertTrue(w.projectiles().get(0).vy < 0);
        int ticks = 0;
        while (!w.projectiles().isEmpty() && ticks < 20) {
            w.tick();
            ticks++;
        }
        assertTrue("shattered within 20 ticks", w.projectiles().isEmpty());
        assertEquals(108, w.particles().size());
        assertEquals(8, count(w, ParticleKind.ITEM_CRACK));
        assertEquals(100, count(w, ParticleKind.SPELL_INSTANT));
        for (Particle p : w.particles()) {
            if (p.kind == ParticleKind.ITEM_CRACK) {
                assertTrue(p.bottle);
            } else {
                // 0xF82423 scaled by 0.75..1.
                assertTrue(p.r <= 0xF8 / 255f + 1e-6 && p.r >= 0.75f * 0xF8 / 255f - 1e-6);
                assertEquals(0x23 / (float) 0xF8, p.b / p.r, 1e-5);
                assertTrue(p.sprite >= 144 && p.sprite <= 151);
            }
        }
    }

    @Test
    public void pearlLandingFiresTheListenerOnce() {
        PreviewPhysics w = physics();
        int[] calls = {0};
        w.setOnPearlLanded(() -> calls[0]++);
        w.setAim(0f, (float) Math.toRadians(30));
        w.throwPearl();
        tick(w, 200);
        assertEquals(1, calls[0]);
        assertTrue(w.projectiles().isEmpty());
    }

    @Test
    public void pearlGivesUpAfterOneAndAHalfSeconds() {
        PreviewPhysics w = physics();
        int[] calls = {0};
        w.setOnPearlLanded(() -> calls[0]++);
        w.setAim(0f, (float) (-Math.PI / 2));
        w.throwPearl();
        tick(w, 30);
        assertEquals(1, w.projectiles().size());
        assertEquals(0, calls[0]);
        tick(w, 3);
        assertTrue(w.projectiles().isEmpty());
        assertEquals(1, calls[0]);
    }

    @Test
    public void portalBurstIsThirtyTwoPortalParticles() {
        PreviewPhysics w = physics();
        w.portalBurst();
        assertEquals(32, w.particles().size());
        for (Particle p : w.particles()) {
            assertSame(ParticleKind.PORTAL, p.kind);
            assertTrue(p.sprite >= 0 && p.sprite <= 7);
        }
    }

    @Test
    public void bobberLandsAboutTwoBlocksOut() {
        for (double pitchDeg : new double[] {0, 20}) {
            PreviewPhysics w = physics();
            w.setAim(0f, (float) Math.toRadians(pitchDeg));
            w.cast();
            assertTrue(w.rodOut());
            tick(w, 150);
            assertEquals(1, w.projectiles().size());
            Projectile b = w.projectiles().get(0);
            assertSame(Projectile.Kind.BOBBER, b.kind);
            assertTrue(b.onGround);
            assertEquals("pitch " + pitchDeg, 2, Math.hypot(b.x, b.z), 0.15);
        }
    }

    @Test
    public void bobberOutsideTheBoundsIsKept() {
        PreviewPhysics w = physics();
        w.setViewBounds(-0.5, 0.5, 0.5);
        w.setAim(SIDEWAYS, 0f);
        w.cast();
        tick(w, 300);
        assertTrue(w.rodOut());
        assertEquals(1, w.projectiles().size());
        assertTrue(w.projectiles().get(0).x > 0.5);
        w.reelIn();
        assertFalse(w.rodOut());
        assertTrue(w.projectiles().isEmpty());
    }

    @Test
    public void castingAgainReplacesTheBobber() {
        PreviewPhysics w = physics();
        w.cast();
        w.cast();
        assertEquals(1, w.projectiles().size());
        assertTrue(w.rodOut());
    }

    @Test
    public void arrowLandedRunsOnceWhenItSticks() {
        PreviewPhysics w = physics();
        int[] landed = {0};
        w.setOnArrowLanded(() -> landed[0]++);
        w.setAim(0f, (float) (Math.PI / 2));
        w.shootArrow(1f);
        Projectile arrow = w.projectiles().get(0);
        int ticks = 0;
        while (!arrow.stuck && ticks++ < 20) {
            assertEquals(0, landed[0]);
            w.tick();
        }
        assertTrue(arrow.stuck);
        assertEquals(1, landed[0]);
        tick(w, PreviewPhysics.ARROW_GROUND_TICKS + 5);
        assertTrue(w.projectiles().isEmpty());
        assertEquals(1, landed[0]);
    }

    @Test
    public void arrowFlyingOffTheViewLandsUnseenOnce() {
        PreviewPhysics w = physics();
        int[] landed = {0};
        w.setOnArrowLanded(() -> landed[0]++);
        w.setViewBounds(-1, 1, -10);
        w.setAim(SIDEWAYS, 0f);
        w.shootArrow(1f);
        Projectile arrow = w.projectiles().get(0);
        assertTrue(arrow.critical);
        assertFalse(arrow.hidden);
        boolean wasHidden = false;
        int ticks = 0;
        while (w.projectiles().contains(arrow) && ticks++ < 100) {
            int crits = count(w, ParticleKind.CRIT);
            w.tick();
            if (wasHidden) {
                // Once hidden it is never drawn again: it stays hidden until it is removed.
                assertTrue("hidden at tick " + ticks, arrow.hidden || !w.projectiles().contains(arrow));
            }
            if (arrow.hidden) {
                wasHidden = true;
                assertTrue("no crit trail while hidden", count(w, ParticleKind.CRIT) <= crits);
                assertFalse(arrow.stuck && w.projectiles().contains(arrow));
            }
        }
        assertTrue(wasHidden);
        assertTrue(arrow.x > 1);
        assertEquals(0, arrow.y, EPS);
        assertFalse(w.projectiles().contains(arrow));
        assertEquals(1, landed[0]);
        tick(w, PreviewPhysics.ARROW_GROUND_TICKS + 5);
        assertEquals(1, landed[0]);
    }

    @Test
    public void arrowLandingInsideTheViewSticksAsBefore() {
        PreviewPhysics w = physics();
        int[] landed = {0};
        w.setOnArrowLanded(() -> landed[0]++);
        w.setViewBounds(-1, 1, -10);
        w.setAim(SIDEWAYS, (float) (Math.PI / 2));
        w.shootArrow(0.5f);
        Projectile arrow = w.projectiles().get(0);
        int ticks = 0;
        while (!arrow.onGround) {
            w.tick();
            ticks++;
        }
        assertTrue(ticks < 20);
        tick(w, 3);
        assertFalse(arrow.hidden);
        assertTrue(arrow.stuck);
        assertEquals(1, landed[0]);
        assertTrue(arrow.shake > 0 && arrow.shake <= 7);
        tick(w, 90);
        assertEquals(0, arrow.shake);
        assertEquals(1, w.projectiles().size());
        assertFalse(arrow.hidden);
        tick(w, 12);
        assertTrue(w.projectiles().isEmpty());
        assertEquals(1, landed[0]);
    }

    @Test
    public void potionBrokeRunsOnceAfterTheShatter() {
        PreviewPhysics w = physics();
        int[] broke = {0};
        int[] particlesThen = {-1};
        w.setOnPotionBroke(() -> {
            broke[0]++;
            particlesThen[0] = count(w, ParticleKind.SPELL_INSTANT);
        });
        w.setAim(0f, (float) (Math.PI / 2));
        w.throwPotion();
        assertEquals(0, broke[0]);
        tick(w, 20);
        assertTrue(w.projectiles().isEmpty());
        assertEquals(1, broke[0]);
        assertEquals(100, particlesThen[0]);
    }

    @Test
    public void potionFlyingOffTheViewBreaksUnseenOnce() {
        PreviewPhysics w = physics();
        int[] broke = {0};
        w.setOnPotionBroke(() -> broke[0]++);
        w.setViewBounds(-0.5, 0.5, -10);
        w.setAim(SIDEWAYS, 0f);
        w.throwPotion();
        Projectile potion = w.projectiles().get(0);
        assertFalse(potion.hidden);
        tick(w, 3);
        assertTrue(potion.hidden);
        assertEquals(0, broke[0]);
        tick(w, 40);
        assertTrue(w.projectiles().isEmpty());
        assertEquals(1, broke[0]);
        // No shatter off the view: no glass cracks, no instant spell ring.
        assertTrue(w.particles().isEmpty());
    }

    @Test
    public void arrowPastMaxXIsHidden() {
        PreviewPhysics w = physics();
        w.setViewBounds(-1, 1, -10);
        w.setAim(SIDEWAYS, 0f);
        w.shootArrow(1f);
        assertEquals(1, w.projectiles().size());
        Projectile arrow = w.projectiles().get(0);
        assertFalse(arrow.hidden);
        tick(w, 1);
        assertTrue(arrow.x > 1);
        assertTrue(arrow.hidden);
        assertFalse(arrow.onGround);
    }

    @Test
    public void arrowTowardTheViewerIsHiddenPastZSixUntilItLands() {
        PreviewPhysics w = physics();
        int[] landed = {0};
        w.setOnArrowLanded(() -> landed[0]++);
        w.setAim(0f, -0.2f);
        w.shootArrow(1f);
        Projectile arrow = w.projectiles().get(0);
        tick(w, 12);
        assertTrue(arrow.hidden);
        assertTrue(arrow.z > PreviewPhysics.MAX_ABS_Z);
        assertEquals(0, landed[0]);
        int ticks = 0;
        while (!w.projectiles().isEmpty() && ticks++ < 100) {
            w.tick();
        }
        assertTrue(w.projectiles().isEmpty());
        assertEquals(1, landed[0]);
    }

    @Test
    public void stuckArrowDisappearsAfterOneHundredTicks() {
        PreviewPhysics w = physics();
        w.setAim(SIDEWAYS, (float) (Math.PI / 2));
        w.shootArrow(0.5f);
        int ticks = 0;
        Projectile arrow = w.projectiles().get(0);
        while (!arrow.onGround) {
            w.tick();
            ticks++;
        }
        assertTrue(ticks < 20);
        tick(w, 3);
        // Sticking sets 7 and the same step already counts one down, as in the mock.
        assertTrue(arrow.shake > 0 && arrow.shake <= 7);
        tick(w, 90);
        assertEquals(0, arrow.shake);
        assertEquals(1, w.projectiles().size());
        tick(w, 12);
        assertTrue(w.projectiles().isEmpty());
    }

    @Test
    public void crumbsSpawnNearTheMouth() {
        PreviewPhysics w = physics();
        double eyeY = 1.62;
        w.crumbs(5, eyeY);
        assertEquals(5, w.particles().size());
        for (Particle p : w.particles()) {
            assertSame(ParticleKind.ITEM_CRACK, p.kind);
            assertFalse(p.bottle);
            assertTrue(Math.abs(p.x) <= 0.15 + EPS);
            assertTrue(p.y >= eyeY - 0.9 - EPS && p.y <= eyeY - 0.3 + EPS);
            assertEquals(0.6, p.z, EPS);
            assertTrue(p.u0 >= 0 && p.u0 <= 0.75f && p.v0 >= 0 && p.v0 <= 0.75f);
        }
    }

    @Test
    public void critBurstRunsThreeTicks() {
        PreviewPhysics w = physics();
        w.critBurst(true);
        tick(w, 1);
        int first = w.particles().size();
        assertTrue(first > 0);
        for (Particle p : w.particles()) {
            assertSame(ParticleKind.MAGIC_CRIT, p.kind);
            assertEquals(66, p.sprite);
        }
        tick(w, 2);
        int third = w.particles().size();
        assertTrue(third > first);
        tick(w, 1);
        assertTrue(w.particles().size() <= third);
    }

    @Test
    public void spellAddsOneRisingMobSpell() {
        PreviewPhysics w = physics();
        w.spell(0xCD5CAB);
        assertEquals(1, w.particles().size());
        Particle p = w.particles().get(0);
        assertSame(ParticleKind.SPELL_MOB, p.kind);
        assertEquals(0xCD / 255f, p.r, 1e-6);
        assertEquals(0x5C / 255f, p.g, 1e-6);
        assertEquals(0xAB / 255f, p.b, 1e-6);
        assertEquals(135, p.sprite);
    }

    @Test
    public void seededRandomMakesRunsIdentical() {
        assertEquals(run(42), run(42));
    }

    private static String run(long seed) {
        PreviewPhysics w = new PreviewPhysics(new Random(seed));
        w.setAim(0.4f, -0.3f);
        w.shootArrow(1f);
        w.throwPotion();
        w.throwPearl();
        w.cast();
        w.critBurst(false);
        w.critBurst(true);
        w.portalBurst();
        w.spell(0x2552A5);
        w.crumbs(5, 1.62);
        StringBuilder sb = new StringBuilder();
        for (int t = 0; t < 60; t++) {
            w.tick();
            for (Projectile p : w.projectiles()) {
                sb.append(p.kind).append(p.x).append(p.y).append(p.z).append(p.onGround).append(p.shake).append('|');
            }
            for (Particle p : w.particles()) {
                sb.append(p.kind).append(p.x).append(p.y).append(p.z).append(p.age).append(p.sprite).append(p.r)
                        .append(p.g).append(p.b).append(p.scale).append('|');
            }
        }
        return sb.toString();
    }
}
