package com.bergerkiller.bukkit.tc.pathfinding;

import com.bergerkiller.bukkit.common.utils.FaceUtil;
import org.bukkit.block.BlockFace;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class PathConnection {
    public final int distance;
    public final String junctionName;
    public final PathNode destination;

    public PathConnection(PathNode destination, DataInputStream stream) throws IOException {
        this.destination = destination;
        this.distance = stream.readInt();
        
        byte n = stream.readByte();
        if (n == (byte) 0xFF) {
            this.junctionName = stream.readUTF();
        } else {
            // BlockFace was stored - perform a poor conversion from face -> n/e/s/w
            // This will only work for normal tracks and is more or less 'legacy' logic.
            BlockFace f = FaceUtil.notchToFace((int) n << 1);
            switch (f) {
            case NORTH:
                this.junctionName = "n"; break;
            case EAST:
                this.junctionName = "e"; break;
            case SOUTH:
                this.junctionName = "s"; break;
            case WEST:
                this.junctionName = "w"; break;
            case UP:
                this.junctionName = "u"; break;
            case DOWN:
                this.junctionName = "d"; break;
            default:
                this.junctionName = "n"; break;
            }
        }
    }

    public PathConnection(PathNode destination, int distance, String junctionName) {
        this.destination = destination;
        this.distance = distance;
        this.junctionName = junctionName;
    }

    @Override
    public String toString() {
        return "to " + destination.toString() + " going " + this.junctionName + " distance " + this.distance;
    }

    public void writeTo(DataOutputStream stream) throws IOException {
        stream.writeInt(this.destination.index);
        stream.writeInt(this.distance);
        stream.writeByte(0xFF);
        stream.writeUTF(this.junctionName);
    }
}
