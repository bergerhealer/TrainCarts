package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.permissions.PermissionEnum;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignBuildEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.pathfinding.PathNode;
import com.bergerkiller.bukkit.tc.pathfinding.PathPredictEvent;
import com.bergerkiller.bukkit.tc.pathfinding.SignRoutingEvent;
import com.bergerkiller.bukkit.tc.rails.RailLookup;
import com.bergerkiller.bukkit.tc.rails.RailLookup.TrackedSign;
import com.bergerkiller.bukkit.tc.utils.SignBuildOptions;

import com.bergerkiller.generated.org.bukkit.block.SignHandle;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.block.SignChangeEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

public abstract class SignAction {
    private static List<SignAction> actions = Collections.emptyList();
    private static List<SignAction> actionsWithLoadedChangedHandler = Collections.emptyList();

    public static void init() {
        actions = new ArrayList<>();
        actionsWithLoadedChangedHandler = new ArrayList<>();
        register(new SignActionStation());
        register(new SignActionLauncher());
        register(new SignActionSwitcher());
        register(new SignActionSpawn());
        register(new SignActionBlockChanger());
        register(new SignActionProperties());
        register(new SignActionTrigger());
        register(new SignActionTeleport());
        register(new SignActionJumper());
        register(new SignActionEject());
        register(new SignActionEnter());
        register(new SignActionDestroy());
        register(new SignActionTransfer());
        register(new SignActionFuel());
        register(new SignActionCraft());
        register(SignActionDetector.INSTANCE);
        register(new SignActionDestination());
        register(new SignActionBlocker());
        register(new SignActionWait());
        register(new SignActionElevator());
        register(new SignActionTicket());
        register(new SignActionAnnounce());
        register(new SignActionEffect());
        register(new SignActionBukkitEffect());
        register(new SignActionSound());
        register(new SignActionSkip());
        register(new SignActionMutex());
        register(new SignActionPathingMutex());
        register(new SignActionFlip());
        register(new SignActionAnimate());

        if (Common.evaluateMCVersion(">=", "1.11")) {
            register(new SignActionTitle());
        }
    }

    public static void deinit() {
        actions = Collections.emptyList();
        actionsWithLoadedChangedHandler = Collections.emptyList();
    }

    /**
     * Obtains the SignAction meant for a SignActionEvent
     *
     * @param event to check
     * @return sign action, or null if not found
     */
    public static SignAction getSignAction(SignActionEvent event) {
        for (SignAction action : actions) {
            if (action.match(event) && action.verify(event)) {
                return action;
            }
        }
        return null;
    }

    /**
     * Registers a new SignAction, which will then be used by trains discovering signs matching
     * its format. Priority will be false, meaning it will not override previously registered
     * sign actions.
     * 
     * @param action    The sign action instance that represents the sign
     * @return input action
     */
    public static <T extends SignAction> T register(T action) {
        return register(action, false);
    }

    /**
     * Registers a new SignAction, which will then be used by trains discovering signs matching
     * its format.
     * 
     * @param action    The sign action instance that represents the sign
     * @param priority  True to have this action override previously registered signs, False otherwise
     * @return input action
     * @throws NullPointerException If the input action is null
     */
    public static <T extends SignAction> T register(T action, boolean priority) {
        if (action == null) {
            throw new NullPointerException("Action is null");
        }
        if (actions != Collections.EMPTY_LIST) {
            if (priority) {
                actions.add(0, action);
            } else {
                actions.add(action);
            }

            // If action implements loadedChanged(), also add it to the list of actions
            // with such a handler
            if (CommonUtil.isMethodOverrided(SignAction.class, action.getClass(), "loadedChanged", SignActionEvent.class, boolean.class)) {
                if (priority) {
                    actionsWithLoadedChangedHandler.add(0, action);
                } else {
                    actionsWithLoadedChangedHandler.add(action);
                }
            }

            // TrackedSign stores a SignAction too - make sure this is wiped
            RailLookup.forceRecalculation();
        }
        return action;
    }

    public static void unregister(SignAction action) {
        if (actions.isEmpty()) return;
        actions.remove(action);
        actionsWithLoadedChangedHandler.remove(action);
    }

