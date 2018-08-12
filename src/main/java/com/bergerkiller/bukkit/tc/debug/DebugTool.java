package com.bergerkiller.bukkit.tc.debug;

import java.util.List;
import java.util.Random;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.signactions.mutex.MutexZone;
import com.bergerkiller.bukkit.tc.signactions.mutex.MutexZoneCache;
import com.bergerkiller.bukkit.tc.utils.TrackWalkingPoint;

import net.md_5.bungee.api.ChatColor;

/**
 * Manages the different functionalities provided by /train debug [type]
 */
public class DebugTool {

    /**
     * Shows a box-shaped particle display for all mutex zones for a few seconds
     * 
     * @param player
     */
    public static void showMutexZones(final Player player) {
        Location loc = player.getEyeLocation();
        final List<MutexZone> zones = MutexZoneCache.findNearbyZones(
                loc.getWorld().getUID(),
                new IntVector3(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()),
                32);

        if (zones.isEmpty()) {
            return;
        }

        final int PARTICLE_DURATION = 100;
        final int PARTICLE_INTERVAL = 4;
        final double PARTICLE_SPACING = 0.2;
        new Task(TrainCarts.plugin) {
            int life = PARTICLE_DURATION / PARTICLE_INTERVAL;

            @Override
            public void run() {
                Random r = new Random();
                for (MutexZone zone : zones) {
                    r.setSeed(MathUtil.longHashToLong(zone.start.hashCode(), zone.end.hashCode()));
                    java.awt.Color awt_color = java.awt.Color.getHSBColor(r.nextFloat(), 1.0f, 1.0f);
                    Color color = Color.fromRGB(awt_color.getRed(), awt_color.getGreen(), awt_color.getBlue());
                    double x1 = zone.start.x;
                    double y1 = zone.start.y;
                    double z1 = zone.start.z;
                    double x2 = zone.end.x + 1.0;
                    double y2 = zone.end.y + 1.0;
                    double z2 = zone.end.z + 1.0;
                    cube(color, x1, y1, z1, x2, y2, z2);
                }

                if (--life == 0) {
                    this.stop();
                }
            }

            void cube(Color color, double x1, double y1, double z1, double x2, double y2, double z2) {
                face(color, x1, y1, z1, x2, y1, z2);
                face(color, x1, y2, z1, x2, y2, z2);
                line(color, x1, y1, z1, x1, y2, z1);
                line(color, x2, y1, z1, x2, y2, z1);
                line(color, x1, y1, z2, x1, y2, z2);
                line(color, x2, y1, z2, x2, y2, z2);
            }

            void face(Color color, double x1, double y1, double z1, double x2, double y2, double z2) {
                line(color, x1, y1, z1, x2, y1, z1);
                line(color, x1, y1, z1, x1, y2, z1);
                line(color, x1, y1, z1, x1, y1, z2);
                line(color, x1, y2, z2, x2, y2, z2);
                line(color, x2, y1, z2, x2, y2, z2);
                line(color, x2, y2, z1, x2, y2, z2);
            }

            void line(Color color, double x1, double y1, double z1, double x2, double y2, double z2) {
                double dist = MathUtil.distance(x1, y1, z1, x2, y2, z2);
                if (dist < 1e-8) {
                    return;
                }
                int n = MathUtil.ceil(dist / PARTICLE_SPACING);
                double dx = x2 - x1;
                double dy = y2 - y1;
                double dz = z2 - z1;
                Vector position = new Vector();
                for (int i = 0; i < n; i++) {
                    double t = (double) i / (double) (n-1);
                    position.setX(x1 + dx * t);
                    position.setY(y1 + dy * t);
                    position.setZ(z1 + dz * t);
                    PlayerUtil.spawnDustParticles(player, position, color);
                }
            }
        }.start(1, PARTICLE_INTERVAL);
    }

    /**
     * Called when a player interacts with a block using a (stick) debug item
     * 
     * @param player
     * @param clickedBlock
     * @param item
     * @param debugType
     */
    public static void onDebugInteract(Player player, Block clickedBlock, ItemStack item, String debugType) {
        // When clicking air, raytrace to find the first rails block in view and walk from the exact position on the path
        if (clickedBlock == null) {
            Location loc = player.getEyeLocation();
            Vector dir = loc.getDirection();
            RailState result = null;
            RailState state = new RailState();
            state.setRailBlock(loc.getBlock());
            state.position().setMotion(dir);
            state.initEnterDirection();

            double minDist = Double.MAX_VALUE;
            for (double d = 0.0; d <= 200.0; d += 0.01) {
                RailPath.Position p = state.position();
                p.posX = loc.getX() + dir.getX() * d;
                p.posY = loc.getY() + dir.getY() * d;
                p.posZ = loc.getZ() + dir.getZ() * d;
                if (RailType.loadRailInformation(state)) {
                    RailPath path = state.loadRailLogic().getPath();
                    double distSq = path.distanceSquared(state.railPosition());
                    if (distSq < minDist) {
                        minDist = distSq;
                        result = state.clone();
                        path.snap(result.position(), result.railBlock());
                    } else {
                        break;
                    }
                }
            }

            if (result == null) {
                player.sendMessage(ChatColor.RED + "No rails found here");
                return;
            }

            // Go!
            player.sendMessage(ChatColor.YELLOW + "Checking for rails from path [" +
                    MathUtil.round(result.position().posX, 3) + "/" +
                    MathUtil.round(result.position().posY, 3) + "/" +
                    MathUtil.round(result.position().posZ, 3) + "]");
            debugRails(player, result);
            return;
        }

        // From rails block clicked
        player.sendMessage(ChatColor.YELLOW + "Checking for rails from [" +
                clickedBlock.getX() + "/" +
                clickedBlock.getY() + "/" +
                clickedBlock.getZ() + "]");

        if (debugType.equalsIgnoreCase("Rails")) {
            debugRails(player, clickedBlock);
        }
    }

    public static void debugRails(Player player, RailState state) {
        TrackWalkingPoint walker = new TrackWalkingPoint(state);
        walker.setLoopFilter(true);
        int lim = 10000;
        if (player.isSneaking()) {
            while (walker.moveFull() && --lim > 0) {
                showParticle(walker.state.railBlock().getLocation().add(0.5, 0.5, 0.5));
            }
        } else {
            while (walker.move(0.3) && --lim > 0) {
                showParticle(walker.state.positionLocation());
            }
        }
    }

    public static void debugRails(Player player, Block railsBlock) {
        Vector direction = player.getEyeLocation().getDirection();
        TrackWalkingPoint walker = new TrackWalkingPoint(railsBlock, FaceUtil.getDirection(direction, false));
        walker.setLoopFilter(true);
        int lim = 10000;
        if (player.isSneaking()) {
            while (walker.moveFull() && --lim > 0) {
                showParticle(walker.state.railBlock().getLocation().add(0.5, 0.5, 0.5));
            }
        } else {
            while (walker.move(0.3) && --lim > 0) {
                showParticle(walker.state.positionLocation());
            }
        }
    }

    private static void showParticle(Location loc) {
        showParticle(loc, Particle.FOOTSTEP);
    }

    private static void showParticle(Location loc, Particle particle) {
        loc.getWorld().spawnParticle(particle, loc, 5);
    }
}
