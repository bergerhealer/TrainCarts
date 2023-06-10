package com.bergerkiller.bukkit.tc.attachments.config.transform;

import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.wrappers.ItemDisplayMode;
import org.bukkit.util.Vector;

/**
 * Hybrid ArmorStand and Display Entity item transformation options. These combine
 * the functionality of {@link ArmorStandItemTransformType} and
 * {@link com.bergerkiller.bukkit.common.wrappers.ItemDisplayMode ItemDisplayMode}
 * to represent the same transform for both client versions, supporting plugins like
 * ViaVersion. To accomplish this, a transformation is performed from one mode
 * to the other.
 */
public enum HybridItemTransformType {
    ARMORSTAND_HEAD("head \u24B6", 0.625, ArmorStandItemTransformType.HEAD, ItemDisplayMode.HEAD) {
        @Override
        public Matrix4x4 transformDisplay(Matrix4x4 tmp, Matrix4x4 transform) {
            tmp.set(transform);
            tmp.translate(0.0, 0.25, 0.0);
            return tmp;
        }
    },
    ARMORSTAND_HEAD_SMALL("head \u24B6\u24AE", 0.4375, ArmorStandItemTransformType.SMALL_HEAD, ItemDisplayMode.HEAD) {
        @Override
        public Matrix4x4 transformDisplay(Matrix4x4 tmp, Matrix4x4 transform) {
            tmp.set(transform);
            tmp.translate(0.0, 0.175, 0.0);
            return tmp;
        }
    },
    ARMORSTAND_RIGHT_HAND("right hand \u24B6", 1.0, ArmorStandItemTransformType.RIGHT_HAND, ItemDisplayMode.THIRD_PERSON_RIGHT_HAND) {
        @Override
        public Matrix4x4 transformDisplay(Matrix4x4 tmp, Matrix4x4 transform) {
            tmp.set(transform);
            tmp.translate(-0.0625, 0.12575, 0.625);
            return tmp;
        }
    },
    ARMORSTAND_RIGHT_HAND_SMALL("right hand \u24B6\u24AE", 0.5, ArmorStandItemTransformType.SMALL_RIGHT_HAND, ItemDisplayMode.THIRD_PERSON_RIGHT_HAND) {
        @Override
        public Matrix4x4 transformDisplay(Matrix4x4 tmp, Matrix4x4 transform) {
            tmp.set(transform);
            tmp.translate(-0.0315, 0.06275, 0.31225);
            tmp.worldTranslate(-0.03625, 0.19625, 0.0);
            return tmp;
        }
    },
    DISPLAY_HEAD("head \u24B9", 0.625, ArmorStandItemTransformType.HEAD, ItemDisplayMode.HEAD) {
        @Override
        public Matrix4x4 transformArmorStand(Matrix4x4 tmp, Matrix4x4 transform) {
            tmp.set(transform);
            tmp.translate(0.0, -0.25, 0.0);
            return tmp;
        }
    },
    DISPLAY_HEAD_SMALL("head \u24B9\u24AE", 0.4375, ArmorStandItemTransformType.SMALL_HEAD, ItemDisplayMode.HEAD) {
        @Override
        public Matrix4x4 transformArmorStand(Matrix4x4 tmp, Matrix4x4 transform) {
            tmp.set(transform);
            tmp.translate(0.0, -0.175, 0.0);
            return tmp;
        }
    },
    DISPLAY_RIGHT_HAND("right hand \u24B9", 1.0, ArmorStandItemTransformType.RIGHT_HAND, ItemDisplayMode.THIRD_PERSON_RIGHT_HAND) {
        @Override
        public Matrix4x4 transformArmorStand(Matrix4x4 tmp, Matrix4x4 transform) {
            tmp.set(transform);
            tmp.translate(-0.0625, -0.12575, 0.625);
            return tmp;
        }
    },
    DISPLAY_RIGHT_HAND_SMALL("right hand \u24B9\u24AE", 0.5, ArmorStandItemTransformType.SMALL_RIGHT_HAND, ItemDisplayMode.THIRD_PERSON_RIGHT_HAND) {
        @Override
        public Matrix4x4 transformArmorStand(Matrix4x4 tmp, Matrix4x4 transform) {
            tmp.set(transform);
            tmp.worldTranslate(0.03625, -0.19625, 0.0);
            tmp.translate(-0.0315, -0.06275, 0.31225);
            return tmp;
        }
    };

    private final String name;
    private final Vector displayScale;
    private final ArmorStandItemTransformType armorStandTransform;
    private final ItemDisplayMode displayMode;

    HybridItemTransformType(String name, double displayScale, ArmorStandItemTransformType armorStandTransform, ItemDisplayMode displayMode) {
        this.name = name;
        this.displayScale = new Vector(displayScale, displayScale, displayScale);
        this.armorStandTransform = armorStandTransform;
        this.displayMode = displayMode;
    }

    /**
     * Gets the scale vector to use when displaying the item using display entities
     *
     * @return Display entity scale factor
     */
    public Vector displayScale() {
        return displayScale;
    }

    /**
     * Gets the item display mode when diplaying the item using display entities
     *
     * @return display mode
     */
    public ItemDisplayMode displayMode() {
        return displayMode;
    }

    /**
     * Gets the armor stand item transformation type to use when displaying the item
     * using ArmorStands.
     *
     * @return ArmorStand item transform type
     */
    public ArmorStandItemTransformType armorStandTransform() {
        return armorStandTransform;
    }

    /**
     * Performs the matrix transformation for armorstand entities showing this type
     * of transform. If this is a DISPLAY category transformation, this will perform
     * the transformation to make the armorstand display at the right place. Otherwise,
     * it is a no-op.
     *
     * @param tmp Temporary scratch space to avoid allocating a new matrix
     * @param transform Input transform
     * @return Updated transform. Input transform should not have changed.
     */
    public Matrix4x4 transformArmorStand(Matrix4x4 tmp, Matrix4x4 transform) {
        return transform;
    }

    /**
     * Performs the matrix transformation for display entities showing this type
     * of transform. If this is an ARMORSTAND category transformation, this will
     * perform the transformation to make the display entity display at the right
     * place. Otherwise, it is a no-op.
     *
     * @param tmp Temporary scratch space to avoid allocating a new matrix
     * @param transform Input transform
     * @return Updated transform. Input transform should not have changed.
     */
    public Matrix4x4 transformDisplay(Matrix4x4 tmp, Matrix4x4 transform) {
        return transform;
    }

    @Override
    public String toString() {
        return name;
    }
}
