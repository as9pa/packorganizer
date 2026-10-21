package io.github.as9pa.packview.gui.preview;

/** Quads built by {@link ItemExtruder}: x, y, z, u, v, nx, ny, nz per vertex, 4 vertices per quad. */
public final class ItemMesh {
    public static final int FLOATS_PER_VERTEX = 8;

    public final float[] data;
    public final int quads;

    ItemMesh(float[] data, int quads) {
        this.data = data;
        this.quads = quads;
    }
}