    /**
     * Handles a change in the loaded change of a sign.
     * Sign Actions bound to this sign will have their {@link #loadedChanged(SignActionEvent, boolean)} called.
     * 
     * @param sign that was loaded/unloaded
     * @param frontText Whether to track the front (true) or back (false, >= MC 1.20 only)
     * @param loaded state change
     */
    public static void handleLoadChange(Sign sign, boolean frontText, boolean loaded) {
        // Initially use a NONE rail piece type when trying to match a sign action
        // This avoids having to look up the rails for all signs on the server...
        // If a SignAction needs rail info for whatever reason in loadedChanged(), then
        // that sign action should use getRail() or get a NPE.
        TrackedSign trackedSign = TrackedSign.forRealSign(sign, frontText, RailPiece.NONE);
        trackedSign.rail = null; // Forces discovery of rail later
        handleLoadChange(trackedSign, loaded);
    }

    /**
     * Handles a change in the loaded change of a sign.
     * Sign Actions bound to this sign will have their {@link #loadedChanged(SignActionEvent, boolean)} called.
     *
     * @param trackedSign TrackedSign that was loaded/unloaded
     * @param loaded state change
     */
    public static void handleLoadChange(TrackedSign trackedSign, boolean loaded) {
        final SignActionEvent info = new SignActionEvent(trackedSign);
        for (SignAction action : actionsWithLoadedChangedHandler) {
            if (action.match(info) && action.verify(info)) {
                action.loadedChanged(info, loaded);
                return;
            }
        }
    }

    /**
     * Handles right-click interaction with a Sign
     *
     * @param clickedSign that was right-clicked
     * @param player      that clicked
     * @return Whether the click was handled (and the original interaction should be cancelled)
     */
    public static boolean handleClick(Block clickedSign, Player player) {
        Sign bsign = BlockUtil.getSign(clickedSign);
        if (bsign == null) {
            return false;
        }

        SignHandle bsignhandle = SignHandle.createHandle(bsign);
        if (!bsignhandle.getFrontLine(0).isEmpty()) {
            if (handleClick(TrackedSign.forRealSign(bsign, clickedSign, true, null), player)) {
                return true;
            }
        }
        if (!bsignhandle.getBackLine(0).isEmpty()) {
            if (handleClick(TrackedSign.forRealSign(bsign, clickedSign, false, null), player)) {
                return true;
            }
        }
        return false;
    }

    private static boolean handleClick(TrackedSign clickedSign, Player player) {
        SignActionEvent info = new SignActionEvent(clickedSign);
        SignAction action = getSignAction(info);
        return action != null && action.click(info, player);
    }

    /**
     * @deprecated please use {@link SignBuildOptions} for this instead
     */
    @Deprecated
    public static boolean handleBuild(SignChangeActionEvent event, PermissionEnum permission, String signname) {
        return handleBuild(event, permission, signname, null);
    }

    /**
     * @deprecated please use {@link SignBuildOptions} for this instead
     */
    @Deprecated
    public static boolean handleBuild(SignChangeActionEvent event, PermissionEnum permission, String signname, String signdescription) {
        return SignBuildOptions.create()
                .setPermission(permission)
                .setName(signname)
                .setDescription(signdescription)
                .handle(event.getPlayer());
    }

    /**
     * Handles the building of a sign.
     *
     * @deprecated Please use {@link #handleBuild(SignBuildEvent)}
     * @param info SignChangeActionEvent
     */
    @Deprecated
    public static void handleBuild(SignChangeActionEvent info) {
        handleBuild(new SignBuildEvent(info));
    }

    /**
     * Handles the building of a sign.
     *
     * @deprecated Please use {@link #handleBuild(SignBuildEvent)}
     * @param event SignChangeEvent
     */
    @Deprecated
    public static void handleBuild(SignChangeEvent event) {
        handleBuild(new SignBuildEvent(event, true));
    }

