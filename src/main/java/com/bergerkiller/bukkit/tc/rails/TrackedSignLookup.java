package com.bergerkiller.bukkit.tc.rails;

import com.bergerkiller.bukkit.common.block.SignChangeTracker;
import com.bergerkiller.bukkit.common.offline.OfflineBlock;
import com.bergerkiller.bukkit.common.utils.StreamUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.offline.train.format.DataBlock;
import org.bukkit.block.Block;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.logging.Level;

/**
 * Looks up tracked signs by their unique key. Other lookup methods can be
 * registered to support custom external plugin sign types.
 */
public final class TrackedSignLookup implements TrainCarts.Provider {
    private final TrainCarts plugin;
    private final List<SignSupplier> suppliers = new ArrayList<>();
    private final Map<String, RegisteredKeySerializer> serializersById = new HashMap<>();
    private final WeakHashMap<Class<?>, RegisteredKeySerializer> serializersByType = new WeakHashMap<>();

    /**
     * This serializer is stored in the serializersByType mapping for Class key types
     * that lack a serializer
     */
    private static final RegisteredKeySerializer MISSING_SERIALIZER = new RegisteredKeySerializer(null, null) {
        @Override
        public byte[] serialize(TrainCarts plugin, Object uniqueKey) {
            return null;
        }
    };

    public TrackedSignLookup(TrainCarts plugin) {
        this.plugin = plugin;

        // When unknown keys are decoded, encode them back the exact same way
        // This is for when plugin-provided keys aren't available and data still lingers
        serializersByType.put(UnknownSignKey.class, new RegisteredKeySerializer(null, null) {
            @Override
            public byte[] serialize(TrainCarts plugin, Object uniqueKey) {
                return ((UnknownSignKey) uniqueKey).data;
            }
        });

        // TrainCarts default serializers for some types
        registerSerializer("tc-realsign", new RealSignKeySerializer());
        registerSerializer("tc-uuid", new KeySerializer<UUID>() {
            @Override
            public Class<UUID> getKeyType() {
                return UUID.class;
            }

            @Override
            public UUID read(DataInputStream input) throws IOException {
                return StreamUtil.readUUID(input);
            }

            @Override
            public void write(DataOutputStream output, UUID value) throws IOException {
                StreamUtil.writeUUID(output, value);
            }
        });
        registerSerializer("tc-string", new KeySerializer<String>() {
            @Override
            public Class<String> getKeyType() {
                return String.class;
            }

            @Override
            public String read(DataInputStream input) throws IOException {
                return input.readUTF();
            }

            @Override
            public void write(DataOutputStream output, String value) throws IOException {
                output.writeUTF(value);
            }
        });

        // TrainCarts real sign supplier
        register(new RealSignSupplier());
    }

    @Override
    public TrainCarts getTrainCarts() {
        return plugin;
    }

    /**
     * Looks up a TrackedSign by its unique key
     *
     * @param uniqueKey Unique Key
     * @return TrackedSign that exists that has this unique key, or null if
     *         this sign does not exist
     */
    public RailLookup.TrackedSign getTrackedSign(Object uniqueKey) {
        for (SignSupplier supplier : suppliers) {
            RailLookup.TrackedSign sign = supplier.getTrackedSign(uniqueKey);
            if (sign != null) {
                return sign;
            }
        }
        return null;
    }

    /**
     * Serializes all the items as sign unique key based metadata
     *
     * @param items Items
     * @param name Name for the resulting DataBlock
     * @param uniqueKeyGetter Function to get the unique sign key of each item
     * @return Unmodifiable list of serialized DataBlocks
     * @param <T> Input List Type
     */
    public <T> List<DataBlock> serializeUniqueKeys(
            final Collection<T> items,
            final String name,
            final Function<T, Object> uniqueKeyGetter
    ) {
        return serializeUniqueKeys(items, name, uniqueKeyGetter, (item, data) -> {});
    }

    /**
     * Serializes all the items as sign unique key based metadata
     *
     * @param items Items
     * @param name Name for the resulting DataBlock
     * @param uniqueKeyGetter Function to get the unique sign key of each item
     * @param extraMetaApplier Optional metadata applier for each DataBlock
     * @return Unmodifiable list of serialized DataBlocks
     * @param <T> Input List Type
     */
    public <T> List<DataBlock> serializeUniqueKeys(
            final Collection<T> items,
            final String name,
            final Function<T, Object> uniqueKeyGetter,
            final BiConsumer<T, DataBlock> extraMetaApplier
    ) {
        if (items.isEmpty()) {
            return Collections.emptyList();
        }

        List<DataBlock> dataBlocks = new ArrayList<>(items.size());
        for (T item : items) {
            byte[] data = serializeUniqueKey(uniqueKeyGetter.apply(item));
            if (data != null) {
                DataBlock dataBlock = DataBlock.createWithData(name, data);
                extraMetaApplier.accept(item, dataBlock);
                dataBlocks.add(dataBlock);
            }
        }
        return Collections.unmodifiableList(dataBlocks);
    }

