package com.bergerkiller.bukkit.tc.attachments.api;

import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.internal.CommonCapabilities;
import com.bergerkiller.bukkit.common.protocol.PacketListener;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.player.network.PlayerClientSynchronizer;
import com.bergerkiller.bukkit.tc.controller.player.network.PlayerPacketListener;
import com.bergerkiller.bukkit.common.math.Quaternion;

import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayInSteerVehicleHandle;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.controller.VehicleMountController;
import com.bergerkiller.bukkit.common.protocol.CommonPacket;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.generated.net.minecraft.network.protocol.PacketHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityMetadataHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutNamedEntitySpawnHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutSpawnEntityLivingHandle;

import me.m56738.smoothcoasters.api.NetworkInterface;
import org.bukkit.util.Vector;

import java.util.Iterator;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Represents a Player that views train attachments. Can be used as a
 * key in a hashmap / hashset, where multiple viewer instances of the
 * same Player are considered equal.<br>
 * <br>
 * Includes optimized methods of sending (many) packets to the viewer.
 */
public interface AttachmentViewer extends TrainCarts.Provider {

    /**
     * Gets the Player this viewer is
     *
     * @return player
     */
    Player getPlayer();

    /**
     * Gets the name of this Player viewer
     *
     * @return Player name
     */
    default String getName() {
        return getPlayer().getName();
    }

    @Override
    TrainCarts getTrainCarts();

    /**
     * Gets the Vehicle Mount Controller of this viewer. This is used to
     * mount and unmount entities into one another for a player.
     *
     * @return vehicle mount controller
     */
    VehicleMountController getVehicleMountController();

    /**
     * Gets the smooth coasters network interface. This can be passed to
     * smoothcoasters API method calls to use this viewer's send methods.
     * May return null if the default network interface should be used.<br>
     * <br>
     * This network interface should <b>only</b> be used for this viewer!
     *
     * @return smooth coasters network interface
     */
    me.m56738.smoothcoasters.api.NetworkInterface getSmoothCoastersNetwork();

    /**
     * Sends a packet to this viewer
     *
     * @param packet
     */
    void send(CommonPacket packet);

    /**
     * Sends a packet to this viewer
     *
     * @param packet
     */
    void send(PacketHandle packet);

    /**
     * Sends a packet to this viewer, bypassing packet listeners like ProtocolLib
     *
     * @param packet
     */
    void sendSilent(CommonPacket packet);

    /**
     * Sends a packet to this viewer, bypassing packet listeners like ProtocolLib
     *
     * @param packet
     */
    void sendSilent(PacketHandle packet);

    /**
     * Sends an EntityLiving spawn packet with entity metadata
     *
     * @param packet
     * @param metadata
     */
    @SuppressWarnings("deprecation")
    default void sendEntityLivingSpawnPacket(PacketPlayOutSpawnEntityLivingHandle packet, DataWatcher metadata) {
        if (packet.hasDataWatcherSupport()) {
            packet.setDataWatcher(metadata);
            send(packet);
        } else {
            send(packet);
            send(PacketPlayOutEntityMetadataHandle.createNew(packet.getEntityId(), metadata, true));
        }
    }

    /**
     * Sends the spawn packet for a named entity. On MC 1.15 and later the metadata for the entity is sent separate
     * from the spawn packet of the named entity.
     *
     * @param packet
     * @param metadata
     */
    @SuppressWarnings("deprecation")
    default void sendNamedEntitySpawnPacket(PacketPlayOutNamedEntitySpawnHandle packet, DataWatcher metadata) {
        if (packet.hasDataWatcherSupport()) {
            packet.setDataWatcher(metadata);
            send(packet);
        } else {
            send(packet);
            send(PacketPlayOutEntityMetadataHandle.createNew(packet.getEntityId(), metadata, true));
        }
    }

    /**
     * Sends a Team packet to this player that disables collision caused by an Entity
     *
     * @param entityUUID UUID of the Entity
     */
    default void sendDisableCollision(UUID entityUUID) {
        getTrainCarts().getTeamProvider().noCollisionTeam().join(this, entityUUID);
    }

    /**
     * Sends a Team packet to this player that disables collision caused by
     * a number of entities.
     *
     * @param entityUUIDs UUID of the Entities to disable collision for
     */
    default void sendDisableCollision(Iterable<UUID> entityUUIDs) {
        getTrainCarts().getTeamProvider().noCollisionTeam().join(this, entityUUIDs);
    }

    /**
     * Gets whether the Player is logged on and has a living Entity on a World.
     * If the player is respawning, this returns False. If it's important to know
     * whether the player logged off, use {@link #isConnected()} instead.
     *
     * @return True if the Player is alive and connected
     */
    default boolean isValid() {
        return getPlayer().isValid();
    }

