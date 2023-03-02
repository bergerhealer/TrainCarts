package com.bergerkiller.bukkit.tc.rails.direction;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Locale;

import com.bergerkiller.bukkit.tc.Util;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.tc.controller.components.RailState;

/**
 * A type of {@link RailEnterDirection} that refers to activation moving towards a certain
 * block face of the block that activated the sign. This is the opposite face that was
 * hit/entered by the train.
 */
public final class RailEnterDirectionToFace implements RailEnterDirection {
    private static final EnumMap<BlockFace, RailEnterDirection> byFace = new EnumMap<>(BlockFace.class);
    private static final EnumMap<BlockFace, RailEnterDirection[]> arrByFace = new EnumMap<>(BlockFace.class);
    private final BlockFace face;
    private final String name;

    static {
        for (BlockFace face : FaceUtil.BLOCK_SIDES) {
            RailEnterDirectionToFace re = new RailEnterDirectionToFace(face);
            byFace.put(face, re);
            arrByFace.put(face, new RailEnterDirection[] { re });
        }

        // Add sub-cardinal faces to arrByFace
        for (BlockFace face : BlockFace.values()) {
            if (face.getModX() != 0 && face.getModZ() != 0) {
                ArrayList<RailEnterDirection> values = new ArrayList<>(2);
                for (BlockFace blockFace : FaceUtil.getFaces(Util.snapFace(face))) {
                    values.add(fromFace(blockFace));
                }
                arrByFace.put(face, values.toArray(new RailEnterDirection[values.size()]));
            }
        }
    }

    static RailEnterDirection fromFace(BlockFace face) {
        return byFace.computeIfAbsent(face, f -> {
            throw new IllegalArgumentException("Invalid block face: " + f);
        });
    }

    // Optimization used for by-name lookup
    static RailEnterDirection[] arrayFromFace(BlockFace face) {
        return arrByFace.computeIfAbsent(face, f -> {
            throw new IllegalArgumentException("Invalid block face: " + f);
        });
    }

    private RailEnterDirectionToFace(BlockFace face) {
        this.face = face;
        this.name = face.name().toLowerCase(Locale.ENGLISH).substring(0, 1);
    }

    /**
     * Gets the BlockFace from which a train should enter
     *
     * @return face
     */
    public BlockFace getFace() {
        return face;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public double motionDot(Vector motion) {
        return motion.dot(FaceUtil.faceToVector(face));
    }

    @Override
    public boolean match(RailState state) {
        return face == state.enterFace();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o instanceof RailEnterDirectionToFace) {
            return face.equals(((RailEnterDirectionToFace) o).getFace());
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return "EnterFrom{face=" + face.name().toLowerCase(Locale.ENGLISH) + "}";
    }
}