    public static void handleBuild(SignBuildEvent info) {
        // Let other plugins see the build event and allow them to cancel it
        CommonUtil.callEvent(info);

        // If cancelled by a plugin, abort
        if (info.isCancelled()) {
            return;
        }

        if (info.hasRegisteredAction()) {
            SignAction action = info.getRegisteredAction();
            if (!info.getTrackedSign().isRealSign() && !action.canSupportFakeSign(info)) {
                info.getPlayer().sendMessage(ChatColor.RED + "A real sign is required for this type of action");
                info.setCancelled(true);
                return;
            }

            // Allow SignAction to cancel the building of the sign too
            if (!action.build(info)) {
                info.setCancelled(true);
                return;
            }

            // Inform about use of RC when not supported
            // Deny building the sign with RC if player lacks permission for this
            if (action.canSupportRC()) {
                if (info.isRCSign() && !Permission.BUILD_REMOTE_CONTROL.has(info.getPlayer())) {
                    Localization.SIGN_NO_RC_PERMISSION.message(info.getPlayer());
                    info.getHeader().setMode(SignActionMode.TRAIN);
                    info.setLine(0, info.getHeader().toString());
                }
            } else if (info.isRCSign()) {
                info.getPlayer().sendMessage(ChatColor.RED + "This sign does not support remote control!");
                info.getHeader().setMode(SignActionMode.TRAIN);
                info.setLine(0, info.getHeader().toString());
            }

            // For signs that define path finding destinations, report about duplicate names
            String destinationName = action.getRailDestinationName(info);
            if (destinationName != null) {
                PathNode node = info.getTrainCarts().getPathProvider().getWorld(info.getWorld()).getNodeByName(destinationName);
                if (node != null) {
                    Player p = info.getPlayer();
                    p.sendMessage(ChatColor.RED + "Another destination with the same name already exists!");
                    p.sendMessage(ChatColor.RED + "Please remove either sign and use /train reroute to fix");

                    // Send location message
                    BlockLocation loc = node.location;
                    StringBuilder locMsg = new StringBuilder(100);
                    locMsg.append(ChatColor.RED).append("Other destination '" + destinationName + "' is ");
                    if (loc.getWorld() != info.getPlayer().getWorld()) {
                        locMsg.append("on world ").append(ChatColor.WHITE).append(node.location.world);
                        locMsg.append(' ').append(ChatColor.RED);
                    }
                    locMsg.append("at ").append(ChatColor.WHITE);
                    locMsg.append('[').append(loc.x).append('/').append(loc.y);
                    locMsg.append('/').append(loc.z).append(']');
                    p.sendMessage(locMsg.toString());
                }
            }

            // Tell train above to update signs, if available
            if (info.hasRails()) {
                for (MinecartMember<?> member : info.getRailPiece().members()) {
                    if (!member.isUnloaded() && !member.getEntity().isRemoved()) {
                        member.getGroup().getSignTracker().updatePosition();
                    }
                }
            }

            // Call loaded
            action.loadedChanged(info, true);
        }

        // Snap to fixed 45-degree angle
        if (info.getMode() != SignActionMode.NONE && info.getTrackedSign().isRealSign()) {
            BlockData data = WorldUtil.getBlockData(info.getBlock());
            if (MaterialUtil.ISSIGN.get(data) && FaceUtil.isVertical(data.getAttachedFace())) {
                BlockFace oldFacing = data.getFacingDirection();
                BlockFace newFacing = Util.snapFace(oldFacing);
                if (oldFacing != newFacing) {
                    BlockUtil.setFacing(info.getBlock(), newFacing);
                }
            }
        }
    }

    public static void handleDestroy(SignActionEvent info) {
        if (info == null || info.getSign() == null) {
            return;
        }
        SignAction action = getSignAction(info);
        if (action != null) {
            // First, remove this sign from all Minecarts on the world
            for (MinecartGroup group : MinecartGroup.getGroups().cloneAsIterable()) {
                group.getSignTracker().removeSign(info.getTrackedSign());
            }

            // Handle sign destroy logic
            // Check for things that are path finding - related first
            boolean switchable = action.isRailSwitcher(info);
            String destinationName = action.getRailDestinationName(info);
            action.destroy(info);

            // Remove (invalidate) the rails block, if part of path finding logic
            if (destinationName != null) {
                PathNode node = info.getTrainCarts().getPathProvider().getWorld(info.getWorld()).getNodeByName(destinationName);
                if (node != null) {
                    node.removeName(destinationName);
                }
            }
            if (switchable) {
                Block rails = info.getRails();
                if (rails != null) {
                    PathNode node = PathNode.get(rails);
                    if (node != null) {
                        node.remove();
                    }
                }
            }

            // Unloaded
            action.loadedChanged(info, false);
        }
    }

    public static void executeAll(SignActionEvent info, SignActionType actiontype) {
        info.setAction(actiontype);
        executeAll(info);
    }

    public static void executeAll(SignActionEvent info) {
        if (info == null || info.getSign() == null) {
            return;
        }

        //Event
        info.setCancelled(false);
        if (CommonUtil.callEvent(info).isCancelled()) {
            return; // ignore further processing
        }

        // Find matching SignAction for this sign
        executeOneImpl(getSignAction(info), info);
    }

