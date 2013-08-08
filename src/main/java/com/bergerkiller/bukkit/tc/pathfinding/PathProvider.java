package com.bergerkiller.bukkit.tc.pathfinding;

import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Level;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.plugin.java.JavaPlugin;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.signactions.SignActionMode;
import com.bergerkiller.bukkit.tc.utils.TrackIterator;

public class PathProvider extends Task {
	public static boolean DEBUG_MODE = false;
	private static final int STEP_COUNT = 100; // Steps performed per timing check
	private static final int MAX_PROCESSING_PER_TICK = 30; // Maximum processing time in Ms per tick
	private static PathProvider task;
	private Set<PathNode> pendingNodes = new LinkedHashSet<PathNode>();
	private Queue<PathFindOperation> pendingOperations = new LinkedList<PathFindOperation>();

	private PathProvider(JavaPlugin plugin) {
		super(plugin);
	}

	public static void init() {
		task = new PathProvider(TrainCarts.plugin);
		task.start(1, 1);
	}

	public static void deinit() {
		if (task == null) {
			return;
		}
		task.stop();
		task = null;
	}

	/**
	 * Tells this Path Provider to start calculating all neighboring paths from the node specified
	 * 
	 * @param startNode to schedule
	 */
	public static void schedule(PathNode startNode) {
		if (task != null) {
			task.pendingNodes.add(startNode);
		}
	}

	/**
	 * Tells this Path Provider to start finding the next node(s) from the start point specified
	 * 
	 * @param startNode from which to seek and to which connections should be made
	 * @param startBlock from which to start searching
	 * @param startDirection towards which direction to start searching
	 */
	public static void schedule(PathNode startNode, Block startBlock, BlockFace startDirection) {
		if (task != null) {
			task.pendingOperations.offer(new PathFindOperation(startNode, startBlock, startDirection));
		}
	}

	/**
	 * Checks whether this Path Provider is currently busy processing path finding
	 * 
	 * @return True if processing is being performed, False if not
	 */
	public static boolean isProcessing() {
		return task != null && (!task.pendingOperations.isEmpty() || !task.pendingNodes.isEmpty());
	}

	@Override
	public Task stop() {
		addPendingNodes();
		if (!this.pendingOperations.isEmpty()) {
			TrainCarts.plugin.log(Level.INFO, "Performing " + this.pendingOperations.size() + " pending path finding operations (can take a while)...");
			while (!this.pendingOperations.isEmpty()) {
				PathFindOperation operation = this.pendingOperations.poll();
				while (operation.next());
			}
		}
		return super.stop();
	}

	@Override
	public void run() {
		if (this.pendingOperations.isEmpty()) {
			addPendingNodes();
		}
		if (this.pendingOperations.isEmpty()) {
			return;
		}
		int i;
		boolean done;
		final long startTime = System.currentTimeMillis();
		while (!this.pendingOperations.isEmpty()) {
			PathFindOperation operation = this.pendingOperations.peek();
			done = false;
			if (DEBUG_MODE) {
				System.out.println("DISCOVERING EVERYTHING FROM " + operation.startNode.getDisplayName() + 
						" INTO " + operation.startDir);
			}
			// Perform the operations in steps
			// Not per step, because System.currentTimeMillis is not entirely cheap!
			do {
				for (i = 0; i < STEP_COUNT && !done; i++) {
					done = operation.next();
				}
			} while (!done && (System.currentTimeMillis() - startTime) <= MAX_PROCESSING_PER_TICK);
			if (done) {
				this.pendingOperations.poll();
			} else {
				break; // Ran out of time
			}
		}
	}

	private void addPendingNodes() {
		if (!this.pendingNodes.isEmpty()) {
			for (PathNode node : this.pendingNodes) {
				Block startRail = node.location.getBlock();
				RailType startType = RailType.getType(startRail);
				if (startType == RailType.NONE) {
					// Track type can not be identified
					continue;
				}
				if (node.containsSwitcher()) {
					if (DEBUG_MODE) {
						System.out.println("NODE " + node.getDisplayName() + " CONTAINS A SWITCHER");
					}
					// Check north-east-south-west for possible routes
					for (BlockFace dir : FaceUtil.AXIS) {
						scheduleNode(node, startType.findMinecartPos(startRail).getRelative(dir), dir);
					}
				} else {
					// Only check available routes
					for (BlockFace dir : startType.getPossibleDirections(startRail)) {
						scheduleNode(node, startType.getNextPos(startRail, dir), dir);
					}
				}
			}
			this.pendingNodes.clear();
		}
	}

	private void scheduleNode(PathNode node, Block startBlock, BlockFace direction) {
		for (RailType nextType : RailType.values()) {
			Block startRail = nextType.findRail(startBlock);
			if (startRail != null) {
				schedule(node, startRail, direction);
				return;
			}
		}
	}

	private static class PathFindOperation {
		private final TrackIterator iter;
		private final BlockFace startDir;
		private final PathNode startNode;

		public PathFindOperation(PathNode startNode, Block startBlock, BlockFace startFace) {
			this.iter = new TrackIterator(startBlock, startFace);
			this.startDir = startFace;
			this.startNode = startNode;
		}

		/**
		 * Performs the next finding run
		 * 
		 * @return True if this task is finished, False if not
		 */
		public boolean next() {
			if (!iter.hasNext()) {
				return true;
			}
			Block nextRail = iter.next();
			BlockLocation newNodeLocation;
			String newNodeName;
			boolean hasFinished = false;
			for (Block signblock : Util.getSignsFromRails(nextRail)) {
				SignActionEvent event = new SignActionEvent(signblock);
				if (event.getMode() != SignActionMode.NONE) {
					if (event.isType("tag", "switcher")){
						newNodeLocation = new BlockLocation(nextRail);
						newNodeName = newNodeLocation.toString();
					} else if (event.isType("destination")) {
						newNodeLocation = new BlockLocation(nextRail);
						newNodeName = event.getLine(2);
					} else if (event.isType("blocker") && event.isWatchedDirection(iter.currentDirection()) && event.isPowerAlwaysOn()) {
						hasFinished = true;
						break;
					} else {
						continue;
					}
					if (!newNodeName.isEmpty() && !startNode.containsName(newNodeName)) {
						//finished, we found our first target - create connection
						PathNode to = PathNode.getOrCreate(newNodeName, newNodeLocation);
						this.startNode.addNeighbour(to, iter.getDistance() + 1, this.startDir);
						hasFinished = true;
						if (DEBUG_MODE) {
							System.out.println("MADE CONNECTION FROM " + startNode.getDisplayName() + " TO " + newNodeName);
						}
					}
				}
			}
			return hasFinished;
		}
	}
}
