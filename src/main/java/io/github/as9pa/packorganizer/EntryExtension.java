package io.github.as9pa.packorganizer;

import io.github.as9pa.packorganizer.filter.PackResolution;
import io.github.as9pa.packorganizer.filter.ResolutionSink;

/** Implemented by {@code ResourcePackRepository.Entry} via mixin. */
public interface EntryExtension extends ResolutionSink {
    /** Detected resolution, or null while the background scan has not finished. */
    PackResolution packorganizer$resolution();
}
