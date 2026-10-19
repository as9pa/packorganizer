package io.github.as9pa.packorganizer.gui.preview;

/**
 * Third person displays of the held items, from the item model JSON in the 1.8.9 jar, read the way
 * {@code ItemTransformVec3f.Deserializer} reads them: rotation in degrees as written, translation times 1/16 and
 * clamped to +-1.5, scale clamped to +-4.
 */
public final class ItemDisplays {
    /** {@code item/handheld}: the swords. */
    public static final Display HANDHELD = Display.of(
            new float[] {0, 90, -35}, new float[] {0, 1.25F, -3.5F}, new float[] {0.85F, 0.85F, 0.85F});
    /** {@code item/generated}: ender pearl, golden apple and splash potion. */
    public static final Display GENERATED = Display.of(
            new float[] {-90, 0, 0}, new float[] {0, 1, -3}, new float[] {0.55F, 0.55F, 0.55F});
    /** {@code item/bow}, shared by the pulling stages. */
    public static final Display BOW = Display.of(
            new float[] {5, 80, -45}, new float[] {0.75F, 0, 0.25F}, new float[] {1, 1, 1});
    /** {@code item/fishing_rod}, shared by the cast rod. */
    public static final Display FISHING_ROD = Display.of(
            new float[] {180, 90, -35}, new float[] {0, 0, -3.5F}, new float[] {0.85F, 0.85F, 0.85F});

    private ItemDisplays() {}

    /** The third person display of a held item. */
    public static Display of(HeldItem item) {
        switch (item) {
            case SWORD:
                return HANDHELD;
            case BOW:
                return BOW;
            case FISHING_ROD:
                return FISHING_ROD;
            default:
                return GENERATED;
        }
    }

    /**
     * One display as loaded. {@code ItemCameraTransforms.applyTransform} draws it as translate, rotate Y, rotate X,
     * rotate Z, scale.
     */
    public static final class Display {
        /** Degrees. */
        public final float rotX, rotY, rotZ;
        /** Blocks. */
        public final float x, y, z;
        public final float scaleX, scaleY, scaleZ;

        private Display(float[] rotation, float[] translation, float[] scale) {
            rotX = rotation[0];
            rotY = rotation[1];
            rotZ = rotation[2];
            x = translation[0];
            y = translation[1];
            z = translation[2];
            scaleX = scale[0];
            scaleY = scale[1];
            scaleZ = scale[2];
        }

        /** A display from JSON values: translation in 1/16 block. */
        static Display of(float[] rotation, float[] translation, float[] scale) {
            float[] t = new float[3];
            float[] s = new float[3];
            for (int i = 0; i < 3; i++) {
                t[i] = clamp(translation[i] * 0.0625F, 1.5F);
                s[i] = clamp(scale[i], 4F);
            }
            return new Display(rotation.clone(), t, s);
        }

        private static float clamp(float v, float limit) {
            return v < -limit ? -limit : v > limit ? limit : v;
        }
    }
}
