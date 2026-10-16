package io.github.as9pa.packorganizer.filter;

/** Receives a pack's scanned or cached resolution. Called on a background thread. */
public interface ResolutionSink {
    void acceptResolution(PackResolution resolution);
}