    /**
     * Gets whether the Player is still connected to the server, and has joined it
     * (past the login phase). This also returns True if the player is still connected
     * but is dead and on the respawn screen.
     *
     * @return True if the Player is still connected to the server and has joined it
     */
    default boolean isConnected() {
        Player player = getPlayer();
        return player.isValid() || Bukkit.getPlayer(player.getUniqueId()) == player;
    }

    /**
     * Gets the PlayerClientSynchronizer instance for a Player. This is used to send packets
     * to the client, and wait for the client to acknowledge them. Then a callback is called
     * on the server end.<br>
     * <br>
     * This is primarily useful to send position updates to the client and know when those
     * have been applied. This accounts for (large) client latency.
     *
     * @return PlayerClientSynchronizer
     */
    default PlayerClientSynchronizer getClientSynchronizer() {
        return getTrainCarts().getPlayerClientSynchronizerProvider().forPlayer(getPlayer());
    }

    /**
     * Creates a packet listener, for this Player viewer only.
     * Must call {@link PlayerPacketListener#enable()} before it is active.
     * Can call disable to temporarily stop it, and terminate to shut down the packet
     * listener forever. Is automatically stopped when this player quits the server.
     *
     * @param packetListener PacketListener
     * @param packetTypes PacketTypes to listen for (receive OR send)
     * @return PlayerPacketListener to enable/disable the listener. Also grants access
     *         to the original packet listener implementation passed in.
     * @param <L> PacketListener implementation type
     */
    default <L extends PacketListener> PlayerPacketListener<L> createPacketListener(L packetListener, PacketType... packetTypes) {
        TrainCarts trainCarts = getTrainCarts();
        return trainCarts.getPlayerPacketListenerProvider().create(
                        getPlayer(), packetListener, packetTypes);
    }

    /**
     * Gets the Entity ID of the Player
     *
     * @return Player entity ID
     */
    default int getEntityId() {
        return getPlayer().getEntityId();
    }

    /**
     * Evaluates a logical expression against the game version supported by this viewer.
     * Will make use of API's such as ViaVersion to detect the actual game version of the player.
     *
     * @param operand to evaluate (>, >=, ==, etc.)
     * @param rightSide value on the right side of the operand
     * @return True if the evaluation succeeds, False if not
     */
    default boolean evaluateGameVersion(String operand, String rightSide) {
        return PlayerUtil.evaluateGameVersion(getPlayer(), operand, rightSide);
    }

    /**
     * Gets whether this viewer, and the server itself, can support the use of display entities.
     * If true, a display entity can be spawned for this player. If false, armorstands should be used.
     *
     * @return True if the display entity is supported by this viewer
     */
    default boolean supportsDisplayEntities() {
        return CommonCapabilities.HAS_DISPLAY_ENTITY && evaluateGameVersion(">=", "1.19.4");
    }

    /**
     * Gets whether this viewer, and the server itself, can support the use of display entities
     * and teleporting them smoothly with interpolation. This is since 1.20.2.
     *
     * @return True if display entity location interpolation is supported for this client
     */
    default boolean supportsDisplayEntityLocationInterpolation() {
        return CommonCapabilities.HAS_DISPLAY_ENTITY_LOCATION_INTERPOLATION && evaluateGameVersion(">=", "1.20.2");
    }

    /**
     * Gets whether this viewer, and the server itself, is capable of sending a relative camera rotation
     * update to this player. Minecraft 1.21.2 - 1.21.8 do not support this.
     *
     * @return True if relative camera rotation updates are supported
     */
    default boolean supportRelativeRotationUpdate() {
        return (Common.evaluateMCVersion("<", "1.21.2") || Common.evaluateMCVersion(">=", "1.21.9")) &&
                (evaluateGameVersion("<", "1.21.2") || evaluateGameVersion(">=", "1.21.9"));
    }

    /**
     * Gets the offset at which a player sits on a surface. Mounts must be adjusted to take this
     * into account.
     *
     * @return ArmorStand butt offset. Game version-dependent.
     */
    default double getArmorStandButtOffset() {
        return evaluateGameVersion(">=", "1.20.2") ? 0.0 : 0.27;
    }

    /**
     * Resets the glow color of an entity
     *
     * @param entityUUID Entity UUID
     */
    default void resetGlowColor(UUID entityUUID) {
        getTrainCarts().getGlowColorTeamProvider().reset(this, entityUUID);
    }

