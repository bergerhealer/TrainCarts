package com.bergerkiller.bukkit.tc.signactions.detector;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.offline.OfflineBlock;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.detector.DetectorListener;
import com.bergerkiller.bukkit.tc.detector.DetectorRegion;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.offline.sign.OfflineSign;
import com.bergerkiller.bukkit.tc.offline.sign.OfflineSignStore;
import com.bergerkiller.bukkit.tc.signactions.SignActionDetector;
import com.bergerkiller.bukkit.tc.statements.Statement;

import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * A single detector sign part of a detector sign pair.
 * Tracks the events purely for this one detector sign.
 */
public class DetectorSign implements DetectorListener {
    private final OfflineSignStore store;
    private final OfflineSign sign;
    private Metadata metadata;

    public DetectorSign(OfflineSignStore store, OfflineSign sign, Metadata metadata) {
        this.store = store;
        this.sign = sign;
        this.metadata = metadata;
    }

    public IntVector3 getLocation() {
        return this.sign.getPosition();
    }

    public boolean isRemoved() {
        return this.metadata.owner != this;
    }

    public void remove() {
        // Event handling will automatically remove it for the pair's other sign too
        this.store.remove(this.sign.getBlock(), Metadata.class);
    }

    public void loadChunks(World world) {
        int cx = MathUtil.toChunk(this.sign.getPosition().x);
        int cz = MathUtil.toChunk(this.sign.getPosition().z);
        WorldUtil.loadChunks(world, cx, cz, 3);
    }

    /**
     * Validates a sign, if returned False, onRemove is called
     *
     * @param event around the sign
     * @return True if it is allowed, False if not
     */
    public boolean validate(SignActionEvent event) {
        return SignActionDetector.INSTANCE.match(event);
    }

    public boolean isLoaded(World world) {
        // return world != null && world.isChunkLoaded(this.location.x >> 4, this.location.z >> 4);
        return world != null;
    }

    /**
     * Gets the sign event from this offline Sign
     *
     * @return the sign, or null if the sign isn't loaded or missing
     */
    public SignActionEvent initSignEvent() {
        Block signBlock = this.sign.getLoadedBlock();
        if (signBlock != null) {
            this.loadChunks(signBlock.getWorld());
            if (MaterialUtil.ISSIGN.get(signBlock)) {
                SignActionEvent event = new SignActionEvent(signBlock);
                if (this.validate(event)) {
                    return event;
                }
            }
            this.remove();
            return null;
        }
        return null;
    }

    @Override
    public void onLeave(MinecartGroup group) {
        if (this.metadata.isLeverDown) {
            SignActionEvent event = initSignEvent();
            if (event != null && event.isTrainSign() && isDown(event, null, group)) {
                updateGroups(event);
            }
        }
    }

    @Override
    public void onEnter(MinecartGroup group) {
        if (!this.metadata.isLeverDown) {
            SignActionEvent event = initSignEvent();
            if (event != null && event.isTrainSign() && isDown(event, null, group)) {
                this.store.putIfPresent(this.sign.getBlock(),
                        this.metadata = this.metadata.setLeverDown(true));
                event.setLevers(true);
            }
        }
    }

    @Override
    public void onLeave(MinecartMember<?> member) {
        if (this.metadata.isLeverDown) {
            SignActionEvent event = initSignEvent();
            if (event != null && event.isCartSign() && isDown(event, member, null)) {
                updateMembers(event);
            }
        }
    }

    @Override
    public void onEnter(MinecartMember<?> member) {
        if (!this.metadata.isLeverDown) {
            SignActionEvent event = initSignEvent();
            if (event != null && event.isCartSign() && isDown(event, member, null)) {
                this.store.putIfPresent(this.sign.getBlock(),
                        this.metadata = this.metadata.setLeverDown(true));
                event.setLevers(true);
            }
        }
    }

    public boolean updateMembers(SignActionEvent event) {
        for (MinecartMember<?> mm : this.metadata.region.getMembers()) {
            if (isDown(event, mm, null)) {
                this.store.putIfPresent(this.sign.getBlock(),
                        this.metadata = this.metadata.setLeverDown(true));
                event.setLevers(true);
                return true;
            }
        }
        this.store.putIfPresent(this.sign.getBlock(),
                this.metadata = this.metadata.setLeverDown(false));
        event.setLevers(false);
        return false;
    }

    public boolean updateGroups(SignActionEvent event) {
        for (MinecartGroup g : this.metadata.region.getGroups()) {
            if (isDown(event, null, g)) {
                this.store.putIfPresent(this.sign.getBlock(),
                        this.metadata = this.metadata.setLeverDown(true));
                event.setLevers(true);
                return true;
            }
        }
        this.store.putIfPresent(this.sign.getBlock(),
                this.metadata = this.metadata.setLeverDown(false));
        event.setLevers(false);
        return false;
    }

    @Override
    public void onUpdate(MinecartMember<?> member) {
        SignActionEvent event = initSignEvent();
        if (event != null) this.updateMembers(event);
    }

    @Override
    public void onUpdate(MinecartGroup group) {
        SignActionEvent event = initSignEvent();
        if (event != null) this.updateGroups(event);
    }

    public boolean isDown(SignActionEvent event, MinecartMember<?> member, MinecartGroup group) {
        boolean firstEmpty = false;
        if (event.getLine(2).isEmpty()) {
            firstEmpty = true;
        } else if (Statement.has(member, group, event.getLine(2), event)) {
            return true;
        }
        if (event.getLine(3).isEmpty()) {
            return firstEmpty; //two empty lines, no statements, simple 'has'
        } else {
            return Statement.has(member, group, event.getLine(3), event);
        }
    }

    @Override
    public void onRegister(DetectorRegion region) {
    }

    @Override
    public void onUnregister(DetectorRegion region) {
    }

    @Override
    public void onUnload(MinecartGroup group) {
        //TODO: Unloaded group storage system
        this.onLeave(group);
    }

    /**
     * Metadata persistently stored for a single detector sign
     */
    public static class Metadata {
        public final OfflineBlock otherSign;
        public final DetectorRegion region;
        public final boolean isLeverDown;
        public DetectorSign owner;

        public Metadata(OfflineBlock otherSign, DetectorRegion region, boolean isLeverDown) {
            this(otherSign, region, isLeverDown, null);
        }

        private Metadata(OfflineBlock otherSign, DetectorRegion region, boolean isLeverDown, DetectorSign owner) {
            this.otherSign = otherSign;
            this.region = region;
            this.isLeverDown = isLeverDown;
            this.owner = owner;
        }

        public Metadata setLeverDown(boolean down) {
            if (down == this.isLeverDown) {
                return this; // Avoids frivolous saves
            }
            return new Metadata(otherSign, region, down, owner);
        }
    }
}
