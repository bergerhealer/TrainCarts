package com.bergerkiller.bukkit.tc.attachments.surface;

import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.generated.net.minecraft.world.phys.AABBHandle;
import org.bukkit.util.Vector;

/**
 * Stores the state of a player that is simulated while walking on a collision surface.
 * This is a data holder only for now.
 */
public class SimulatedPlayer {
    public final AttachmentViewer viewer;
    public final AttachmentViewer.MovementController pmc;
    public Vector lastPosition;
    public PlayerState lastDebugState = null;
    public Vector position;
    /** Velocity of the player relative to the surface or air. Does not include velocity of the surface itself. */
    public Vector velocity;
    public CollisionSurface lastSurface;
    public boolean flying;
    public boolean lastJumpInput;

    public SimulatedPlayer(AttachmentViewer viewer, Vector position) {
        this(viewer, viewer.controlMovement(), position, position.clone(), new Vector(), CollisionSurface.DISABLED, false);
    }

    public SimulatedPlayer(
            AttachmentViewer viewer,
            AttachmentViewer.MovementController pmc,
            Vector lastPosition,
            Vector position,
            Vector velocity,
            CollisionSurface lastSurface,
            boolean flying
    ) {
        this.viewer = viewer;
        this.pmc = pmc;
        this.lastPosition = lastPosition;
        this.position = position;
        this.velocity = velocity;
        this.lastSurface = lastSurface;
        this.flying = flying;
        this.lastJumpInput = false;
    }
}

