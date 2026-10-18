package io.github.as9pa.packorganizer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SettingsTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private File json() {
        return new File(tmp.getRoot(), "config/packorganizer/settings.json");
    }

    private void writeJson(String text) throws IOException {
        json().getParentFile().mkdirs();
        Files.write(json().toPath(), text.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertDefaults(Settings s) {
        assertTrue(s.packPreviews);
        assertFalse(s.transparentBackground);
        assertTrue(s.packFolders);
        assertTrue(s.colorFilter);
        assertTrue(s.resolutionLabels);
        assertTrue(s.rememberOpenFolders);
        assertTrue(s.openFolders.isEmpty());
    }

    @Test
    public void missingFileMeansDefaults() {
        assertDefaults(Settings.load(json()));
        assertFalse(json().exists());
    }

    @Test
    public void roundTripsEveryField() {
        Settings s = new Settings();
        s.packPreviews = false;
        s.transparentBackground = true;
        s.packFolders = false;
        s.colorFilter = false;
        s.resolutionLabels = false;
        s.openFolders.add("faithful");
        s.openFolders.add("pvp/16x");
        s.save(json());
        assertTrue(json().isFile());
        assertFalse(new File(json().getPath() + ".tmp").exists());

        Settings r = Settings.load(json());
        assertFalse(r.packPreviews);
        assertTrue(r.transparentBackground);
        assertFalse(r.packFolders);
        assertFalse(r.colorFilter);
        assertFalse(r.resolutionLabels);
        assertTrue(r.rememberOpenFolders);
        assertEquals(Arrays.asList("faithful", "pvp/16x"), new java.util.ArrayList<>(r.openFolders));
    }

    @Test
    public void malformedJsonMeansDefaults() throws IOException {
        writeJson("{ not json at all");
        assertDefaults(Settings.load(json()));
        writeJson("[1, 2, 3]");
        assertDefaults(Settings.load(json()));
    }

    @Test
    public void unknownAndMissingKeysAreTolerated() throws IOException {
        writeJson("{\"colorFilter\": false, \"somethingNew\": 42, \"openFolders\": [\"a\", 7]}");
        Settings s = Settings.load(json());
        assertFalse(s.colorFilter);
        assertTrue(s.packPreviews);
        assertTrue(s.packFolders);
        assertTrue(s.openFolders.contains("a"));
    }

    @Test
    public void badValueForOneKeyKeepsItsDefault() throws IOException {
        writeJson("{\"packPreviews\": {\"x\": 1}, \"packFolders\": false}");
        Settings s = Settings.load(json());
        assertTrue(s.packPreviews);
        assertFalse(s.packFolders);
    }

    @Test
    public void rememberOpenFoldersFalseDropsTheSet() throws IOException {
        Settings s = new Settings();
        s.rememberOpenFolders = false;
        s.openFolders.add("faithful");
        s.save(json());
        String text = new String(Files.readAllBytes(json().toPath()), StandardCharsets.UTF_8);
        assertFalse(text.contains("faithful"));

        Settings r = Settings.load(json());
        assertFalse(r.rememberOpenFolders);
        assertTrue(r.openFolders.isEmpty());

        // A stored set is ignored on load when remembering is off.
        writeJson("{\"rememberOpenFolders\": false, \"openFolders\": [\"faithful\"]}");
        assertEquals(Collections.emptySet(), Settings.load(json()).openFolders);
    }

    @Test
    public void initIsIdempotentAndSaveNowWrites() {
        File dir = new File(tmp.getRoot(), "config/packorganizer");
        Settings.init(dir);
        Settings first = Settings.get();
        Settings.init(dir);
        assertSame(first, Settings.get());
        first.colorFilter = false;
        Settings.saveNow();
        assertFalse(Settings.load(new File(dir, "settings.json")).colorFilter);
    }

    @Test
    public void newPreviewDefaults() {
        Settings s = new Settings();
        assertTrue(s.playerPreview);
        assertTrue(s.skyBackdrop);
        assertTrue(s.skyCycle);
        assertTrue(s.showCape);
        assertEquals("diamond", s.previewArmor);
        assertEquals("sword", s.previewItem);
        assertFalse(s.previewEnchanted);
    }

    @Test
    public void previewFieldsRoundTrip() {
        Settings s = Settings.load(json());
        s.playerPreview = false;
        s.skyBackdrop = false;
        s.skyCycle = false;
        s.showCape = false;
        s.previewArmor = "gold";
        s.previewItem = "bow";
        s.previewEnchanted = true;
        s.save(json());

        Settings r = Settings.load(json());
        assertFalse(r.playerPreview);
        assertFalse(r.skyBackdrop);
        assertFalse(r.skyCycle);
        assertFalse(r.showCape);
        assertEquals("gold", r.previewArmor);
        assertEquals("bow", r.previewItem);
        assertTrue(r.previewEnchanted);
    }

    @Test
    public void unknownArmorOrItemFallsBack() throws IOException {
        writeJson("{\"previewArmor\": \"netherite\", \"previewItem\": 42}");
        Settings r = Settings.load(json());
        assertEquals("diamond", r.previewArmor);
        assertEquals("sword", r.previewItem);
    }

    @Test
    public void switchesAreListedInOrder() {
        List<String> labels = new ArrayList<>();
        for (Settings.Switch sw : Settings.SWITCHES) {
            labels.add(sw.label);
        }
        assertEquals(Arrays.asList(
                "Pack previews", "Transparent background", "Pack folders",
                "Color filter", "Resolution labels", "Remember open folders",
                "Player preview", "Pack sky backdrop", "Sky day cycle", "Show cape", "Hit tint"), labels);
    }

    @Test
    public void hitTintDefaultsOnAndRoundTripsOff() {
        assertTrue(new Settings().previewHitTint);
        Settings s = Settings.load(json());
        s.previewHitTint = false;
        s.save(json());
        assertFalse(Settings.load(json()).previewHitTint);
        s.previewHitTint = true;
        s.save(json());
        assertTrue(Settings.load(json()).previewHitTint);
    }

    @Test
    public void everySwitchTogglesOnlyItsOwnField() {
        for (int i = 0; i < Settings.SWITCHES.size(); i++) {
            Settings s = new Settings();
            boolean[] before = snapshot(s);
            Settings.Switch sw = Settings.SWITCHES.get(i);
            sw.set(s, !sw.get(s));
            boolean[] after = snapshot(s);
            int changed = 0;
            for (int j = 0; j < before.length; j++) {
                if (before[j] != after[j]) {
                    changed++;
                }
            }
            assertEquals("switch " + sw.label, 1, changed);
        }
    }

    /** Every boolean setting in a fixed order, including Enchanted which has no switch. */
    private static boolean[] snapshot(Settings s) {
        return new boolean[] {
                s.packPreviews, s.transparentBackground, s.packFolders, s.colorFilter,
                s.resolutionLabels, s.rememberOpenFolders,
                s.playerPreview, s.skyBackdrop, s.skyCycle, s.showCape, s.previewHitTint, s.previewEnchanted
        };
    }
}
