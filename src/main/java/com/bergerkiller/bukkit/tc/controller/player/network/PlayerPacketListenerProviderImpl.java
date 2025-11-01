package com.bergerkiller.bukkit.tc.controller.player.network;

import com.bergerkiller.bukkit.common.RunOnceTask;
import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.events.PacketReceiveEvent;
import com.bergerkiller.bukkit.common.events.PacketSendEvent;
import com.bergerkiller.bukkit.common.protocol.PacketListener;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Hosts instances of the {@link PlayerPacketListener} and manages their
 * lifetime.
 */
final class PlayerPacketListenerProviderImpl implements PlayerPacketListener.Provider {
    private static final int CLEANUP_INTERVAL = 10 * 20; // 10 seconds
    private final Map<Set<PacketType>, TypeSetListener> activeTypeSetListeners = new HashMap<>();
    private final List<Player> recentlyQuitPlayers = new ArrayList<>();
    private final TrainCarts traincarts;
    private final Task cleanupTask;
    private boolean disabled = false;

    public PlayerPacketListenerProviderImpl(TrainCarts traincarts) {
        this.traincarts = traincarts;
        this.cleanupTask = new Task(traincarts) {
            @Override
            public void run() {
                synchronized (PlayerPacketListenerProviderImpl.this) {
                    // We only need to store these for at most a tick, so this is fine
                    // Just avoid wasting memory...
                    recentlyQuitPlayers.clear();

                    // Delete type set listeners that are inactive
                    activeTypeSetListeners.values().removeIf(TypeSetListener::isTerminated);
                }
            }
        };
    }

    @Override
    public synchronized <L extends PacketListener> PlayerPacketListener<L> create(Player player, L packetListener, PacketType... packetTypes) {
        if (disabled || player == null || (!player.isValid() && Bukkit.getPlayer(player.getUniqueId()) != player)) {
            return PlayerPacketListener.createNoOp(player, packetListener);
        }

        // The above check is probably sufficient, but we want to be very sure that no more listeners are
        // created for players that have quit the server.
        if (!recentlyQuitPlayers.isEmpty()) {
            for (Player p : recentlyQuitPlayers) {
                if (p == player) {
                    return PlayerPacketListener.createNoOp(player, packetListener);
                }
            }
        }

        // Convert to set
        Set<PacketType> packetTypesSet = new HashSet<>(Arrays.asList(packetTypes));

        // Instantiate a new listener for these packets if non exists yet, or the
        // last one that existed has already terminated automatically
        TypeSetListener typeSetListener = activeTypeSetListeners.compute(packetTypesSet,
                (packets, existing) -> {
                    if (existing == null || existing.isTerminated()) {
                        return new TypeSetListener(traincarts, packets);
                    } else {
                        return existing;
                    }
                });
        return typeSetListener.addListener(player, packetListener);
    }

    @Override
    public synchronized void enable() {
        disabled = false;

        // Every 10 seconds clean up type-set packet listeners that have terminated.
        // Once terminated they already take up little resources, so this is not too important.
        cleanupTask.start(CLEANUP_INTERVAL, CLEANUP_INTERVAL);

        // Keep track of when players log off and clean up all the garbage stored for them, then
        // This might result in a miss though, as someone could be adding a listener right
        // as the player quit. We clean that up as part of the 10 second cleanup routine.
        traincarts.register(new Listener() {
            @EventHandler(priority = EventPriority.MONITOR)
            public void onPlayerQuit(PlayerQuitEvent event) {
                recentlyQuitPlayers.add(event.getPlayer());

                activeTypeSetListeners.values().forEach(l -> l.onPlayerQuit(event.getPlayer()));
            }
        });
    }

    @Override
    public synchronized void disable() {
        disabled = true;
        cleanupTask.stop();
    }

    private static class TypeSetListener implements PacketListener {
        private final TrainCarts traincarts;
        private final Multimap<Player, PlayerPacketListenerImpl<?>> packetListeners = Multimaps.newMultimap(
                new IdentityHashMap<>(), ArrayList::new);
        private Map<Player, List<PlayerPacketListenerImpl<?>>> packetListenersVisible = Collections.emptyMap();
        private final RunOnceTask checkTerminated;
        private boolean terminated;

        public TypeSetListener(TrainCarts traincarts, Set<PacketType> packetTypes) {
            this.traincarts = traincarts;
            this.checkTerminated = RunOnceTask.create(traincarts, this::tryTerminateIfEmpty);
            this.terminated = false;

            // Little ugly but start listening for these packets right away
            traincarts.register(this, packetTypes.toArray(new PacketType[0]));
        }

        private synchronized void tryTerminateIfEmpty() {
            if (!terminated && packetListeners.isEmpty()) {
                terminate();
            }
        }

