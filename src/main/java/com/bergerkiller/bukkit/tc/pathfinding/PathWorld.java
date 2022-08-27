package com.bergerkiller.bukkit.tc.pathfinding;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.bukkit.block.Block;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.collections.BlockMap;
import com.bergerkiller.bukkit.tc.TrainCarts;

/**
 * A single world on which path nodes are stored
 */
public class PathWorld implements TrainCarts.Provider {
    private final PathProvider _provider;
    private final String _name;
    private final BlockMap<PathNode> _blockNodes;
    private final Map<String, PathNode> _nodes;

    public PathWorld(PathProvider provider, String worldName) {
        _provider = provider;
        _name = worldName;
        _blockNodes = new BlockMap<>();
        _nodes = new HashMap<>();
    }

    @Override
    public TrainCarts getTrainCarts() {
        return _provider.getTrainCarts();
    }

    /**
     * Gets the path provider responsible for this world
     * 
     * @return provider
     */
    public PathProvider getProvider() {
        return _provider;
    }

    /**
     * Gets the name of this World
     * 
     * @return world name
     */
    public String getName() {
        return _name;
    }

    public PathNode getNodeAtRail(BlockLocation railLocation) {
        return _blockNodes.get(railLocation);
    }

    public PathNode getNodeAtRail(Block railBlock) {
        return _blockNodes.get(railBlock);
    }

    public PathNode getNodeByName(String name) {
        return _nodes.get(name);
    }

    public Set<BlockLocation> getRailBlocks() {
        return _blockNodes.keySet();
    }

    public Collection<PathNode> getNodes() {
        return _blockNodes.values();
    }

    public PathNode removeAtRail(Block railBlock) {
        PathNode node = _blockNodes.remove(railBlock);
        if (node != null) node.remove();
        return node;
    }

    public PathNode getOrCreateAtRail(final BlockLocation location) {
        if (location == null) {
            return null;
        }

        PathNode node = getNodeAtRail(location);
        return (node != null) ? node : addNode(location);
    }

    public PathNode addNode(BlockLocation location) {
        PathNode node = new PathNode(this, location);
        addToMapping(node);
        _provider.scheduleNode(node);
        return node;
    }

    public void rerouteAll() {
        for (BlockLocation location : getRailBlocks()) {
            _provider.discoverFromRail(location);
        }
        clearAll();
    }

    public void clearAll() {
        _nodes.clear();
        _blockNodes.clear();
        _provider.markChanged();
    }

    protected void addNodeName(PathNode node, String name) {
        _nodes.put(name, node);
        _provider.markChanged();
    }

    protected void removeNodeName(PathNode node, String name) {
        PathNode removed = _nodes.remove(name);
        if (removed == node) {
            _provider.markChanged();
        } else if (removed != null) {
            _nodes.put(name, removed); // restore
        }
    }

    protected void addToMapping(PathNode node) {
        for (String name : node.getNames()) {
            addNodeName(node, name);
        }
        _blockNodes.put(node.location, node);
        _nodes.put(node.location.toString(), node);
        _provider.markChanged();
    }

    protected void removeFromMapping(PathNode node) {
        for (String name : node.getNames()) {
            PathNode removed = _nodes.remove(name);
            if (removed != null && removed != node) {
                _nodes.put(name, removed); // restore
            }
        }
        PathNode removed = _blockNodes.remove(node.location);
        if (removed != null && removed != node) {
            _blockNodes.put(node.location, removed); // restore
        } else if (removed != null) {
            _nodes.remove(node.location.toString());
        }
        _provider.markChanged();
    }
}