    /**
     * Attempts to serialize a unique key to a byte[] array so that it can be persistently
     * stored. Returns null if the type cannot be serialized.
     *
     * @param uniqueKey Unique Key
     * @return Serialized data byte array, or null if the unique key cannot be serialized
     */
    public byte[] serializeUniqueKey(Object uniqueKey) {
        if (uniqueKey == null) {
            return null; // Eh?
        }

        // Find a registered serializer for this key type. Also check if this unique key
        // is maybe a subclass of a registered type.
        // If not found, map it to MISSING_SERIALIZER for faster lookup next time.
        RegisteredKeySerializer registered = serializersByType.get(uniqueKey.getClass());
        if (registered == null) {
            registered = MISSING_SERIALIZER;
            Class<?> type = uniqueKey.getClass();
            for (Map.Entry<Class<?>, RegisteredKeySerializer> mapped : serializersByType.entrySet()) {
                if (mapped.getKey().isAssignableFrom(type)) {
                    registered = mapped.getValue();
                    break;
                }
            }
            serializersByType.put(type, registered);
        }
        return registered.serialize(plugin, uniqueKey);
    }

    /**
     * Deserializes the byte[] data of all data blocks specified into unique sign keys,
     * and returns the deserialized items as a new unmodifiable List.
     *
     * @param dataBlocks Data Blocks whose data to deserialize
     * @return Unmodifiable List of unique sign keys
     */
    public List<Object> deserializeUniqueKeys(List<DataBlock> dataBlocks) {
        if (dataBlocks.isEmpty()) {
            return Collections.emptyList();
        }

        List<Object> uniqueKeys = new ArrayList<>(dataBlocks.size());
        for (DataBlock dataBlock : dataBlocks) {
            Object uniqueKey = deserializeUniqueKey(dataBlock.data);
            if (uniqueKey != null) {
                uniqueKeys.add(uniqueKey);
            }
        }
        return Collections.unmodifiableList(uniqueKeys);
    }

