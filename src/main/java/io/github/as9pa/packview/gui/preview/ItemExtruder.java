package io.github.as9pa.packview.gui.preview;

import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/**
 * Turns an item texture into a 1/16 thick slab in the 0..1 box, like vanilla {@code ItemModelGenerator}:
 * a front and a back face over the whole texture, and a side face on every opaque texel edge that borders
 * transparency or the texture border. Each side quad is textured with its own texel column or row.
 */
public final class ItemExtruder {
    static final float Z0 = 7.5F / 16F;
    static final float Z1 = 8.5F / 16F;

    private ItemExtruder() {
    }

    /**
     * {@code argb} is row-major from the top-left, at least {@code w * h} long. Alpha above 0 counts as
     * opaque. Image row 0 is the top of the mesh (y = 1) and v = 0.
     */
    public static ItemMesh build(int[] argb, int w, int h) {
        if (argb == null || w <= 0 || h <= 0 || argb.length < w * h) {
            throw new IllegalArgumentException("bad item texture " + w + "x" + h);
        }
        int sides = 0;
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                if (!opaque(argb, w, h, i, j)) continue;
                if (!opaque(argb, w, h, i + 1, j)) sides++;
                if (!opaque(argb, w, h, i - 1, j)) sides++;
                if (!opaque(argb, w, h, i, j - 1)) sides++;
                if (!opaque(argb, w, h, i, j + 1)) sides++;
            }
        }
        int quads = 2 + sides;
        float[] d = new float[quads * 4 * ItemMesh.FLOATS_PER_VERTEX];
        int at = 0;
        // Front faces +z, back faces -z; both map u = x so the back shows the texture mirrored, as vanilla.
        at = put(d, at, 0, 0, Z1, 0, 1, 0, 0, 1);
        at = put(d, at, 1, 0, Z1, 1, 1, 0, 0, 1);
        at = put(d, at, 1, 1, Z1, 1, 0, 0, 0, 1);
        at = put(d, at, 0, 1, Z1, 0, 0, 0, 0, 1);
        at = put(d, at, 1, 0, Z0, 1, 1, 0, 0, -1);
        at = put(d, at, 0, 0, Z0, 0, 1, 0, 0, -1);
        at = put(d, at, 0, 1, Z0, 0, 0, 0, 0, -1);
        at = put(d, at, 1, 1, Z0, 1, 0, 0, 0, -1);
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                if (!opaque(argb, w, h, i, j)) continue;
                float x0 = (float) i / w;
                float x1 = (float) (i + 1) / w;
                float y0 = (float) (h - 1 - j) / h;
                float y1 = (float) (h - j) / h;
                float v0 = (float) j / h;
                float v1 = (float) (j + 1) / h;
                // Across the edge the side samples the texel centre so it never picks up a neighbour.
                float uc = (i + 0.5F) / w;
                float vc = (j + 0.5F) / h;
                if (!opaque(argb, w, h, i + 1, j)) {
                    at = put(d, at, x1, y0, Z1, uc, v1, 1, 0, 0);
                    at = put(d, at, x1, y0, Z0, uc, v1, 1, 0, 0);
                    at = put(d, at, x1, y1, Z0, uc, v0, 1, 0, 0);
                    at = put(d, at, x1, y1, Z1, uc, v0, 1, 0, 0);
                }
                if (!opaque(argb, w, h, i - 1, j)) {
                    at = put(d, at, x0, y0, Z0, uc, v1, -1, 0, 0);
                    at = put(d, at, x0, y0, Z1, uc, v1, -1, 0, 0);
                    at = put(d, at, x0, y1, Z1, uc, v0, -1, 0, 0);
                    at = put(d, at, x0, y1, Z0, uc, v0, -1, 0, 0);
                }
                if (!opaque(argb, w, h, i, j - 1)) {
                    at = put(d, at, x0, y1, Z1, x0, vc, 0, 1, 0);
                    at = put(d, at, x1, y1, Z1, x1, vc, 0, 1, 0);
                    at = put(d, at, x1, y1, Z0, x1, vc, 0, 1, 0);
                    at = put(d, at, x0, y1, Z0, x0, vc, 0, 1, 0);
                }
                if (!opaque(argb, w, h, i, j + 1)) {
                    at = put(d, at, x0, y0, Z0, x0, vc, 0, -1, 0);
                    at = put(d, at, x1, y0, Z0, x1, vc, 0, -1, 0);
                    at = put(d, at, x1, y0, Z1, x1, vc, 0, -1, 0);
                    at = put(d, at, x0, y0, Z1, x0, vc, 0, -1, 0);
                }
            }
        }
        return new ItemMesh(d, quads);
    }

    /** Records the mesh into a new display list. Client thread only; free it with {@link #delete(int)}. */
    public static int compile(ItemMesh mesh) {
        int list = GLAllocation.generateDisplayLists(1);
        GL11.glNewList(list, GL11.GL_COMPILE);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer renderer = tessellator.getWorldRenderer();
        renderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_NORMAL);
        float[] d = mesh.data;
        int end = mesh.quads * 4 * ItemMesh.FLOATS_PER_VERTEX;
        for (int at = 0; at < end; at += ItemMesh.FLOATS_PER_VERTEX) {
            renderer.pos(d[at], d[at + 1], d[at + 2]).tex(d[at + 3], d[at + 4])
                    .normal(d[at + 5], d[at + 6], d[at + 7]).endVertex();
        }
        tessellator.draw();
        GL11.glEndList();
        return list;
    }

    /** Frees a list from {@link #compile(ItemMesh)}; ids of 0 or below are ignored. Client thread only. */
    public static void delete(int list) {
        if (list > 0) {
            GLAllocation.deleteDisplayLists(list);
        }
    }

    private static boolean opaque(int[] argb, int w, int h, int i, int j) {
        return i >= 0 && j >= 0 && i < w && j < h && (argb[j * w + i] >>> 24) != 0;
    }

    private static int put(float[] d, int at, float x, float y, float z, float u, float v,
                           float nx, float ny, float nz) {
        d[at] = x;
        d[at + 1] = y;
        d[at + 2] = z;
        d[at + 3] = u;
        d[at + 4] = v;
        d[at + 5] = nx;
        d[at + 6] = ny;
        d[at + 7] = nz;
        return at + ItemMesh.FLOATS_PER_VERTEX;
    }
}
