package com.bergerkiller.bukkit.tc.utils;

import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

/*
 * A wrapper around the track iterator, which walks along the tracks
 * The walk step distance can be set
 */
@Deprecated
public class TrackWalkIterator {
    private final TrackWalkingPoint walker;
    private double stepsize = TCConfig.cartDistanceGap + 1.0;
    private Location current, next;

    /**
     * Constructs a new walking iterator
     *
     * @param startRail (NOT position block)
     * @param direction to start walking into
     */
    public TrackWalkIterator(Block startRail, BlockFace direction) {
        RailType startRailType = RailType.getType(startRail);
        Block startBlock = startRailType.findMinecartPos(startRail);
        Location startLoc = startBlock == null ? null : startRailType.getSpawnLocation(startRail, direction);
        this.walker = new TrackWalkingPoint(startRail, direction, startLoc);
        this.current = this.next = walker.position == null ? null : walker.position.clone();
    }

    /**
     * Constructs a new walking iterator
     *
     * @param start     position of the Minecart (NOT the rails!)
     * @param direction to start walking into
     */
    public TrackWalkIterator(Location start, BlockFace direction) {
        RailInfo railInfo = RailType.findRailInfo(start.getBlock());
        Block railsBlock = (railInfo == null) ? null : railInfo.railBlock;
        this.walker = new TrackWalkingPoint(railsBlock, direction, start);
        this.current = this.next = this.walker.position.clone();
    }

    public TrackWalkIterator setStep(double size) {
        this.stepsize = size;
        return this;
    }

    private void genNext() {
        this.next = walker.move(stepsize) ? walker.position.clone() : null;
    }

    public boolean hasNext() {
        return this.next != null;
    }

    public Block getRailsBlock() {
        return walker.currentTrack;
    }

    public Location current() {
        return this.current;
    }

    public Location peekNext() {
        return this.next;
    }

    public Location next() {
        this.current = this.next;
        this.genNext();
        return this.current;
    }
}
