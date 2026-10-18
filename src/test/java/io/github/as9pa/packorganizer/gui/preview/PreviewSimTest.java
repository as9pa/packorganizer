package io.github.as9pa.packorganizer.gui.preview;

import static org.junit.Assert.*;

import io.github.as9pa.packorganizer.gui.preview.Particle.ParticleKind;
import java.util.List;
import java.util.Random;
import org.junit.Test;

public class PreviewSimTest {
    private static PreviewSim sim(HeldItem item) {
        PreviewSim s = new PreviewSim(new Random(7));
        s.setItem(item);
        return s;
    }

    private static void ticks(PreviewSim s, int n) {
        for (int i = 0; i < n; i++) {
            s.tick();
        }
    }

    private static PreviewSim drawBow(int ticks) {
        PreviewSim s = sim(HeldItem.BOW);
        s.rightPress();
        ticks(s, ticks);
        s.rightRelease();
        return s;
    }

    /** ITEM_CRACK particles spawned during the last tick. */
    private static int newCracks(PreviewSim s) {
        int n = 0;
        for (Particle p : s.world().particles()) {
            if (p.kind == ParticleKind.ITEM_CRACK && p.age == 0) {
                n++;
            }
        }
        return n;
    }

    private static int count(PreviewSim s, ParticleKind kind) {
        int n = 0;
        for (Particle p : s.world().particles()) {
            if (p.kind == kind) {
                n++;
            }
        }
        return n;
    }

    private static PreviewSim ateApple() {
        PreviewSim s = sim(HeldItem.GOLDEN_APPLE);
        s.rightPress();
        ticks(s, 32);
        s.rightRelease();
        return s;
    }

    private static void assertColor(int expected, int actual) {
        assertEquals((expected >> 16) & 255, (actual >> 16) & 255, 1);
        assertEquals((expected >> 8) & 255, (actual >> 8) & 255, 1);
        assertEquals(expected & 255, actual & 255, 1);
    }

    @Test
    public void idsMatchSettings() {
        assertEquals(HeldItem.SWORD, HeldItem.fromId("sword"));
        assertEquals(HeldItem.BOW, HeldItem.fromId("bow"));
        assertEquals(HeldItem.ENDER_PEARL, HeldItem.fromId("ender_pearl"));
        assertEquals(HeldItem.GOLDEN_APPLE, HeldItem.fromId("golden_apple"));
        assertEquals(HeldItem.SPLASH_POTION, HeldItem.fromId("splash_potion"));
        assertEquals(HeldItem.FISHING_ROD, HeldItem.fromId("fishing_rod"));
        assertEquals(HeldItem.NONE, HeldItem.fromId("none"));
        assertEquals(HeldItem.NONE, HeldItem.fromId("nonsense"));
        assertEquals(HeldItem.NONE, HeldItem.fromId(null));
    }

    @Test
    public void toggleSelectsThenDeselects() {
        assertEquals(HeldItem.SWORD, HeldItem.toggle(HeldItem.NONE, HeldItem.SWORD, HeldItem.NONE));
        assertEquals(HeldItem.NONE, HeldItem.toggle(HeldItem.SWORD, HeldItem.SWORD, HeldItem.NONE));
        assertEquals(HeldItem.BOW, HeldItem.toggle(HeldItem.SWORD, HeldItem.BOW, HeldItem.NONE));
        // Armor ids, where none is null.
        assertEquals("iron", HeldItem.toggle(null, "iron", null));
        assertNull(HeldItem.toggle("iron", "iron", null));
        assertEquals("gold", HeldItem.toggle("iron", "gold", null));
        // Item ids as the panel keeps them.
        assertEquals("none", HeldItem.toggle("bow", "bow", HeldItem.NONE.id));
        assertEquals("bow", HeldItem.toggle("none", "bow", HeldItem.NONE.id));
    }

