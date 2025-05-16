package com.bergerkiller.bukkit.tc.offline.train.format;

import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.tc.Util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A single block of data which includes a name, data byte array and
 * optionally a number of child data blocks. This format is used for
 * serializing the groupdata format in the modern way, to allow for more
 * expandable data.
 */
public final class OfflineDataBlock {
    private static final byte[] NO_DATA = new byte[0];

    /** Is shared between all data blocks to efficiently serialize data */
    private final DataBlockBuilder dataBlockBuilder;
    /** Name of this data block */
    public final String name;
    /** Data of this data block */
    public final byte[] data;
    /** Children added to this data block */
    public final List<OfflineDataBlock> children;

    /**
     * Reads a OfflineDataBlock from an input stream
     *
     * @param stream Stream to read from
     * @return OfflineDataBlock
     * @throws IOException
     */
    public static OfflineDataBlock read(DataInputStream stream) throws IOException {
        OfflineDataBlockSerializer serializer = new OfflineDataBlockSerializer();
        return serializer.readDataBlock(stream);
    }

    /**
     * Creates a new OfflineDataBlock
     *
     * @param name Name of the root data block
     * @return new OfflineDataBlock
     */
    public static OfflineDataBlock create(String name) {
        return new OfflineDataBlock(new DataBlockBuilder(), name, NO_DATA);
    }

    /**
     * Creates a new OfflineDataBlock with data
     *
     * @param name Name of the root data block
     * @param data Data
     * @return new OfflineDataBlock
     */
    public static OfflineDataBlock createWithData(String name, byte[] data) {
        return new OfflineDataBlock(new DataBlockBuilder(), name, data);
    }

    /**
     * Creates a new OfflineDataBlock with data
     *
     * @param name Name of the root data block
     * @param writer Writer for generating the data of the child
     * @throws IOException If the writer throws one
     * @return new OfflineDataBlock, or <i>null</i> if the writer throws the
     *         {@link AbortChildException}
     */
    public static OfflineDataBlock createWithData(String name, DataWriter writer) throws IOException {
        return (new DataBlockBuilder()).create(name, writer);
    }

    OfflineDataBlock(DataBlockBuilder dataBlockBuilder, String name, byte[] data) {
        this.dataBlockBuilder = dataBlockBuilder;
        this.name = name;
        this.data = data;
        this.children = new ArrayList<>();
    }

    /**
     * Writes this OfflineDataBlock to an output stream.
     *
     * @param stream Stream to write to
     * @throws IOException
     */
    public void writeTo(DataOutputStream stream) throws IOException {
        OfflineDataBlockSerializer serializer = new OfflineDataBlockSerializer();
        serializer.writeDataBlock(stream, this);
    }

    /**
     * Opens a stream to read the data of this data block
     *
     * @return data stream
     */
    public DataInputStream readData() {
        return new DataInputStream(new ByteArrayInputStream(data));
    }

    /**
     * Finds all children data blocks that have a certain name
     *
     * @param name Name of the data block
     * @return Children, empty list if none are found
     */
    public List<OfflineDataBlock> findChildren(String name) {
        return Util.filterList(Collections.unmodifiableList(children), c -> c.name.equals(name));
    }

    /**
     * Finds the first child that has a certain name. Throws a runtime exception
     * if none is found.
     *
     * @param name Name of the data block
     * @return Found child
     */
    public OfflineDataBlock findChildOrThrow(String name) {
        return findChild(name).orElseThrow(() -> new RuntimeException(
                "Data '" + name + "' is missing in '" + OfflineDataBlock.this.name + "' data"));
    }

    /**
     * Finds the first child that has a certain name. Returns an empty
     * optional if none is found.
     *
     * @param name Name of the data block
     * @return Found child, or empty if not found
     */
    public Optional<OfflineDataBlock> findChild(String name) {
        for (OfflineDataBlock child : children) {
            if (child.name.equals(name)) {
                return Optional.of(child);
            }
        }
        return Optional.empty();
    }

