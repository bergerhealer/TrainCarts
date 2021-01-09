package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.common.collections.BlockMap;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.tc.DirectionStatement;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.actions.GroupActionWaitPathFinding;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.pathfinding.PathConnection;
import com.bergerkiller.bukkit.tc.pathfinding.PathNode;
import com.bergerkiller.bukkit.tc.properties.IProperties;
import com.bergerkiller.bukkit.tc.utils.SignBuildOptions;

import org.bukkit.block.Block;
import org.bukkit.block.Sign;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class SignActionSwitcher extends SignAction {
    private BlockMap<AtomicInteger> switchedTimes = new BlockMap<>();

    private AtomicInteger getSwitchedTimes(Block signblock) {
        AtomicInteger i = switchedTimes.get(signblock);
        if (i == null) {
            i = new AtomicInteger();
            switchedTimes.put(signblock, i);
        }
        return i;
    }

    private static List<DirectionStatement> parseDirectionStatements(SignActionEvent info) {
        //find out what statements to parse
        List<DirectionStatement> statements = new ArrayList<>();
        if (!info.getLine(2).isEmpty() || !info.getLine(3).isEmpty()) {
            if (info.getLine(2).isEmpty()) {
                statements.add(new DirectionStatement("default", "left"));
            } else {
                statements.add(new DirectionStatement(info.getLine(2), "left"));
            }
            if (info.getLine(3).isEmpty()) {
                statements.add(new DirectionStatement("default", "right"));
            } else {
                statements.add(new DirectionStatement(info.getLine(3), "right"));
            }
        }
        //other signs below this sign we could parse?
        for (Sign sign : info.findSignsBelow()) {
            boolean valid = true;
            for (String line : sign.getLines()) {
                if (line.isEmpty()) {
                    continue;
                }
                DirectionStatement stat = new DirectionStatement(line, "");
                if (stat.direction.isEmpty()) {
                    valid = false;
                    break;
                } else {
                    statements.add(stat);
                }
            }
            if (!valid) {
                break;
            }
        }
        
        return statements;
    }

    @Override
    public boolean match(SignActionEvent info) {
        return info.isType("switcher", "tag");
    }

    @Override
    public void execute(SignActionEvent info) {
        List<DirectionStatement> statements = parseDirectionStatements(info);
        boolean hasFromDirections = false;
        for (DirectionStatement statement : statements) {
            if (!statement.isSwitchedFromSelf()) {
                hasFromDirections = true;
                break;
            }
        }

        boolean toggleRails = info.isCartSign() ? info.isAction(SignActionType.MEMBER_ENTER) : info.isAction(SignActionType.GROUP_ENTER);
        boolean doCart = false;
        boolean doTrain = false;
        if (info.isAction(SignActionType.GROUP_ENTER, SignActionType.GROUP_UPDATE) && info.isTrainSign()) {
            doTrain = true;
        } else if (info.isAction(SignActionType.MEMBER_ENTER, SignActionType.MEMBER_UPDATE) && info.isCartSign()) {
            doCart = true;
        } else if (info.isAction(SignActionType.MEMBER_LEAVE) && info.isCartSign()) {
            info.setLevers(false);
            return;
        } else if (info.isAction(SignActionType.GROUP_LEAVE) && info.isTrainSign()) {
            info.setLevers(false);
            return;
        } else if (hasFromDirections && info.isPowered() && info.isAction(SignActionType.REDSTONE_CHANGE)) {
            // Redstone change used with from-to directions, to toggle track automatically
            // Used when toggling rails using redstone input
            toggleRails = true;
        } else {
            return;
        }

        final boolean hasMember = info.hasRailedMember();
        final boolean facing = !hasMember || info.isFacing();

        if (facing) {
            if (statements.isEmpty()) {
                // If no directions are at all specified, all we do is toggle the lever
                if (hasMember) {
                    info.setLevers(true);
                }
            } else {
                //parse all of the statements
                //are we going to use a counter?
                int maxcount = 0;
                int currentcount = 0;
                AtomicInteger signcounter = null;
                for (DirectionStatement stat : statements) {
                    //System.out.println(stat.toString());
                    if (stat.hasNumber()) {
                        maxcount += stat.number;
                        if (signcounter == null) {
                            signcounter = getSwitchedTimes(info.getBlock());
                            if (info.isAction(SignActionType.MEMBER_ENTER, SignActionType.GROUP_ENTER)) {
                                currentcount = signcounter.getAndIncrement();
                            } else if (info.isAction(SignActionType.REDSTONE_ON) && !stat.isSwitchedFromSelf()) {
                                currentcount = signcounter.getAndIncrement();
                            } else {
                                currentcount = signcounter.get();
                            }
                        }
                    }
                }
                if (signcounter != null && currentcount >= maxcount) {
                    signcounter.set(1);
                    currentcount = 0;
                }

                int counter = 0;
                DirectionStatement dir = null;
                for (DirectionStatement stat : statements) {
                    // Counter logic
                    if (stat.hasNumber() && (counter += stat.number) > currentcount) {
                        dir = stat;
                        break;
                    }

                    // Cart/Train logic
                    if ((doCart && stat.has(info, info.getMember())) || (doTrain && stat.has(info, info.getGroup()))) {
                        dir = stat;
                        break;
                    }

                    // When no member exists, but the statement supports that, and a from direction is also specified
                    if (!stat.isSwitchedFromSelf() && stat.has(info, (MinecartMember<?>) null)) {
                        dir = stat;
                        break;
                    }
                }
                boolean foundDirection = (dir != null);
                if (!foundDirection) {
                    // Check if any direction is marked "default"
                    for (DirectionStatement stat : statements) {
                        if (stat.isDefault() && (hasMember || !stat.isSwitchedFromSelf())) {
                            dir = stat;
                            break;
                        }
                    }
                }

                if (hasMember) {
                    info.setLevers(foundDirection || statements.isEmpty());
                }

                if (dir != null && !dir.direction.isEmpty() && info.isPowered()) {
                    //handle this direction
                    if (toggleRails) {
                        if (dir.isSwitchedFromSelf()) {
                            info.setRailsTo(dir.direction);
                        } else {
                            info.setRailsFromTo(dir.directionFrom, dir.direction);
                        }
                    }
                    return; //don't do destination stuff
                }
            }
        }

        // Pathfinding or nah?
        boolean handlePathfinding;
        if (TCConfig.onlyPoweredEmptySwitchersDoPathfinding) {
            handlePathfinding = info.isPowered() && statements.isEmpty();
        } else {
            handlePathfinding = true;
        }

        // Handle path finding
        if (handlePathfinding && info.isAction(SignActionType.MEMBER_ENTER, SignActionType.GROUP_ENTER) && (facing || !info.isWatchedDirectionsDefined())) {
            PathNode node = PathNode.getOrCreate(info);
            if (node != null) {
                String destination = null;
                IProperties prop = null;
                if (doCart && info.hasMember()) {
                    prop = info.getMember().getProperties();
                } else if (doTrain && info.hasGroup()) {
                    prop = info.getGroup().getProperties();
                }
                if (prop != null) {
                    destination = prop.getDestination();
                    prop.setLastPathNode(node.getName());
                }
                // Continue with path finding if a valid destination is specified
                // If the current node denotes the destination - don't switch!
                if (!LogicUtil.nullOrEmpty(destination) && !node.containsName(destination)) {
                    if (TrainCarts.plugin.getPathProvider().isProcessing()) {
                        double currentForce = info.getGroup().getAverageForce();
                        // Add an action to let the train wait until the node IS explored
                        info.getGroup().getActions().addAction(new GroupActionWaitPathFinding(info, node, destination));
                        info.getMember().getActions().addActionLaunch(info.getMember().getDirectionFrom(), 1.0, currentForce);
                        info.getGroup().stop();
                    } else {
                        // Switch the rails to the right direction
                        PathConnection conn = node.findConnection(destination);
                        if (conn != null) {
                            if (toggleRails) {
                                info.setRailsTo(conn.junctionName);
                            }
                        } else {
                            Localization.PATHING_FAILED.broadcast(info.getGroup(), destination);
                        }
                    }
                }
            }
        }
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        if (event.isCartSign()) {
            return SignBuildOptions.create()
                    .setPermission(Permission.BUILD_SWITCHER)
                    .setName("cart switcher")
                    .setDescription("switch between tracks based on properties of the cart above")
                    .setTraincartsWIKIHelp("TrainCarts/Signs/Switcher")
                    .handle(event.getPlayer());
        } else if (event.isTrainSign()) {
            return SignBuildOptions.create()
                    .setPermission(Permission.BUILD_SWITCHER)
                    .setName("train switcher")
                    .setDescription("switch between tracks based on properties of the train above")
                    .setTraincartsWIKIHelp("TrainCarts/Signs/Switcher")
                    .handle(event.getPlayer());
        }
        return false;
    }

    @Override
    public boolean isRailSwitcher(SignActionEvent info) {
        return true;
    }

    @Override
    public boolean overrideFacing() {
        return true;
    }
}