        public synchronized void terminate() {
            terminated = true;
            packetListeners.values().forEach(PlayerPacketListenerImpl::setStateTerminated);
            packetListeners.clear();
            packetListenersVisible = Collections.emptyMap();
            traincarts.unregister(this);
            checkTerminated.cancel();
        }

        public synchronized boolean isTerminated() {
            return terminated;
        }

        public synchronized void terminateListener(PlayerPacketListenerImpl<?> playerPacketListener) {
            if (this.terminated) {
                return;
            }

            final Player player = playerPacketListener.getPlayer();
            Collection<PlayerPacketListenerImpl<?>> listenersForPlayer = packetListeners.get(player);
            if (listenersForPlayer.remove(playerPacketListener)) {
                // Update visible map too
                updateVisiblePacketListeners(player, listenersForPlayer);

                // If now empty, schedule a task to deregister this packet listener later
                // if there still are no packet listeners installed.
                if (packetListeners.isEmpty()) {
                    checkTerminated.restart(10);
                }
            }
        }

        /**
         * Adds a new packet listener for these packets
         *
         * @param player Player
         * @param packetListener PacketListener
         * @return PacketListenerCancelToken to stop listening again.
         *         Returns null if this listener has terminated and a new one must be made.
         */
        public synchronized <L extends PacketListener> PlayerPacketListenerImpl<L> addListener(Player player, L packetListener) {
            final PlayerPacketListenerImpl<L> ppl = new PlayerPacketListenerImpl<>(this, player, packetListener);
            final Collection<PlayerPacketListenerImpl<?>> newListeners = packetListeners.get(player);
            newListeners.add(ppl);
            updateVisiblePacketListeners(player, newListeners);

            return ppl;
        }

        private void updateVisiblePacketListeners(Player player, Collection<PlayerPacketListenerImpl<?>> newValues) {
            Map<Player, List<PlayerPacketListenerImpl<?>>> map = new IdentityHashMap<>(packetListenersVisible);
            if (newValues.isEmpty()) {
                map.remove(player);
            } else {
                map.put(player, new ArrayList<>(newValues));
            }
            packetListenersVisible = map;
        }

        /**
         * Disables all packet listeners for a player and handles cleanup. This assumes the player
         * connection has died, so no packets will be received (or sent) anymore.
         *
         * @param player Player
         */
        public synchronized void onPlayerQuit(Player player) {
            // Disable all listeners for this player
            Collection<PlayerPacketListenerImpl<?>> listeners = packetListeners.removeAll(player);
            listeners.forEach(PlayerPacketListenerImpl::setStateTerminated);
            updateVisiblePacketListeners(player, Collections.emptyList());

            // Schedule cleanup if now empty
            if (packetListeners.isEmpty()) {
                checkTerminated.restart(10);
            }
        }

        private Iterable<PlayerPacketListenerImpl<?>> iterateListenersFor(Player player) {
            return packetListenersVisible.getOrDefault(player, Collections.emptyList());
        }

        @Override
        public void onPacketReceive(PacketReceiveEvent event) {
            for (PlayerPacketListenerImpl<?> playerPacketListener : iterateListenersFor(event.getPlayer())) {
                if (playerPacketListener.isEnabled()) {
                    playerPacketListener.getListener().onPacketReceive(event);
                }
            }
        }

        @Override
        public void onPacketSend(PacketSendEvent event) {
            for (PlayerPacketListenerImpl<?> playerPacketListener : iterateListenersFor(event.getPlayer())) {
                if (playerPacketListener.isEnabled()) {
                    playerPacketListener.getListener().onPacketSend(event);
                }
            }
        }
    }

    private static final class PlayerPacketListenerImpl<L extends PacketListener> implements PlayerPacketListener<L> {
        private final AtomicReference<State> state = new AtomicReference<>(State.DISABLED);
        private final TypeSetListener typeSetListener;
        private final Player player;
        private final L packetListener;

        public PlayerPacketListenerImpl(TypeSetListener typeSetListener, Player player, L packetListener) {
            this.typeSetListener = typeSetListener;
            this.player = player;
            this.packetListener = packetListener;
        }

        @Override
        public Player getPlayer() {
            return player;
        }

        @Override
        public L getListener() {
            return packetListener;
        }

        @Override
        public boolean isEnabled() {
            return state.get() == State.ENABLED;
        }

        @Override
        public PlayerPacketListener<L> enable() {
            state.compareAndSet(State.DISABLED, State.ENABLED);
            return this;
        }

        @Override
        public PlayerPacketListener<L> disable() {
            state.compareAndSet(State.ENABLED, State.DISABLED);
            return this;
        }

        public void setStateTerminated() {
            state.set(State.TERMINATED);
        }

        @Override
        public void terminate() {
            if (state.getAndSet(State.TERMINATED) != State.TERMINATED) {
                typeSetListener.terminateListener(this);
            }
        }

        private enum State {
            DISABLED,
            ENABLED,
            TERMINATED
        }
    }
}
