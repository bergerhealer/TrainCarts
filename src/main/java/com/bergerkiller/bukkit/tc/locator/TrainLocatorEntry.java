package com.bergerkiller.bukkit.tc.locator;

import java.util.Collections;

import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.tc.attachments.particle.VirtualFishingLine;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

/**
 * Displays a line between the player and a minecart member.
 */
class TrainLocatorEntry {
    private static final double MAX_DISTANCE = 50.0;

    public final Player player;
    public final MinecartMember<?> member;
    private final VirtualFishingLine line;
    public int timeoutTickTime;

    private TrainLocatorEntry(Player player, MinecartMember<?> member) {
        this.player = player;
        this.member = member;
        this.line = new VirtualFishingLine(true);
        this.timeoutTickTime = Integer.MAX_VALUE;
    }

    public void spawn() {
        line.spawn(player, null, calcTarget());
    }

    public void update() {
        line.update(Collections.singleton(player), null, calcTarget());
    }

    public void despawn() {
        line.destroy(player);
    }

    private Vector calcTarget() {
        Vector targetPos = member.getEntity().loc.vector();
        Vector diff = targetPos.clone().subtract(player.getLocation().toVector());
        double distance = diff.length();
        if (distance > MAX_DISTANCE) {
            targetPos.subtract(diff.multiply(1.0 - (MAX_DISTANCE / distance)));
        }
        return targetPos;
    }

    /**
     * Creates and spawns a new train locator
     *
     * @param player Player that will be seeing the locator
     * @param member Member at which the locator points
     * @return train locator
     */
    public static TrainLocatorEntry create(Player player, MinecartMember<?> member) {
        TrainLocatorEntry locator = new TrainLocatorEntry(player, member);
        locator.spawn();
        return locator;
    }
}
