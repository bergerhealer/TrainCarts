package com.bergerkiller.bukkit.tc.attachments.control.light;

import org.bukkit.World;

import com.bergerkiller.bukkit.common.bases.IntVector3;

import ru.beykerykt.minecraft.lightapi.common.LightAPI;
import ru.beykerykt.minecraft.lightapi.common.api.engine.EditPolicy;
import ru.beykerykt.minecraft.lightapi.common.api.engine.LightFlag;
import ru.beykerykt.minecraft.lightapi.common.api.engine.SendPolicy;
import ru.beykerykt.minecraft.lightapi.common.api.engine.sched.ICallback;

/**
 * Implementation of the LightAPIController. Requires LightAPI version 5
 * or newer to be installed.
 */
class LightAPIControllerV5Impl extends LightAPIController {
    private final LightAPI api;
    private final ICallback callback;
    private final String worldName;
    private final int lightFlag;

    private LightAPIControllerV5Impl(String worldName, int lightFlag) {
        this.api = LightAPI.get();
        this.callback = (requestFlag, resultCode) -> {};
        this.worldName = worldName;
        this.lightFlag = lightFlag;
    }

    public static LightAPIControllerV5Impl forBlockLight(World world) {
        return new LightAPIControllerV5Impl(world.getName(), LightFlag.BLOCK_LIGHTING);
    }

    public static LightAPIControllerV5Impl forSkyLight(World world) {
        return new LightAPIControllerV5Impl(world.getName(), LightFlag.SKY_LIGHTING);
    }

    private void set(IntVector3 position, int level, EditPolicy editPolicy) {
        SendPolicy sendPolicy = SendPolicy.DEFERRED;
        api.setLightLevel(worldName, position.x, position.y, position.z, level,
                lightFlag, editPolicy, sendPolicy, callback);
    }

    @Override
    public void add(IntVector3 position, int level) {
        set(position, level, EditPolicy.DEFERRED);
    }

    @Override
    public void remove(IntVector3 position, int level) {
        // Must be immediate, otherwise sync operations fail (bug)
        set(position, 0, EditPolicy.DEFERRED);
    }

    @Override
    public void move(IntVector3 old_position, IntVector3 new_position, int level) {
        set(new_position, level, EditPolicy.DEFERRED);
        set(old_position, 0, EditPolicy.DEFERRED);
    }

    @Override
    public void update(IntVector3 position, int old_level, int new_level) {
        set(position, new_level, EditPolicy.DEFERRED);
    }

    @Override
    protected boolean onSync() {
        return false;
    }
}
