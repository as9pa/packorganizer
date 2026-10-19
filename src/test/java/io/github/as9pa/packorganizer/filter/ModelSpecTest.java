package io.github.as9pa.packorganizer.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.HashSet;
import org.junit.Test;

public class ModelSpecTest {
    @Test
    public void helpersBuildVanillaFileNames() {
        assertEquals("models/armor/diamond_layer_1.png", ModelSpec.armor("diamond", 1));
        assertEquals("models/armor/chainmail_layer_2.png", ModelSpec.armor("chainmail", 2));
        assertEquals("models/armor/leather_layer_1_overlay.png", ModelSpec.armorOverlay(1));
        assertEquals("models/armor/leather_layer_2_overlay.png", ModelSpec.armorOverlay(2));
        assertEquals("items/gold_helmet.png", ModelSpec.helmet("gold"));
        assertEquals("items/leather_helmet_overlay.png", ModelSpec.LEATHER_HELMET_OVERLAY);
    }

    @Test
    public void swordsMapFromArmorMaterials() {
        assertEquals("items/wood_sword.png", ModelSpec.sword("leather"));
        assertEquals("items/stone_sword.png", ModelSpec.sword("chainmail"));
        assertEquals("items/iron_sword.png", ModelSpec.sword("iron"));
        assertEquals("items/gold_sword.png", ModelSpec.sword("gold"));
        assertEquals("items/diamond_sword.png", ModelSpec.sword("diamond"));
    }

    @Test
    public void constantsMatchVanillaPaths() {
        assertEquals(Arrays.asList("leather", "chainmail", "iron", "gold", "diamond"), ModelSpec.MATERIALS);
        assertEquals("items/bow_standby.png", ModelSpec.BOW);
        assertEquals(3, ModelSpec.BOW_PULL.length);
        assertEquals("items/bow_pulling_0.png", ModelSpec.BOW_PULL[0]);
        assertEquals("items/bow_pulling_2.png", ModelSpec.BOW_PULL[2]);
        assertEquals("items/ender_pearl.png", ModelSpec.ENDER_PEARL);
        assertEquals("items/apple_golden.png", ModelSpec.GOLDEN_APPLE);
        assertEquals("items/potion_overlay.png", ModelSpec.POTION_OVERLAY);
        assertEquals("items/potion_bottle_splash.png", ModelSpec.POTION_SPLASH);
        assertEquals("items/fishing_rod_uncast.png", ModelSpec.ROD);
        assertEquals("items/fishing_rod_cast.png", ModelSpec.ROD_CAST);
        assertEquals("items/book_enchanted.png", ModelSpec.BOOK);
        assertEquals("entity/arrow.png", ModelSpec.ARROW);
        assertEquals("particle/particles.png", ModelSpec.PARTICLES);
        assertEquals("gui/container/inventory.png", ModelSpec.INVENTORY);
        assertEquals("gui/icons.png", ModelSpec.ICONS);
        assertEquals("misc/enchanted_item_glint.png", ModelSpec.GLINT);
        assertEquals("assets/minecraft/textures/", ModelSpec.ROOT);
    }

    @Test
    public void pathsHoldEveryTextureOnce() {
        assertEquals(ModelSpec.PATHS.size(), new HashSet<>(ModelSpec.PATHS).size());
        for (String m : ModelSpec.MATERIALS) {
            assertTrue(ModelSpec.PATHS.contains(ModelSpec.armor(m, 1)));
            assertTrue(ModelSpec.PATHS.contains(ModelSpec.armor(m, 2)));
            assertTrue(ModelSpec.PATHS.contains(ModelSpec.helmet(m)));
            assertTrue(ModelSpec.PATHS.contains(ModelSpec.sword(m)));
        }
        assertTrue(ModelSpec.PATHS.contains(ModelSpec.armorOverlay(1)));
        assertTrue(ModelSpec.PATHS.contains(ModelSpec.armorOverlay(2)));
        assertTrue(ModelSpec.PATHS.containsAll(Arrays.asList(ModelSpec.BOW_PULL)));
        assertTrue(ModelSpec.PATHS.containsAll(Arrays.asList(
                ModelSpec.LEATHER_HELMET_OVERLAY, ModelSpec.BOW, ModelSpec.ENDER_PEARL, ModelSpec.GOLDEN_APPLE,
                ModelSpec.POTION_OVERLAY, ModelSpec.POTION_SPLASH, ModelSpec.ROD, ModelSpec.ROD_CAST,
                ModelSpec.BOOK, ModelSpec.ARROW, ModelSpec.PARTICLES, ModelSpec.INVENTORY, ModelSpec.ICONS,
                ModelSpec.GLINT)));
        assertEquals(39, ModelSpec.PATHS.size());
    }

    @Test
    public void unknownMaterialOrLayerIsRejected() {
        assertRejected(() -> ModelSpec.armor("netherite", 1));
        assertRejected(() -> ModelSpec.armor("iron", 3));
        assertRejected(() -> ModelSpec.armorOverlay(0));
        assertRejected(() -> ModelSpec.helmet("wood"));
        assertRejected(() -> ModelSpec.sword("wood"));
    }

    private static void assertRejected(Runnable call) {
        try {
            call.run();
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }
}
