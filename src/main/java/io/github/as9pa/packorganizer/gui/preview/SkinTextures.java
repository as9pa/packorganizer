package io.github.as9pa.packorganizer.gui.preview;

/** The skin and cape the preview draws, as GL texture ids. A cape of 0 means no cape. */
public final class SkinTextures {
    public final int skin;
    public final boolean slim;
    public final int cape;

    public SkinTextures(int skin, boolean slim, int cape) {
        this.skin = skin;
        this.slim = slim;
        this.cape = cape;
    }
}
