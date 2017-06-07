package com.bergerkiller.bukkit.tc.controller.components;

import java.util.ArrayList;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.utils.TrackIterator;

/**
 * Uses a track iterator to keep track of the rails a train is driving on.
 * This information is then used to update minecart rails information,
 * handle the detection of signs, update minecart movement directions and
 * detect splitting of trains
 */
public class RailTrackerGroup extends RailTracker {
    private final MinecartGroup owner;
    private final ArrayList<RailInfo> rails = new ArrayList<RailInfo>();

    public RailTrackerGroup(MinecartGroup owner) {
        this.owner = owner;
    }

    public void refresh() {
        // This assumes the members already know about their own rails.

        for (MinecartMember<?> member : this.owner) {
            member.getRailTracker().refresh(findInfo(member));
        }

        /*
        rails.clear();
        if (owner.size() == 1) {
            // Only a single minecart, there is no need to iterate the tracks
            // All we have to do is update the current block the minecart is on
            RailTracker tracker = owner.get(0).getRailTracker();
            rails.add(new RailInfo(tracker.getBlock(), tracker.getRailType(), BlockFace.SELF));
        } else if (owner.size() > 1) {
            // Iterate the tracks from the minecart from the tail to the front
            // If we fail to find the next minecart in the chain within a limit
            // amount if blocks, assume the train has split at that minecart.

            // First step: attempt to find the direction in order to find the other members of the train
            
            
            TrackIterator iter = new TrackIterator();
            
            for (int i = owner.size() - 1; i >= 0; --i) {
                
                
                
                
                
            }
        }
        */
    }

}