    @Test
    public void emptyHandRightClickDoesNothing() {
        PreviewSim s = sim(HeldItem.NONE);
        s.rightPress();
        ticks(s, 25);
        assertFalse(s.blocking());
        assertFalse(s.aimingBow());
        assertEquals(0f, s.swing().progress(0.5f), 0f);
        s.rightRelease();
        ticks(s, 5);
        assertTrue(s.world().projectiles().isEmpty());
        assertTrue(s.world().particles().isEmpty());
        assertTrue(s.effects().isEmpty());
        assertFalse(s.rodCast());
    }

    @Test
    public void fistClickOnModelIsAHit() {
        PreviewSim s = sim(HeldItem.NONE);
        s.setEnchanted(true);   // nothing in the hand to carry the enchantment
        s.leftClick(true);
        assertEquals(0.3f, s.hurt(0), 0f);
        s.tick();
        assertTrue(count(s, ParticleKind.CRIT) > 0);
        assertEquals(0, count(s, ParticleKind.MAGIC_CRIT));
    }

    @Test
    public void fistClickOffModelSwings() {
        PreviewSim s = sim(HeldItem.NONE);
        s.leftClick(false);
        assertEquals(0f, s.hurt(0), 0f);
        assertTrue(s.swing().progress(0.5f) > 0);
    }

    @Test
    public void emptyingTheHandStopsTheBowDraw() {
        PreviewSim s = sim(HeldItem.BOW);
        s.rightPress();
        ticks(s, 20);
        assertTrue(s.aimingBow());
        s.setItem(HeldItem.NONE);
        assertFalse(s.aimingBow());
        s.rightRelease();
        assertTrue(s.world().projectiles().isEmpty());
    }

    @Test
    public void emptyingTheHandStopsEatingAndReelsIn() {
        PreviewSim s = sim(HeldItem.GOLDEN_APPLE);
        s.rightPress();
        ticks(s, 10);
        s.setItem(HeldItem.NONE);
        ticks(s, 40);
        s.rightRelease();
        assertTrue(s.effects().isEmpty());

        PreviewSim r = sim(HeldItem.FISHING_ROD);
        r.rightPress();
        r.rightRelease();
        assertTrue(r.rodCast());
        r.setItem(HeldItem.NONE);
        assertFalse(r.rodCast());
    }

    @Test
    public void bowTwoTicksShootsNothing() {
        assertTrue(drawBow(2).world().projectiles().isEmpty());
    }

    @Test
    public void bowThreeTicksShoots() {
        List<Projectile> shot = drawBow(3).world().projectiles();
        assertEquals(1, shot.size());
        assertEquals(Projectile.Kind.ARROW, shot.get(0).kind);
        assertFalse(shot.get(0).critical);
    }

    @Test
    public void bowTwentyTicksShootsCritical() {
        List<Projectile> shot = drawBow(20).world().projectiles();
        assertEquals(1, shot.size());
        assertTrue(shot.get(0).critical);
    }

    @Test
    public void bowFrames() {
        PreviewSim s = sim(HeldItem.BOW);
        assertEquals(-1, s.bowFrame());
        s.rightPress();
        assertTrue(s.aimingBow());
        assertEquals(-1, s.bowFrame());
        s.tick();
        assertEquals(0, s.bowFrame());
        ticks(s, 12);
        assertEquals(13, s.useTicks());
        assertEquals(0, s.bowFrame());
        s.tick();
        assertEquals(1, s.bowFrame());
        ticks(s, 3);
        assertEquals(17, s.useTicks());
        assertEquals(1, s.bowFrame());
        s.tick();
        assertEquals(2, s.bowFrame());
        s.rightRelease();
        assertFalse(s.aimingBow());
        assertEquals(-1, s.bowFrame());
    }

    @Test
    public void swordHoldBlocks() {
        PreviewSim s = sim(HeldItem.SWORD);
        assertFalse(s.blocking());
        s.rightPress();
        ticks(s, 5);
        assertTrue(s.blocking());
        s.rightRelease();
        assertFalse(s.blocking());
    }

