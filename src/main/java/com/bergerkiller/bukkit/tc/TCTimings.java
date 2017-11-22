package com.bergerkiller.bukkit.tc;

import com.bergerkiller.bukkit.common.Timings;

public class TCTimings {
    private static final Timings timings = new Timings(TrainCarts.plugin);
    public static final Timings GROUP_DOPHYSICS = timings.create("Train Physics");
    public static final Timings GROUP_UPDATE_DIRECTION = timings.create("Train Physics - MinecartGroup::updateDirection()");
    public static final Timings GROUP_LOAD_CHUNKS = timings.create("Train Physics - load nearby chunks");
    public static final Timings GROUP_ENFORCE_SPEEDAHEAD = timings.create("Train Physics - track speed ahead");
    public static final Timings GROUP_TICK_ACTIONS = timings.create("Train Physics - update actions");
    public static final Timings MEMBER_PHYSICS_PRE = timings.create("Train Physics - MinecartMember::onPhysicsPreMove()");
    public static final Timings MEMBER_PHYSICS_POST = timings.create("Train Physics - MinecartMember::onPhysicsPostMove()");
    public static final Timings MEMBER_PHYSICS_MOVE = timings.create("Train Physics - MinecartMember::onPhysicsPostMove() - onMove()");
    public static final Timings MEMBER_PHYSICS_MOVE_EVENT = timings.create("Train Physics - MinecartMember::onPhysicsPostMove() - MEMBER_MOVE");
    public static final Timings MEMBER_PHYSICS_BLOCK_COLLISION = timings.create("Train Physics - block collision handling");
    public static final Timings BLOCKTRACKER_REFRESH = timings.create("Train Physics - BlockTracker::refresh()");
    public static final Timings RAILTRACKER_REFRESH = timings.create("Train Physics - RailTracker::refresh()");
    public static final Timings RAILTYPE_FINDRAILINFO = timings.create("Train Physics - RailType::findRailInfo()");
}
