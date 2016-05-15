package com.bergerkiller.bukkit.tc.controller.components;

import com.bergerkiller.bukkit.tc.actions.*;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

/**
 * An Action tracker meant for a group Member.
 * Some adding methods add the action to the group instead of the member.
 */
public class ActionTrackerMember extends ActionTracker {
    private final MinecartMember<?> owner;

    public ActionTrackerMember(MinecartMember<?> owner) {
        this.owner = owner;
    }

    /**
     * Gets the owner of this Block Tracker
     *
     * @return the Owner
     */
    public MinecartMember<?> getOwner() {
        return owner;
    }

    @Override
    public void clear() {
        super.clear();
        owner.getGroup().getActions().removeActions(owner);
    }

    @Override
    public <T extends Action> T addAction(T action) {
        if (action instanceof MemberAction) {
            ((MemberAction) action).setMember(owner);
        }
        return super.addAction(action);
    }

    /**
     * Adds a Member Action (for this member) to the group action scheduler
     *
     * @param action to add
     * @return the action added
     */
    public <A extends MemberAction> A addGroupAction(A action) {
        action.setMember(owner);
        return owner.getGroup().getActions().addAction(action);
    }

    public MemberActionWaitDistance addActionWaitDistance(double distance) {
        return addGroupAction(new MemberActionWaitDistance(distance));
    }

    public MemberActionWaitLocation addActionWaitLocation(Location location) {
        return addGroupAction(new MemberActionWaitLocation(location));
    }

    public MemberActionWaitLocation addActionWaitLocation(Location location, double radius) {
        return addGroupAction(new MemberActionWaitLocation(location, radius));
    }

    public MemberActionLaunch addActionLaunch(double distance, double targetvelocity) {
        return addGroupAction(new MemberActionLaunch(distance, targetvelocity));
    }

    public MemberActionLaunchLocation addActionLaunch(Location destination, double targetvelocity) {
        return addGroupAction(new MemberActionLaunchLocation(targetvelocity, destination));
    }

    public MemberActionLaunchLocation addActionLaunch(Vector offset, double targetvelocity) {
        return addActionLaunch(owner.getEntity().getLocation().add(offset), targetvelocity);
    }

    public MemberActionLaunchDirection addActionLaunch(final BlockFace direction, double targetdistance, double targetvelocity) {
        return addGroupAction(new MemberActionLaunchDirection(targetdistance, targetvelocity, direction));
    }

    public MemberActionWaitOccupied addActionWaitOccupied(int maxsize, long launchDelay, double launchDistance) {
        return addActionWaitOccupied(maxsize, launchDelay, launchDistance, null, null);
    }

    public MemberActionWaitOccupied addActionWaitOccupied(int maxsize, long launchDelay, double launchDistance, BlockFace launchDirection, Double launchVelocity) { // Use Double to allow null
        return addGroupAction(new MemberActionWaitOccupied(maxsize, launchDelay, launchDistance, launchDirection, launchVelocity));
    }
}
