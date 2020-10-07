package com.bergerkiller.bukkit.tc.signactions;

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

import org.bukkit.entity.Player;

public class SignActionDestination extends SignAction {

    @Override
    public boolean match(SignActionEvent info) {
        return info.isType("destination");
    }

    @Override
    public boolean click(SignActionEvent info, Player player) {
        //get the train this player is editing
        CartProperties cprop = CartProperties.getEditing(player);
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
        if (info.isRCSign() && info.isAction(SignActionType.REDSTONE_ON)) {
            for (TrainProperties prop : info.getRCTrainProperties()) {
                for (CartProperties cprop : prop) {
                    // Set the cart destination to what is on the fourth line
                    cprop.setDestination(info.getLine(3));
                }
            }
            return;
        }

        // Only activate the sign if it is a cart/train sign, and the appropriate enter event is fired (or redstone-triggered)
        if ( !(info.isCartSign() && info.isAction(SignActionType.REDSTONE_ON, SignActionType.MEMBER_ENTER)) &&
             !(info.isTrainSign() && info.isAction(SignActionType.REDSTONE_ON, SignActionType.GROUP_ENTER)) )
        {
            return;
        }

        // Give node to path finding, discovering routes if it doesn't already exist
        PathNode.getOrCreate(info);

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
        // If this sign was triggered using a redstone-on signal, set the destination on this sign at all times
        // This ignores route and whether or not the destination on the sign matches that of the train
        if (info.isAction(SignActionType.REDSTONE_ON)) {
            return info.getLine(3);
        }

        // If sign is not powered, this sign does nothing
        if (!info.isPowered()) {
            return null;
        }

        // If the destination name of the sign itself is empty, then this sign
        // always sets the destination to what is on the sign.
        // This acts similar to the property destination sign
        if (info.getLine(2).isEmpty()) {
            return info.getLine(3);
        }

        // If the destination of this sign is not one the train is going for,
        // do not set a new destination just yet.
        if (cart.hasDestination() && !cart.getDestination().equals(info.getLine(2))) {
            return null;
        }

        // Use next destination on route if one is used, otherwise use the fourth line for it
        String nextOnRoute = cart.getNextDestinationOnRoute();
        return nextOnRoute.isEmpty() ? info.getLine(3) : nextOnRoute;
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
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
        return opt.handle(event.getPlayer());
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
