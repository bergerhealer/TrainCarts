package com.bergerkiller.bukkit.tc.controller.type;

import com.bergerkiller.bukkit.common.entity.type.CommonMinecartCommandBlock;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

public class MinecartMemberCommandBlock extends MinecartMember<CommonMinecartCommandBlock> {

    @Override
    public void onActivatorUpdate(boolean activated) {
        //TODO!
        //Logging.LOGGER_DEBUG.warnOnce("CommandBlock ActivatorUpdate not implemented");
        getEntity().activate(this.getBlock(), activated);
    }
}
