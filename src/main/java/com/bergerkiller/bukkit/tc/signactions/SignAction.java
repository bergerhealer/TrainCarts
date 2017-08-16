package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.SignChangeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public abstract class SignAction {
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
        register(new SignActionSkip());
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

    public static <T extends SignAction> T register(T action) {
        if (actions == null) return action;
        actions.add(action);
        return action;
    }

    public static void unregister(SignAction action) {
        if (actions == null) return;
        actions.remove(action);
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

    public static boolean handleBuild(SignChangeActionEvent event, Permission permission, String signname) {
        return handleBuild(event, permission, signname, null);
    }

    public static boolean handleBuild(SignChangeActionEvent event, Permission permission, String signname, String signdescription) {
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
                if (!action.canSupportRC() && info.isRCSign()) {
                    event.getPlayer().sendMessage(ChatColor.RED + "This sign does not support remote control!");
                    info.getHeader().setMode(SignActionMode.TRAIN);
                    event.setLine(0, info.getHeader().toString());
                }
                // Tell train above to update signs, if available
                if (info.hasRails()) {
                    final MinecartMember<?> member = MinecartMemberStore.getAt(info.getRails());
                    if (member != null) {
                        member.getGroup().getBlockTracker().updatePosition();
                    }
                }
            } else {
                event.setCancelled(true);
            }
            if (event.isCancelled()) {
                return;
            }
        }
        if (info.getMode() != SignActionMode.NONE && event.getBlock().getType() == Material.SIGN_POST) {
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
            for (MinecartGroup group : MinecartGroup.getGroups()) {
                group.getBlockTracker().removeSign(info.getBlock());
            }
            // Handle sign destroy logic
            action.destroy(info);
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

        //facing?
        boolean facing = info.getAction().isMovement() ? info.isFacing() : true;

        SignAction action = getSignAction(info);
        if (action != null && (facing || action.overrideFacing())) {
            try {
                action.execute(info);
            } catch (Throwable t) {
                TrainCarts.plugin.getLogger().log(Level.SEVERE, "Failed to execute " + info.getAction().toString() +
                        " for " + action.getClass().getSimpleName() + ":", CommonUtil.filterStackTrace(t));
            }
        }
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
     * Handles the post-destroy logic for when this sign is broken
     *
     * @param info related to the destroy event
     */
    public void destroy(SignActionEvent info) {
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
