package io.github.as9pa.packorganizer.gui.preview.sound;

/** One playable clip: a cache-unique id (unique per pack file and variant, ends in ".ogg") and its bytes. */
public final class SoundClip {
    public final String id;
    public final byte[] ogg;

    public SoundClip(String id, byte[] ogg) {
        this.id = id;
        this.ogg = ogg;
    }
}
