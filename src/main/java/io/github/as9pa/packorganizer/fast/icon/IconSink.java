package io.github.as9pa.packorganizer.fast.icon;

import java.awt.image.BufferedImage;

/** Receives a decoded (and possibly downscaled) icon. Called on a background thread. */
public interface IconSink {
    void acceptIcon(BufferedImage image);
}
