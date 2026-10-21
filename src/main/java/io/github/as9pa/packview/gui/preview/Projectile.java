package io.github.as9pa.packview.gui.preview;

/**
 * One thrown or shot item in model space: blocks, feet at y 0, +z toward the viewer, yaw 0 facing +z. One physics
 * step per game tick; drawn like vanilla Render, interpolated from {@code px,py,pz} to {@code x,y,z} (and from
 * {@code prevYaw,prevPitch} to {@code yaw,pitch}) by the partial tick.
 */
public final class Projectile {
    public enum Kind { ARROW, PEARL, POTION, BOBBER }

    public final Kind kind;
    public double x, y, z, px, py, pz, vx, vy, vz;
    /** Facing in degrees like Entity.rotationYaw and rotationPitch: yaw atan2(vx, vz), pitch positive upward. */
    public float yaw, pitch, prevYaw, prevPitch;
    public boolean onGround, critical;
    /** Flew out of the view: it keeps flying unseen until it lands, so the landing still sounds, but is not drawn. */
    public boolean hidden;
    /** Arrow shake left after sticking, counted in physics steps. */
    public int shake;
    /** Game ticks since a stuck arrow landed. */
    public int groundTicks;

    final double drag, gravity;
    /** The landing has been handled: arrows stick, pearls arrive, potions shatter. */
    boolean stuck;
    /** Game ticks since the throw. */
    int age;

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

    public double drawX(float partial) {
        return px + (x - px) * partial;
    }

    public double drawY(float partial) {
        return py + (y - py) * partial;
    }

    public double drawZ(float partial) {
        return pz + (z - pz) * partial;
    }

    /** RenderArrow: prevRotationYaw + (rotationYaw - prevRotationYaw) * partial. */
    public float drawYaw(float partial) {
        return prevYaw + (yaw - prevYaw) * partial;
    }

    public float drawPitch(float partial) {
        return prevPitch + (pitch - prevPitch) * partial;
    }
}
