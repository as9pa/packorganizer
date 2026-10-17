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
        boolean ground = false;
        for (int step = 1; step <= 30; step++) {
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
                    vx *= 0.99;
                    vy = vy * 0.99 - 0.05;
                    vz *= 0.99;
                }
            }
            // The first step runs on the shot itself, then one every 3 ticks.
            if (step > 1) {
                tick(w, 3);
            }
            assertEquals("x at step " + step, x, arrow.x, EPS);
            assertEquals("y at step " + step, y, arrow.y, EPS);
            assertEquals("z at step " + step, z, arrow.z, EPS);
            assertEquals("ground at step " + step, ground, arrow.onGround);
        }
        assertTrue(ground);
        assertTrue(w.projectiles().contains(arrow));
    }

    @Test
    public void criticalArrowTrailsFourCritsPerStep() {
        PreviewPhysics w = physics();
        w.setAim(SIDEWAYS, -0.3f);
        w.shootArrow(1f);
        tick(w, 3);
        // All 4 of the first segment, then the first of the second.
        assertEquals(5, w.particles().size());
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
    public void arrowPastMaxXIsRemoved() {
        PreviewPhysics w = physics();
        w.setViewBounds(-1, 1, -10);
        w.setAim(SIDEWAYS, 0f);
        w.shootArrow(1f);
        assertEquals(1, w.projectiles().size());
        tick(w, 6);
        assertTrue(w.projectiles().isEmpty());
    }

    @Test
    public void arrowTowardTheViewerIsRemovedPastZSix() {
        PreviewPhysics w = physics();
        w.setAim(0f, -0.2f);
        w.shootArrow(1f);
        tick(w, 12);
        assertTrue(w.projectiles().isEmpty());
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
