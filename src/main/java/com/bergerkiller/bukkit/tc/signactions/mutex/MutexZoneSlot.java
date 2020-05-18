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
        return this.name == null;
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
                .sorted((z0, z1) -> z0.sign.compareTo(z1.sign))
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
            this.setLevers(false);
            return;
        }
    }

    public boolean tryEnter(MinecartGroup group) {
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
                    return true;
                }
            }
        }

        // Check not occupied by someone else
        if (this.currentGroup != null && this.currentGroup != group) {
            this.refresh(true);
            if (this.currentGroup != null) {
                return false;
            }
        }

        // Occupy it.
        this.currentGroup = group;
        this.currentGroupTime = CommonUtil.getServerTicks();
        this.setLevers(true);
        return true;
    }

    private void setLevers(boolean down) {
        for (MutexZone zone : this.zones) {
            zone.setLevers(down);
        }
    }

    private boolean containsBlock(Block block) {
        for (MutexZone zone : this.zones) {
            if (zone.containsBlock(block)) {
                return true;
            }
        }
        return false;
    }
}
