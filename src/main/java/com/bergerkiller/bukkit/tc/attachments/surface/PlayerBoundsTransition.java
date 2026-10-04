package com.bergerkiller.bukkit.tc.attachments.surface;

import com.bergerkiller.generated.net.minecraft.world.phys.AABBHandle;

/**
 * A player being moved from one position/state to another. Its bounds should not clip through
 * any surfaces during the transition.
 */
public class PlayerBoundsTransition {
    public final PlayerBoundsState from;
    public final PlayerBoundsState to;

    public PlayerBoundsTransition(AABBHandle from, AABBHandle to) {
        this(new PlayerBoundsState(from), new PlayerBoundsState(to));
    }

    public PlayerBoundsTransition(PlayerBoundsState from, PlayerBoundsState to) {
        this.from = from;
        this.to = to;
    }

    public PlayerBoundsState interpolate(double theta) {
        return from.interpolate(to, theta);
    }

    /**
     * Prints code used in unit tests to recreate the situation that spawned this collision solver result.
     * This will include the surface transitions involved and the player from/to bbox translation.
     *
     * @param str StringBuilder to write to
     * @param indentStr Indent string to prefix each line by
     */
    public void printDebugCreate(StringBuilder str, String indentStr) {
        str.append("new PlayerBoundsTransition(\n");
        str.append(indentStr).append("    ");
        from.printDebugCreate(str, indentStr + "    ");
        str.append(",\n").append(indentStr).append("    ");
        to.printDebugCreate(str, indentStr + "    ");
        str.append("\n").append(indentStr).append(");");
    }
}
