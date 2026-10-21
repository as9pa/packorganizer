package io.github.as9pa.packview.fast;

import io.github.as9pa.packview.fast.icon.IconSink;

/** Implemented by {@code ResourcePackRepository.Entry} via the fast-loading mixin. */
public interface FastEntryExtension extends IconSink {
    /** Recompute the cached identity key from the file's current state. */
    void packview$fastRefreshKey();
}
