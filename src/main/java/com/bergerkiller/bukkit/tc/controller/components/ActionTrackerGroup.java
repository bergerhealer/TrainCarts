package com.bergerkiller.bukkit.tc.controller.components;

import com.bergerkiller.bukkit.tc.actions.*;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

public class ActionTrackerGroup extends ActionTracker {
    private final MinecartGroup owner;

    public ActionTrackerGroup(MinecartGroup owner) {
        this.owner = owner;
    }

    /**
     * Gets the owner of this Block Tracker
     *
     * @return the Owner
     */
    @Override
    public MinecartGroup getOwner() {
        return owner;
    }

    @Override
    public MinecartGroup getGroupOwner() {
        return owner;
    }

    @Override
    public void doTick() {
        super.doTick();
        for (MinecartMember<?> member : this.owner) {
            member.getActions().doTick();
        }
    }

    /**
     * Clears all set actions ( {@link #clear()} ) while preserving launch speed of existing actions.
     * This is a workaround to allow multiple activated actions in a single tick to function.
     */
    public void launchReset() {
        Action action = this.getCurrentAction();
        if (action instanceof MemberActionLaunch && action.elapsedTicks() == 0) {
            MemberActionLaunch launchAction = (MemberActionLaunch) action;
            this.getOwner().setForwardForce(launchAction.getTargetVelocity());
        } else if (action instanceof MemberActionWaitOccupied) {
            // This action 'reads' the current speed and re-applied it after waiting is done
            // When clearing this speed would normally be gone. So try to restore it.
            MemberActionWaitOccupied waitOccupied = (MemberActionWaitOccupied) action;
            if (!Double.isNaN(waitOccupied.getPostWaitLaunchForce())) {
                this.getOwner().setForwardForce(waitOccupied.getPostWaitLaunchForce());
            }
        }
        this.clear();
    }

    @Override
    public void clear() {
        super.clear();
        for (MinecartMember<?> member : owner) {
            member.getActions().clear();
        }
    }

    @Override
    public <T extends Action> T addAction(T action) {
        if (action instanceof GroupAction) {
            ((GroupAction) action).setGroup(owner);
        } else if (action instanceof MemberAction && ((MemberAction) action).getMember() == null) {
            throw new RuntimeException("Can not add member action without a member set beforehand!");
        }
        return super.addAction(action);
    }

    public GroupActionWaitDelay addActionWait(long delay) {
        return this.addAction(new GroupActionWaitDelay(delay));
    }

    public GroupActionWaitTill addActionWaitTill(long time) {
        return this.addAction(new GroupActionWaitTill(time));
    }

    public GroupActionWaitTicks addActionWaitTicks(int ticks) {
        return this.addAction(new GroupActionWaitTicks(ticks));
    }

    public GroupActionWaitForever addActionWaitForever() {
        return this.addAction(new GroupActionWaitForever());
    }

    public GroupActionWaitState addActionWaitState() {
        return this.addAction(new GroupActionWaitState());
    }

    public GroupActionSizzle addActionSizzle() {
        return this.addAction(new GroupActionSizzle());
    }

    public GroupActionRefill addActionRefill() {
        return this.addAction(new GroupActionRefill());
    }
}
