package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.common.collections.BlockMap;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.tc.DirectionStatement;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.actions.GroupActionWaitPathFinding;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.pathfinding.PathConnection;
import com.bergerkiller.bukkit.tc.pathfinding.PathNode;
import com.bergerkiller.bukkit.tc.pathfinding.PathProvider;
import com.bergerkiller.bukkit.tc.properties.IProperties;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

    @Override
    public boolean match(SignActionEvent info) {
        return info.isType("switcher", "tag");
    }

    @Override
    public void execute(SignActionEvent info) {
        boolean toggleRails = info.isAction(SignActionType.GROUP_ENTER, SignActionType.MEMBER_ENTER);
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
        } else {
            return;
        }
        if (!info.hasRailedMember()) {
            return;
        }
        final boolean facing = info.isFacing();
        if (facing) {
            //find out what statements to parse
            List<DirectionStatement> statements = new ArrayList<>();
            if (!info.getLine(2).isEmpty()) {
                statements.add(new DirectionStatement(info.getLine(2), "left"));
            }
            if (!info.getLine(3).isEmpty()) {
                statements.add(new DirectionStatement(info.getLine(3), "right"));
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

            if (statements.isEmpty()) {
                // If no directions are at all specified, all we do is toggle the lever
                info.setLevers(true);
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
                String dir = "";
                boolean foundDirection = false;
                for (DirectionStatement stat : statements) {
                    if ((stat.hasNumber() && (counter += stat.number) > currentcount)
                            || (doCart && stat.has(info, info.getMember()))
                            || (doTrain && stat.has(info, info.getGroup()))) {

                        dir = stat.direction;
                        foundDirection = true;
                        break;
                    }
                }
                if (!foundDirection) {
                    // Check if any direction is marked "default"
                    for (DirectionStatement stat : statements) {
                        String str = stat.text.toLowerCase(Locale.ENGLISH);
                        if (str.equals("def") || str.equals("default")) {
                            dir = stat.direction;
                            break;
                        }
                    }
                }

                info.setLevers(!dir.isEmpty());
                if (!dir.isEmpty() && info.isPowered()) {
                    //handle this direction
                    if (toggleRails) {
                        info.setRailsTo(dir);
                    }
                    return; //don't do destination stuff
                }
            }
        }

        // Handle destination alternatively
        if (info.isAction(SignActionType.MEMBER_ENTER, SignActionType.GROUP_ENTER) && (facing || !info.isWatchedDirectionsDefined())) {
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
                    if (PathProvider.isProcessing()) {
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
            return handleBuild(event, Permission.BUILD_SWITCHER, "cart switcher", "switch between tracks based on properties of the cart above");
        } else if (event.isTrainSign()) {
            return handleBuild(event, Permission.BUILD_SWITCHER, "train switcher", "switch between tracks based on properties of the train above");
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
