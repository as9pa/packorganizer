package io.github.as9pa.packorganizer.filter;

/** Receives a pack's scanned or cached resolution and colors. Called on a background thread. */
public interface ResolutionSink {
    void acceptResolution(PackResolution resolution);

    /** Called just before {@link #acceptResolution}. */
    default void acceptColors(PackColors colors) {}
}
