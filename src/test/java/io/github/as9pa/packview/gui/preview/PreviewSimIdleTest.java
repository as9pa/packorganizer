package io.github.as9pa.packview.gui.preview;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Random;
import org.junit.Test;

public class PreviewSimIdleTest {
    private final PreviewSim sim = new PreviewSim(new Random(1), new Random(2));

    private void tick(int n) {
        for (int i = 0; i < n; i++) {
            sim.tick();
        }
    }

    /** Ticks until the sim stops animating; fails past {@code max}. */
    private void settle(int max) {
        for (int i = 0; i < max; i++) {
            if (!sim.animating()) {
                return;
            }
            sim.tick();
        }
        assertFalse("still animating after " + max + " ticks", sim.animating());
    }

    @Test
    public void aNewSimIsIdle() {
        assertFalse(sim.animating());
        tick(50);
        assertFalse(sim.animating());
    }

    @Test
    public void aSwingAnimatesUntilItEnds() {
        sim.setItem(HeldItem.NONE);
        sim.leftClick(false);
        assertTrue(sim.animating());
        tick(5);
        assertTrue(sim.animating());
        tick(1);
        assertFalse(sim.animating());
    }

    @Test
    public void aHitAnimatesForTheHurtTime() {
        sim.setItem(HeldItem.SWORD);
        sim.leftClick(true);
        assertTrue(sim.animating());
        tick(PreviewSim.HURT_TICKS - 1);
        assertTrue(sim.animating());
        tick(1);
        assertFalse(sim.animating());
    }

    @Test
    public void blockingAnimatesWhileHeld() {
        sim.setItem(HeldItem.SWORD);
        sim.rightPress();
        tick(100);
        assertTrue(sim.animating());
        sim.rightRelease();
        assertFalse(sim.animating());
    }

    @Test
    public void anArrowAnimatesUntilItSticks() {
        sim.setItem(HeldItem.BOW);
        sim.rightPress();
        tick(20);
        sim.rightRelease();
        assertTrue(sim.animating());
        settle(400);
        assertFalse(sim.world().flying());
    }

    @Test
    public void thrownPearlsAndPotionsAnimateUntilTheyLand() {
        sim.setItem(HeldItem.ENDER_PEARL);
        sim.rightPress();
        assertTrue(sim.world().flying());
        settle(400);
        sim.setItem(HeldItem.SPLASH_POTION);
        sim.rightPress();
        assertTrue(sim.world().flying());
        settle(400);
        assertFalse(sim.world().flying());
    }

    @Test
    public void theAppleAnimatesWhileEatenButItsEffectsDoNot() {
        sim.setItem(HeldItem.GOLDEN_APPLE);
        sim.rightPress();
        tick(PreviewSim.EAT_TICKS - 1);
        assertTrue(sim.animating());
        tick(1);
        assertFalse(sim.effects().isEmpty());
        assertFalse(sim.animating());
    }

    @Test
    public void aCastRodDoesNotAnimateOnceTheSwingEnds() {
        sim.setItem(HeldItem.FISHING_ROD);
        sim.rightPress();
        tick(6);
        assertTrue(sim.rodCast());
        assertFalse(sim.animating());
    }
}