    @Test
    public void appleCrumbsEveryFourTicksThenSixteenWithEffects() {
        PreviewSim s = sim(HeldItem.GOLDEN_APPLE);
        s.rightPress();
        for (int t = 1; t <= 31; t++) {
            s.tick();
            assertEquals("tick " + t, t >= 8 && t % 4 == 0 ? 5 : 0, newCracks(s));
            assertTrue(s.effects().isEmpty());
        }
        s.tick();
        assertEquals(16, newCracks(s));
        assertEquals(2, s.effects().size());
    }

    @Test
    public void appleReleaseCancels() {
        PreviewSim s = sim(HeldItem.GOLDEN_APPLE);
        s.rightPress();
        ticks(s, 20);
        s.rightRelease();
        ticks(s, 20);
        assertTrue(s.effects().isEmpty());
        assertEquals(0, s.useTicks());
    }

    @Test
    public void appleGivesRegenerationAndAbsorption() {
        List<Effect> e = ateApple().effects();
        assertEquals(2, e.size());
        Effect regen = e.get(0), abs = e.get(1);
        assertEquals("Regeneration II", regen.name);
        assertEquals(7, regen.iconIndex);
        assertEquals(0xCD5CAB, regen.color);
        assertEquals(1, regen.amplifier);
        assertEquals(100, regen.ticksLeft);
        assertEquals("Absorption", abs.name);
        assertEquals(18, abs.iconIndex);
        assertEquals(0x2552A5, abs.color);
        assertEquals(0, abs.amplifier);
        assertEquals(2400, abs.ticksLeft);
    }

    @Test
    public void effectsCountDownAndExpire() {
        PreviewSim s = ateApple();
        ticks(s, 99);
        assertEquals(2, s.effects().size());
        assertEquals(1, s.effects().get(0).ticksLeft);
        s.tick();
        assertEquals(1, s.effects().size());
        assertEquals("Absorption", s.effects().get(0).name);
        assertEquals(2300, s.effects().get(0).ticksLeft);
    }

    @Test
    public void effectColorMixesByAmplifier() {
        PreviewSim s = ateApple();
        assertColor((149 << 16) | (88 << 8) | 169, s.effectColor());
    }

    @Test
    public void effectColorAbsorptionAlone() {
        PreviewSim s = sim(HeldItem.ENDER_PEARL);
        assertEquals(0, s.effectColor());
        s.addEffect(Effect.absorption());
        assertEquals(0x2552A5, s.effectColor());
    }

    @Test
    public void effectsEmitSpellParticles() {
        PreviewSim s = ateApple();
        ticks(s, 40);
        int spells = count(s, ParticleKind.SPELL_MOB);
        assertTrue("spells " + spells, spells > 0);
        PreviewSim none = sim(HeldItem.SWORD);
        ticks(none, 40);
        assertEquals(0, count(none, ParticleKind.SPELL_MOB));
    }

    @Test
    public void leavingTheAppleClearsEffects() {
        PreviewSim s = ateApple();
        s.setItem(HeldItem.BOW);
        assertTrue(s.effects().isEmpty());
        s.setItem(HeldItem.SWORD);
        assertTrue(s.effects().isEmpty());
    }

    @Test
    public void otherSwitchesKeepEffects() {
        PreviewSim s = sim(HeldItem.ENDER_PEARL);
        s.addEffect(Effect.absorption());
        s.setItem(HeldItem.GOLDEN_APPLE);
        assertEquals(1, s.effects().size());
        s.setItem(HeldItem.GOLDEN_APPLE);
        assertEquals(1, s.effects().size());
    }

    @Test
    public void addEffectReplacesTheSameName() {
        PreviewSim s = sim(HeldItem.SWORD);
        s.addEffect(Effect.absorption());
        ticks(s, 10);
        s.addEffect(Effect.absorption());
        assertEquals(1, s.effects().size());
        assertEquals(2400, s.effects().get(0).ticksLeft);
    }

