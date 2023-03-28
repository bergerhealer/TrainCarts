package com.bergerkiller.bukkit.tc.attachments;

import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.protocol.CommonPacket;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentManager;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.generated.net.minecraft.network.protocol.PacketHandle;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * An object that stores a list of AttachmentViewer viewers that
 * can be spawned/destroyed/updated for these viewers. Implementations
 * can decide how spawning/destroying functions. Also includes some
 * base functions for position synchronization.
 */
public abstract class VirtualSpawnableObject {
    protected final AttachmentManager manager;
    private final ArrayList<AttachmentViewer> viewers = new ArrayList<AttachmentViewer>();
    private List<AttachmentViewer> viewersPendingGlowColorRemoval = Collections.emptyList();
    private ChatColor glowColor = null;

    public VirtualSpawnableObject(AttachmentManager manager) {
        this.manager = manager; // Can be null!
    }

    /**
     * Should be implemented to spawn this object to the viewer
     *
     * @param viewer Attachment Viewer
     * @param motion Initial motion of this object
     */
    protected abstract void sendSpawnPackets(AttachmentViewer viewer, Vector motion);

    /**
     * Should be implemented to destroy this object to the viewer
     *
     * @param viewer Attachment Viewer
     */
    protected abstract void sendDestroyPackets(AttachmentViewer viewer);

    /**
     * Applies the glowing effect to this entity, for all viewers. This should
     * update metadata used by all viewers. After this, the
     * {@link #applyGlowColorForViewer(AttachmentViewer, ChatColor)} method may
     * be called for all current viewers.<br>
     * <br>
     * This method is <b>not</b> called when glowing is still disabled (default)
     * and the entity is first created.<br>
     * <br>
     * This is called every time {@link #setGlowColor(ChatColor)} is called
     * with a different glow color, or null.
     *
     * @param color Glow color
     */
    protected void applyGlowing(ChatColor color) {
    }

    /**
     * Applies the glowing effect to this spawned entity for a viewer.
     * Is called when the glowing effect is changed, or when this entity
     * is spawned to a new viewer. When glowing is on, this method is called
     * with a viewer for which the entity is destroyed with a null color
     * to clean up any metadata for this viewer.
     *
     * @param viewer Attachment Viewer
     * @param color Color to be applied. Null to disable the glowing effect.
     */
    protected void applyGlowColorForViewer(AttachmentViewer viewer, ChatColor color) {
    }

    /**
     * Updates the position and rotation transform of this spawnable object
     * Before {@link #spawn(AttachmentViewer, Vector)} can be called this method
     * should have been called at least once.
     *
     * @param transform Transformation matrix describing the absolute position
     *                  and orientation of this spawnable object.
     */
    public abstract void updatePosition(Matrix4x4 transform);

    /**
     * Compares the position this spawnable object has to the viewers against
     * the live transformation last supplied using {@link #updatePosition(Matrix4x4)}
     * and sends corrective synchronization updates. This method can be called
     * less often than once a tick, making use of client-side interpolation
     * to make the movement smooth.
     *
     * @param absolute Whether to synchronize the position using an absolute
     *                 teleport. This ensures any position drift is corrected.
     */
    public abstract void syncPosition(boolean absolute);

    /**
     * Gets whether a particular entity id is in use for displaying this spawnable object.
     * Is used when players click the entity.
     *
     * @param entityId Entity id to check
     */
    public abstract boolean containsEntityId(int entityId);

    /**
     * Sets whether this object is glowing - a way to show the selected attachment in the editor.
     * If a color is specified glowing is activated. If null is specified, it is disabled.
     *
     * @param color Glow color to apply, null to disable glowing
     */
    public final void setGlowColor(ChatColor color) {
        if (this.glowColor != color) {
            this.glowColor = color;
            applyGlowing(color);

            // When color is disabled, there is no need to apply it right away
            // However, it does need to be applied when this entity is destroyed for players
            // This otherwise leaks teams being created and not destroyed.
            if (color == null) {
                this.viewersPendingGlowColorRemoval = viewers.isEmpty()
                        ? Collections.emptyList() : new ArrayList<>(viewers);
            } else {
                this.viewersPendingGlowColorRemoval = Collections.emptyList();
                forAllViewers(viewer -> applyGlowColorForViewer(viewer, color));
            }
        }
    }

    /**
     * Gets the glow color that is currently applied
     *
     * @return glow color. <i>Null</i> if glowing is disabled.
     * @see #setGlowColor(ChatColor) 
     */
    public final ChatColor getGlowColor() {
        return glowColor;
    }

    @Deprecated
    public void addViewerWithoutSpawning(Player viewer) {
        addViewerWithoutSpawning(asAttachmentViewer(viewer));
    }

    public void addViewerWithoutSpawning(AttachmentViewer viewer) {
        if (!this.viewers.contains(viewer)) {
            this.viewers.add(viewer);
        }
    }

    public boolean hasViewers() {
        return !this.viewers.isEmpty();
    }

    public Collection<AttachmentViewer> getViewers() {
        return viewers;
    }

    public void forAllViewers(Consumer<? super AttachmentViewer> action) {
        viewers.forEach(action);
    }

    @Deprecated
    public boolean isViewer(Player viewer) {
        return isViewer(asAttachmentViewer(viewer));
    }

    public boolean isViewer(AttachmentViewer viewer) {
        return this.viewers.contains(viewer);
    }

    @Deprecated
    public void spawn(Player viewer, Vector motion) {
        spawn(asAttachmentViewer(viewer), motion);
    }

    public void spawn(AttachmentViewer viewer, Vector motion) {
        // Destroy first if needed. Shouldn't happen, but just in case.
        if (this.viewers.contains(viewer)) {
            this.destroy(viewer);
        }
        this.viewers.add(viewer);

        this.sendSpawnPackets(viewer, motion);

        if (glowColor != null) {
            this.applyGlowColorForViewer(viewer, glowColor);
        }
    }

    public void destroyForAll() {
        for (AttachmentViewer viewer : this.viewers) {
            this.sendDestroyPackets(viewer);
            if (this.viewersPendingGlowColorRemoval.contains(viewer)) {
                this.applyGlowColorForViewer(viewer, null);
            }
        }
        this.viewers.clear();
        this.viewersPendingGlowColorRemoval = Collections.emptyList();
    }

    @Deprecated
    public void destroy(Player viewer) {
        destroy(asAttachmentViewer(viewer));
    }

    public void destroy(AttachmentViewer viewer) {
        this.viewers.remove(viewer);
        this.sendDestroyPackets(viewer);
        if (this.viewersPendingGlowColorRemoval.remove(viewer)) {
            this.applyGlowColorForViewer(viewer, null);
        }
    }

    public void broadcast(CommonPacket packet) {
        viewers.forEach(v -> v.send(packet));
    }

    public void broadcast(PacketHandle packet) {
        viewers.forEach(v -> v.send(packet));
    }

    private AttachmentViewer asAttachmentViewer(Player player) {
        if (this.manager != null) {
            return this.manager.asAttachmentViewer(player);
        } else {
            return AttachmentViewer.fallback(player);
        }
    }
}
