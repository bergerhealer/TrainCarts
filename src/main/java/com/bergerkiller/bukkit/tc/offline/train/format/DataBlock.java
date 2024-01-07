package com.bergerkiller.bukkit.tc.offline.train.format;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A single block of data which includes a name, data byte array and
 * optionally a number of child data blocks. This format is used for
 * serializing the groupdata format in the modern way, to allow for more
 * expandable data.
 */
public class DataBlock {
    private static final byte[] NO_DATA = new byte[0];

    /** Compresses string tokens used in data blocks or the data itself */
    private final DataBlockSerializer serializer;
    /** Name of this data block */
    public final String name;
    /** Data of this data block */
    public final byte[] data;
    /** Children added to this data block */
    public final List<DataBlock> children;

    /**
     * Reads a DataBlock from an input stream
     *
     * @param stream Stream to read from
     * @return DataBlock
     * @throws IOException
     */
    public static DataBlock read(DataInputStream stream) throws IOException {
        DataBlockSerializer serializer = new DataBlockSerializer();
        return serializer.readDataBlock(stream);
    }

    /**
     * Creates a new DataBlock
     *
     * @param name Name of the root data block
     * @return new DataBlock
     */
    public static DataBlock create(String name) {
        return new DataBlock(new DataBlockSerializer(), name, NO_DATA, new ArrayList<>());
    }

    DataBlock(DataBlockSerializer serializer, String name, byte[] data, List<DataBlock> children) {
        this.serializer = serializer;
        this.name = name;
        this.data = data;
        this.children = children;
    }

    /**
     * Writes this DataBlock to an output stream.
     *
     * @param stream Stream to write to
     * @throws IOException
     */
    public void writeTo(DataOutputStream stream) throws IOException {
        serializer.reset();
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
    public List<DataBlock> findChildren(String name) {
        List<DataBlock> result = new ArrayList<>(children.size());
        for (DataBlock child : children) {
            if (child.name.equals(name)) {
                result.add(child);
            }
        }
        return result;
    }

    /**
     * Adds a child to this data block with the name specified, and no data
     *
     * @param name Name of the child
     * @return Added DataBlock child
     */
    public DataBlock addChild(String name) {
        DataBlock child = new DataBlock(serializer, name, NO_DATA, new ArrayList<>());
        this.children.add(child);
        return child;
    }

    /**
     * Adds a child to this data block with the name specified, and data.
     * The callback should generate the data for the new data block.
     *
     * @param name Name of the child
     * @param writer Writer for generating the data of the child
     * @throws IOException If the writer throws one
     * @return Added DataBlock child
     */
    public DataBlock addChild(String name, DataWriter writer) throws IOException {
        DataBlock child = serializer.createDataBlock(name, writer);
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
            for (DataBlock child : children) {
                str.append('\n');
                child.appendToString(str, indent + 1);
            }
        }
    }

    @FunctionalInterface
    public interface DataWriter {
        void write(DataOutputStream stream) throws IOException;
    }
}
