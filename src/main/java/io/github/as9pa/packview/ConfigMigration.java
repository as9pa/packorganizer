package io.github.as9pa.packview;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * One-time copy of config/packorganizer (the mod's old name) into config/packview. Copies, never moves: the old
 * folder stays as it was so the old jar still works after a rollback. No Minecraft classes, so tests can call it.
 */
public final class ConfigMigration {
    /** The mod's id before the rename, and so its old config folder name. */
    public static final String OLD_MODID = "packorganizer";

    public enum Result { MIGRATED, SKIPPED_NEW_EXISTS, SKIPPED_NO_OLD, FAILED }

    private ConfigMigration() {}

    /**
     * Copies everything under oldDir into newDir, but only when newDir does not exist yet; an existing newDir is
     * never touched. On failure the partial newDir is removed so the next launch tries again. Never throws.
     */
    public static Result migrate(Path oldDir, Path newDir) {
        if (Files.exists(newDir, LinkOption.NOFOLLOW_LINKS)) {
            return Result.SKIPPED_NEW_EXISTS;
        }
        if (!Files.isDirectory(oldDir)) {
            return Result.SKIPPED_NO_OLD;
        }
        boolean created = false;
        try {
            Path parent = newDir.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.createDirectory(newDir);
            created = true;
            List<Path> sources;
            try (Stream<Path> walk = Files.walk(oldDir)) {
                sources = walk.collect(Collectors.toList());
            }
            // Files.walk is pre-order, so every folder is created before the files inside it.
            for (Path src : sources) {
                Path rel = oldDir.relativize(src);
                if (rel.toString().isEmpty()) {
                    continue;
                }
                Path dst = newDir.resolve(rel.toString());
                if (Files.isDirectory(src, LinkOption.NOFOLLOW_LINKS)) {
                    Files.createDirectory(dst);
                } else {
                    Files.copy(src, dst);
                }
            }
            Log.LOG.info("packview: moved settings from config/" + OLD_MODID);
            return Result.MIGRATED;
        } catch (IOException | UncheckedIOException e) {
            Log.LOG.warn("packview: could not move settings from config/{}, using defaults: {}",
                    OLD_MODID, describe(e, newDir));
            if (created) {
                deletePartial(newDir);
            }
            return Result.FAILED;
        }
    }

    /** Removes the half-copied newDir, deepest entries first. Only ever called on a folder this class created. */
    private static void deletePartial(Path newDir) {
        try {
            List<Path> paths;
            try (Stream<Path> walk = Files.walk(newDir)) {
                paths = walk.collect(Collectors.toList());
            }
            Collections.reverse(paths);
            for (Path p : paths) {
                Files.deleteIfExists(p);
            }
        } catch (IOException | UncheckedIOException e) {
            Log.LOG.warn("packview: could not remove the partial config/{}: {}",
                    newDir.getFileName(), describe(e, newDir));
        }
    }

    /** The exception's message with the game folder prefix cut off, so the log shows no absolute path or user name. */
    private static String describe(Exception e, Path newDir) {
        Throwable cause = e instanceof UncheckedIOException && e.getCause() != null ? e.getCause() : e;
        String message = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
        Path config = newDir.toAbsolutePath().getParent();
        Path game = config != null ? config.getParent() : null;
        if (game != null) {
            message = message.replace(game.toString() + File.separator, "");
        }
        return message;
    }
}
