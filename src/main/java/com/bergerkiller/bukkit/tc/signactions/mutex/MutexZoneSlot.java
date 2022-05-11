package com.bergerkiller.bukkit.tc.signactions.mutex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.block.Block;

import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartGroupStore;
import com.bergerkiller.bukkit.tc.controller.components.RailTracker.TrackedRail;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bergerkiller.bukkit.tc.statements.Statement;

/**
 * A slot where a group can be put that was granted access to move through.
 * Named mutex zones can share the same (named) slot
 */
public class MutexZoneSlot {
    private static int TICK_DELAY_CLEAR_AUTOMATIC = 6; // clear delay when no trains are waiting for it
    private static int TICK_DELAY_CLEAR_WAITING = 5; // clear delay when trains are waiting for it
    private final String name;
    private MinecartGroup currentGroup = null;
    private boolean currentGroupHardEnter = false;
    private int currentGroupTime = 0;
    private List<MutexZone> zones;
    private List<String> statements;

    protected MutexZoneSlot(String name) {
        this.name = name;
        this.zones = Collections.emptyList();
        this.statements = Collections.emptyList();
    }

    public String getName() {
        return this.name;
    }

    public boolean isAnonymous() {
        return this.name.isEmpty();
    }

    protected MutexZoneSlot addZone(MutexZone zone) {
        if (this.zones.isEmpty()) {
            this.zones = Collections.singletonList(zone);
        } else {
            this.zones = new ArrayList<MutexZone>(this.zones);
            this.zones.add(zone);
        }
        this.refreshStatements();
        return this;
    }

    public void removeZone(MutexZone zone) {
        if (this.zones.size() == 1 && this.zones.get(0) == zone) {
            this.zones = Collections.emptyList();
            this.statements = Collections.emptyList();
        } else if (this.zones.size() > 1) {
            this.zones.remove(zone);
            this.refreshStatements();
        }
    }

    public boolean hasZones() {
        return !this.zones.isEmpty();
    }

    /**
     * Gets a full list of statement rules for this mutex zone. Each sign
     * can add statements to this list as they join the mutex zone name.
     * 
     * @return statements
     */
    public List<String> getStatements() {
        return this.statements;
    }

    private void refreshStatements() {
        this.statements = this.zones.stream()
                .sorted((z0, z1) -> z0.signBlock.getPosition().compareTo(z1.signBlock.getPosition()))
                .map(z -> z.statement)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Called every tick to refresh mutex zones that have a group inside.
     * If a group leaves a zone, this eventually releases that group again.
     */
    public void refresh(boolean trainWaiting) {
        if (this.currentGroup == null) {
            return;
        }

        if (this.currentGroup.isUnloaded() || !MinecartGroupStore.getGroups().contains(this.currentGroup)) {
            this.currentGroup = null;
            this.currentGroupHardEnter = false;
            this.setLevers(false);
            return;
        }

        int serverTicks = CommonUtil.getServerTicks();
        if ((serverTicks - this.currentGroupTime) >= (trainWaiting ? TICK_DELAY_CLEAR_WAITING : TICK_DELAY_CLEAR_AUTOMATIC)) {
            // Check whether the group is still occupying this mutex zone
            // Do so by iterating all the rails (positiosn!) of that train
            for (TrackedRail rail : this.currentGroup.getRailTracker().getRailInformation()) {
                if (this.containsBlock(rail.minecartBlock)) {
                    this.currentGroupTime = serverTicks;
                    return;
                }
            }

            // It is not. clear it.
            this.currentGroup = null;
            this.currentGroupHardEnter = false;
            this.setLevers(false);
            return;
        }
    }

    public EnterResult tryEnter(MinecartGroup group, boolean hard) {
        // Verify using statements whether the group is even considered
        {
            List<String> statements = this.getStatements();
            if (!statements.isEmpty()) {
                SignActionEvent signEvent = null;
                if (this.zones.size() == 1) {
                    Block signBlock = this.zones.get(0).getSignBlock();
                    if (signBlock != null && MaterialUtil.ISSIGN.get(signBlock)) {
                        signEvent = new SignActionEvent(signBlock, group);
                        signEvent.setAction(SignActionType.GROUP_ENTER);
                    }
                }
                if (!Statement.hasMultiple(group, this.getStatements(), signEvent)) {
                    // Ignored!
                    return EnterResult.IGNORED;
                }
            }
        }

        // If hard and current group is soft, overwrite
        if (hard && !this.currentGroupHardEnter && this.currentGroup != null && this.currentGroup != group) {
            this.currentGroup = null;
        }

        // Check not occupied by someone else
        if (this.currentGroup != null && this.currentGroup != group) {
            this.refresh(true);
            if (this.currentGroup != null) {
                return this.currentGroupHardEnter ? EnterResult.OCCUPIED_HARD : EnterResult.OCCUPIED_SOFT;
            }
        }

        // Occupy it.
        this.currentGroup = group;
        this.currentGroupHardEnter |= hard;
        this.currentGroupTime = CommonUtil.getServerTicks();
        if (hard) {
            this.setLevers(true);
        }
        return EnterResult.SUCCESS;
    }

    private void setLevers(boolean down) {
        for (MutexZone zone : this.zones) {
            zone.setLevers(down);
        }
    }

    private boolean containsBlock(Block block) {
        for (MutexZone zone : this.zones) {
            if (zone.signBlock.getLoadedWorld() == block.getWorld() && zone.containsBlock(block)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the group that currently is inside this zone. If not null, it
     * means no other group can enter it.
     *
     * @return current group that activated this mutex zone
     */
    public MinecartGroup getCurrentGroup() {
        return this.currentGroupHardEnter ? this.currentGroup : null;
    }

    /**
     * Gets the group that is expected to enter this mutex zone very soon
     *
     * @return prospective group
     */
    public MinecartGroup getProspectiveGroup() {
        return this.currentGroup;
    }

    /**
     * The result of trying to enter a mutex
     */
    public static enum EnterResult {
        /** The group does not match conditions to enter/be seen by the mutex zone */
        IGNORED,
        /** The mutex zone was entered */
        SUCCESS,
        /** The mutex zone is occupied (hard, can't ever enter it) */
        OCCUPIED_HARD,
        /** The mutex zone is about to be occupied (soft, can enter but must slow approach) */
        OCCUPIED_SOFT
    }
}
