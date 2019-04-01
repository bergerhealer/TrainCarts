package com.bergerkiller.bukkit.tc.attachments.api;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.tc.attachments.control.PassengerController;

/**
 * All state information for an attachment manager that is used
 * for internal bookkeeping.
 */
public class AttachmentManagerInternalState {
    /**
     * Map of passenger controllers, for each player to which packets are sent
     */
    protected final Map<Player, PassengerController> passengerControllers = new HashMap<Player, PassengerController>();
}
