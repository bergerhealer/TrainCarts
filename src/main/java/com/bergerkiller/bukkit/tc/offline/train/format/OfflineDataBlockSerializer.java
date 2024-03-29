package com.bergerkiller.bukkit.tc.offline.train.format;

import com.bergerkiller.bukkit.tc.Util;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper class for serializing and de-serializing data blocks
 */
class OfflineDataBlockSerializer {
    private final List<String> values = new ArrayList<>();
    private final Map<String, Integer> valueToIndex = new HashMap<>();
    private final OfflineDataBlock.DataBlockBuilder dataBlockBuilder = new OfflineDataBlock.DataBlockBuilder();

    public OfflineDataBlockSerializer() {
        reset();
    }

    public void reset() {
        values.clear();
        valueToIndex.clear();
        values.add("");
        valueToIndex.put("", 0);
    }

    /**
     * Reads a single data block, including all child data blocks, from a stream
     *
     * @param stream Stream to read from
     * @return Decoded Data Block, or null if the end of a list of data blocks was reached
     * @throws IOException
     */
    public OfflineDataBlock readDataBlock(DataInputStream stream) throws IOException {
        String name = readString(stream);
        if (name.isEmpty()) {
            return null; // End of data blocks
        } else {
            byte[] data = Util.readByteArray(stream);
            OfflineDataBlock dataBlock = new OfflineDataBlock(dataBlockBuilder, name, data);
            OfflineDataBlock child;
            while ((child = readDataBlock(stream)) != null) {
                dataBlock.addChild(child);
            }
            return dataBlock;
        }
    }

    /**
     * Writes a single data block, including all child data blocks, to a stream
     *
     * @param stream Stream to write to
     * @param dataBlock OfflineDataBlock to write
     * @throws IOException
     */
    public void writeDataBlock(DataOutputStream stream, OfflineDataBlock dataBlock) throws IOException {
        writeString(stream, dataBlock.name);
        Util.writeByteArray(stream, dataBlock.data);
        for (OfflineDataBlock child : dataBlock.children) {
            writeDataBlock(stream, child);
        }
        writeEmptyString(stream);
    }

    /**
     * Writes out a string to the stream. If the string was previously written,
     * just the identifier of that string is written. Otherwise, the string
     * itself is written too.
     *
     * @param stream Stream to write to
     * @param value String to write
     */
    public void writeString(DataOutputStream stream, String value) throws IOException {
        Integer index = valueToIndex.get(value);
        if (index == null) {
            index = values.size();
            values.add(value);
            valueToIndex.put(value, index);
            Util.writeVariableLengthInt(stream, index);
            stream.writeUTF(value);
        } else {
            Util.writeVariableLengthInt(stream, index);
        }
    }

    /**
     * Writes an empty string to the stream
     *
     * @param stream Stream to write to
     * @throws IOException
     */
    public void writeEmptyString(DataOutputStream stream) throws IOException {
        Util.writeVariableLengthInt(stream, 0);
    }

    /**
     * Reads a string from the stream. First reads an identifier, and if this identifier
     * was already known, returns the string from cache. Otherwise, reads the string value
     * too.
     *
     * @param stream Stream to read from
     * @return Read string
     * @throws IOException
     */
    public String readString(DataInputStream stream) throws IOException {
        int index = Util.readVariableLengthInt(stream);
        if (index == values.size()) {
            String value = stream.readUTF();
            values.add(value);
            valueToIndex.put(value, index);
            return value;
        } else if (index < 0 || index > values.size()) {
            throw new IOException("String index out of range: " + index);
        } else {
            return values.get(index);
        }
    }
}
