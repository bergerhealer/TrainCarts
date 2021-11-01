package com.bergerkiller.bukkit.tc.offline.sign;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.tc.offline.world.OfflineBlock;
import com.bergerkiller.bukkit.tc.offline.world.OfflineWorld;

/**
 * A single offline sign and the last-known associated metadata of it.
 * This allows tying custom data to a sign that will be stored persistently,
 * until it is discovered the sign no longer exists.<br>
 * <br>
 * Also stores the lines of text on the sign, which are used to check when
 * the text on a sign is changed between reloads.
 * 
 * @param T - Metadata type stored for this sign
 */
public class OfflineSign {
    private static final String EMPTY_STR = ""; // for memory efficiency
    private final OfflineBlock block;
    private final String[] lines;

    protected OfflineSign(OfflineBlock block, String[] lines) {
        this.block = block;
        this.lines = new String[4];
        for (int i = 0; i < lines.length; i++) {
            this.lines[i] = lines[i].isEmpty() ? EMPTY_STR : lines[i];
        }
    }

    /**
     * Gets the offline world, which stores the world uuid
     * and the loaded world, if loaded.
     *
     * @return offline world
     */
    public OfflineWorld getWorld() {
        return this.block.getWorld();
    }

    /**
     * Gets the offline block where this sign is located
     *
     * @return offline block
     */
    public OfflineBlock getBlock() {
        return this.block;
    }

    /**
     * Gets the unique id of the world this sign is on
     *
     * @return world uuid
     */
    public UUID getWorldUUID() {
        return this.block.getWorldUUID();
    }

    /**
     * Gets the world this sign on, if this world is currently
     * loaded. If not, null is returned.
     *
     * @return World this offline sign is on
     */
    public World getLoadedWorld() {
        return this.block.getLoadedWorld();
    }

    /**
     * Gets the x/y/z block coordinates where the sign is located
     *
     * @return block position
     */
    public IntVector3 getPosition() {
        return this.block.getPosition();
    }

    /**
     * Gets the block this sign is at. If the world this sign is
     * on is currently not loaded, returns null. Use {@link #getPosition()}
     * if the position is all that is needed.
     *
     * @return Block the sign is on
     */
    public Block getLoadedBlock() {
        return this.block.getLoadedBlock();
    }

    /**
     * Gets the 4 lines of this sign that was last known
     *
     * @return last known 4 lines on the sign
     */
    public String[] getLines() {
        return this.lines;
    }

    /**
     * Gets a single line of this sign that was last known
     *
     * @param index Index of the line, should be between 0 and 3
     * @return last known value of this line on the sign
     */
    public String getLine(int index) {
        return this.lines[index];
    }

    /**
     * Verifies the information on this OfflineSign still matches the information
     * on an actual loaded sign
     *
     * @param sign Sign to verify against
     * @return True if verified and still matching
     */
    public boolean verify(Sign sign) {
        for (int n = 0; n < 4; n++) {
            if (!sign.getLine(n).equals(this.lines[n])) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        World world = this.block.getLoadedWorld();
        return String.format("OfflineSign{world=%s, x=%d, y=%d, z=%d, lines=[%s | %s | %s | %s]}",
                (world != null) ? world.getName() : "uuid_" + this.block.getWorldUUID(),
                this.block.getX(), this.block.getY(), this.block.getZ(),
                this.lines[0], this.lines[1], this.lines[2], this.lines[3]);
    }

    /**
     * Reads the details of an OfflineSign from a data input stream
     *
     * @param stream Stream to read from
     * @return Decoded OfflineSign
     * @throws IOException
     */
    public static OfflineSign readFrom(DataInputStream stream) throws IOException {
        OfflineBlock block = OfflineBlock.readFrom(stream);
        String[] lines = new String[4];
        for (int n = 0; n < 4; n++) {
            lines[n] = stream.readUTF();
        }
        return new OfflineSign(block, lines);
    }

    /**
     * Writes the details of an OfflineSign to a data output stream
     *
     * @param stream Stream to write to
     * @param sign Sign to write
     * @throws IOException
     */
    public static void writeTo(DataOutputStream stream, OfflineSign sign) throws IOException {
        OfflineBlock.writeTo(stream, sign.getBlock());
        for (String line : sign.getLines()) {
            stream.writeUTF(line);
        }
    }
}