    /**
     * Executes a specific SignAction that was previously decoded using
     * {@link #getSignAction(SignActionEvent)}. Before executing the SignAction,
     * the SignActionEvent is called as a Bukkit event, which can be cancelled.
     *
     * @param action SignAction to execute. If null, calls only the Bukkit event
     * @param info SignActionEvent to call and handle with the SignAction
     */
    public static void executeOne(SignAction action, SignActionEvent info) {
        if (info == null || info.getSign() == null) {
            return;
        }

        //Event
        info.setCancelled(false);
        if (CommonUtil.callEvent(info).isCancelled()) {
            return; // ignore further processing
        }

        // SignAction
        executeOneImpl(action, info);
    }

    private static void executeOneImpl(SignAction action, SignActionEvent info) {
        // Ignore if null (only event is fired)
        if (action == null) {
            return;
        }

        // Ignore MEMBER_MOVE if not handled
        if (info.isAction(SignActionType.MEMBER_MOVE) && !action.isMemberMoveHandled(info)) {
            return;
        }

        // If fake signs aren't supported, don't call the SignAction itself
        if (!info.getTrackedSign().isRealSign() && !action.canSupportFakeSign(info)) {
            return;
        }

        // When not facing the sign (unless overrided), do not process it
        if (!action.overrideFacing() && info.getAction().isMovement() && !info.isFacing()) {
            return;
        }

        // Actually execute it
        try {
            action.execute(info);
        } catch (Throwable t) {
            String signInfo;
            if (info.getTrackedSign().isRealSign()) {
                Block signBlock = info.getBlock();
                signInfo = signBlock.getWorld().getName() + " x=" + signBlock.getX() +
                        " y=" + signBlock.getY() + " z=" + signBlock.getZ();
            } else if (info.hasRails()) {
                Block railBlock = info.getRails();
                signInfo = info.getTrackedSign().getClass().getSimpleName() + " rail " +
                        railBlock.getWorld().getName() + " x=" + railBlock.getX() +
                        " y=" + railBlock.getY() + " z=" + railBlock.getZ();
            } else {
                signInfo = info.getTrackedSign().getClass().getSimpleName() + " key " +
                        info.getTrackedSign().getUniqueKey();
            }
            info.getTrainCarts().getLogger().log(Level.SEVERE, "Failed to execute " + info.getAction().toString() +
                    " for " + action.getClass().getSimpleName() + " at {" + signInfo + "}:", CommonUtil.filterStackTrace(t));
        }
    }

    private final boolean _hasPathPrediction;

    public SignAction() {
        this._hasPathPrediction = CommonUtil.isMethodOverrided(SignAction.class, this.getClass(), "predictPathFinding", SignActionEvent.class, PathPredictEvent.class);
    }

    /**
     * Verifies that a SignActionEvent covers a valid TrainCarts sign.
     * If you have a sign format that differs greatly from the TC format, override this method.
     * This function is called before handling sign building and sign action execution.
     * 
     * @param info input event
     * @return True if valid, False if not
     */
    public boolean verify(SignActionEvent info) {
        // Ignore actions that are not in the TC format
        if (!info.getHeader().isValid()) {
            return false;
        }

        // Check whether the action is actually valid for this type of sign
        // When only redstone-related events are allowed, toss out the non-redstone-related ones
        // When redstone-related events should never occur (always powered), ignore as well
        if (info.getHeader().isActionFiltered(info.getAction())) {
            return false;
        }

        return true;
    }

    /**
     * Checks whether a sign action event is meant for this type of Sign Action
     *
     * @param info event
     * @return True if it matched, False if not
     */
    public abstract boolean match(SignActionEvent info);

    /**
     * Fired when this sign is being executed for a certain event
     *
     * @param info event
     */
    public abstract void execute(SignActionEvent info);

    /**
     * Fired when a sign is being built
     *
     * @param event containing relevant Build information
     * @return True if building is allowed, False if not
     */
    public abstract boolean build(SignChangeActionEvent event);

    /**
     * Handles the post-destroy logic for when this sign is broken
     *
     * @param info related to the destroy event
     */
    public void destroy(SignActionEvent info) {
    }

    /**
     * Called when a sign becomes loaded (after placement or chunk loads), or unloaded (destroyed or when chunk unloads)
     * 
     * @param info for the sign
     * @param loaded state the sign changed to
     */
    public void loadedChanged(SignActionEvent info, boolean loaded) {
    }

