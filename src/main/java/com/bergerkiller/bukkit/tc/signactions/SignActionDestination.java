package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.common.wrappers.ChatText;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.pathfinding.PathNode;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.IProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.utils.SignBuildOptions;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public class SignActionDestination extends TrainCartsSignAction {

    public SignActionDestination() {
        super("destination");
    }

    @Override
    public boolean click(SignActionEvent info, Player player) {
        //get the train this player is editing
        CartProperties cprop = info.getTrainCarts().getPlayer(player).getEditedCart();
        if (cprop == null) {
            if (Permission.COMMAND_PROPERTIES.has(player)) {
                Localization.EDIT_NOSELECT.message(player);
            } else {
                Localization.EDIT_NOTALLOWED.message(player);
            }
            return true;
        }
        IProperties prop;
        if (info.isTrainSign()) {
            prop = cprop.getTrainProperties();
        } else if (info.isCartSign()) {
            prop = cprop;
        } else {
            return false;
        }
        if (!prop.hasOwnership(player)) {
            Localization.EDIT_NOTOWNED.message(player);
        } else {
            String dest = info.getLine(2);
            prop.setDestination(dest);
            Localization.SELECT_DESTINATION.message(player, dest);
        }
        return true;
    }

    @Override
    public void execute(SignActionEvent info) {
        // Remote control logic
        if (info.isRCSign()) {
            if (info.isAction(SignActionType.REDSTONE_ON)) {
                for (TrainProperties prop : info.getRCTrainProperties()) {
                    for (CartProperties cprop : prop) {
                        // Set the cart destination to what is on the fourth line
                        cprop.setDestination(info.getLine(3));
                    }
                }
            }
            return;
        }

        // Must have rails or nothing will happen (no path finding node)
        if (!info.hasRails()) {
            return;
        }

        // Only activate the sign if it is a cart/train sign, and the appropriate enter event is fired (or redstone-triggered)
        if ( !(info.isCartSign() && info.isAction(SignActionType.REDSTONE_ON, SignActionType.MEMBER_ENTER)) &&
             !(info.isTrainSign() && info.isAction(SignActionType.REDSTONE_ON, SignActionType.GROUP_ENTER)) )
        {
            return;
        }

        // Give node to path finding, discovering routes if it doesn't already exist
        PathNode node = PathNode.getOrCreate(info);

        // Refresh the last-visited node for the members. Used when displaying route.
        for (MinecartMember<?> member : info.getMembers()) {
            member.getProperties().setLastPathNode(node.getName());
        }

        // Compute next destination to set for the minecarts and apply it
        for (MinecartMember<?> member : info.getMembers()) {
            String nextDestination = this.getNextDestination(member.getProperties(), info);
            if (nextDestination != null) {
                if (nextDestination.isEmpty()) {
                    member.getProperties().clearDestination();
                } else {
                    member.getProperties().setDestination(nextDestination);
                }
            }
        }
    }

    /**
     * Gets the next destination name that should be set for a single Minecart, given an event.
     * If null is returned, then no destination should be set.
     * 
     * @param cart The cart to compute the next destination for
     * @param info Event information
     * @return next destination to set, empty to clear, null to do nothing
     */
    private String getNextDestination(CartProperties cart, SignActionEvent info) {
        // Parse new destination to set. If empty, returns null (set nothing)
        String newDestination = info.getLine(3).trim();
        if (newDestination.isEmpty()) {
            newDestination = null;
        }

        // If this sign was triggered using a redstone-on signal, set the destination on this sign at all times
        // This ignores route and whether or not the destination on the sign matches that of the train
        if (info.isAction(SignActionType.REDSTONE_ON)) {
            return newDestination;
        }

        // If sign is not powered, this sign does nothing
        if (!info.isPowered()) {
            return null;
        }

        // If the destination name of the sign itself is empty, then this sign
        // always sets the destination to what is on the sign.
        // This acts similar to the property destination sign
        String signDestination = info.getLine(2);
        if (signDestination.isEmpty()) {
            return newDestination;
        }

        // If the destination of this sign is not one the train is going for,
        // do not set a new destination just yet.
        if (cart.hasDestination() && !cart.getDestination().equals(signDestination)) {
            return null;
        }

        // Pick the next destination on the route from the name of the destination sign triggered
        // The train had no destination set yet, picks the next one assuming it activated a route
        // starting with this destination sign.
        String nextOnRoute = cart.getNextDestinationOnRoute(signDestination);

        // If this sign does not declare a destination of itself, and the cart has a destination
        // that is not part of the route, assume that the route should be restarted anyways.
        if (nextOnRoute.isEmpty() && newDestination == null && !cart.getDestinationRoute().isEmpty()) {
            nextOnRoute = cart.getDestinationRoute().get(0);
        }

        return nextOnRoute.isEmpty() ? newDestination : nextOnRoute;
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        // Check destination by this name already exists. If it does, fail building, and tell the
        // person where it's at
        if (!event.getLine(2).isEmpty()) {
            PathNode node = event.getTrainCarts().getPathProvider().getWorld(event.getWorld()).getNodeByName(event.getLine(2));
            if (node != null) {
                event.getPlayer().sendMessage(ChatColor.RED + "Destination with name '" + event.getLine(2) +
                        "' already exists on this world!");
                ChatText text = ChatText.fromMessage(ChatColor.RED + "Find it at ");
                ChatText command = ChatText.fromMessage(ChatColor.WHITE.toString() + ChatColor.UNDERLINE + "[" +
                        node.location.x + " / " + node.location.y + " / " + node.location.z + "]");
                command.setClickableSuggestedCommand("/tp @p " + node.location.x + " " + node.location.y + " " + node.location.z);
                text.append(command);
                text.sendTo(event.getPlayer());
                return false;
            }
        }

        SignBuildOptions opt = SignBuildOptions.create()
                .setPermission(Permission.BUILD_DESTINATION)
                .setName(event.isCartSign() ? "cart destination" : "train destination")
                .setTraincartsWIKIHelp("TrainCarts/Signs/Destination");

        if (event.isTrainSign()) {
            opt.setDescription("set a train destination and the next destination to set once it is reached");
        } else if (event.isCartSign()) {
            opt.setDescription("set a cart destination and the next destination to set once it is reached");
        } else if (event.isRCSign()) {
            opt.setDescription("set the destination on a remote train");
        }
        return opt.handle(event);
    }

    @Override
    public String getRailDestinationName(SignActionEvent info) {
        String name = info.getLine(2);
        return (name.isEmpty()) ? null : name;
    }

    @Override
    public boolean canSupportRC() {
        return true;
    }
}
