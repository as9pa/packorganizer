package io.github.as9pa.packview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ConfigMigrationTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private Path oldDir() {
        return new File(tmp.getRoot(), "config/packorganizer").toPath();
    }

    private Path newDir() {
        return new File(tmp.getRoot(), "config/packview").toPath();
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, text.getBytes(StandardCharsets.UTF_8));
    }

    private static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    @Test
    public void copiesOldFolderAndLeavesItInPlace() throws IOException {
        write(oldDir().resolve("settings.json"), "{\"favorites\": [\"a.zip\"]}");
        write(oldDir().resolve("packs.json"), "{\"version\": 1}");
        write(oldDir().resolve("extra/nested/notes.txt"), "kept");

        assertEquals(ConfigMigration.Result.MIGRATED, ConfigMigration.migrate(oldDir(), newDir()));

        assertEquals("{\"favorites\": [\"a.zip\"]}", read(newDir().resolve("settings.json")));
        assertEquals("{\"version\": 1}", read(newDir().resolve("packs.json")));
        assertEquals("kept", read(newDir().resolve("extra/nested/notes.txt")));
        // Copy, not move: the old jar still finds its settings after a rollback.
        assertEquals("{\"favorites\": [\"a.zip\"]}", read(oldDir().resolve("settings.json")));
        assertEquals("kept", read(oldDir().resolve("extra/nested/notes.txt")));
    }

    @Test
    public void existingNewFolderIsNeverOverwritten() throws IOException {
        write(oldDir().resolve("settings.json"), "old");
        write(oldDir().resolve("only-old.txt"), "old");
        write(newDir().resolve("settings.json"), "new");

        assertEquals(ConfigMigration.Result.SKIPPED_NEW_EXISTS, ConfigMigration.migrate(oldDir(), newDir()));

        assertEquals("new", read(newDir().resolve("settings.json")));
        assertFalse(Files.exists(newDir().resolve("only-old.txt")));
    }

    @Test
    public void emptyNewFolderStillCountsAsExisting() throws IOException {
        write(oldDir().resolve("settings.json"), "old");
        Files.createDirectories(newDir());

        assertEquals(ConfigMigration.Result.SKIPPED_NEW_EXISTS, ConfigMigration.migrate(oldDir(), newDir()));
        assertFalse(Files.exists(newDir().resolve("settings.json")));
    }

    @Test
    public void noOldFolderSkipsAndCreatesNothing() {
        assertEquals(ConfigMigration.Result.SKIPPED_NO_OLD, ConfigMigration.migrate(oldDir(), newDir()));
        assertFalse(Files.exists(newDir()));
    }

    @Test
    public void oldPathThatIsAFileIsIgnored() throws IOException {
        write(oldDir(), "not a folder");

        assertEquals(ConfigMigration.Result.SKIPPED_NO_OLD, ConfigMigration.migrate(oldDir(), newDir()));
        assertFalse(Files.exists(newDir()));
        assertTrue(Files.isRegularFile(oldDir()));
    }
}
