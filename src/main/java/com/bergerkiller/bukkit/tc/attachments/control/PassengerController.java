package com.bergerkiller.bukkit.tc.attachments.control;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.controller.VehicleMountController;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;

/**
 * Tracks which entities are a passenger of other entities, and handles the synchronization of changes in this.
 * Each viewer of the entity has it's own passenger controller.
 */
@Deprecated
public class PassengerController {
    private final VehicleMountController mountHandler;

    public PassengerController(Player viewer) {
        this.mountHandler = PlayerUtil.getVehicleMountController(viewer);
    }

    /**
     * Informs a particular Entity Id should be removed from being a passenger or vehicle in this.
     * 
     * @param entityId of the entity to remove
     * @param sendPackets whether to inform the viewer of the changes
     */
    public void remove(int entityId, boolean sendPackets) {
        if (sendPackets) {
            mountHandler.clear(entityId);
        } else {
            mountHandler.remove(entityId);
        }
    }

    /**
     * Makes sure that a passenger entity is no longer mounted in a vehicle.
     * Does nothing if the passenger is not mounted in the vehicle.
     * 
     * @param vehicleEntityId
     * @param passengerEntityId
     */
    public void unmount(int vehicleEntityId, int passengerEntityId) {
        mountHandler.unmount(vehicleEntityId, passengerEntityId);
    }

    /**
     * Attempts to mount a passenger inside a vehicle.
     * Mounting may fail if the server is 1.8.8 or before, and another passenger is already mounted
     * in the vehicle.
     * 
     * @param vehicleEntityId
     * @param passengerEntityId
     * @return True if the mounting was possible, False if not.
     */
    public boolean mount(int vehicleEntityId, int passengerEntityId) {
        return mountHandler.mount(vehicleEntityId, passengerEntityId);
    }
}
