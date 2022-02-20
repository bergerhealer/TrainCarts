package com.bergerkiller.bukkit.tc.offline.sign;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Handler for persistently stored sign metadata of some type.
 * Encodes/decodes this metadata, and is notified when metadata
 * of signs change.
 *
 * @param <T> Metadata class type - must be immutable
 */
public interface OfflineSignMetadataHandler<T> {

    /**
     * Gets the encoded data version of this metadata handler. If the encoded
     * data changes, this method can be overrided to bump to a different value.
     * Combine that with a migrating decoder to decode older version encoded
     * data.<br>
     * <br>
     * Avoid using negative or very large metadata versions as they take more
     * bytes to encode.
     *
     * @return metadata encoder data version
     */
    default int getMetadataVersion() {
        return 0;
    }

    /**
     * Gets a decoder for decoding older (or newer) encoded metadata. This is used
     * when data is read that uses a data version that differs from
     * {@link #getMetadataVersion()}.
     *
     * @param sign Sign details for which the metadata is
     * @param dataVersion Data version of the encoded data to decode
     * @return Migration decoder for this metadata
     * @throws UnsupportedOperationException If the data cannot be decoded
     */
    default DataMigrationDecoder<T> getMigrationDecoder(OfflineSign sign, int dataVersion) {
        throw new UnsupportedOperationException("Not supported");
    }

    /**
     * Called when the metadata bound to a sign is updated
     * through the registry api.
     *
     * @param store OfflineSignStore where this handler was registered
     * @param sign Sign whose metadata was updated
     * @param oldValue Old metadata value
     * @param newValue New metadata value
     */
    void onUpdated(OfflineSignStore store, OfflineSign sign, T oldValue, T newValue);

    /**
     * Called when a sign is loaded in, or registered/added
     * through the registry api.
     *
     * @param store OfflineSignStore where this handler was registered
     * @param sign Offline sign that was added
     * @param metadata The new metadata now bound to the sign
     */
    void onAdded(OfflineSignStore store, OfflineSign sign, T metadata);

    /**
     * Called when a sign with metadata that had previously existed, no longer
     * exists, was destroyed by a player or had it's metadata removed.
     *
     * @param store OfflineSignStore where this handler was registered
     * @param sign Offline sign that was removed
     * @param metadata The metadata bound to the sign at the time of removal
     */
    void onRemoved(OfflineSignStore store, OfflineSign sign, T metadata);

    /**
     * Called when this handler is registered and all metadata entries are loaded in,
     * or when a world loads and {@link #isUnloadedWorldsIgnored()} is <i>true</i>.<br>
     * <br>
     * By default call {@link #onAdded(OfflineSignStore, OfflineSign, Object) onAdded()}
     * but can be overrided to do something different.
     *
     * @param store OfflineSignStore where this handler was registered
     * @param sign Offline sign that was loaded
     * @param metadata The metadata bound to the sign at the time of loading
     */
    default void onLoaded(OfflineSignStore store, OfflineSign sign, T metadata) {
        this.onAdded(store, sign, metadata);
    }

    /**
     * Called when the handler is unregistered and all previous metadata is unloaded,
     * or when a world unloads and {@link #isUnloadedWorldsIgnored()} is <i>true</i>.<br>
     * <br>
     * By default calls {@link #onRemoved(OfflineSignStore, OfflineSign, Object) onRemoved()}
     * but can be overrided to do something different.
     *
     * @param store OfflineSignStore where this handler was registered
     * @param sign Offline sign that was unloaded
     * @param metadata The metadata bound to the sign at the time of unloading
     */
    default void onUnloaded(OfflineSignStore store, OfflineSign sign, T metadata) {
        this.onRemoved(store, sign, metadata);
    }

    /**
     * Called when the contents of a sign have changed since storing metadata for it.
     * In here, new metadata can be returned that correctly applied to the new sign, if
     * the two signs are similar enough in content. It should return null if the sign is
     * no longer valid for containing this metadata. By returning the old metadata,
     * nothing changes.<br>
     * <br>
     * If null is returned, {@link #onRemoved(OfflineSignStore, OfflineSign, Object)} will be
     * called with the previous sign information and metadata. Otherwise,
     * {@link #onUpdated(OfflineSignStore, OfflineSign, Object, Object)} will be called with
     * the new sign and old/new metadata values. This will also be called if the metadata
     * didn't change, since the sign did.
     *
     * @param store OfflineSignStore where this handler was registered
     * @param oldSign Previous sign's contents
     * @param newSign New sign's contents
     * @param metadata The metadata that was stored for this sign
     * @return The new metadata to store for this sign position. Return the the previous
     *         metadata to keep it, or return null to remove the metadata from the store.
     */
    default T onSignChanged(OfflineSignStore store, OfflineSign oldSign, OfflineSign newSign, T metadata) {
        return null;
    }

    /**
     * Called when sign metadata has to be encoded
     *
     * @param stream Stream to write the encoded data to
     * @param sign Offline sign whose metadata is being encoded
     * @param value Sign metadata to be encoded
     * @throws IOException
     */
    void onEncode(DataOutputStream stream, OfflineSign sign, T value) throws IOException;

    /**
     * Called when sign metadata has to be decoded from a data
     * stream
     *
     * @param stream Stream to read encoded data from
     * @param sign Offline sign whose metadata is being decoded
     * @return Encoded metadata value
     * @throws IOException
     */
    T onDecode(DataInputStream stream, OfflineSign sign) throws IOException;

    /**
     * Gets whether metadata stored for worlds that are not loaded
     * is remembered, but not decoded and loaded in. Once the world is later
     * loaded, all metadata is loaded in anyway and {@link #onAdded(OfflineSign, Object)}
     * is called for each. If an existing loaded world is unloaded,
     * {@link #onRemoved(OfflineSign, Object)} is called for all offline signs on that world.<br>
     * <br>
     * This is by default true, but can be overrided to return false so that this
     * registry knows of all signs, including those on worlds that aren't currently loaded.
     *
     * @return True if signs on unloaded worlds are ignored
     */
    default boolean isUnloadedWorldsIgnored() {
        return true;
    }

    /**
     * Decoder used when older (or newer) encoded data needs to be decoded.
     * For example, when decoding an older save file after the plugin was
     * updated.
     *
     * @param <T> Metadata class type - must be immutable
     */
    public static interface DataMigrationDecoder<T> {

        /**
         * Called when sign metadata has to be decoded from a data
         * stream encoded using an older/different data version.
         *
         * @param stream Stream to read encoded data from
         * @param sign Offline sign whose metadata is being decoded
         * @param dataVersion The data version of the encoded data
         * @return Encoded metadata value
         * @throws IOException
         */
        T onDecode(DataInputStream stream, OfflineSign sign, int dataVersion) throws IOException;
    }

    /**
     * Exception that can be thrown from
     * {@link OfflineSignMetadataHandler#onEncode(DataOutputStream, OfflineSign, Object) onEncode()}
     * or
     * {@link OfflineSignMetadataHandler#onDecode(DataInputStream, OfflineSign) onDecode()}
     * to indicate the current metadata is invalid and should not be further decoded/encoded.
     * When thrown during encoding the metadata is subsequently removed, with the usual removal
     * callbacks firing for the handler.<br>
     * <br>
     * No exception is logged when this exception is thrown.
     */
    public static final class InvalidMetadataException extends RuntimeException {
        private static final long serialVersionUID = 1301135081987007765L;

        public InvalidMetadataException() {
        }
    }
}
