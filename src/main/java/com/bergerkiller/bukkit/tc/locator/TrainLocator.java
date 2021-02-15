package com.bergerkiller.bukkit.tc.locator;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

/**
 * Displays line particles between a player and one or more trains
 * to locate them.
 */
public class TrainLocator {
    private final Map<Key, TrainLocatorEntry> locators = new HashMap<>();
    private Task updateTask;

    public void enable(TrainCarts plugin) {
        this.updateTask = new Task(plugin) {
            @Override
            public void run() {
                int currentTime = CommonUtil.getServerTicks();
                Iterator<TrainLocatorEntry> iter = locators.values().iterator();
                while (iter.hasNext()) {
                    TrainLocatorEntry locator = iter.next();
                    if (!canLocate(locator.player, locator.member)
                            || currentTime > locator.timeoutTickTime
                    ) {
                        locator.despawn();
                        iter.remove();
                    } else {
                        locator.update();
                    }
                }

                if (locators.isEmpty()) {
                    stop();
                }
            }
        };
    }

    public void disable() {
        Task.stop(updateTask);
        this.updateTask = null;
    }

    /**
     * Gets whether a player can locate a certain member. This is only
     * possible if the player is online, the member is not unloaded,
     * and the player and member are on the same world.
     *
     * @param player Player to check
     * @param member Minecart Member to check
     * @return True if locating the member is possible, False if not
     */
    public boolean canLocate(Player player, MinecartMember<?> member) {
        return player.isOnline()
                && !member.isUnloaded()
                && player.getWorld() == member.getWorld();
    }

    /**
     * Gets whether a member of a group is currently being located by the player
     *
     * @param player Player to check
     * @param group Group of members to check
     * @return True if the player is locating a member of the group, False otherwise
     */
    public boolean isLocating(Player player, MinecartGroup group) {
        for (MinecartMember<?> member : group) {
            if (isLocating(player, member)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets whether a member is currently being located by the player
     *
     * @param player Player to check
     * @param member Minecart Member to check
     * @return True if the player is locating the member, False otherwise
     */
    public boolean isLocating(Player player, MinecartMember<?> member) {
        return locators.containsKey(new Key(player, member));
    }

    /**
     * Starts locating all the minecart members of a group for a player
     *
     * @param player Player that will be locating the members of the group
     * @param group Group whose members to locate
     * @return True if the group is now being located by the player,
     *         False if this isn't possible
     * @see #canLocate(Player, MinecartMember)
     */
    public boolean start(Player player, MinecartGroup group) {
        return start(player, group, -1);
    }

    /**
     * Starts locating all the minecart members of a group for a player
     *
     * @param player Player that will be locating the members of the group
     * @param group Group whose members to locate
     * @param timeoutTicks Amount of ticks after which the locator
     *                     automatically disappears.
     * @return True if the group is now being located by the player,
     *         False if this isn't possible
     * @see #canLocate(Player, MinecartMember)
     */
    public boolean start(Player player, MinecartGroup group, int timeoutTicks) {
        boolean started = false;
        for (MinecartMember<?> member : group) {
            started |= start(player, member, timeoutTicks);
        }
        return started;
    }

    /**
     * Starts locating a minecart member for a player
     *
     * @param player Player that will be locating the member
     * @param member Member to be located
     * @return True if the member is now being located by the player,
     *         False if this isn't possible
     * @see #canLocate(Player, MinecartMember)
     */
    public boolean start(Player player, MinecartMember<?> member) {
        return start(player, member, -1);
    }

    /**
     * Starts locating a minecart member for a player
     *
     * @param player Player that will be locating the member
     * @param member Member to be located
     * @param timeoutTicks Amount of ticks after which the locator
     *                     automatically disappears.
     * @return True if the member is now being located by the player,
     *         False if this isn't possible
     * @see #canLocate(Player, MinecartMember)
     */
    public boolean start(Player player, MinecartMember<?> member, int timeoutTicks) {
        // Check first
        if (!canLocate(player, member)) {
            return false;
        }

        // Start task the first time a locator is created
        if (locators.isEmpty()) {
            updateTask.start(1, 1); 
        }

        TrainLocatorEntry locator = locators.computeIfAbsent(new Key(player, member),
                k -> TrainLocatorEntry.create(k.player, k.member));
        locator.timeoutTickTime = (timeoutTicks >= 0)
                ? (CommonUtil.getServerTicks() + timeoutTicks) : Integer.MAX_VALUE;
        return true;
    }

    /**
     * Stops locating all members for a single player
     *
     * @param player The player that wants to stop locating members
     * @return True if one or more locators were stopped, False if the player
     *         was not locating any minecarts
     */
    public boolean stopAll(Player player) {
        boolean found = false;
        Iterator<TrainLocatorEntry> iter = locators.values().iterator();
        while (iter.hasNext()) {
            TrainLocatorEntry locator = iter.next();
            if (locator.player == player) {
                iter.remove();
                locator.despawn();
                found = true;
            }
        }
        if (locators.isEmpty()) {
            updateTask.stop();
        }
        return found;
    }

    /**
     * Stops locating the minecart members of a group for a player
     *
     * @param player Player that will no longer be locating a member
     * @param group Group of members the player should no longer locate
     * @return True if locating was stopped, False if no locating was happening
     */
    public boolean stop(Player player, MinecartGroup group) {
        boolean stopped = false;
        for (MinecartMember<?> member : group) {
            stopped |= stop(player, member);
        }
        return stopped;
    }

    /**
     * Stops locating a minecart member for a player
     *
     * @param player Player that will no longer be locating a member
     * @param member Member the player should no longer locate
     * @return True if locating was stopped, False if no locating was happening
     */
    public boolean stop(Player player, MinecartMember<?> member) {
        TrainLocatorEntry locator = locators.remove(new Key(player, member));
        if (locator != null) {
            locator.despawn();
            if (locators.isEmpty()) {
                updateTask.stop();
            }
            return true;
        } else {
            return false;
        }
    }

    private static final class Key {
        private final Player player;
        private final MinecartMember<?> member;

        public Key(Player player, MinecartMember<?> member) {
            this.player = player;
            this.member = member;
        }

        @Override
        public int hashCode() {
            return 31 * player.hashCode() + member.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            Key k = (Key) o;
            return this.player == k.player && this.member == k.member;
        }
    }
}