    /**
     * Whether the remote control format is supported for this sign
     */
    public boolean canSupportRC() {
        return false;
    }

    /**
     * Gets whether this sign action supports fake signs. If true, it may be
     * executed with signs that don't have an actual physical sign block. If false,
     * then building the sign will fail with a message, and the sign will not be executed.
     *
     * @param info SignActionEvent for the fake sign
     * @return True if fake signs are supported
     */
    public boolean canSupportFakeSign(SignActionEvent info) {
        return true;
    }

    /**
     * Whether this sign overrides the internal facing check
     */
    public boolean overrideFacing() {
        return false;
    }

    /**
     * Whether this Sign Action handles {@link SignActionType#MEMBER_MOVE} for an event.
     * By default this is False, allowing for minor performance optimizations.
     * If <i>MEMBER_MOVE</i> must be handled, override this method and return True when appropriate.
     * 
     * @param info of the sign
     * @return True if member move is handled for the sign
     */
    public boolean isMemberMoveHandled(SignActionEvent info) {
        return false;
    }

    /**
     * Called when this sign is encountered while computing the path finding routing information.
     * Calls {@link #isRailSwitcher(SignActionEvent)}, {@link #getRailDestinationName(SignActionEvent)}
     * and {@link #isPathFindingBlocked(SignActionEvent, RailState)} by default. Can be overridden
     * for more advanced logic, such as path prediction past otherwise routed nodes.
     *
     * @param event SignRoutingEvent (SignActionEvent with extra routing-related helper methods)
     */
    public void route(SignRoutingEvent event) {
        // If path blocked, abort
        if (isPathFindingBlocked(event, event.getCartEnterState())) {
            event.setBlocked();
            return;
        }

        // Is switchable, mark it
        if (isRailSwitcher(event)) {
            event.setRouteSwitchable(true);
        }

        // If destination name is set, add it
        String destinationName = getRailDestinationName(event);
        if (destinationName != null) {
            event.addDestinationName(destinationName);
        }
    }

    /**
     * Whether this sign switches the rails below it based on path finding information.
     * The path discovery logic will look at all possible switchable junctions of the
     * piece of track for new paths.
     * 
     * @param info of the sign
     * @return True if this rail can be switched, and path finding should take that into account
     */
    public boolean isRailSwitcher(SignActionEvent info) {
        return false;
    }

    /**
     * Gets the destination name used when routing using path finding.
     * When non-null is returned, this name becomes available as a potential destination
     * for trains to reach.
     * 
     * @param info of the sign
     * @return destination name, null if no destination exists for this sign (default)
     */
    public String getRailDestinationName(SignActionEvent info) {
        return null;
    }

    /**
     * Gets whether the path finding is halted by this sign, not allowing
     * any further discovery from this point onwards. This can be useful to forcibly
     * prevent certain paths from being taken.
     * 
     * @param info of the sign
     * @param state while driving on the rails, which stores the movement direction among things
     * @return True if blocked, False if not (default)
     */
    public boolean isPathFindingBlocked(SignActionEvent info, RailState state) {
        return false;
    }

    /**
     * Called to predict what should happen to the predicted movement path of a train
     * as it crossed by this sign. Switchers can instruct the train to go a certain
     * way, different from the track layout. Blockers and speed traps can be created
     * by imposing a speed limit, which will gradually slow a train down if a wait
     * deceleration is set.
     *
     * @param info Sign Action Event with sign context information
     * @param prediction The path prediction being performed
     * @see PathPredictEvent
     */
    public void predictPathFinding(SignActionEvent info, PathPredictEvent prediction) {
    }

    /**
     * Gets whether this SignAction performs path prediction. Returns whether
     * {@link #predictPathFinding(SignActionEvent, PathPredictEvent)} has been implemented.
     *
     * @return True if this SignAction has path finding prediction
     */
    public final boolean hasPathFindingPrediction() {
        return this._hasPathPrediction;
    }

    /**
     * Handles a player right-clicking this action sign
     *
     * @param info   related to the event
     * @param player that clicked
     * @return True if handled, False if not
     */
    public boolean click(SignActionEvent info, Player player) {
        return false;
    }

    /**
     * Called when the contents of a Sign matching this SignAction have changed.
     * Returns whether leave/enter events need to be fired as a result of it.
     *
     * @param event Details about the sign that changed
     * @return True if the train should fire leave/enter events for the sign
     */
    public boolean signTextChanged(SignActionEvent event) {
        return true;
    }
}
