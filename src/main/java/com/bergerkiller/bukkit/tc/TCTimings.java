package com.bergerkiller.bukkit.tc;

import com.bergerkiller.bukkit.common.Timings;

public class TCTimings {
    private static final Timings timings = new Timings(TrainCarts.plugin);
    public static final Timings GROUP_DOPHYSICS = timings.create("Train Physics");
    public static final Timings GROUP_UPDATE_DIRECTION = timings.create("updateDirection  (Train Physics)");
    public static final Timings GROUP_UPDATE_CHUNKS = timings.create("updateChunkInformation  (Train Physics)");
    public static final Timings GROUP_ENFORCE_SPEEDAHEAD = timings.create("getSpeedAhead  (Train Physics)");
    public static final Timings GROUP_TICK_ACTIONS = timings.create("tickActions  (Train Physics)");
    public static final Timings MEMBER_PHYSICS_PRE = timings.create("onPhysicsPreMove  (Train Physics)");
    public static final Timings MEMBER_PHYSICS_POST = timings.create("onPhysicsPostMove  (Train Physics)");
    public static final Timings MEMBER_PHYSICS_POST_MOVE = timings.create("onMove  (Train Physics, Post-Move)");
    public static final Timings MEMBER_PHYSICS_POST_RAIL_LOGIC = timings.create("onPostMove  (Train Physics, Post-Move, RailLogic)");
    public static final Timings MEMBER_PHYSICS_BLOCK_COLLISION = timings.create("onBlockCollision  (Train Physics, Post-Move)");
    public static final Timings MEMBER_PHYSICS_UPDATE_WHEELS = timings.create("updateWheels  (Train Physics, Wheel Tracker)");
    public static final Timings MEMBER_PHYSICS_DISCOVER_RAIL = timings.create("discoverRail (Train Physics, RailLogic)");
    public static final Timings SIGNTRACKER_REFRESH = timings.create("refreshSigns  (Train Physics, Sign Tracker)");
    public static final Timings RAILTRACKER_REFRESH = timings.create("refreshRails  (Train Physics, Rail Tracker)");
    public static final Timings RAILMEMBERCACHE = timings.create("cacheRailMembers  (Train Physics, Rail Tracker, Cache)");
    public static final Timings RAILTYPE_FINDRAILINFO = timings.create("findRailInfo  (Rail Type Cache)");
    public static final Timings SIGNACTION_SPAWN = timings.create("spawn  (Sign Action, Spawner)");
    public static final Timings NETWORK_UPDATE_POSITIONS = timings.create("updatePositions  (Network)");
    public static final Timings NETWORK_PERFORM_TICK = timings.create("performTick  (Network)");
    public static final Timings NETWORK_PERFORM_MOVEMENT = timings.create("performMovement  (Network)");
}
