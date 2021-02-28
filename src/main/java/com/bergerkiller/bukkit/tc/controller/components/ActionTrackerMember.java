package com.bergerkiller.bukkit.tc.controller.components;

import com.bergerkiller.bukkit.tc.actions.*;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.utils.LauncherConfig;

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
        if (!owner.isUnloaded()) {
            owner.getGroup().getActions().removeActions(owner);
        }
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
        MemberActionLaunch action = new MemberActionLaunch();
        action.initDistance(distance, targetvelocity);
        return addGroupAction(action);
    }

    public MemberActionLaunch addActionTimedLaunch(int timeTicks, double targetvelocity) {
        MemberActionLaunch action = new MemberActionLaunch();
        action.initTime(timeTicks, targetvelocity);
        return addGroupAction(action);
    }

    public MemberActionLaunch addActionLaunch(LauncherConfig config, double targetvelocity) {
        MemberActionLaunch action = new MemberActionLaunch();
        action.init(config, targetvelocity);
        return addGroupAction(action);
    }

    public MemberActionLaunchDirection addActionLaunch(final BlockFace direction, double targetdistance, double targetvelocity) {
        MemberActionLaunchDirection action = new MemberActionLaunchDirection();
        action.initDistance(targetdistance, targetvelocity, direction);
        return addGroupAction(action);
    }

    public MemberActionLaunchDirection addActionTimedLaunch(final BlockFace direction, int timeTicks, double targetvelocity) {
        MemberActionLaunchDirection action = new MemberActionLaunchDirection();
        action.initTime(timeTicks, targetvelocity, direction);
        return addGroupAction(action);
    }

    public MemberActionLaunchDirection addActionLaunch(final BlockFace direction, LauncherConfig config, double targetvelocity) {
        MemberActionLaunchDirection action = new MemberActionLaunchDirection();
        action.init(config, targetvelocity, direction);
        return addGroupAction(action);
    }

    public MemberActionLaunchDirection addActionLaunch(final BlockFace direction, LauncherConfig config, double targetvelocity, double targetspeedlimit) {
        MemberActionLaunchDirection action = new MemberActionLaunchDirection();
        action.init(config, targetvelocity, targetspeedlimit, direction);
        return addGroupAction(action);
    }

    public MemberActionLaunchLocation addActionLaunch(Location destination, double targetvelocity) {
        return addGroupAction(new MemberActionLaunchLocation(targetvelocity, destination));
    }

    public MemberActionLaunchLocation addActionLaunch(Vector offset, double targetvelocity) {
        return addActionLaunch(owner.getEntity().getLocation().add(offset), targetvelocity);
    }

    public MemberActionWaitOccupied addActionWaitOccupied(double maxDistance, long launchDelay, double launchDistance) {
        return addActionWaitOccupied(maxDistance, launchDelay, launchDistance, null, null);
    }

    public MemberActionWaitOccupied addActionWaitOccupied(double maxDistance, long launchDelay, double launchDistance, BlockFace launchDirection, Double launchVelocity) { // Use Double to allow null
        return addGroupAction(new MemberActionWaitOccupied(maxDistance, launchDelay, launchDistance, launchDirection, launchVelocity));
    }
}