    @Test
    public void swordClickOnModelIsAHit() {
        PreviewSim s = sim(HeldItem.SWORD);
        s.leftClick(true);
        assertEquals(0.3f, s.hurt(0), 0f);
        assertEquals(0f, s.swing().progress(0.5f), 0f);
        s.tick();
        assertEquals(0f, s.swing().progress(0), 0f);
        assertTrue(count(s, ParticleKind.CRIT) > 0);
        assertEquals(0, count(s, ParticleKind.MAGIC_CRIT));
    }

    @Test
    public void enchantedHitAddsMagicCrits() {
        PreviewSim s = sim(HeldItem.SWORD);
        s.setEnchanted(true);
        s.leftClick(true);
        s.tick();
        assertTrue(count(s, ParticleKind.CRIT) > 0);
        assertTrue(count(s, ParticleKind.MAGIC_CRIT) > 0);
    }

    @Test
    public void bowClickOnModelSwings() {
        PreviewSim s = sim(HeldItem.BOW);
        s.leftClick(true);
        assertEquals(0f, s.hurt(0), 0f);
        assertTrue(s.swing().progress(0.5f) > 0);
        s.tick();
        assertTrue(s.swing().progress(0) > 0);
        assertEquals(0, count(s, ParticleKind.CRIT));
    }

    @Test
    public void swordClickOffModelSwings() {
        PreviewSim s = sim(HeldItem.SWORD);
        s.leftClick(false);
        assertEquals(0f, s.hurt(0), 0f);
        assertTrue(s.swing().progress(0.5f) > 0);
    }

    @Test
    public void hurtLastsTenTicks() {
        PreviewSim s = sim(HeldItem.SWORD);
        s.leftClick(true);
        ticks(s, 9);
        assertEquals(0.3f, s.hurt(0), 0f);
        s.tick();
        assertEquals(0f, s.hurt(0), 0f);
    }

    @Test
    public void rodPressTwiceReelsIn() {
        PreviewSim s = sim(HeldItem.FISHING_ROD);
        s.rightPress();
        s.rightRelease();
        assertTrue(s.rodCast());
        assertTrue(s.swing().progress(0.5f) > 0);
        ticks(s, 10);
        s.rightPress();
        s.rightRelease();
        assertFalse(s.rodCast());
    }

    @Test
    public void switchingAwayReelsIn() {
        PreviewSim s = sim(HeldItem.FISHING_ROD);
        s.rightPress();
        s.rightRelease();
        assertTrue(s.rodCast());
        s.setItem(HeldItem.SWORD);
        assertFalse(s.rodCast());
    }

    @Test
    public void switchingStopsTheUse() {
        PreviewSim s = sim(HeldItem.BOW);
        s.rightPress();
        ticks(s, 20);
        s.setItem(HeldItem.SWORD);
        assertFalse(s.blocking());
        assertFalse(s.aimingBow());
        s.rightRelease();
        assertTrue(s.world().projectiles().isEmpty());
    }

    @Test
    public void pearlLandingBurstsPortalsAndHurts() {
        PreviewSim s = sim(HeldItem.ENDER_PEARL);
        s.rightPress();
        s.rightRelease();
        assertTrue(s.swing().progress(0.5f) > 0);
        assertEquals(1, s.world().projectiles().size());
        int t = 0;
        while (!s.world().projectiles().isEmpty() && t++ < 60) {
            s.tick();
        }
        assertTrue(s.world().projectiles().isEmpty());
        assertEquals(32, count(s, ParticleKind.PORTAL));
        assertEquals(0.3f, s.hurt(0), 0f);
    }

    @Test
    public void potionPressThrows() {
        PreviewSim s = sim(HeldItem.SPLASH_POTION);
        s.rightPress();
        assertTrue(s.swing().progress(0.5f) > 0);
        assertEquals(1, s.world().projectiles().size());
        assertEquals(Projectile.Kind.POTION, s.world().projectiles().get(0).kind);
    }
}
