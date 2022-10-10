package com.bergerkiller.bukkit.tc.rails;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.bukkit.World;
import org.bukkit.block.Block;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.offline.OfflineBlock;
import com.bergerkiller.bukkit.common.offline.OfflineWorld;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.controller.global.SignControllerWorld;
import com.bergerkiller.bukkit.tc.detector.DetectorRegion;
import com.bergerkiller.bukkit.tc.rails.RailLookup.CachedRailPiece;
import com.bergerkiller.bukkit.tc.rails.RailLookup.TrackedSign;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.signactions.mutex.MutexZoneCacheWorld;

/**
 * Placeholder implementation for worlds that aren't loaded (null).
 * Used by {@link WorldRailLookup#NONE}.
 */
final class WorldRailLookupNone implements WorldRailLookup {

    @Override
    public World getWorld() {
        return null;
    }

    @Override
    public OfflineWorld getOfflineWorld() {
        return OfflineWorld.NONE;
    }

    @Override
    public MutexZoneCacheWorld getMutexZones() {
        throw new UnsupportedOperationException("World Rail Lookup cache is closed");
    }

    @Override
    public SignControllerWorld getSignController() {
        throw new UnsupportedOperationException("World Rail Lookup cache is closed");
    }

    @Override
    public boolean isValid() {
        return false;
    }

    @Override
    public boolean isValidForWorld(World world) {
        return false;
    }

    @Override
    public RailPiece[] findAtStatePosition(RailState state) {
        throw new ClosedException();
    }

    @Override
    public RailPiece[] findAtBlockPosition(OfflineBlock positionBlock) {
        throw new ClosedException();
    }

    @Override
    public CachedRailPiece lookupCachedRailPieceIfCached(OfflineBlock railOfflineBlock, RailType railType) {
        // It's obviously not cached because this one is invalid...
        // This does assume people keep their cache up-to-date. Otherwise it could exist in another cache.
        return CachedRailPiece.NONE;
    }

    @Override
    public List<CachedRailPiece> lookupCachedRailPieces(OfflineBlock railOfflineBlock) {
        return Collections.emptyList();
    }

    @Override
    public CachedRailPiece lookupCachedRailPiece(OfflineBlock railOfflineBlock, Block railBlock, RailType railType) {
        throw new ClosedException();
    }

    @Override
    public List<MinecartMember<?>> findMembersOnRail(IntVector3 railCoordinates) {
        // Safe to assume there's no members. Lookup might be out of date. Not our responsibility.
        return Collections.emptyList();
    }

    @Override
    public List<MinecartMember<?>> findMembersOnRail(OfflineBlock railOfflineBlock) {
        // Safe to assume there's no members. Lookup might be out of date. Not our responsibility.
        return Collections.emptyList();
    }

    @Override
    public void removeMemberFromAll(MinecartMember<?> member) {
        throw new ClosedException();
    }

    @Override
    public TrackedSign[] discoverSignsAtRailPiece(RailPiece rail) {
        throw new ClosedException();
    }

    @Override
    public RailPiece discoverRailPieceFromSign(Block signblock) {
        throw new ClosedException();
    }

    @Override
    public void storeDetectorRegions(IntVector3 coordinates, DetectorRegion[] regions) {
        throw new ClosedException();
    }

    @Override
    public DetectorRegion[] getDetectorRegions(IntVector3 coordinates) {
        throw new ClosedException();
    }

    @Override
    public Collection<IntVector3> getBlockIndex() {
        return Collections.emptySet();
    }
}
