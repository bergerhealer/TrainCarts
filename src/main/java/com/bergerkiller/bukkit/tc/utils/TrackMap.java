package com.bergerkiller.bukkit.tc.utils;

import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.rails.type.RailType;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import java.util.ArrayList;

public class TrackMap extends ArrayList<Block> {
    private static final long serialVersionUID = 1L;
    private final TrackIterator iterator;

    public TrackMap(Block start, BlockFace direction) {
        this(new TrackIterator(start, direction));
    }

    public TrackMap(Block start, BlockFace direction, final int maxdistance) {
        this(new TrackIterator(start, direction, maxdistance, false));
    }

    public TrackMap(final TrackIterator iterator) {
        this.iterator = iterator;
    }

    public TrackMap generate(int size) {
        while (this.getDistance() < size && this.hasNext()) {
            this.next();
        }
        return this;
    }

    public TrackMap generate(int size, double stepsize) {
        return this.generate((int) (stepsize * size));
    }

    public boolean find(Block rail, int maxstepcount) {
        Block next;
        for (; maxstepcount > 0; --maxstepcount) {
            next = this.next();
            if (next == null) return false;
            if (BlockUtil.equals(rail, next)) return true;
        }
        return false;
    }

    public Block last() {
        return last(0);
    }

    public Block last(int index) {
        index = size() - index - 1;
        if (index < 0) return null;
        return get(index);
    }

    public int getDistance() {
        return this.iterator.getDistance();
    }

    public Block getBlock() {
        return this.iterator.current();
    }

    public RailPiece getRailPiece() {
        return this.iterator.currentRailPiece();
    }

    public RailType getRailType() {
        return this.iterator.currentRailType();
    }

    public Vector getDirection() {
        return this.iterator.currentDirection();
    }

    public TrackIterator getTrackIterator() {
        return this.iterator;
    }

    public boolean hasNext() {
        return this.iterator.hasNext();
    }

    public Block next() {
        Block next = this.iterator.next();
        if (next != null) this.add(next);
        return next;
    }

}