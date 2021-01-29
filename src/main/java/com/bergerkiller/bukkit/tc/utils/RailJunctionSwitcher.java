package com.bergerkiller.bukkit.tc.utils;

import java.util.List;
import java.util.stream.Collectors;

import com.bergerkiller.bukkit.tc.cache.RailMemberCache;
import com.bergerkiller.bukkit.tc.controller.MinecartGroupStore;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.RailJunction;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.controller.components.RailTracker.TrackedRail;

/**
 * Handles the logic of switching rails from one junction position
 * to another. Not only switches the junction, but also moves carts
 * currently on the junction to the new junction.
 */
public class RailJunctionSwitcher {
    private final RailPiece rail;

    public RailJunctionSwitcher(RailPiece rail) {
        this.rail = rail;
    }

    /**
     * Switches the rails from one junction to another. Junctions are used from
     * {@link com.bergerkiller.bukkit.tc.rails.type.RailType#getJunctions(railBlock)}.
     * In addition to switching track, also moves carts currently on this rail block
     * along to the new junction, if possible.
     * 
     * @param railBlock where this Rail Type is at
     * @param from junction
     * @param to junction
     */
    public void switchJunction(RailJunction from, RailJunction to) {
        // Before switching, collect the 'position on rails' information for
        // all carts currently on this rail block. We need to know the path used,
        // what end of the path the train entered, and the distance traveled from
        // that end.
        List<MemberOnRail> members = RailMemberCache.findAll(this.rail.block()).stream()
            .map(m -> m.getRailTracker().getRail())
            .filter(rail -> rail.state.railPiece().equals(this.rail))
            .map(MemberOnRail::new)
            .collect(Collectors.toList());

        // Switch the rails, permanently altering the logic
        // Also notify a physics change, so trains recalculate things
        MinecartGroupStore.notifyPhysicsChange();
        this.rail.type().switchJunction(this.rail.block(), from, to);

        // Move all minecarts that are currently on this rail to the new junction path.
        // This uses the previously calculated distance traveled on the previous junction.
        for (MemberOnRail member : members) {
            RailPath path = member.state.loadRailLogic().getPath();
            path.move(member.state, member.distanceTraveled);
            member.member.snapToPosition(member.state.position());
        }
    }

    private static class MemberOnRail {
        public final MinecartMember<?> member;
        public final RailState state;
        public final double distanceTraveled;

        public MemberOnRail(TrackedRail rail) {
            this.member = rail.member;

            this.state = rail.state.cloneAndInvertMotion();
            this.distanceTraveled = rail.getPath().move(this.state, Double.MAX_VALUE);
            this.state.position().invertMotion();
        }
    }
}