    /**
     * Sets a glow color for an entity
     *
     * @param entityUUID Entity UUID
     * @param color Desired color. Null to reset.
     */
    default void updateGlowColor(UUID entityUUID, ChatColor color) {
        getTrainCarts().getGlowColorTeamProvider().update(this, entityUUID, color);
    }

    /**
     * Sets a glow color for an entity
     *
     * @param entityUUIDs Entity UUIDs
     * @param color Desired color. Null to reset.
     */
    default void updateGlowColor(Iterable<UUID> entityUUIDs, ChatColor color) {
        getTrainCarts().getGlowColorTeamProvider().update(this, entityUUIDs, color);
    }

    /**
     * Obtains the AttachmentViewer implementation best suited for a Player.
     * If TrainCarts is enabled and the player is still online, uses the TrainCarts
     * implementation with optimized APIs. Otherwise uses the
     * {@link #fallback(Player)} implementation.
     *
     * @param player Player
     * @return AttachmentViewer
     */
    static AttachmentViewer forPlayer(final Player player) {
        TrainCarts trainCarts = TrainCarts.plugin;
        if (trainCarts != null && trainCarts.isEnabled()) {
            return trainCarts.getAttachmentViewer(player);
        } else {
            return fallback(player);
        }
    }

    /**
     * Starts controlling the movement of this viewer. This causes the player to lose direct
     * control over W/A/S/D movement controls, and allows the caller to send new positions
     * to this player. The player is smoothly moved to these new positions.<br>
     * <br>
     * If someone else was controlling movement before, their control is ended.
     *
     * @return MovementController
     */
    default MovementController controlMovement() {
        return controlMovement(MovementController.Options.create());
    }

    /**
     * Starts controlling the movement of this viewer. This causes the player to lose direct
     * control over W/A/S/D movement controls, and allows the caller to send new positions
     * to this player. The player is smoothly moved to these new positions.<br>
     * <br>
     * If someone else was controlling movement before, their control is ended.
     *
     * @param options Extra options to configure movement controller behavior
     * @return MovementController
     */
    default MovementController controlMovement(MovementController.Options options) {
        TrainCarts plugin = getTrainCarts();
        if (plugin.isEnabled() && isConnected()) {
            return plugin.getAttachmentViewer(getPlayer()).controlMovement(options);
        } else {
            return MovementController.DISABLED;
        }
    }

    /**
     * If someone had called {@link #controlMovement()} before, stops this controller.
     */
    default void stopControllingMovement() {
        TrainCarts plugin = getTrainCarts();
        if (plugin.isEnabled()) {
            plugin.getAttachmentViewer(getPlayer()).stopControllingMovement();
        }
    }

    /**
     * Obtains a fallback AttachmentViewer implementation, to be used when only
     * a Player input is provided.
     *
     * @param player
     * @return AttachmentViewer
     */
    static AttachmentViewer fallback(final Player player) {
        return new AttachmentViewer() {
            @Override
            public TrainCarts getTrainCarts() {
                return TrainCarts.plugin;
            }

            @Override
            public Player getPlayer() {
                return player;
            }

            @Override
            public VehicleMountController getVehicleMountController() {
                return PlayerUtil.getVehicleMountController(player);
            }

            @Override
            public NetworkInterface getSmoothCoastersNetwork() {
                return null; // Let the API decide
            }

            @Override
            public void send(CommonPacket packet) {
                PacketUtil.sendPacket(player, packet);
            }

            @Override
            public void send(PacketHandle packet) {
                PacketUtil.sendPacket(player, packet);
            }

            @Override
            public void sendSilent(CommonPacket packet) {
                PacketUtil.sendPacket(player, packet, false);
            }

            @Override
            public void sendSilent(PacketHandle packet) {
                PacketUtil.sendPacket(player, packet, false);
            }

            @Override
            public int hashCode() {
                return player.hashCode();
            }

            @Override
            public boolean equals(Object o) {
                if (o == this) {
                    return true;
                } else if (o instanceof AttachmentViewer) {
                    return ((AttachmentViewer) o).getPlayer() == player;
                } else {
                    return false;
                }
            }
        };
    }

    /**
     * Adapts a Player Iterable and calls {@link #fallback(Player)} on every element.
     *
     * @param players Player Iterable
     * @return Iterable of Attachment Viewers
     */
    public static Iterable<AttachmentViewer> fallbackIterable(Iterable<Player> players) {
        return () -> new Iterator<AttachmentViewer>() {
            private final Iterator<Player> baseIter = players.iterator();

            @Override
            public boolean hasNext() {
                return baseIter.hasNext();
            }

            @Override
            public AttachmentViewer next() {
                return fallback(baseIter.next());
            }

            @Override
            public void remove() {
                baseIter.remove();
            }

            @Override
            public void forEachRemaining(Consumer<? super AttachmentViewer> action) {
                baseIter.forEachRemaining(p -> action.accept(fallback(p)));
            }
        };
    }

