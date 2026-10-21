package io.github.as9pa.packview;

import io.github.as9pa.packview.filter.PackColors;
import io.github.as9pa.packview.filter.PackResolution;
import io.github.as9pa.packview.filter.ResolutionSink;
import java.io.File;

/** Implemented by {@code ResourcePackRepository.Entry} via mixin. */
public interface EntryExtension extends ResolutionSink {
    /** Detected resolution, or null while the background scan has not finished. */
    PackResolution packview$resolution();

    /** Sword and armor color features, or null while the background scan has not finished. */
    PackColors packview$colors();

    /** The pack file or folder this entry wraps. */
    File packview$file();
}
