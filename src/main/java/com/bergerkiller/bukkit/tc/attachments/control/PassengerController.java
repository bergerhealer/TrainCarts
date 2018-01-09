package com.bergerkiller.bukkit.tc.attachments.control;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.tc.TCMountPacketHandler;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutMountHandle;

/**
 * Tracks which entities are a passenger of other entities, and handles the synchronization of changes in this.
 * Each viewer of the entity has it's own passenger controller.
 */
public class PassengerController {
    private static final boolean MULTIPLE_PASSENGERS = PacketPlayOutMountHandle.T.isAvailable();
    private final List<Vehicle> vehicles = new ArrayList<Vehicle>();
    private final TCMountPacketHandler.PlayerHandler mountHandler;

    public PassengerController(Player viewer) {
        this.mountHandler = TrainCarts.plugin.getMountHandler().get(viewer);
    }

    /**
     * Resends any mounting packets for a particular entity.
     * This is often called after an entity is re-spawned.
     * 
     * @param entityId
     */
    public void resend(int entityId) {
        for (Vehicle vehicle : vehicles) {
            if (vehicle.id == entityId) {
                // Resend as vehicle
                if (MULTIPLE_PASSENGERS) {
                    mountHandler.mount(vehicle.id, vehicle.passengers);
                } else {
                    for (int passengerId : vehicle.passengers) {
                        mountHandler.attach(vehicle.id, passengerId);
                    }
                }
            } else {
                for (int passengerId : vehicle.passengers) {
                    if (passengerId == entityId) {
                        // Resend for passenger
                        if (MULTIPLE_PASSENGERS) {
                            mountHandler.mount(vehicle.id, vehicle.passengers);
                        } else {
                            mountHandler.attach(vehicle.id, passengerId);
                        }
                        break;
                    }
                }
            }
        }
    }

    /**
     * Informs a particular Entity Id should be removed from being a passenger or vehicle in this.
     * 
     * @param entityId of the entity to remove
     * @param sendPackets whether to inform the viewer of the changes
     */
    public void remove(int entityId, boolean sendPackets) {
        Iterator<Vehicle> vehicle_iter = this.vehicles.iterator();
        while (vehicle_iter.hasNext()) {
            Vehicle vehicle = vehicle_iter.next();
            if (vehicle.id == entityId || (vehicle.passengers.length == 1 && vehicle.passengers[0] == entityId)) {
                vehicle_iter.remove();

                // Send packets indicating removal
                if (sendPackets) {
                    if (MULTIPLE_PASSENGERS) {
                        mountHandler.mount(vehicle.id, new int[0]);
                    } else {
                        for (int passengerId : vehicle.passengers) {
                            mountHandler.attach(-1, passengerId);
                        }
                    }
                }
                continue;
            }

            // Remove passengers with this Id
            for (int i = 0; i < vehicle.passengers.length; i++) {
                if (vehicle.passengers[i] == entityId) {
                    vehicle.removePassengerAt(i);

                    // Send packets
                    if (sendPackets) {
                        if (MULTIPLE_PASSENGERS) {
                            mountHandler.mount(vehicle.id, vehicle.passengers);
                        } else {
                            mountHandler.attach(-1, entityId);
                        }
                    }

                    break;
                }
            }
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
        if (vehicleEntityId == -1 || passengerEntityId == -1) {
            return;
        }

        Iterator<Vehicle> vehicle_iter = this.vehicles.iterator();
        while (vehicle_iter.hasNext()) {
            Vehicle vehicle = vehicle_iter.next();
            if (vehicle.id != vehicleEntityId) {
                continue;
            }
            if (vehicle.passengers.length == 1 && vehicle.passengers[0] == passengerEntityId) {
                // Remove entire vehicle
                vehicle_iter.remove();
                if (MULTIPLE_PASSENGERS) {
                    mountHandler.mount(vehicle.id, new int[0]);
                } else {
                    mountHandler.attach(-1, passengerEntityId);
                }
                return;
            }
            for (int i = 0; i < vehicle.passengers.length; i++) {
                if (vehicle.passengers[i] == passengerEntityId) {
                    vehicle.removePassengerAt(i);
                    if (MULTIPLE_PASSENGERS) {
                        mountHandler.mount(vehicle.id, vehicle.passengers);
                    } else {
                        mountHandler.attach(-1, passengerEntityId);
                    }
                    return;
                }
            }
            return;
        }
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
        if (vehicleEntityId == -1 || passengerEntityId == -1) {
            return false;
        }
        for (Vehicle vehicle : this.vehicles) {
            if (vehicle.id == vehicleEntityId) {
                // Check if already contained. If so, skip.
                for (int passenger : vehicle.passengers) {
                    if (passenger == passengerEntityId) {
                        return true;
                    }
                }

                // Can we add another passenger?
                if (!MULTIPLE_PASSENGERS && vehicle.passengers.length > 0) {
                    return false;
                }

                // First remove the passenger from any previous vehicles, silently
                removePassengerSilently(passengerEntityId);

                // Add another passenger
                vehicle.passengers = Arrays.copyOf(vehicle.passengers, vehicle.passengers.length + 1);
                vehicle.passengers[vehicle.passengers.length - 1] = passengerEntityId;

                // Send mount packets
                if (MULTIPLE_PASSENGERS) {
                    mountHandler.mount(vehicle.id, vehicle.passengers);
                } else {
                    mountHandler.attach(vehicle.id, passengerEntityId);
                }
                return true;
            }
        }

        // Create a brand new link
        // First remove the player passenger from any other vehicles
        removePassengerSilently(passengerEntityId);

        // Create a new Vehicle instance
        Vehicle new_vehicle = new Vehicle(vehicleEntityId, passengerEntityId);
        this.vehicles.add(new_vehicle);
        if (MULTIPLE_PASSENGERS) {
            mountHandler.mount(new_vehicle.id, new_vehicle.passengers);
        } else {
            mountHandler.attach(new_vehicle.id, passengerEntityId);
        }

        return true;
    }

    private void removePassengerSilently(int entityId) {
        Iterator<Vehicle> vehicle_iter = this.vehicles.iterator();
        while (vehicle_iter.hasNext()) {
            Vehicle vehicle = vehicle_iter.next();
            if (vehicle.passengers.length == 1 && vehicle.passengers[0] == entityId) {
                vehicle_iter.remove();
                continue;
            }
            for (int i = 0; i < vehicle.passengers.length; i++) {
                if (vehicle.passengers[i] == entityId) {
                    vehicle.removePassengerAt(i);
                    break;
                }
            }
        }
    }

    private static class Vehicle {
        public final int id;
        public int[] passengers;

        public Vehicle(int id, int...passengerIds) {
            this.id = id;
            this.passengers = passengerIds;
        }

        public void removePassengerAt(int index) {
            int[] new_passengers = new int[this.passengers.length - 1];
            int dstIdx = 0;
            for (int srcIdx = 0; srcIdx < this.passengers.length; srcIdx++) {
                if (srcIdx != index) {
                    new_passengers[dstIdx++] = this.passengers[srcIdx];
                }
            }
            this.passengers = new_passengers;
        }
    }
}
