package io.github.as9pa.packview.gui.preview.sound;

import static org.junit.Assert.*;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;
import org.junit.Test;

public class VanillaSoundRulesTest {
    private static final int DRAWS = 10_000;

    /** Hands out the given floats and ints in order, so each formula can be checked by hand. */
    private static final class Fixed extends Random {
        private final Deque<Float> floats = new ArrayDeque<>();
        private final Deque<Integer> ints = new ArrayDeque<>();

        Fixed withFloats(float... fs) {
            for (float f : fs) floats.add(f);
            return this;
        }

        Fixed withInts(int... is) {
            for (int i : is) ints.add(i);
            return this;
        }

        @Override
        public float nextFloat() {
            return floats.remove();
        }

        @Override
        public int nextInt(int bound) {
            return ints.remove();
        }
    }

    @Test
    public void formulasMatchVanillaForKnownDraws() {
        assertEquals(1.1f, VanillaSoundRules.hurtPitch(new Fixed().withFloats(0.75f, 0.25f)), 1e-6f);
        assertEquals(1f / 1.4f + 0.5f, VanillaSoundRules.bowReleasePitch(new Fixed().withFloats(0.5f), 1f), 1e-6f);
        assertEquals(1f / 1.4f, VanillaSoundRules.bowReleasePitch(new Fixed().withFloats(0.5f), 0f), 1e-6f);
        assertEquals(0.4f, VanillaSoundRules.throwPitch(new Fixed().withFloats(0.5f)), 1e-6f);
        assertEquals(1.2f, VanillaSoundRules.bowHitPitch(new Fixed().withFloats(0.5f)), 1e-6f);
        assertEquals(0.5f, VanillaSoundRules.eatVolume(new Fixed().withInts(0)), 0f);
        assertEquals(1.0f, VanillaSoundRules.eatVolume(new Fixed().withInts(1)), 0f);
        assertEquals(0.9f, VanillaSoundRules.eatPitch(new Fixed().withFloats(0.25f, 0.75f)), 1e-6f);
        assertEquals(0.95f, VanillaSoundRules.burpPitch(new Fixed().withFloats(0.5f)), 1e-6f);
        assertEquals(0.95f, VanillaSoundRules.glassPitch(new Fixed().withFloats(0.5f)), 1e-6f);
        assertEquals(0.5f, VanillaSoundRules.THROW_VOLUME, 0f);
        assertEquals(0.5f, VanillaSoundRules.BURP_VOLUME, 0f);
    }

    @Test
    public void seededHurtPitchIsExact() {
        Random same = new Random(42);
        float expected = (same.nextFloat() - same.nextFloat()) * 0.2F + 1.0F;
        assertEquals(expected, VanillaSoundRules.hurtPitch(new Random(42)), 0f);
    }

    @Test
    public void seededPitchesStayInVanillaRanges() {
        Random r = new Random(42);
        float hurtMin = 2, hurtMax = 0;
        for (int i = 0; i < DRAWS; i++) {
            float hurt = VanillaSoundRules.hurtPitch(r);
            assertBetween(hurt, 0.8f, 1.2f);
            hurtMin = Math.min(hurtMin, hurt);
            hurtMax = Math.max(hurtMax, hurt);
            assertBetween(VanillaSoundRules.bowReleasePitch(r, 1f), 1.125f, 1f / 1.2f + 0.5f);
            assertBetween(VanillaSoundRules.bowReleasePitch(r, 0f), 0.625f, 1f / 1.2f);
            assertBetween(VanillaSoundRules.throwPitch(r), 1f / 3f, 0.5f);
            assertBetween(VanillaSoundRules.bowHitPitch(r), 1.2f / 1.1f, 1.2f / 0.9f);
            assertBetween(VanillaSoundRules.eatPitch(r), 0.8f, 1.2f);
            assertBetween(VanillaSoundRules.burpPitch(r), 0.9f, 1.0f);
            assertBetween(VanillaSoundRules.glassPitch(r), 0.9f, 1.0f);
        }
        assertTrue(hurtMin < 0.85f);
        assertTrue(hurtMax > 1.15f);
    }

    @Test
    public void seededEatVolumeIsHalfOrFull() {
        Random r = new Random(7);
        boolean half = false, full = false;
        for (int i = 0; i < 100; i++) {
            float v = VanillaSoundRules.eatVolume(r);
            assertTrue(v == 0.5f || v == 1.0f);
            half |= v == 0.5f;
            full |= v == 1.0f;
        }
        assertTrue(half && full);
    }

    private static void assertBetween(float v, float lo, float hi) {
        assertTrue(v + " below " + lo, v >= lo - 1e-6f);
        assertTrue(v + " above " + hi, v <= hi + 1e-6f);
    }
}
