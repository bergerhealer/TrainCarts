package com.bergerkiller.bukkit.tc.controller.global;

import com.bergerkiller.bukkit.common.block.SignSide;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.events.SignBuildEvent;
import com.bergerkiller.bukkit.tc.rails.RailLookup;
import org.bukkit.block.Sign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;

/**
 * Uses Bukkit {@link BlockPlaceEvent} and {@link SignChangeEvent} to detect when
 * signs are changed by a Player
 */
class SignControllerEditListenerLegacy implements Listener {
    private final SignController controller;

    public SignControllerEditListenerLegacy(SignController controller) {
        this.controller = controller;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockPlaceSignCheck(BlockPlaceEvent event) {
        Sign sign;
        if (
                !event.canBuild() ||
                        TrainCarts.isWorldDisabled(event) ||
                        !MaterialUtil.ISSIGN.get(event.getBlockPlaced()) ||
                        (sign = BlockUtil.getSign(event.getBlockPlaced())) == null
        ) {
            return;
        }

        // Mock a sign change event to handle building it
        SignBuildEvent build_event = new SignBuildEvent(
                event.getPlayer(),
                RailLookup.TrackedSign.forRealSign(sign, true, null),
                true
        );
        controller.handleSignChange(build_event, event.getBlock(), SignSide.FRONT, false);

        // If cancelled, cancel block placement too
        if (build_event.isCancelled()) {
            event.setBuild(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        if (TrainCarts.isWorldDisabled(event)) {
            return;
        }

        controller.handleSignChange(new SignBuildEvent(event, true), event.getBlock(), SignSide.sideChanged(event), true);
    }
}
