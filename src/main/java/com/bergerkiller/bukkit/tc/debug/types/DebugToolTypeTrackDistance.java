package com.bergerkiller.bukkit.tc.debug.types;

import java.text.NumberFormat;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.debug.DebugTool;
import com.bergerkiller.bukkit.tc.debug.DebugToolUtil;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.utils.TrackWalkingPoint;

public class DebugToolTypeTrackDistance extends DebugToolTrackWalkerType {

    @Override
    public String getIdentifier() {
        return "TrackDistance";
    }

    @Override
    public String getTitle() {
        return "Track distance calculator";
    }

    @Override
    public String getDescription() {
        return "Calculates and displays the track distance between two points";
    }

    @Override
    public String getInstructions() {
        return "Left-click on one point and right-click another to display the track distance between the two points";
    }

    @Override
    public boolean handlesLeftClick() {
        return true;
    }

    @Override
    public void onBlockInteract(TrainCarts plugin, Player player, TrackWalkingPoint walker, ItemStack item, boolean isRightClick) {
        // Update pos1/pos2 in the item's metadata
        item = ItemUtil.cloneItem(item);
        RailState start, goal;
        if (isRightClick) {
            saveRailState(item, "pos2", walker.state);
            start = loadRailState(player, item, "pos1");
            goal = walker.state;
        } else {
            saveRailState(item, "pos1", walker.state);
            start = walker.state;
            goal = loadRailState(player, item, "pos2");
        }
        DebugTool.updateToolItem(player, item);
        if (start == null || goal == null || player.isSneaking()) {
            DebugToolUtil.showParticle(walker.state.positionLocation());
            if (isRightClick) {
                player.sendMessage(ChatColor.YELLOW + "End" + ChatColor.GREEN + " position set");
            } else {
                player.sendMessage(ChatColor.YELLOW + "Start" + ChatColor.GREEN + " position set");
            }
            return;
        }

        // Check world is the same
        if (start.railWorld() != goal.railWorld()) {
            player.sendMessage(ChatColor.RED + "The two positions are on different worlds!");
            return;
        }

        // Distance too large
        double distance = start.position().distance(goal.position());
        if (distance > 2000.0) {
            player.sendMessage(ChatColor.RED + "Distance between the two positions is too large!");
            return;
        }

        // Measure distance between pos1 and pos2
        double totalDistance;
        {
            TrackWalkingPoint measure = new TrackWalkingPoint(start);
            final double PARTICLE_STEP = 0.5;
            double remaining;
            int cycleCtr = 10000;
            double bestRemaining = Double.MAX_VALUE;
            double bestTotal = 0.0;
            boolean foundGoalRailBlock = false;
            while ((remaining = measure.state.position().distance(goal.position())) > 1e-4) {
                if (!measure.move(Math.min(PARTICLE_STEP, remaining))) {
                    DebugToolUtil.showEndOfTheRail(player, measure, 0.0);
                    return;
                }
                if (measure.movedTotal > 2000.0 || --cycleCtr <= 0) {
                    player.sendMessage(ChatColor.RED + "Distance between the two positions is too large!");
                    return;
                }
                DebugToolUtil.showParticle(measure.state.positionLocation());
                if (measure.state.railPiece().equals(goal.railPiece())) {
                    if (remaining < bestRemaining) {
                        bestRemaining = remaining;
                        bestTotal = measure.movedTotal;
                    }
                    foundGoalRailBlock = true;
                } else if (foundGoalRailBlock) {
                    // Somehow skipped it despite actually reaching this rail block
                    // Just log the closest we've gotten to it
                    measure.movedTotal = bestTotal;
                    break;
                }
            }

            totalDistance = measure.movedTotal;
        }
        NumberFormat df = NumberFormat.getNumberInstance(Locale.ENGLISH);
        df.setGroupingUsed(false);
        df.setMinimumFractionDigits(2);
        if (isRightClick) {
            player.sendMessage(ChatColor.GREEN + "Distance from start to " +
                    ChatColor.YELLOW + "end" + ChatColor.GREEN + " is " +
                    ChatColor.WHITE + df.format(totalDistance) + ChatColor.GREEN + " blocks");
        } else {
            player.sendMessage(ChatColor.GREEN + "Distance from " +
                    ChatColor.YELLOW + "start" + ChatColor.GREEN + " to end is " +
                    ChatColor.WHITE + df.format(totalDistance) + ChatColor.GREEN + " blocks");
        }
    }

    private static void saveRailState(ItemStack item, String prefix, RailState state) {
        state.position().assertAbsolute();

        CommonTagCompound meta = ItemUtil.getMetaTag(item, false).createCompound(prefix);
        meta.putValue("world", state.railWorld().getName());
        meta.putValue("posX", state.position().posX);
        meta.putValue("posY", state.position().posY);
        meta.putValue("posZ", state.position().posZ);
        meta.putValue("motX", state.position().motX);
        meta.putValue("motY", state.position().motY);
        meta.putValue("motZ", state.position().motZ);
    }

    private static RailState loadRailState(Player player, ItemStack item, String prefix) {
        CommonTagCompound meta = ItemUtil.getMetaTag(item, false).get(prefix, CommonTagCompound.class);
        if (meta == null) {
            return null;
        }

        String worldName = meta.getValue("world", "");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            player.sendMessage("Other position is on a world that is not loaded: " + worldName);
            return null;
        }

        RailPath.Position position = new RailPath.Position();
        position.relative = false;
        position.posX = meta.getValue("posX", 0.0);
        position.posY = meta.getValue("posY", 0.0);
        position.posZ = meta.getValue("posZ", 0.0);
        position.motX = meta.getValue("motX", 0.0);
        position.motY = meta.getValue("motY", 0.0);
        position.motZ = meta.getValue("motZ", 0.0);

        RailState state = new RailState();
        state.setRailPiece(RailPiece.createWorldPlaceholder(world));
        state.setPosition(position);
        if (!RailType.loadRailInformation(state)) {
            player.sendMessage("Rails at the other position doesn't exist anymore!");
            return null;
        }

        return state;
    }
}
