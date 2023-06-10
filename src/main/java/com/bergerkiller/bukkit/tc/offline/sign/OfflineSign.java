package com.bergerkiller.bukkit.tc.offline.sign;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

import com.bergerkiller.generated.org.bukkit.block.SignHandle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.offline.OfflineBlock;
import com.bergerkiller.bukkit.common.offline.OfflineWorld;

/**
 * A single offline sign, stores the block coordinates and the lines of text on it.
 * This information can be used to later verify and detect sign changes.
 */
public class OfflineSign {
    private static final String EMPTY_STR = ""; // for memory efficiency
    private final OfflineSignSide side;
    private final String[] lines;

    protected OfflineSign(OfflineBlock block, boolean front, String[] lines) {
        this.side = OfflineSignSide.of(block, front);
        this.lines = new String[4];
        for (int i = 0; i < lines.length; i++) {
            this.lines[i] = lines[i].isEmpty() ? EMPTY_STR : lines[i];
        }
    }

    /**
     * Gets the Sign OfflineBlock and Side of the text. Can be used as a Key in
     * HashMaps.
     *
     * @return OfflineSignSide
     */
    public OfflineSignSide getSide() {
        return side;
    }

    /**
     * Gets the offline world, which stores the world uuid
     * and the loaded world, if loaded.
     *
     * @return offline world
     */
    public OfflineWorld getWorld() {
        return this.side.getWorld();
    }

    /**
     * Gets the offline block where this sign is located
     *
     * @return offline block
     */
    public OfflineBlock getBlock() {
        return this.side.getBlock();
    }

    /**
     * Gets whether this OfflineSign represents the front text of the sign (true) or the
     * back side (false, &gt;= MC 1.20)
     *
     * @return True if this OfflineSign represents the front sign side text
     */
    public boolean isFrontText() {
        return this.side.isFrontText();
    }

    /**
     * Gets the unique id of the world this sign is on
     *
     * @return world uuid
     */
    public UUID getWorldUUID() {
        return this.side.getWorldUUID();
    }

    /**
     * Gets the world this sign on, if this world is currently
     * loaded. If not, null is returned.
     *
     * @return World this offline sign is on
     */
    public World getLoadedWorld() {
        return this.side.getLoadedWorld();
    }

    /**
     * Gets the x/y/z block coordinates where the sign is located
     *
     * @return block position
     */
    public IntVector3 getPosition() {
        return this.side.getPosition();
    }

    /**
     * Gets the block this sign is at. If the world this sign is
     * on is currently not loaded, returns null. Use {@link #getPosition()}
     * if the position is all that is needed.
     *
     * @return Block the sign is on
     */
    public Block getLoadedBlock() {
        return this.side.getLoadedBlock();
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
        SignHandle signHandle = SignHandle.createHandle(sign);
        if (side.isFrontText()) {
            for (int n = 0; n < 4; n++) {
                if (!signHandle.getFrontLine(n).equals(this.lines[n])) {
                    return false;
                }
            }
        } else {
            for (int n = 0; n < 4; n++) {
                if (!signHandle.getBackLine(n).equals(this.lines[n])) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public String toString() {
        OfflineBlock block = side.getBlock();
        World world = block.getLoadedWorld();
        return String.format("OfflineSign{world=%s, x=%d, y=%d, z=%d, side=%s, lines=[%s | %s | %s | %s]}",
                (world != null) ? world.getName() : "uuid_" + block.getWorldUUID(),
                block.getX(), block.getY(), block.getZ(),
                (side.isFrontText() ? "front" : "back"),
                this.lines[0], this.lines[1], this.lines[2], this.lines[3]);
    }

    /**
     * Reads the details of an OfflineSign from a data input stream.
     *
     * @param stream Stream to read from
     * @return Decoded OfflineSign
     * @throws IOException
     */
    public static OfflineSign readFrom(DataInputStream stream) throws IOException {
        OfflineBlock block = OfflineBlock.readFrom(stream);
        boolean front = stream.readBoolean();
        String[] lines = new String[4];
        for (int n = 0; n < 4; n++) {
            lines[n] = stream.readUTF();
        }
        return new OfflineSign(block, front, lines);
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
        stream.writeBoolean(sign.isFrontText());
        for (String line : sign.getLines()) {
            stream.writeUTF(line);
        }
    }

    /**
     * Creates a new state copy of a Bukkit sign's information
     *
     * @param sign Bukkit Sign whose lines and block information to copy
     * @param isFrontText Whether to represent the front text (true) or the
     *                    back text (false, &gt;= MC 1.20 only)
     * @return new OfflineSign
     */
    public static OfflineSign fromSign(Sign sign, boolean isFrontText) {
        OfflineBlock signBlock = OfflineWorld.of(sign.getWorld())
                .getBlockAt(sign.getX(), sign.getY(), sign.getZ());
        SignHandle signHandle = SignHandle.createHandle(sign);
        return new OfflineSign(signBlock,
                               isFrontText,
                               (isFrontText ? signHandle.getFrontLines() : signHandle.getBackLines()));
    }
}