    /**
     * An active movement controller set on this viewer
     */
    interface MovementController {
        /** A disabled no-op movement controller */
        MovementController DISABLED = new MovementController() {
            @Override
            public void stop() {
            }

            @Override
            public boolean hasStopped() {
                return true;
            }

            @Override
            public Input getInput() {
                return Input.NONE;
            }

            @Override
            public void update(Vector position, Quaternion orientation) {
            }
        };

        /**
         * Stops this movement controller. The player will regain control over their
         * own character.
         */
        void stop();

        /**
         * Gets whether {@link #stop()} was called, the player has logged off or someone
         * else took over movement control. If stopped, further updates
         * have no effect.
         *
         * @return True if this movement has controlled has stopped
         */
        boolean hasStopped();

        /**
         * Gets the latest input received from the player while controlling it. If this
         * controller {@link #hasStopped()}, returns no more inputs.
         *
         * @return Latest Input snapshot state from the player
         */
        Input getInput();

        /**
         * Sends a new player position and look-orientation to the player. The player
         * will be updated to move to this new position and have its look-orientation
         * updated to reflect the new orientation. The orientation will be updated
         * relatively: the change in orientation is kept track of and that is
         * synchronized to the player.
         *
         * @param position New position for the Player viewer
         * @param orientation New look-orientation for the Player viewer.
         *                    Use <i>null</i> to leave look-orientation untouched.
         */
        void update(Vector position, Quaternion orientation);

        /**
         * Sends a new player position to the player. Look-orientation is not updated
         * and kept to the value of whatever it was before.
         *
         * @param position New position for the Player viewer
         */
        default void update(Vector position) {
            update(position, null);
        }

        /**
         * Options for movement control
         */
        final class Options {
            private boolean preserveInput = false;
            private boolean syncAsArmorStand = true;

            /**
             * Creates new MovementController Options with the default configuration
             *
             * @return New Options
             */
            public static Options create() {
                return new Options();
            }

            private Options() {
            }

            /**
             * Sets whether to preserve the original input from the Player. Is false by default.
             * If true, then the controls from the Player will still be handled by the server
             * as if they are steering controls / player inputs.
             *
             * @param preserve True to preserve player input as steering controls
             * @return this
             */
            public Options preserveInput(boolean preserve) {
                preserveInput = preserve;
                return this;
            }

            /**
             * Sets whether position updates are synchronized as if the player is an Armorstand entity.
             * This makes it so that the player moves in sync with surrounding armor stand entities.
             * This is true by default.
             *
             * @param sync Whether to sync as armorstand. True by default.
             * @return this
             */
            public Options syncAsArmorstand(boolean sync) {
                syncAsArmorStand = sync;
                return this;
            }

            /**
             * Gets the current setting of {@link #preserveInput(boolean)}
             *
             * @return Whether player input is preserved as steering controls
             */
            public boolean isPreserveInput() {
                return preserveInput;
            }

            /**
             * Gets the current setting of {@link #syncAsArmorstand(boolean)}
             *
             * @return True if position updates are re-interpolated as armorstand movement
             */
            public boolean isSyncAsArmorStand() {
                return syncAsArmorStand;
            }
        }
    }

    /**
     * Snapshot state of the input state received from a player. On older versions
     * of Minecraft this reflects the steering controls.
     */
    final class Input {
        /** No input (all false) */
        public static final Input NONE = of(false, false, false, false, false, false, false);

        private final boolean left, right, forwards, backwards, jumping, sneaking, sprinting;

        public static Input of(boolean left, boolean right, boolean forwards, boolean backwards, boolean jumping, boolean sneaking, boolean sprinting) {
            return new Input(left, right, forwards, backwards, jumping, sneaking, sprinting);
        }

        public static Input fromVehicleSteer(PacketPlayInSteerVehicleHandle packet) {
            return of(packet.isLeft(), packet.isRight(), packet.isForward(), packet.isBackward(),
                    packet.isJump(), packet.isUnmount(), packet.isSprint());
        }

        private Input(boolean left, boolean right, boolean forwards, boolean backwards, boolean jumping, boolean sneaking, boolean sprinting) {
            this.left = left;
            this.right = right;
            this.forwards = forwards;
            this.backwards = backwards;
            this.jumping = jumping;
            this.sneaking = sneaking;
            this.sprinting = sprinting;
        }

