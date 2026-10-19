package io.github.as9pa.packorganizer.gui.preview;

/**
 * One preview particle in model space: blocks, feet at y 0, +z toward the viewer. Drawn interpolated from
 * {@code px,py,pz} to {@code x,y,z} with the partial tick.
 */
public final class Particle {
    public enum ParticleKind { CRIT, MAGIC_CRIT, PORTAL, ITEM_CRACK, SPELL_MOB, SPELL_INSTANT }

    /** Side of an ITEM_CRACK patch in texture units: 4x4 texels of a 16 px item. */
    public static final float PATCH = 0.25f;

    public final ParticleKind kind;
    public double x, y, z, px, py, pz, vx, vy, vz;
    public int age, maxAge;
    /** Quad half size in blocks (0.1 * EntityFX.particleScale). */
    public float scale;
    public float r, g, b;
    /** Cell in particles.png (16 x 16 grid); -1 for ITEM_CRACK, which draws from the item texture. */
    public int sprite;
    /** ITEM_CRACK: top left of the patch in the item texture, 0..0.75. */
    public float u0, v0;
    /** ITEM_CRACK: the patch comes from the splash bottle, otherwise from the golden apple. */
    public boolean bottle;

    /** PORTAL: the origin the particle eases back to from its offset. */
    double ox, oy, oz;
    /** SPELL_MOB and SPELL_INSTANT: first frame; the 8 frames play backward over the life. */
    int base;

    Particle(ParticleKind kind, double x, double y, double z, double vx, double vy, double vz, int maxAge, float scale) {
        this.kind = kind;
        this.x = this.px = x;
        this.y = this.py = y;
        this.z = this.pz = z;
        this.vx = vx;
        this.vy = vy;
        this.vz = vz;
        this.maxAge = maxAge;
        this.scale = scale;
    }
}
