package com.bergerkiller.bukkit.tc.debug;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import com.bergerkiller.bukkit.tc.utils.TrackWalkingPoint;

/**
 * Helpful functions used when displaying debug details
 */
public class DebugToolUtil {
    private static final double PARTICLE_SPACING = 0.2;
    private static final NumberFormat DEFAULT_NUMBER_FORMAT_SMALL = new DecimalFormat("0.0###", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
    private static final NumberFormat DEFAULT_NUMBER_FORMAT_LARGE = new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(Locale.ENGLISH));

    // cyclical array of chat colors used to turn an index into a color
    // there is a clear green/blue/cyan/magenta/yellow repeating pattern
    // red is omitted, because it is already used for errors/initial path particles
    private static final ChatColor[] chatcolor_wheel_values = {
            ChatColor.GREEN, ChatColor.BLUE,
            ChatColor.AQUA, ChatColor.LIGHT_PURPLE, ChatColor.GOLD,
            ChatColor.DARK_RED, ChatColor.DARK_GREEN, ChatColor.DARK_BLUE,
            ChatColor.DARK_AQUA, ChatColor.DARK_PURPLE, ChatColor.YELLOW,
            ChatColor.BLACK, ChatColor.DARK_GRAY, ChatColor.GRAY, ChatColor.WHITE
    };
    private static final Color[] color_wheel_values = {
            Color.GREEN, Color.BLUE,
            Color.AQUA, Color.FUCHSIA, Color.YELLOW,
            Color.MAROON, Color.OLIVE, Color.NAVY,
            Color.TEAL, Color.PURPLE,Color.YELLOW,
            Color.BLACK, Color.ORANGE, Color.GRAY, Color.WHITE
    };

    public static ChatColor getWheelChatColor(int index) {
        return chatcolor_wheel_values[index % chatcolor_wheel_values.length];
    }

    public static Color getWheelColor(int index) {
        return color_wheel_values[index % color_wheel_values.length];
    }

    public static String coordinates(RailPath.Position position) {
        return coordinates(MathUtil.floor(position.posX),
                           MathUtil.floor(position.posY),
                           MathUtil.floor(position.posZ));
    }

    public static String coordinates(int x, int y, int z) {
        return "[" + x + "/" + y + "/" + z + "]";
    }

    public static void showParticle(Location loc) {
        Util.spawnDustParticle(loc, 1.0, 0.1, 0.1);
    }

    public static void showFaceParticles(Player viewer, Color color, double x1, double y1, double z1, double x2, double y2, double z2) {
        showLineParticles(viewer, color, x1, y1, z1, x2, y1, z1);
        showLineParticles(viewer, color, x1, y1, z1, x1, y2, z1);
        showLineParticles(viewer, color, x1, y1, z1, x1, y1, z2);
        showLineParticles(viewer, color, x1, y2, z2, x2, y2, z2);
        showLineParticles(viewer, color, x2, y1, z2, x2, y2, z2);
        showLineParticles(viewer, color, x2, y2, z1, x2, y2, z2);
    }

    public static void showCubeParticles(Player player, Color color, double x1, double y1, double z1, double x2, double y2, double z2) {
        showFaceParticles(player, color, x1, y1, z1, x2, y1, z2);
        showFaceParticles(player, color, x1, y2, z1, x2, y2, z2);
        showLineParticles(player, color, x1, y1, z1, x1, y2, z1);
        showLineParticles(player, color, x2, y1, z1, x2, y2, z1);
        showLineParticles(player, color, x1, y1, z2, x1, y2, z2);
        showLineParticles(player, color, x2, y1, z2, x2, y2, z2);
    }

    public static void showLineParticles(Player viewer, Color color, Vector p1, Vector p2) {
        showLineParticles(viewer, color, p1.getX(), p1.getY(), p1.getZ(), p2.getX(), p2.getY(), p2.getZ());
    }

    public static void showLineParticles(Player viewer, Color color, double x1, double y1, double z1, double x2, double y2, double z2) {
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
            PlayerUtil.spawnDustParticles(viewer, position, color);
        }
    }

    public static void showEndOfTheRail(Player player, TrackWalkingPoint walker, double initialDistance) {
        // Move a very tiny bit backwards so we are still 'on' the rails, rather than the block edge
        RailPath.Position p = walker.state.position();
        if (Math.abs(p.posX - Math.floor(p.posX)) < 0.01 ||
            Math.abs(p.posY - Math.floor(p.posY)) < 0.01 ||
            Math.abs(p.posZ - Math.floor(p.posZ)) < 0.01)
        {
            p.move(-0.01);
        }

        if (walker.failReason == TrackWalkingPoint.FailReason.NAVIGATION_ABORTED) {
            player.sendMessage(ChatColor.RED + "A blocker sign at " + ChatColor.YELLOW + coordinates(p) +
                    ChatColor.RED + " is blocking trains after " +
                    ((int) (walker.movedTotal + initialDistance)) + " blocks!");
        } else {
            player.sendMessage(ChatColor.RED + "End of the rail at " + coordinates(p) +
                    " after " + ((int) (walker.movedTotal + initialDistance)) + " blocks!");
        }
    }

    public static String formatNumber(double value) {
        return ((value > 10.0) ? DEFAULT_NUMBER_FORMAT_LARGE : DEFAULT_NUMBER_FORMAT_SMALL).format(value);
    }
}