        /**
         * Gets whether the player is strafing left (holding A)
         *
         * @return True if player is strafing left
         */
        public boolean left() {
            return left;
        }

        /**
         * Gets whether the player is strafing right (holding D)
         *
         * @return True if player is strafing right
         */
        public boolean right() {
            return right;
        }

        /**
         * Gets whether the player is walking forwards (holding W)
         *
         * @return True if player is walking forwards
         */
        public boolean forwards() {
            return forwards;
        }

        /**
         * Gets whether the player is walking backwards (holding S)
         *
         * @return True if player is walking backwards
         */
        public boolean backwards() {
            return backwards;
        }

        /**
         * Gets whether the player is jumping (holding spacebar)
         *
         * @return True if player is holding the jump key
         */
        public boolean jumping() {
            return jumping;
        }

        /**
         * Gets whether the player is sneaking (holding sneak button / exit vehicle button)
         *
         * @return True if player is holding the sneak key
         */
        public boolean sneaking() {
            return sneaking;
        }

        /**
         * Gets whether the player is sprinting (double-tapped W)
         *
         * @return True if player is sprinting
         */
        public boolean sprinting() {
            return sprinting;
        }

        /**
         * Gets whether any of the left/right/forwards/backwards keys are held
         * pressed by the player
         *
         * @return True if there is walking input
         */
        public boolean hasWalkInput() {
            return left || right || forwards || backwards;
        }

        /**
         * Gets whether the player is walking, and is strafing diagonally.
         * This means either forwards/backwards and a left/right input is active.
         * Does not check vertical motion (jump/sneak).
         *
         * @return True if walking diagonally
         */
        public boolean hasDiagonalWalkInput() {
            return (left != right) && (forwards != backwards);
        }

        /**
         * Returns 1.0 when moving {@link #left()}, -1.0 when moving {@link #right()}
         * and 0.0 if neither or both inputs are active.
         *
         * @return Sideways sig num [0.0, -1.0, 1.0]
         */
        public double sidewaysSigNum() {
            return (left == right) ? 0.0 : (left ? 1.0 : -1.0);
        }

        /**
         * Returns 1.0 when moving {@link #forwards()}, -1.0 when moving {@link #backwards()}
         * and 0.0 if neither or both inputs are active.
         *
         * @return Forwards sig num [0.0, -1.0, 1.0]
         */
        public double forwardsSigNum() {
            return (forwards == backwards) ? 0.0 : (forwards ? 1.0 : -1.0);
        }

        /**
         * Returns 1.0 when {@link #jumping()}, -1.0 when {@link #sneaking()}
         * and 0.0 if neither or both inputs are active.
         *
         * @return Vertical input sig num [0.0, -1.0, 1.0]
         */
        public double verticalSigNum() {
            return (jumping == sneaking) ? 0.0 : (jumping ? 1.0 : -1.0);
        }

        /**
         * Returns a new Input with the {@link #sprinting()} value changed
         * to a new one.
         *
         * @param newSprinting New sprinting value
         * @return Updated Input
         */
        public Input withSprinting(boolean newSprinting) {
            return of(left, right, forwards, backwards, jumping, sneaking, newSprinting);
        }

        /**
         * Creates a new vehicle steer packet with these inputs. This packet can be
         * received on the server and treated as (fake) input from the player.
         *
         * @return New PacketPlayInSteerVehicleHandle
         */
        public PacketPlayInSteerVehicleHandle createSteerPacket() {
            return PacketPlayInSteerVehicleHandle.createNew(
                    left, right, forwards, backwards,
                    jumping, sneaking, sprinting);
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            } else if (o instanceof Input) {
                Input other = (Input) o;
                return left == other.left && right == other.right &&
                        forwards == other.forwards && backwards == other.backwards &&
                        jumping == other.jumping && sneaking == other.sneaking &&
                        sprinting == other.sprinting;
            } else {
                return false;
            }
        }

        @Override
        public String toString() {
            StringBuilder str = new StringBuilder();
            str.append("Input{");
            appendIf(str, left, "left ");
            appendIf(str, right, "right ");
            appendIf(str, forwards, "forwards ");
            appendIf(str, backwards, "backwards ");
            appendIf(str, jumping, "jumping ");
            appendIf(str, sneaking, "sneaking ");
            appendIf(str, sprinting, "sprinting ");
            if (str.charAt(str.length() - 1) == ' ') {
                str.replace(str.length() - 1, str.length(), "");
            }
            str.append("}");
            return str.toString();
        }

        private static void appendIf(StringBuilder str, boolean condition, String text) {
            if (condition) {
                str.append(text);
            }
        }
    }
}
