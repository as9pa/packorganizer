package io.github.as9pa.packorganizer.gui.preview;

/**
 * One thrown or shot item in model space: blocks, feet at y 0, +z toward the viewer, yaw 0 facing +z. Drawn
 * interpolated from {@code px,py,pz} to {@code x,y,z} with {@link #acc} as the partial step.
 */
public final class Projectile {
    public enum Kind { ARROW, PEARL, POTION, BOBBER }

    public final Kind kind;
    public double x, y, z, px, py, pz, vx, vy, vz;
    public boolean onGround, critical;
    /** Arrow shake left after sticking, counted in physics steps. */
    public int shake;
    /** Progress toward the next physics step, 0..1; also the partial step for drawing. */
    public float acc;
    /** Game ticks since a stuck arrow landed. */
    public int groundTicks;

    final double drag, gravity;
    /** The landing has been handled: arrows stick, pearls arrive, potions shatter. */
    boolean stuck;
    /** Game ticks since the throw. */
    int age;
    /** Crit trail particles already spawned along the current segment. */
    int critK;

    Projectile(Kind kind, double x, double y, double z, double vx, double vy, double vz, double drag, double gravity) {
        this.kind = kind;
        this.x = this.px = x;
        this.y = this.py = y;
        this.z = this.pz = z;
        this.vx = vx;
        this.vy = vy;
        this.vz = vz;
        this.drag = drag;
        this.gravity = gravity;
    }
}
