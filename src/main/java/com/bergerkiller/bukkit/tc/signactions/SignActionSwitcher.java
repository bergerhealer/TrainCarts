package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.common.collections.BlockMap;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.tc.Direction;
import com.bergerkiller.bukkit.tc.DirectionStatement;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.actions.GroupActionWaitPathFinding;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.RailJunction;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.events.MissingPathConnectionEvent;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.pathfinding.PathConnection;
import com.bergerkiller.bukkit.tc.pathfinding.PathNavigateEvent;
import com.bergerkiller.bukkit.tc.pathfinding.PathNode;
import com.bergerkiller.bukkit.tc.pathfinding.PathPredictEvent;
import com.bergerkiller.bukkit.tc.pathfinding.SignRoutingEvent;
import com.bergerkiller.bukkit.tc.properties.IProperties;
import com.bergerkiller.bukkit.tc.statements.Statement;
import com.bergerkiller.bukkit.tc.utils.SignBuildOptions;

import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class SignActionSwitcher extends TrainCartsSignAction {
    private BlockMap<CounterState> switchedTimes = new BlockMap<>();

    private CounterState getSwitchedTimes(Block signblock) {
        CounterState i = switchedTimes.get(signblock);
        if (i == null) {
            i = new CounterState();
            switchedTimes.put(signblock, i);
        }
        return i;
    }

    /**
     * As trains leave the switcher sign, we will want to clean
     * up the counter state tracked for it
     *
     * @param info
     */
    private void cleanupCountersOnLeave(SignActionEvent info) {
        if (info.isAction(SignActionType.GROUP_LEAVE)) {
            CounterState state = switchedTimes.get(info.getBlock());
            if (state != null) {
                for (MinecartMember<?> member : info.getGroup()) {
                    state.syncLeave(member);
                }
            }
        }
    }

    private static List<DirectionStatement> parseDirectionStatements(SignActionEvent info) {
        //find out what statements to parse
        List<DirectionStatement> statements = new ArrayList<>();
        if (!info.getLine(2).isEmpty() || !info.getLine(3).isEmpty()) {
            String left_str = Direction.IMPLICIT_LEFT.aliases()[0];
            if (info.getLine(2).isEmpty()) {
                statements.add(new DirectionStatement("default", left_str));
            } else {
                statements.add(new DirectionStatement(info.getLine(2), left_str));
            }

            String right_str = Direction.IMPLICIT_RIGHT.aliases()[0];
            if (info.getLine(3).isEmpty()) {
                statements.add(new DirectionStatement("default", right_str));
            } else {
                statements.add(new DirectionStatement(info.getLine(3), right_str));
            }
        }
        //other signs below this sign we could parse?
        for (String line : info.getExtraLinesBelow()) {
            if (line.isEmpty()) {
                continue;
            }
            DirectionStatement stat = new DirectionStatement(line, "");
            if (!stat.direction.isEmpty()) {
                statements.add(stat);
            }
        }
        return statements;
    }

    public SignActionSwitcher() {
        super("switcher", "tag");
    }

    @Override
    public void execute(SignActionEvent info) {
        (new SwitcherLogic(info)).run();
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        if (event.isCartSign()) {
            return SignBuildOptions.create()
                    .setPermission(Permission.BUILD_SWITCHER)
                    .setName("cart switcher")
                    .setDescription("switch between tracks based on properties of the cart above")
                    .setTraincartsWIKIHelp("TrainCarts/Signs/Switcher")
                    .handle(event);
        } else if (event.isTrainSign()) {
            return SignBuildOptions.create()
                    .setPermission(Permission.BUILD_SWITCHER)
                    .setName("train switcher")
                    .setDescription("switch between tracks based on properties of the train above")
                    .setTraincartsWIKIHelp("TrainCarts/Signs/Switcher")
                    .handle(event);
        }
        return false;
    }

    @Override
    public boolean isRailSwitcher(SignActionEvent info) {
        if (TCConfig.onlyPoweredSwitchersDoPathFinding && info.getHeader().isAlwaysOff()) {
            return false; // Used purely for detecting trains, not actively switching anything
        }
        return true;
    }

    @Override
    public void predictPathFinding(SignActionEvent info, PathPredictEvent prediction) {
        (new SwitcherLogic(info)).predict(prediction);
    }

    @Override
    public void route(SignRoutingEvent event) {
        // Optimization
        if (TCConfig.onlyPoweredSwitchersDoPathFinding && event.getHeader().isAlwaysOff()) {
            return;
        }

        (new SwitcherLogic(event)).route(event);
    }

    @Override
    public boolean overrideFacing() {
        return true;
    }

    /**
     * Stores all the switcher-related logic routines
     */
    private class SwitcherLogic {
        private final SignActionEvent info;
        private final List<DirectionStatement> statements;
        private final boolean hasFromDirections;
        private final boolean doCart;
        private final boolean doTrain;
        private final boolean canToggleRails;

        public SwitcherLogic(SignActionEvent info) {
            this.info = info;
            this.statements = parseDirectionStatements(info);

            {
                boolean calcHasFromDirections = false;
                for (DirectionStatement statement : statements) {
                    if (!statement.isSwitchedFromSelf() && !statement.isDefault()) {
                        calcHasFromDirections = true;
                        break;
                    }
                }
                this.hasFromDirections = calcHasFromDirections;
            }

            // Whether to update the switcher (lever) state
            this.doTrain = info.isTrainSign() && info.isAction(SignActionType.GROUP_ENTER, SignActionType.GROUP_UPDATE);
            this.doCart = info.isCartSign() && info.isAction(SignActionType.MEMBER_ENTER, SignActionType.MEMBER_UPDATE);

            // Only toggles the rails itself when trains or carts enter the sign, or when a redstone change
            // can do so because from-directions are always specified.
            this.canToggleRails = (info.isCartSign() ? info.isAction(SignActionType.MEMBER_ENTER) : info.isAction(SignActionType.GROUP_ENTER)) ||
                    (hasFromDirections && info.isAction(SignActionType.REDSTONE_CHANGE) && info.hasRails() && info.isPowered());
        }

        public void route(SignRoutingEvent event) {
            // Check to see if there is an active constant statement for current activation
            // In that case, don't register a switcher, but instead predict the route taken
            // This avoids issues of trains believing they can take directions they really cannot
            // because of switchers, such as at X-intersections with enter-direction statements.
            if (!statements.isEmpty() && info.isEnterActivated()) {
                DirectionStatement activeDirection = this.selectStatement(true, false);
                if (activeDirection != null) {
                    predictRails(event, activeDirection);
                    return;
                }
            }

            // Mark as switchable
            event.setRouteSwitchable(true);
        }

        public void predict(PathPredictEvent prediction) {
            if (!canToggleRails) {
                return;
            }

            final boolean facing = info.isEnterActivated();

            DirectionStatement activeDirection = null;
            if (!statements.isEmpty() && facing) {
                activeDirection = this.selectStatement(false, false);

                // If not powered or rails cannot be switched, don't switch rails at all
                // Also don't do this after the path finding logic has concluded.
                if (activeDirection != null && (!canToggleRails || !info.isPowered())) {
                    activeDirection = null;
                }

                // If the active direction is non-default, activate it right away
                // Skip path finding logic in that case
                if (activeDirection != null && !activeDirection.isDefault()) {
                    predictRails(prediction, activeDirection);
                    return; //don't do destination stuff
                }
            }

            // Pathfinding or nah?
            boolean handlePathfinding = true;
            if (TCConfig.onlyPoweredSwitchersDoPathFinding && !info.isPowered()) {
                handlePathfinding = false;
            }
            if (TCConfig.onlyEmptySwitchersDoPathFinding && !statements.isEmpty()) {
                handlePathfinding = false;
            }

            // Handle path finding. If switching occurred, don't do anything more
            if (handlePathfinding && this.predictPathFinding(prediction, facing)) {
                return;
            }

            // If a default direction was specified, switch that now that path finding also says nope
            if (activeDirection != null) {
                predictRails(prediction, activeDirection);
            }
        }

        public void run() {
            cleanupCountersOnLeave(info);

            if (doTrain || doCart) {
                // Member/train enter or update logic
            } else if (info.isAction(SignActionType.MEMBER_LEAVE) && info.isCartSign()) {
                info.setLevers(false);
                return;
            } else if (info.isAction(SignActionType.GROUP_LEAVE) && info.isTrainSign()) {
                info.setLevers(false);
                return;
            } else if (!canToggleRails) {
                return; // Nothing to do here
            }

            final boolean hasMember = info.hasRailedMember();
            final boolean facing = !hasMember || info.isFacing();

            DirectionStatement activeDirection = null;
            if (facing) {
                if (statements.isEmpty()) {
                    // When no statements are specified, only toggle lever
                    if (hasMember) {
                        info.setLevers(true);
                    }
                } else {
                    activeDirection = this.selectStatement(false, true);

                    // Only set levers down when a non-default statement condition matches and a cart is on the sign
                    if (hasMember) {
                        info.setLevers(activeDirection != null && !activeDirection.isDefault());
                    }

                    // If not powered or rails cannot be switched, don't switch rails at all
                    // Also don't do this after the path finding logic has concluded.
                    if (activeDirection != null && (!canToggleRails || !info.isPowered())) {
                        activeDirection = null;
                    }

                    // If the active direction is non-default, activate it right away
                    // Skip path finding logic in that case
                    if (activeDirection != null && !activeDirection.isDefault()) {
                        switchRails(activeDirection);
                        return; //don't do destination stuff
                    }
                }
            }

            // Pathfinding or nah?
            boolean handlePathfinding = true;
            if (TCConfig.onlyPoweredSwitchersDoPathFinding && !info.isPowered()) {
                handlePathfinding = false;
            }
            if (TCConfig.onlyEmptySwitchersDoPathFinding && !statements.isEmpty()) {
                handlePathfinding = false;
            }

            // Handle path finding. If switching occurred, don't do anything more
            if (handlePathfinding && this.handlePathFinding(facing)) {
                return;
            }

            // If a default direction was specified, switch that now that path finding also says nope
            if (activeDirection != null) {
                switchRails(activeDirection);
            }
        }

        private void switchRails(DirectionStatement direction) {
            if (direction.isSwitchedFromSelf()) {
                info.setRailsTo(direction.direction);
            } else {
                info.setRailsFromTo(direction.directionFrom, direction.direction);
            }
        }

        private void predictRails(PathNavigateEvent navigateEvent, DirectionStatement direction) {
            RailJunction a = info.findJunction(direction.direction);
            RailJunction b = direction.isSwitchedFromSelf()
                    ? null : info.findJunction(direction.directionFrom);
            if (b == null) {
                if (a == null) {
                    return;
                } else {
                    navigateEvent.setSwitchedJunction(a);
                }
            } else if (a == null) {
                navigateEvent.setSwitchedJunction(b);
            } else {
                // Figure out whether to switch to direction or directionFrom
                // This is based on the current movement direction
                RailPath.Position pos = navigateEvent.railState().position();
                if (a.position().motDot(pos) > b.position().motDot(pos)) {
                    navigateEvent.setSwitchedJunction(a);
                } else {
                    navigateEvent.setSwitchedJunction(b);
                }
            }
        }

        private void predictRailsTo(PathNavigateEvent prediction, String name) {
            RailJunction junction = info.findJunction(name);
            if (junction != null) {
                prediction.setSwitchedJunction(junction);
            }
        }

        private boolean handlePathFinding(boolean facing) {
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
                        if (info.getTrainCarts().getPathProvider().isProcessing()) {
                            double currentForce = info.getGroup().getAverageForce();
                            // Add an action to let the train wait until the node IS explored
                            info.getGroup().getActions().addAction(new GroupActionWaitPathFinding(info, node, destination));
                            info.getMember().getActions().addActionLaunch(info.getMember().getDirectionFrom(), 1.0, currentForce);
                            info.getGroup().stop();
                        } else {
                            // Switch the rails to the right direction
                            PathConnection conn = node.findConnection(destination);
                            if (conn != null) {
                                if (this.canToggleRails) {
                                    info.setRailsTo(conn.junctionName);
                                }
                            } else {
                                // Call MissingPathConnectionEvent
                                CommonUtil.callEvent(new MissingPathConnectionEvent(info.getRailPiece(), node, info.getGroup(), destination));
                                Localization.PATHING_FAILED.broadcast(info.getGroup(), destination);
                            }
                        }
                        
                        // Successfully handled, don't switch the track!
                        return true;
                    }
                }
            }

            return false;
        }

        private boolean predictPathFinding(PathPredictEvent prediction, boolean facing) {
            if (facing || !info.isWatchedDirectionsDefined()) {
                PathNode node = PathNode.getOrCreate(info);
                if (node != null) {
                    if (info.getTrainCarts().getPathProvider().isProcessing()) {
                        // Train should wait until this is done. Polls every tick.
                        prediction.setSpeedLimit(0.0);
                    } else {
                        // Figure out where to go
                        String destination = null;
                        if (doCart) {
                            destination = info.getMember().getProperties().getDestination();
                        } else if (doTrain) {
                            destination = info.getGroup().getProperties().getDestination();
                        }

                        // Continue with path finding if a valid destination is specified
                        // If the current node denotes the destination - don't switch!
                        if (!LogicUtil.nullOrEmpty(destination) && !node.containsName(destination)) {
                            PathConnection conn = node.findConnection(destination);
                            if (conn != null) {
                                this.predictRailsTo(prediction, conn.junctionName);
                            }
                        }
                    }
                }
            }

            return false;
        }

        /**
         * Retrieves the direction statement on the switcher sign that is active right now.
         *
         * @param isPathRouting Whether a route is being calculated in the path finding algorithm.
         *                      In this case, if a statement is hit that isn't constant such
         *                      as 'true' or based on enter direction, then null is returned.
         *                      This makes sure trains try to navigate there to let switcher
         *                      behavior handle it. If false, behaves normally.
         * @param incrementCounters Whether to increment counter statements. If false, only
         *                          reads the counter state (prediction).
         * @return Active direction statement
         */
        private DirectionStatement selectStatement(boolean isPathRouting, boolean incrementCounters) {
            boolean hasMember = info.hasRailedMember();
            if (statements.isEmpty()) {
                // If no directions are at all specified, all we do is toggle the lever
                if (hasMember) {
                    info.setLevers(true);
                }
                return null;
            } else {
                //parse all of the statements
                //are we going to use a counter?
                int maxcount = 0;
                CounterState signcounter = null;
                for (DirectionStatement stat : statements) {
                    if (stat.hasCounter()) {
                        if (signcounter == null) {
                            signcounter = getSwitchedTimes(info.getBlock());
                            if (info.isCartSign() && incrementCounters && info.hasGroup()) {
                                signcounter.syncCartSignEnter(info.getGroup(), info.getRailPiece());
                            }
                        }
                        maxcount += stat.counter.get(signcounter.startLength);
                    }
                }

                // Increment counter every time a new cart enters the sign
                int counter = 0;
                if (signcounter != null && incrementCounters) {
                    if (info.isAction(SignActionType.MEMBER_ENTER, SignActionType.GROUP_ENTER)) {
                        signcounter.counter++;
                    } else if (info.isAction(SignActionType.REDSTONE_ON) && hasFromDirections) {
                        signcounter.counter++;
                    }
                    if (signcounter.counter > maxcount) {
                        signcounter.counter = 1;
                    }
                    counter = 1; // counter starts at 1 due to increment
                }

                DirectionStatement dir = null;
                for (DirectionStatement stat : statements) {
                    // Don't evaluate these here
                    if (stat.isDefault()) {
                        continue;
                    }

                    // Counter logic
                    if (stat.hasCounter()) {
                        // Counters are not constant, fail routing
                        if (isPathRouting) {
                            return null;
                        }

                        // Check counter
                        if ((counter += stat.counter.get(signcounter.startLength)) > signcounter.counter) {
                            dir = stat;
                            break;
                        }
                    }

                    // If path routing, the statement must support this. It must be constant.
                    // Many statements like random or redstone are not constant and must be evaluated
                    // when the train rolls over it.
                    if (isPathRouting) {
                        Statement.MatchResult result = Statement.Matcher.of(stat.text).withSignEvent(info).match();
                        if (!result.isConstant()) {
                            // Not a constant, can't use it for path prediction when routing
                            return null;
                        } else if (result.has()) {
                            // Constant statement evaluates true, pick it
                            dir = stat;
                            break;
                        }
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

                // Check if any direction is marked "default"
                // Don't select these when doing path routing, as path finding has preference
                if (dir == null && !isPathRouting) {
                    for (DirectionStatement stat : statements) {
                        if (stat.isDefault() && (hasMember || !stat.isSwitchedFromSelf())) {
                            dir = stat;
                            break;
                        }
                    }
                }

                // If direction itself is null, return null, which acts as a 'do nothing'
                if (dir != null && dir.direction.isEmpty()) {
                    dir = null;
                }

                return dir;
            }
        }
    }

    /**
     * Tracks the counter state of counters on a switcher sign.
     * Not persistent.
     */
    private static class CounterState {
        public int counter = 0;
        public int startLength = 0;
        public Set<UUID> uuidsToIgnore = Collections.emptySet();

        /**
         * Synchronizes the counter. If it finds out an entirely new
         * group hit the switcher sign, resets the counter.
         *
         * @param group
         * @param railPiece
         */
        public void syncCartSignEnter(MinecartGroup group, RailPiece railPiece) {
            if (!TCConfig.switcherResetCountersOnFirstCart) {
                return;
            }

            boolean isNewGroup = !isGroupTracked(group);
            addAll(group);
            if (isNewGroup) {
                this.startLength = group.size();
                if (TCConfig.switcherResetCountersOnFirstCart) {
                    this.counter = 0;
                }
                if (uuidsToIgnore.size() != group.size()) {
                    // Weird situation that there are carts not
                    // tracked by the group. Sync up!
                    uuidsToIgnore.clear();
                    addAll(group);
                    if (railPiece != RailPiece.NONE) {
                        railPiece.members().stream()
                            .map(MinecartMember::getGroup)
                            .distinct()
                            .forEach(this::addAll);
                    }
                }
            }
        }

        /**
         * Cleans up tracked uuids as carts leave the sign
         *
         * @param member
         */
        public void syncLeave(MinecartMember<?> member) {
            // Remove from set, once empty, set to empty constant
            if (!uuidsToIgnore.isEmpty()) {
                uuidsToIgnore.remove(member.getEntity().getUniqueId());
                if (uuidsToIgnore.isEmpty()) {
                    uuidsToIgnore = Collections.emptySet();
                }
            }
        }

        private void addAll(MinecartGroup group) {
            if (uuidsToIgnore.isEmpty()) {
                uuidsToIgnore = new HashSet<>();
            }
            for (MinecartMember<?> member : group) {
                uuidsToIgnore.add(member.getEntity().getUniqueId());
            }
        }

        private boolean isGroupTracked(MinecartGroup group) {
            for (MinecartMember<?> member : group) {
                if (uuidsToIgnore.contains(member.getEntity().getUniqueId())) {
                    return true;
                }
            }
            return false;
        }
    }
}
