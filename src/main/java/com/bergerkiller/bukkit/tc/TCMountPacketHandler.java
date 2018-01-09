package com.bergerkiller.bukkit.tc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.protocol.CommonPacket;
import com.bergerkiller.bukkit.common.protocol.PacketMonitor;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.wrappers.IntHashMap;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutAttachEntityHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutMountHandle;

/**
 * Does a whole lot of tracking to send entity mount (or attach on older versions) packets at the right time,
 * after the entities referenced in them have been spawned.
 */
public class TCMountPacketHandler implements PacketMonitor {
    private final Map<Player, PlayerHandler> _players = new IdentityHashMap<Player, PlayerHandler>();
    public static PacketType[] MONITORED_TYPES = {
            PacketType.OUT_ENTITY_SPAWN,
            PacketType.OUT_ENTITY_SPAWN_LIVING,
            PacketType.OUT_ENTITY_SPAWN_NAMED,
            PacketType.OUT_ENTITY_DESTROY
    };

    public synchronized void cleanup() {
        Iterator<Player> iter = this._players.keySet().iterator();
        while (iter.hasNext()) {
            if (!iter.next().isOnline()) {
                iter.remove();
            }
        }
    }

    public synchronized void remove(Player player) {
        this._players.remove(player);
    }

    public synchronized PlayerHandler get(Player player) {
        PlayerHandler handler = this._players.get(player);
        if (handler == null) {
            handler = new PlayerHandler(player);
            if (player.isOnline()) {
                this._players.put(player, handler);
            }
        }
        return handler;
    }

    @Override
    public void onMonitorPacketReceive(CommonPacket packet, Player player) {
    }

    @Override
    public void onMonitorPacketSend(CommonPacket packet, Player player) {
        get(player).handle(packet);
    }

    public static class PlayerHandler {
        private final Player _player;
        private final IntHashMap<EntityMeta> _map = new IntHashMap<EntityMeta>();
        private final HashSet<PendingTask> _checkTasks = new HashSet<PendingTask>();

        private PlayerHandler(Player player) {
            this._player = player;
        }

        public Player getPlayer() {
            return this._player;
        }

        public synchronized void mount(int vehicleId, int[] passengerIds) {
            new PendingMount(vehicleId, passengerIds).run(this);
        }

        public synchronized void attach(int vehicleId, int passengerId) {
            new PendingAttach(vehicleId, passengerId).run(this);
        }

        private synchronized void despawn(int[] ids) {
            for (int id : ids) {
                EntityMeta meta = _map.get(id);
                if (meta != null && meta.spawned) {
                    meta.spawned = false;
                    if (meta.pendingTasks == null || meta.pendingTasks.isEmpty()) {
                        _map.remove(id);
                    }
                }
            }
        }

        private synchronized void spawn(int id) {
            EntityMeta meta = _map.get(id);
            if (meta == null) {
                _map.put(id, new EntityMeta());
            } else if (!meta.spawned) {
                meta.spawned = true;

                // Refresh tasks associated with this Id
                if (meta.pendingTasks != null && !meta.pendingTasks.isEmpty()) {
                    if (this._checkTasks.isEmpty()) {
                        CommonUtil.nextTick(new Runnable() {
                            @Override
                            public void run() {
                                runCheckTasks();
                            }
                        });
                    }
                    this._checkTasks.addAll(meta.pendingTasks);
                }
            }
        }

        private synchronized void runCheckTasks() {
            ArrayList<PendingTask> tasks = new ArrayList<PendingTask>(this._checkTasks);
            this._checkTasks.clear();
            for (PendingTask task : tasks) {
                task.run(this);
            }
        }

        private void handle(CommonPacket packet) {
            PacketType type = packet.getType();
            if (type == PacketType.OUT_ENTITY_SPAWN) {
                spawn(packet.read(PacketType.OUT_ENTITY_SPAWN.entityId));
            } else if (type == PacketType.OUT_ENTITY_SPAWN_LIVING) {
                spawn(packet.read(PacketType.OUT_ENTITY_SPAWN_LIVING.entityId));
            } else if (type == PacketType.OUT_ENTITY_SPAWN_NAMED) {
                spawn(packet.read(PacketType.OUT_ENTITY_SPAWN_NAMED.entityId));
            } else if (type == PacketType.OUT_ENTITY_DESTROY) {
                despawn(packet.read(PacketType.OUT_ENTITY_DESTROY.entityIds));
            }
        }

        // called before running a task to handle pre-spawn dependencies
        // adds the pending task to the metadata of the entity, if the entity is not yet spawned
        private boolean require(PendingTask task, int entityId) {
            if (entityId < 0) {
                return true;
            }
            EntityMeta meta = _map.get(entityId);
            if (meta == null) {
                meta = new EntityMeta();
                meta.spawned = false;
                meta.pendingTasks = new ArrayList<PendingTask>(1);
                meta.pendingTasks.add(task);
                _map.put(entityId, meta);
                return false;
            }
            if (meta.pendingTasks != null) {
                meta.pendingTasks.remove(task);
                if (meta.pendingTasks.isEmpty()) {
                    meta.pendingTasks = null;
                }
            }
            if (!meta.spawned) {
                if (meta.pendingTasks == null) {
                    meta.pendingTasks = new ArrayList<PendingTask>(1);
                }
                meta.pendingTasks.add(task);
                return false;
            }
            return true;
        }
    }

    private static class EntityMeta {
        public boolean spawned = true;
        public List<PendingTask> pendingTasks = null;
    }

    private static interface PendingTask {
        public void run(PlayerHandler handler);
    }

    private static class PendingMount implements PendingTask {
        public final int vehicleId;
        public final int[] passengerIds;

        public PendingMount(int vehicleId, int[] passengerIds) {
            this.vehicleId = vehicleId;
            this.passengerIds = passengerIds;
        }

        @Override
        public void run(PlayerHandler handler) {
            if (!handler.require(this, this.vehicleId)) {
                return;
            }
            for (int passenger : this.passengerIds) {
                if (!handler.require(this, passenger)) {
                    return;
                }
            }
            PacketUtil.sendPacket(handler.getPlayer(), PacketPlayOutMountHandle.createNew(vehicleId, passengerIds));
        }
    }

    private static class PendingAttach implements PendingTask {
        public final int vehicleId;
        public final int passengerId;

        public PendingAttach(int vehicleId, int passengerId) {
            this.vehicleId = vehicleId;
            this.passengerId = passengerId;
        }

        @Override
        public void run(PlayerHandler handler) {
            if (!handler.require(this, this.vehicleId)) {
                return;
            }
            if (!handler.require(this, this.passengerId)) {
                return;
            }
            PacketPlayOutAttachEntityHandle attach = PacketPlayOutAttachEntityHandle.T.newHandleNull();
            attach.setVehicleId(this.vehicleId);
            attach.setPassengerId(this.passengerId);
            PacketUtil.sendPacket(handler.getPlayer(), attach);
        }
    }
}
