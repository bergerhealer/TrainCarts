package com.bergerkiller.bukkit.tc.properties.standard.fieldbacked;

import java.util.List;
import java.util.Set;

import com.bergerkiller.bukkit.tc.properties.standard.type.ChunkLoadOptions;
import org.bukkit.Material;

import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.api.IProperty;
import com.bergerkiller.bukkit.tc.properties.standard.type.AttachmentModelBoundToCart;
import com.bergerkiller.bukkit.tc.properties.standard.type.BankingOptions;
import com.bergerkiller.bukkit.tc.properties.standard.type.CollisionOptions;
import com.bergerkiller.bukkit.tc.properties.standard.type.SignSkipOptions;
import com.bergerkiller.bukkit.tc.properties.standard.type.SlowdownMode;
import com.bergerkiller.bukkit.tc.properties.standard.type.WaitOptions;

/**
 * A property of TrainCarts itself that stores a copy of the property value
 * in the properties of the cart or train itself. This is often done when
 * accessing the property needs to be very fast, because it occurs
 * very often.
 * 
 * @param <T> Property value type
 */
public abstract class FieldBackedProperty<T> implements IProperty<T> {

    /**
     * Wraps internal data, making sure only field backed property
     * implementations can access it.
     */
    public static final class CartInternalDataHolder {
        protected final CartInternalData data = new CartInternalData();
    }

    /**
     * Wraps internal data, making sure only field backed property
     * implementations can access it.
     */
    public static final class TrainInternalDataHolder {
        protected final TrainInternalData data = new TrainInternalData();
    }

    /**
     * Holds a <b>copy</b> of the cart properties stored in YAML configuration
     * for faster access at runtime. Properties that aren't accessed
     * very often aren't stored here.
     */
    protected static class CartInternalData {
        public SignSkipOptions signSkipOptionsData;
        public Set<String> tags;
        public Set<String> owners;
        public Set<String> ownerPermissions;
        public Set<Material> blockBreakTypes;
        public boolean pickUpItems;
        public boolean canOnlyOwnersEnter;
        public AttachmentModelBoundToCart model = null;

        public static CartInternalData get(CartProperties properties) {
            return properties.getStandardPropertiesHolder().data;
        }
    }

    /**
     * Holds a <b>copy</b> of the train properties stored in YAML configuration
     * for faster access at runtime. Properties that aren't accessed
     * very often aren't stored here.
     */
    protected static final class TrainInternalData {
        public double speedLimit;
        public double gravity;
        public double friction;
        public double cartGap;
        public CollisionOptions collision;
        public Set<SlowdownMode> slowdown;
        public SignSkipOptions signSkipOptionsData;
        public WaitOptions waitOptionsData;
        public BankingOptions bankingOptionsData;
        public boolean soundEnabled;
        public ChunkLoadOptions chunkLoadOptions;
        public boolean allowPlayerManualMovement;
        public boolean allowMobManualMovement;
        public boolean realtimePhysics;
        public List<String> activeSavedTrainSpawnLimits;
        // Combined Sets from CartInternalData, computed and cached
        public final FieldBackedCombinedTrainProperty<String> tags = new FieldBackedCombinedTrainProperty<>();
        public final FieldBackedCombinedTrainProperty<String> owners = new FieldBackedCombinedTrainProperty<>();
        public final FieldBackedCombinedTrainProperty<String> ownerPermissions = new FieldBackedCombinedTrainProperty<>();

        public static TrainInternalData get(TrainProperties properties) {
            return properties.getStandardPropertiesHolder().data;
        }
    }
}
