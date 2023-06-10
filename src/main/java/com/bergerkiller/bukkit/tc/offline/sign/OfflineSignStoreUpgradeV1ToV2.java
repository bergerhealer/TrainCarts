package com.bergerkiller.bukkit.tc.offline.sign;

import com.bergerkiller.bukkit.common.offline.OfflineBlock;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Transforms the input data from v1 format to v2 format.
 * Difference is that v2 format now stores, per sign, whether it stores it
 * for the front or back side of the sign. Assumes all previous sign
 * metadata was for the front side only.
 */
class OfflineSignStoreUpgradeV1ToV2 {

    public static DataInputStream upgrade(DataInputStream stream) throws IOException {
        ByteArrayOutputStream outByteStream = new ByteArrayOutputStream();
        OfflineSignStore.writeVariableLengthInt(outByteStream, 2); // V2
        while (stream.available() > 0) {
            // Read metadata bytes
            byte[] encodedData = new byte[OfflineSignStore.readVariableLengthInt(stream)];
            stream.readFully(encodedData);

            // Decode just the legacy OfflineSign data and metadata that is put after
            OfflineBlock signBlock;
            String[] signLines;
            byte[] metadataContents;
            try (ByteArrayInputStream m_b_stream = new ByteArrayInputStream(encodedData);
                 InflaterInputStream m_d_stream = new InflaterInputStream(m_b_stream);
                 DataInputStream m_stream = new DataInputStream(m_d_stream))
            {
                signBlock = OfflineBlock.readFrom(m_stream);
                signLines = new String[4];
                for (int n = 0; n < 4; n++) {
                    signLines[n] = m_stream.readUTF();
                }

                // All remaining byte data is metadata that is unchanged, save it to a byte array
                ByteArrayOutputStream data = new ByteArrayOutputStream();
                int b;
                while((b = m_stream.read()) != -1) {
                    data.write(b);
                }
                metadataContents = data.toByteArray();
            }

            // Encode into the v2 format, leave metadata untouched
            byte[] upgradedData = encodeMetadata(signBlock, signLines, metadataContents);

            // Write the v2 format to the output stream
            OfflineSignStore.writeVariableLengthInt(outByteStream, upgradedData.length);
            outByteStream.write(upgradedData);
        }

        return new DataInputStream(new ByteArrayInputStream(outByteStream.toByteArray()));
    }

    private static byte[] encodeMetadata(OfflineBlock signBlock, String[] signLines, byte[] metadata) throws IOException {
        try (ByteArrayOutputStream b_stream = new ByteArrayOutputStream()) {
            try (DeflaterOutputStream d_stream = new DeflaterOutputStream(b_stream);
                 DataOutputStream stream = new DataOutputStream(d_stream)
            ) {
                // Prefix with the sign information itself - is required for later decoding
                OfflineBlock.writeTo(stream, signBlock);
                stream.writeBoolean(true); // Legacy always front text
                for (String line : signLines) {
                    stream.writeUTF(line);
                }

                // Write byte blob of other data
                stream.write(metadata);
            }

            return b_stream.toByteArray();
        }
    }
}