    /**
     * Chains {@link #findChild(String)} and {@link OfflineDataBlock#readData()} with
     * a callback to read that data, if the child exists.
     *
     * @param name Name of the data block
     * @param reader Callback reader that will read the data of the child data block,
     *               if it exists
     * @return True if the data block existed, False if not
     * @throws IOException
     */
    public boolean tryReadChild(String name, DataReader reader) throws IOException {
        OfflineDataBlock block = findChild(name).orElse(null);
        if (block == null) {
            return false;
        }

        try (DataInputStream stream = block.readData()) {
            reader.read(stream);
        }
        return true;
    }

    /**
     * Adds a child to this data block with the name specified, and no data
     *
     * @param name Name of the child
     * @return Added OfflineDataBlock child
     */
    public OfflineDataBlock addChild(String name) {
        return addChild(new OfflineDataBlock(dataBlockBuilder, name, NO_DATA));
    }

    /**
     * Adds a child to this data block with the name specified, and data.
     * The callback should generate the data for the new data block.
     *
     * @param name Name of the child
     * @param writer Writer for generating the data of the child
     * @throws IOException If the writer throws one
     * @return Added OfflineDataBlock child, or <i>null</i> if aborted with
     *         {@link AbortChildException}
     */
    public OfflineDataBlock addChild(String name, DataWriter writer) throws IOException {
        OfflineDataBlock child = addChildOrAbort(name, writer);
        if (child == null) {
            throw new IllegalStateException("AbortChildException thrown in addChild. Use addChildOrAbort instead!");
        }
        return child;
    }

    /**
     * Adds a child to this data block with the name specified, and data.
     * The callback should generate the data for the new data block.
     * If the writer throws an {@link AbortChildException} then the
     * child is not added, and null is returned.
     *
     * @param name Name of the child
     * @param writer Writer for generating the data of the child
     * @throws IOException If the writer throws one
     * @return Added OfflineDataBlock child, or <i>null</i> if aborted with
     *         {@link AbortChildException}
     */
    public OfflineDataBlock addChildOrAbort(String name, AbortableDataWriter writer) throws IOException {
        OfflineDataBlock child = dataBlockBuilder.create(name, writer);
        if (child == null) {
            return null;
        }
        return addChild(child);
    }

    /**
     * Adds a child to this data block with the name specified, and data.
     *
     * @param name Name of the child
     * @param data Data for the child
     * @return Added OfflineDataBlock child
     */
    public OfflineDataBlock addChild(String name, byte[] data) {
        return addChild(new OfflineDataBlock(dataBlockBuilder, name, data));
    }

    OfflineDataBlock addChild(OfflineDataBlock child) {
        this.children.add(child);
        return child;
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        appendToString(str, 0);
        return str.toString();
    }

    private void appendToString(StringBuilder str, int indent) {
        for (int i = 0; i < indent; i++) {
            str.append("  ");
        }
        str.append(name);
        if (data.length > 0) {
            str.append(" b[").append(data.length).append("]");
        }
        if (!children.isEmpty()) {
            str.append(':');
            for (OfflineDataBlock child : children) {
                str.append('\n');
                child.appendToString(str, indent + 1);
            }
        }
    }

    @FunctionalInterface
    public interface AbortableDataWriter {
        void write(DataOutputStream stream) throws IOException, AbortChildException;
    }

    @FunctionalInterface
    public interface DataWriter extends AbortableDataWriter {
        void write(DataOutputStream stream) throws IOException;
    }

    @FunctionalInterface
    public interface DataReader {
        void read(DataInputStream stream) throws IOException;
    }

    /**
     * Throw this exception inside the data writer to abort adding/creating the child
     */
    public static final class AbortChildException extends Exception {
    }

    static final class DataBlockBuilder {
        private WeakReference<ByteArrayOutputStream> stream = LogicUtil.nullWeakReference();

        public OfflineDataBlock create(String name, AbortableDataWriter writer) throws IOException {
            ByteArrayOutputStream tempByteArrayStream = this.stream.get();
            if (tempByteArrayStream == null) {
                tempByteArrayStream = new ByteArrayOutputStream(64);
                this.stream = new WeakReference<>(tempByteArrayStream);
            }
            try {
                try (DataOutputStream stream = new DataOutputStream(tempByteArrayStream)) {
                    writer.write(stream);
                }
                return new OfflineDataBlock(this, name, tempByteArrayStream.toByteArray());
            } catch (AbortChildException ex) {
                return null;
            } finally {
                tempByteArrayStream.reset();
            }
        }
    }
}