    /**
     * Deserializes a previously serialized unique key. Returns null if the data could
     * not be decoded.
     *
     * @param data Unique Key serialized data
     * @return Deserialized Unique Key, or null if deserialization failed
     */
    public Object deserializeUniqueKey(byte[] data) {
        try {
            try (ByteArrayInputStream stream = new ByteArrayInputStream(data);
                 DataInputStream dataStream = new DataInputStream(stream)
            ) {
                String id = dataStream.readUTF();
                RegisteredKeySerializer registered = serializersById.get(id);

                // If not registered, provide a fallback
                if (registered == null) {
                    return new UnknownSignKey(id, data);
                }

                // Try to deserialize
                return registered.serializer.read(dataStream);
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Failed to deserialize unique sign key", t);
        }

        return null;
    }

    /**
     * Registers a new sign supplier that is then called to look up
     * signs with a certain unique key
     *
     * @param supplier Sign Supplier to register
     */
    public void register(SignSupplier supplier) {
        if (!suppliers.contains(supplier)) {
            suppliers.add(supplier);
        }
    }

    /**
     * Un-registers a previously registered sign supplier, so that it is
     * no longer called.
     *
     * @param supplier Sign Supplier to un-register
     */
    public void unregister(SignSupplier supplier) {
        suppliers.remove(supplier);
    }

    /**
     * Registers a unique key serializer
     *
     * @param id ID of the key type, which is used to find this serializer again
     * @param serializer Key Serializer to register
     */
    public void registerSerializer(String id, KeySerializer<?> serializer) {
        Class<?> keyType = serializer.getKeyType();
        RegisteredKeySerializer registered = new RegisteredKeySerializer(id, serializer);
        serializersById.put(id, registered);
        serializersByType.put(keyType, registered);

        // Remove all classes mapped to the MISSING_SERIALIZER as those may have become invalid
        // after registering this new type. They're regenerated.
        serializersByType.values().removeIf(s -> s == MISSING_SERIALIZER);
    }

    /**
     * Un-registers a unique key serializer
     *
     * @param id ID of the key type that was previously registered
     */
    public void unregisterSerializer(String id) {
        serializersById.remove(id);
        // Note: do not unregister the by-class mapping. We might find this key still in use
        //       in TrainCarts somewhere, so we must be able to serialize those still.
        //       It's a weak-keyed hashmap, so this is no big deal.
    }

    private static class RegisteredKeySerializer {
        public final String id;
        public final KeySerializer<Object> serializer;

        @SuppressWarnings("unchecked")
        public RegisteredKeySerializer(String id, KeySerializer<?> serializer) {
            this.id = id;
            this.serializer = (KeySerializer<Object>) serializer;
        }

        public byte[] serialize(TrainCarts plugin, Object uniqueKey) {
            try {
                try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
                    try (DataOutputStream dataStream = new DataOutputStream(stream)) {
                        dataStream.writeUTF(id);
                        serializer.write(dataStream, uniqueKey);
                    }
                    return stream.toByteArray();
                }
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE, "Failed to serialize unique sign key " +
                        uniqueKey.getClass().getName(), t);
                return null;
            }
        }
    }

    /**
     * Reads and writes the unique key of signs. Only unique keys that have a registered
     * serializer are written.
     */
    public interface KeySerializer<T> {
        Class<T> getKeyType();
        T read(DataInputStream input) throws IOException;
        void write(DataOutputStream output, T value) throws IOException;
    }

    /**
     * Supplies tracked signs from the unique key the tracked sign had
     */
    @FunctionalInterface
    public interface SignSupplier {
        /**
         * Gets the tracked sign that has the unique key specified. Returns null
         * if the sign by this unique key is not found.
         *
         * @param uniqueKey Unique key
         * @return TrackedSign with this key
         */
        RailLookup.TrackedSign getTrackedSign(Object uniqueKey);
    }

    private static class RealSignKeySerializer implements KeySerializer<RealSignKey> {
        @Override
        public Class<RealSignKey> getKeyType() {
            return RealSignKey.class;
        }

        @Override
        public RealSignKey read(DataInputStream input) throws IOException {
            byte version = input.readByte();
            if (version == 1) {
                // Version 1
                OfflineBlock block = OfflineBlock.readFrom(input);
                boolean front = input.readBoolean();
                return new RealSignKey(block, front);
            } else {
                // Unsupported
                return null;
            }
        }

        @Override
        public void write(DataOutputStream output, RealSignKey value) throws IOException {
            // Version 1
            output.writeByte(1);
            OfflineBlock.writeTo(output, value.block);
            output.writeBoolean(value.front);
        }
    }

    private static class RealSignSupplier implements SignSupplier {
        @Override
        public RailLookup.TrackedSign getTrackedSign(Object uniqueKey) {
            // Must be a RealSignKey
            if (uniqueKey instanceof RealSignKey) {
                return ((RealSignKey) uniqueKey).findRealSign();
            }

            return null;
        }
    }

    /**
     * Key of a real sign in the Minecraft world
     */
    protected static final class RealSignKey {
        public final OfflineBlock block;
        public final boolean front;
        private final int hashCode;

        public RealSignKey(OfflineBlock block, boolean front) {
            this.block = block;
            this.front = front;
            this.hashCode = block.hashCode();
        }

        public RailLookup.TrackedSign findRealSign() {
            Block loaded = block.getLoadedBlock();
            if (loaded == null) {
                return null;
            }
            SignChangeTracker tracker = SignChangeTracker.track(loaded);
            if (tracker.isRemoved()) {
                return null;
            }

            RailLookup.TrackedSign sign = RailLookup.TrackedSign.forRealSign(tracker, front, RailPiece.NONE);
            sign.rail = null; // Discover on first use
            return sign;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            } else if (o instanceof RealSignKey) {
                RealSignKey other = (RealSignKey) o;
                return this.block.equals(other.block) && this.front == other.front;
            } else {
                return false;
            }
        }

        @Override
        public String toString() {
            return "RealSign{block=" + block + " side=" + (front ? "front" : "back") + "}";
        }
    }

    /**
     * This sign key is used when de-serializing a type of key that is not registered.
     * It makes sure that any metadata mapped to signs doesn't get lost when the plugin
     * providing this sign isn't enabled at one point.
     */
    private static class UnknownSignKey {
        public final String id;
        public final byte[] data;

        public UnknownSignKey(String id, byte[] data) {
            this.id = id;
            this.data = data;
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof UnknownSignKey) {
                return Arrays.equals(data, ((UnknownSignKey) o).data);
            } else {
                return false;
            }
        }

        @Override
        public String toString() {
            return "UnknownSignKey{" + id + "}@" + System.identityHashCode(this);
        }
    }
}
