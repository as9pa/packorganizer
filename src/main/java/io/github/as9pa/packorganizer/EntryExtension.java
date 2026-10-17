package io.github.as9pa.packorganizer;

import io.github.as9pa.packorganizer.filter.PackColors;
import io.github.as9pa.packorganizer.filter.PackResolution;
import io.github.as9pa.packorganizer.filter.ResolutionSink;
import java.io.File;

/** Implemented by {@code ResourcePackRepository.Entry} via mixin. */
public interface EntryExtension extends ResolutionSink {
    /** Detected resolution, or null while the background scan has not finished. */
    PackResolution packorganizer$resolution();

    /** Sword and armor color features, or null while the background scan has not finished. */
    PackColors packorganizer$colors();

    /** The pack file or folder this entry wraps. */
    File packorganizer$file();
}
