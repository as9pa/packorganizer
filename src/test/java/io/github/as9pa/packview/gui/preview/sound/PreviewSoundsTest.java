package io.github.as9pa.packview.gui.preview.sound;

import static org.junit.Assert.*;

import io.github.as9pa.packview.Settings;
import io.github.as9pa.packview.gui.preview.HeldItem;
import io.github.as9pa.packview.gui.preview.PreviewSim;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PreviewSoundsTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static final String HIT1 = "assets/minecraft/sounds/damage/hit1.ogg";
    private static final String HIT2 = "assets/minecraft/sounds/damage/hit2.ogg";
    private static final String HIT3 = "assets/minecraft/sounds/damage/hit3.ogg";
    private static final String BOW = "assets/minecraft/sounds/random/bow.ogg";
    private static final String GLASS1 = "assets/minecraft/sounds/random/glass1.ogg";

    private final Recorder out = new Recorder();
    private final Map<String, byte[]> vanillaFiles = new HashMap<>();
    private final List<String> vanillaReads = new ArrayList<>();
    private final Function<String, byte[]> vanilla = p -> {
        vanillaReads.add(p);
        return vanillaFiles.get(p);
    };

    /** Records what reached the sound engine. */
    private static final class Recorder implements PreviewSounds.Output {
        final List<String> ids = new ArrayList<>();
        final List<String> bytes = new ArrayList<>();
        final List<float[]> levels = new ArrayList<>();
        final List<String> unloaded = new ArrayList<>();

        @Override
        public void play(String id, byte[] ogg, float volume, float pitch) {
            ids.add(id);
            bytes.add(new String(ogg, StandardCharsets.UTF_8));
            levels.add(new float[] {volume, pitch});
        }

        @Override
        public void unload(String id) {
            unloaded.add(id);
        }

        String lastId() {
            return ids.get(ids.size() - 1);
        }

        String lastBytes() {
            return bytes.get(bytes.size() - 1);
        }
    }

    /** Picks the given variant indexes in order, then 0. */
    private static final class Picks extends Random {
        private final Deque<Integer> next = new ArrayDeque<>();

        Picks(int... picks) {
            for (int p : picks) next.add(p);
        }

        @Override
        public int nextInt(int bound) {
            int p = next.isEmpty() ? 0 : next.remove();
            assertTrue(p < bound);
            return p;
        }
    }

    private PreviewSounds sounds(int... picks) {
        return new PreviewSounds(out, vanilla, new Picks(picks));
    }

    private static byte[] ogg(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private File zip(String name, String... pathsAndContents) throws IOException {
        File target = new File(tmp.getRoot(), name);
        target.getParentFile().mkdirs();
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put("pack.mcmeta", ogg("{\"pack\":{\"pack_format\":1,\"description\":\"t\"}}"));
        for (int i = 0; i < pathsAndContents.length; i += 2) {
            e.put(pathsAndContents[i], ogg(pathsAndContents[i + 1]));
        }
        try (ZipOutputStream zo = new ZipOutputStream(new FileOutputStream(target))) {
            for (Map.Entry<String, byte[]> en : e.entrySet()) {
                zo.putNextEntry(new ZipEntry(en.getKey()));
                zo.write(en.getValue());
                zo.closeEntry();
            }
        }
        return target;
    }

    private static void write(File file, String content) throws IOException {
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), ogg(content));
    }

    @Test
    public void soundsListTheirVanillaVariants() {
        assertEquals(Arrays.asList(HIT1, HIT2, HIT3), PreviewSound.HURT.paths());
        assertEquals("game.player.hurt", PreviewSound.HURT.eventName());
        assertEquals(Arrays.asList(BOW), PreviewSound.BOW.paths());
        assertEquals(4, PreviewSound.BOW_HIT.paths().size());
        assertEquals("assets/minecraft/sounds/random/bowhit4.ogg", PreviewSound.BOW_HIT.paths().get(3));
        assertEquals(3, PreviewSound.EAT.paths().size());
        assertEquals("random.burp", PreviewSound.BURP.eventName());
        assertEquals(GLASS1, PreviewSound.GLASS.paths().get(0));
        assertEquals("game.potion.smash", PreviewSound.GLASS.eventName());
    }

    @Test
    public void playsTheChosenVariantFromAZipPack() throws Exception {
        PreviewSounds s = sounds(1);
        s.setPack(zip("p.zip", HIT1, "pack-hit1", HIT2, "pack-hit2", HIT3, "pack-hit3"));
        s.play(PreviewSound.HURT, 1f, 1.1f);

        assertEquals(1, out.ids.size());
        assertEquals("pack-hit2", out.lastBytes());
        assertTrue(out.lastId().startsWith("po_"));
        assertFalse(out.lastId().startsWith("po_vanilla_"));
        assertTrue(out.lastId().endsWith("_damage_hit2.ogg"));
        assertEquals(1f, out.levels.get(0)[0], 0f);
        assertEquals(1.1f, out.levels.get(0)[1], 0f);
        assertTrue(vanillaReads.isEmpty());
    }

    @Test
    public void playsFromAFolderPack() throws Exception {
        File dir = tmp.newFolder("folder");
        write(new File(dir, BOW), "folder-bow");
        PreviewSounds s = sounds();
        s.setPack(dir);
        s.play(PreviewSound.BOW, 0.5f, 0.4f);

        assertEquals("folder-bow", out.lastBytes());
        assertTrue(out.lastId().endsWith("_random_bow.ogg"));
        assertEquals(0, s.zipOpens);
    }

    @Test
    public void fallsBackToVanillaWithoutAPackOrWhenThePackLacksTheSound() throws Exception {
        vanillaFiles.put(HIT1, ogg("vanilla-hit1"));
        PreviewSounds s = sounds();
        s.play(PreviewSound.HURT, 1f, 1f);
        assertEquals("vanilla-hit1", out.lastBytes());
        assertEquals("po_vanilla_damage_hit1.ogg", out.lastId());

        s.setPack(zip("plain.zip"));
        s.play(PreviewSound.HURT, 1f, 1f);
        assertEquals("vanilla-hit1", out.lastBytes());
        assertEquals("po_vanilla_damage_hit1.ogg", out.lastId());

        s.setPack(null);
        s.play(PreviewSound.HURT, 1f, 1f);
        assertEquals(3, out.ids.size());
        assertEquals(Arrays.asList(HIT1), vanillaReads);
    }

    @Test
    public void fallsBackToVanillaForOnlyTheMissingVariant() throws Exception {
        vanillaFiles.put(HIT1, ogg("vanilla-hit1"));
        vanillaFiles.put(HIT3, ogg("vanilla-hit3"));
        PreviewSounds s = sounds(0, 2);
        s.setPack(zip("partial.zip", HIT1, "pack-hit1"));
        s.play(PreviewSound.HURT, 1f, 1f);
        assertEquals("pack-hit1", out.lastBytes());
        s.play(PreviewSound.HURT, 1f, 1f);
        assertEquals("vanilla-hit3", out.lastBytes());
        assertEquals("po_vanilla_damage_hit3.ogg", out.lastId());
        assertEquals(Arrays.asList(HIT3), vanillaReads);
    }

    @Test
    public void playsNothingWhenNeitherHasTheSound() throws Exception {
        PreviewSounds s = sounds();
        s.setPack(zip("empty.zip"));
        s.play(PreviewSound.GLASS, 1f, 1f);

        File junk = new File(tmp.getRoot(), "junk.zip");
        Files.write(junk.toPath(), ogg("not a zip"));
        s.setPack(junk);
        s.play(PreviewSound.GLASS, 1f, 1f);

        s.setPack(new File(tmp.getRoot(), "absent.zip"));
        s.play(PreviewSound.GLASS, 1f, 1f);

        PreviewSounds failing = new PreviewSounds(out, p -> {
            throw new IllegalStateException("default pack closed");
        }, new Picks());
        failing.play(PreviewSound.GLASS, 1f, 1f);

        assertTrue(out.ids.isEmpty());
        assertEquals(Arrays.asList(GLASS1), vanillaReads);
    }

    @Test
    public void readsEachZipOncePerVersionAndKeepsTheBytes() throws Exception {
        vanillaFiles.put(GLASS1, ogg("vanilla-glass1"));
        File a = zip("a.zip", HIT1, "a-hit1", BOW, "a-bow");
        File b = zip("b.zip", HIT1, "b-hit1");
        PreviewSounds s = sounds();
        s.setPack(a);
        s.play(PreviewSound.HURT, 1f, 1f);
        s.play(PreviewSound.BOW, 1f, 1f);
        s.play(PreviewSound.GLASS, 1f, 1f);
        s.play(PreviewSound.GLASS, 1f, 1f);
        s.play(PreviewSound.HURT, 1f, 1f);
        assertEquals(1, s.zipOpens);
        assertEquals(Arrays.asList(GLASS1), vanillaReads);

        s.setPack(b);
        s.play(PreviewSound.HURT, 1f, 1f);
        assertEquals("b-hit1", out.lastBytes());
        s.setPack(a);
        s.play(PreviewSound.HURT, 1f, 1f);
        assertEquals("a-hit1", out.lastBytes());
        assertEquals(2, s.zipOpens);

        assertTrue(a.delete());
        s.play(PreviewSound.BOW, 1f, 1f);
        assertEquals("a-bow", out.lastBytes());
        assertEquals(2, s.zipOpens);
    }

    @Test
    public void idsDifferAcrossPacksWithTheSameFile() throws Exception {
        File one = zip("one/p.zip", HIT1, "same");
        File two = zip("two/p.zip", HIT1, "same");
        assertTrue(one.setLastModified(1_600_000_000_000L));
        assertTrue(two.setLastModified(1_600_000_000_000L));
        PreviewSounds s = sounds();
        s.setPack(one);
        s.play(PreviewSound.HURT, 1f, 1f);
        s.setPack(two);
        s.play(PreviewSound.HURT, 1f, 1f);
        assertNotEquals(out.ids.get(0), out.ids.get(1));

        assertTrue(one.setLastModified(1_600_000_100_000L));
        s.setPack(one);
        s.play(PreviewSound.HURT, 1f, 1f);
        assertEquals(3, s.zipOpens);
        assertNotEquals(out.ids.get(0), out.ids.get(2));
        assertNotEquals(out.ids.get(1), out.ids.get(2));
        for (String id : out.ids) {
            assertTrue(id.matches("po_[0-9a-f]+_damage_hit1\\.ogg"));
        }
    }

    @Test
    public void disabledPlaysNothingAndDrawsNoVariant() throws Exception {
        PreviewSounds s = sounds(2);
        s.setPack(zip("d.zip", HIT3, "pack-hit3"));
        s.setEnabled(false);
        s.play(PreviewSound.HURT, 1f, 1f);
        assertTrue(out.ids.isEmpty());
        assertEquals(0, s.zipOpens);
        assertTrue(vanillaReads.isEmpty());

        s.setEnabled(true);
        s.play(PreviewSound.HURT, 1f, 1f);
        assertEquals("pack-hit3", out.lastBytes());
    }

    /** The panel's wiring: the sim's sink is play, and each tick enables it from the Preview sounds switch. */
    @Test
    public void previewSoundsOffKeepsTheSimsSoundsFromTheEngine() {
        vanillaFiles.put(HIT1, ogg("vanilla-hit1"));
        Settings settings = new Settings();
        settings.previewSounds = false;
        PreviewSounds s = sounds(0, 0);
        PreviewSim sim = new PreviewSim(new Random(7), new Random(1));
        sim.setSoundSink(s::play);
        s.setEnabled(settings.previewSounds);
        sim.leftClick(true);
        sim.setItem(HeldItem.ENDER_PEARL);
        sim.rightPress();
        for (int i = 0; i < 60; i++) {
            sim.tick();
        }
        assertTrue(out.ids.isEmpty());
        assertTrue(vanillaReads.isEmpty());

        settings.previewSounds = true;
        s.setEnabled(settings.previewSounds);
        sim.setItem(HeldItem.SWORD);
        sim.leftClick(true);
        assertEquals(1, out.ids.size());
        assertEquals("vanilla-hit1", out.lastBytes());
    }

    /** Hovering a row or switching packs only calls setPack, which reads and plays nothing. */
    @Test
    public void choosingPacksPlaysNothing() throws Exception {
        PreviewSounds s = sounds();
        s.setPack(zip("h.zip", HIT1, "pack-hit1"));
        s.setPack(null);
        s.setPack(zip("i.zip", BOW, "pack-bow"));
        assertTrue(out.ids.isEmpty());
        assertEquals(0, s.zipOpens);
        assertTrue(vanillaReads.isEmpty());
    }

    @Test
    public void closeUnloadsEachPlayedClipOnceAndClearsTheCache() throws Exception {
        vanillaFiles.put(HIT2, ogg("vanilla-hit2"));
        PreviewSounds s = sounds(0, 1, 0, 1, 0, 1);
        s.setPack(zip("c.zip", HIT1, "pack-hit1"));
        for (int i = 0; i < 4; i++) {
            s.play(PreviewSound.HURT, 1f, 1f);
        }
        String packId = out.ids.get(0);
        s.close();
        assertEquals(2, out.unloaded.size());
        assertTrue(out.unloaded.contains(packId));
        assertTrue(out.unloaded.contains("po_vanilla_damage_hit2.ogg"));

        s.play(PreviewSound.HURT, 1f, 1f);
        assertEquals(packId, out.lastId());
        assertEquals(2, s.zipOpens);
        s.play(PreviewSound.HURT, 1f, 1f);
        assertEquals(Arrays.asList(HIT2, HIT2), vanillaReads);

        out.unloaded.clear();
        s.close();
        assertEquals(2, out.unloaded.size());
        s.close();
        assertEquals(2, out.unloaded.size());
    }

    @Test
    public void dropsTheLeastRecentlyPlayedPackPastTheLimit() throws Exception {
        PreviewSounds s = sounds();
        File first = zip("lru0.zip", HIT1, "first");
        s.setPack(first);
        s.play(PreviewSound.HURT, 1f, 1f);
        String firstId = out.lastId();
        for (int i = 1; i < PreviewSounds.MAX_PACKS; i++) {
            s.setPack(zip("lru" + i + ".zip", HIT1, "pack" + i));
            s.play(PreviewSound.HURT, 1f, 1f);
        }
        assertTrue(out.unloaded.isEmpty());

        s.setPack(zip("lru-last.zip", HIT1, "last"));
        s.play(PreviewSound.HURT, 1f, 1f);
        assertEquals(Arrays.asList(firstId), out.unloaded);

        s.setPack(first);
        s.play(PreviewSound.HURT, 1f, 1f);
        assertEquals("first", out.lastBytes());
        assertEquals(PreviewSounds.MAX_PACKS + 2, s.zipOpens);
    }
}
