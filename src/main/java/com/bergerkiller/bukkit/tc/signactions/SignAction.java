package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.permissions.PermissionEnum;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.pathfinding.PathNode;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.block.SignChangeEvent;

import static com.bergerkiller.bukkit.common.utils.MaterialUtil.getMaterial;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public abstract class SignAction {
    private static final Material SIGN_POST_TYPE = getMaterial("LEGACY_SIGN_POST");
    private static List<SignAction> actions;

    public static void init() {
        actions = new ArrayList<>();
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
        register(new SignActionSound());
        register(new SignActionSkip());
        register(new SignActionMutex());
        register(new SignActionFlip());
        register(new SignActionAnimate());
    }

    public static void deinit() {
        actions = null;
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
     */
    public static <T extends SignAction> T register(T action, boolean priority) {
        if (actions != null) {
            if (priority) {
                actions.add(0, action);
            } else {
                actions.add(action);
            }
        }
        return action;
    }
    
    public static void unregister(SignAction action) {
        if (actions == null) return;
        actions.remove(action);
    }

    /**
     * Handles a change in the loaded change of a sign.
     * Sign Actions bound to this sign will have their {@link #loadedChanged(SignActionEvent, boolean)} called.
     * 
     * @param sign that was loaded/unloaded
     * @param loaded state change
     */
    public static void handleLoadChange(Sign sign, boolean loaded) {
        final SignActionEvent info = new SignActionEvent(sign.getBlock(), sign, null);
        for (SignAction action : actions) {
            if (action._hasLoadedChangeHandler && action.match(info) && action.verify(info)) {
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
        SignActionEvent info = new SignActionEvent(clickedSign);
        if (info.getSign() == null) {
            return false;
        }
        SignAction action = getSignAction(info);
        return action != null && action.click(info, player);
    }

    public static boolean handleBuild(SignChangeActionEvent event, PermissionEnum permission, String signname) {
        return handleBuild(event, permission, signname, null);
    }

    public static boolean handleBuild(SignChangeActionEvent event, PermissionEnum permission, String signname, String signdescription) {
        if (permission.handleMsg(event.getPlayer(), ChatColor.RED + "You do not have permission to use this sign")) {
            event.getPlayer().sendMessage(ChatColor.YELLOW + "You built a " + ChatColor.WHITE + signname + ChatColor.YELLOW + "!");
            if (signdescription != null) {
                event.getPlayer().sendMessage(ChatColor.GREEN + "This sign can " + signdescription + ".");
            }
            return true;
        }
        return false;
    }

    public static void handleBuild(SignChangeEvent event) {
        final SignChangeActionEvent info = new SignChangeActionEvent(event);
        SignAction action = getSignAction(info);
        if (action != null) {
            if (action.build(info)) {
                // Inform about use of RC when not supported
                if (!action.canSupportRC() && info.isRCSign()) {
                    event.getPlayer().sendMessage(ChatColor.RED + "This sign does not support remote control!");
                    info.getHeader().setMode(SignActionMode.TRAIN);
                    event.setLine(0, info.getHeader().toString());
                }

                // For signs that define path finding destinations, report about duplicate names
                String destinationName = action.getRailDestinationName(info);
                if (destinationName != null) {
                    PathNode node = TrainCarts.plugin.getPathProvider().getWorld(info.getWorld()).getNodeByName(destinationName);
                    if (node != null) {
                        Player p = event.getPlayer();
                        p.sendMessage(ChatColor.RED + "Another destination with the same name already exists!");
                        p.sendMessage(ChatColor.RED + "Please remove either sign and use /train reroute to fix");

                        // Send location message
                        BlockLocation loc = node.location;
                        StringBuilder locMsg = new StringBuilder(100);
                        locMsg.append(ChatColor.RED).append("Other destination '" + destinationName + "' is ");
                        if (loc.getWorld() != event.getPlayer().getWorld()) {
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
                    final MinecartMember<?> member = MinecartMemberStore.getAt(info.getRails());
                    if (member != null) {
                        member.getGroup().getSignTracker().updatePosition();
                    }
                }

                // Call loaded
                action.loadedChanged(info, true);
            } else {
                event.setCancelled(true);
            }
            if (event.isCancelled()) {
                return;
            }
        }
        if (info.getMode() != SignActionMode.NONE && WorldUtil.getBlockData(event.getBlock()).isType(SIGN_POST_TYPE)) {
            //snap to fixed 45-degree angle
            BlockFace facing = BlockUtil.getFacing(event.getBlock());
            BlockUtil.setFacing(event.getBlock(), Util.snapFace(facing));
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
                group.getSignTracker().removeSign(info.getBlock());
            }

            // Handle sign destroy logic
            // Check for things that are path finding - related first
            boolean switchable = action.isRailSwitcher(info);
            String destinationName = action.getRailDestinationName(info);
            action.destroy(info);

            // Remove (invalidate) the rails block, if part of path finding logic
            if (destinationName != null) {
                PathNode node = TrainCarts.plugin.getPathProvider().getWorld(info.getWorld()).getNodeByName(destinationName);
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
        if (CommonUtil.callEvent(info).isCancelled() || actions == null) {
            return; // ignore further processing
        }

        // Find matching SignAction for this sign
        SignAction action = getSignAction(info);
        if (action == null) {
            return;
        }

        // Ignore MEMBER_MOVE if not handled
        if (info.isAction(SignActionType.MEMBER_MOVE) && !action.isMemberMoveHandled(info)) {
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
            TrainCarts.plugin.getLogger().log(Level.SEVERE, "Failed to execute " + info.getAction().toString() +
                    " for " + action.getClass().getSimpleName() + ":", CommonUtil.filterStackTrace(t));
        }
    }

    private final boolean _hasLoadedChangeHandler;

    public SignAction() {
        this._hasLoadedChangeHandler = CommonUtil.isMethodOverrided(SignAction.class, this.getClass(), "loadedChanged", SignActionEvent.class, boolean.class);
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
     * Handles a player right-clicking this action sign
     *
     * @param info   related to the event
     * @param player that clicked
     * @return True if handled, False if not
     */
    public boolean click(SignActionEvent info, Player player) {
        return false;
    }
}
