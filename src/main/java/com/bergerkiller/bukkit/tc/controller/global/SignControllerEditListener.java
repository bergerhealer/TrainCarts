package com.bergerkiller.bukkit.tc.controller.global;

import com.bergerkiller.bukkit.common.events.SignEditTextEvent;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.events.SignBuildEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Uses BKCommonLib's {@link SignEditTextEvent} to track when signs are edited
 * or placed by players.
 */
class SignControllerEditListener implements Listener {
    private final SignController controller;

    public SignControllerEditListener(SignController controller) {
        this.controller = controller;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSignEditText(SignEditTextEvent event) {
        if (TrainCarts.isWorldDisabled(event)) {
            return;
        }

        controller.handleSignChange(SignBuildEvent.BKCLSignEditBuildEvent.create(event, true),
                event.getBlock(), event.getSide(),
                event.getEditReason() != SignEditTextEvent.EditReason.CTRL_PICK_PLACE);
    }
}
